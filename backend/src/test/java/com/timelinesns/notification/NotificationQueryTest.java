package com.timelinesns.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.timelinesns.post.PostResponse;
import com.timelinesns.support.DataAccessTest;
import com.timelinesns.support.Fixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 通知一覧・未読数・既読化の SQL（NotificationQuery）。DB に入れた通知を、実際の SQL で読み出して確かめる。
 */
@DataAccessTest
class NotificationQueryTest {

    @Autowired
    private NotificationQuery query;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixtures fixtures;
    private UUID taro;
    private UUID hanako;
    private UUID postId;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(jdbc);
        taro = fixtures.user("taro", "太郎");
        hanako = fixtures.user("hanako", "花子");
        postId = fixtures.post(taro, "投稿");
    }

    private static List<String> types(List<NotificationResponse> notifications) {
        return notifications.stream().map(NotificationResponse::type).toList();
    }

    @Test
    void listsNotificationsNewestFirstWithActorPostAndComment() {
        fixtures.image(postId, 0, "posts/1.jpg", "posts/1-t.jpg", 100, 100);
        fixtures.avatar(hanako, "avatars/hanako.jpg");
        UUID commentId = fixtures.comment(hanako, postId, null, "いいですね");
        fixtures.notification(taro, hanako, "LIKE", postId, null);
        UUID comment = fixtures.notification(taro, hanako, "COMMENT", postId, commentId);
        fixtures.notification(taro, hanako, "FOLLOW", null, null);

        List<NotificationResponse> list = query.list(taro, null, 10);

        assertThat(types(list)).containsExactly("FOLLOW", "COMMENT", "LIKE");
        NotificationResponse commented = list.get(1);
        assertThat(commented.id()).isEqualTo(comment);
        assertThat(commented.unread()).isTrue();
        assertThat(commented.actor()).isEqualTo(
                new PostResponse.Author(hanako, "hanako", "花子", "/api/media/avatars/hanako.jpg"));
        assertThat(commented.post()).isEqualTo(
                new NotificationResponse.Post(postId, "投稿", "/api/media/posts/1-t.jpg"));
        assertThat(commented.comment()).isEqualTo(new NotificationResponse.Comment(commentId, "いいですね"));
        // フォローの通知には投稿もコメントもない
        assertThat(list.getFirst().post()).isNull();
        assertThat(list.getFirst().comment()).isNull();
    }

    @Test
    void listsOnlyRecipientsOwnNotifications() {
        fixtures.notification(taro, hanako, "FOLLOW", null, null);
        fixtures.notification(hanako, taro, "FOLLOW", null, null);

        assertThat(query.list(taro, null, 10)).extracting(n -> n.actor().id()).containsExactly(hanako);
    }

    @Test
    void hidesBodyOfDeletedComment() {
        UUID commentId = fixtures.comment(hanako, postId, null, "消す");
        fixtures.notification(taro, hanako, "COMMENT", postId, commentId);
        fixtures.markCommentDeleted(commentId);

        assertThat(query.list(taro, null, 10).getFirst().comment())
                .isEqualTo(new NotificationResponse.Comment(commentId, null));
    }

    @Test
    void deletesNotificationsWithPost() {
        fixtures.notification(taro, hanako, "LIKE", postId, null);

        jdbc.update("DELETE FROM posts WHERE id = ?", postId);

        assertThat(query.list(taro, null, 10)).isEmpty();
    }

    @Test
    void excludesSuspendedAndBlockedActors() {
        UUID jiro = fixtures.user("jiro");
        UUID saburo = fixtures.user("saburo");
        fixtures.notification(taro, jiro, "FOLLOW", null, null);
        fixtures.notification(taro, saburo, "FOLLOW", null, null);
        fixtures.notification(taro, hanako, "FOLLOW", null, null);
        fixtures.suspend(jiro);
        fixtures.block(taro, saburo);

        assertThat(query.list(taro, null, 10)).extracting(n -> n.actor().id()).containsExactly(hanako);
        assertThat(query.unreadCount(taro)).isEqualTo(1);
    }

    @Test
    void pagesWithCursor() {
        fixtures.notification(taro, hanako, "LIKE", postId, null);
        fixtures.notification(taro, hanako, "COMMENT", postId, null);
        fixtures.notification(taro, hanako, "FOLLOW", null, null);

        List<NotificationResponse> first = query.list(taro, null, 2);
        List<NotificationResponse> second = query.list(taro, first.getLast().id(), 2);

        assertThat(types(first)).containsExactly("FOLLOW", "COMMENT");
        assertThat(types(second)).containsExactly("LIKE");
    }

    @Test
    void countsUnreadUpToMax() {
        for (int i = 0; i < NotificationQuery.MAX_UNREAD + 1; i++) {
            fixtures.notification(taro, hanako, "FOLLOW", null, null);
        }

        assertThat(query.unreadCount(taro)).isEqualTo(NotificationQuery.MAX_UNREAD);
    }

    @Test
    void markReadWithUntilLeavesNewerOnesUnread() {
        fixtures.notification(taro, hanako, "LIKE", postId, null);
        UUID shown = fixtures.notification(taro, hanako, "COMMENT", postId, null);
        // 一覧を開いたあとに届いた通知
        fixtures.notification(taro, hanako, "FOLLOW", null, null);
        fixtures.notification(hanako, taro, "FOLLOW", null, null);

        query.markRead(taro, shown);

        assertThat(query.list(taro, null, 10))
                .extracting(NotificationResponse::type, NotificationResponse::unread)
                .containsExactly(tuple("FOLLOW", true), tuple("COMMENT", false), tuple("LIKE", false));
        assertThat(query.unreadCount(taro)).isEqualTo(1);
        // 他の人の通知には触らない
        assertThat(query.unreadCount(hanako)).isEqualTo(1);
    }

    @Test
    void markReadWithoutUntilReadsAll() {
        fixtures.notification(taro, hanako, "LIKE", postId, null);
        fixtures.notification(taro, hanako, "FOLLOW", null, null);

        query.markRead(taro, null);

        assertThat(query.unreadCount(taro)).isZero();
        assertThat(query.list(taro, null, 10)).noneMatch(NotificationResponse::unread);
    }
}
