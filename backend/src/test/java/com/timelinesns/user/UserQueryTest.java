package com.timelinesns.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.timelinesns.support.DataAccessTest;
import com.timelinesns.support.Fixtures;
import com.timelinesns.user.UserQuery.FollowList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * プロフィール・フォロー一覧・ユーザー検索の SQL（UserQuery）。DB に入れたデータを、実際の SQL で読み出して確かめる。
 */
@DataAccessTest
class UserQueryTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private UserQuery query;

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

    private static List<String> handles(List<UserSummaryResponse> users) {
        return users.stream().map(UserSummaryResponse::handle).toList();
    }

    @Nested
    class FindProfile {

        @Test
        void returnsProfileWithCountsAndAvatarUrl() {
            UUID jiro = fixtures.user("jiro");
            fixtures.post(taro, "1");
            fixtures.post(taro, "2");
            fixtures.follow(taro, hanako);
            fixtures.follow(hanako, taro);
            fixtures.follow(jiro, taro);
            fixtures.avatar(taro, "avatars/taro.jpg");

            ProfileResponse profile = query.findProfile("taro", null).orElseThrow();

            assertThat(profile.id()).isEqualTo(taro);
            assertThat(profile.handle()).isEqualTo("taro");
            assertThat(profile.displayName()).isEqualTo("太郎");
            assertThat(profile.avatarUrl()).isEqualTo("/api/media/avatars/taro.jpg");
            assertThat(profile.postCount()).isEqualTo(2);
            assertThat(profile.followingCount()).isEqualTo(1);
            assertThat(profile.followerCount()).isEqualTo(2);
        }

        @Test
        void findsHandleIgnoringCase() {
            assertThat(query.findProfile("TARO", null)).map(ProfileResponse::id).contains(taro);
        }

        @Test
        void guestHasNoRelations() {
            fixtures.follow(hanako, taro);

            ProfileResponse profile = query.findProfile("taro", null).orElseThrow();

            assertThat(profile.following()).isFalse();
            assertThat(profile.followedBy()).isFalse();
            assertThat(profile.blocking()).isFalse();
            assertThat(profile.blockedBy()).isFalse();
        }

        @Test
        void returnsRelationsWithViewer() {
            fixtures.follow(hanako, taro);
            fixtures.follow(taro, hanako);

            ProfileResponse profile = query.findProfile("taro", hanako).orElseThrow();

            assertThat(profile.following()).isTrue();
            assertThat(profile.followedBy()).isTrue();
            assertThat(profile.blocking()).isFalse();
            assertThat(profile.blockedBy()).isFalse();
        }

        @Test
        void returnsBlockRelationsInBothDirections() {
            fixtures.block(hanako, taro);

            assertThat(query.findProfile("taro", hanako).orElseThrow().blocking()).isTrue();
            assertThat(query.findProfile("hanako", taro).orElseThrow().blockedBy()).isTrue();
        }

        @Test
        void doesNotCountSuspendedFollowers() {
            UUID suspended = fixtures.user("suspended");
            fixtures.follow(suspended, taro);
            fixtures.follow(taro, suspended);
            fixtures.suspend(suspended);

            ProfileResponse profile = query.findProfile("taro", null).orElseThrow();

            assertThat(profile.followerCount()).isZero();
            assertThat(profile.followingCount()).isZero();
        }

        @Test
        void treatsSuspendedOrUnknownUserAsMissing() {
            fixtures.suspend(taro);

            assertThat(query.findProfile("taro", null)).isEmpty();
            assertThat(query.findProfile("nobody", null)).isEmpty();
        }
    }

    @Nested
    class FindTarget {

        @Test
        void returnsIdAndWhetherBlockedInEitherDirection() {
            assertThat(query.findTarget("taro", hanako)).contains(new UserQuery.Target(taro, false));

            fixtures.block(taro, hanako);

            assertThat(query.findTarget("taro", hanako)).contains(new UserQuery.Target(taro, true));
            assertThat(query.findTarget("hanako", taro)).contains(new UserQuery.Target(hanako, true));
        }

        @Test
        void excludesSuspendedUser() {
            fixtures.suspend(taro);

            assertThat(query.findTarget("taro", hanako)).isEmpty();
        }
    }

    @Nested
    class FollowLists {

        private UUID jiro;
        private UUID saburo;

        @BeforeEach
        void follows() {
            jiro = fixtures.user("jiro");
            saburo = fixtures.user("saburo");
            // taro は hanako → jiro → saburo の順にフォローした
            fixtures.follow(taro, hanako, T0);
            fixtures.follow(taro, jiro, T0.plusSeconds(1));
            fixtures.follow(taro, saburo, T0.plusSeconds(2));
        }

        @Test
        void listsFollowingNewestFirst() {
            assertThat(handles(query.followList(FollowList.FOLLOWING, taro, null, null, 10)))
                    .containsExactly("saburo", "jiro", "hanako");
        }

        @Test
        void listsFollowers() {
            fixtures.follow(hanako, jiro, T0);

            assertThat(handles(query.followList(FollowList.FOLLOWERS, jiro, null, null, 10)))
                    .containsExactly("taro", "hanako");
        }

        @Test
        void pagesWithCursor() {
            List<UserSummaryResponse> first = query.followList(FollowList.FOLLOWING, taro, null, null, 2);
            List<UserSummaryResponse> second =
                    query.followList(FollowList.FOLLOWING, taro, null, first.getLast().id(), 2);

            assertThat(handles(first)).containsExactly("saburo", "jiro");
            assertThat(handles(second)).containsExactly("hanako");
            assertThat(query.followList(FollowList.FOLLOWING, taro, null, hanako, 2)).isEmpty();
        }

        @Test
        void excludesSuspendedAndBlockedUsers() {
            UUID viewer = fixtures.user("viewer");
            fixtures.suspend(jiro);
            fixtures.block(saburo, viewer);

            assertThat(handles(query.followList(FollowList.FOLLOWING, taro, viewer, null, 10)))
                    .containsExactly("hanako");
            // ブロック関係にない閲覧者には saburo も見える
            assertThat(handles(query.followList(FollowList.FOLLOWING, taro, null, null, 10)))
                    .containsExactly("saburo", "hanako");
        }

        @Test
        void returnsRelationsWithViewer() {
            fixtures.follow(hanako, jiro, T0);
            fixtures.follow(jiro, hanako, T0);

            UserSummaryResponse jiroRow = query.followList(FollowList.FOLLOWING, taro, hanako, null, 10).stream()
                    .filter(u -> u.id().equals(jiro)).findFirst().orElseThrow();

            assertThat(jiroRow.following()).isTrue();
            assertThat(jiroRow.followedBy()).isTrue();
        }
    }

    @Nested
    class Search {

        @Test
        void ranksExactThenPrefixThenOtherMatches() {
            fixtures.user("taro_2", "二郎");
            fixtures.user("mitaro", "みたろう");
            fixtures.user("zzz", "taro ファン");

            assertThat(handles(query.search("taro", null, null, 10)))
                    .containsExactly("taro", "taro_2", "mitaro", "zzz");
        }

        @Test
        void matchesHandleAndDisplayNameIgnoringCase() {
            assertThat(handles(query.search("TARO", null, null, 10))).containsExactly("taro");
            assertThat(handles(query.search("花", null, null, 10))).containsExactly("hanako");
        }

        @Test
        void treatsLikeWildcardsAsPlainCharacters() {
            fixtures.user("a_b");
            fixtures.user("axb");

            assertThat(handles(query.search("a_b", null, null, 10))).containsExactly("a_b");
            assertThat(query.search("%", null, null, 10)).isEmpty();
        }

        @Test
        void excludesSuspendedAndBlockedUsers() {
            UUID taro2 = fixtures.user("taro_2");
            UUID taro3 = fixtures.user("taro_3");
            fixtures.suspend(taro2);
            fixtures.block(taro3, hanako);

            assertThat(handles(query.search("taro", hanako, null, 10))).containsExactly("taro");
            assertThat(handles(query.search("taro", null, null, 10))).containsExactly("taro", "taro_3");
        }

        @Test
        void pagesWithCursorInRankOrder() {
            fixtures.user("taro_a");
            fixtures.user("taro_b");
            fixtures.user("mitaro");

            List<UserSummaryResponse> first = query.search("taro", null, null, 2);
            List<UserSummaryResponse> second = query.search("taro", null, first.getLast().id(), 2);
            List<UserSummaryResponse> third = query.search("taro", null, second.getLast().id(), 2);

            assertThat(handles(first)).containsExactly("taro", "taro_a");
            assertThat(handles(second)).containsExactly("taro_b", "mitaro");
            assertThat(third).isEmpty();
        }

        @Test
        void returnsRelationsWithViewer() {
            fixtures.follow(hanako, taro);

            UserSummaryResponse found = query.search("taro", hanako, null, 10).getFirst();

            assertThat(found.following()).isTrue();
            assertThat(found.followedBy()).isFalse();
        }
    }
}
