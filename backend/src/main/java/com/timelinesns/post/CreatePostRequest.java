package com.timelinesns.post;

import com.timelinesns.common.MaxCodePoints;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿作成（F-10・F-11）の入力。multipart/form-data で受け取る。
 * 画像があれば本文なしでも投稿できる（本文・画像のどちらもない場合は {@link PostService} で弾く）。
 *
 * @param body   本文。送られなければ空文字と同じ
 * @param images 画像（images パートを 0〜4 個）
 */
public record CreatePostRequest(
        @MaxCodePoints(value = PostService.MAX_BODY, message = "本文は 280 文字以内にしてください")
        @Nullable String body,

        @Size(max = PostService.MAX_IMAGES, message = "画像は 4 枚までです")
        @Nullable List<MultipartFile> images) {

    public CreatePostRequest {
        images = images == null ? null : List.copyOf(images);
    }

    /** 前後の空白を除いた本文 */
    public String strippedBody() {
        return body == null ? "" : body.strip();
    }

    /** 空のパート（ファイルを選ばずに送られたもの）を除いた画像 */
    public List<MultipartFile> files() {
        return images == null ? List.of() : images.stream().filter(file -> !file.isEmpty()).toList();
    }
}
