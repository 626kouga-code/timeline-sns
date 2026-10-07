package com.timelinesns.comment;

import com.timelinesns.comment.CommentQuery.Ancestor;
import com.timelinesns.comment.CommentQuery.DeleteTarget;
import com.timelinesns.comment.CommentQuery.Row;
import com.timelinesns.common.ApiException;
import com.timelinesns.notification.NotificationService;
import com.timelinesns.post.PostQuery;
import com.timelinesns.user.UserRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * コメント（F-31）・返信（F-32）・コメント削除（F-33）。
 */
@Service
public class CommentService {

    static final int MAX_BODY = 280;

    private final CommentQuery comments;
    private final PostQuery posts;
    private final UserRepository users;
    private final NotificationService notifications;
    private final Clock clock;

    public CommentService(CommentQuery comments, PostQuery posts, UserRepository users,
            NotificationService notifications, Clock clock) {
        this.comments = comments;
        this.notifications = notifications;
        this.posts = posts;
        this.users = users;
        this.clock = clock;
    }

    /**
     * 投稿のコメントを古い順に返す。SQL は投稿の確認とコメント全件の 2 回だけ。
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID postId, @Nullable UUID viewer) {
        ensurePostVisible(postId, viewer);
        return visibleThread(comments.findByPost(postId, viewer));
    }

    /**
     * 閲覧者に見せるコメントだけ残す。{@code rows} は親が子より前に並んでいること（{@link CommentQuery#findByPost}）。
     * <ul>
     *   <li>凍結ユーザー・ブロック関係にあるユーザーのコメントは、その返信ごと除く</li>
     *   <li>削除済みのコメントは、見せる返信が残っているときだけ「削除されました」として残す</li>
     * </ul>
     */
    static List<CommentResponse> visibleThread(List<Row> rows) {
        Set<UUID> excluded = new HashSet<>();
        for (Row row : rows) {
            if (row.hidden() || (row.parentId() != null && excluded.contains(row.parentId()))) {
                excluded.add(row.id());
            }
        }
        // 子から親へさかのぼるので後ろから見る
        Set<UUID> kept = new HashSet<>();
        Set<UUID> hasKeptReply = new HashSet<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (excluded.contains(row.id()) || (row.removed() && !hasKeptReply.contains(row.id()))) {
                continue;
            }
            kept.add(row.id());
            if (row.parentId() != null) {
                hasKeptReply.add(row.parentId());
            }
        }
        return rows.stream().filter(row -> kept.contains(row.id())).map(Row::toResponse).toList();
    }

    @Transactional
    public CommentResponse create(UUID postId, UUID userId, CreateCommentRequest request) {
        // トークンは有効でも、発行後に退会したユーザーは存在しない
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません");
        }
        ensurePostVisible(postId, userId);
        UUID parentId = request.parentId();
        if (parentId != null) {
            // 別の投稿のコメント・削除済み・見えない（凍結・ブロック関係。F-60 で返信もできない）コメントには返信できない
            comments.findById(parentId, userId)
                    .filter(parent -> parent.postId().equals(postId) && !parent.removed() && !parent.hidden())
                    .orElseThrow(CommentService::notFound);
        }
        UUID id = comments.insert(postId, userId, parentId, request.body().strip());
        // 投稿へのコメントは投稿者へ、返信は返信先のコメントを書いた人へ通知する（F-50）
        if (parentId == null) {
            notifications.commented(postId, id, userId);
        } else {
            notifications.replied(postId, id, parentId, userId);
        }
        return comments.findById(id, userId).orElseThrow(CommentService::notFound).toResponse();
    }

    /**
     * 本人のコメントだけ削除できる。返信が付いていれば論理削除し、なければ物理削除する。
     * 物理削除で、削除済みの親に返信が 1 件も残らなくなるなら、親もさかのぼって消す。
     */
    @Transactional
    public void delete(UUID commentId, UUID userId) {
        DeleteTarget target = comments.findDeleteTarget(commentId).orElseThrow(CommentService::notFound);
        if (!userId.equals(target.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "この操作を行う権限がありません");
        }
        if (target.hasReplies()) {
            comments.markDeleted(commentId, clock.instant());
            return;
        }
        UUID top = commentId;
        if (target.parentId() != null) {
            for (Ancestor ancestor : comments.findAncestors(target.parentId())) {
                // 削除済みで、返信がいま消す系統の 1 件だけなら一緒に消す
                if (!ancestor.removed() || ancestor.replies() != 1) {
                    break;
                }
                top = ancestor.id();
            }
        }
        // 一番上を消せば、その下の系統は ON DELETE CASCADE で消える
        comments.delete(top);
    }

    private void ensurePostVisible(UUID postId, @Nullable UUID viewer) {
        if (posts.findVisible(postId, viewer).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "この投稿は存在しないか、表示できません");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "このコメントは存在しないか、表示できません");
    }
}
