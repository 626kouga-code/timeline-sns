package com.timelinesns.post;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * タイムライン・投稿詳細で返す投稿。画像は #14 で追加する。
 *
 * @param liked ログイン中のユーザーがいいねしているか（ゲストは常に false）
 */
public record PostResponse(
        UUID id,
        String body,
        Instant createdAt,
        Author author,
        long likeCount,
        long commentCount,
        boolean liked) {

    /**
     * 投稿者。email など本人にしか見せない項目は含めない。{@code avatarUrl} はアイコン画像の URL（未設定なら null）。
     */
    public record Author(UUID id, String handle, String displayName, @Nullable String avatarUrl) {
    }
}
