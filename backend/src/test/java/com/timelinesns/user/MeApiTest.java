package com.timelinesns.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * プロフィール編集（F-41, PATCH /api/me）の結合テスト。
 */
@IntegrationTest
class MeApiTest {

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

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
        alice = fixtures.user("alice");
    }

    @Test
    void updatesDisplayNameAndBio() throws Exception {
        update("{\"displayName\":\"  アリス \",\"bio\":\" よろしく\\nお願いします \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("アリス"))
                .andExpect(jsonPath("$.bio").value("よろしく\nお願いします"))
                .andExpect(jsonPath("$.handle").value("alice"));

        mvc.perform(get("/api/users/{handle}", "alice"))
                .andExpect(jsonPath("$.displayName").value("アリス"))
                .andExpect(jsonPath("$.bio").value("よろしく\nお願いします"));
    }

    @Test
    void canClearBio() throws Exception {
        update("{\"displayName\":\"a\",\"bio\":\"x\"}");
        update("{\"displayName\":\"a\",\"bio\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(""));
    }

    // 文字数は画面のカウンタと同じくコードポイントで数える（絵文字も 1 文字）
    @Test
    void acceptsLimitsCountedInCodePoints() throws Exception {
        update("{\"displayName\":\"" + "😀".repeat(50) + "\",\"bio\":\"" + "😀".repeat(160) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        update("{\"displayName\":\" \",\"bio\":\"" + "a".repeat(161) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.displayName").value("表示名を入力してください"))
                .andExpect(jsonPath("$.errors.bio").value("自己紹介は 160 文字以内にしてください"));
        update("{\"displayName\":\"" + "a".repeat(51) + "\",\"bio\":\"\"}")
                .andExpect(jsonPath("$.errors.displayName").value("表示名は 50 文字以内にしてください"));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"a\",\"bio\":\"\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions update(String json) throws Exception {
        return mvc.perform(patch("/api/me")
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
