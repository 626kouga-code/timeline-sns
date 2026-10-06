package com.timelinesns.follow;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * フォロー・解除（F-42）の結合テスト。
 */
@IntegrationTest
class FollowApiTest {

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

    @Test
    void followAndUnfollowReturnStateAndFollowerCount() throws Exception {
        follow(bob, "alice").andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        mvc.perform(get("/api/users/{handle}", "alice").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        unfollow(bob, "alice").andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    // 連打や再送でもエラーにせず状態だけ返す
    @Test
    void followAndUnfollowAreIdempotent() throws Exception {
        follow(bob, "alice");
        follow(bob, "alice").andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        unfollow(bob, "alice");
        unfollow(bob, "alice").andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    // フォローするとホームタイムラインに相手の投稿が入る
    @Test
    void followedUsersPostsAppearInHomeTimeline() throws Exception {
        fixtures.post(alice, "from alice");
        mvc.perform(get("/api/timeline/home").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                .andExpect(jsonPath("$.items.length()").value(0));

        follow(bob, "alice");
        mvc.perform(get("/api/timeline/home").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(bob)))
                .andExpect(jsonPath("$.items[*].body").value(contains("from alice")));
    }

    @Test
    void cannotFollowSelf() throws Exception {
        follow(alice, "alice").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
    }

    // ブロック関係にある相手はフォローできない（F-60）。どちらがブロックしていても同じ
    @Test
    void cannotFollowAcrossBlocks() throws Exception {
        fixtures.block(alice, bob);
        follow(bob, "alice").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOCKED"));
        follow(alice, "bob").andExpect(status().isForbidden());
    }

    @Test
    void cannotFollowMissingOrSuspendedUser() throws Exception {
        follow(bob, "nobody").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        fixtures.suspend(alice);
        follow(bob, "alice").andExpect(status().isNotFound());
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(put("/api/users/{handle}/follow", "alice")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/{handle}/follow", "alice")).andExpect(status().isUnauthorized());
    }

    private ResultActions follow(UUID userId, String handle) throws Exception {
        return mvc.perform(put("/api/users/{handle}/follow", handle)
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId)));
    }

    private ResultActions unfollow(UUID userId, String handle) throws Exception {
        return mvc.perform(delete("/api/users/{handle}/follow", handle)
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId)));
    }
}
