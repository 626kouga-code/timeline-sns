package com.timelinesns.notification;

import com.timelinesns.post.PostResponse;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 通知（F-50）の 1 件。
 *
 * @param type    LIKE（投稿へのいいね）/ COMMENT（投稿へのコメント）/ REPLY（コメントへの返信）/ FOLLOW（フォロー）
 * @param unread  未読か（取得した時点。既読にするのは POST /api/notifications/read）
 * @param actor   通知のもとになった操作をした人
 * @param post    対象の投稿。FOLLOW なら null
 * @param comment コメント・返信。LIKE・FOLLOW なら null
 */
public record NotificationResponse(
        UUID id,
        String type,
        Instant createdAt,
        boolean unread,
        PostResponse.Author actor,
        @Nullable Post post,
        @Nullable Comment comment) {

    /**
     * 対象の投稿の要約。
     *
     * @param body         本文（画像だけの投稿なら空文字）
     * @param thumbnailUrl 1 枚目の画像のサムネイル。画像がなければ null
     */
    public record Post(UUID id, String body, @Nullable String thumbnailUrl) {
    }

    /**
     * コメント・返信。
     *
     * @param body 本文。削除済み（返信が付いていて「削除されました」として残っている）なら null
     */
    public record Comment(UUID id, @Nullable String body) {
    }
}
