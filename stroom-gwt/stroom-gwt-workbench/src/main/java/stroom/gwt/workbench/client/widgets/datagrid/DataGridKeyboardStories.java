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
import stroom.cell.tickbox.client.TickBoxState;
import stroom.cell.valuespinner.client.EditableInteger;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Button;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.selection.client.SelectionBoxCell;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.selection.client.SimpleSelectionListModel;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// The keyboard of a [DataGrid] (its `DataGridKeyboard`): the row focus, Right
/// into the cells, the controls in them (a tick box and a value spinner, as `ProcessorList`
/// has, a text field and a selection box, as `FloorMapSettings`'s value schema has),
/// editing with Enter or F2, and Tab from one control to the next while editing.
///
/// The plays press the keys as `userEvent.keyboard` does; the real input checks
/// (`test-runner/lib/real-input/data-grid.mjs`) press them for real, for what only the browser does
/// (scrolling on Space and the arrows, Tab moving the focus).
public final class DataGridKeyboardStories {

    private static final String ON_CHANGE = "onChange";
    private static final String GRID = "grid";
    private static final String SELECTED = "Selected: ";
    private static final List<String> NAMES = List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo");
    // The columns, in order
    private static final int NAME_COLUMN = 0;
    private static final int ENABLED_COLUMN = 1;
    private static final int PRIORITY_COLUMN = 2;
    private static final int PATH_COLUMN = 3;
    private static final int ROLE_COLUMN = 4;

    private DataGridKeyboardStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/DataGridKeyboard", DataGridKeyboardStories.class)
                .layout(StoryLayout.PADDED)
                // A grid that chooses several rows: the row keys and the selection
                .story("Rows", context -> render(context, true))
                .withPlay(DataGridKeyboardStories::playRows)
                // A grid that chooses one row: the selection follows the focus
                .story("SingleSelection", context -> render(context, false))
                .withPlay(DataGridKeyboardStories::playSingleSelection)
                // Into the cells, and the controls in them
                .story("Cells", context -> render(context, true))
                .withPlay(DataGridKeyboardStories::playCells)
                // Editing a text field or spinner, and Tab from one control to the next
                .story("Editing", context -> render(context, true))
                .withPlay(DataGridKeyboardStories::playEditing)
                // Up from the first row into the column headers, and back
                .story("Headers", context -> render(context, true))
                .withPlay(DataGridKeyboardStories::playHeaders)
                // A selection box given the focus, and then opened, is left as it is: the grid
                // doesn't draw its row again for the focus coming or going
                .story("SelectionBoxStaysPut", context -> render(context, false))
                .withPlay(DataGridKeyboardStories::playSelectionBoxStaysPut)
                // On macOS, Cmd does what Ctrl does (design deviation D3)
                .story("CmdActsAsCtrlOnMac", context -> render(context, true))
                .withPlay(DataGridKeyboardStories::playCmdActsAsCtrlOnMac);
    }

    // --------------------------------------------------------------------------------
    // Plays

    private static void playRows(final Play play) {
        enterGrid(play);

        // Down moves to the next row, choosing it
        play.keyboard("{ArrowDown}");
        expectFocus(play, row(play, "Bravo"));
        expectSelected(play, "Bravo");
        // Shift+Down extends the choice
        play.keyboard("{Shift>}{ArrowDown}{/Shift}");
        expectFocus(play, row(play, "Charlie"));
        expectSelected(play, "Bravo, Charlie");
        // Ctrl+Down moves without choosing, and Space adds the row
        play.keyboard("{Control>}{ArrowDown}{/Control}");
        expectFocus(play, row(play, "Delta"));
        expectSelected(play, "Bravo, Charlie");
        play.keyboard(" ");
        expectSelected(play, "Bravo, Charlie, Delta");
        // Space again takes it back out
        play.keyboard(" ");
        expectSelected(play, "Bravo, Charlie");
        // End and Home go to the last and first rows
        play.keyboard("{End}");
        expectFocus(play, row(play, "Echo"));
        expectSelected(play, "Echo");
        play.keyboard("{Home}");
        expectFocus(play, row(play, "Alpha"));
        expectSelected(play, "Alpha");
        // Up on the first row stays there
        play.keyboard("{ArrowUp}");
        expectFocus(play, row(play, "Alpha"));

        // The grid is one tab stop: the focused row
        play.expect(play.querySelectorAll("[data-testid='" + GRID + "'] [tabindex='0']")).toHaveLength(1);
        play.expect(row(play, "Alpha")).toHaveAttribute("tabindex", "0");
    }

    private static void playSingleSelection(final Play play) {
        enterGrid(play);
        play.keyboard("{ArrowDown}");
        expectSelected(play, "Bravo");
        // Shift and Ctrl don't add in a grid that chooses one row
        play.keyboard("{Shift>}{ArrowDown}{/Shift}");
        expectSelected(play, "Charlie");
        play.keyboard("{Control>}{ArrowDown}{/Control}");
        expectFocus(play, row(play, "Delta"));
        expectSelected(play, "Delta");
    }

    private static void playCells(final Play play) {
        enterGrid(play);
        play.keyboard("{ArrowDown}");
        expectSelected(play, "Bravo");

        // Right moves into the row's first cell, then along it; the selection stays
        play.keyboard("{ArrowRight}");
        expectFocus(play, cell(play, "Bravo", NAME_COLUMN));
        // A tick box takes the focus itself, and Space ticks it
        play.keyboard("{ArrowRight}");
        expectFocus(play, control(play, "Bravo", ENABLED_COLUMN, "[role='checkbox']"));
        play.keyboard(" ");
        play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith("Bravo enabled=false"));
        expectSelected(play, "Bravo");
        // A spinner's cell takes the focus, not its field
        play.keyboard("{ArrowRight}");
        expectFocus(play, cell(play, "Bravo", PRIORITY_COLUMN));
        play.keyboard("{ArrowRight}{ArrowRight}");
        // A selection box takes the focus itself
        expectFocus(play, control(play, "Bravo", ROLE_COLUMN, "[role='combobox']"));
        // Right on the last cell stays there
        play.keyboard("{ArrowRight}");
        expectFocus(play, control(play, "Bravo", ROLE_COLUMN, "[role='combobox']"));
        // Down moves to the same column of the next row, without choosing it, and without opening
        // the selection box's list
        play.keyboard("{ArrowDown}");
        expectFocus(play, control(play, "Charlie", ROLE_COLUMN, "[role='combobox']"));
        expectSelected(play, "Bravo");
        play.expect(play.querySelectorAll("[role='listbox']")).toHaveLength(0);
        // Home and End go to the first and last cells
        play.keyboard("{Home}");
        expectFocus(play, cell(play, "Charlie", NAME_COLUMN));
        play.keyboard("{End}");
        expectFocus(play, control(play, "Charlie", ROLE_COLUMN, "[role='combobox']"));
        // Left from the first cell goes back to the row
        play.keyboard("{Home}{ArrowLeft}");
        expectFocus(play, row(play, "Charlie"));
        // and Down from the row chooses again
        play.keyboard("{ArrowDown}");
        expectSelected(play, "Delta");
    }

    private static void playEditing(final Play play) {
        enterGrid(play);
        play.keyboard("{ArrowRight}{ArrowRight}{ArrowRight}");
        expectFocus(play, cell(play, "Alpha", PRIORITY_COLUMN));

        // Enter edits the spinner, whose Up then steps it; Enter commits and goes back to the cell
        play.keyboard("{Enter}");
        expectFocus(play, control(play, "Alpha", PRIORITY_COLUMN, "input"));
        play.keyboard("{ArrowUp}");
        play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith("Alpha priority=11"));
        play.keyboard("{Enter}");
        expectFocus(play, cell(play, "Alpha", PRIORITY_COLUMN));

        // F2 edits the text field; Escape puts its value back and goes back to the cell
        play.keyboard("{ArrowRight}{F2}");
        expectFocus(play, control(play, "Alpha", PATH_COLUMN, "input"));
        play.keyboard("{Backspace}");
        play.expect(control(play, "Alpha", PATH_COLUMN, "input")).toHaveValue("");
        play.keyboard("{Escape}");
        expectFocus(play, cell(play, "Alpha", PATH_COLUMN));
        play.expect(control(play, "Alpha", PATH_COLUMN, "input")).toHaveValue("/alpha");

        // Tab while editing goes on to the next control (the selection box), and from the last in a
        // row to the next row's first (its tick box); Shift+Tab goes back
        play.keyboard("{Enter}");
        play.tab();
        expectFocus(play, control(play, "Alpha", ROLE_COLUMN, "[role='combobox']"));
        play.tab();
        expectFocus(play, control(play, "Bravo", ENABLED_COLUMN, "[role='checkbox']"));
        play.tab(true);
        expectFocus(play, control(play, "Alpha", ROLE_COLUMN, "[role='combobox']"));

        // A click in a field edits it at once
        play.click(control(play, "Charlie", PATH_COLUMN, "input"));
        expectFocus(play, control(play, "Charlie", PATH_COLUMN, "input"));
        play.keyboard("{Escape}");
        expectFocus(play, cell(play, "Charlie", PATH_COLUMN));
    }

    private static void playHeaders(final Play play) {
        enterGrid(play);
        play.keyboard("{ArrowRight}");
        expectFocus(play, cell(play, "Alpha", NAME_COLUMN));
        // Up from the first row's cell goes to its column's header
        play.keyboard("{ArrowUp}");
        expectFocus(play, header(play, NAME_COLUMN));
        // Enter on a sortable header sorts the column, as a click does
        play.expect(header(play, NAME_COLUMN)).toHaveAttribute("aria-sort", "none");
        play.keyboard("{Enter}");
        play.waitFor(() -> play.expect(header(play, NAME_COLUMN)).toHaveAttribute("aria-sort", "ascending"));
        expectFocus(play, header(play, NAME_COLUMN));
        // Right moves along the headers, and Down goes to the first row's cell in the column (its
        // tick box)
        play.keyboard("{ArrowRight}");
        expectFocus(play, header(play, ENABLED_COLUMN));
        play.keyboard("{ArrowDown}");
        expectFocus(play, control(play, "Alpha", ENABLED_COLUMN, "[role='checkbox']"));
        // Up on a row (not a cell) doesn't go to the headers
        play.keyboard("{Home}{ArrowLeft}{ArrowUp}");
        expectFocus(play, row(play, "Alpha"));
    }

    // A column's header, by column (its name changes as it is sorted, taking the sort's position)
    private static void playSelectionBoxStaysPut(final Play play) {
        play.waitFor(() -> play.expect(row(play, "Bravo")).toBeInTheDocument());
        final Query box = control(play, "Bravo", ROLE_COLUMN, "[role='combobox']");
        final Element[] focused = new Element[1];
        // As a click on it would, which the grid follows (its keyboard row becomes Bravo's)
        play.run("focus Bravo's box", () -> {
            focused[0] = box.element().get();
            focused[0].focus();
        });
        play.waitFor(() -> play.expect(row(play, "Bravo")).toHaveClass("data-grid__row--keyboard"));
        play.expect("the box focused is still the row's", () -> isConnected(focused[0])).toBe(true);
        play.expect(box).toHaveFocus();
        // Enter opens its list, whose filter takes the focus out of the grid; the box says it is open
        play.keyboard("{Enter}");
        play.waitFor(() -> play.expect(play.screen().getByRole("listbox")).toBeVisible());
        play.waitFor(() -> play.expect(row(play, "Bravo")).not().toHaveClass("data-grid__row--keyboard"));
        play.expect("the box opened is still the row's", () -> isConnected(focused[0])).toBe(true);
        play.expect(box).toHaveAttribute("aria-expanded", "true");
        play.keyboard("{Escape}");
        play.waitFor(() -> play.expect(play.screen().queryByRole("listbox")).toBeNull());
        expectFocus(play, box);
    }

    private static void playCmdActsAsCtrlOnMac(final Play play) {
        play.run("pretend to be on macOS", DataGridKeyboardStories::pretendToBeOnMac);
        enterGrid(play);
        play.keyboard("{ArrowDown}");
        expectSelected(play, "Bravo");
        // Cmd+Down moves without choosing, as Ctrl+Down does
        play.keyboard("{Meta>}{ArrowDown}{/Meta}");
        expectFocus(play, row(play, "Charlie"));
        expectSelected(play, "Bravo");
        play.keyboard(" ");
        expectSelected(play, "Bravo, Charlie");
        // Cmd+click adds a row, as Ctrl+click does
        play.fireEvent().mouseDown(play.within(row(play, "Echo")).getByText("Echo"),
                EventInit.create().metaKey().button(0));
        expectSelected(play, "Bravo, Charlie, Echo");
    }

    // The grid asks the browser which system it is on
    private static native void pretendToBeOnMac() /*-{
        Object.defineProperty($wnd.navigator, "platform", {get: function () { return "MacIntel"; }});
        Object.defineProperty($wnd.navigator, "userAgentData", {get: function () { return undefined; }});
    }-*/;

    private static native boolean isConnected(Element element) /*-{
        return !!element && element.isConnected;
    }-*/;

    private static Query header(final Play play, final int column) {
        return play.within(play.getByTestId(GRID)).querySelector("[role='columnheader'][data-hcol='" + column + "']");
    }

    // Tabs from the button before the grid into the grid, whose tab stop is its first row
    private static void enterGrid(final Play play) {
        play.waitFor(() -> play.expect(row(play, "Alpha")).toBeInTheDocument());
        play.click(play.getByRole("button", "Before"));
        play.tab();
        expectFocus(play, row(play, "Alpha"));
    }

    // Waits for the focus, which a redraw of the rows (as the selection changes) gives back a
    // moment later
    private static void expectFocus(final Play play, final Query target) {
        play.waitFor(() -> play.expect(target).toHaveFocus());
    }

    private static void expectSelected(final Play play, final String names) {
        play.waitFor(() -> play.expect(play.getByTestId("selected")).toHaveTextContent(SELECTED + names));
    }

    private static Query row(final Play play, final String name) {
        return play.within(play.getByTestId(GRID)).getByText(name).closest("[role='row']");
    }

    private static Query cell(final Play play, final String name, final int column) {
        return play.within(row(play, name)).querySelector(nthCell(column));
    }

    private static Query control(final Play play, final String name, final int column, final String selector) {
        return play.within(row(play, name)).querySelector(nthCell(column) + " " + selector);
    }

    private static String nthCell(final int column) {
        return "[data-col='" + column + "']";
    }

    // --------------------------------------------------------------------------------
    // Rendering

    // A button to tab from, the grid, and what is selected
    private static Widget render(final StoryContext context, final boolean multiSelect) {
        final Spy onChange = context.fn(ON_CHANGE);
        final List<Row> rows = new ArrayList<>();
        for (final String name : NAMES) {
            rows.add(new Row(name));
        }

        final DataGrid<Row> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.setAccessibleName("Processors");

        // Sortable, so its header sorts on Enter
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((Row row) -> row.name)
                        .withSorting("Name")
                        .build(),
                "Name",
                120);
        final Column<Row, TickBoxState> enabled = new Column<Row, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.DefaultAppearance(), false, false, true)) {
            @Override
            public TickBoxState getValue(final Row row) {
                return TickBoxState.fromBoolean(row.enabled);
            }
        };
        enabled.setFieldUpdater((index, row, value) -> {
            row.enabled = value.toBoolean();
            onChange.call(row.name + " enabled=" + row.enabled);
        });
        grid.addColumn(enabled, "Enabled", 70);
        grid.addColumn(DataGridUtil.valueSpinnerColumnBuilder("Priority",
                                (Row row) -> new EditableInteger(row.priority),
                                1,
                                100)
                        .withFieldUpdater((index, row, value) -> {
                            row.priority = value.intValue();
                            onChange.call(row.name + " priority=" + row.priority);
                        })
                        .build(),
                DataGridUtil.headingBuilder("Priority").build(),
                ColumnSizeConstants.VALUE_SPINNER_COL);
        final Column<Row, String> path = new Column<Row, String>(
                new TextFieldCell(index -> "Path for " + NAMES.get(index))) {
            @Override
            public String getValue(final Row row) {
                return row.path;
            }
        };
        path.setFieldUpdater((index, row, value) -> {
            row.path = value;
            onChange.call(row.name + " path=" + value);
        });
        grid.addColumn(path, "Path", 130);
        final SimpleSelectionListModel<String> roleModel = new SimpleSelectionListModel<>();
        roleModel.addItems(List.of("Name", "Status", "Custom"));
        final Column<Row, String> role = new Column<Row, String>(
                new SelectionBoxCell<String, SimpleSelectionItemWrapper<String>>(
                        roleModel, index -> "Role for " + NAMES.get(index))) {
            @Override
            public String getValue(final Row row) {
                return row.role;
            }
        };
        role.setFieldUpdater((index, row, value) -> {
            row.role = value;
            onChange.call(row.name + " role=" + value);
            grid.redraw();
        });
        grid.addColumn(role, "Role", 110);

        final Label selected = new Label(SELECTED);
        selected.getElement().setAttribute("data-testid", "selected");
        final MultiSelectionModelImpl<Row> selectionModel = grid.addDefaultSelectionModel(multiSelect);
        selectionModel.addSelectionHandler(event -> {
            final List<String> names = new ArrayList<>();
            for (final Row row : rows) {
                if (selectionModel.isSelected(row)) {
                    names.add(row.name);
                }
            }
            selected.setText(SELECTED + String.join(", ", names));
        });
        grid.setRowData(rows);
        grid.setRowCount(rows.size());

        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().setAttribute("data-testid", GRID);
        panel.getElement().getStyle().setProperty("height", "240px");
        panel.getElement().getStyle().setProperty("width", "580px");

        final Button before = new Button();
        before.setText("Before");
        before.getElement().setAttribute("data-testid", "before");
        final Button after = new Button();
        after.setText("After");
        after.getElement().setAttribute("data-testid", "after");
        return StoryPanels.column(12, StoryPanels.row(8, before), panel, StoryPanels.row(8, after), selected);
    }

    // --------------------------------------------------------------------------------

    // A grid row: a processor's settings
    private static final class Row {

        private final String name;
        private boolean enabled = true;
        private int priority = 10;
        private String path;
        private String role = "Name";

        private Row(final String name) {
            this.name = name;
            this.path = "/" + name.toLowerCase();
        }
    }
}
