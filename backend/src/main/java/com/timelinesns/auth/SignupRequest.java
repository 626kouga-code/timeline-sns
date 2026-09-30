package com.timelinesns.auth;

import com.timelinesns.common.MaxCodePoints;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新規登録（F-01）の入力。
 */
public record SignupRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "正しいメールアドレスを入力してください")
        @Size(max = 254, message = "メールアドレスが長すぎます")
        String email,

        // BCrypt は 72 バイトまでしか使わないため、上限もそこに合わせる
        @NotNull(message = "パスワードを入力してください")
        @Size(min = 8, max = 72, message = "パスワードは 8〜72 文字で入力してください")
        String password,

        @NotNull(message = "ユーザーIDを入力してください")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,15}$", message = "ユーザーIDは英数字と _ で 3〜15 文字にしてください")
        String handle,

        @NotBlank(message = "表示名を入力してください")
        @MaxCodePoints(value = 50, message = "表示名は 50 文字以内にしてください")
        String displayName,

        @AssertTrue(message = "利用規約とプライバシーポリシーへの同意が必要です")
        boolean agreedToTerms) {
}
