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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRowWritePlan {

    private static final int[] THREE = {0, 1, 2};
    private static final List<String> BEFORE = Arrays.asList("a", "b", "c");

    @Test
    void nothingChangedWritesNothing() {
        final RowWritePlan plan = RowWritePlan.plan(BEFORE, THREE, BEFORE, THREE, 1, false);

        assertThat(plan.isNothing()).isTrue();
        assertThat(plan.isWholeRow()).isFalse();
    }

    @Test
    void focusOutsideTheRowWritesTheWholeRow() {
        final RowWritePlan plan = RowWritePlan.plan(BEFORE, THREE, Arrays.asList("a", "B", "c"), THREE, -1, false);

        assertThat(plan.isWholeRow()).isTrue();
        assertThat(plan.isResetFocus()).isFalse();
    }

    @Test
    void focusInsideAChangedCellLeavesItAndWritesTheOthers() {
        final RowWritePlan plan = RowWritePlan.plan(
                BEFORE, THREE, Arrays.asList("A", "B", "C"), THREE, 1, false);

        assertThat(plan.isWholeRow()).isFalse();
        assertThat(plan.getCells()).containsExactly(0, 2);
        assertThat(plan.getLeftStale()).isEqualTo(1);
        assertThat(plan.getContentOnly()).isEqualTo(-1);
    }

    @Test
    void focusInsideAnUnchangedCellWritesOnlyTheChangedOnes() {
        final RowWritePlan plan = RowWritePlan.plan(
                BEFORE, THREE, Arrays.asList("a", "b", "C"), THREE, 1, false);

        assertThat(plan.getCells()).containsExactly(2);
        assertThat(plan.getLeftStale()).isEqualTo(-1);
    }

    @Test
    void focusOnAChangedCellsElementWritesItsContentOnly() {
        final RowWritePlan plan = RowWritePlan.plan(
                BEFORE, THREE, Arrays.asList("a", "B", "C"), THREE, 1, true);

        assertThat(plan.getCells()).containsExactly(2);
        assertThat(plan.getContentOnly()).isEqualTo(1);
        assertThat(plan.getLeftStale()).isEqualTo(-1);
    }

    @Test
    void aForgottenCellCountsAsChanged() {
        final RowWritePlan plan = RowWritePlan.plan(
                Arrays.asList("a", null, "c"), THREE, BEFORE, THREE, 1, false);

        // The cell whose HTML was forgotten (it rewrote its own DOM) is left while it has the focus
        assertThat(plan.getCells()).isEmpty();
        assertThat(plan.getLeftStale()).isEqualTo(1);
    }

    @Test
    void aRowNeverWrittenIsWrittenWhole() {
        final RowWritePlan plan = RowWritePlan.plan(null, null, BEFORE, THREE, -1, false);

        assertThat(plan.isWholeRow()).isTrue();
        assertThat(plan.isResetFocus()).isFalse();
    }

    @Test
    void anAddedColumnWritesTheWholeRowAndPutsTheFocusBack() {
        final RowWritePlan plan = RowWritePlan.plan(
                BEFORE, THREE, Arrays.asList("a", "b", "c", "d"), new int[]{0, 1, 2, 3}, 1, false);

        assertThat(plan.isWholeRow()).isTrue();
        assertThat(plan.isResetFocus()).isTrue();
    }

    @Test
    void aChangedSpanWritesTheWholeRow() {
        // Column 1 now spans column 2, so the row has two cells, at columns 0 and 1
        final RowWritePlan plan = RowWritePlan.plan(
                BEFORE, THREE, Arrays.asList("a", "bc"), new int[]{0, 1}, 0, true);

        assertThat(plan.isWholeRow()).isTrue();
        assertThat(plan.isResetFocus()).isTrue();
    }

    @Test
    void theSameCellsAtOtherColumnsWriteTheWholeRow() {
        final RowWritePlan plan = RowWritePlan.plan(
                BEFORE, THREE, BEFORE, new int[]{0, 2, 3}, -1, false);

        assertThat(plan.isWholeRow()).isTrue();
    }

    @Test
    void afterIsRequired() {
        assertThatThrownBy(() -> RowWritePlan.plan(BEFORE, THREE, null, THREE, -1, false))
                .isInstanceOf(NullPointerException.class);
    }
}
