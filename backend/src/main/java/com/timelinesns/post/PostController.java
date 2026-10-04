package com.timelinesns.post;

import com.timelinesns.common.Viewer;
import jakarta.validation.Valid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(posts.create(Viewer.id(jwt), request));
    }

    // ゲストも閲覧できる（SecurityConfig で GET /api/posts/** を許可している）
    @GetMapping("/{id}")
    public PostResponse get(@AuthenticationPrincipal @Nullable Jwt jwt, @PathVariable UUID id) {
        return posts.get(id, Viewer.idOrNull(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        posts.delete(id, Viewer.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
