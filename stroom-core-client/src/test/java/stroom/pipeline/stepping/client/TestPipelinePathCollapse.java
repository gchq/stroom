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

package stroom.pipeline.stepping.client;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelinePathCollapse {

    private static final int ELLIPSIS = 30;

    @Test
    void testCollapse_allFit() {
        final boolean[] collapsed = PipelinePathCollapse.collapse(widths(5, 100), 500, 2, ELLIPSIS);

        assertThat(collapsed)
                .containsExactly(false, false, false, false, false);
    }

    @Test
    void testCollapse_furthestFromSelectedFirst() {
        // 8 x 100 = 800 in 600: collapsing 1 and 2 (-200 + 30) makes 630, so 3 goes too (530)
        final boolean[] collapsed = PipelinePathCollapse.collapse(widths(8, 100), 600, 5, ELLIPSIS);

        assertThat(collapsed)
                .containsExactly(false, true, true, true, false, false, false, false);
        assertThat(PipelinePathCollapse.getWidth(widths(8, 100), collapsed, ELLIPSIS))
                .isEqualTo(530);
    }

    @Test
    void testCollapse_bothSidesOfSelected() {
        // Selected in the middle of 11: both sides collapse, each into its own ellipsis
        final boolean[] collapsed = PipelinePathCollapse.collapse(widths(11, 100), 600, 5, ELLIPSIS);

        assertThat(collapsed)
                .containsExactly(false, true, true, true, false, false, false, true, true, true, false);
        assertThat(PipelinePathCollapse.getWidth(widths(11, 100), collapsed, ELLIPSIS))
                .isEqualTo(560);
    }

    @Test
    void testCollapse_endsSelectedAndNeighboursKept() {
        // Far too narrow: only those that may be collapsed are
        final boolean[] collapsed = PipelinePathCollapse.collapse(widths(7, 100), 50, 3, ELLIPSIS);

        assertThat(collapsed)
                .containsExactly(false, true, false, false, false, true, false);
    }

    @Test
    void testCollapse_noSelection() {
        // With nothing selected the start is kept
        final boolean[] collapsed = PipelinePathCollapse.collapse(widths(6, 100), 450, -1, ELLIPSIS);

        assertThat(collapsed)
                .containsExactly(false, false, false, true, true, false);
    }

    @Test
    void testCollapse_tooFewToCollapse() {
        final boolean[] collapsed = PipelinePathCollapse.collapse(widths(3, 100), 100, 1, ELLIPSIS);

        assertThat(collapsed)
                .containsExactly(false, false, false);
    }

    @Test
    void testCollapse_empty() {
        assertThat(PipelinePathCollapse.collapse(new int[0], 100, -1, ELLIPSIS))
                .isEmpty();
    }

    @Test
    void testGetWidth_eachRunOneEllipsis() {
        final int[] widths = widths(6, 100);
        final boolean[] collapsed = {false, true, true, false, true, false};

        assertThat(PipelinePathCollapse.getWidth(widths, collapsed, ELLIPSIS))
                .isEqualTo(300 + 2 * ELLIPSIS);
    }

    private static int[] widths(final int count, final int width) {
        final int[] widths = new int[count];
        Arrays.fill(widths, width);
        return widths;
    }
}
