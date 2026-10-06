package com.timelinesns.timeline;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * カーソル方式の一覧の 1 ページ。
 *
 * @param nextCursor 次のページを取るときに {@code cursor} に渡す値。最後のページなら null
 */
public record PageResponse<T>(List<T> items, @Nullable UUID nextCursor) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    public PageResponse {
        items = List.copyOf(items);
    }

    /**
     * 要求された件数を 1〜{@link #MAX_LIMIT} に収める。
     */
    public static int clampLimit(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    /**
     * {@code size + 1} 件を要求して得た {@code rows} から 1 ページを作る。1 件多く取れたら次のページがある。
     *
     * @param cursorOf 次のページの cursor にする値（ページの最後の要素の ID）
     */
    public static <T> PageResponse<T> of(List<T> rows, int size, Function<T, UUID> cursorOf) {
        if (rows.size() <= size) {
            return new PageResponse<>(rows, null);
        }
        List<T> items = rows.subList(0, size);
        return new PageResponse<>(items, cursorOf.apply(items.getLast()));
    }
}
