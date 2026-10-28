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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestColumnModel {

    @Test
    void testResolveOnlyOnFirstNonZeroWidth() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("a", ColumnSizing.fixed(100));
        model.add("b", ColumnSizing.fill(100));

        // A grid built in a hidden tab has no width yet.
        assertThat(model.resolve(0)).isFalse();
        assertThat(model.isResolved()).isFalse();
        assertThat(model.getWidths()).containsExactly(100, 100);

        assertThat(model.resolve(500)).isTrue();
        assertThat(model.getWidths()).containsExactly(100, 400);

        // Later resizes of the container never re-run it.
        assertThat(model.resolve(900)).isFalse();
        assertThat(model.getWidths()).containsExactly(100, 400);
        assertThat(model.getTotalWidth()).isEqualTo(500);
    }

    @Test
    void testSetWidthWithinFillTakesBackWhatTheColumnGains() {
        // Server Tasks: the expander column widens as the tree deepens (25 to 45). The fill
        // column gives the 20 back, rather than the columns running past the grid's edge.
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("expander", ColumnSizing.fixed(25));
        model.add("info", ColumnSizing.fill(200));
        model.resolve(500);
        assertThat(model.getWidths()).containsExactly(25, 475);

        model.setWidthWithinFill(0, 45, 500);
        assertThat(model.getWidths()).containsExactly(45, 455);
        assertThat(model.getTotalWidth()).isEqualTo(500);

        // Narrower again: the gap is left for a fill to take
        model.setWidthWithinFill(0, 25, 500);
        assertThat(model.getWidths()).containsExactly(25, 455);
    }

    @Test
    void testSetWidthWithinFillKeepsAnOverflowThatWasThere() {
        // The user widened the fill column past the edge: only the expander's gain is taken back
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("expander", ColumnSizing.fixed(25));
        model.add("info", ColumnSizing.fill(200));
        model.resolve(500);
        model.resize(1, 600);

        model.setWidthWithinFill(0, 45, 500);
        assertThat(model.getWidths()).containsExactly(45, 580);
    }

    @Test
    void testSetWidthWithinFillLeavesTheFloor() {
        // A fill column already at its floor gives nothing, so the columns overflow
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("expander", ColumnSizing.fixed(25));
        model.add("info", ColumnSizing.fill(200));
        model.resolve(225);

        model.setWidthWithinFill(0, 45, 225);
        assertThat(model.getWidths()).containsExactly(45, 200);
    }

    @Test
    void testResolveWaitsForColumns() {
        // A controller that adds its columns once a fetch returns: the grid is laid out first
        final ColumnModel<String> model = new ColumnModel<>();
        assertThat(model.resolve(500)).isFalse();
        assertThat(model.isResolved()).isFalse();

        model.add("a", ColumnSizing.fixed(100));
        model.add("b", ColumnSizing.fill(200));
        assertThat(model.resolve(500)).isTrue();
        assertThat(model.getWidths()).containsExactly(100, 400);
    }

    @Test
    void testColumnsAddedAfterResolveAreSizedImmediately() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("a", ColumnSizing.fixed(100));
        model.resolve(800);

        model.add("b", ColumnSizing.percent(25));
        model.add("c", ColumnSizing.fill(50));

        // Percent uses the width the grid had when first resolved; fill gets its floor.
        assertThat(model.getWidths()).containsExactly(100, 200, 50);
    }

    @Test
    void testFillCanBeRequestedAgain() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("a", ColumnSizing.fixed(100));
        model.add("b", ColumnSizing.fill(100));
        model.resolve(300);

        assertThat(model.fill(500)).isTrue();
        assertThat(model.getWidths()).containsExactly(100, 400);

        // Never shrinks.
        assertThat(model.fill(200)).isFalse();
        assertThat(model.getWidths()).containsExactly(100, 400);
    }

    @Test
    void testResizeHonoursPerColumnMinimum() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("a", ColumnSizing.resizable(100));
        model.add("b", ColumnSizing.resizable(100).withMinWidth(80));

        assertThat(model.resize(0, 5)).isEqualTo(ColumnSizing.DEFAULT_MIN_WIDTH);
        assertThat(model.resize(1, 50)).isEqualTo(80);
        assertThat(model.resize(1, 150)).isEqualTo(150);
        assertThat(model.getWidths()).containsExactly(30, 150);
    }

    @Test
    void testSetWidthTreatsZeroAsOnePixel() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("expander", ColumnSizing.fixed(0));
        model.setWidth(0, 0);

        assertThat(model.getWidth(0)).isEqualTo(1);
        assertThat(model.toGridTemplate()).isEqualTo("1px");
    }

    @Test
    void testListTemplateLastColumnFills() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("icon", ColumnSizing.fixed(22));
        model.add("name", ColumnSizing.fixed(200));

        assertThat(model.toListTemplate()).isEqualTo("22px minmax(200px, 1fr)");
    }

    @Test
    void testListTemplateFillColumnFills() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("icon", ColumnSizing.fixed(22));
        model.add("name", ColumnSizing.fill(100));
        model.add("filter", ColumnSizing.fixed(22));

        // The name column fills, not the filter icon after it
        assertThat(model.toListTemplate()).isEqualTo("22px minmax(100px, 1fr) 22px");
    }

    @Test
    void testListTemplateFillColumnShrinksToItsFloorNotItsGrownWidth() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("icon", ColumnSizing.fixed(22));
        model.add("name", ColumnSizing.fill(100));
        model.add("filter", ColumnSizing.fixed(22));
        // The fill column grew on a wider layout
        model.setWidth(1, 210);

        // It can shrink back to its floor, e.g. to make room for the row's padding
        assertThat(model.toListTemplate()).isEqualTo("22px minmax(100px, 1fr) 22px");
        assertThat(model.getListMinWidth()).isEqualTo(144);
    }

    @Test
    void testListTemplateOneColumn() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("name", ColumnSizing.fixed(0));

        assertThat(model.toListTemplate()).isEqualTo("minmax(1px, 1fr)");
    }

    @Test
    void testListTemplateNoColumns() {
        assertThat(new ColumnModel<String>().toListTemplate()).isEmpty();
    }

    @Test
    void testMoveUsesMyDataGridIndexConvention() {
        final ColumnModel<String> model = modelOf("a", "b", "c", "d");

        // Move "a" to before "c" (index 2 in the original order).
        model.move(0, 2);
        assertThat(columns(model)).containsExactly("b", "a", "c", "d");

        // Move "d" to the front.
        model.move(3, 0);
        assertThat(columns(model)).containsExactly("d", "b", "a", "c");

        // Move "d" to the end.
        model.move(0, 4);
        assertThat(columns(model)).containsExactly("b", "a", "c", "d");
    }

    @Test
    void testMoveKeepsWidthWithColumn() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("a", ColumnSizing.resizable(10));
        model.add("b", ColumnSizing.resizable(20));
        model.move(0, 2);

        assertThat(columns(model)).containsExactly("b", "a");
        assertThat(model.getWidths()).containsExactly(20, 10);
    }

    @Test
    void testInsertPositionUsesColumnMidpoints() {
        // Columns at 0-100, 100-200, 200-300, 300-400.
        final ColumnModel<String> model = modelOf("a", "b", "c", "d");

        // Dragging "d" left of b's midpoint goes before "b".
        assertThat(model.insertPosition(3, 140)).isEqualTo(1);
        // Dragging "a" past c's midpoint goes after "c".
        assertThat(model.insertPosition(0, 260)).isEqualTo(3);
        // Dragging "a" beyond the last column goes to the end.
        assertThat(model.insertPosition(0, 1000)).isEqualTo(4);
    }

    @Test
    void testInsertPositionDirectlyAfterItselfIsNoMove() {
        final ColumnModel<String> model = modelOf("a", "b", "c", "d");

        // "b" dragged slightly right, still left of "c"'s left edge: position 1 means no move.
        assertThat(model.insertPosition(1, 190)).isEqualTo(1);
        // The last column dragged anywhere to its right stays put.
        assertThat(model.insertPosition(3, 1000)).isEqualTo(3);
    }

    @Test
    void testInsertPositionNotBeforeFirstMovableColumn() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("tick", ColumnSizing.fixed(100));
        model.add("b", ColumnSizing.resizable(100));
        model.add("c", ColumnSizing.resizable(100));

        assertThat(model.firstMovableIndex()).isEqualTo(1);
        assertThat(model.insertPosition(2, 10)).isEqualTo(1);
    }

    @Test
    void testInsertPositionNotPastAnyNonMovableColumn() {
        // Design deviation D6: a fixed column in the middle of the table also bounds a move
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("tick", ColumnSizing.fixed(100));
        model.add("b", ColumnSizing.resizable(100));
        model.add("c", ColumnSizing.resizable(100));
        model.add("icon", ColumnSizing.fixed(100));
        model.add("e", ColumnSizing.resizable(100));
        model.add("f", ColumnSizing.resizable(100));

        // "b" dragged to the far right stops before "icon"
        assertThat(model.insertPosition(1, 1000)).isEqualTo(3);
        // "f" dragged to the far left stops after "icon"
        assertThat(model.insertPosition(5, 10)).isEqualTo(4);
        // Moves within a run are unchanged
        assertThat(model.insertPosition(2, 140)).isEqualTo(1);
        assertThat(model.insertPosition(4, 1000)).isEqualTo(6);
    }

    @Test
    void testVersionsChange() {
        final ColumnModel<String> model = new ColumnModel<>();
        final int structure = model.getStructureVersion();
        final int width = model.getWidthVersion();

        model.add("a", ColumnSizing.resizable(100));
        assertThat(model.getStructureVersion()).isGreaterThan(structure);

        final int structureAfterAdd = model.getStructureVersion();
        final int widthAfterAdd = model.getWidthVersion();
        assertThat(widthAfterAdd).isGreaterThan(width);

        // A width change does not change the structure.
        model.resize(0, 150);
        assertThat(model.getStructureVersion()).isEqualTo(structureAfterAdd);
        assertThat(model.getWidthVersion()).isGreaterThan(widthAfterAdd);
    }

    @Test
    void testRemoveAndIndexOf() {
        final ColumnModel<String> model = modelOf("a", "b", "c");

        assertThat(model.indexOf("b")).isEqualTo(1);
        assertThat(model.remove("b")).isTrue();
        assertThat(model.remove("x")).isFalse();
        assertThat(columns(model)).containsExactly("a", "c");
        assertThat(model.getLeft(1)).isEqualTo(100);

        model.clear();
        assertThat(model.size()).isZero();
    }

    @Test
    void testResizeTargetIsRightEdgeOfColumnUnderPointer() {
        final ColumnModel<String> model = modelOf("a", "b", "c");

        assertThat(model.resizeTarget(50, 10)).isEqualTo(0);
        assertThat(model.resizeTarget(150, 10)).isEqualTo(1);
        assertThat(model.resizeTarget(299, 10)).isEqualTo(2);
    }

    @Test
    void testResizeTargetNearLeftEdgeIsPreviousColumn() {
        final ColumnModel<String> model = modelOf("a", "b", "c");

        // Within the handle's width of b's left edge, and nearer it than b's right edge.
        assertThat(model.resizeTarget(100, 10)).isEqualTo(0);
        assertThat(model.resizeTarget(110, 10)).isEqualTo(0);
        assertThat(model.resizeTarget(111, 10)).isEqualTo(1);
        // The first column has no previous column.
        assertThat(model.resizeTarget(2, 10)).isEqualTo(0);
    }

    @Test
    void testResizeTargetSkipsFixedColumns() {
        final ColumnModel<String> model = new ColumnModel<>();
        model.add("tick", ColumnSizing.fixed(100));
        model.add("b", ColumnSizing.resizable(100));
        model.add("icon", ColumnSizing.fixed(100));
        model.add("d", ColumnSizing.fixed(100));

        // A fixed column with no resizable column before it has no handle.
        assertThat(model.resizeTarget(50, 10)).isEqualTo(-1);
        // Near b's left edge, but the previous column is fixed, so b's own right edge.
        assertThat(model.resizeTarget(105, 10)).isEqualTo(1);
        // Anywhere over a fixed column after a resizable one resizes that one.
        assertThat(model.resizeTarget(290, 10)).isEqualTo(1);
        assertThat(model.resizeTarget(350, 10)).isEqualTo(-1);
        // Past the last column.
        assertThat(model.resizeTarget(400, 10)).isEqualTo(-1);
        assertThat(model.resizeTarget(-1, 10)).isEqualTo(-1);
    }

    @Test
    void testColumnAt() {
        final ColumnModel<String> model = modelOf("a", "b");

        assertThat(model.columnAt(-1)).isEqualTo(-1);
        assertThat(model.columnAt(0)).isEqualTo(0);
        assertThat(model.columnAt(99.5)).isEqualTo(0);
        assertThat(model.columnAt(100)).isEqualTo(1);
        assertThat(model.columnAt(200)).isEqualTo(-1);
    }

    private static ColumnModel<String> modelOf(final String... names) {
        final ColumnModel<String> model = new ColumnModel<>();
        for (final String name : names) {
            model.add(name, ColumnSizing.resizable(100));
        }
        return model;
    }

    private static List<String> columns(final ColumnModel<String> model) {
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            list.add(model.getColumn(i));
        }
        return list;
    }
}
