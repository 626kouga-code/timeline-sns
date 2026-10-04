package com.timelinesns.support;

import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 結合テスト用のデータを DB に直接入れる。まだ API が無い関係（フォロー・ブロック・凍結）もここで作る。
 */
public class Fixtures {

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AccessTokenService accessTokens;

    public Fixtures(JdbcTemplate jdbc, UserRepository users, AccessTokenService accessTokens) {
        this.jdbc = jdbc;
        this.users = users;
        this.accessTokens = accessTokens;
    }

    public UUID user(String handle) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, handle, display_name) VALUES (?, ?, ?, ?)",
                id, handle + "@example.com", handle, handle.toUpperCase());
        return id;
    }

    public String token(UUID userId) {
        return accessTokens.issue(users.findById(userId).orElseThrow());
    }

    public String bearer(UUID userId) {
        return "Bearer " + token(userId);
    }

    /**
     * 投稿を入れる。id は DB の uuidv7() で採番されるので、入れた順に新しくなる。
     */
    public UUID post(UUID userId, String body) {
        return jdbc.queryForObject("INSERT INTO posts (user_id, body, created_at) VALUES (?, ?, ?) RETURNING id",
                UUID.class, userId, body, Timestamp.from(Instant.now()));
    }

    public void follow(UUID follower, UUID followee) {
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", follower, followee);
    }

    public void block(UUID blocker, UUID blocked) {
        jdbc.update("INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)", blocker, blocked);
    }

    public void suspend(UUID userId) {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", userId);
    }

    public void like(UUID userId, UUID postId) {
        jdbc.update("INSERT INTO likes (user_id, post_id) VALUES (?, ?)", userId, postId);
    }

    public void comment(UUID userId, UUID postId, boolean deleted) {
        jdbc.update("INSERT INTO comments (post_id, user_id, body, deleted_at) VALUES (?, ?, 'c', ?)",
                postId, userId, deleted ? Timestamp.from(Instant.now()) : null);
    }

    /**
     * コメント（parentId を渡すと返信）を入れて ID を返す。入れた順に新しくなる。
     */
    public UUID comment(UUID userId, UUID postId, @Nullable UUID parentId, String body) {
        return jdbc.queryForObject(
                "INSERT INTO comments (post_id, user_id, parent_id, body) VALUES (?, ?, ?, ?) RETURNING id",
                UUID.class, postId, userId, parentId, body);
    }

    /**
     * 返信 {@code depth} 段の系統（コメント → 返信 → 返信への返信 …）を入れ、上（ルート）から順に ID を返す。
     */
    public List<UUID> chain(UUID userId, UUID postId, int depth) {
        List<UUID> ids = new ArrayList<>();
        @Nullable UUID parent = null;
        for (int i = 0; i < depth; i++) {
            parent = comment(userId, postId, parent, "depth " + i);
            ids.add(parent);
        }
        return ids;
    }

    public void markCommentDeleted(UUID commentId) {
        jdbc.update("UPDATE comments SET deleted_at = now(), body = '' WHERE id = ?", commentId);
    }
}
