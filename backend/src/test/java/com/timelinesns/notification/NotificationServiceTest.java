package com.timelinesns.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.timelinesns.support.DataAccessTest;
import com.timelinesns.support.Fixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 通知を作る・消す（NotificationService）。受け取る人は SQL の中で決めるので、本物の DB に書き込んで、
 * 誰にどの通知が入ったかを読み出して確かめる。
 */
@DataAccessTest
class NotificationServiceTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixtures fixtures;
    private UUID taro;
    private UUID hanako;
    private UUID taroPost;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(jdbc);
        taro = fixtures.user("taro");
        hanako = fixtures.user("hanako");
        taroPost = fixtures.post(taro, "太郎の投稿");
    }

    private List<Map<String, Object>> notifications() {
        return jdbc.queryForList(
                "SELECT recipient_id, actor_id, type, post_id, comment_id FROM notifications ORDER BY id");
    }

    private static Map<String, Object> row(UUID recipient, UUID actor, String type, UUID postId, UUID commentId) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("recipient_id", recipient);
        row.put("actor_id", actor);
        row.put("type", type);
        row.put("post_id", postId);
        row.put("comment_id", commentId);
        return row;
    }

    @Test
    void likedNotifiesPostAuthor() {
        service.liked(taroPost, hanako);

        assertThat(notifications()).containsExactly(row(taro, hanako, "LIKE", taroPost, null));
    }

    @Test
    void likingOwnPostDoesNotNotify() {
        service.liked(taroPost, taro);

        assertThat(notifications()).isEmpty();
    }

    @Test
    void unlikedDeletesOnlyThatLikeNotification() {
        UUID jiro = fixtures.user("jiro");
        service.liked(taroPost, hanako);
        service.liked(taroPost, jiro);
        service.followed(taro, hanako);

        service.unliked(taroPost, hanako);

        assertThat(notifications()).containsExactly(
                row(taro, jiro, "LIKE", taroPost, null), row(taro, hanako, "FOLLOW", null, null));
    }

    @Test
    void commentedNotifiesPostAuthorExceptOwnComment() {
        UUID comment = fixtures.comment(hanako, taroPost, null, "いいね");
        UUID own = fixtures.comment(taro, taroPost, null, "自分で");

        service.commented(taroPost, comment, hanako);
        service.commented(taroPost, own, taro);

        assertThat(notifications()).containsExactly(row(taro, hanako, "COMMENT", taroPost, comment));
    }

    @Test
    void repliedNotifiesParentCommentAuthor() {
        UUID jiro = fixtures.user("jiro");
        UUID parent = fixtures.comment(hanako, taroPost, null, "花子のコメント");
        UUID reply = fixtures.comment(jiro, taroPost, parent, "次郎の返信");

        service.replied(taroPost, reply, parent, jiro);

        // 投稿者（太郎）ではなく、返信先のコメントを書いた花子に届く
        assertThat(notifications()).containsExactly(row(hanako, jiro, "REPLY", taroPost, reply));
    }

    @Test
    void replyingToOwnCommentDoesNotNotify() {
        UUID parent = fixtures.comment(hanako, taroPost, null, "花子のコメント");
        UUID reply = fixtures.comment(hanako, taroPost, parent, "花子の追記");

        service.replied(taroPost, reply, parent, hanako);

        assertThat(notifications()).isEmpty();
    }

    @Test
    void replyingToCommentOfDeletedUserDoesNotNotify() {
        UUID jiro = fixtures.user("jiro");
        UUID parent = fixtures.comment(jiro, taroPost, null, "次郎のコメント");
        jdbc.update("DELETE FROM users WHERE id = ?", jiro);
        UUID reply = fixtures.comment(hanako, taroPost, parent, "返信");

        service.replied(taroPost, reply, parent, hanako);

        assertThat(notifications()).isEmpty();
    }

    @Test
    void followedAndUnfollowed() {
        UUID jiro = fixtures.user("jiro");
        service.followed(taro, hanako);
        service.followed(taro, jiro);

        service.unfollowed(taro, hanako);

        assertThat(notifications()).containsExactly(row(taro, jiro, "FOLLOW", null, null));
    }
}
