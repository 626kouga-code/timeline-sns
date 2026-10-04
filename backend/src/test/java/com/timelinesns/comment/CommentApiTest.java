package com.timelinesns.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.support.QueryCounter;
import com.timelinesns.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * コメント（F-31）・返信（F-32）・コメント削除（F-33）の結合テスト。
 */
@IntegrationTest
class CommentApiTest {

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
        post = fixtures.post(alice, "hello");
    }

    @Nested
    class Create {

        @Test
        void returns201WithCommentAndCountsIt() throws Exception {
            create(bob, "  いいですね\n", null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.body").value("いいですね"))
                    .andExpect(jsonPath("$.parentId").value(nullValue()))
                    .andExpect(jsonPath("$.author.handle").value("bob"))
                    .andExpect(jsonPath("$.deleted").value(false));

            mvc.perform(get("/api/posts/{id}", post)).andExpect(jsonPath("$.commentCount").value(1));
        }

        @Test
        void rejectsBlankAndTooLongBody() throws Exception {
            create(bob, "  ", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").value("本文を入力してください"));
            create(bob, "あ".repeat(281), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").value("本文は 280 文字以内にしてください"));
            create(bob, "😀".repeat(280), null).andExpect(status().isCreated());
        }

        @Test
        void requiresLogin() throws Exception {
            mvc.perform(post("/api/posts/{id}/comments", post)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void repliesToCommentAtAnyDepth() throws Exception {
            UUID root = id(create(bob, "root", null));
            UUID reply = id(create(carol, "reply", root));
            create(alice, "reply to reply", reply)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.parentId").value(reply.toString()));
        }

        @Test
        void cannotReplyToCommentOfAnotherPostOrDeletedComment() throws Exception {
            UUID otherPost = fixtures.post(alice, "other");
            UUID foreign = fixtures.comment(bob, otherPost, null, "foreign");
            create(carol, "x", foreign).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));

            UUID deleted = fixtures.comment(bob, post, null, "deleted");
            fixtures.markCommentDeleted(deleted);
            create(carol, "x", deleted).andExpect(status().isNotFound());

            create(carol, "x", UUID.randomUUID()).andExpect(status().isNotFound());
        }

        // ブロック関係にある相手の投稿へのコメント、相手のコメントへの返信はできない（F-60）
        @Test
        void cannotCommentOrReplyAcrossBlocks() throws Exception {
            UUID carolComment = fixtures.comment(carol, post, null, "carol");
            fixtures.block(carol, bob);
            create(bob, "x", carolComment).andExpect(status().isNotFound());

            fixtures.block(alice, bob);
            create(bob, "x", null).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }
    }

    @Nested
    class ListComments {

        @Test
        void guestSeesCommentsOldestFirstWithParents() throws Exception {
            UUID first = fixtures.comment(bob, post, null, "first");
            UUID reply = fixtures.comment(carol, post, first, "reply");
            fixtures.comment(alice, post, null, "second");

            mvc.perform(get("/api/posts/{id}/comments", post))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].body").value("first"))
                    .andExpect(jsonPath("$[0].author.displayName").value("BOB"))
                    .andExpect(jsonPath("$[1].id").value(reply.toString()))
                    .andExpect(jsonPath("$[1].parentId").value(first.toString()))
                    .andExpect(jsonPath("$[2].body").value("second"));
        }

        @Test
        void returns404ForInvisiblePost() throws Exception {
            mvc.perform(get("/api/posts/{id}/comments", UUID.randomUUID())).andExpect(status().isNotFound());
            fixtures.suspend(alice);
            mvc.perform(get("/api/posts/{id}/comments", post)).andExpect(status().isNotFound());
        }

        // ブロック関係・凍結のユーザーのコメントは、付いた返信ごと見せない
        @Test
        void hidesCommentsOfBlockedOrSuspendedUsersWithTheirReplies() throws Exception {
            UUID bobComment = fixtures.comment(bob, post, null, "bob");
            fixtures.comment(alice, post, bobComment, "reply to bob");
            UUID carolComment = fixtures.comment(carol, post, null, "carol");
            fixtures.comment(alice, post, carolComment, "reply to carol");
            fixtures.block(alice, bob);
            fixtures.suspend(carol);

            mvc.perform(asUser(get("/api/posts/{id}/comments", post), alice))
                    .andExpect(jsonPath("$.length()").value(0));
            // ゲストにはブロックは関係ないが、凍結は関係する
            mvc.perform(get("/api/posts/{id}/comments", post))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].body").value("bob"));
        }

        @Test
        void deletedCommentShowsOnlyWhileItHasVisibleReplies() throws Exception {
            UUID withReply = fixtures.comment(bob, post, null, "secret");
            fixtures.comment(carol, post, withReply, "reply");
            fixtures.markCommentDeleted(withReply);
            UUID withoutReply = fixtures.comment(bob, post, null, "lonely");
            fixtures.markCommentDeleted(withoutReply);

            mvc.perform(get("/api/posts/{id}/comments", post))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].deleted").value(true))
                    .andExpect(jsonPath("$[0].body").value(nullValue()))
                    .andExpect(jsonPath("$[0].author").value(nullValue()))
                    .andExpect(jsonPath("$[1].body").value("reply"));
        }
    }

    @Nested
    class Delete {

        @Test
        void commentWithoutRepliesIsRemoved() throws Exception {
            UUID comment = fixtures.comment(bob, post, null, "bye");
            remove(bob, comment).andExpect(status().isNoContent());
            assertThat(commentCount()).isZero();
        }

        @Test
        void commentWithRepliesIsSoftDeleted() throws Exception {
            UUID comment = fixtures.comment(bob, post, null, "secret");
            fixtures.comment(carol, post, comment, "reply");

            remove(bob, comment).andExpect(status().isNoContent());

            mvc.perform(get("/api/posts/{id}/comments", post))
                    .andExpect(jsonPath("$[0].deleted").value(true))
                    .andExpect(jsonPath("$[0].body").value(nullValue()));
            assertThat(jdbc.queryForObject("SELECT body FROM comments WHERE id = ?", String.class, comment)).isEmpty();
            // 投稿のコメント数は削除済みを数えない
            mvc.perform(get("/api/posts/{id}", post)).andExpect(jsonPath("$.commentCount").value(1));
        }

        // 最後の返信を消すと、返信が残らなくなった削除済みの祖先もまとめて消える
        @Test
        void removingLastReplyAlsoRemovesDeletedAncestors() throws Exception {
            List<UUID> chain = fixtures.chain(bob, post, 4);
            UUID sibling = fixtures.comment(carol, post, chain.get(0), "sibling");
            fixtures.markCommentDeleted(chain.get(1));
            fixtures.markCommentDeleted(chain.get(2));

            remove(bob, chain.get(3)).andExpect(status().isNoContent());

            // chain[0] は sibling が残っているので残る（削除されていないので本文も残る）
            assertThat(jdbc.queryForList("SELECT id FROM comments ORDER BY created_at", UUID.class))
                    .containsExactly(chain.get(0), sibling);
        }

        @Test
        void othersCannotDelete() throws Exception {
            UUID comment = fixtures.comment(bob, post, null, "mine");
            // 投稿者でも他人のコメントは消せない
            remove(alice, comment).andExpect(status().isForbidden());
            assertThat(commentCount()).isOne();
        }

        @Test
        void returns404ForMissingOrAlreadyDeletedComment() throws Exception {
            remove(bob, UUID.randomUUID()).andExpect(status().isNotFound());
            UUID comment = fixtures.comment(bob, post, null, "x");
            fixtures.comment(carol, post, comment, "reply");
            fixtures.markCommentDeleted(comment);
            remove(bob, comment).andExpect(status().isNotFound());
        }

        @Test
        void deletingPostRemovesItsComments() throws Exception {
            fixtures.chain(bob, post, 3);
            mvc.perform(delete("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(status().isNoContent());
            assertThat(commentCount()).isZero();
        }
    }

    /**
     * N+1 の回帰テスト。コメントの件数や返信の深さが増えても、実行する SQL の数は変わらない。
     */
    @Nested
    class QueryCount {

        @Test
        void listUsesSameNumberOfQueriesRegardlessOfComments() throws Exception {
            fixtures.comment(bob, post, null, "only");
            long one = QueryCounter.count(() -> listAs(carol));

            UUID other = fixtures.post(alice, "busy");
            UUID root = fixtures.comment(bob, other, null, "root");
            for (int i = 0; i < 20; i++) {
                fixtures.comment(i % 2 == 0 ? bob : carol, other, root, "reply " + i);
            }
            fixtures.chain(carol, other, 30);
            long many = QueryCounter.count(() -> mvc.perform(asUser(get("/api/posts/{id}/comments", other), carol))
                    .andExpect(jsonPath("$.length()").value(51)));

            assertThat(one).isPositive();
            assertThat(many).isEqualTo(one);
        }

        @Test
        void deleteUsesSameNumberOfQueriesRegardlessOfDepth() throws Exception {
            List<UUID> shallow = fixtures.chain(bob, post, 2);
            fixtures.markCommentDeleted(shallow.get(0));
            long one = QueryCounter.count(() -> remove(bob, shallow.get(1)).andExpect(status().isNoContent()));

            List<UUID> deep = fixtures.chain(bob, post, 40);
            deep.subList(0, 39).forEach(fixtures::markCommentDeleted);
            long many = QueryCounter.count(() -> remove(bob, deep.get(39)).andExpect(status().isNoContent()));

            assertThat(one).isPositive();
            assertThat(many).isEqualTo(one);
            assertThat(commentCount()).isZero();
        }

        @Test
        void timelineUsesSameNumberOfQueriesRegardlessOfLikesAndComments() throws Exception {
            long one = QueryCounter.count(() -> timelineAs(carol));
            for (int i = 0; i < 20; i++) {
                UUID p = fixtures.post(alice, "post " + i);
                fixtures.like(bob, p);
                fixtures.like(carol, p);
                fixtures.chain(bob, p, 3);
            }
            long many = QueryCounter.count(() -> timelineAs(carol));
            assertThat(one).isPositive();
            assertThat(many).isEqualTo(one);
        }

        private void listAs(UUID userId) throws Exception {
            mvc.perform(asUser(get("/api/posts/{id}/comments", post), userId)).andExpect(status().isOk());
        }

        private void timelineAs(UUID userId) throws Exception {
            mvc.perform(asUser(get("/api/timeline/global"), userId)).andExpect(status().isOk());
        }
    }

    private ResultActions create(UUID userId, String body, @Nullable UUID parentId) throws Exception {
        String json = parentId == null
                ? "{\"body\":%s}".formatted(quote(body))
                : "{\"body\":%s,\"parentId\":\"%s\"}".formatted(quote(body), parentId);
        return mvc.perform(asUser(post("/api/posts/{id}/comments", post), userId)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions remove(UUID userId, UUID commentId) throws Exception {
        return mvc.perform(asUser(delete("/api/comments/{id}", commentId), userId));
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, UUID userId) {
        return request.header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private long commentCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM comments", Long.class);
        return count == null ? 0 : count;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
