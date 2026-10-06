package com.timelinesns.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.support.QueryCounter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * プロフィール（F-40）・投稿一覧と、フォロー・フォロワー一覧（F-43）の結合テスト。
 */
@IntegrationTest
class UserApiTest {

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

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
        carol = fixtures.user("carol");
    }

    @Nested
    class Profile {

        @Test
        void returnsProfileWithCountsToGuest() throws Exception {
            fixtures.post(alice, "one");
            fixtures.post(alice, "two");
            fixtures.follow(alice, bob);
            fixtures.follow(bob, alice);
            fixtures.follow(carol, alice);

            mvc.perform(get("/api/users/{handle}", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(alice.toString()))
                    .andExpect(jsonPath("$.handle").value("alice"))
                    .andExpect(jsonPath("$.displayName").value("ALICE"))
                    .andExpect(jsonPath("$.bio").value(""))
                    .andExpect(jsonPath("$.postCount").value(2))
                    .andExpect(jsonPath("$.followingCount").value(1))
                    .andExpect(jsonPath("$.followerCount").value(2))
                    .andExpect(jsonPath("$.following").value(false))
                    .andExpect(jsonPath("$.followedBy").value(false))
                    .andExpect(jsonPath("$.email").doesNotExist());
        }

        @Test
        void returnsRelationshipWithViewer() throws Exception {
            fixtures.follow(bob, alice);
            mvc.perform(asUser(get("/api/users/{handle}", "alice"), bob))
                    .andExpect(jsonPath("$.following").value(true))
                    .andExpect(jsonPath("$.followedBy").value(false));

            fixtures.follow(alice, bob);
            mvc.perform(asUser(get("/api/users/{handle}", "alice"), bob))
                    .andExpect(jsonPath("$.followedBy").value(true));
        }

        // ユーザーIDは大文字小文字を区別しない
        @Test
        void handleIsCaseInsensitive() throws Exception {
            mvc.perform(get("/api/users/{handle}", "ALICE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.handle").value("alice"));
        }

        @Test
        void returns404ForMissingUser() throws Exception {
            mvc.perform(get("/api/users/{handle}", "nobody"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        // 凍結されたアカウントのプロフィールは表示しない（F-63）。フォロー数にも数えない
        @Test
        void hidesSuspendedUser() throws Exception {
            fixtures.follow(carol, alice);
            fixtures.follow(bob, alice);
            fixtures.suspend(carol);

            mvc.perform(get("/api/users/{handle}", "carol")).andExpect(status().isNotFound());
            mvc.perform(get("/api/users/{handle}", "alice")).andExpect(jsonPath("$.followerCount").value(1));
        }

        // ブロック関係にあってもプロフィールは見える。どちらがブロックしているかを返す（F-60）
        @Test
        void reportsBlocks() throws Exception {
            fixtures.block(alice, bob);

            mvc.perform(asUser(get("/api/users/{handle}", "bob"), alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.blocking").value(true))
                    .andExpect(jsonPath("$.blockedBy").value(false));
            mvc.perform(asUser(get("/api/users/{handle}", "alice"), bob))
                    .andExpect(jsonPath("$.blocking").value(false))
                    .andExpect(jsonPath("$.blockedBy").value(true));
        }
    }

    @Nested
    class Posts {

        @Test
        void returnsOnlyThatUsersPostsNewestFirst() throws Exception {
            UUID first = fixtures.post(alice, "first");
            fixtures.post(bob, "bob");
            UUID second = fixtures.post(alice, "second");

            mvc.perform(get("/api/users/{handle}/posts", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(contains(second.toString(), first.toString())))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        @Test
        void pagesWithCursor() throws Exception {
            for (int i = 0; i < 3; i++) {
                fixtures.post(alice, "post " + i);
            }
            ResultActions page1 = mvc.perform(get("/api/users/{handle}/posts", "alice").param("limit", "2"))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].body").value("post 2"));
            String cursor = JsonPath.read(page1.andReturn().getResponse().getContentAsString(), "$.nextCursor");

            mvc.perform(get("/api/users/{handle}/posts", "alice").param("limit", "2").param("cursor", cursor))
                    .andExpect(jsonPath("$.items[*].body").value(contains("post 0")))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        @Test
        void isEmptyAcrossBlocks() throws Exception {
            fixtures.post(alice, "hidden");
            fixtures.block(bob, alice);

            mvc.perform(asUser(get("/api/users/{handle}/posts", "alice"), bob))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(0));
        }

        @Test
        void returns404ForSuspendedUser() throws Exception {
            fixtures.suspend(alice);
            mvc.perform(get("/api/users/{handle}/posts", "alice")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class FollowLists {

        @Test
        void listsFollowingAndFollowersNewestFollowFirst() throws Exception {
            fixtures.follow(alice, bob);
            fixtures.follow(alice, carol);
            fixtures.follow(bob, alice);

            mvc.perform(get("/api/users/{handle}/following", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].handle").value(contains("carol", "bob")))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
            mvc.perform(get("/api/users/{handle}/followers", "alice"))
                    .andExpect(jsonPath("$.items[*].handle").value(contains("bob")));
        }

        @Test
        void returnsRelationshipWithViewer() throws Exception {
            fixtures.follow(alice, bob);
            fixtures.follow(carol, bob);
            fixtures.follow(bob, carol);

            mvc.perform(asUser(get("/api/users/{handle}/following", "alice"), carol))
                    .andExpect(jsonPath("$.items[0].handle").value("bob"))
                    .andExpect(jsonPath("$.items[0].following").value(true))
                    .andExpect(jsonPath("$.items[0].followedBy").value(true));
        }

        @Test
        void pagesWithCursor() throws Exception {
            UUID dave = fixtures.user("dave");
            fixtures.follow(bob, alice);
            fixtures.follow(carol, alice);
            fixtures.follow(dave, alice);

            ResultActions page1 = mvc.perform(get("/api/users/{handle}/followers", "alice").param("limit", "2"))
                    .andExpect(jsonPath("$.items[*].handle").value(contains("dave", "carol")))
                    .andExpect(jsonPath("$.nextCursor").value(carol.toString()));
            String cursor = JsonPath.read(page1.andReturn().getResponse().getContentAsString(), "$.nextCursor");

            mvc.perform(get("/api/users/{handle}/followers", "alice").param("limit", "2").param("cursor", cursor))
                    .andExpect(jsonPath("$.items[*].handle").value(contains("bob")))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        // 凍結されたユーザーと、閲覧者とブロック関係にあるユーザーは一覧に出さない
        @Test
        void excludesSuspendedAndBlockedUsers() throws Exception {
            UUID dave = fixtures.user("dave");
            fixtures.follow(bob, alice);
            fixtures.follow(carol, alice);
            fixtures.follow(dave, alice);
            fixtures.suspend(carol);
            fixtures.block(dave, bob);

            mvc.perform(asUser(get("/api/users/{handle}/followers", "alice"), bob))
                    .andExpect(jsonPath("$.items[*].handle").value(contains("bob")));
        }

        @Test
        void returns404ForMissingUser() throws Exception {
            mvc.perform(get("/api/users/{handle}/following", "nobody")).andExpect(status().isNotFound());
        }
    }

    /**
     * N+1 の回帰テスト。件数が増えても、実行する SQL の数は変わらない。
     */
    @Nested
    class QueryCount {

        @Test
        void followListUsesSameNumberOfQueriesRegardlessOfUsers() throws Exception {
            fixtures.follow(bob, alice);
            long one = QueryCounter.count(() -> followersAs(carol).andExpect(jsonPath("$.items.length()").value(1)));

            for (int i = 0; i < 20; i++) {
                UUID follower = fixtures.user("user" + i);
                fixtures.follow(follower, alice);
                fixtures.follow(carol, follower);
            }
            long many = QueryCounter.count(() -> followersAs(carol).andExpect(jsonPath("$.items.length()").value(20)));

            assertThat(one).isPositive();
            assertThat(many).isEqualTo(one);
        }

        @Test
        void profileUsesSameNumberOfQueriesRegardlessOfFollowsAndPosts() throws Exception {
            long none = QueryCounter.count(() -> mvc.perform(asUser(get("/api/users/{handle}", "alice"), bob))
                    .andExpect(status().isOk()));

            for (int i = 0; i < 20; i++) {
                UUID follower = fixtures.user("user" + i);
                fixtures.follow(follower, alice);
                fixtures.post(alice, "post " + i);
            }
            long many = QueryCounter.count(() -> mvc.perform(asUser(get("/api/users/{handle}", "alice"), bob))
                    .andExpect(jsonPath("$.followerCount").value(20)));

            assertThat(none).isPositive();
            assertThat(many).isEqualTo(none);
        }
    }

    private ResultActions followersAs(UUID userId) throws Exception {
        return mvc.perform(asUser(get("/api/users/{handle}/followers", "alice"), userId));
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, UUID userId) {
        return request.header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId));
    }
}
