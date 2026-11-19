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

import static org.assertj.core.api.Assertions.assertThat;

class TestScrollGutters {

    private static final int GUTTER = 12;

    @Test
    void testNoGuttersWhenEverythingFits() {
        final ScrollGutters gutters = ScrollGutters.compute(400, 500, 300, 300, false, GUTTER);

        assertThat(gutters).isEqualTo(new ScrollGutters(false, false));
    }

    @Test
    void testVerticalOnlyWhenRowsAreTaller() {
        final ScrollGutters gutters = ScrollGutters.compute(500, 500, 800, 300, false, GUTTER);

        assertThat(gutters).isEqualTo(new ScrollGutters(false, true));
    }

    @Test
    void testHorizontalOnlyWhenRowsAreWider() {
        final ScrollGutters gutters = ScrollGutters.compute(700, 500, 200, 300, false, GUTTER);

        assertThat(gutters).isEqualTo(new ScrollGutters(true, false));
    }

    @Test
    void testHorizontalGutterMakesRowsThatJustFitScroll() {
        // The rows fit the height exactly, but the gutter under them doesn't
        final ScrollGutters gutters = ScrollGutters.compute(700, 500, 300, 300, false, GUTTER);

        assertThat(gutters).isEqualTo(new ScrollGutters(true, true));
    }

    @Test
    void testHorizontalGutterNowIsNotCountedAsRows() {
        // 300px of rows plus the 12px gutter they have now: still no room without the gutter
        final ScrollGutters stillWide = ScrollGutters.compute(700, 500, 312, 300, true, GUTTER);
        assertThat(stillWide).isEqualTo(new ScrollGutters(true, true));

        // Once the rows are no wider than the scroller, the gutter's height doesn't keep the
        // vertical track
        final ScrollGutters narrowed = ScrollGutters.compute(500, 500, 312, 312, true, GUTTER);
        assertThat(narrowed).isEqualTo(new ScrollGutters(false, false));
    }

    @Test
    void testVerticalGutterNeverMakesRowsScrollSideways() {
        // Rows exactly as wide as the scroller, and tall enough to scroll: only the vertical track
        final ScrollGutters gutters = ScrollGutters.compute(500, 500, 900, 300, false, GUTTER);

        assertThat(gutters.horizontal()).isFalse();
        assertThat(gutters.vertical()).isTrue();
    }

    @Test
    void testEmptyScroller() {
        final ScrollGutters gutters = ScrollGutters.compute(0, 0, 0, 0, false, GUTTER);

        assertThat(gutters).isEqualTo(new ScrollGutters(false, false));
    }
}
