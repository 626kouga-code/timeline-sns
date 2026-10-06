package com.timelinesns.user;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * ユーザー一覧（フォロー・フォロワー一覧など）の 1 件。
 *
 * @param avatarUrl  アイコン画像の URL。未設定なら null
 * @param following  閲覧者がこのユーザーをフォローしているか（ゲストは常に false。以下も同じ）
 * @param followedBy このユーザーが閲覧者をフォローしているか
 */
public record UserSummaryResponse(
        UUID id,
        String handle,
        String displayName,
        String bio,
        @Nullable String avatarUrl,
        boolean following,
        boolean followedBy) {
}
