package com.timelinesns.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelinesns.common.ApiException;
import com.timelinesns.comment.CommentQuery.Ancestor;
import com.timelinesns.comment.CommentQuery.DeleteTarget;
import com.timelinesns.comment.CommentQuery.Row;
import com.timelinesns.notification.NotificationService;
import com.timelinesns.post.PostQuery;
import com.timelinesns.post.PostResponse;
import com.timelinesns.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * コメント・返信・削除のロジック（CommentService）。SQL（CommentQuery）はモックにし、判断と呼び出しを確かめる。
 */
class CommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID ME = UUID.randomUUID();
    private static final UUID POST = UUID.randomUUID();
    private static final PostResponse.Author AUTHOR = new PostResponse.Author(ME, "taro", "太郎", null);

    private CommentQuery comments;
    private PostQuery posts;
    private UserRepository users;
    private NotificationService notifications;
    private CommentService service;

    @BeforeEach
    void setUp() {
        comments = mock(CommentQuery.class);
        posts = mock(PostQuery.class);
        users = mock(UserRepository.class);
        notifications = mock(NotificationService.class);
        service = new CommentService(comments, posts, users, notifications, Clock.fixed(NOW, ZoneOffset.UTC));
        when(users.existsById(ME)).thenReturn(true);
        PostResponse post = new PostResponse(POST, "投稿", List.of(), NOW, AUTHOR, 0, 0, false);
        when(posts.findVisible(any(), any())).thenReturn(Optional.empty());
        when(posts.findVisible(POST, ME)).thenReturn(Optional.of(post));
        when(posts.findVisible(POST, null)).thenReturn(Optional.of(post));
    }

    private static Row row(UUID id, @Nullable UUID parentId, boolean removed, boolean hidden) {
        return new Row(id, POST, parentId, removed ? "" : "本文", NOW, AUTHOR, removed, hidden);
    }

    private static void assertApiError(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    /**
     * 閲覧者に見せるコメントの選別（visibleThread）。行は親が子より前に並ぶ。
     */
    @Nested
    class VisibleThread {

        private final List<UUID> ids = new ArrayList<>();

        private UUID id(int i) {
            while (ids.size() <= i) {
                ids.add(UUID.randomUUID());
            }
            return ids.get(i);
        }

        private List<UUID> visible(Row... rows) {
            return CommentService.visibleThread(List.of(rows)).stream().map(CommentResponse::id).toList();
        }

        @Test
        void keepsOrdinaryCommentsInOrder() {
            assertThat(visible(row(id(0), null, false, false), row(id(1), id(0), false, false),
                    row(id(2), null, false, false)))
                    .containsExactly(id(0), id(1), id(2));
        }

        @Test
        void hidesHiddenCommentWithAllItsReplies() {
            assertThat(visible(row(id(0), null, false, true), row(id(1), id(0), false, false),
                    row(id(2), id(1), false, false), row(id(3), null, false, false)))
                    .containsExactly(id(3));
        }

        @Test
        void keepsRemovedCommentOnlyWhenItHasVisibleReply() {
            assertThat(visible(row(id(0), null, true, false), row(id(1), id(0), false, false),
                    row(id(2), null, true, false)))
                    .containsExactly(id(0), id(1));
        }

        @Test
        void dropsRemovedCommentWhoseRepliesAreAllHidden() {
            assertThat(visible(row(id(0), null, true, false), row(id(1), id(0), false, true))).isEmpty();
        }

        @Test
        void keepsChainOfRemovedAncestorsAboveVisibleReply() {
            assertThat(visible(row(id(0), null, true, false), row(id(1), id(0), true, false),
                    row(id(2), id(1), false, false)))
                    .containsExactly(id(0), id(1), id(2));
        }

        @Test
        void removedCommentHasNoBodyOrAuthor() {
            List<CommentResponse> thread = CommentService.visibleThread(
                    List.of(row(id(0), null, true, false), row(id(1), id(0), false, false)));

            assertThat(thread.getFirst().body()).isNull();
            assertThat(thread.getFirst().author()).isNull();
            assertThat(thread.getFirst().deleted()).isTrue();
            assertThat(thread.get(1).body()).isEqualTo("本文");
        }
    }

    @Nested
    class Create {

        private final UUID newId = UUID.randomUUID();

        @BeforeEach
        void inserted() {
            when(comments.insert(any(), any(), any(), anyString())).thenReturn(newId);
            when(comments.findById(newId, ME)).thenReturn(Optional.of(row(newId, null, false, false)));
        }

        @Test
        void topLevelCommentNotifiesPostAuthor() {
            CommentResponse created = service.create(POST, ME, new CreateCommentRequest("  いいね  ", null));

            assertThat(created.id()).isEqualTo(newId);
            verify(comments).insert(POST, ME, null, "いいね");
            verify(notifications).commented(POST, newId, ME);
            verify(notifications, never()).replied(any(), any(), any(), any());
        }

        @Test
        void replyNotifiesParentAuthor() {
            UUID parent = UUID.randomUUID();
            when(comments.findById(parent, ME)).thenReturn(Optional.of(row(parent, null, false, false)));

            service.create(POST, ME, new CreateCommentRequest("返信", parent));

            verify(comments).insert(POST, ME, parent, "返信");
            verify(notifications).replied(POST, newId, parent, ME);
            verify(notifications, never()).commented(any(), any(), any());
        }

        @Test
        void cannotReplyToCommentOfAnotherPost() {
            UUID parent = UUID.randomUUID();
            when(comments.findById(parent, ME)).thenReturn(Optional.of(
                    new Row(parent, UUID.randomUUID(), null, "別の投稿", NOW, AUTHOR, false, false)));

            assertApiError(() -> service.create(POST, ME, new CreateCommentRequest("返信", parent)),
                    HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND");
            verify(comments, never()).insert(any(), any(), any(), anyString());
        }

        @Test
        void cannotReplyToRemovedOrHiddenComment() {
            UUID removed = UUID.randomUUID();
            UUID hidden = UUID.randomUUID();
            when(comments.findById(removed, ME)).thenReturn(Optional.of(row(removed, null, true, false)));
            when(comments.findById(hidden, ME)).thenReturn(Optional.of(row(hidden, null, false, true)));

            assertApiError(() -> service.create(POST, ME, new CreateCommentRequest("返信", removed)),
                    HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND");
            assertApiError(() -> service.create(POST, ME, new CreateCommentRequest("返信", hidden)),
                    HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND");
            verify(comments, never()).insert(any(), any(), any(), anyString());
        }

        @Test
        void cannotCommentOnInvisiblePost() {
            assertApiError(() -> service.create(UUID.randomUUID(), ME, new CreateCommentRequest("こんにちは", null)),
                    HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
        }

        @Test
        void deletedViewerIsUnauthorized() {
            assertApiError(() -> service.create(POST, UUID.randomUUID(), new CreateCommentRequest("こんにちは", null)),
                    HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND");
        }
    }

    @Test
    void listRejectsInvisiblePost() {
        assertApiError(() -> service.list(UUID.randomUUID(), null), HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
    }

    @Nested
    class Delete {

        private final UUID comment = UUID.randomUUID();

        @Test
        void unknownOrAlreadyDeletedCommentIsNotFound() {
            assertApiError(() -> service.delete(comment, ME), HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND");
        }

        @Test
        void cannotDeleteOthersComment() {
            when(comments.findDeleteTarget(comment))
                    .thenReturn(Optional.of(new DeleteTarget(UUID.randomUUID(), null, false)));

            assertApiError(() -> service.delete(comment, ME), HttpStatus.FORBIDDEN, "FORBIDDEN");
            verify(comments, never()).delete(any());
            verify(comments, never()).markDeleted(any(), any());
        }

        @Test
        void commentWithRepliesIsMarkedDeleted() {
            when(comments.findDeleteTarget(comment)).thenReturn(Optional.of(new DeleteTarget(ME, null, true)));

            service.delete(comment, ME);

            verify(comments).markDeleted(comment, NOW);
            verify(comments, never()).delete(any());
        }

        @Test
        void commentWithoutRepliesIsDeleted() {
            when(comments.findDeleteTarget(comment)).thenReturn(Optional.of(new DeleteTarget(ME, null, false)));

            service.delete(comment, ME);

            verify(comments).delete(comment);
        }

        @Test
        void alsoDeletesRemovedAncestorsLeftWithoutReplies() {
            UUID parent = UUID.randomUUID();
            UUID grandParent = UUID.randomUUID();
            UUID root = UUID.randomUUID();
            when(comments.findDeleteTarget(comment)).thenReturn(Optional.of(new DeleteTarget(ME, parent, false)));
            // 親・祖父は削除済みで、返信はこの系統の 1 件だけ。根元は削除されていない
            when(comments.findAncestors(parent)).thenReturn(List.of(
                    new Ancestor(parent, true, 1), new Ancestor(grandParent, true, 1), new Ancestor(root, false, 1)));

            service.delete(comment, ME);

            verify(comments).delete(grandParent);
        }

        @Test
        void keepsRemovedAncestorThatHasOtherReplies() {
            UUID parent = UUID.randomUUID();
            when(comments.findDeleteTarget(comment)).thenReturn(Optional.of(new DeleteTarget(ME, parent, false)));
            when(comments.findAncestors(parent)).thenReturn(List.of(new Ancestor(parent, true, 2)));

            service.delete(comment, ME);

            verify(comments).delete(comment);
        }
    }
}
