package com.timelinesns.post;

import com.timelinesns.common.ApiException;
import com.timelinesns.user.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の作成（F-10）・取得（F-13）・削除（F-12）。
 */
@Service
public class PostService {

    static final int MAX_BODY = 280;
    static final int MAX_IMAGES = 4;

    private final PostRepository posts;
    private final PostQuery query;
    private final PostImageStore images;
    private final UserRepository users;
    private final Clock clock;

    public PostService(PostRepository posts, PostQuery query, PostImageStore images, UserRepository users,
            Clock clock) {
        this.posts = posts;
        this.query = query;
        this.images = images;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public PostResponse create(UUID userId, CreatePostRequest request) {
        // トークンは有効でも、発行後に退会したユーザーは存在しない
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません");
        }
        String body = request.strippedBody();
        List<MultipartFile> files = request.files();
        // 画像があれば本文なしでも投稿できる（F-10）
        if (body.isEmpty() && files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "本文を入力するか、画像を選んでください",
                    "body");
        }
        // 形式違反などは、投稿も画像も保存する前に弾く
        List<PostImageStore.Prepared> prepared = images.prepare(files);

        // 表示用の取得は SQL で行うので、先に書き込んでおく
        Post post = posts.saveAndFlush(new Post(userId, body, clock.instant()));
        images.save(post.getId(), prepared);
        return query.findVisible(post.getId(), userId).orElseThrow(PostService::notFound);
    }

    @Transactional(readOnly = true)
    public PostResponse get(UUID postId, @Nullable UUID viewer) {
        return query.findVisible(postId, viewer).orElseThrow(PostService::notFound);
    }

    /**
     * 本人の投稿だけ削除できる。画像・いいね・コメント・通知の行は DB の ON DELETE CASCADE で一緒に消え、
     * 保存先の画像ファイルは削除が確定してから消す。
     */
    @Transactional
    public void delete(UUID postId, UUID userId) {
        Post post = posts.findById(postId).orElseThrow(PostService::notFound);
        if (!post.isWrittenBy(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "この操作を行う権限がありません");
        }
        images.deleteAfterCommit(postId);
        posts.delete(post);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "この投稿は存在しないか、表示できません");
    }
}
