/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.datagrid.client.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPageState {

    @Test
    void testSetRowDataOnFirstPage() {
        final PageState<String> state = new PageState<>(100);

        final boolean countChanged = state.setRowData(0, Arrays.asList("a", "b", "c"));

        // The count grows to fit the values, as GWT does.
        assertThat(countChanged).isTrue();
        assertThat(state.getRows()).containsExactly("a", "b", "c");
        assertThat(state.getRowCount()).isEqualTo(3);
    }

    @Test
    void testRowCountTrimsRows() {
        final PageState<String> state = new PageState<>(100);
        state.setRowData(0, Arrays.asList("a", "b", "c"));
        state.takeDirtyRows();

        // A refreshed fetch that returns fewer rows: data first, then the count.
        state.setRowData(0, Collections.singletonList("x"));
        assertThat(state.setRowCount(1, true)).isTrue();

        assertThat(state.getRows()).containsExactly("x");
        assertThat(state.takeDirtyRows()).isNull();
    }

    @Test
    void testSetRowCountUnchangedReportsNoChange() {
        final PageState<String> state = new PageState<>(100);
        assertThat(state.setRowCount(10, true)).isTrue();
        assertThat(state.setRowCount(10, true)).isFalse();
        assertThat(state.setRowCount(10, false)).isTrue();
        assertThat(state.isRowCountExact()).isFalse();
    }

    @Test
    void testDataOutsidePageIsIgnored() {
        final PageState<String> state = new PageState<>(10);
        state.setRowCount(100, true);

        state.setRowData(50, Arrays.asList("a", "b"));

        assertThat(state.getRows()).isEmpty();
    }

    @Test
    void testDataIsClippedToPage() {
        final PageState<String> state = new PageState<>(2);
        state.setRowCount(10, true);

        state.setRowData(0, Arrays.asList("a", "b", "c"));

        assertThat(state.getRows()).containsExactly("a", "b");
    }

    @Test
    void testPlaceholdersForGaps() {
        final PageState<String> state = new PageState<>(10);
        state.setRowCount(10, true);

        state.setRowData(2, Collections.singletonList("c"));

        assertThat(state.getRows()).containsExactly(null, null, "c");
        assertThat(state.getRow(0)).isNull();
        assertThat(state.getRow(99)).isNull();
    }

    @Test
    void testDirtyRangeTracksReplacedRows() {
        final PageState<String> state = new PageState<>(10);
        state.setRowData(0, Arrays.asList("a", "b", "c", "d"));
        state.takeDirtyRows();

        assertThat(state.takeDirtyRows()).containsExactly(0, 0);

        state.setRowData(1, Collections.singletonList("B"));
        state.markDirty(3);
        assertThat(state.takeDirtyRows()).containsExactly(1, 4);

        state.markAllDirty();
        assertThat(state.takeDirtyRows()).isNull();
    }

    @Test
    void testSetVisibleRangeFiresOnlyOnChange() {
        final PageState<String> state = new PageState<>(10);

        assertThat(state.setVisibleRange(0, 10, false)).isFalse();
        assertThat(state.setVisibleRange(10, 10, false)).isTrue();
        assertThat(state.setVisibleRange(10, 20, false)).isTrue();
        assertThat(state.getPageStart()).isEqualTo(10);
        assertThat(state.getPageSize()).isEqualTo(20);
    }

    @Test
    void testMovingForwardKeepsOverlappingRows() {
        final PageState<String> state = new PageState<>(4);
        state.setRowData(0, Arrays.asList("a", "b", "c", "d"));
        state.setRowCount(100, true);

        state.setVisibleRange(2, 4, false);

        assertThat(state.getRows()).containsExactly("c", "d");
    }

    @Test
    void testMovingBackInsertsPlaceholders() {
        final PageState<String> state = new PageState<>(4);
        state.setRowCount(100, true);
        state.setVisibleRange(2, 4, false);
        state.setRowData(2, Arrays.asList("c", "d"));

        state.setVisibleRange(0, 4, false);

        assertThat(state.getRows()).containsExactly(null, null, "c", "d");
    }

    @Test
    void testClearData() {
        final PageState<String> state = new PageState<>(4);
        state.setRowData(0, Arrays.asList("a", "b"));

        assertThat(state.setVisibleRange(0, 4, true)).isFalse();
        assertThat(state.getRows()).isEmpty();
    }

    @Test
    void testNegativeRangeIsRejected() {
        final PageState<String> state = new PageState<>(4);

        assertThatThrownBy(() -> state.setVisibleRange(-1, 4, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state.setVisibleRange(0, -4, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetRowDataForWholeListPattern() {
        // AbstractHasData.setRowData(List): count, then range, then data.
        final PageState<String> state = new PageState<>(100);
        state.setRowCount(3, true);
        state.setVisibleRange(0, 3, false);
        state.setRowData(0, Arrays.asList("a", "b", "c"));

        assertThat(state.getRows()).containsExactly("a", "b", "c");
        assertThat(state.getPageSize()).isEqualTo(3);
    }
}
