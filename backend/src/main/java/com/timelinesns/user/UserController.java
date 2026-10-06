package com.timelinesns.user;

import com.timelinesns.common.ApiException;
import com.timelinesns.common.Viewer;
import com.timelinesns.post.PostQuery;
import com.timelinesns.post.PostResponse;
import com.timelinesns.timeline.PageResponse;
import com.timelinesns.user.UserQuery.FollowList;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * プロフィール（F-40）・投稿一覧と、フォロー・フォロワー一覧（F-43）。
 * どれもゲストも見られる（SecurityConfig で GET /api/users/** を許可している）。
 * 凍結されたユーザー（F-63）は存在しないものとして 404 にする。
 */
@RestController
@RequestMapping("/api/users/{handle}")
public class UserController {

    private final UserQuery users;
    private final PostQuery posts;

    public UserController(UserQuery users, PostQuery posts) {
        this.users = users;
        this.posts = posts;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ProfileResponse profile(@AuthenticationPrincipal @Nullable Jwt jwt, @PathVariable String handle) {
        return users.findProfile(handle, Viewer.idOrNull(jwt)).orElseThrow(UserController::notFound);
    }

    /**
     * 投稿一覧。閲覧者とブロック関係にあるユーザーなら空になる（F-60）。
     */
    @GetMapping("/posts")
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> posts(@AuthenticationPrincipal @Nullable Jwt jwt, @PathVariable String handle,
            @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_LIMIT) int limit) {
        UUID viewer = Viewer.idOrNull(jwt);
        UUID owner = ownerId(handle, viewer);
        int size = PageResponse.clampLimit(limit);
        return PageResponse.of(posts.byAuthor(owner, viewer, cursor, size + 1), size, PostResponse::id);
    }

    @GetMapping("/following")
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> following(@AuthenticationPrincipal @Nullable Jwt jwt,
            @PathVariable String handle, @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_LIMIT) int limit) {
        return followList(FollowList.FOLLOWING, Viewer.idOrNull(jwt), handle, cursor, limit);
    }

    @GetMapping("/followers")
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> followers(@AuthenticationPrincipal @Nullable Jwt jwt,
            @PathVariable String handle, @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_LIMIT) int limit) {
        return followList(FollowList.FOLLOWERS, Viewer.idOrNull(jwt), handle, cursor, limit);
    }

    private PageResponse<UserSummaryResponse> followList(FollowList kind, @Nullable UUID viewer, String handle,
            @Nullable UUID cursor, int limit) {
        UUID owner = ownerId(handle, viewer);
        int size = PageResponse.clampLimit(limit);
        return PageResponse.of(users.followList(kind, owner, viewer, cursor, size + 1), size,
                UserSummaryResponse::id);
    }

    private UUID ownerId(String handle, @Nullable UUID viewer) {
        return users.findTarget(handle, viewer).orElseThrow(UserController::notFound).id();
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "このユーザーは存在しないか、表示できません");
    }
}
