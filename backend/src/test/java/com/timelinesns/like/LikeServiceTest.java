package com.timelinesns.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelinesns.common.ApiException;
import com.timelinesns.notification.NotificationService;
import com.timelinesns.post.PostQuery;
import com.timelinesns.post.PostResponse;
import com.timelinesns.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * いいね・取り消しの判断（LikeService）。DB はモックにし、どの条件で書き込み・通知するかを確かめる。
 * 実際の書き込みと集計は LikeApiTest（結合テスト）で確かめている。
 */
class LikeServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID POST = UUID.randomUUID();

    private NamedParameterJdbcTemplate jdbc;
    private PostQuery posts;
    private UserRepository users;
    private NotificationService notifications;
    private LikeService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        posts = mock(PostQuery.class);
        users = mock(UserRepository.class);
        notifications = mock(NotificationService.class);
        service = new LikeService(jdbc, posts, users, notifications);
        when(users.existsById(ME)).thenReturn(true);
        PostResponse visible = new PostResponse(POST, "本文", List.of(), Instant.now(),
                new PostResponse.Author(UUID.randomUUID(), "hanako", "花子", null), 0, 0, false);
        when(posts.findVisible(POST, ME)).thenReturn(Optional.of(visible));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<LikeResponse>>any())).thenReturn(new LikeResponse(true, 1));
    }

    private void insertReturns(int rows) {
        when(jdbc.update(ArgumentMatchers.contains("INSERT INTO likes"), any(SqlParameterSource.class)))
                .thenReturn(rows);
    }

    private static void assertApiError(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void likeNotifiesAuthorWhenNewlyLiked() {
        insertReturns(1);

        assertThat(service.like(POST, ME)).isEqualTo(new LikeResponse(true, 1));
        verify(notifications).liked(POST, ME);
    }

    @Test
    void likeTwiceDoesNotNotifyAgain() {
        insertReturns(0);

        service.like(POST, ME);

        verify(notifications, never()).liked(any(), any());
    }

    @Test
    void unlikeDeletesLikeAndItsNotification() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<LikeResponse>>any())).thenReturn(new LikeResponse(false, 0));

        assertThat(service.unlike(POST, ME)).isEqualTo(new LikeResponse(false, 0));
        verify(jdbc).update(ArgumentMatchers.contains("DELETE FROM likes"), any(SqlParameterSource.class));
        verify(notifications).unliked(POST, ME);
    }

    @Test
    void cannotReactToInvisiblePost() {
        // 存在しない・凍結ユーザーの・ブロック関係にある投稿は findVisible が空
        UUID hidden = UUID.randomUUID();

        assertApiError(() -> service.like(hidden, ME), HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
        assertApiError(() -> service.unlike(hidden, ME), HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void deletedViewerIsUnauthorized() {
        UUID deleted = UUID.randomUUID();

        assertApiError(() -> service.like(POST, deleted), HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND");
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }
}
