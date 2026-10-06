package com.timelinesns.follow;

import com.timelinesns.common.Viewer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{handle}/follow")
public class FollowController {

    private final FollowService follows;

    public FollowController(FollowService follows) {
        this.follows = follows;
    }

    @PutMapping
    public FollowResponse follow(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
        return follows.follow(handle, Viewer.id(jwt));
    }

    @DeleteMapping
    public FollowResponse unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
        return follows.unfollow(handle, Viewer.id(jwt));
    }
}
