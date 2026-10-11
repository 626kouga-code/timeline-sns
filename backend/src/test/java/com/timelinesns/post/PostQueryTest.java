package com.timelinesns.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.timelinesns.post.PostQuery.Timeline;
import com.timelinesns.support.DataAccessTest;
import com.timelinesns.support.Fixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 投稿詳細・タイムライン・新着件数の SQL（PostQuery）。DB に入れたデータを、実際の SQL で読み出して確かめる。
 */
@DataAccessTest
class PostQueryTest {

    @Autowired
    private PostQuery query;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixtures fixtures;
    private UUID taro;
    private UUID hanako;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(jdbc);
        taro = fixtures.user("taro", "太郎");
        hanako = fixtures.user("hanako", "花子");
    }

    private static List<String> bodies(List<PostResponse> posts) {
        return posts.stream().map(PostResponse::body).toList();
    }

    @Nested
    class FindVisible {

        @Test
        void returnsPostWithAuthorAndCounts() {
            UUID postId = fixtures.post(taro, "こんにちは");
            fixtures.avatar(taro, "avatars/taro.jpg");
            fixtures.like(taro, postId);
            fixtures.like(hanako, postId);
            fixtures.comment(hanako, postId, false);
            // 論理削除したコメントは数えない
            fixtures.comment(hanako, postId, true);

            PostResponse post = query.findVisible(postId, null).orElseThrow();

            assertThat(post.id()).isEqualTo(postId);
            assertThat(post.body()).isEqualTo("こんにちは");
            assertThat(post.author()).isEqualTo(
                    new PostResponse.Author(taro, "taro", "太郎", "/api/media/avatars/taro.jpg"));
            assertThat(post.likeCount()).isEqualTo(2);
            assertThat(post.commentCount()).isEqualTo(1);
            assertThat(post.images()).isEmpty();
        }

        @Test
        void returnsWhetherViewerLiked() {
            UUID postId = fixtures.post(taro, "こんにちは");
            fixtures.like(hanako, postId);

            assertThat(query.findVisible(postId, hanako).orElseThrow().liked()).isTrue();
            assertThat(query.findVisible(postId, taro).orElseThrow().liked()).isFalse();
            assertThat(query.findVisible(postId, null).orElseThrow().liked()).isFalse();
        }

        @Test
        void returnsImagesInSortOrderWithUrls() {
            UUID postId = fixtures.post(taro, "");
            fixtures.image(postId, 1, "posts/2.jpg", "posts/2-t.jpg", 800, 600);
            fixtures.image(postId, 0, "posts/1.gif", "posts/1-t.jpg", 2048, 1536);

            assertThat(query.findVisible(postId, null).orElseThrow().images()).containsExactly(
                    new PostResponse.Image("/api/media/posts/1.gif", "/api/media/posts/1-t.jpg", 2048, 1536),
                    new PostResponse.Image("/api/media/posts/2.jpg", "/api/media/posts/2-t.jpg", 800, 600));
        }

        @Test
        void hidesPostsOfSuspendedAuthor() {
            UUID postId = fixtures.post(taro, "こんにちは");
            fixtures.suspend(taro);

            assertThat(query.findVisible(postId, null)).isEmpty();
        }

        @Test
        void hidesPostsWhenBlockedInEitherDirection() {
            UUID postId = fixtures.post(taro, "こんにちは");
            fixtures.block(taro, hanako);

            assertThat(query.findVisible(postId, hanako)).isEmpty();
            assertThat(query.findVisible(postId, null)).isPresent();

            UUID hanakoPost = fixtures.post(hanako, "やあ");
            assertThat(query.findVisible(hanakoPost, taro)).isEmpty();
        }

        @Test
        void returnsEmptyForUnknownPost() {
            assertThat(query.findVisible(UUID.randomUUID(), null)).isEmpty();
        }
    }

    @Nested
    class Timelines {

        @Test
        void globalListsAllPostsNewestFirst() {
            fixtures.post(taro, "1");
            fixtures.post(hanako, "2");
            fixtures.post(taro, "3");

            assertThat(bodies(query.timeline(Timeline.GLOBAL, null, null, 10))).containsExactly("3", "2", "1");
        }

        @Test
        void homeListsOwnAndFollowedPostsOnly() {
            UUID jiro = fixtures.user("jiro");
            fixtures.follow(taro, hanako);
            fixtures.post(taro, "自分");
            fixtures.post(hanako, "フォロー中");
            fixtures.post(jiro, "フォローしていない");

            assertThat(bodies(query.timeline(Timeline.HOME, taro, null, 10))).containsExactly("フォロー中", "自分");
        }

        @Test
        void excludesSuspendedAndBlockedAuthors() {
            UUID jiro = fixtures.user("jiro");
            UUID saburo = fixtures.user("saburo");
            fixtures.post(jiro, "凍結");
            fixtures.post(saburo, "ブロック");
            fixtures.post(hanako, "見える");
            fixtures.suspend(jiro);
            fixtures.block(saburo, taro);

            assertThat(bodies(query.timeline(Timeline.GLOBAL, taro, null, 10))).containsExactly("見える");
        }

        @Test
        void pagesWithCursor() {
            fixtures.post(taro, "1");
            fixtures.post(taro, "2");
            fixtures.post(taro, "3");

            List<PostResponse> first = query.timeline(Timeline.GLOBAL, null, null, 2);
            List<PostResponse> second = query.timeline(Timeline.GLOBAL, null, first.getLast().id(), 2);

            assertThat(bodies(first)).containsExactly("3", "2");
            assertThat(bodies(second)).containsExactly("1");
            assertThat(query.timeline(Timeline.GLOBAL, null, second.getLast().id(), 2)).isEmpty();
        }

        @Test
        void byAuthorListsOnlyThatAuthor() {
            fixtures.post(taro, "太郎 1");
            fixtures.post(hanako, "花子");
            fixtures.post(taro, "太郎 2");

            assertThat(bodies(query.byAuthor(taro, null, null, 10))).containsExactly("太郎 2", "太郎 1");
        }

        @Test
        void byAuthorIsEmptyWhenBlocked() {
            fixtures.post(taro, "太郎");
            fixtures.block(hanako, taro);

            assertThat(query.byAuthor(taro, hanako, null, 10)).isEmpty();
        }
    }

    @Nested
    class CountNewer {

        @Test
        void countsPostsNewerThanSinceExceptViewersOwn() {
            UUID since = fixtures.post(hanako, "表示中の先頭");
            fixtures.post(hanako, "新しい 1");
            fixtures.post(taro, "自分の投稿");
            fixtures.post(hanako, "新しい 2");

            assertThat(query.countNewer(Timeline.GLOBAL, taro, since)).isEqualTo(2);
            // ゲストは全員の投稿を数える
            assertThat(query.countNewer(Timeline.GLOBAL, null, since)).isEqualTo(3);
        }

        @Test
        void homeCountsOnlyFollowedUsers() {
            UUID jiro = fixtures.user("jiro");
            fixtures.follow(taro, hanako);
            UUID since = fixtures.post(taro, "表示中の先頭");
            fixtures.post(hanako, "フォロー中");
            fixtures.post(jiro, "フォローしていない");

            assertThat(query.countNewer(Timeline.HOME, taro, since)).isEqualTo(1);
        }

        @Test
        void stopsCountingAtMax() {
            UUID since = fixtures.post(hanako, "表示中の先頭");
            for (int i = 0; i < PostQuery.MAX_NEW_COUNT + 1; i++) {
                fixtures.post(hanako, "新しい " + i);
            }

            assertThat(query.countNewer(Timeline.GLOBAL, null, since)).isEqualTo(PostQuery.MAX_NEW_COUNT);
        }
    }
}
