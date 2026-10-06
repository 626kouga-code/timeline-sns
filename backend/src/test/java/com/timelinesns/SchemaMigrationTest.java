package com.timelinesns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.timelinesns.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway の初期マイグレーション（V1__init.sql）の制約が、データモデルどおりに効いていることを確認する。
 * 各テストはトランザクション内で実行し、終了時にロールバックする。
 * PostgreSQL ではエラー後のトランザクションが使えなくなるため、制約違反の確認は 1 テストにつき 1 つにしている。
 */
@IntegrationTest
@Transactional
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // 他の結合テストが残したデータと衝突しないよう、空の状態から始める（テストのトランザクション内なので最後に戻る）
    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
    }

    @Test
    void createsAllTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
                "users", "auth_providers", "refresh_tokens", "email_verifications", "password_resets",
                "posts", "post_images", "comments", "likes", "follows", "blocks", "notifications", "reports");
    }

    @Test
    void generatesUuidV7ByDefault() {
        UUID id = insertUser("alice@example.com", "alice");

        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        insertUser("Alice@Example.com", "alice");

        assertThatThrownBy(() -> insertUser("alice@example.com", "alice2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void handleIsUniqueIgnoringCase() {
        insertUser("alice@example.com", "Alice");

        assertThatThrownBy(() -> insertUser("alice2@example.com", "alice"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidHandle() {
        assertThatThrownBy(() -> insertUser("alice@example.com", "al"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownRole() {
        UUID id = insertUser("alice@example.com", "alice");

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role = 'ROOT' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cannotFollowSelf() {
        UUID id = insertUser("alice@example.com", "alice");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", id, id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsAtMostFourImagesPerPost() {
        UUID userId = insertUser("alice@example.com", "alice");
        UUID postId = insertPost(userId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO post_images (post_id, original_key, thumbnail_key, width, height, sort_order)
                VALUES (?, 'o', 't', 100, 100, 4)
                """, postId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserCascadesToPostsAndLikesButKeepsCommentsOnOthersPosts() {
        UUID alice = insertUser("alice@example.com", "alice");
        UUID bob = insertUser("bob@example.com", "bob");
        UUID alicePost = insertPost(alice);
        UUID bobPost = insertPost(bob);
        jdbc.update("INSERT INTO likes (user_id, post_id) VALUES (?, ?)", alice, bobPost);
        UUID comment = jdbc.queryForObject(
                "INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, 'hi') RETURNING id",
                UUID.class, bobPost, alice);

        jdbc.update("DELETE FROM users WHERE id = ?", alice);

        assertThat(count("SELECT count(*) FROM posts WHERE id = ?", alicePost)).isZero();
        assertThat(count("SELECT count(*) FROM likes WHERE user_id = ?", alice)).isZero();
        // コメントは残り、投稿者だけが NULL になる（F-06 の「削除されたユーザー」表示用）
        assertThat(count("SELECT count(*) FROM comments WHERE id = ? AND user_id IS NULL", comment)).isEqualTo(1);
    }

    private UUID insertUser(String email, String handle) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, handle, display_name) VALUES (?, ?, ?) RETURNING id",
                UUID.class, email, handle, handle);
    }

    private UUID insertPost(UUID userId) {
        return jdbc.queryForObject(
                "INSERT INTO posts (user_id, body) VALUES (?, 'hello') RETURNING id", UUID.class, userId);
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }
}
