package com.timelinesns.user;

import java.util.UUID;

/**
 * ログイン中の本人の情報。本人にだけ返すので email を含む。
 */
public record MeResponse(
        UUID id,
        String email,
        String handle,
        String displayName,
        String bio,
        Role role,
        boolean emailVerified) {

    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getHandle(),
                user.getDisplayName(),
                user.getBio(),
                user.getRole(),
                user.getEmailVerifiedAt() != null);
    }
}
