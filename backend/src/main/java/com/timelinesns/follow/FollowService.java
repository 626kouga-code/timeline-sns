package com.timelinesns.follow;

import com.timelinesns.common.ApiException;
import com.timelinesns.notification.NotificationService;
import com.timelinesns.user.UserQuery;
import com.timelinesns.user.UserQuery.Target;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * フォロー・解除（F-42）。どちらも冪等で、二重に送ってもエラーにしない。
 * SQL の回数はフォロワー数によらず固定（本人と相手の確認・書き込み・通知・集計）。
 */
@Service
public class FollowService {

    private final NamedParameterJdbcTemplate jdbc;
    private final UserQuery userQuery;
    private final UserRepository users;
    private final NotificationService notifications;

    public FollowService(NamedParameterJdbcTemplate jdbc, UserQuery userQuery, UserRepository users,
            NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.userQuery = userQuery;
        this.users = users;
    }

    @Transactional
    public FollowResponse follow(String handle, UUID userId) {
        Target target = findTarget(handle, userId);
        if (target.id().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_FOLLOW_SELF", "自分自身はフォローできません");
        }
        // ブロック関係にある相手とはフォローし合えない（F-60）
        if (target.blocked()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLOCKED", "このユーザーはフォローできません");
        }
        int added = jdbc.update(
                "INSERT INTO follows (follower_id, followee_id) VALUES (:userId, :targetId) ON CONFLICT DO NOTHING",
                params(userId, target.id()));
        // 二重に送られたとき（すでにフォロー済み）は通知を増やさない
        if (added == 1) {
            notifications.followed(target.id(), userId);
        }
        return state(userId, target.id());
    }

    @Transactional
    public FollowResponse unfollow(String handle, UUID userId) {
        Target target = findTarget(handle, userId);
        jdbc.update("DELETE FROM follows WHERE follower_id = :userId AND followee_id = :targetId",
                params(userId, target.id()));
        notifications.unfollowed(target.id(), userId);
        return state(userId, target.id());
    }

    private Target findTarget(String handle, UUID userId) {
        // トークンは有効でも、発行後に退会したユーザーは存在しない
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません");
        }
        return userQuery.findTarget(handle, userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "このユーザーは存在しないか、表示できません"));
    }

    // フォロワー数はプロフィールと同じく、凍結されたユーザーを数えない
    private FollowResponse state(UUID userId, UUID targetId) {
        return jdbc.queryForObject("""
                SELECT count(*) AS follower_count, coalesce(bool_or(f.follower_id = :userId), false) AS following
                FROM follows f
                JOIN users u ON u.id = f.follower_id
                WHERE f.followee_id = :targetId AND u.status = 'ACTIVE'
                """, params(userId, targetId),
                (rs, rowNum) -> new FollowResponse(rs.getBoolean("following"), rs.getLong("follower_count")));
    }

    private static MapSqlParameterSource params(UUID userId, UUID targetId) {
        return new MapSqlParameterSource().addValue("userId", userId).addValue("targetId", targetId);
    }
}
