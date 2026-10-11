package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelinesns.common.ApiException;
import com.timelinesns.storage.MediaUrls;
import com.timelinesns.storage.StorageProperties;
import com.timelinesns.user.User;
import com.timelinesns.user.UserRepository;
import com.timelinesns.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 登録・ログイン・トークンの再発行・ログアウトのロジック（AuthService）。DB とトークンの発行はモックにする。
 */
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String DUMMY_HASH = "dummy-hash";
    // UTF-8 で 3 バイトの文字を 25 個（75 バイト）。文字数は 72 以内でも BCrypt の上限を超える
    private static final String TOO_LONG_PASSWORD = "あ".repeat(25);

    private UserRepository users;
    private PasswordEncoder encoder;
    private AccessTokenService accessTokens;
    private RefreshTokenService refreshTokens;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        accessTokens = mock(AccessTokenService.class);
        refreshTokens = mock(RefreshTokenService.class);
        when(encoder.encode("dummy-password-for-timing")).thenReturn(DUMMY_HASH);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(accessTokens.issue(any())).thenReturn("access-token");
        when(accessTokens.expiresInSeconds()).thenReturn(900L);
        when(refreshTokens.issue(any())).thenReturn("refresh-token");
        MediaUrls mediaUrls = new MediaUrls(new StorageProperties("b", "r", null, "/api/media"));
        service = new AuthService(users, encoder, accessTokens, refreshTokens, mediaUrls,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(String passwordHash, UserStatus status) {
        User user = new User("taro@example.com", passwordHash, "taro", "太郎", NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setStatus(status);
        return user;
    }

    private static void assertApiError(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Nested
    class Signup {

        private SignupRequest request(String email, String password, String handle, String displayName) {
            return new SignupRequest(email, password, handle, displayName, true);
        }

        @Test
        void savesUserWithHashedPasswordAndStartsSession() {
            AuthService.Session session = service.signup(request(" taro@example.com ", "password123", "taro", " 太郎 "));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(users).saveAndFlush(saved.capture());
            User user = saved.getValue();
            assertThat(user.getEmail()).isEqualTo("taro@example.com");
            assertThat(user.getPasswordHash()).isEqualTo("hashed");
            assertThat(user.getHandle()).isEqualTo("taro");
            assertThat(user.getDisplayName()).isEqualTo("太郎");
            assertThat(user.getCreatedAt()).isEqualTo(NOW);

            assertThat(session.refreshToken()).isEqualTo("refresh-token");
            assertThat(session.body().accessToken()).isEqualTo("access-token");
            assertThat(session.body().tokenType()).isEqualTo("Bearer");
            assertThat(session.body().expiresIn()).isEqualTo(900);
            assertThat(session.body().user().handle()).isEqualTo("taro");
            assertThat(session.body().user().emailVerified()).isFalse();
        }

        @Test
        void rejectsTakenEmail() {
            when(users.existsByEmailIgnoreCase("taro@example.com")).thenReturn(true);

            assertApiError(() -> service.signup(request("taro@example.com", "password123", "taro", "太郎")),
                    HttpStatus.CONFLICT, "EMAIL_TAKEN");
            verify(users, never()).saveAndFlush(any());
        }

        @Test
        void rejectsTakenHandle() {
            when(users.existsByHandleIgnoreCase("taro")).thenReturn(true);

            assertApiError(() -> service.signup(request("taro@example.com", "password123", "taro", "太郎")),
                    HttpStatus.CONFLICT, "HANDLE_TAKEN");
            verify(users, never()).saveAndFlush(any());
        }

        @Test
        void rejectsPasswordOverBcryptByteLimit() {
            assertApiError(() -> service.signup(request("taro@example.com", TOO_LONG_PASSWORD, "taro", "太郎")),
                    HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
            verify(users, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Login {

        @Test
        void startsSessionWithCorrectPassword() {
            User taro = user("stored-hash", UserStatus.ACTIVE);
            when(users.findByEmailIgnoreCase("taro@example.com")).thenReturn(Optional.of(taro));
            when(encoder.matches("password123", "stored-hash")).thenReturn(true);

            AuthService.Session session = service.login(new LoginRequest(" taro@example.com ", "password123"));

            assertThat(session.body().user().id()).isEqualTo(taro.getId());
            verify(refreshTokens).issue(taro.getId());
        }

        @Test
        void rejectsWrongPassword() {
            when(users.findByEmailIgnoreCase("taro@example.com"))
                    .thenReturn(Optional.of(user("stored-hash", UserStatus.ACTIVE)));

            assertApiError(() -> service.login(new LoginRequest("taro@example.com", "wrong")),
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            verify(refreshTokens, never()).issue(any());
        }

        @Test
        void unknownEmailStillChecksDummyPasswordForTiming() {
            assertApiError(() -> service.login(new LoginRequest("nobody@example.com", "password123")),
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            verify(encoder).matches("password123", DUMMY_HASH);
        }

        @Test
        void rejectsUserWithoutPassword() {
            // Google ログインだけのユーザー
            when(users.findByEmailIgnoreCase("taro@example.com"))
                    .thenReturn(Optional.of(user(null, UserStatus.ACTIVE)));
            when(encoder.matches(anyString(), eq(DUMMY_HASH))).thenReturn(true);

            assertApiError(() -> service.login(new LoginRequest("taro@example.com", "password123")),
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        @Test
        void rejectsSuspendedUserOnlyAfterPasswordMatches() {
            when(users.findByEmailIgnoreCase("taro@example.com"))
                    .thenReturn(Optional.of(user("stored-hash", UserStatus.SUSPENDED)));
            when(encoder.matches("password123", "stored-hash")).thenReturn(true);

            assertApiError(() -> service.login(new LoginRequest("taro@example.com", "password123")),
                    HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED");
            // パスワードが違えば、凍結されているかどうかは明かさない
            assertApiError(() -> service.login(new LoginRequest("taro@example.com", "wrong")),
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        @Test
        void rejectsTooLongPasswordWithoutCallingBcrypt() {
            when(users.findByEmailIgnoreCase("taro@example.com"))
                    .thenReturn(Optional.of(user("stored-hash", UserStatus.ACTIVE)));

            assertApiError(() -> service.login(new LoginRequest("taro@example.com", TOO_LONG_PASSWORD)),
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            verify(encoder, never()).matches(eq(TOO_LONG_PASSWORD), anyString());
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesTokenAndReturnsNewSession() {
            User taro = user("hash", UserStatus.ACTIVE);
            when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotation(taro.getId(), "new"));
            when(users.findById(taro.getId())).thenReturn(Optional.of(taro));

            AuthService.Session session = service.refresh("old");

            assertThat(session.refreshToken()).isEqualTo("new");
            assertThat(session.body().accessToken()).isEqualTo("access-token");
            assertThat(session.body().user().id()).isEqualTo(taro.getId());
        }

        @Test
        void rejectsWhenUserNoLongerExists() {
            UUID deleted = UUID.randomUUID();
            when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotation(deleted, "new"));

            assertApiError(() -> service.refresh("old"), HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        @Test
        void revokesAllTokensOfSuspendedUser() {
            User taro = user("hash", UserStatus.SUSPENDED);
            when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotation(taro.getId(), "new"));
            when(users.findById(taro.getId())).thenReturn(Optional.of(taro));

            assertApiError(() -> service.refresh("old"), HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED");
            verify(refreshTokens).revokeAll(taro.getId());
        }
    }

    @Test
    void logoutRevokesRefreshToken() {
        service.logout("token");

        verify(refreshTokens).revoke("token");
    }
}
