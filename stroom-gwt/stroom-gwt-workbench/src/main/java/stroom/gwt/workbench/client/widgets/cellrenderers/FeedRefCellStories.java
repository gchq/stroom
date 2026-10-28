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

package stroom.gwt.workbench.client.widgets.cellrenderers;

import stroom.data.client.component.FeedRefCell;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// Stories for Stroom's [FeedRefCell]. Each cell is a `CellWidget` in a box (see
/// [CellRendererWidgets]).
///
/// The rows are the feed names; an empty name is a row with no feed, shown as `(no feed)`. The
/// `onOpen` and `onCopyLink` spies report the cell's `onOpen` and `onCopyUrl` callbacks, which
/// `RefCellsController` sets in Stroom (to fire an `OpenFeedEvent` and a `CopyFeedUrlEvent`); the
/// latter is from the right-click menu of the grid it is in, which the story shows with Stroom's
/// real menu. The cell copies to the clipboard itself, so the
/// story reports a mouse down on the copy button to the `onCopy` spy.
public final class FeedRefCellStories {

    private static final String ON_OPEN = "onOpen";
    private static final String ON_COPY = "onCopy";
    private static final String ON_COPY_LINK = "onCopyLink";
    // The class of the cell's copy button
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
    // The class of the cell's open button
    private static final String OPEN_CLASS_NAME = "docRefLinkOpen";
    private static final String NO_FEED_TEXT = "(no feed)";
    private static final int CELL_WIDTH_PX = 280;

    private FeedRefCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/FeedRefCell", FeedRefCellStories.class)
                .layout(StoryLayout.CENTERED)
                // No args: every story has its own render
                // A feed row (icon + name, hover to reveal copy / open) and a non-feed row (no name:
                // plain text)
                .story("Basic", FeedRefCellStories::basic)
                // In a grid, a right click shows one menu: the grid's, with the cell's 'Open Feed'
                // and 'Copy Feed Details' first. The cell showed a menu of its own as well, which the
                // grid's replaced
                .story("ContextMenu", FeedRefCellStories::grid)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.rightClick(play.findByText("TEST_FEED"));
                    final Query menu = screen.findByRole("menu");
                    play.expect(screen.getAllByRole("menu")).toHaveLength(1);
                    play.expect(play.within(menu).getByRole("menuitem", "Open Feed")).toBeInTheDocument();
                    play.expect(play.within(menu).getByRole("menuitem", "Copy Feed Details")).toBeInTheDocument();
                    play.expect(play.within(menu).getByRole("menuitem", "Export Table")).toBeInTheDocument();
                    play.click(play.within(menu).getByRole("menuitem", "Open Feed"));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN)).toHaveBeenCalledWith("TEST_FEED"));
                    play.waitFor(() -> play.expect(screen.queryByRole("menu")).toBeNull());
                })
                // The grid's menu's Copy Feed Details > Link asks the cell's owner to copy the feed's
                // link (onCopyUrl; Stroom's RefCellsController fires CopyFeedUrlEvent)
                .story("CopyLinkFromTheMenu", FeedRefCellStories::grid)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.rightClick(play.findByText("TEST_FEED"));
                    final Query menu = screen.findByRole("menu");
                    play.click(play.within(menu).getByRole("menuitem", "Copy Feed Details"));
                    play.click(screen.findByRole("menuitem", "Link"));
                    play.waitFor(() -> play.expect(play.spy(ON_COPY_LINK)).toHaveBeenCalledWith("TEST_FEED"));
                })
                // In a grid, a press on the open icon (an SVG image) opens the feed. The grid's
                // selection threw on an SVG element, so the cell never had the press
                .story("OpenIconInGrid", FeedRefCellStories::grid)
                .withPlay(play -> {
                    play.findByText("TEST_FEED");
                    final Query open = play.querySelector("." + OPEN_CLASS_NAME + " svg");
                    play.fireEvent().mouseDown(open, EventInit.create().atCentreOf(open).buttons(1));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN)).toHaveBeenCalledWith("TEST_FEED"));
                })
                // With and without the feed type icon
                .story("IconToggle", context -> StoryPanels.column(8,
                        cellBox(cell(true), "TEST_FEED"),
                        cellBox(cell(false), "TEST_FEED")));
    }

    private static Widget basic(final StoryContext context) {
        // Stroom's real menu, for the cell's right-click menu
        StoryPopups.create(context).withMenus();
        final InlineLabel lastAction = CellRendererWidgets.lastAction();
        final Spy onOpen = context.fn(ON_OPEN);
        final Spy onCopy = context.fn(ON_COPY);
        final Spy onCopyLink = context.fn(ON_COPY_LINK);
        final FeedRefCell<String> cell = cell(
                name -> {
                    onOpen.call(name);
                    lastAction.setText("Last action: open feed \"" + name + "\"");
                },
                name -> {
                    onCopyLink.call(name);
                    lastAction.setText("Last action: copy link for \"" + name + "\"");
                },
                true);
        final FlowPanel column = StoryPanels.column(8);
        for (final String name : new String[]{"TEST_FEED", "ANOTHER_LONG_FEED_NAME_THAT_ELLIPSISES", ""}) {
            final FlowPanel box = cellBox(cell, name);
            // The cell copies to the clipboard itself, so the story reports the mouse down that the
            // cell copies on. A row with no feed has no copy button
            box.addDomHandler(event -> {
                if (CellRendererWidgets.targetHasClassName(event.getNativeEvent(), COPY_CLASS_NAME)) {
                    onCopy.call(name);
                    lastAction.setText("Last action: copy \"" + name + "\"");
                }
            }, MouseDownEvent.getType());
            column.add(box);
        }
        column.add(lastAction);
        return column;
    }

    /// The cell in a grid, as Stroom's lists use it, whose right-click menu is Stroom's real menu.
    private static Widget grid(final StoryContext context) {
        // The grid asks for its context menu with a MenuRequest
        StoryPopups.create(context).withPageRequests();
        final Spy onOpen = context.fn(ON_OPEN);
        final Spy onCopyLink = context.fn(ON_COPY_LINK);
        final FeedRefCell<String> cell = cell(onOpen::call, onCopyLink::call, true);
        final DataGrid<String> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addDefaultSelectionModel(false);
        grid.addResizableColumn(new Column<String, String>(cell) {
            @Override
            public String getValue(final String name) {
                return name;
            }
        }, "Feed", CELL_WIDTH_PX);
        grid.setRowData(List.of("TEST_FEED", ""));

        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().getStyle().setProperty("height", "200px");
        panel.getElement().getStyle().setProperty("width", (CELL_WIDTH_PX + 40) + "px");
        return panel;
    }

    /// The cell, with a row's name as its value (an empty name is a row with no feed), that can open
    /// its feed and copy its link but reports neither.
    private static FeedRefCell<String> cell(final boolean showIcon) {
        return cell(name -> {
        }, name -> {
        }, showIcon);
    }

    /// The cell, with a row's name as its value: an empty name is a row with no feed.
    private static FeedRefCell<String> cell(final Consumer<String> onOpen,
                                            final Consumer<String> onCopyUrl,
                                            final boolean showIcon) {
        return new FeedRefCell.Builder<String>()
                .onOpen(onOpen)
                .onCopyUrl(onCopyUrl)
                .showIcon(showIcon)
                .nameFunction(name -> name.isEmpty()
                        ? null
                        : name)
                .cellTextFunction(name -> SafeHtmlUtils.fromString(name.isEmpty()
                        ? NO_FEED_TEXT
                        : name))
                .build();
    }

    private static FlowPanel cellBox(final FeedRefCell<String> cell, final String name) {
        return CellRendererWidgets.cellBox(new CellWidget<>(cell, name), CELL_WIDTH_PX);
    }
}
