package com.timelinesns.notification;

import com.timelinesns.post.PostResponse;
import com.timelinesns.storage.MediaUrls;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 通知一覧（F-50）・未読数（F-51）・既読化。
 * 一覧は、操作した人・投稿・コメントをまとめて 1 つの SQL で取る（件数によらず SQL の回数は変わらない）。
 *
 * <p>操作した人が凍結されている、または受け取る人とブロック関係（どちらからでも）にある通知は、
 * 一覧にも未読数にも出さない（F-60・F-63）。
 */
@Repository
public class NotificationQuery {

    /** 未読数はこれ以上数えない（画面では「99+」と表示する） */
    static final int MAX_UNREAD = 100;

    private static final String FROM = """
            FROM notifications n
            JOIN users a ON a.id = n.actor_id
            """;

    private static final String WHERE_VISIBLE = """
            WHERE n.recipient_id = :me
              AND a.status = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1 FROM blocks b
                  WHERE (b.blocker_id = :me AND b.blocked_id = a.id)
                     OR (b.blocker_id = a.id AND b.blocked_id = :me))
            """;

    // 投稿・コメントは消えていれば ON DELETE CASCADE で通知ごと消える。論理削除のコメントは本文を返さない
    private static final String LIST = """
            SELECT n.id, n.type, n.created_at, n.read_at IS NULL AS unread,
                   a.id AS actor_id, a.handle, a.display_name, a.avatar_key,
                   p.id AS post_id, p.body AS post_body,
                   (SELECT i.thumbnail_key FROM post_images i WHERE i.post_id = p.id
                    ORDER BY i.sort_order LIMIT 1) AS post_thumbnail,
                   c.id AS comment_id, CASE WHEN c.deleted_at IS NULL THEN c.body END AS comment_body
            """ + FROM + """
            LEFT JOIN posts p ON p.id = n.post_id
            LEFT JOIN comments c ON c.id = n.comment_id
            """ + WHERE_VISIBLE;

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaUrls mediaUrls;

    public NotificationQuery(NamedParameterJdbcTemplate jdbc, MediaUrls mediaUrls) {
        this.jdbc = jdbc;
        this.mediaUrls = mediaUrls;
    }

    /**
     * 新しい順に返す（id は時系列順に並ぶ UUIDv7）。{@code cursor} を渡すと、その通知より古いものを返す。
     * 次のページがあるか判定できるよう、呼び出し側は limit + 1 件を要求する。
     */
    public List<NotificationResponse> list(UUID me, @Nullable UUID cursor, int limit) {
        MapSqlParameterSource params = params(me).addValue("limit", limit);
        String sql = LIST;
        if (cursor != null) {
            sql += "  AND n.id < :cursor\n";
            params.addValue("cursor", cursor);
        }
        return jdbc.query(sql + "ORDER BY n.id DESC\nLIMIT :limit", params, this::toResponse);
    }

    /**
     * 未読の数。{@link #MAX_UNREAD} 件で打ち切る。
     */
    public long unreadCount(UUID me) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT 1 " + FROM + WHERE_VISIBLE
                        + "  AND n.read_at IS NULL\nLIMIT :max) unread",
                params(me).addValue("max", MAX_UNREAD), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 既読にする。{@code until} を渡すと、その通知以前（画面に表示した分）だけを既読にする。
     * 一覧を開いている間に届いた通知を、見ないまま既読にしないため。
     */
    public void markRead(UUID me, @Nullable UUID until) {
        String sql = "UPDATE notifications SET read_at = now() WHERE recipient_id = :me AND read_at IS NULL";
        MapSqlParameterSource params = params(me);
        if (until != null) {
            sql += " AND id <= :until";
            params.addValue("until", until);
        }
        jdbc.update(sql, params);
    }

    private static MapSqlParameterSource params(UUID me) {
        return new MapSqlParameterSource().addValue("me", me);
    }

    private NotificationResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        UUID postId = rs.getObject("post_id", UUID.class);
        UUID commentId = rs.getObject("comment_id", UUID.class);
        return new NotificationResponse(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("unread"),
                new PostResponse.Author(
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("handle"),
                        rs.getString("display_name"),
                        mediaUrls.url(rs.getString("avatar_key"))),
                postId == null ? null : new NotificationResponse.Post(
                        postId, rs.getString("post_body"), mediaUrls.url(rs.getString("post_thumbnail"))),
                commentId == null ? null : new NotificationResponse.Comment(commentId, rs.getString("comment_body")));
    }
}
