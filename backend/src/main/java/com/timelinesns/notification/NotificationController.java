package com.timelinesns.notification;

import com.timelinesns.common.Viewer;
import com.timelinesns.timeline.PageResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知一覧（F-50）・未読数と既読化（F-51）。どれもログインが必要。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQuery notifications;

    public NotificationController(NotificationQuery notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_LIMIT) int limit) {
        int size = PageResponse.clampLimit(limit);
        return PageResponse.of(notifications.list(Viewer.id(jwt), cursor, size + 1), size, NotificationResponse::id);
    }

    /**
     * 未読の数（100 件で打ち切る）。
     */
    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(notifications.unreadCount(Viewer.id(jwt)));
    }

    /**
     * 既読にする。{@code until}（画面に表示した一番新しい通知）を渡すと、それ以前だけを既読にする。
     */
    @PostMapping("/read")
    @Transactional
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) @Nullable MarkReadRequest request) {
        notifications.markRead(Viewer.id(jwt), request == null ? null : request.until());
        return ResponseEntity.noContent().build();
    }

    /**
     * 既読化の入力。
     *
     * @param until この通知以前を既読にする。null ならすべて
     */
    public record MarkReadRequest(@Nullable UUID until) {
    }
}
