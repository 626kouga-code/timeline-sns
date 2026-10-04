package com.timelinesns.like;

import com.timelinesns.common.Viewer;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/likes")
public class LikeController {

    private final LikeService likes;

    public LikeController(LikeService likes) {
        this.likes = likes;
    }

    @PostMapping
    public LikeResponse like(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return likes.like(postId, Viewer.id(jwt));
    }

    @DeleteMapping
    public LikeResponse unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return likes.unlike(postId, Viewer.id(jwt));
    }
}
