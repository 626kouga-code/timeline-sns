package com.timelinesns.user;

import com.timelinesns.common.MaxCodePoints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * プロフィール編集（F-41）の入力。アイコン画像は画像の保存先（#40）が決まってから追加する。
 * 画面は常に両方を送るので、どちらも必須にする（自己紹介を消すときは空文字）。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "表示名を入力してください")
        @MaxCodePoints(value = User.MAX_DISPLAY_NAME, message = "表示名は 50 文字以内にしてください")
        String displayName,

        @NotNull(message = "自己紹介を指定してください")
        @MaxCodePoints(value = User.MAX_BIO, message = "自己紹介は 160 文字以内にしてください")
        String bio) {
}
