package com.timelinesns.auth;

import com.timelinesns.user.MeResponse;

/**
 * 登録・ログイン・リフレッシュの応答。リフレッシュトークンは本文には含めず、Cookie でだけ渡す。
 *
 * @param accessToken アクセストークン（JWT）。フロントはメモリにだけ保持する
 * @param tokenType   常に Bearer
 * @param expiresIn   アクセストークンの有効期間（秒）
 * @param user        ログインしたユーザー
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {
}
