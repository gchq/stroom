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

package stroom.ai.impl.vis;

import stroom.ai.impl.vis.TimelineRows.Bar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimelineRows {

    @Test
    void overlappingBarsAreDrawnOneUnderAnother() {
        // 0-10 and 10-20 share a row (one starts as the other ends); 5-8 overlaps the first, and 8-9 follows 5-8.
        final List<Bar> bars = List.of(new Bar(0, 10), new Bar(5, 8), new Bar(10, 20), new Bar(8, 9));

        assertThat(TimelineRows.pack(bars)).containsExactly(0, 1, 0, 1);
        assertThat(TimelineRows.rowCount(bars)).isEqualTo(2);
    }

    @Test
    void aMarkerIsNeverDrawnOverABarOrAnotherMarker() {
        // A marker where a bar ends goes below it, a second marker at the same time below that, and a bar starting
        // where the first ends follows it in its row.
        final List<Bar> bars = List.of(new Bar(0, 10), new Bar(10, 10), new Bar(10, 10), new Bar(10, 20));

        assertThat(TimelineRows.pack(bars)).containsExactly(0, 1, 2, 0);
        // A marker within a bar.
        assertThat(TimelineRows.pack(List.of(new Bar(0, 10), new Bar(5, 5)))).containsExactly(0, 1);
        // A marker before a bar, apart from it, shares its row.
        assertThat(TimelineRows.pack(List.of(new Bar(20, 30), new Bar(5, 5)))).containsExactly(0, 0);
    }

    @Test
    void barsArePlacedInOrderOfStartAndGivenInTheOrderGiven() {
        assertThat(TimelineRows.pack(List.of(new Bar(20, 30), new Bar(0, 25)))).containsExactly(1, 0);
        // The same start: the shorter first.
        assertThat(TimelineRows.pack(List.of(new Bar(0, 30), new Bar(0, 10), new Bar(10, 40))))
                .containsExactly(1, 0, 0);
    }

    @Test
    void barsNotOverlappingShareOneRow() {
        assertThat(TimelineRows.pack(List.of(new Bar(0, 1), new Bar(2, 3), new Bar(4, 5)))).containsExactly(0, 0, 0);
        assertThat(TimelineRows.rowCount(List.of(new Bar(0, 1), new Bar(2, 3)))).isEqualTo(1);
    }

    @Test
    void noBarsNeedNoRows() {
        assertThat(TimelineRows.pack(List.of())).isEmpty();
        assertThat(TimelineRows.rowCount(List.of())).isZero();
    }

    @Test
    void aBarEndingAsItStartsIsAMarker() {
        assertThat(new Bar(5, 5).isMarker()).isTrue();
        assertThat(new Bar(5, 6).isMarker()).isFalse();
    }
}
