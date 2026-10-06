package com.timelinesns.user;

import com.timelinesns.storage.MediaUrls;
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
 * 表示用のユーザーの取得（プロフィール・フォロー一覧）。
 * フォロー数や閲覧者との関係までまとめて 1 クエリで取るため、JPA ではなく SQL で書く。
 *
 * <p>凍結されたユーザー（F-63）は存在しないものとして扱う。フォロー数にも数えない。
 * 閲覧者（viewer）が null ならゲストとして扱い、関係を表す項目はすべて false になる。
 */
@Repository
public class UserQuery {

    // ハンドルは大文字小文字を区別せずに一意なので、検索も lower() で揃える
    private static final String ACTIVE_BY_HANDLE = "lower(u.handle) = lower(:handle) AND u.status = 'ACTIVE'";

    private static final String PROFILE = """
            SELECT u.id, u.handle, u.display_name, u.bio, u.avatar_key, u.created_at,
                   (SELECT count(*) FROM posts p WHERE p.user_id = u.id) AS post_count,
                   (SELECT count(*) FROM follows f JOIN users t ON t.id = f.followee_id
                    WHERE f.follower_id = u.id AND t.status = 'ACTIVE') AS following_count,
                   (SELECT count(*) FROM follows f JOIN users t ON t.id = f.follower_id
                    WHERE f.followee_id = u.id AND t.status = 'ACTIVE') AS follower_count,
                   EXISTS (SELECT 1 FROM follows f
                           WHERE f.follower_id = :viewer AND f.followee_id = u.id) AS following,
                   EXISTS (SELECT 1 FROM follows f
                           WHERE f.follower_id = u.id AND f.followee_id = :viewer) AS followed_by,
                   EXISTS (SELECT 1 FROM blocks b
                           WHERE b.blocker_id = :viewer AND b.blocked_id = u.id) AS blocking,
                   EXISTS (SELECT 1 FROM blocks b
                           WHERE b.blocker_id = u.id AND b.blocked_id = :viewer) AS blocked_by
            FROM users u
            WHERE\s""" + ACTIVE_BY_HANDLE;

    private static final String TARGET = """
            SELECT u.id,
                   EXISTS (
                       SELECT 1 FROM blocks b
                       WHERE (b.blocker_id = :viewer AND b.blocked_id = u.id)
                          OR (b.blocker_id = u.id AND b.blocked_id = :viewer)) AS blocked
            FROM users u
            WHERE\s""" + ACTIVE_BY_HANDLE;

    // フォロー一覧の 1 行。{listed} は一覧に並ぶ側、{own} は一覧の持ち主の側の列名（follows の列）に置き換える。
    // 凍結されたユーザーと、閲覧者とブロック関係にあるユーザーは並べない。
    // 並びはフォローした日時の新しい順。cursor は前のページの最後のユーザーで、その行のフォロー日時より前から続ける
    private static final String FOLLOW_LIST = """
            SELECT u.id, u.handle, u.display_name, u.bio, u.avatar_key,
                   EXISTS (SELECT 1 FROM follows v
                           WHERE v.follower_id = :viewer AND v.followee_id = u.id) AS following,
                   EXISTS (SELECT 1 FROM follows v
                           WHERE v.follower_id = u.id AND v.followee_id = :viewer) AS followed_by
            FROM follows f
            JOIN users u ON u.id = f.{listed}
            WHERE f.{own} = :owner
              AND u.status = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1 FROM blocks b
                  WHERE (b.blocker_id = :viewer AND b.blocked_id = u.id)
                     OR (b.blocker_id = u.id AND b.blocked_id = :viewer))
            """;

    private static final String FOLLOW_LIST_CURSOR = """
              AND (f.created_at, f.{listed}) < (
                  SELECT c.created_at, c.{listed} FROM follows c WHERE c.{own} = :owner AND c.{listed} = :cursor)
            """;

    private static final String FOLLOW_LIST_ORDER = """
            ORDER BY f.created_at DESC, f.{listed} DESC
            LIMIT :limit""";

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaUrls mediaUrls;

    public UserQuery(NamedParameterJdbcTemplate jdbc, MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
        this.jdbc = jdbc;
    }

    public Optional<ProfileResponse> findProfile(String handle, @Nullable UUID viewer) {
        return jdbc.query(PROFILE, params(viewer).addValue("handle", handle), this::toProfile)
                .stream().findFirst();
    }

    /**
     * フォロー・解除の対象。{@code blocked} は閲覧者とブロック関係（どちらからでも）にあるか。
     */
    public Optional<Target> findTarget(String handle, @Nullable UUID viewer) {
        return jdbc.query(TARGET, params(viewer).addValue("handle", handle),
                (rs, rowNum) -> new Target(rs.getObject("id", UUID.class), rs.getBoolean("blocked")))
                .stream().findFirst();
    }

    /**
     * フォロー・フォロワー一覧（F-43）。フォローした日時の新しい順に返す。
     * 次のページがあるか判定できるよう、呼び出し側は limit + 1 件を要求する。
     */
    public List<UserSummaryResponse> followList(FollowList kind, UUID owner, @Nullable UUID viewer,
            @Nullable UUID cursor, int limit) {
        String template = FOLLOW_LIST + (cursor == null ? "" : FOLLOW_LIST_CURSOR) + FOLLOW_LIST_ORDER;
        String sql = kind == FollowList.FOLLOWING
                ? template.replace("{listed}", "followee_id").replace("{own}", "follower_id")
                : template.replace("{listed}", "follower_id").replace("{own}", "followee_id");
        MapSqlParameterSource params = params(viewer).addValue("owner", owner).addValue("limit", limit);
        if (cursor != null) {
            params.addValue("cursor", cursor);
        }
        return jdbc.query(sql, params, this::toSummary);
    }

    // NULL のときも PostgreSQL が型を決められるよう、型を明示して渡す
    private static MapSqlParameterSource params(@Nullable UUID viewer) {
        return new MapSqlParameterSource().addValue("viewer", viewer, Types.OTHER);
    }

    private ProfileResponse toProfile(ResultSet rs, int rowNum) throws SQLException {
        return new ProfileResponse(
                rs.getObject("id", UUID.class),
                rs.getString("handle"),
                rs.getString("display_name"),
                rs.getString("bio"),
                mediaUrls.url(rs.getString("avatar_key")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("post_count"),
                rs.getLong("following_count"),
                rs.getLong("follower_count"),
                rs.getBoolean("following"),
                rs.getBoolean("followed_by"),
                rs.getBoolean("blocking"),
                rs.getBoolean("blocked_by"));
    }

    private UserSummaryResponse toSummary(ResultSet rs, int rowNum) throws SQLException {
        return new UserSummaryResponse(
                rs.getObject("id", UUID.class),
                rs.getString("handle"),
                rs.getString("display_name"),
                rs.getString("bio"),
                mediaUrls.url(rs.getString("avatar_key")),
                rs.getBoolean("following"),
                rs.getBoolean("followed_by"));
    }

    /**
     * フォロー・解除の対象のユーザー。
     *
     * @param blocked 閲覧者とブロック関係（どちらからでも）にあるか
     */
    public record Target(UUID id, boolean blocked) {
    }

    /**
     * フォロー一覧の種類。
     */
    public enum FollowList {
        /** そのユーザーがフォローしている人 */
        FOLLOWING,
        /** そのユーザーをフォローしている人 */
        FOLLOWERS
    }
}
