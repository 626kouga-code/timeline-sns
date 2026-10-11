package com.timelinesns.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.timelinesns.post.PostResponse;
import com.timelinesns.support.DataAccessTest;
import com.timelinesns.support.Fixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * コメントの読み書きの SQL（CommentQuery）。書き込んだコメントを、実際の SQL で読み戻して確かめる。
 */
@DataAccessTest
class CommentQueryTest {

    @Autowired
    private CommentQuery query;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixtures fixtures;
    private UUID taro;
    private UUID hanako;
    private UUID postId;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(jdbc);
        taro = fixtures.user("taro", "太郎");
        hanako = fixtures.user("hanako", "花子");
        postId = fixtures.post(taro, "投稿");
    }

    @Test
    void insertsAndReadsBackComment() {
        fixtures.avatar(hanako, "avatars/hanako.jpg");

        UUID id = query.insert(postId, hanako, null, "いいですね");
        CommentQuery.Row row = query.findById(id, null).orElseThrow();

        assertThat(row.id()).isEqualTo(id);
        assertThat(row.postId()).isEqualTo(postId);
        assertThat(row.parentId()).isNull();
        assertThat(row.body()).isEqualTo("いいですね");
        assertThat(row.createdAt()).isNotNull();
        assertThat(row.author()).isEqualTo(
                new PostResponse.Author(hanako, "hanako", "花子", "/api/media/avatars/hanako.jpg"));
        assertThat(row.removed()).isFalse();
        assertThat(row.hidden()).isFalse();
    }

    @Test
    void listsCommentsOfPostOldestFirstWithReplies() {
        UUID first = query.insert(postId, hanako, null, "1");
        UUID reply = query.insert(postId, taro, first, "1 への返信");
        UUID second = query.insert(postId, hanako, null, "2");
        query.insert(fixtures.post(taro, "別の投稿"), hanako, null, "別の投稿へのコメント");

        List<CommentQuery.Row> rows = query.findByPost(postId, null);

        assertThat(rows).extracting(CommentQuery.Row::id).containsExactly(first, reply, second);
        assertThat(rows.get(1).parentId()).isEqualTo(first);
    }

    @Test
    void marksCommentsOfSuspendedOrBlockedAuthorsAsHidden() {
        UUID jiro = fixtures.user("jiro");
        UUID byHanako = query.insert(postId, hanako, null, "花子");
        UUID byJiro = query.insert(postId, jiro, null, "次郎");
        fixtures.suspend(jiro);
        fixtures.block(hanako, taro);

        assertThat(query.findById(byHanako, taro).orElseThrow().hidden()).isTrue();
        assertThat(query.findById(byHanako, null).orElseThrow().hidden()).isFalse();
        assertThat(query.findById(byJiro, null).orElseThrow().hidden()).isTrue();
    }

    @Test
    void treatsCommentOfDeletedUserAsRemoved() {
        UUID jiro = fixtures.user("jiro");
        UUID id = query.insert(postId, jiro, null, "次郎");

        jdbc.update("DELETE FROM users WHERE id = ?", jiro);
        CommentQuery.Row row = query.findById(id, null).orElseThrow();

        assertThat(row.author()).isNull();
        assertThat(row.removed()).isTrue();
        assertThat(row.hidden()).isFalse();
    }

    @Test
    void markDeletedKeepsRowButClearsBody() {
        UUID id = query.insert(postId, hanako, null, "消す");
        Instant now = Instant.parse("2026-10-01T09:00:00Z");

        query.markDeleted(id, now);
        CommentQuery.Row row = query.findById(id, null).orElseThrow();

        assertThat(row.removed()).isTrue();
        assertThat(row.body()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM comments WHERE id = ?", Instant.class, id))
                .isEqualTo(now);
        // 論理削除したコメントは、もう削除の対象にならない
        assertThat(query.findDeleteTarget(id)).isEmpty();
    }

    @Test
    void deleteRemovesCommentWithReplies() {
        UUID parent = query.insert(postId, hanako, null, "親");
        UUID reply = query.insert(postId, taro, parent, "返信");

        query.delete(parent);

        assertThat(query.findById(parent, null)).isEmpty();
        assertThat(query.findById(reply, null)).isEmpty();
    }

    @Test
    void findDeleteTargetReportsOwnerParentAndReplies() {
        UUID parent = query.insert(postId, hanako, null, "親");
        UUID reply = query.insert(postId, taro, parent, "返信");

        assertThat(query.findDeleteTarget(parent))
                .contains(new CommentQuery.DeleteTarget(hanako, null, true));
        assertThat(query.findDeleteTarget(reply))
                .contains(new CommentQuery.DeleteTarget(taro, parent, false));
        assertThat(query.findDeleteTarget(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAncestorsReturnsChainNearestFirst() {
        List<UUID> chain = fixtures.chain(hanako, postId, 4);
        fixtures.markCommentDeleted(chain.get(1));

        List<CommentQuery.Ancestor> ancestors = query.findAncestors(chain.get(3));

        assertThat(ancestors).containsExactly(
                new CommentQuery.Ancestor(chain.get(3), false, 0),
                new CommentQuery.Ancestor(chain.get(2), false, 1),
                new CommentQuery.Ancestor(chain.get(1), true, 1),
                new CommentQuery.Ancestor(chain.get(0), false, 1));
    }
}
