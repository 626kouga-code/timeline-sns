package com.timelinesns.comment;

import com.timelinesns.common.Viewer;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    // ゲストも閲覧できる（SecurityConfig で GET /api/posts/** を許可している）
    @GetMapping("/posts/{postId}/comments")
    public List<CommentResponse> list(@AuthenticationPrincipal @Nullable Jwt jwt, @PathVariable UUID postId) {
        return comments.list(postId, Viewer.idOrNull(jwt));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comments.create(postId, Viewer.id(jwt), request));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        comments.delete(id, Viewer.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
