package com.timelinesns.post;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 表示用の投稿の取得（投稿詳細・タイムライン・新着件数）。
 * 作者・いいね数・コメント数をまとめて 1 クエリで取るため、JPA ではなく SQL で書く。
 *
 * <p>どの取得でも次の投稿は見せない。
 * <ul>
 *   <li>凍結されたユーザーの投稿（F-63）</li>
 *   <li>閲覧者とブロック関係（どちらからでも）にあるユーザーの投稿（F-60）</li>
 * </ul>
 * 閲覧者（viewer）が null ならゲストとして扱う。
 */
@Repository
public class PostQuery {

    /** 新着件数はこれ以上数えない（画面では「100 件以上」と同じ扱いでよい） */
    static final int MAX_NEW_COUNT = 100;

    private static final String SELECT = """
            SELECT p.id, p.body, p.created_at, u.id AS author_id, u.handle, u.display_name,
                   (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
                   (SELECT count(*) FROM comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL) AS comment_count,
                   EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :viewer) AS liked
            """;

    // ゲストのときは :viewer が NULL になり、ブロックの条件はどれにも一致しない
    private static final String FROM_VISIBLE = """
            FROM posts p
            JOIN users u ON u.id = p.user_id
            WHERE u.status = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1 FROM blocks b
                  WHERE (b.blocker_id = :viewer AND b.blocked_id = p.user_id)
                     OR (b.blocker_id = p.user_id AND b.blocked_id = :viewer))
            """;

    // ホームタイムライン（F-20）：自分とフォロー中のユーザーの投稿
    private static final String HOME = """
              AND (p.user_id = :viewer
                   OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewer))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PostQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PostResponse> findVisible(UUID postId, @Nullable UUID viewer) {
        String sql = SELECT + FROM_VISIBLE + "  AND p.id = :postId";
        List<PostResponse> found = jdbc.query(sql, params(viewer).addValue("postId", postId), PostQuery::toResponse);
        return found.stream().findFirst();
    }

    /**
     * タイムラインを新しい順に取得する。{@code cursor} を渡すと、その投稿より古いものを返す。
     * 次のページがあるか判定できるよう、呼び出し側は limit + 1 件を要求する。
     */
    public List<PostResponse> timeline(Timeline timeline, @Nullable UUID viewer, @Nullable UUID cursor, int limit) {
        return page(filter(timeline), params(viewer), cursor, limit);
    }

    /**
     * プロフィールの投稿一覧（F-40）。{@code authorId} の投稿を新しい順に返す。ページングは {@link #timeline} と同じ。
     * 作者が凍結されている、または閲覧者とブロック関係にある場合は空になる。
     */
    public List<PostResponse> byAuthor(UUID authorId, @Nullable UUID viewer, @Nullable UUID cursor, int limit) {
        return page("  AND p.user_id = :author\n", params(viewer).addValue("author", authorId), cursor, limit);
    }

    private List<PostResponse> page(String filter, MapSqlParameterSource params, @Nullable UUID cursor, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append(FROM_VISIBLE).append(filter);
        params.addValue("limit", limit);
        if (cursor != null) {
            sql.append("  AND p.id < :cursor\n");
            params.addValue("cursor", cursor);
        }
        sql.append("ORDER BY p.id DESC\nLIMIT :limit");
        return jdbc.query(sql.toString(), params, PostQuery::toResponse);
    }

    /**
     * {@code since} より新しい投稿の数（新着表示 F-22）。閲覧者自身の投稿は数えない。
     * 最大 {@link #MAX_NEW_COUNT} 件で打ち切る。
     */
    public long countNewer(Timeline timeline, @Nullable UUID viewer, UUID since) {
        String sql = "SELECT count(*) FROM (SELECT 1 " + FROM_VISIBLE + filter(timeline)
                + "  AND p.id > :since\n"
                + "  AND p.user_id IS DISTINCT FROM :viewer\n"
                + "LIMIT :max) newer";
        MapSqlParameterSource params = params(viewer).addValue("since", since).addValue("max", MAX_NEW_COUNT);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count == null ? 0 : count;
    }

    private static String filter(Timeline timeline) {
        return timeline == Timeline.HOME ? HOME : "";
    }

    // NULL のときも PostgreSQL が型を決められるよう、型を明示して渡す
    private static MapSqlParameterSource params(@Nullable UUID viewer) {
        return new MapSqlParameterSource().addValue("viewer", viewer, Types.OTHER);
    }

    private static PostResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        return new PostResponse(
                rs.getObject("id", UUID.class),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant(),
                new PostResponse.Author(
                        rs.getObject("author_id", UUID.class),
                        rs.getString("handle"),
                        rs.getString("display_name")),
                rs.getLong("like_count"),
                rs.getLong("comment_count"),
                rs.getBoolean("liked"));
    }

    /**
     * タイムラインの種類。
     */
    public enum Timeline {
        /** 全ユーザーの投稿（F-21） */
        GLOBAL,
        /** 自分とフォロー中のユーザーの投稿（F-20） */
        HOME
    }
}
