package com.timelinesns.post;

import com.timelinesns.common.MaxCodePoints;
import jakarta.validation.constraints.NotBlank;

/**
 * 投稿作成（F-10）の入力。multipart/form-data で受け取る（画像 #14 は images パートとして追加する）。
 * 画像の投稿ができるまでは本文が必須。
 */
public record CreatePostRequest(
        @NotBlank(message = "本文を入力してください")
        @MaxCodePoints(value = PostService.MAX_BODY, message = "本文は 280 文字以内にしてください")
        String body) {
}
