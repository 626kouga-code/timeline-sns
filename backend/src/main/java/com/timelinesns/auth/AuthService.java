package com.timelinesns.auth;

import com.timelinesns.common.ApiException;
import com.timelinesns.storage.MediaUrls;
import com.timelinesns.user.MeResponse;
import com.timelinesns.user.User;
import com.timelinesns.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新規登録（F-01）・ログイン／ログアウト（F-03）・トークンの再発行。
 * 登録・ログインが成功したら、アクセストークンとリフレッシュトークンを発行する。
 */
@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final MediaUrls mediaUrls;
    private final Clock clock;

    // 存在しないメールアドレスでも照合と同程度の時間をかけ、応答時間から登録の有無を推測されにくくする
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
            RefreshTokenService refreshTokens, MediaUrls mediaUrls, Clock clock) {
        this.users = users;
        this.mediaUrls = mediaUrls;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 登録して、そのままログイン状態にする。メールアドレスの確認（F-02）はまだなので未確認のまま。
     */
    @Transactional
    public Session signup(SignupRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "パスワードが長すぎます", "password");
        }
        String email = request.email().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "このメールアドレスは既に登録されています", "email");
        }
        if (users.existsByHandleIgnoreCase(request.handle())) {
            throw new ApiException(HttpStatus.CONFLICT, "HANDLE_TAKEN", "このユーザーIDは既に使われています", "handle");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.handle(),
                request.displayName().trim(), clock.instant());
        // 同時登録で一意制約に違反した場合に、ここで例外にする（ApiExceptionHandler が 409 にする）
        users.saveAndFlush(user);
        return startSession(user);
    }

    @Transactional
    public Session login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = user == null || user.getPasswordHash() == null ? dummyPasswordHash : user.getPasswordHash();
        boolean matches = !exceedsBcryptLimit(request.password()) && passwordEncoder.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "メールアドレスまたはパスワードが正しくありません");
        }
        // パスワードの照合後に確認し、凍結の有無から登録の有無が分からないようにする
        ensureActive(user);
        return startSession(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
        User user = users.findById(rotation.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "ログインの有効期限が切れました。もう一度ログインしてください"));
        if (user.isSuspended()) {
            refreshTokens.revokeAll(user.getId());
        }
        ensureActive(user);
        return new Session(response(user), rotation.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private Session startSession(User user) {
        return new Session(response(user), refreshTokens.issue(user.getId()));
    }

    private AuthResponse response(User user) {
        return new AuthResponse(accessTokens.issue(user), "Bearer", accessTokens.expiresInSeconds(),
                MeResponse.from(user, mediaUrls));
    }

    // BCrypt は先頭 72 バイトしか使わない（超えると Spring Security は例外にする）。文字数ではなくバイト数で確認する
    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    private static void ensureActive(User user) {
        if (user.isSuspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "このアカウントは凍結されています");
        }
    }

    /**
     * 応答本文と、Cookie で渡すリフレッシュトークン。
     */
    public record Session(AuthResponse body, String refreshToken) {
    }
}
