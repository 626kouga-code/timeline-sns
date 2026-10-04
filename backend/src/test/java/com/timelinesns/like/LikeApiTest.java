package com.timelinesns.like;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * いいね・取り消し（F-30）の結合テスト。
 */
@IntegrationTest
class LikeApiTest {

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
    private UUID post;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
        post = fixtures.post(alice, "hello");
    }

    @Test
    void likeAndUnlikeReturnStateAndCount() throws Exception {
        like(bob).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        like(alice).andExpect(jsonPath("$.likeCount").value(2));

        mvc.perform(get("/api/posts/{id}", post).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(2));

        unlike(bob).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    // 1 ユーザーにつき 1 投稿 1 回。連打や再送でもエラーにせず状態だけ返す
    @Test
    void likeAndUnlikeAreIdempotent() throws Exception {
        like(bob);
        like(bob).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        unlike(bob);
        unlike(bob).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(post("/api/posts/{id}/likes", post)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/posts/{id}/likes", post)).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotLikeMissingPost() throws Exception {
        mvc.perform(post("/api/posts/{id}/likes", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ブロック関係にある相手の投稿にはいいねできない（F-60）。どちらがブロックしていても同じ
    @Test
    void cannotLikeAcrossBlocks() throws Exception {
        fixtures.block(alice, bob);
        like(bob).andExpect(status().isNotFound());

        UUID bobPost = fixtures.post(bob, "bob");
        mvc.perform(post("/api/posts/{id}/likes", bobPost).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotLikePostOfSuspendedUser() throws Exception {
        fixtures.suspend(alice);
        like(bob).andExpect(status().isNotFound());
    }

    private ResultActions like(UUID userId) throws Exception {
        return mvc.perform(post("/api/posts/{id}/likes", post)
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId)));
    }

    private ResultActions unlike(UUID userId) throws Exception {
        return mvc.perform(delete("/api/posts/{id}/likes", post)
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId)));
    }
}
