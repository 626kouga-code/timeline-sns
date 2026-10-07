package com.timelinesns.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.support.QueryCounter;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 通知一覧（F-50）・未読数と既読化（F-51）の結合テスト。通知は実際の API（いいね・コメント・フォロー）から作る。
 */
@IntegrationTest
class NotificationApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccessTokenService accessTokens;

    private Fixtures fixtures;
    private UUID alice;
    private UUID bob;
    private UUID carol;
    private UUID post;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
        carol = fixtures.user("carol");
        post = fixtures.post(alice, "alice の投稿");
    }

    @Nested
    class Creation {

        @Test
        void likeCommentReplyAndFollowNotifyTheRightPeopleNewestFirst() throws Exception {
            like(bob, post);
            UUID comment = comment(bob, post, null, "いいですね");
            comment(alice, post, comment, "ありがとう");
            follow(carol, "alice");

            // alice: いいね・コメント（bob）とフォロー（carol）。自分の返信は通知されない
            list(alice)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].type").value(contains("FOLLOW", "COMMENT", "LIKE")))
                    .andExpect(jsonPath("$.items[0].actor.handle").value("carol"))
                    .andExpect(jsonPath("$.items[0].post").value(nullValue()))
                    .andExpect(jsonPath("$.items[1].comment.body").value("いいですね"))
                    .andExpect(jsonPath("$.items[1].post.body").value("alice の投稿"))
                    .andExpect(jsonPath("$.items[2].actor.handle").value("bob"))
                    .andExpect(jsonPath("$.items[2].comment").value(nullValue()))
                    .andExpect(jsonPath("$.items[*].unread").value(contains(true, true, true)));
            // bob: 自分のコメントへの返信
            list(bob)
                    .andExpect(jsonPath("$.items[*].type").value(contains("REPLY")))
                    .andExpect(jsonPath("$.items[0].comment.body").value("ありがとう"))
                    .andExpect(jsonPath("$.items[0].actor.handle").value("alice"));
        }

        @Test
        void ownActionsDoNotNotify() throws Exception {
            like(alice, post);
            comment(alice, post, null, "自分でコメント");
            list(alice).andExpect(jsonPath("$.items.length()").value(0));
        }

        // いいねを付け直しても通知は 1 件のまま。取り消すと消える
        @Test
        void unlikeAndUnfollowRemoveNotifications() throws Exception {
            like(bob, post);
            like(bob, post);
            follow(bob, "alice");
            list(alice).andExpect(jsonPath("$.items.length()").value(2));

            mvc.perform(asUser(delete("/api/posts/{id}/likes", post), bob)).andExpect(status().isOk());
            mvc.perform(asUser(delete("/api/users/{handle}/follow", "alice"), bob)).andExpect(status().isOk());
            list(alice).andExpect(jsonPath("$.items.length()").value(0));

            like(bob, post);
            list(alice).andExpect(jsonPath("$.items[*].type").value(contains("LIKE")));
        }

        // 削除された投稿・コメントの通知は一緒に消える。論理削除のコメントは本文を返さない
        @Test
        void followsDeletionOfPostsAndComments() throws Exception {
            UUID comment = comment(bob, post, null, "消すコメント");
            comment(carol, post, comment, "返信");
            mvc.perform(asUser(delete("/api/comments/{id}", comment), bob)).andExpect(status().isNoContent());

            list(alice)
                    .andExpect(jsonPath("$.items[?(@.type == 'COMMENT')].comment.body").value(contains(nullValue())));

            mvc.perform(asUser(delete("/api/posts/{id}", post), alice)).andExpect(status().isNoContent());
            list(alice).andExpect(jsonPath("$.items.length()").value(0));
        }
    }

    @Nested
    class Listing {

        @Test
        void includesThumbnailOfFirstImage() throws Exception {
            jdbc.update("INSERT INTO post_images (post_id, original_key, thumbnail_key, width, height, sort_order) "
                    + "VALUES (?, 'posts/x/1.jpg', 'posts/x/1-thumb.jpg', 10, 10, 0)", post);
            like(bob, post);
            list(alice).andExpect(jsonPath("$.items[0].post.thumbnailUrl").value("/api/media/posts/x/1-thumb.jpg"));
        }

        @Test
        void pagesWithCursor() throws Exception {
            for (int i = 0; i < 3; i++) {
                comment(bob, post, null, "コメント " + i);
            }
            ResultActions page1 = mvc.perform(asUser(get("/api/notifications").param("limit", "2"), alice))
                    .andExpect(jsonPath("$.items[*].comment.body").value(contains("コメント 2", "コメント 1")));
            String cursor = JsonPath.read(page1.andReturn().getResponse().getContentAsString(), "$.nextCursor");

            mvc.perform(asUser(get("/api/notifications").param("limit", "2").param("cursor", cursor), alice))
                    .andExpect(jsonPath("$.items[*].comment.body").value(contains("コメント 0")))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        // 凍結されたユーザー・ブロック関係にあるユーザーからの通知は、一覧にも未読数にも出さない
        @Test
        void hidesNotificationsFromSuspendedAndBlockedUsers() throws Exception {
            like(bob, post);
            follow(carol, "alice");
            UUID dave = fixtures.user("dave");
            like(dave, post);
            fixtures.suspend(bob);
            fixtures.block(carol, alice);

            list(alice).andExpect(jsonPath("$.items[*].actor.handle").value(contains("dave")));
            unreadCount(alice).andExpect(jsonPath("$.count").value(1));
        }

        @Test
        void requiresLogin() throws Exception {
            mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/notifications/read")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Read {

        @Test
        void markReadClearsUnreadCount() throws Exception {
            like(bob, post);
            follow(bob, "alice");
            unreadCount(alice).andExpect(jsonPath("$.count").value(2));

            mvc.perform(asUser(post("/api/notifications/read"), alice)).andExpect(status().isNoContent());

            unreadCount(alice).andExpect(jsonPath("$.count").value(0));
            list(alice).andExpect(jsonPath("$.items[*].unread").value(contains(false, false)));
        }

        // 画面に表示した分（until 以前）だけを既読にし、その後に届いた通知は未読のまま残す
        @Test
        void markReadUntilKeepsNewerUnread() throws Exception {
            like(bob, post);
            String shown = JsonPath.read(list(alice).andReturn().getResponse().getContentAsString(), "$.items[0].id");
            follow(carol, "alice");

            mvc.perform(asUser(post("/api/notifications/read"), alice)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"until\":\"" + shown + "\"}"))
                    .andExpect(status().isNoContent());

            unreadCount(alice).andExpect(jsonPath("$.count").value(1));
            list(alice).andExpect(jsonPath("$.items[*].unread").value(contains(true, false)));
        }

        // 他の人の通知は既読にならない
        @Test
        void onlyAffectsOwnNotifications() throws Exception {
            like(bob, post);
            UUID bobPost = fixtures.post(bob, "bob の投稿");
            like(alice, bobPost);

            mvc.perform(asUser(post("/api/notifications/read"), alice)).andExpect(status().isNoContent());
            unreadCount(bob).andExpect(jsonPath("$.count").value(1));
        }
    }

    // N+1 の回帰テスト。通知の件数・種類が増えても、一覧の SQL の数は変わらない
    @Test
    void listUsesSameNumberOfQueriesRegardlessOfNotifications() throws Exception {
        like(bob, post);
        long one = QueryCounter.count(() -> list(alice).andExpect(jsonPath("$.items.length()").value(1)));

        for (int i = 0; i < 10; i++) {
            UUID user = fixtures.user("user" + i);
            UUID comment = comment(user, post, null, "コメント " + i);
            comment(user, post, comment, "自分への返信");
            follow(user, "alice");
        }
        long many = QueryCounter.count(() -> list(alice).andExpect(jsonPath("$.items.length()").value(20)));

        assertThat(one).isPositive();
        assertThat(many).isEqualTo(one);
    }

    private ResultActions list(UUID userId) throws Exception {
        return mvc.perform(asUser(get("/api/notifications"), userId));
    }

    private ResultActions unreadCount(UUID userId) throws Exception {
        return mvc.perform(asUser(get("/api/notifications/unread-count"), userId));
    }

    private void like(UUID userId, UUID postId) throws Exception {
        mvc.perform(asUser(post("/api/posts/{id}/likes", postId), userId)).andExpect(status().isOk());
    }

    private void follow(UUID userId, String handle) throws Exception {
        mvc.perform(asUser(put("/api/users/{handle}/follow", handle), userId)).andExpect(status().isOk());
    }

    private UUID comment(UUID userId, UUID postId, UUID parentId, String body) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        String json = mvc.perform(asUser(post("/api/posts/{id}/comments", postId), userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"" + parent + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, UUID userId) {
        return request.header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId));
    }
}
