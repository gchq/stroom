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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestColumnWidthResolver {

    @Test
    void testFixedColumnsKeepDeclaredWidths() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.fixed(22), ColumnSizing.resizable(200)),
                1000);

        // No fill column, so the space on the right stays empty (R-L4).
        assertThat(widths).containsExactly(22, 200);
    }

    @Test
    void testPercentColumnUsesInitialWidth() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.percent(25), ColumnSizing.percent(33.3)),
                1001);

        // Truncated, not rounded.
        assertThat(widths).containsExactly(250, 333);
    }

    @Test
    void testSingleFillColumnTakesRemainingSpace() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.fixed(100), ColumnSizing.fill(200)),
                1000);

        assertThat(widths).containsExactly(100, 900);
    }

    @Test
    void testFillIsResolvedAfterPercent() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.fixed(100), ColumnSizing.percent(50), ColumnSizing.fill(50)),
                1000);

        // 1000 - 100 - 500 - 50 = 350 spare, all to the fill column.
        assertThat(widths).containsExactly(100, 500, 400);
    }

    @Test
    void testFillColumnsShareByWeightAndTruncate() {
        // Mirrors Tools -> Dependencies: four fill columns.
        final List<ColumnSizing> sizings = Arrays.asList(
                ColumnSizing.fill(120),
                ColumnSizing.fill(300),
                ColumnSizing.fill(120),
                ColumnSizing.fill(300));
        final int[] widths = ColumnWidthResolver.resolve(sizings, 1110);

        // Spare = 1110 - 840 = 270, 67.5 each, truncated to 67.
        assertThat(widths).containsExactly(187, 367, 187, 367);
    }

    @Test
    void testUnequalWeights() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.fill(100, 100), ColumnSizing.fill(300, 100)),
                600);

        // Spare 400 split 1:3.
        assertThat(widths).containsExactly(200, 400);
    }

    @Test
    void testFillNeverShrinksWhenColumnsOverflow() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.fixed(400), ColumnSizing.percent(80), ColumnSizing.fill(150)),
                500);

        // Fixed + percent already overflow, so fill keeps its floor and the grid scrolls.
        assertThat(widths).containsExactly(400, 400, 150);
    }

    @Test
    void testZeroWeightFillGetsNothing() {
        final int[] widths = ColumnWidthResolver.resolve(
                Arrays.asList(ColumnSizing.fill(0, 100), ColumnSizing.fill(100)),
                500);

        assertThat(widths).containsExactly(100, 400);
    }

    @Test
    void testNoColumns() {
        assertThat(ColumnWidthResolver.resolve(Collections.emptyList(), 500)).isEmpty();
    }

    @Test
    void testZeroOuterWidthIsRejected() {
        assertThatThrownBy(() -> ColumnWidthResolver.resolve(
                Collections.singletonList(ColumnSizing.fill(100)), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFillReportsWhetherAnythingChanged() {
        final List<ColumnSizing> sizings = Arrays.asList(ColumnSizing.fixed(100), ColumnSizing.fill(100));
        final int[] widths = {100, 100};

        assertThat(ColumnWidthResolver.fill(sizings, widths, 200)).isFalse();
        assertThat(widths).containsExactly(100, 100);

        assertThat(ColumnWidthResolver.fill(sizings, widths, 300)).isTrue();
        assertThat(widths).containsExactly(100, 200);
    }

    @Test
    void testUnfillSharesByWeight() {
        final List<ColumnSizing> sizings = Arrays.asList(
                ColumnSizing.fixed(100), ColumnSizing.fill(100, 100), ColumnSizing.fill(300, 100));
        final int[] widths = {100, 200, 400};

        assertThat(ColumnWidthResolver.unfill(sizings, widths, 40)).isEqualTo(40);
        assertThat(widths).containsExactly(100, 190, 370);
    }

    @Test
    void testUnfillStopsAtTheFloor() {
        final List<ColumnSizing> sizings = Arrays.asList(ColumnSizing.fill(100), ColumnSizing.fill(100));
        final int[] widths = {110, 150};

        // Half each is asked for, but the first has only 10 above its floor; the last makes it up
        assertThat(ColumnWidthResolver.unfill(sizings, widths, 60)).isEqualTo(60);
        assertThat(widths).containsExactly(100, 100);

        // Nothing is left above the floors
        assertThat(ColumnWidthResolver.unfill(sizings, widths, 10)).isZero();
        assertThat(widths).containsExactly(100, 100);
    }

    @Test
    void testUnfillWithoutFillColumns() {
        final int[] widths = {100, 200};

        assertThat(ColumnWidthResolver.unfill(
                Arrays.asList(ColumnSizing.fixed(100), ColumnSizing.resizable(200)), widths, 20)).isZero();
        assertThat(widths).containsExactly(100, 200);
    }
}
