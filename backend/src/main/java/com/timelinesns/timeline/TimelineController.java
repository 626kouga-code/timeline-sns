package com.timelinesns.timeline;

import com.timelinesns.common.Viewer;
import com.timelinesns.post.PostQuery;
import com.timelinesns.post.PostQuery.Timeline;
import com.timelinesns.post.PostResponse;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ホームタイムライン（F-20）・全体タイムライン（F-21）と、その新着件数（F-22）。
 * 一覧はカーソル方式（{@code ?cursor=<前のページの最後の id>&limit=20}）。
 * 全体タイムラインはゲストも見られる（SecurityConfig で許可している）。
 */
@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final PostQuery query;

    public TimelineController(PostQuery query) {
        this.query = query;
    }

    @GetMapping("/global")
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> global(@AuthenticationPrincipal @Nullable Jwt jwt,
            @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return page(Timeline.GLOBAL, Viewer.idOrNull(jwt), cursor, limit);
    }

    @GetMapping("/home")
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> home(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Nullable UUID cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return page(Timeline.HOME, Viewer.id(jwt), cursor, limit);
    }

    @GetMapping("/global/new-count")
    @Transactional(readOnly = true)
    public NewCountResponse globalNewCount(@AuthenticationPrincipal @Nullable Jwt jwt, @RequestParam UUID since) {
        return new NewCountResponse(query.countNewer(Timeline.GLOBAL, Viewer.idOrNull(jwt), since));
    }

    @GetMapping("/home/new-count")
    @Transactional(readOnly = true)
    public NewCountResponse homeNewCount(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID since) {
        return new NewCountResponse(query.countNewer(Timeline.HOME, Viewer.id(jwt), since));
    }

    private PageResponse<PostResponse> page(Timeline timeline, @Nullable UUID viewer, @Nullable UUID cursor,
            int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        // 1 件多く取り、取れたら次のページがある
        List<PostResponse> rows = query.timeline(timeline, viewer, cursor, size + 1);
        if (rows.size() <= size) {
            return new PageResponse<>(rows, null);
        }
        List<PostResponse> items = rows.subList(0, size);
        return new PageResponse<>(items, items.getLast().id());
    }
}
