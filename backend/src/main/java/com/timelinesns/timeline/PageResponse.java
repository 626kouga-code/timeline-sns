package com.timelinesns.timeline;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * カーソル方式の一覧の 1 ページ。
 *
 * @param nextCursor 次のページを取るときに {@code cursor} に渡す値。最後のページなら null
 */
public record PageResponse<T>(List<T> items, @Nullable UUID nextCursor) {

    public PageResponse {
        items = List.copyOf(items);
    }
}
