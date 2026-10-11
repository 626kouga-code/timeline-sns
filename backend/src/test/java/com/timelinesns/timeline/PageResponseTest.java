package com.timelinesns.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * カーソル方式の 1 ページの組み立て（PageResponse）。
 */
class PageResponseTest {

    private static final List<UUID> IDS = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @ParameterizedTest
    @CsvSource({"-5, 1", "0, 1", "1, 1", "20, 20", "50, 50", "51, 50", "1000, 50"})
    void clampLimitKeepsWithinRange(int requested, int expected) {
        assertThat(PageResponse.clampLimit(requested)).isEqualTo(expected);
    }

    @Test
    void lastPageHasNoNextCursor() {
        PageResponse<UUID> page = PageResponse.of(IDS.subList(0, 2), 2, Function.identity());

        assertThat(page.items()).containsExactlyElementsOf(IDS.subList(0, 2));
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void extraRowMeansNextPageStartsAfterLastItem() {
        PageResponse<UUID> page = PageResponse.of(IDS, 2, Function.identity());

        assertThat(page.items()).containsExactly(IDS.get(0), IDS.get(1));
        assertThat(page.nextCursor()).isEqualTo(IDS.get(1));
    }

    @Test
    void emptyPage() {
        PageResponse<UUID> page = PageResponse.of(List.of(), 20, Function.identity());

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void itemsAreCopiedAndUnmodifiable() {
        List<UUID> rows = new ArrayList<>(IDS);
        PageResponse<UUID> page = PageResponse.of(rows, 5, Function.identity());

        rows.clear();

        assertThat(page.items()).hasSize(3);
        assertThatThrownBy(() -> page.items().add(UUID.randomUUID())).isInstanceOf(UnsupportedOperationException.class);
    }
}
