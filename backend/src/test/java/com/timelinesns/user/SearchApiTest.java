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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * ユーザー検索（F-44）の結合テスト。
 */
@IntegrationTest
class SearchApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccessTokenService accessTokens;

    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
    }

    // ユーザーIDの完全一致 → 前方一致 → それ以外（途中一致・表示名だけの一致）、同じ順位はユーザーID順
    @Test
    void matchesHandleAndDisplayNameRankedByHandleMatch() throws Exception {
        fixtures.user("taro");
        fixtures.user("taro_2");
        fixtures.user("bigtaro");
        UUID hanako = fixtures.user("hanako");
        jdbc.update("UPDATE users SET display_name = 'たろうの友だち TARO' WHERE id = ?", hanako);
        fixtures.user("jiro");

        search("taro", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].handle").value(contains("taro", "taro_2", "bigtaro", "hanako")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void isCaseInsensitiveAndMatchesJapaneseDisplayName() throws Exception {
        UUID alice = fixtures.user("Alice");
        jdbc.update("UPDATE users SET display_name = 'ありす' WHERE id = ?", alice);

        search("aLiCe", null).andExpect(jsonPath("$.items[*].handle").value(contains("Alice")));
        search("りす", null).andExpect(jsonPath("$.items[*].handle").value(contains("Alice")));
    }

    // % と _ はワイルドカードではなく、ただの文字として探す
    @Test
    void treatsLikeWildcardsAsLiterals() throws Exception {
        fixtures.user("a_b");
        fixtures.user("axb");

        search("a_b", null).andExpect(jsonPath("$.items[*].handle").value(contains("a_b")));
        search("%", null).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void pagesWithCursor() throws Exception {
        for (int i = 0; i < 5; i++) {
            fixtures.user("user" + i);
        }
        ResultActions page1 = mvc.perform(get("/api/search/users").param("q", "user").param("limit", "3"))
                .andExpect(jsonPath("$.items[*].handle").value(contains("user0", "user1", "user2")));
        String cursor = JsonPath.read(page1.andReturn().getResponse().getContentAsString(), "$.nextCursor");

        mvc.perform(get("/api/search/users").param("q", "user").param("limit", "3").param("cursor", cursor))
                .andExpect(jsonPath("$.items[*].handle").value(contains("user3", "user4")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    // カーソルが順位をまたいでも、続きから正しく取れる
    @Test
    void cursorContinuesAcrossRanks() throws Exception {
        fixtures.user("cat");
        fixtures.user("catalog");
        fixtures.user("bobcat");
        ResultActions page1 = mvc.perform(get("/api/search/users").param("q", "cat").param("limit", "2"))
                .andExpect(jsonPath("$.items[*].handle").value(contains("cat", "catalog")));
        String cursor = JsonPath.read(page1.andReturn().getResponse().getContentAsString(), "$.nextCursor");

        mvc.perform(get("/api/search/users").param("q", "cat").param("limit", "2").param("cursor", cursor))
                .andExpect(jsonPath("$.items[*].handle").value(contains("bobcat")));
    }

    @Test
    void excludesSuspendedAndBlockedUsers() throws Exception {
        UUID viewer = fixtures.user("viewer");
        fixtures.user("kenta");
        UUID suspended = fixtures.user("kenji");
        UUID blocker = fixtures.user("kenzo");
        UUID blocked = fixtures.user("kenta2");
        fixtures.suspend(suspended);
        fixtures.block(blocker, viewer);
        fixtures.block(viewer, blocked);

        search("ken", viewer).andExpect(jsonPath("$.items[*].handle").value(contains("kenta")));
        // ゲストにはブロックの除外がかからない
        search("ken", null).andExpect(jsonPath("$.items[*].handle").value(contains("kenta", "kenta2", "kenzo")));
    }

    @Test
    void returnsRelationshipWithViewer() throws Exception {
        UUID viewer = fixtures.user("viewer");
        UUID mika = fixtures.user("mika");
        fixtures.follow(viewer, mika);
        fixtures.follow(mika, viewer);

        search("mika", viewer)
                .andExpect(jsonPath("$.items[0].following").value(true))
                .andExpect(jsonPath("$.items[0].followedBy").value(true));
    }

    @Test
    void rejectsBlankOrTooLongQuery() throws Exception {
        mvc.perform(get("/api/search/users").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").value("キーワードを入力してください"));
        mvc.perform(get("/api/search/users").param("q", "あ".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").value("キーワードは 50 文字以内にしてください"));
        mvc.perform(get("/api/search/users")).andExpect(status().isBadRequest());
    }

    // N+1 の回帰テスト。ヒット件数が増えても、実行する SQL の数は変わらない
    @Test
    void usesSameNumberOfQueriesRegardlessOfResults() throws Exception {
        UUID viewer = fixtures.user("viewer");
        fixtures.user("sato0");
        long one = QueryCounter.count(() -> search("sato", viewer).andExpect(jsonPath("$.items.length()").value(1)));

        for (int i = 1; i < 20; i++) {
            UUID u = fixtures.user("sato" + i);
            fixtures.follow(viewer, u);
        }
        long many = QueryCounter.count(() -> search("sato", viewer).andExpect(jsonPath("$.items.length()").value(20)));

        assertThat(one).isPositive();
        assertThat(many).isEqualTo(one);
    }

    private ResultActions search(String q, UUID viewer) throws Exception {
        var request = get("/api/search/users").param("q", q);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, fixtures.bearer(viewer));
        }
        return mvc.perform(request);
    }
}
