package com.timelinesns.post;

import com.timelinesns.common.Viewer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "投稿")
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @Operation(summary = "投稿作成", description = "本文と画像 0〜4 枚（JPEG / PNG / WebP / GIF、1 枚 5MB まで）。"
            + "画像があれば本文は空でもよい")
    @ApiResponse(responseCode = "201", description = "作成した")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(posts.create(Viewer.id(jwt), request));
    }

    // ゲストも閲覧できる（SecurityConfig で GET /api/posts/** を許可している）
    @Operation(summary = "投稿の取得")
    @GetMapping("/{id}")
    public PostResponse get(@AuthenticationPrincipal @Nullable Jwt jwt, @PathVariable UUID id) {
        return posts.get(id, Viewer.idOrNull(jwt));
    }

    @Operation(summary = "投稿削除", description = "本人のみ。保存先の画像も消す")
    @ApiResponse(responseCode = "204", description = "削除した")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        posts.delete(id, Viewer.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
