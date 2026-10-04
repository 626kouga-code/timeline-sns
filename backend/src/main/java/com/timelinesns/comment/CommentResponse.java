package com.timelinesns.comment;

import com.timelinesns.post.PostResponse;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * コメント。スレッドの組み立て（返信の並べ方）はフロントで行うので、親の ID だけ返す。
 *
 * @param parentId 返信先のコメント。投稿への直接のコメントなら null
 * @param body     本文。削除済みなら null
 * @param author   作者。削除済み（退会したユーザーのものを含む）なら null
 * @param deleted  削除済み（返信が付いているので「このコメントは削除されました」として残っている）
 */
public record CommentResponse(
        UUID id,
        @Nullable UUID parentId,
        @Nullable String body,
        Instant createdAt,
        PostResponse.@Nullable Author author,
        boolean deleted) {
}
