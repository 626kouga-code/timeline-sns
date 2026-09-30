package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.timelinesns.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 認証 API（登録・ログイン・リフレッシュ・ログアウト・/api/me）と認可ルールの結合テスト。
 */
@IntegrationTest
class AuthApiTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE users CASCADE");
    }

    @Nested
    class Signup {

        @Test
        void returns201WithAccessTokenAndRefreshCookie() throws Exception {
            MvcResult result = signup("alice@example.com", "alice", "Alice")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.handle").value("alice"))
                    .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.user.role").value("USER"))
                    .andExpect(jsonPath("$.user.emailVerified").value(false))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=1209600")))
                    .andReturn();

            assertThat(accessToken(result)).isNotBlank();
            // パスワードは BCrypt のハッシュ、リフレッシュトークンは SHA-256 のハッシュだけが保存される
            String passwordHash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
            assertThat(passwordHash).startsWith("$2").isNotEqualTo(PASSWORD);
            String tokenHash = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
            assertThat(tokenHash).hasSize(64).isNotEqualTo(refreshCookie(result));
        }

        @Test
        void rejectsInvalidInputWithFieldErrors() throws Exception {
            postJson("/api/auth/signup", """
                    {"email":"not-an-email","password":"short","handle":"a!","displayName":" ","agreedToTerms":false}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.handle").exists())
                    .andExpect(jsonPath("$.errors.displayName").exists())
                    .andExpect(jsonPath("$.errors.agreedToTerms").exists());
        }

        @Test
        void countsDisplayNameLengthInCodePoints() throws Exception {
            signup("emoji50@example.com", "emoji50", "😀".repeat(50)).andExpect(status().isCreated());
            signup("emoji51@example.com", "emoji51", "😀".repeat(51))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.displayName").exists());
        }

        @Test
        void rejectsPasswordLongerThan72Bytes() throws Exception {
            // 25 文字だが UTF-8 で 75 バイト
            postJson("/api/auth/signup", signupJson("alice@example.com", "alice", "Alice", "あ".repeat(25)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
        }

        @Test
        void rejectsDuplicateEmailIgnoringCase() throws Exception {
            signup("alice@example.com", "alice", "Alice").andExpect(status().isCreated());

            signup("ALICE@example.com", "alice2", "Alice")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        void rejectsDuplicateHandleIgnoringCase() throws Exception {
            signup("alice@example.com", "alice", "Alice").andExpect(status().isCreated());

            signup("other@example.com", "ALICE", "Alice")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
        }
    }

    @Nested
    class Login {

        @Test
        void returnsTokensForValidCredentials() throws Exception {
            signup("alice@example.com", "alice", "Alice");

            MvcResult result = login("Alice@Example.com", PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.handle").value("alice"))
                    .andReturn();

            assertThat(accessToken(result)).isNotBlank();
            assertThat(refreshCookie(result)).isNotBlank();
        }

        @Test
        void returnsSameErrorForWrongPasswordAndUnknownEmail() throws Exception {
            signup("alice@example.com", "alice", "Alice");

            login("alice@example.com", "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
            login("nobody@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        void rejectsSuspendedUser() throws Exception {
            signup("alice@example.com", "alice", "Alice");
            jdbc.update("UPDATE users SET status = 'SUSPENDED'");

            login("alice@example.com", PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
            // パスワードが違う場合は、凍結されているかどうかを明かさない
            login("alice@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Me {

        @Test
        void returnsCurrentUser() throws Exception {
            String token = accessToken(signup("alice@example.com", "alice", "Alice").andReturn());

            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.handle").value("alice"))
                    .andExpect(jsonPath("$.email").value("alice@example.com"));
        }

        @Test
        void requiresAccessToken() throws Exception {
            mvc.perform(get("/api/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            MvcResult result = signup("alice@example.com", "alice", "Alice").andReturn();
            String userId = JsonPath.read(body(result), "$.user.id");
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
            String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder()
                            .issuer(AccessTokenService.ISSUER)
                            .subject(userId)
                            .issuedAt(past)
                            .expiresAt(past.plus(15, ChronoUnit.MINUTES))
                            .claim(AccessTokenService.ROLE_CLAIM, "USER")
                            .build()))
                    .getTokenValue();

            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsTamperedToken() throws Exception {
            String token = accessToken(signup("alice@example.com", "alice", "Alice").andReturn());
            int signatureStart = token.lastIndexOf('.') + 1;
            char replaced = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
            String tampered = token.substring(0, signatureStart) + replaced + token.substring(signatureStart + 1);

            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesRefreshToken() throws Exception {
            String first = refreshCookie(signup("alice@example.com", "alice", "Alice").andReturn());

            MvcResult result = refresh(first).andExpect(status().isOk()).andReturn();
            String second = refreshCookie(result);

            assertThat(second).isNotBlank().isNotEqualTo(first);
            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(result)))
                    .andExpect(status().isOk());
            assertThat(count("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NOT NULL")).isEqualTo(1);
        }

        @Test
        void reuseOfRevokedTokenRevokesAllSessions() throws Exception {
            String first = refreshCookie(signup("alice@example.com", "alice", "Alice").andReturn());
            String second = refreshCookie(refresh(first).andReturn());

            // 使用済みのトークンが再び使われたら、盗まれたとみなして新しいトークンも無効にする
            refresh(first).andExpect(status().isUnauthorized());
            refresh(second).andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsMissingOrUnknownToken() throws Exception {
            mvc.perform(postRequest("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
            refresh("unknown-token").andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            String token = refreshCookie(signup("alice@example.com", "alice", "Alice").andReturn());
            jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'");

            refresh(token).andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsSuspendedUserAndRevokesTokens() throws Exception {
            String token = refreshCookie(signup("alice@example.com", "alice", "Alice").andReturn());
            jdbc.update("UPDATE users SET status = 'SUSPENDED'");

            refresh(token)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
            assertThat(count("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL")).isZero();
        }
    }

    @Nested
    class Logout {

        @Test
        void revokesTokenAndClearsCookie() throws Exception {
            String token = refreshCookie(signup("alice@example.com", "alice", "Alice").andReturn());

            mvc.perform(postRequest("/api/auth/logout").cookie(new Cookie(RefreshTokenCookies.NAME, token)))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            refresh(token).andExpect(status().isUnauthorized());
        }

        @Test
        void succeedsWithoutCookie() throws Exception {
            mvc.perform(postRequest("/api/auth/logout")).andExpect(status().isNoContent());
        }
    }

    @Nested
    class Authorization {

        @Test
        void adminApiRequiresAdminRole() throws Exception {
            String userToken = accessToken(signup("alice@example.com", "alice", "Alice").andReturn());
            signup("admin@example.com", "admin", "Admin");
            jdbc.update("UPDATE users SET role = 'ADMIN' WHERE handle = 'admin'");
            String adminToken = accessToken(login("admin@example.com", PASSWORD).andReturn());

            mvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        void doesNotCreateSession() throws Exception {
            signup("alice@example.com", "alice", "Alice")
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("JSESSIONID"))));
        }
    }

    private ResultActions signup(String email, String handle, String displayName) throws Exception {
        return postJson("/api/auth/signup", signupJson(email, handle, displayName, PASSWORD));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(postRequest("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookies.NAME, token)));
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(postRequest(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static MockHttpServletRequestBuilder postRequest(String path) {
        return MockMvcRequestBuilders.post(path);
    }

    private static String signupJson(String email, String handle, String displayName, String password) {
        String template = "{\"email\":\"%s\",\"password\":\"%s\",\"handle\":\"%s\","
                + "\"displayName\":\"%s\",\"agreedToTerms\":true}";
        return template.formatted(email, password, handle, displayName);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(body(result), "$.accessToken");
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(cookie).as("refresh_token cookie").isNotNull();
        return cookie.getValue();
    }

    private int count(String sql) {
        Integer result = jdbc.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
