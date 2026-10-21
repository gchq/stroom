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

import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSelectionRules {

    private static final List<String> ROWS = Arrays.asList("r0", "r1", "r2", "r3", "r4");

    @Test
    void testPlainClickSelectsOnlyThatRow() {
        final Selection<String> selection = selectionOf("r0", "r1");

        final String anchor = SelectionRules.select(selection, ROWS, "r0", "r3", type(true, false, false));

        assertThat(selection.getSelectedItems()).containsExactly("r3");
        assertThat(anchor).isEqualTo("r3");
    }

    @Test
    void testCtrlClickTogglesAndMovesAnchor() {
        final Selection<String> selection = selectionOf("r0");

        String anchor = SelectionRules.select(selection, ROWS, "r0", "r2", type(true, true, false));
        assertThat(selection.getSelectedItems()).containsExactlyInAnyOrder("r0", "r2");
        assertThat(anchor).isEqualTo("r2");

        anchor = SelectionRules.select(selection, ROWS, anchor, "r0", type(true, true, false));
        assertThat(selection.getSelectedItems()).containsExactly("r2");
        assertThat(anchor).isEqualTo("r0");
    }

    @Test
    void testShiftClickSelectsRangeAndKeepsAnchor() {
        final Selection<String> selection = selectionOf("r4");

        final String anchor = SelectionRules.select(selection, ROWS, "r3", "r1", type(true, false, true));

        assertThat(selection.getSelectedItems()).containsExactlyInAnyOrder("r1", "r2", "r3");
        assertThat(anchor).isEqualTo("r3");
    }

    @Test
    void testCtrlShiftClickAddsRange() {
        final Selection<String> selection = selectionOf("r4");

        SelectionRules.select(selection, ROWS, "r0", "r1", type(true, true, true));

        assertThat(selection.getSelectedItems()).containsExactlyInAnyOrder("r0", "r1", "r4");
    }

    @Test
    void testShiftClickWithAnchorOffPage() {
        final Selection<String> selection = selectionOf("r4");

        final String anchor = SelectionRules.select(selection, ROWS, "gone", "r1", type(true, false, true));

        assertThat(selection.getSelectedItems()).containsExactly("r1");
        assertThat(anchor).isEqualTo("r1");
    }

    @Test
    void testShiftClickWithoutAnchorIsPlainClick() {
        final Selection<String> selection = selectionOf("r4");

        final String anchor = SelectionRules.select(selection, ROWS, null, "r1", type(true, false, true));

        assertThat(selection.getSelectedItems()).containsExactly("r1");
        assertThat(anchor).isEqualTo("r1");
    }

    @Test
    void testSingleSelectIgnoresModifiers() {
        final Selection<String> selection = selectionOf("r0");

        SelectionRules.select(selection, ROWS, "r0", "r2", type(false, true, true));

        assertThat(selection.getSelectedItems()).containsExactly("r2");
    }

    @Test
    void testNullRowClears() {
        final Selection<String> selection = selectionOf("r0", "r1");

        final String anchor = SelectionRules.select(selection, ROWS, "r0", null, type(true, false, false));

        assertThat(selection.size()).isZero();
        assertThat(anchor).isNull();
    }

    @Test
    void testMoveDownAndUp() {
        assertThat(SelectionRules.move(ROWS, 1, 2, 1, 1, row -> true)).isEqualTo(2);
        assertThat(SelectionRules.move(ROWS, 1, 0, -1, 1, row -> true)).isZero();
    }

    @Test
    void testMoveClampsToEnds() {
        assertThat(SelectionRules.move(ROWS, 4, 5, 1, 1, row -> true)).isEqualTo(4);
        assertThat(SelectionRules.move(ROWS, 0, -1, -1, 1, row -> true)).isZero();
    }

    @Test
    void testMoveSkipsUnselectableRows() {
        assertThat(SelectionRules.move(ROWS, 1, 2, 1, 1, row -> !row.equals("r2"))).isEqualTo(3);
        // Nothing selectable in the direction of travel leaves the row where it was.
        assertThat(SelectionRules.move(ROWS, 3, 4, 1, 1, row -> !row.equals("r4"))).isEqualTo(3);
    }

    @Test
    void testPageDown() {
        final List<String> rows = Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");

        assertThat(SelectionRules.move(rows, 0, SelectionRules.PAGE_SIZE, 1, SelectionRules.PAGE_SIZE, row -> true))
                .isEqualTo(10);
        assertThat(SelectionRules.move(rows, 5, 15, 1, SelectionRules.PAGE_SIZE, row -> true))
                .isEqualTo(11);
    }

    @Test
    void testMoveWithNoRows() {
        assertThat(SelectionRules.move(Collections.<String>emptyList(), -1, 0, 1, 1, row -> true))
                .isEqualTo(-1);
    }

    private static Selection<String> selectionOf(final String... rows) {
        final Selection<String> selection = new Selection<>();
        for (final String row : rows) {
            selection.setSelected(row, true);
        }
        return selection;
    }

    @Test
    void testCmdActsAsCtrlOnlyOnMacOs() {
        // Ctrl is Ctrl everywhere
        assertThat(SelectionRules.isCtrl(true, false, false)).isTrue();
        assertThat(SelectionRules.isCtrl(true, false, true)).isTrue();
        // Cmd (meta) stands in for it on macOS only (design deviation D3)
        assertThat(SelectionRules.isCtrl(false, true, true)).isTrue();
        assertThat(SelectionRules.isCtrl(false, true, false)).isFalse();
        assertThat(SelectionRules.isCtrl(false, false, true)).isFalse();
    }

    private static SelectionType type(final boolean allowMulti, final boolean ctrl, final boolean shift) {
        return new SelectionType(false, false, allowMulti, ctrl, shift);
    }
}
