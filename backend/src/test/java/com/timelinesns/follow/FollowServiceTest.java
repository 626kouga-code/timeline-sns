package com.timelinesns.follow;

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
import com.timelinesns.user.UserQuery;
import com.timelinesns.user.UserRepository;
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
 * フォロー・解除の判断（FollowService）。DB はモックにし、どの条件で書き込み・通知するかを確かめる。
 * 実際の書き込みと集計は FollowApiTest（結合テスト）で確かめている。
 */
class FollowServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private NamedParameterJdbcTemplate jdbc;
    private UserQuery userQuery;
    private UserRepository users;
    private NotificationService notifications;
    private FollowService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        userQuery = mock(UserQuery.class);
        users = mock(UserRepository.class);
        notifications = mock(NotificationService.class);
        service = new FollowService(jdbc, userQuery, users, notifications);
        when(users.existsById(ME)).thenReturn(true);
        when(userQuery.findTarget("hanako", ME)).thenReturn(Optional.of(new UserQuery.Target(TARGET, false)));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FollowResponse>>any())).thenReturn(new FollowResponse(true, 1));
    }

    private void insertReturns(int rows) {
        when(jdbc.update(ArgumentMatchers.contains("INSERT INTO follows"), any(SqlParameterSource.class)))
                .thenReturn(rows);
    }

    private static void assertApiError(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void followNotifiesTargetWhenNewlyFollowed() {
        insertReturns(1);

        FollowResponse response = service.follow("hanako", ME);

        assertThat(response).isEqualTo(new FollowResponse(true, 1));
        verify(notifications).followed(TARGET, ME);
    }

    @Test
    void followTwiceDoesNotNotifyAgain() {
        insertReturns(0);

        service.follow("hanako", ME);

        verify(notifications, never()).followed(any(), any());
    }

    @Test
    void cannotFollowSelf() {
        when(userQuery.findTarget("me", ME)).thenReturn(Optional.of(new UserQuery.Target(ME, false)));

        assertApiError(() -> service.follow("me", ME), HttpStatus.BAD_REQUEST, "CANNOT_FOLLOW_SELF");
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void cannotFollowWhenBlocked() {
        when(userQuery.findTarget("hanako", ME)).thenReturn(Optional.of(new UserQuery.Target(TARGET, true)));

        assertApiError(() -> service.follow("hanako", ME), HttpStatus.FORBIDDEN, "BLOCKED");
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void unknownTargetIsNotFound() {
        assertApiError(() -> service.follow("nobody", ME), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        assertApiError(() -> service.unfollow("nobody", ME), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void deletedViewerIsUnauthorized() {
        UUID deleted = UUID.randomUUID();

        assertApiError(() -> service.follow("hanako", deleted), HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND");
        assertApiError(() -> service.unfollow("hanako", deleted), HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND");
    }

    @Test
    void unfollowDeletesFollowAndItsNotification() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FollowResponse>>any())).thenReturn(new FollowResponse(false, 0));

        FollowResponse response = service.unfollow("hanako", ME);

        assertThat(response).isEqualTo(new FollowResponse(false, 0));
        verify(jdbc).update(ArgumentMatchers.contains("DELETE FROM follows"), any(SqlParameterSource.class));
        verify(notifications).unfollowed(TARGET, ME);
    }
}
