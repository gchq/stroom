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

import stroom.cell.info.client.ActionMenuCell;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.user.cellview.client.CellWidget;
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
                // The "Actions..." ellipsis opens the row menu on a left-click
                .story("Actions", context -> {
                    final Spy clicked = context.fn(CLICKED);
                    final List<Item> items = new ArrayList<>();
                    items.add(menuItem(1, "Add new rule above", SvgImage.ADD_ABOVE, () -> clicked.call("add")));
                    items.add(menuItem(2, "Copy Rule", SvgImage.COPY, () -> clicked.call("copy")));
                    items.add(menuItem(3, "Delete Rule", SvgImage.DELETE, () -> clicked.call("delete")));
                    return actionMenuCell(context, items);
                })
                .withPlay(play -> {
                    final Play body = play.screen();
                    play.click(play.getByTitle("Actions..."));
                    play.waitFor(() -> play.expect(body.getByText("Copy Rule")).toBeVisible());
                    play.expect(body.getByText("Add new rule above")).toBeVisible();

                    play.click(body.getByText("Copy Rule"));
                    play.waitFor(() -> play.expect(play.spy(CLICKED)).toHaveBeenCalledTimes(1));
                    play.expect(play.spy(CLICKED)).toHaveBeenCalledWith("copy");
                })
                // No items: the cell has no menu to show
                .story("NoItems", context -> {
                    context.fn(CLICKED);
                    return actionMenuCell(context, Collections.emptyList());
                })
                .withPlay(play -> {
                    // ActionMenuCell renders its ellipsis for every row, even with no items; a mouse
                    // down on it then shows no menu
                    play.expect(play.getByTitle("Actions...")).toBeInTheDocument();
                    play.click(play.getByTitle("Actions..."));
                    play.expect(play.screen().querySelector(".menuCellTable")).toBeNull();
                });
    }

    private static Widget actionMenuCell(final StoryContext context, final List<Item> items) {
        final StoryPopups popups = StoryPopups.create(context).withMenus();
        final ActionMenuCell<String> cell = new ActionMenuCell<>(row -> items, popups.getEventBus()::fireEvent);
        return new CellWidget<>(cell, ROW);
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
