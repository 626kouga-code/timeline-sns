package com.timelinesns.auth;

import com.timelinesns.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "認証")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService auth, RefreshTokenCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @Operation(summary = "新規登録", description = "アクセストークンを返し、リフレッシュトークンを Cookie に設定する")
    @ApiResponse(responseCode = "201", description = "登録した")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return withCookie(HttpStatus.CREATED, auth.signup(request));
    }

    @Operation(summary = "ログイン", description = "アクセストークンを返し、リフレッシュトークンを Cookie に設定する")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(HttpStatus.OK, auth.login(request));
    }

    @Operation(summary = "トークンの再発行", description = "Cookie のリフレッシュトークンで、アクセストークンとリフレッシュトークンを作り直す")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "ログインしてください");
        }
        return withCookie(HttpStatus.OK, auth.refresh(refreshToken));
    }

    @Operation(summary = "ログアウト", description = "リフレッシュトークンを無効にし、Cookie を消す")
    @ApiResponse(responseCode = "204", description = "ログアウトした")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            auth.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthService.Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString())
                .body(session.body());
    }
}
