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

import stroom.cell.client.ActionMenuCell;
import stroom.cell.client.CommandLink;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.textfield.client.TextFieldCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.cell.valuespinner.client.EditableInteger;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRows.Person;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;

import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Grids with Stroom's interactive cells, for the `Widgets/Data Grid/DataGrid` stories.
///
/// They check that [DataGrid] works with Stroom's existing GWT cells unchanged: the cells get
/// their events, their field updaters fire, a click on a control in a cell does not select the row,
/// a cell that redraws itself is put right when the row is sent again, and a cell being edited keeps
/// the focus while its row is redrawn. The columns are built as Stroom's controllers build them.
final class InteractiveCellGrids {

    static final String ON_CHANGE = "onChange";
    static final String ON_OPEN = "onOpen";
    static final String ON_ACTION = "onAction";

    static final String TICKED = "Ticked";
    static final String HALF_TICKED = "Half-Ticked";
    static final String NOT_TICKED = "Not Ticked";
    static final String HEADER_TICK_BOX = "[role=\"columnheader\"] .tick-box";

    // The row a tick can't be changed for: its change is refused and the rows are sent again
    static final String LOCKED = "David Brown";

    // Often enough that a poll comes between every step of the spinner's play (clearing, typing,
    // Enter), as one did now and then under load when it was 250ms: the row drawn again lost the
    // cleared field's text, and the typed value wasn't committed
    private static final int SPINNER_POLL_MILLIS = 10;
    // Stops the value spinner grid's polling (valueSpinnerGrid sets it)
    private static Runnable stopPolling = () -> {
    };

    private InteractiveCellGrids() {
        // Static utility
    }

    /// The first four people.
    static List<Person> fourPeople() {
        return new ArrayList<>(DataGridRows.people().subList(0, 4));
    }

    // --------------------------------------------------------------------------------
    // Tick box column with a select-all header, as ColumnSelection builds them

    static Widget tickBoxGrid(final StoryContext context) {
        final Spy onChange = context.fn(ON_CHANGE);
        final List<Person> rows = fourPeople();
        final Set<String> ticked = new LinkedHashSet<>();
        ticked.add("Bob Smith");

        final DataGrid<Person> grid = grid();
        grid.addDefaultSelectionModel(true);
        final Column<Person, TickBoxState> column = new Column<Person, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.DefaultAppearance(), false, false, true)) {
            @Override
            public TickBoxState getValue(final Person person) {
                return TickBoxState.fromBoolean(ticked.contains(person.getName()));
            }
        };
        final Header<TickBoxState> header = new Header<TickBoxState>(TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (ticked.size() == rows.size()) {
                    return TickBoxState.TICK;
                } else if (!ticked.isEmpty()) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        header.setUpdater(value -> {
            ticked.clear();
            if (TickBoxState.TICK.equals(value)) {
                for (final Person person : rows) {
                    ticked.add(person.getName());
                }
            }
            onChange.call(join(rows, ticked));
            grid.redraw();
        });
        column.setFieldUpdater((index, person, value) -> {
            if (LOCKED.equals(person.getName())) {
                // Refused: send the rows again, as a controller refreshing from the server would
                grid.setRowData(0, rows);
                return;
            }
            if (value.toBoolean()) {
                ticked.add(person.getName());
            } else {
                ticked.remove(person.getName());
            }
            onChange.call(join(rows, ticked));
            grid.redrawHeaders();
        });
        grid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        grid.setRowData(rows);
        return sized(grid);
    }

    /// The tick box in the row with the name.
    static Query rowTickBox(final Play play, final String name) {
        return play.within(rowOf(play, name)).querySelector(".tick-box");
    }

    // --------------------------------------------------------------------------------
    // Value spinner column, as JobNodeList's Max Tasks, in a grid that polls

    static Widget valueSpinnerGrid(final StoryContext context) {
        final Spy onChange = context.fn(ON_CHANGE);
        final List<Person> rows = fourPeople();
        final Map<String, Integer> limits = new HashMap<>();
        for (final Person person : rows) {
            limits.put(person.getName(), person.getAge());
        }
        final int[] polls = {0};

        final DataGrid<Person> grid = grid();
        grid.addDefaultSelectionModel(false);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        grid.addColumn(DataGridUtil.valueSpinnerColumnBuilder("Max Tasks",
                                (Person person) -> new EditableInteger(limits.get(person.getName())), 1L, 9999L)
                        .withFieldUpdater((index, person, value) -> {
                            limits.put(person.getName(), value.intValue());
                            onChange.call(person.getName(), value.intValue());
                        })
                        .build(),
                DataGridUtil.headingBuilder("Max Tasks").build(),
                80);
        // Changes on every poll, so each poll really rewrites every row
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((Person person) -> polls[0] < 0
                        ? "Stopped"
                        : "Poll " + polls[0]).build(),
                "Polled", 100);
        grid.setRowData(rows);

        // As NodeStatusList and JobNodeList poll and redraw
        final Timer poll = new Timer() {
            @Override
            public void run() {
                polls[0]++;
                grid.setRowData(0, rows);
            }
        };
        grid.addAttachHandler(event -> {
            if (event.isAttached()) {
                poll.scheduleRepeating(SPINNER_POLL_MILLIS);
            } else {
                poll.cancel();
            }
        });
        // For the play's end: the grid stops polling and shows the same thing every run, so that
        // its screenshot doesn't depend on how many polls the play took
        stopPolling = () -> {
            poll.cancel();
            polls[0] = -1;
            grid.setRowData(0, rows);
        };
        return sized(grid);
    }

    /// Stops the value spinner grid's polling, its Polled column then saying so.
    static void stopPolling() {
        stopPolling.run();
    }

    // --------------------------------------------------------------------------------
    // Text field column, as FloorMapSettings builds them, in a grid showing Stroom's menus

    static Widget textFieldGrid(final StoryContext context) {
        final Spy onChange = context.fn(ON_CHANGE);
        // The grid asks for its context menu with a MenuRequest
        StoryPopups.create(context).withPageRequests();
        final DataGrid<Person> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.setTableName("People");
        final List<Person> rows = fourPeople();
        final Map<String, String> nicknames = new HashMap<>();
        for (final Person person : rows) {
            nicknames.put(person.getName(), person.getName().split(" ")[0]);
        }
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        final Column<Person, String> nickname = new Column<Person, String>(
                new TextFieldCell(index -> "Nickname for " + rows.get(index).getName())) {
            @Override
            public String getValue(final Person person) {
                return nicknames.get(person.getName());
            }
        };
        nickname.setFieldUpdater((index, person, value) -> {
            nicknames.put(person.getName(), value);
            onChange.call(person.getName(), value);
        });
        grid.addColumn(nickname, "Nickname", 160);
        grid.setRowData(rows);
        return sized(grid);
    }

    // --------------------------------------------------------------------------------
    // Expander column, as the client-built trees (e.g. DataRetentionImpactController) build them

    static Widget expanderGrid(final StoryContext context) {
        final List<TreeRow> tree = Arrays.asList(
                new TreeRow("Engineering", 0, false),
                new TreeRow("Alice Johnson", 1, true),
                new TreeRow("David Brown", 1, true),
                new TreeRow("Design", 0, false),
                new TreeRow("Bob Smith", 1, true));
        final Set<String> expanded = new HashSet<>();

        final DataGrid<TreeRow> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addDefaultSelectionModel(false);
        final Column<TreeRow, Expander> column = new Column<TreeRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TreeRow row) {
                return new Expander(row.depth, expanded.contains(row.name), row.leaf);
            }
        };
        column.setFieldUpdater((index, row, value) -> {
            if (value.isExpanded()) {
                expanded.remove(row.name);
            } else {
                expanded.add(row.name);
            }
            grid.setRowData(visibleRows(tree, expanded));
        });
        grid.addColumn(column, "<br/>", ExpanderCell.getColumnWidth(1));
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((TreeRow row) -> row.name).build(), "Name", 200);
        grid.setRowData(visibleRows(tree, expanded));
        return sized(grid);
    }

    private static List<TreeRow> visibleRows(final List<TreeRow> tree, final Set<String> expanded) {
        final List<TreeRow> visible = new ArrayList<>();
        boolean parentExpanded = false;
        for (final TreeRow row : tree) {
            if (row.depth == 0) {
                visible.add(row);
                parentExpanded = expanded.contains(row.name);
            } else if (parentExpanded) {
                visible.add(row);
            }
        }
        return visible;
    }

    // --------------------------------------------------------------------------------
    // Command link column, as JobNodeColumns' schedule column

    static Widget commandLinkGrid(final StoryContext context) {
        final Spy onOpen = context.fn(ON_OPEN);
        final DataGrid<Person> grid = grid();
        grid.addDefaultSelectionModel(false);
        grid.addResizableColumn(DataGridUtil.commandLinkColumnBuilder((Person person) -> new CommandLink(
                                person.getName(),
                                "Open " + person.getName(),
                                () -> onOpen.call(person.getName())))
                        .build(),
                "Name", 220);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getRole).build(), "Role", 120);
        grid.setRowData(fourPeople());
        return sized(grid);
    }

    // --------------------------------------------------------------------------------
    // Action menu column, as the rule list controllers' row menus

    static Widget actionMenuGrid(final StoryContext context) {
        final Spy onAction = context.fn(ON_ACTION);
        // The cell asks for its menu with a MenuRequest, which the relay shows
        StoryPopups.create(context).withPageRequests();
        final DataGrid<Person> grid = grid();
        grid.addDefaultSelectionModel(false);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        final ActionMenuCell<Person> cell = new ActionMenuCell<>(person -> {
            final List<Item> items = new ArrayList<>();
            items.add(new IconMenuItem.Builder()
                    .priority(1)
                    .icon(SvgImage.COPY)
                    .text("Copy Rule")
                    .command(() -> onAction.call("copy", person.getName()))
                    .build());
            return items;
        });
        grid.addColumn(new Column<Person, Person>(cell) {
            @Override
            public Person getValue(final Person person) {
                return person;
            }
        }, "", ColumnSizeConstants.ICON_COL);
        grid.setRowData(fourPeople());
        return sized(grid);
    }

    // --------------------------------------------------------------------------------

    /// The grid row containing the text.
    static Query rowOf(final Play play, final String text) {
        return play.getByText(text).closest(".data-grid__row");
    }

    private static DataGrid<Person> grid() {
        final DataGrid<Person> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        return grid;
    }

    private static Widget sized(final Widget grid) {
        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().getStyle().setProperty("height", "220px");
        panel.getElement().getStyle().setProperty("width", "500px");
        return panel;
    }

    private static String join(final List<Person> rows, final Set<String> names) {
        final StringBuilder sb = new StringBuilder();
        for (final Person person : rows) {
            if (names.contains(person.getName())) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(person.getName());
            }
        }
        return sb.toString();
    }

    // --------------------------------------------------------------------------------

    /// A row of the expander tree.
    static final class TreeRow {

        private final String name;
        private final int depth;
        private final boolean leaf;

        private TreeRow(final String name, final int depth, final boolean leaf) {
            this.name = name;
            this.depth = depth;
            this.leaf = leaf;
        }
    }
}
