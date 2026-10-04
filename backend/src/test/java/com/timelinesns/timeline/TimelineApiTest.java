package com.timelinesns.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * タイムライン API（ホーム F-20・全体 F-21・新着件数 F-22）の結合テスト。
 */
@IntegrationTest
class TimelineApiTest {

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
    class Global {

        @Test
        void guestSeesAllPostsNewestFirst() throws Exception {
            UUID first = fixtures.post(alice, "1");
            UUID second = fixtures.post(bob, "2");
            UUID third = fixtures.post(carol, "3");

            assertThat(itemIds(get("/api/timeline/global"))).isEqualTo(ids(third, second, first));
            mvc.perform(get("/api/timeline/global"))
                    .andExpect(jsonPath("$.items[0].author.handle").value("carol"))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        @Test
        void pagesWithCursor() throws Exception {
            List<UUID> posts = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                posts.add(fixtures.post(alice, "post " + i));
            }

            String firstPage = body(get("/api/timeline/global"));
            List<String> firstIds = JsonPath.read(firstPage, "$.items[*].id");
            String cursor = JsonPath.read(firstPage, "$.nextCursor");
            assertThat(firstIds).hasSize(20).first().isEqualTo(posts.get(24).toString());
            assertThat(cursor).isEqualTo(posts.get(5).toString());

            mvc.perform(get("/api/timeline/global").param("cursor", cursor))
                    .andExpect(jsonPath("$.items.length()").value(5))
                    .andExpect(jsonPath("$.items[0].id").value(posts.get(4).toString()))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        @Test
        void rejectsMalformedCursorWithFieldError() throws Exception {
            mvc.perform(get("/api/timeline/global").param("cursor", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.cursor").value("値の形式が正しくありません"));
        }

        @Test
        void limitIsClampedToMaximum() throws Exception {
            for (int i = 0; i < 55; i++) {
                fixtures.post(alice, "post " + i);
            }
            mvc.perform(get("/api/timeline/global").param("limit", "1000"))
                    .andExpect(jsonPath("$.items.length()").value(50));
            mvc.perform(get("/api/timeline/global").param("limit", "3"))
                    .andExpect(jsonPath("$.items.length()").value(3));
        }

        @Test
        void excludesSuspendedAndBlockedUsers() throws Exception {
            UUID fromAlice = fixtures.post(alice, "alice");
            UUID fromBob = fixtures.post(bob, "bob");
            fixtures.post(carol, "carol");
            fixtures.suspend(carol);
            fixtures.block(bob, alice);

            assertThat(itemIds(get("/api/timeline/global"))).isEqualTo(ids(fromBob, fromAlice));
            // ブロックした側からもされた側からも見えない
            assertThat(itemIds(asUser(get("/api/timeline/global"), alice))).isEqualTo(ids(fromAlice));
            assertThat(itemIds(asUser(get("/api/timeline/global"), bob))).isEqualTo(ids(fromBob));
        }
    }

    @Nested
    class Home {

        @Test
        void showsOwnAndFollowedUsersPosts() throws Exception {
            UUID fromAlice = fixtures.post(alice, "alice");
            UUID fromBob = fixtures.post(bob, "bob");
            fixtures.post(carol, "carol");
            fixtures.follow(alice, bob);
            // carol が alice をフォローしていても、alice のホームには関係ない
            fixtures.follow(carol, alice);

            assertThat(itemIds(asUser(get("/api/timeline/home"), alice))).isEqualTo(ids(fromBob, fromAlice));
        }

        @Test
        void excludesBlockedFollowees() throws Exception {
            fixtures.post(bob, "bob");
            fixtures.follow(alice, bob);
            fixtures.block(bob, alice);

            mvc.perform(asUser(get("/api/timeline/home"), alice))
                    .andExpect(jsonPath("$.items.length()").value(0));
        }

        @Test
        void requiresLogin() throws Exception {
            mvc.perform(get("/api/timeline/home")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/timeline/home/new-count").param("since", UUID.randomUUID().toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class NewCount {

        @Test
        void countsPostsNewerThanSince() throws Exception {
            UUID since = fixtures.post(alice, "seen");
            fixtures.post(bob, "new 1");
            fixtures.post(carol, "new 2");

            mvc.perform(get("/api/timeline/global/new-count").param("since", since.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2));
        }

        @Test
        void doesNotCountOwnPosts() throws Exception {
            UUID since = fixtures.post(bob, "seen");
            fixtures.post(alice, "mine");
            fixtures.post(bob, "new");

            mvc.perform(asUser(get("/api/timeline/global/new-count"), alice).param("since", since.toString()))
                    .andExpect(jsonPath("$.count").value(1));
        }

        @Test
        void homeCountsOnlyFollowedUsers() throws Exception {
            UUID since = fixtures.post(alice, "seen");
            fixtures.follow(alice, bob);
            fixtures.post(bob, "followed");
            fixtures.post(carol, "not followed");

            mvc.perform(asUser(get("/api/timeline/home/new-count"), alice).param("since", since.toString()))
                    .andExpect(jsonPath("$.count").value(1));
        }

        @Test
        void stopsCountingAtMaximum() throws Exception {
            UUID since = fixtures.post(alice, "seen");
            jdbc.update("INSERT INTO posts (user_id, body) SELECT ?, 'p' FROM generate_series(1, 120)", bob);

            mvc.perform(get("/api/timeline/global/new-count").param("since", since.toString()))
                    .andExpect(jsonPath("$.count").value(100));
        }

        @Test
        void requiresSince() throws Exception {
            mvc.perform(get("/api/timeline/global/new-count"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.since").value("値を指定してください"));
        }
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, UUID userId) {
        return request.header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId));
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<String> itemIds(MockHttpServletRequestBuilder request) throws Exception {
        return JsonPath.read(body(request), "$.items[*].id");
    }

    private static List<String> ids(UUID... ids) {
        return Arrays.stream(ids).map(UUID::toString).toList();
    }
}
