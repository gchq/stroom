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

package stroom.gwt.workbench.client.widgets.actionmenucell;

import stroom.cell.client.ActionMenuCell;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;

import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Stories for Stroom's [ActionMenuCell].
///
/// The cell is a `CellWidget` whose value is the row. On a mouse down on its "Actions..." ellipsis
/// it fires `ShowMenuEvent` with the row's menu items, which the story shows with Stroom's real
/// menu ([StoryPopups#withMenus()]). The menu items' actions are reported to the `clicked` spy.
public final class ActionMenuCellStories {

    private static final String CLICKED = "clicked";
    private static final String ROW = "rule-1";

    private ActionMenuCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/ActionMenuCell", ActionMenuCellStories.class)
                .layout(StoryLayout.CENTERED)
                // No arg types: the items are functions, the title is ActionMenuCell's tooltip
                // extractor (left as Stroom's default, "Actions...") and the cell has no enabled state
                // The "Actions..." ellipsis opens the row menu on a left-click. Given no name, the
                // menu is named for its row
                .story("Actions", context -> {
                    final Spy clicked = context.fn(CLICKED);
                    final List<Item> items = new ArrayList<>();
                    items.add(menuItem(1, "Add Rule Above", SvgImage.ADD_ABOVE, () -> clicked.call("add")));
                    items.add(menuItem(2, "Duplicate Rule", SvgImage.COPY, () -> clicked.call("copy")));
                    items.add(menuItem(3, "Delete Rule", SvgImage.DELETE, () -> clicked.call("delete")));
                    return actionMenuCell(context, items);
                })
                .withPlay(play -> {
                    final Play body = play.screen();
                    // The button says it opens a menu, and whether the menu is open
                    final Query button = play.getByTitle("Actions...");
                    play.expect(button).toHaveAttribute("aria-haspopup", "menu");
                    play.expect(button).toHaveAttribute("aria-expanded", "false");
                    play.click(button);
                    play.waitFor(() -> play.expect(body.getByText("Duplicate Rule")).toBeVisible());
                    play.expect(body.getByRole("menu", "Actions for row 1")).toBeInTheDocument();
                    play.expect(button).toHaveAttribute("aria-expanded", "true");
                    play.expect(body.getByText("Add Rule Above")).toBeVisible();

                    play.click(body.getByText("Duplicate Rule"));
                    play.waitFor(() -> play.expect(play.spy(CLICKED)).toHaveBeenCalledTimes(1));
                    play.expect(play.spy(CLICKED)).toHaveBeenCalledWith("copy");
                })
                // The "Actions..." button is a button, so the keyboard can use it (it was a picture that
                // only a mouse press opened): in a grid, as Stroom's screens show it, the grid's keys
                // reach it, Enter, or Space, opens the menu, which takes the focus, Escape puts
                // the focus back on the button, and Left on a top item closes the menu
                .story("OpensFromTheKeyboard", context -> {
                    final Spy clicked = context.fn(CLICKED);
                    final List<Item> items = new ArrayList<>();
                    items.add(menuItem(1, "Add Rule Above", SvgImage.ADD_ABOVE, () -> clicked.call("add")));
                    items.add(menuItem(2, "Duplicate Rule", SvgImage.COPY, () -> clicked.call("copy")));
                    return actionMenuGrid(context, items);
                })
                .withPlay(play -> {
                    final Play body = play.screen();
                    final Query button = play.getByRole("button", "Actions...");
                    play.click(play.getByText(ROW));
                    play.keyboard("{ArrowRight}{End}");
                    play.expect(button).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(body.getByRole("menuitem", "Add Rule Above")).toHaveFocus());
                    play.expect(body.getByRole("menu", "Actions for row 1")).toBeInTheDocument();
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(body.queryByRole("menu")).toBeNull());
                    play.expect(button).toHaveFocus();
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(body.getByRole("menuitem", "Add Rule Above")).toHaveFocus());
                    // Left on a top item closes the row's menu too (the request carries it)
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(body.queryByRole("menu")).toBeNull());
                    play.expect(play.spy(CLICKED)).not().toHaveBeenCalled();
                })
                // No items: the cell has no menu to show, so no button (it once showed one that did
                // nothing)
                .story("NoItems", context -> {
                    context.fn(CLICKED);
                    return actionMenuCell(context, Collections.emptyList());
                })
                .withPlay(play -> play.expect(play.queryByTitle("Actions...")).toBeNull());
    }

    private static Widget actionMenuCell(final StoryContext context, final List<Item> items) {
        // The cell asks for its menu with a MenuRequest, which the relay shows
        StoryPopups.create(context).withPageRequests();
        final ActionMenuCell<String> cell = new ActionMenuCell<>(row -> items);
        return new CellWidget<>(cell, ROW);
    }

    // A one-row grid of a name column and the cell's, as Stroom's lists show it
    private static Widget actionMenuGrid(final StoryContext context, final List<Item> items) {
        // The cell asks for its menu with a MenuRequest, which the relay shows
        StoryPopups.create(context).withPageRequests();
        final DataGrid<String> grid = new DataGrid<>();
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((String row) -> row).build(), "Name", 150);
        grid.addColumn(DataGridUtil.columnBuilder(
                                (String row) -> row,
                                () -> new ActionMenuCell<String>(row -> items))
                        .build(),
                "",
                40);
        grid.setRowData(List.of(ROW));
        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().getStyle().setProperty("height", "120px");
        panel.getElement().getStyle().setProperty("width", "240px");
        return panel;
    }

    private static Item menuItem(final int priority,
                                 final String text,
                                 final SvgImage icon,
                                 final Runnable action) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(icon)
                .text(text)
                .command(action::run)
                .build();
    }
}
