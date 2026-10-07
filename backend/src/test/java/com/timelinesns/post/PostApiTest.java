package com.timelinesns.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 投稿 API（作成 F-10・詳細 F-13・削除 F-12）の結合テスト。
 */
@IntegrationTest
class PostApiTest {

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

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
    }

    @Nested
    class Create {

        @Test
        void returns201WithPostAndStripsSurroundingWhitespace() throws Exception {
            create(alice, "  はじめての投稿\n ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.body").value("はじめての投稿"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.author.handle").value("alice"))
                    .andExpect(jsonPath("$.author.displayName").value("ALICE"))
                    .andExpect(jsonPath("$.author.email").doesNotExist())
                    .andExpect(jsonPath("$.likeCount").value(0))
                    .andExpect(jsonPath("$.commentCount").value(0))
                    .andExpect(jsonPath("$.liked").value(false));

            assertThat(jdbc.queryForObject("SELECT body FROM posts WHERE user_id = ?", String.class, alice))
                    .isEqualTo("はじめての投稿");
        }

        @Test
        void countsBodyLengthInCodePoints() throws Exception {
            create(alice, "😀".repeat(280)).andExpect(status().isCreated());
            create(alice, "😀".repeat(281))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.body").value("本文は 280 文字以内にしてください"));
        }

        // 画像もなく本文も空なら投稿できない（画像だけの投稿は PostImageApiTest）
        @Test
        void rejectsBlankBodyWithoutImages() throws Exception {
            create(alice, "  \n ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").value("本文を入力するか、画像を選んでください"));
            mvc.perform(multipart("/api/posts").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").value("本文を入力するか、画像を選んでください"));
        }

        @Test
        void requiresLogin() throws Exception {
            mvc.perform(multipart("/api/posts").param("body", "hello"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    class Get {

        @Test
        void guestCanViewPostWithCounts() throws Exception {
            UUID post = fixtures.post(alice, "hello");
            fixtures.like(bob, post);
            fixtures.comment(bob, post, false);
            fixtures.comment(bob, post, true);

            mvc.perform(get("/api/posts/{id}", post))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("hello"))
                    .andExpect(jsonPath("$.likeCount").value(1))
                    .andExpect(jsonPath("$.commentCount").value(1))
                    .andExpect(jsonPath("$.liked").value(false));

            mvc.perform(get("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                    .andExpect(jsonPath("$.liked").value(true));
        }

        @Test
        void returns404ForMissingPost() throws Exception {
            mvc.perform(get("/api/posts/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }

        // UUID でない ID は、Spring の英語のエラーではなく「存在しない」と同じ扱いにする
        @Test
        void returns404ForMalformedId() throws Exception {
            mvc.perform(get("/api/posts/{id}", "not-a-uuid"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("指定されたデータは存在しないか、表示できません"));
        }

        @Test
        void hidesPostsOfSuspendedUsers() throws Exception {
            UUID post = fixtures.post(alice, "hello");
            fixtures.suspend(alice);

            mvc.perform(get("/api/posts/{id}", post)).andExpect(status().isNotFound());
        }

        @Test
        void hidesPostsAcrossBlocksInBothDirections() throws Exception {
            UUID post = fixtures.post(alice, "hello");
            UUID carol = fixtures.user("carol");
            fixtures.block(alice, bob);
            fixtures.block(carol, alice);

            mvc.perform(get("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(carol)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/posts/{id}", post)).andExpect(status().isOk());
        }
    }

    @Nested
    class Delete {

        @Test
        void authorCanDeletePostWithLikesAndComments() throws Exception {
            UUID post = fixtures.post(alice, "hello");
            fixtures.like(bob, post);
            fixtures.comment(bob, post, false);

            mvc.perform(delete("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(status().isNoContent());

            assertThat(count("SELECT count(*) FROM posts")).isZero();
            assertThat(count("SELECT count(*) FROM likes")).isZero();
            assertThat(count("SELECT count(*) FROM comments")).isZero();
        }

        @Test
        void othersCannotDelete() throws Exception {
            UUID post = fixtures.post(alice, "hello");

            mvc.perform(delete("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(delete("/api/posts/{id}", post)).andExpect(status().isUnauthorized());

            assertThat(count("SELECT count(*) FROM posts")).isEqualTo(1);
        }

        @Test
        void returns404ForMissingPost() throws Exception {
            mvc.perform(delete("/api/posts/{id}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(status().isNotFound());
        }
    }

    private ResultActions create(UUID userId, String body) throws Exception {
        return mvc.perform(multipart("/api/posts")
                .param("body", body)
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId)));
    }

    private int count(String sql) {
        Integer result = jdbc.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
