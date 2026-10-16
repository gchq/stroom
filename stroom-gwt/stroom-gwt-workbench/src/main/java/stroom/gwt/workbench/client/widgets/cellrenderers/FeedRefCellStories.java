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

import stroom.data.client.presenter.FeedRefCell;
import stroom.data.grid.client.MyDataGrid;
import stroom.feed.client.CopyFeedUrlEvent;
import stroom.feed.client.OpenFeedEvent;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.List;

/// Stories for Stroom's [FeedRefCell]. Each cell is a `CellWidget` in a box (see
/// [CellRendererWidgets]).
///
/// The rows are the feed names; an empty name is a row with no feed, shown as `(no feed)`. The
/// `onOpen` and `onCopyLink` spies report Stroom's [OpenFeedEvent] and [CopyFeedUrlEvent], which
/// the cell fires on its event bus (the latter from the right-click menu of the grid it is in,
/// which the story shows with Stroom's real menu). The cell copies to the clipboard itself, so the
/// story reports a mouse down on the copy button to the `onCopy` spy.
public final class FeedRefCellStories {

    private static final String ON_OPEN = "onOpen";
    private static final String ON_COPY = "onCopy";
    private static final String ON_COPY_LINK = "onCopyLink";
    // The class of the cell's copy button
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
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
                // With and without the feed type icon
                .story("IconToggle", context -> StoryPanels.column(8,
                        cellBox(cell(new SimpleEventBus(), true), "TEST_FEED"),
                        cellBox(cell(new SimpleEventBus(), false), "TEST_FEED")));
    }

    private static Widget basic(final StoryContext context) {
        // Stroom's real menu, for the cell's right-click menu
        final EventBus eventBus = StoryPopups.create(context).withMenus().getEventBus();
        final InlineLabel lastAction = CellRendererWidgets.lastAction();
        final Spy onOpen = context.fn(ON_OPEN);
        final Spy onCopy = context.fn(ON_COPY);
        final Spy onCopyLink = context.fn(ON_COPY_LINK);
        eventBus.addHandler(OpenFeedEvent.getType(), event -> {
            onOpen.call(event.getName());
            lastAction.setText("Last action: open feed \"" + event.getName() + "\"");
        });
        eventBus.addHandler(CopyFeedUrlEvent.getType(), event -> {
            onCopyLink.call(event.getName());
            lastAction.setText("Last action: copy link for \"" + event.getName() + "\"");
        });

        final FeedRefCell<String> cell = cell(eventBus, true);
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
        final EventBus eventBus = StoryPopups.create(context).withMenus().getEventBus();
        final Spy onOpen = context.fn(ON_OPEN);
        eventBus.addHandler(OpenFeedEvent.getType(), event -> onOpen.call(event.getName()));
        final FeedRefCell<String> cell = cell(eventBus, true);
        final MyDataGrid<String> grid = new MyDataGrid<>(eventBus::fireEvent);
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

    /// The cell, with a row's name as its value: an empty name is a row with no feed.
    private static FeedRefCell<String> cell(final EventBus eventBus, final boolean showIcon) {
        return new FeedRefCell.Builder<String>()
                .eventBus(eventBus)
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
