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

package stroom.gwt.workbench.client.widgets.datagrid;

import stroom.data.grid.client.MyDataGrid;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRows.Person;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// A harness for comparing the new [DataGridWidget] with `MyDataGrid`: performance, and the
/// behaviour the design's Phase 0 measures (where the keyboard row and the scroll position end up
/// when new data arrives).
///
/// The story shows an empty frame and does nothing on its own. It puts a `dgBench` object on the
/// page's window with synchronous operations (set up a grid of either kind with N rows, send the
/// rows again, change some rows, select a row, resize a column, scroll), each of which renders,
/// forces layout and returns the time it took in milliseconds. A script drives it, repeating each
/// operation and timing the next painted frame as well. Both grids get the same five columns and
/// the same generated rows.
///
/// For the behaviour measurements, `replace` sends different data as a presenter would,
/// `rebuildColumns` removes and re-adds every column as the dashboard does on a settings change,
/// and `state` reports the keyboard row, focus, selection and scroll position.
public final class BenchmarkStories {

    private static final int FRAME_HEIGHT = 500;
    private static final int FRAME_WIDTH = 900;

    // The grid under test. Static, as the window functions are static.
    private static SimplePanel host;
    private static Widget grid;
    private static MyDataGrid<Person> oldGrid;
    private static DataGridWidget<Person> newGrid;
    private static MultiSelectionModelImpl<Person> selectionModel;
    private static List<Person> rows;

    private BenchmarkStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/Benchmark", BenchmarkStories.class)
                .layout(StoryLayout.PADDED)
                // An empty frame: a script drives window.dgBench (see the class comment)
                .story("Harness", BenchmarkStories::harness);
    }

    private static Widget harness(final StoryContext context) {
        host = new SimplePanel();
        host.getElement().getStyle().setProperty("height", FRAME_HEIGHT + "px");
        host.getElement().getStyle().setProperty("width", FRAME_WIDTH + "px");
        host.setWidget(new Label("Driven by window.dgBench"));
        exportFunctions();
        return host;
    }

    // --------------------------------------------------------------------------------
    // Operations, called from the page. Each returns the milliseconds it took to render and lay out.

    /// @param kind      `old`, `new` or `virtual`.
    /// @param rowCount  The number of rows, all on one page.
    /// @param selection Whether to add a multi-select selection model.
    private static double setup(final String kind, final int rowCount, final boolean selection) {
        host.clear();
        oldGrid = null;
        newGrid = null;
        selectionModel = null;
        rows = DataGridRows.generatedPeople(rowCount);
        final int pageSize = Math.max(rowCount, 1);

        final double start = now();
        if ("old".equals(kind)) {
            oldGrid = new MyDataGrid<>(new SimpleEventBus(), pageSize);
            addColumns(oldGrid::addResizableColumn);
            if (selection) {
                selectionModel = oldGrid.addDefaultSelectionModel(true);
            }
            grid = oldGrid;
        } else {
            newGrid = new DataGridWidget<>(new SimpleEventBus(), pageSize);
            newGrid.setVirtual("virtual".equals(kind));
            addColumns(newGrid::addResizableColumn);
            if (selection) {
                selectionModel = newGrid.addDefaultSelectionModel(true);
            }
            grid = newGrid;
        }
        grid.setWidth("100%");
        grid.setHeight("100%");
        host.setWidget(grid);
        setRowData(rows);
        flushAndLayout();
        return now() - start;
    }

    /// Send the same rows again, as an unchanged poll does.
    private static double refresh() {
        final double start = now();
        setRowData(new ArrayList<>(rows));
        flushAndLayout();
        return now() - start;
    }

    /// Replace some rows, spread through the page, with changed copies, and send all the rows.
    private static double refreshChanged(final int count) {
        final List<Person> next = new ArrayList<>(rows);
        final int step = Math.max(1, next.size() / Math.max(1, count));
        for (int i = 0, changed = 0; i < next.size() && changed < count; i += step, changed++) {
            final Person person = next.get(i);
            next.set(i, new Person(person.getId(),
                    person.getName(),
                    person.getRole(),
                    person.getAge() + 1,
                    !person.isActive()));
        }
        rows = next;
        final double start = now();
        setRowData(new ArrayList<>(rows));
        flushAndLayout();
        return now() - start;
    }

    /// Select one row, as a click does.
    private static double select(final int index) {
        final double start = now();
        selectionModel.setSelected(rows.get(Math.min(index, rows.size() - 1)));
        flushAndLayout();
        return now() - start;
    }

    /// Resize the Name column, as one frame of a column drag does.
    private static double resize(final int width) {
        final double start = now();
        if (oldGrid != null) {
            oldGrid.resizeColumn(1, width);
        } else {
            newGrid.resizeColumn(1, width);
        }
        flushAndLayout();
        return now() - start;
    }

    /// Send different data, as a presenter does when new data arrives.
    ///
    /// @param what `same` (an unchanged refresh), `changed` (every row's values changed, same keys),
    ///             `fewer` (the first 3 rows only), `empty` (no rows) or `restore` (the rows the
    ///             grid was set up with).
    private static double replace(final String what) {
        final List<Person> next;
        if ("changed".equals(what)) {
            next = new ArrayList<>();
            for (final Person person : rows) {
                next.add(new Person(person.getId(),
                        person.getName() + " (changed)",
                        person.getRole(),
                        person.getAge(),
                        person.isActive()));
            }
        } else if ("fewer".equals(what)) {
            next = new ArrayList<>(rows.subList(0, Math.min(3, rows.size())));
        } else if ("empty".equals(what)) {
            next = new ArrayList<>();
        } else {
            next = new ArrayList<>(rows);
        }
        final double start = now();
        setRowData(next);
        flushAndLayout();
        return now() - start;
    }

    /// Remove every column and add them again, as the dashboard's `updateColumns` does.
    private static double rebuildColumns() {
        final double start = now();
        if (oldGrid != null) {
            while (oldGrid.getColumnCount() > 0) {
                oldGrid.removeColumn(oldGrid.getColumn(0));
            }
            addColumns(oldGrid::addResizableColumn);
            oldGrid.resizeTableToFitColumns();
        } else {
            while (newGrid.getColumnCount() > 0) {
                newGrid.removeColumn(newGrid.getColumn(0));
            }
            addColumns(newGrid::addResizableColumn);
            newGrid.resizeTableToFitColumns();
        }
        flushAndLayout();
        return now() - start;
    }

    /// Move the keyboard row, as a key press does.
    private static void keyboard(final int index, final boolean stealFocus) {
        if (oldGrid != null) {
            oldGrid.setKeyboardSelectedRow(index, stealFocus);
            oldGrid.flush();
        } else {
            newGrid.setKeyboardSelectedRow(index, stealFocus);
            newGrid.flush();
        }
    }

    /// @return A description of the grid's state: the keyboard row it reports, the row showing the
    /// keyboard row style, the row with the focus, the number of selected rows and the scroll
    /// position, e.g. `kb=5 styled=5 focused=5 selected=1 scrollTop=0`.
    private static String state() {
        final int keyboardRow = oldGrid != null
                ? oldGrid.getKeyboardSelectedRow()
                : newGrid.getKeyboardSelectedRow();
        int selected = 0;
        if (selectionModel != null) {
            selected = selectionModel.getSelectedItems().size();
        }
        final Element scroller = scroller();
        return "kb=" + keyboardRow
               + " styled=" + styledKeyboardRow(grid.getElement())
               + " focused=" + focusedRow(grid.getElement())
               + " selected=" + selected
               + " scrollTop=" + (scroller != null
                ? scroller.getScrollTop()
                : 0)
               + " rows=" + (oldGrid != null
                ? oldGrid.getVisibleItemCount()
                : newGrid.getVisibleItemCount());
    }

    private static native String styledKeyboardRow(Element root) /*-{
        var row = root.querySelector(".dataGridKeyboardSelectedRow");
        if (!row) {
            return "none";
        }
        if (row.hasAttribute("data-row")) {
            return row.getAttribute("data-row");
        }
        return String(row.sectionRowIndex);
    }-*/;

    private static native String focusedRow(Element root) /*-{
        var el = $doc.activeElement;
        if (!el || !root.contains(el)) {
            return "outside";
        }
        while (el && el !== root) {
            if (el.hasAttribute && el.hasAttribute("data-row")) {
                return el.getAttribute("data-row");
            }
            if (el.tagName === "TR" && el.parentElement && el.parentElement.tagName === "TBODY") {
                return String(el.sectionRowIndex);
            }
            el = el.parentElement;
        }
        return "grid";
    }-*/;

    /// @return The grid's scrolling element: the new grid's scroller, or the first element in the
    /// old grid that scrolls vertically.
    private static Element scroller() {
        if (newGrid != null) {
            return findByClass(newGrid.getElement(), "dg-scroller");
        }
        return findScroller(oldGrid.getElement());
    }

    /// @return The number of elements in the grid.
    private static int domCount() {
        return grid.getElement().getElementsByTagName("*").getLength();
    }

    // --------------------------------------------------------------------------------

    private static void setRowData(final List<Person> values) {
        if (oldGrid != null) {
            oldGrid.setRowData(0, values);
            oldGrid.setRowCount(values.size(), true);
        } else {
            newGrid.setRowData(0, values);
            newGrid.setRowCount(values.size(), true);
        }
    }

    private static void flushAndLayout() {
        if (oldGrid != null) {
            oldGrid.flush();
        } else {
            newGrid.flush();
        }
        // Reading a size forces style and layout, so the time includes them.
        grid.getElement().getOffsetHeight();
        final Element scroller = scroller();
        if (scroller != null) {
            scroller.getScrollHeight();
        }
    }

    private static void addColumns(final ColumnAdder adder) {
        adder.add(DataGridUtil.textColumnBuilder((Person person) -> String.valueOf(person.getId()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("ID").rightAligned().build(),
                60);
        adder.add(DataGridUtil.textColumnBuilder(Person::getName).build(),
                DataGridUtil.headingBuilder("Name").build(),
                200);
        adder.add(DataGridUtil.textColumnBuilder(Person::getRole).build(),
                DataGridUtil.headingBuilder("Role").build(),
                120);
        adder.add(DataGridUtil.textColumnBuilder((Person person) -> String.valueOf(person.getAge()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Age").rightAligned().build(),
                70);
        adder.add(DataGridUtil.textColumnBuilder(Person::getActiveText).centerAligned().build(),
                DataGridUtil.headingBuilder("Active").centerAligned().build(),
                80);
    }

    private static Element findByClass(final Element root, final String className) {
        if (root.hasClassName(className)) {
            return root;
        }
        for (Element child = root.getFirstChildElement(); child != null; child = child.getNextSiblingElement()) {
            final Element found = findByClass(child, className);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // The descendant, no taller than the grid, that scrolls vertically the furthest: the old grid's
    // body scroller. GWT's resize detectors also scroll, but are far taller than the grid.
    private static native Element findScroller(Element root) /*-{
        var best = null;
        var bestRange = 0;
        var all = root.querySelectorAll("*");
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var style = $wnd.getComputedStyle(el);
            var range = el.scrollHeight - el.clientHeight;
            if ((style.overflowY === "auto" || style.overflowY === "scroll") && style.visibility !== "hidden"
                && el.clientHeight <= root.clientHeight && range > bestRange) {
                best = el;
                bestRange = range;
            }
        }
        return best;
    }-*/;

    private static native double now() /*-{
        return $wnd.performance.now();
    }-*/;

    private static native void exportFunctions() /*-{
        $wnd.dgBench = {
            setup: $entry(function (kind, rows, selection) {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::setup(Ljava/lang/String;IZ)(
                    kind, rows, selection);
            }),
            refresh: $entry(function () {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::refresh()();
            }),
            refreshChanged: $entry(function (count) {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::refreshChanged(I)(count);
            }),
            select: $entry(function (index) {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::select(I)(index);
            }),
            resize: $entry(function (width) {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::resize(I)(width);
            }),
            scroller: $entry(function () {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::scroller()();
            }),
            domCount: $entry(function () {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::domCount()();
            }),
            replace: $entry(function (what) {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::replace(Ljava/lang/String;)(
                    what);
            }),
            rebuildColumns: $entry(function () {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::rebuildColumns()();
            }),
            keyboard: $entry(function (index, stealFocus) {
                @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::keyboard(IZ)(index, stealFocus);
            }),
            state: $entry(function () {
                return @stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories::state()();
            })
        };
    }-*/;


    // --------------------------------------------------------------------------------


    /// Adds a resizable column to either grid.
    private interface ColumnAdder {

        void add(Column<Person, ?> column, Header<?> header, int width);
    }
}
