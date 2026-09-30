package com.timelinesns.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * ログイン（F-03）の入力。形式の細かいチェックはせず、照合に失敗したら同じエラーを返す。
 */
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        String email,

        @NotBlank(message = "パスワードを入力してください")
        String password) {
}
