package com.timelinesns.user;

import com.timelinesns.storage.MediaUrls;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * ログイン中の本人の情報。本人にだけ返すので email を含む。
 *
 * @param avatarUrl アイコン画像の URL。未設定なら null
 */
public record MeResponse(
        UUID id,
        String email,
        String handle,
        String displayName,
        String bio,
        @Nullable String avatarUrl,
        Role role,
        boolean emailVerified) {

    public static MeResponse from(User user, MediaUrls mediaUrls) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getHandle(),
                user.getDisplayName(),
                user.getBio(),
                mediaUrls.url(user.getAvatarKey()),
                user.getRole(),
                user.getEmailVerifiedAt() != null);
    }
}
