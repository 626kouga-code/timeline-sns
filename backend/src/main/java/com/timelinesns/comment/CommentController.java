package com.timelinesns.comment;

import com.timelinesns.common.Viewer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "コメント")
@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    // ゲストも閲覧できる（SecurityConfig で GET /api/posts/** を許可している）
    @Operation(summary = "コメント一覧", description = "全件を古い順の平らな配列で返す。スレッドは画面で組み立てる")
    @GetMapping("/posts/{postId}/comments")
    public List<CommentResponse> list(@AuthenticationPrincipal @Nullable Jwt jwt, @PathVariable UUID postId) {
        return comments.list(postId, Viewer.idOrNull(jwt));
    }

    @Operation(summary = "コメント作成", description = "parentId を指定すると、そのコメントへの返信になる")
    @ApiResponse(responseCode = "201", description = "作成した")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comments.create(postId, Viewer.id(jwt), request));
    }

    @Operation(summary = "コメント削除", description = "本人のみ。返信が付いていれば論理削除、なければ物理削除する")
    @ApiResponse(responseCode = "204", description = "削除した")
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        comments.delete(id, Viewer.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
