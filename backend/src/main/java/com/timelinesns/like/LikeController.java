package com.timelinesns.like;

import com.timelinesns.common.Viewer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "いいね")
@RestController
@RequestMapping("/api/posts/{postId}/likes")
public class LikeController {

    private final LikeService likes;

    public LikeController(LikeService likes) {
        this.likes = likes;
    }

    @Operation(summary = "いいね", description = "冪等")
    @PostMapping
    public LikeResponse like(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return likes.like(postId, Viewer.id(jwt));
    }

    @Operation(summary = "いいね取り消し", description = "冪等")
    @DeleteMapping
    public LikeResponse unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return likes.unlike(postId, Viewer.id(jwt));
    }
}
