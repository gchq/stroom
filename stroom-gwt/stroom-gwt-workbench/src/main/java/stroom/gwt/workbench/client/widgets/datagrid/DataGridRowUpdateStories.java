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

import stroom.cell.textfield.client.TextFieldCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.MyDataGrid;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGridWidget;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Which of a grid's rows are drawn again when its rows change, as Stroom's screens change them, for
/// the current grid ([MyDataGrid]) and the new one ([DataGridWidget]): a row drawn again has its
/// cells replaced, so a control in it loses its focus and anything typed in it but not yet saved.
///
/// The rows arrive as a refresh from the server brings them: new objects, equal to the old ones
/// (they compare equal on every field) where nothing changed. The scenarios: ticking a row's tick
/// box (its field updater saves the change, then the screen refreshes), a poll that changes
/// nothing, a poll that changes one row while another row's text field has unsaved text and the
/// focus, and choosing a row with a click.
///
/// After each, the story notes which rows were drawn again ("Rows drawn again: Charlie"): each cell's
/// content is marked, and a row with a cell whose mark has gone was drawn again.
public final class DataGridRowUpdateStories {

    private static final String GRID = "grid";
    private static final String REDRAWN = "redrawn";
    private static final String REDRAWN_PREFIX = "Rows drawn again: ";
    private static final List<String> NAMES = List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo");
    private static final int ENABLED_COLUMN = 1;
    private static final int PATH_COLUMN = 2;

    // The rendered story's poll, for the plays to run without moving the focus (as a timer would)
    private static Consumer<String> poll;

    private DataGridRowUpdateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/DataGridRowUpdates", DataGridRowUpdateStories.class)
                .layout(StoryLayout.PADDED)
                .story("CurrentGrid", context -> render(false))
                .withPlay(play -> playUpdates(play, false))
                .story("NewGrid", context -> render(true))
                .withPlay(play -> playUpdates(play, true));
    }

    // --------------------------------------------------------------------------------
    // Plays

    private static void playUpdates(final Play play, final boolean newGrid) {
        play.waitFor(() -> play.expect(play.getByTestId(REDRAWN)).toHaveTextContent(REDRAWN_PREFIX + "none"));

        // Ticking a row's box: its field updater saves the change, and the screen refreshes
        play.click(control(play, "Charlie", ENABLED_COLUMN, "[role='checkbox']"));
        expectRedrawn(play, newGrid
                ? "Charlie"
                : "Alpha, Bravo, Charlie, Delta, Echo");

        // A poll that changes nothing
        play.run("poll, nothing changed", () -> poll.accept(null));
        expectRedrawn(play, newGrid
                ? "none"
                : "Alpha, Bravo, Charlie, Delta, Echo");

        // Text typed in Echo's field but not saved, which has the focus, as a poll changes Alpha
        play.click(control(play, "Echo", PATH_COLUMN, "input"));
        play.type(control(play, "Echo", PATH_COLUMN, "input"), "/typed");
        play.run("poll, Alpha changed", () -> poll.accept("Alpha"));
        expectRedrawn(play, newGrid
                ? "Alpha"
                : "Alpha, Bravo, Charlie, Delta, Echo");
        // The typed text is kept by both (the cell keeps it while unsaved); the new grid keeps the
        // field itself, so it keeps the focus, where the current grid's is replaced, losing it
        play.waitFor(() -> play.expect(control(play, "Echo", PATH_COLUMN, "input")).toHaveValue("/echo/typed"));
        if (newGrid) {
            play.expect(control(play, "Echo", PATH_COLUMN, "input")).toHaveFocus();
        } else {
            play.expect(control(play, "Echo", PATH_COLUMN, "input")).not().toHaveFocus();
        }

        // Choosing a row with a click draws it again (its cells show it chosen); the current grid
        // also draws again the row the keyboard was on (Alpha, the first)
        play.click(control(play, "Bravo", 0, "*"));
        expectRedrawn(play, newGrid
                ? "Bravo"
                : "Alpha, Bravo");

        // Text typed in Delta's field, as a poll changes Delta itself
        play.click(control(play, "Delta", PATH_COLUMN, "input"));
        play.type(control(play, "Delta", PATH_COLUMN, "input"), "/typed");
        play.run("poll, Delta changed", () -> poll.accept("Delta"));
        expectRedrawn(play, newGrid
                ? "Delta"
                : "Alpha, Bravo, Charlie, Delta, Echo");
    }

    private static void expectRedrawn(final Play play, final String names) {
        play.waitFor(() -> play.expect(play.getByTestId(REDRAWN)).toHaveTextContent(REDRAWN_PREFIX + names));
    }

    private static Query control(final Play play, final String name, final int column, final String selector) {
        final Query row = play.within(play.getByTestId(GRID)).getByText(name).closest("tr, [role='row']");
        return play.within(row).querySelector("td:nth-child(" + (column + 1) + ") " + selector + ", [data-col='"
                                              + column + "'] " + selector);
    }

    // --------------------------------------------------------------------------------
    // Rendering

    // A grid of rows with a tick box and a text field, the note of the rows drawn again, and the
    // poll the plays run
    private static Widget render(final boolean newGrid) {
        final List<Row> rows = new ArrayList<>();
        for (final String name : NAMES) {
            rows.add(new Row(name, true, "/" + name.toLowerCase()));
        }
        final Label redrawn = new Label(REDRAWN_PREFIX);
        redrawn.getElement().setAttribute("data-testid", REDRAWN);
        final SimplePanel frame = new SimplePanel();
        frame.getElement().setAttribute("data-testid", GRID);
        frame.getElement().getStyle().setProperty("height", "220px");
        frame.getElement().getStyle().setProperty("width", "420px");

        // Notes the rows drawn again since the last time, once the grid has drawn them, and marks
        // the cells again
        final Runnable measure = () -> Scheduler.get().scheduleDeferred(() -> {
            final String names = redrawnRows(frame.getElement());
            redrawn.setText(REDRAWN_PREFIX + (names.isEmpty()
                    ? "none"
                    : names));
            markCells(frame.getElement());
        });

        final Grid grid = newGrid
                ? new NewGrid()
                : new CurrentGrid();
        // A refresh: the rows again, each a new object
        final Runnable refresh = () -> {
            final List<Row> copies = new ArrayList<>();
            for (final Row row : rows) {
                copies.add(row.copy());
            }
            grid.setRows(copies);
            measure.run();
        };
        grid.addColumns(
                (index, row, value) -> {
                    // Saved, then the screen refreshes
                    rows.set(rows.indexOf(row), new Row(row.name, value.toBoolean(), row.path));
                    refresh.run();
                },
                (index, row, value) -> {
                    rows.set(rows.indexOf(row), new Row(row.name, row.enabled, value));
                    refresh.run();
                });
        grid.onSelect(measure);
        frame.setWidget(grid.asWidget());

        poll = changed -> {
            if (changed != null) {
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i).name.equals(changed)) {
                        rows.set(i, new Row(changed, rows.get(i).enabled, rows.get(i).path + "/polled"));
                    }
                }
            }
            refresh.run();
        };
        // The first rows: shown, once drawn, as the starting point (nothing drawn again yet)
        final List<Row> first = new ArrayList<>(rows);
        grid.setRows(first);
        Scheduler.get().scheduleFixedDelay(() -> {
            if (!frame.isAttached()) {
                return true;
            }
            if (rowCount(frame.getElement()) < NAMES.size()) {
                return true;
            }
            markCells(frame.getElement());
            redrawn.setText(REDRAWN_PREFIX + "none");
            return false;
        }, 20);
        return StoryPanels.column(12, frame, redrawn);
    }

    // --------------------------------------------------------------------------------
    // Native

    private static native int rowCount(Element container) /*-{
        return container.querySelectorAll('tr[__gwt_row], [role="row"][data-row]').length;
    }-*/;

    // Marks each cell's content, so that content drawn again has no mark
    private static native void markCells(Element container) /*-{
        var cells = container.querySelectorAll('tr[__gwt_row] > td, [role="row"][data-row] > [data-col]');
        for (var i = 0; i < cells.length; i++) {
            var content = cells[i].firstElementChild;
            if (content) {
                content.__drawn = true;
            }
        }
    }-*/;

    // The names of the rows with a cell whose content has no mark (drawn again since marked)
    private static native String redrawnRows(Element container) /*-{
        var rows = container.querySelectorAll('tr[__gwt_row], [role="row"][data-row]');
        var names = [];
        for (var i = 0; i < rows.length; i++) {
            var cells = rows[i].querySelectorAll(':scope > td, :scope > [data-col]');
            var drawn = false;
            for (var c = 0; c < cells.length; c++) {
                var content = cells[c].firstElementChild;
                if (content && !content.__drawn) {
                    drawn = true;
                }
            }
            if (drawn && cells.length > 0) {
                names.push(cells[0].textContent.trim());
            }
        }
        return names.join(', ');
    }-*/;

    private static Column<Row, TickBoxState> enabledColumn(final Updater<TickBoxState> updater) {
        final Column<Row, TickBoxState> column = new Column<Row, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.DefaultAppearance(), false, false, true)) {
            @Override
            public TickBoxState getValue(final Row row) {
                return TickBoxState.fromBoolean(row.enabled);
            }
        };
        column.setFieldUpdater(updater::update);
        return column;
    }

    private static Column<Row, String> pathColumn(final Updater<String> updater) {
        final Column<Row, String> column = new Column<Row, String>(
                new TextFieldCell(index -> "Path for " + NAMES.get(index))) {
            @Override
            public String getValue(final Row row) {
                return row.path;
            }
        };
        column.setFieldUpdater(updater::update);
        return column;
    }

    // --------------------------------------------------------------------------------

    // A row: compares equal to a copy with the same values, as Stroom's shared classes do
    private static final class Row {

        private final String name;
        private final boolean enabled;
        private final String path;

        private Row(final String name, final boolean enabled, final String path) {
            this.name = name;
            this.enabled = enabled;
            this.path = path;
        }

        private Row copy() {
            return new Row(name, enabled, path);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof final Row row)) {
                return false;
            }
            return enabled == row.enabled && name.equals(row.name) && path.equals(row.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, enabled, path);
        }
    }

    // The grid under test, the current or the new
    private interface Grid {

        void addColumns(Updater<TickBoxState> enabledUpdater, Updater<String> pathUpdater);

        void setRows(List<Row> rows);

        void onSelect(Runnable runnable);

        Widget asWidget();
    }

    @FunctionalInterface
    private interface Updater<C> {

        void update(int index, Row row, C value);
    }

    private static final class CurrentGrid implements Grid {

        private final MyDataGrid<Row> grid = new MyDataGrid<>(new SimpleEventBus());

        @Override
        public void addColumns(final Updater<TickBoxState> enabledUpdater, final Updater<String> pathUpdater) {
            grid.addResizableColumn(DataGridUtil.textColumnBuilder((Row row) -> row.name).build(), "Name", 100);
            grid.addColumn(enabledColumn(enabledUpdater), "Enabled", 70);
            grid.addColumn(pathColumn(pathUpdater), "Path", 200);
        }

        @Override
        public void setRows(final List<Row> rows) {
            grid.setRowData(0, rows);
            grid.setRowCount(rows.size());
        }

        @Override
        public void onSelect(final Runnable runnable) {
            grid.addDefaultSelectionModel(false).addSelectionHandler(event -> runnable.run());
        }

        @Override
        public Widget asWidget() {
            grid.setWidth("100%");
            grid.setHeight("100%");
            return grid;
        }
    }

    private static final class NewGrid implements Grid {

        private final DataGridWidget<Row> grid = new DataGridWidget<>(new SimpleEventBus());

        @Override
        public void addColumns(final Updater<TickBoxState> enabledUpdater, final Updater<String> pathUpdater) {
            grid.addResizableColumn(DataGridUtil.textColumnBuilder((Row row) -> row.name).build(), "Name", 100);
            grid.addColumn(enabledColumn(enabledUpdater), "Enabled", 70);
            grid.addColumn(pathColumn(pathUpdater), "Path", 200);
        }

        @Override
        public void setRows(final List<Row> rows) {
            grid.setRowData(0, rows);
            grid.setRowCount(rows.size());
        }

        @Override
        public void onSelect(final Runnable runnable) {
            grid.addDefaultSelectionModel(false).addSelectionHandler(event -> runnable.run());
        }

        @Override
        public Widget asWidget() {
            grid.setWidth("100%");
            grid.setHeight("100%");
            return grid;
        }
    }
}
