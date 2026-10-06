package com.timelinesns.user;

import java.time.Instant;
import java.util.UUID;

/**
 * プロフィール（F-40）。本人以外にも返すので email は含まない。
 * フォロー数・フォロワー数には凍結されたユーザーを数えない。
 *
 * @param postCount  投稿数
 * @param following  閲覧者がこのユーザーをフォローしているか（ゲストは常に false。以下も同じ）
 * @param followedBy このユーザーが閲覧者をフォローしているか
 * @param blocking   閲覧者がこのユーザーをブロックしているか
 * @param blockedBy  このユーザーが閲覧者をブロックしているか
 */
public record ProfileResponse(
        UUID id,
        String handle,
        String displayName,
        String bio,
        Instant createdAt,
        long postCount,
        long followingCount,
        long followerCount,
        boolean following,
        boolean followedBy,
        boolean blocking,
        boolean blockedBy) {
}
