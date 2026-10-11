package com.timelinesns.follow;

import com.timelinesns.common.Viewer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "フォロー")
@RestController
@RequestMapping("/api/users/{handle}/follow")
public class FollowController {

    private final FollowService follows;

    public FollowController(FollowService follows) {
        this.follows = follows;
    }

    @Operation(summary = "フォロー", description = "冪等。自分自身は 400、ブロック関係にある相手は 403")
    @PutMapping
    public FollowResponse follow(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
        return follows.follow(handle, Viewer.id(jwt));
    }

    @Operation(summary = "フォロー解除", description = "冪等")
    @DeleteMapping
    public FollowResponse unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
        return follows.unfollow(handle, Viewer.id(jwt));
    }
}
