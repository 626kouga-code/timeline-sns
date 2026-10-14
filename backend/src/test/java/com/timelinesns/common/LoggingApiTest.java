package com.timelinesns.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ログ監視のためのアプリ側の仕組み（docs/10）の結合テスト。
 * ヘルスチェック、リクエスト ID、アクセスログへのユーザー ID の付与を確かめる。
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LoggingApiTest {

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

    @Test
    void healthCheckIsPublicAndReturnsOnlyStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        // SecurityConfig で health 以外は拒否しているので、未ログインなら 401 になり中身は返らない
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarryRequestIdEvenWhenUnauthorized() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    void accessLogHasUserIdOfLoggedInUser(CapturedOutput output) throws Exception {
        UUID alice = fixtures.user("alice");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                .andExpect(status().isOk());

        assertThat(output).containsPattern("," + alice + "\\].*GET /api/me 200");
        // アクセストークンそのものはログに出さない
        assertThat(output).doesNotContain(fixtures.token(alice));
    }
}
