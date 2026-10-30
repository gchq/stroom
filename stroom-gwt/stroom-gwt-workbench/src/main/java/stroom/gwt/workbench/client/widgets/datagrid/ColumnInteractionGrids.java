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

import stroom.ai.shared.GeneralTableContext;
import stroom.cell.textfield.client.TextFieldCell;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.AskStroomAiTableRequest;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRows.Person;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.LinkValue;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Button;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.Heading;
import stroom.widget.datagrid.client.HeadingListener;
import stroom.widget.menu.client.IconParentMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.text.client.Label;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// Grids for the `Widgets/Data Grid/DataGrid` stories of the header's pointer interactions
/// and the built-in context menu (design sections 5.3 to 5.5 and 5.8), with their plays.
///
/// The plays drive the grid with pointer events, as a user's mouse would, and check what the grid
/// tells its `HeadingListener`, as the dashboard and query tables rely on.
final class ColumnInteractionGrids {

    static final String ON_ROW_CONTEXT = "onRowContext";
    private static final String ON_COLUMN_RESIZE = "onColumnResize";
    private static final String ON_COLUMN_REORDER = "onColumnReorder";
    private static final String ON_SHOW_MENU = "onShowMenu";
    private static final String ON_MOVE_START = "onMoveStart";
    private static final String ON_MOVE_END = "onMoveEnd";
    private static final String ON_FILTER_CHANGE = "onFilterChange";
    // A link from the context menu, to the grid's own handler or to the app
    private static final String ON_GRID_LINK = "onGridLink";
    private static final String ON_APP_LINK = "onAppLink";

    private static final String HEADER_CELLS = "[role=\"columnheader\"]";
    private static final String ROW = ".data-grid__body [data-row]";
    private static final String HANDLE = ".data-grid__resize-handle";
    private static final String MOVE_GHOST = ".data-grid__move-ghost";
    private static final String MOVE_LINE = ".data-grid__move-line";
    private static final String KEYBOARD_ROW_CLASS = "data-grid__row--keyboard";

    private static final int MENU_ROWS = 500;
    private static final String ASK_AI = "Ask Stroom AI About";

    private ColumnInteractionGrids() {
        // Static utility
    }

    // --------------------------------------------------------------------------------
    // Resize and reorder

    /// The people grid with a heading listener that records what the grid tells it. ID, Name, Role
    /// and Age can be resized and moved; Active is fixed.
    static Widget resizeAndReorder(final StoryContext context) {
        final DataGrid<Person> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        addColumns(grid);
        grid.setRowData(DataGridRows.people());
        grid.setHeadingListener(new RecordingListener(context));
        return sized(grid, "320px", "900px");
    }

    static void resizeAndReorderPlay(final Play play) {
        final Query name = header(play, 1);
        play.waitFor(() -> play.expect(name).toBeInTheDocument());
        expectWidth(play, 1, 200);

        play.step("drag Name's edge 50px right: the column grows as it is dragged", () -> {
            hoverNearRightEdge(play, 1, 200);
            final Query handle = play.querySelector(HANDLE);
            play.waitFor(() -> play.expect(handle).toBeVisible());
            play.fireEvent().pointerDown(handle, EventInit.create().atCentreOf(handle).button(0).buttons(1));
            play.fireEvent().pointerMove(handle, EventInit.create().relativeTo(handle, 55, 5).buttons(1));
            // Live: the column is wider before the release, but the listener has not been told
            expectWidth(play, 1, 250);
            play.expect(play.spy(ON_COLUMN_RESIZE)).not().toHaveBeenCalled();
            play.expect(play.spy(ON_MOVE_START)).toHaveBeenCalledWith(1);
            play.fireEvent().pointerUp(handle, EventInit.create().relativeTo(handle, 5, 5));
            play.waitFor(() -> play.expect(play.spy(ON_COLUMN_RESIZE)).toHaveBeenCalledWith(1, 250));
            play.expect(play.spy(ON_COLUMN_RESIZE)).toHaveBeenCalledTimes(1);
            play.expect(play.spy(ON_MOVE_END)).toHaveBeenCalledTimes(1);
        });

        play.step("drag it far left: the column stops at its 30px minimum", () -> {
            hoverNearRightEdge(play, 1, 250);
            final Query handle = play.querySelector(HANDLE);
            play.waitFor(() -> play.expect(handle).toBeVisible());
            play.fireEvent().pointerDown(handle, EventInit.create().atCentreOf(handle).button(0).buttons(1));
            play.fireEvent().pointerMove(handle, EventInit.create().relativeTo(handle, -400, 5).buttons(1));
            play.fireEvent().pointerUp(handle, EventInit.create().relativeTo(handle, 5, 5));
            expectWidth(play, 1, 30);
            play.waitFor(() -> play.expect(play.spy(ON_COLUMN_RESIZE)).toHaveBeenLastCalledWith(1, 30));
        });

        play.step("double-click the edge: the column fits its widest name", () -> {
            hoverNearRightEdge(play, 1, 30);
            final Query handle = play.querySelector(HANDLE);
            play.waitFor(() -> play.expect(handle).toBeVisible());
            play.fireEvent().dblClick(handle);
            final Value<Double> width = name.width();
            play.waitFor(() -> play.expect("Name's width", width::get).toBeGreaterThan(60));
            play.waitFor(() -> play.expect("Name cells cut short", () -> truncatedCells(play, 1)).toEqual(0));
            play.expect("Name's width", width::get).toBeLessThan(200);
        });

        play.step("drag Role onto the start: it moves first, and the listener is told before", () -> {
            final Query role = header(play, 2);
            play.fireEvent().pointerDown(role, EventInit.create().atCentreOf(role).button(0).buttons(1));
            play.fireEvent().pointerMove(header(play, 0),
                    EventInit.create().relativeTo(header(play, 0), 2, 5).buttons(1));
            play.waitFor(() -> play.expect(play.querySelector(MOVE_GHOST)).toBeVisible());
            play.expect(play.querySelector(MOVE_LINE)).toBeVisible();
            play.fireEvent().pointerUp(header(play, 0), EventInit.create().relativeTo(header(play, 0), 2, 5));
            play.waitFor(() -> play.expect(play.spy(ON_COLUMN_REORDER)).toHaveBeenCalledWith(2, 0));
            final Value<List<String>> names = play.querySelectorAll(HEADER_CELLS).textContents();
            play.waitFor(() -> play.expect("the header order", names::get)
                    .toEqual(list("Role", "ID", "Name", "Age", "Active")));
            play.expect(play.querySelector(MOVE_GHOST)).not().toBeVisible();
            // A move does not also show the header menu
            play.expect(play.spy(ON_SHOW_MENU)).not().toHaveBeenCalled();
        });

        play.step("drag Active, a fixed column: it does not move, and the release shows its menu", () -> {
            final Query active = header(play, 4);
            play.fireEvent().pointerDown(active, EventInit.create().atCentreOf(active).button(0).buttons(1));
            play.fireEvent().pointerMove(header(play, 0),
                    EventInit.create().relativeTo(header(play, 0), 2, 5).buttons(1));
            play.expect(play.querySelector(MOVE_GHOST)).not().toBeVisible();
            play.fireEvent().pointerUp(active, EventInit.create().atCentreOf(active).button(0));
            play.waitFor(() -> play.expect(play.spy(ON_SHOW_MENU)).toHaveBeenCalledWith(4));
            play.expect(play.spy(ON_COLUMN_REORDER)).toHaveBeenCalledTimes(1);
        });

        play.step("click Age: the header menu is shown for it", () -> {
            final Query age = header(play, 3);
            play.fireEvent().pointerDown(age, EventInit.create().atCentreOf(age).button(0).buttons(1));
            play.fireEvent().pointerUp(age, EventInit.create().atCentreOf(age).button(0));
            play.waitFor(() -> play.expect(play.spy(ON_SHOW_MENU)).toHaveBeenLastCalledWith(3));
        });
    }

    /// The same grid with resizing and moving switched off, as the dashboard does outside design
    /// mode.
    static Widget resizeAndReorderDisabled(final StoryContext context) {
        final DataGrid<Person> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        addColumns(grid);
        grid.setRowData(DataGridRows.people());
        grid.setAllowResize(false);
        grid.setAllowMove(false);
        grid.setHeadingListener(new RecordingListener(context));
        return sized(grid, "320px", "900px");
    }

    static void resizeAndReorderDisabledPlay(final Play play) {
        play.waitFor(() -> play.expect(header(play, 1)).toBeInTheDocument());
        hoverNearRightEdge(play, 1, 200);
        play.expect(play.querySelector(HANDLE)).not().toBeVisible();

        final Query role = header(play, 2);
        play.fireEvent().pointerDown(role, EventInit.create().atCentreOf(role).button(0).buttons(1));
        play.fireEvent().pointerMove(header(play, 0), EventInit.create().relativeTo(header(play, 0), 2, 5).buttons(1));
        play.expect(play.querySelector(MOVE_GHOST)).not().toBeVisible();
        play.fireEvent().pointerUp(header(play, 0), EventInit.create().relativeTo(header(play, 0), 2, 5));
        play.expect(play.spy(ON_COLUMN_REORDER)).not().toHaveBeenCalled();
        final Value<List<String>> names = play.querySelectorAll(HEADER_CELLS).textContents();
        play.expect("the header order", names::get).toEqual(list("ID", "Name", "Role", "Age", "Active"));
    }

    // --------------------------------------------------------------------------------
    // Interactive header cells

    /// A header with a live text box (a `TextFieldCell`, as the dashboard's `FilterCell` holds):
    /// what is typed reaches the header's cell, a change fires its updater, and the grid neither
    /// takes the focus nor treats the keys as its own.
    static Widget headerInput(final StoryContext context) {
        final DataGrid<Person> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addDefaultSelectionModel(false);
        final Spy onFilterChange = context.fn(ON_FILTER_CHANGE);
        final Header<String> filterHeader = new Header<String>(new TextFieldCell(index -> "Filter names")) {
            @Override
            public String getValue() {
                return "";
            }
        };
        filterHeader.setUpdater(onFilterChange::call);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), filterHeader, 200);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getRole).build(), "Role", 120);
        grid.setRowData(DataGridRows.people());
        return sized(grid, "320px", "900px");
    }

    static void headerInputPlay(final Play play) {
        final Query input = play.querySelector(HEADER_CELLS + " input");
        play.waitFor(() -> play.expect(input).toBeInTheDocument());
        play.click(input);
        play.expect(input).toHaveFocus();
        // j and k move the grid's keyboard row; typed into the header they are just text
        play.type(input, "jk");
        play.expect(input).toHaveValue("jk");
        play.expect(input).toHaveFocus();
        play.expect(play.querySelectorAll("." + KEYBOARD_ROW_CLASS)).toHaveLength(0);
        play.fireEvent().change(input, "jk");
        play.waitFor(() -> play.expect(play.spy(ON_FILTER_CHANGE)).toHaveBeenCalledWith("jk"));
        play.expect(play.spy(ON_FILTER_CHANGE)).toHaveBeenCalledTimes(1);
    }

    private static void addColumns(final DataGrid<Person> grid) {
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((Person person) -> String.valueOf(person.getId()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("ID").rightAligned().build(),
                60);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getRole).build(), "Role", 120);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((Person person) -> String.valueOf(person.getAge()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Age").rightAligned().build(),
                70);
        // Not resizable or movable
        grid.addColumn(DataGridUtil.textColumnBuilder(Person::getActiveText).centerAligned().build(),
                DataGridUtil.headingBuilder("Active").centerAligned().build(),
                80);
    }

    // --------------------------------------------------------------------------------
    // Context menu

    /// A virtual grid of 500 people in a short frame, so most rows are not in the DOM. The story
    /// shows the menu the grid asks for, and can run its Ask Stroom AI items, showing what they
    /// send: the text comes from the row data, so rows that are not rendered are included.
    static Widget contextMenu(final StoryContext context) {
        // What the grid asks for (its menu, asking Stroom AI) comes to the story's bus, as to Stroom's
        final SimpleEventBus eventBus = new SimpleEventBus();
        context.addCleanUp(MenuRequest.relay(eventBus::fireEvent)::removeHandler);
        context.addCleanUp(AskStroomAiTableRequest.relay(eventBus::fireEvent)::removeHandler);
        final DataGrid<Person> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.setVirtual(true);
        grid.setTableName("People");
        addColumns(grid);
        final List<Person> rows = DataGridRows.generatedPeople(MENU_ROWS);
        grid.setRowData(rows);

        final Label menuLabel = note("Menu: (none)");
        final Label aiLabel = note("AI: (none)");
        final Map<String, Command> aiCommands = new HashMap<>();
        eventBus.addHandler(ShowMenuEvent.getType(), event -> {
            menuLabel.setText("Menu: " + String.join(" | ", texts(event.getItems())));
            aiCommands.clear();
            for (final Item item : event.getItems()) {
                if (item instanceof IconParentMenuItem && ASK_AI.equals(text(item))) {
                    ((IconParentMenuItem) item).getChildren().onSuccess(children -> {
                        for (final Item child : children) {
                            aiCommands.put(text(child), ((MenuItem) child).getCommand());
                        }
                    });
                }
            }
        });
        eventBus.addHandler(AskStroomAiEvent.getType(), event -> {
            final GeneralTableContext table = (GeneralTableContext) event.getData();
            final List<List<String>> aiRows = table.getRows();
            aiLabel.setText("AI: " + table.getDescription()
                            + " / columns: " + String.join(", ", table.getColumns())
                            + " / last row: " + String.join(", ", aiRows.get(aiRows.size() - 1)));
        });

        final Spy onRowContext = context.fn(ON_ROW_CONTEXT);
        grid.addDomHandler(event -> {
            final int index = rowIndexOf(Element.as(event.getNativeEvent().getEventTarget()));
            if (index >= 0) {
                onRowContext.call(index);
            }
        }, ContextMenuEvent.getType());

        final FlowPanel panel = new FlowPanel();
        panel.add(sized(grid, "200px", null));
        panel.add(menuLabel);
        panel.add(button("Ask AI about the cell", () -> run(aiCommands, "Cell")));
        panel.add(button("Ask AI about the page", () -> run(aiCommands, "Current Page")));
        panel.add(aiLabel);
        return panel;
    }

    static void contextMenuPlay(final Play play) {
        final Query nameCell = play.querySelector(ROW + "[data-row=\"2\"] .data-grid__cell[data-col=\"1\"]");
        play.waitFor(() -> play.expect(nameCell).toBeInTheDocument());
        play.rightClick(nameCell);
        play.waitFor(() -> play.expect(play.spy(ON_ROW_CONTEXT)).toHaveBeenCalledWith(2));
        play.waitFor(() -> play.expect(play.getByText("Menu: Copy | Export Table | " + ASK_AI))
                .toBeInTheDocument());

        play.click(play.getByRole("button", "Ask AI about the cell"));
        play.waitFor(() -> play.expect(play.getByText(TextMatch.startingWith("AI: ")))
                .toHaveTextContent("AI: People Cell [Name] / columns: Name / last row: Person 3"));

        // Row 500 is far out of view, so not rendered, but is still sent
        play.click(play.getByRole("button", "Ask AI about the page"));
        play.waitFor(() -> play.expect(play.getByText(TextMatch.startingWith("AI: ")))
                .toHaveTextContent("AI: People (500 rows, 5 cols) / columns: ID, Name, Role, Age, Active"
                                   + " / last row: 500, Person 500, Manager, 24, Yes"));
    }

    // --------------------------------------------------------------------------------
    // Links in the context menu

    /// A grid with a link to a dashboard in its cell, showing Stroom's real menu. With `handled`,
    /// something handles the grid's links, as a dashboard's table does (adding the dashboard it is
    /// in, which the linked dashboard takes its parameters from): the menu's link items go to it,
    /// as a click on the link does. They went to the app directly, so a dashboard opened from the
    /// menu had no parent. With nothing handling them, they go up the page for the app to open.
    ///
    /// @param context The story's context.
    /// @param handled Whether something handles the grid's links.
    /// @return The grid.
    static Widget linkMenuGrid(final StoryContext context, final boolean handled) {
        final Spy onGridLink = context.fn(ON_GRID_LINK);
        final Spy onAppLink = context.fn(ON_APP_LINK);
        final StoryPopups popups = StoryPopups.create(context).withPageRequests();
        popups.getEventBus().addHandler(HyperlinkEvent.getType(), event ->
                onAppLink.call(describe(event.getHyperlink())));
        final DataGrid<String> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addResizableColumn(new Column<String, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final String link) {
                return SafeHtmlUtils.fromTrustedString("<u link=\"" + link + "\">Other Dashboard</u>");
            }
        }, "Link", 240);
        grid.setRowData(List.of("[Other Dashboard](uuid=other){dashboard}"));
        if (handled) {
            grid.addHyperlinkHandler(event -> onGridLink.call(describe(event.getHyperlink())));
        }
        return sized(grid, "120px", "300px");
    }

    /// Opens a dashboard link from the menu of [#linkMenuGrid].
    ///
    /// @param play    The play.
    /// @param handled Whether something handles the grid's links.
    static void linkMenuPlay(final Play play, final boolean handled) {
        play.rightClick(play.findByText("Other Dashboard"));
        final Query menu = play.screen().findByRole("menu");
        play.click(play.within(menu).getByRole("menuitem", TextMatch.containing("Open In This Tab")));
        // The link, to open in this tab
        final String expected = "uuid=other self";
        if (handled) {
            play.waitFor(() -> play.expect(play.spy(ON_GRID_LINK)).toHaveBeenCalledWith(expected));
            play.expect(play.spy(ON_APP_LINK)).not().toHaveBeenCalled();
        } else {
            play.waitFor(() -> play.expect(play.spy(ON_APP_LINK)).toHaveBeenCalledWith(expected));
        }
    }

    private static String describe(final LinkValue hyperlink) {
        return hyperlink.getHref() + " " + hyperlink.getTarget();
    }

    private static void run(final Map<String, Command> commands, final String name) {
        final Command command = commands.get(name);
        if (command != null) {
            command.execute();
        }
    }

    private static List<String> texts(final List<Item> items) {
        final List<String> texts = new ArrayList<>();
        for (final Item item : items) {
            final String text = text(item);
            if (text != null) {
                texts.add(text);
            }
        }
        return texts;
    }

    private static String text(final Item item) {
        return item instanceof MenuItem && ((MenuItem) item).getText() != null
                ? ((MenuItem) item).getText().asString()
                : null;
    }

    // --------------------------------------------------------------------------------
    // Helpers

    private static Query header(final Play play, final int index) {
        return play.querySelector(HEADER_CELLS + "[data-hcol=\"" + index + "\"]");
    }

    /// Moves the pointer over a header, 5px inside its right edge, so the resize handle goes there.
    ///
    /// @param width The header's width, as the play expects it to be by then.
    private static void hoverNearRightEdge(final Play play, final int index, final int width) {
        final Query header = header(play, index);
        play.fireEvent().pointerMove(header, EventInit.create().relativeTo(header, width - 5, 5));
    }

    private static void expectWidth(final Play play, final int index, final long width) {
        final Value<Double> actual = header(play, index).width();
        play.waitFor(() -> play.expect("header " + index + "'s width", () -> Math.round(actual.get()))
                .toBe(width));
    }

    /// The number of the column's cells whose content is wider than the cell.
    private static int truncatedCells(final Play play, final int column) {
        final Element grid = play.querySelector(".data-grid").element().get();
        int truncated = 0;
        final NodeList<Element> divs = grid.getElementsByTagName("div");
        for (int i = 0; i < divs.getLength(); i++) {
            final Element cell = divs.getItem(i);
            if (String.valueOf(column).equals(cell.getAttribute("data-col"))) {
                final Element content = cell.getFirstChildElement();
                if (content != null && content.getScrollWidth() > content.getClientWidth()) {
                    truncated++;
                }
            }
        }
        return truncated;
    }

    private static int rowIndexOf(final Element element) {
        Element current = element;
        while (current != null) {
            if (current.hasAttribute("data-row")) {
                return Integer.parseInt(current.getAttribute("data-row"));
            }
            current = current.getParentElement();
        }
        return -1;
    }

    private static List<String> list(final String... items) {
        final List<String> list = new ArrayList<>();
        for (final String item : items) {
            list.add(item);
        }
        return list;
    }

    private static Widget sized(final Widget widget, final String height, final String width) {
        final SimplePanel frame = new SimplePanel(widget);
        frame.setHeight(height);
        if (width != null) {
            frame.setWidth(width);
        }
        return frame;
    }

    private static Label note(final String text) {
        final Label label = new Label(text);
        label.getElement().getStyle().setProperty("padding", "6px 0");
        return label;
    }

    private static Button button(final String text, final Runnable action) {
        final Button button = new Button(text);
        button.addClickHandler(event -> action.run());
        return button;
    }


    // --------------------------------------------------------------------------------


    /// Records what the grid tells its heading listener, as spies.
    private static final class RecordingListener implements HeadingListener {

        private final Spy onColumnResize;
        private final Spy onColumnReorder;
        private final Spy onShowMenu;
        private final Spy onMoveStart;
        private final Spy onMoveEnd;

        private RecordingListener(final StoryContext context) {
            onColumnResize = context.fn(ON_COLUMN_RESIZE);
            onColumnReorder = context.fn(ON_COLUMN_REORDER);
            onShowMenu = context.fn(ON_SHOW_MENU);
            onMoveStart = context.fn(ON_MOVE_START);
            onMoveEnd = context.fn(ON_MOVE_END);
        }

        @Override
        public void onMoveStart(final NativeEvent event, final Supplier<Heading> heading) {
            onMoveStart.call(columnOf(heading));
        }

        @Override
        public void onMoveEnd(final NativeEvent event, final Supplier<Heading> heading) {
            onMoveEnd.call(columnOf(heading));
        }

        @Override
        public void onShowMenu(final NativeEvent event, final Supplier<Heading> heading) {
            onShowMenu.call(columnOf(heading));
        }

        @Override
        public void moveColumn(final int fromIndex, final int toIndex) {
            onColumnReorder.call(fromIndex, toIndex);
        }

        @Override
        public void resizeColumn(final int colIndex, final int size) {
            onColumnResize.call(colIndex, size);
        }

        private static int columnOf(final Supplier<Heading> heading) {
            final Heading value = heading.get();
            return value != null
                    ? value.getColIndex()
                    : -1;
        }
    }
}
