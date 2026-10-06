package com.timelinesns.comment;

import com.timelinesns.post.PostResponse;
import com.timelinesns.storage.MediaUrls;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * コメントの読み書き。N+1 を起こさないよう JPA の関連は使わず、作者・凍結・ブロック関係まで 1 つの SQL で取る。
 * どのメソッドもコメントの件数や返信の深さによらず SQL は 1 回。
 */
@Repository
public class CommentQuery {

    // 作者が凍結されている、または閲覧者とブロック関係にあるコメントは hidden（その返信ごと見せない）。
    // 退会したユーザー（user_id が NULL）のコメントは hidden ではなく削除済みとして扱う
    private static final String SELECT = """
            SELECT c.id, c.post_id, c.parent_id, c.body, c.created_at, c.user_id, u.handle, u.display_name,
                   u.avatar_key,
                   (c.deleted_at IS NOT NULL OR c.user_id IS NULL) AS removed,
                   coalesce(u.status <> 'ACTIVE', false)
                       OR EXISTS (
                           SELECT 1 FROM blocks b
                           WHERE (b.blocker_id = :viewer AND b.blocked_id = c.user_id)
                              OR (b.blocker_id = c.user_id AND b.blocked_id = :viewer)) AS hidden
            FROM comments c
            LEFT JOIN users u ON u.id = c.user_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaUrls mediaUrls;

    public CommentQuery(NamedParameterJdbcTemplate jdbc, MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
        this.jdbc = jdbc;
    }

    /**
     * 投稿のコメントを全件、古い順に取る。返信は必ず親より後に並ぶ（作成が後なので created_at・uuidv7 の id とも大きい）。
     */
    public List<Row> findByPost(UUID postId, @Nullable UUID viewer) {
        return jdbc.query(SELECT + "WHERE c.post_id = :postId\nORDER BY c.created_at, c.id",
                params(viewer).addValue("postId", postId), this::toRow);
    }

    public Optional<Row> findById(UUID commentId, @Nullable UUID viewer) {
        return jdbc.query(SELECT + "WHERE c.id = :id", params(viewer).addValue("id", commentId), this::toRow)
                .stream().findFirst();
    }

    public UUID insert(UUID postId, UUID userId, @Nullable UUID parentId, String body) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("userId", userId)
                .addValue("parentId", parentId, Types.OTHER)
                .addValue("body", body);
        return jdbc.queryForObject("""
                INSERT INTO comments (post_id, user_id, parent_id, body)
                VALUES (:postId, :userId, :parentId, :body)
                RETURNING id
                """, params, UUID.class);
    }

    /**
     * 削除の判断に使う情報。削除済みのコメントは対象外（空）。
     */
    public Optional<DeleteTarget> findDeleteTarget(UUID commentId) {
        return jdbc.query("""
                SELECT c.user_id, c.parent_id,
                       EXISTS (SELECT 1 FROM comments r WHERE r.parent_id = c.id) AS has_replies
                FROM comments c
                WHERE c.id = :id AND c.deleted_at IS NULL
                """, new MapSqlParameterSource("id", commentId),
                (rs, rowNum) -> new DeleteTarget(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        rs.getBoolean("has_replies")))
                .stream().findFirst();
    }

    /**
     * {@code parentId} から根元までの祖先を近い順に、再帰 CTE でまとめて取る（深さによらず 1 回）。
     */
    public List<Ancestor> findAncestors(UUID parentId) {
        return jdbc.query("""
                WITH RECURSIVE ancestors AS (
                    SELECT id, parent_id, (deleted_at IS NOT NULL OR user_id IS NULL) AS removed, 1 AS depth
                    FROM comments WHERE id = :parentId
                    UNION ALL
                    SELECT c.id, c.parent_id, (c.deleted_at IS NOT NULL OR c.user_id IS NULL), a.depth + 1
                    FROM comments c
                    JOIN ancestors a ON c.id = a.parent_id
                )
                SELECT a.id, a.removed, (SELECT count(*) FROM comments r WHERE r.parent_id = a.id) AS replies
                FROM ancestors a
                ORDER BY a.depth
                """, new MapSqlParameterSource("parentId", parentId),
                (rs, rowNum) -> new Ancestor(
                        rs.getObject("id", UUID.class), rs.getBoolean("removed"), rs.getLong("replies")));
    }

    /**
     * 論理削除。本文は残さない。
     */
    public void markDeleted(UUID commentId, Instant now) {
        jdbc.update("UPDATE comments SET deleted_at = :now, body = '' WHERE id = :id",
                new MapSqlParameterSource("id", commentId).addValue("now", Timestamp.from(now)));
    }

    /**
     * 物理削除。返信は parent_id の ON DELETE CASCADE で一緒に消える。
     */
    public void delete(UUID commentId) {
        jdbc.update("DELETE FROM comments WHERE id = :id", new MapSqlParameterSource("id", commentId));
    }

    // NULL のときも PostgreSQL が型を決められるよう、型を明示して渡す
    private static MapSqlParameterSource params(@Nullable UUID viewer) {
        return new MapSqlParameterSource().addValue("viewer", viewer, Types.OTHER);
    }

    private Row toRow(ResultSet rs, int rowNum) throws SQLException {
        UUID userId = rs.getObject("user_id", UUID.class);
        PostResponse.Author author = userId == null
                ? null
                : new PostResponse.Author(userId, rs.getString("handle"), rs.getString("display_name"),
                        mediaUrls.url(rs.getString("avatar_key")));
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant(),
                author,
                rs.getBoolean("removed"),
                rs.getBoolean("hidden"));
    }

    /**
     * コメント 1 件。{@code removed} は削除済み（退会したユーザーのものを含む）、{@code hidden} は閲覧者に見せないもの。
     */
    public record Row(UUID id, UUID postId, @Nullable UUID parentId, String body, Instant createdAt,
            PostResponse.@Nullable Author author, boolean removed, boolean hidden) {

        CommentResponse toResponse() {
            return removed
                    ? new CommentResponse(id, parentId, null, createdAt, null, true)
                    : new CommentResponse(id, parentId, body, createdAt, author, false);
        }
    }

    public record DeleteTarget(@Nullable UUID userId, @Nullable UUID parentId, boolean hasReplies) {
    }

    public record Ancestor(UUID id, boolean removed, long replies) {
    }
}
