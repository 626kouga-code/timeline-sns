package com.timelinesns.comment;

import com.timelinesns.common.MaxCodePoints;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * コメント（F-31）・返信（F-32）の入力。
 *
 * @param parentId 返信先のコメント。投稿へ直接コメントするなら null
 */
public record CreateCommentRequest(
        @NotBlank(message = "本文を入力してください")
        @MaxCodePoints(value = CommentService.MAX_BODY, message = "本文は 280 文字以内にしてください")
        String body,

        @Nullable UUID parentId) {
}
