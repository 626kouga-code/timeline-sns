package com.timelinesns.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * タイムライン・投稿詳細で返す投稿。
 *
 * @param images 画像（0〜4 枚、投稿したときの順）
 * @param liked  ログイン中のユーザーがいいねしているか（ゲストは常に false）
 */
public record PostResponse(
        UUID id,
        String body,
        List<Image> images,
        Instant createdAt,
        Author author,
        long likeCount,
        long commentCount,
        boolean liked) {

    public PostResponse {
        images = List.copyOf(images);
    }

    /**
     * 投稿者。email など本人にしか見せない項目は含めない。{@code avatarUrl} はアイコン画像の URL（未設定なら null）。
     */
    public record Author(UUID id, String handle, String displayName, @Nullable String avatarUrl) {
    }

    /**
     * 投稿の画像。
     *
     * @param url          拡大表示用（長辺 2048px まで。GIF は元のファイル）
     * @param thumbnailUrl 一覧用（長辺 640px まで）
     * @param width        拡大表示用の幅（px）
     * @param height       拡大表示用の高さ（px）
     */
    public record Image(String url, String thumbnailUrl, int width, int height) {
    }
}
