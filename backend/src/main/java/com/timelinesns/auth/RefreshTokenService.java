package com.timelinesns.auth;

import com.timelinesns.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・ローテーション・失効を扱う。
 * 使うたびに新しいトークンに入れ替え、失効済みのトークンが再利用されたら盗まれたとみなして
 * そのユーザーのトークンをすべて失効させる。
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 新しいトークンを発行して平文を返す（DB にはハッシュだけを保存する）。
     */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new RefreshToken(userId, hash(raw), now.plus(properties.refreshTokenTtl()), now));
        return raw;
    }

    /**
     * トークンを検証して失効させ、同じユーザーに新しいトークンを発行する。
     * 再利用を検知したときの全失効は、エラーを返してもコミットする必要があるので noRollbackFor を付けている。
     *
     * @return トークンの持ち主と新しいトークン
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String raw) {
        RefreshToken current = tokens.findByTokenHash(hash(raw)).orElseThrow(RefreshTokenService::invalid);
        Instant now = clock.instant();
        if (current.isRevoked()) {
            // 失効済みのトークンが使われた＝盗まれた可能性がある。本人のセッションもすべて無効にする
            tokens.revokeAllByUserId(current.getUserId(), now);
            throw invalid();
        }
        if (current.isExpired(now)) {
            throw invalid();
        }
        current.revoke(now);
        return new Rotation(current.getUserId(), issue(current.getUserId()));
    }

    /**
     * ログアウト用。未知・失効済みのトークンでもエラーにしない。
     */
    @Transactional
    public void revoke(String raw) {
        tokens.findByTokenHash(hash(raw)).ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllByUserId(userId, clock.instant());
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 はすべての Java 実装で必ず使える
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "ログインの有効期限が切れました。もう一度ログインしてください");
    }

    public record Rotation(UUID userId, String refreshToken) {
    }
}
