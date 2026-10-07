package com.timelinesns.like;

import com.timelinesns.common.ApiException;
import com.timelinesns.notification.NotificationService;
import com.timelinesns.post.PostQuery;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * いいね・取り消し（F-30）。どちらも冪等で、二重に送ってもエラーにしない。
 * SQL の回数はいいね数によらず固定（投稿の確認・書き込み・通知・集計）。
 */
@Service
public class LikeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PostQuery posts;
    private final UserRepository users;
    private final NotificationService notifications;

    public LikeService(NamedParameterJdbcTemplate jdbc, PostQuery posts, UserRepository users,
            NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.posts = posts;
        this.users = users;
    }

    @Transactional
    public LikeResponse like(UUID postId, UUID userId) {
        ensureCanReact(postId, userId);
        int added = jdbc.update("INSERT INTO likes (user_id, post_id) VALUES (:userId, :postId) ON CONFLICT DO NOTHING",
                params(postId, userId));
        // 二重に送られたとき（すでにいいね済み）は通知を増やさない
        if (added == 1) {
            notifications.liked(postId, userId);
        }
        return state(postId, userId);
    }

    @Transactional
    public LikeResponse unlike(UUID postId, UUID userId) {
        ensureCanReact(postId, userId);
        jdbc.update("DELETE FROM likes WHERE user_id = :userId AND post_id = :postId", params(postId, userId));
        notifications.unliked(postId, userId);
        return state(postId, userId);
    }

    // 見えない投稿（凍結ユーザー・ブロック関係）にはいいねできない（F-60・F-63）
    private void ensureCanReact(UUID postId, UUID userId) {
        // トークンは有効でも、発行後に退会したユーザーは存在しない
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません");
        }
        if (posts.findVisible(postId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "この投稿は存在しないか、表示できません");
        }
    }

    private LikeResponse state(UUID postId, UUID userId) {
        return jdbc.queryForObject("""
                SELECT count(*) AS like_count, coalesce(bool_or(user_id = :userId), false) AS liked
                FROM likes WHERE post_id = :postId
                """, params(postId, userId),
                (rs, rowNum) -> new LikeResponse(rs.getBoolean("liked"), rs.getLong("like_count")));
    }

    private static MapSqlParameterSource params(UUID postId, UUID userId) {
        return new MapSqlParameterSource().addValue("postId", postId).addValue("userId", userId);
    }
}
