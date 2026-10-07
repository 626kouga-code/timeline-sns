package com.timelinesns.notification;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 通知（F-50）を作る・消す。いいね・コメント・返信・フォローの処理と同じトランザクションの中で呼ぶ。
 *
 * <ul>
 *   <li>自分の投稿・コメントへの自分の操作では通知しない</li>
 *   <li>いいねの取り消し・フォロー解除では、その通知も消す（いいねを付け直しても通知が溜まらないように）</li>
 *   <li>どれも SQL 1 回。受け取る人は SQL の中で決める</li>
 * </ul>
 */
@Service
public class NotificationService {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 投稿にいいねされた（投稿者へ） */
    public void liked(UUID postId, UUID actorId) {
        jdbc.update("""
                INSERT INTO notifications (recipient_id, actor_id, type, post_id)
                SELECT p.user_id, :actor, 'LIKE', p.id FROM posts p
                WHERE p.id = :postId AND p.user_id <> :actor
                """, params(actorId).addValue("postId", postId));
    }

    public void unliked(UUID postId, UUID actorId) {
        jdbc.update("DELETE FROM notifications WHERE type = 'LIKE' AND actor_id = :actor AND post_id = :postId",
                params(actorId).addValue("postId", postId));
    }

    /** 投稿にコメントされた（投稿者へ） */
    public void commented(UUID postId, UUID commentId, UUID actorId) {
        jdbc.update("""
                INSERT INTO notifications (recipient_id, actor_id, type, post_id, comment_id)
                SELECT p.user_id, :actor, 'COMMENT', p.id, :commentId FROM posts p
                WHERE p.id = :postId AND p.user_id <> :actor
                """, params(actorId).addValue("postId", postId).addValue("commentId", commentId));
    }

    /** コメントに返信された（返信先のコメントを書いた人へ。退会済みなら通知しない） */
    public void replied(UUID postId, UUID commentId, UUID parentId, UUID actorId) {
        jdbc.update("""
                INSERT INTO notifications (recipient_id, actor_id, type, post_id, comment_id)
                SELECT c.user_id, :actor, 'REPLY', :postId, :commentId FROM comments c
                WHERE c.id = :parentId AND c.user_id IS NOT NULL AND c.user_id <> :actor
                """, params(actorId).addValue("postId", postId).addValue("commentId", commentId)
                .addValue("parentId", parentId));
    }

    /** フォローされた（相手へ） */
    public void followed(UUID targetId, UUID actorId) {
        jdbc.update("""
                INSERT INTO notifications (recipient_id, actor_id, type)
                VALUES (:target, :actor, 'FOLLOW')
                """, params(actorId).addValue("target", targetId));
    }

    public void unfollowed(UUID targetId, UUID actorId) {
        jdbc.update("DELETE FROM notifications WHERE type = 'FOLLOW' AND actor_id = :actor AND recipient_id = :target",
                params(actorId).addValue("target", targetId));
    }

    private static MapSqlParameterSource params(UUID actorId) {
        return new MapSqlParameterSource().addValue("actor", actorId);
    }
}
