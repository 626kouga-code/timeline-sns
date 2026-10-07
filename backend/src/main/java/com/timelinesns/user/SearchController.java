package com.timelinesns.user;

import com.timelinesns.common.ApiException;
import com.timelinesns.common.Viewer;
import com.timelinesns.timeline.PageResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザー検索（F-44）。ゲストも使える（SecurityConfig で GET /api/search/** を許可している）。
 * パスを /api/users/search にしないのは、ユーザーID「search」のプロフィール（/api/users/{handle}）と衝突するため。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    static final int MAX_QUERY = 50;

    private final UserQuery users;

    public SearchController(UserQuery users) {
        this.users = users;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> users(@AuthenticationPrincipal @Nullable Jwt jwt,
            @RequestParam String q, @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_LIMIT) int limit) {
        String query = q.strip();
        if (query.isEmpty()) {
            throw invalid("キーワードを入力してください");
        }
        if (query.codePointCount(0, query.length()) > MAX_QUERY) {
            throw invalid("キーワードは 50 文字以内にしてください");
        }
        int size = PageResponse.clampLimit(limit);
        return PageResponse.of(users.search(query, Viewer.idOrNull(jwt), cursor, size + 1), size,
                UserSummaryResponse::id);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, "q");
    }
}
