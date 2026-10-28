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

import stroom.data.client.component.DocRefCell;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.menu.client.HasChildren;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.menu.client.Separator;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories for Stroom's [DocRefCell]. Each cell is a `CellWidget` in a box (see
/// [CellRendererWidgets]).
///
/// The `onOpen` spy reports the cell's `onOpen` callback, which `RefCellsController` sets in Stroom
/// (to fire an `OpenDocumentEvent`).
/// The cell copies the name to the clipboard itself, so the story reports a mouse down on the copy
/// button (as the cell sees it) to the `onCopy` spy.
public final class DocRefCellStories {

    private static final String ON_OPEN = "onOpen";
    private static final String ON_COPY = "onCopy";
    // The class of the cell's copy button
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final int CELL_WIDTH_PX = 280;

    private static final DocRef FEED_REF = new DocRef("Feed", "uuid-feed-1", "TEST_FEED");
    private static final DocRef PIPELINE_REF = new DocRef("Pipeline", "uuid-pipe-1", "Event Processing");
    private static final DocRef DICTIONARY_REF = new DocRef("Dictionary", "uuid-dict-1", "Known Hosts");
    // A broken ref: a name but no UUID. A DocRef can't have a null UUID, so it is empty
    private static final DocRef BROKEN_REF = new DocRef("Pipeline", "", "Deleted Pipeline");

    private DocRefCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/DocRefCell", DocRefCellStories.class)
                .layout(StoryLayout.CENTERED)
                // No args: every story has its own render
                // A feed, a pipeline and a dictionary reference (with type icons). Hover a row to
                // reveal the copy / open buttons. The broken ref has no uuid.
                .story("Basic", DocRefCellStories::basic)
                .withPlay(play -> {
                    // The broken ref can be copied but not opened (DocRefCell once only hid the open
                    // button for a null UUID, which a DocRef can't have)
                    play.expect(play.getByTitle("Open Pipeline Event Processing")).toBeInTheDocument();
                    play.expect(play.getByTitle("Copy name 'Deleted Pipeline' to clipboard")).toBeInTheDocument();
                    play.expect(play.queryByTitle("Open Pipeline Deleted Pipeline")).toBeNull();
                })
                // In a grid, as Stroom's lists show it: the open icon is a button, named for what it
                // opens, which the grid's keys reach (Right from the row into its cell, whose button
                // takes the focus; Down to the same cell of the next row) and Enter or Space
                // presses. It was an icon that only a mouse could press
                .story("OpenByKeyboard", DocRefCellStories::grid)
                .withPlay(play -> {
                    final Query openFeed = play.getByRole("button", "Open Feed TEST_FEED");
                    final Query openPipeline = play.getByRole("button", "Open Pipeline Event Processing");
                    // Pressing a row's text gives the row the keyboard focus, and Right its button
                    play.click(play.getByText("TEST_FEED"));
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(openFeed).toHaveFocus());
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN)).toHaveBeenCalledWith("Feed TEST_FEED"));
                    // Down to the same cell of the next row, whose button takes the focus
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(openPipeline).toHaveFocus());
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN))
                            .toHaveBeenNthCalledWith(2, "Pipeline Event Processing"));
                    // Each key opened its document once (the button's own click does nothing more)
                    play.expect(play.spy(ON_OPEN).callCount()).toBe(2);
                })
                // No type icon, and the hasOpenAndCopy(false) variant (plain text, no buttons)
                .story("IconAndButtonToggles", context -> StoryPanels.column(8,
                        cellBox(new DocRefCell.Builder<DocRef>()
                                .onOpen(docRef -> {
                                })
                                .showIcon(false)
                                .docRefFunction(docRef -> docRef)
                                .build(), FEED_REF),
                        cellBox(new DocRefCell.Builder<DocRef>()
                                .onOpen(docRef -> {
                                })
                                .showIcon(true)
                                .hasOpenAndCopy(false)
                                .docRefFunction(docRef -> docRef)
                                .build(), PIPELINE_REF)))
                // The labels of the menu items that DocRefCell.getContextMenuItems gives the grid's
                // context menu
                .story("ContextMenuItems", context -> contextMenuItems(new DocRefCell.Builder<DocRef>()
                        .onOpen(docRef -> {
                        })
                        .docRefFunction(docRef -> docRef)
                        .build()))
                .withPlay(play -> play.expect(play.getByText(TextMatch.startingWith("Open Feed")))
                        .toBeInTheDocument())
                // With no onOpen (no owner to open documents) the cell offers no way to open one:
                // no open button, and its menu has only 'Copy Feed Details'
                .story("NoOpen", context -> StoryPanels.column(8,
                        cellBox(new DocRefCell.Builder<DocRef>()
                                .showIcon(true)
                                .docRefFunction(docRef -> docRef)
                                .build(), FEED_REF),
                        contextMenuItems(new DocRefCell.Builder<DocRef>()
                                .docRefFunction(docRef -> docRef)
                                .build())))
                .withPlay(play -> {
                    play.expect(play.getByTitle("Copy name 'TEST_FEED' to clipboard")).toBeInTheDocument();
                    play.expect(play.queryByTitle("Open Feed TEST_FEED")).toBeNull();
                    play.expect(play.getByText(TextMatch.startingWith("Copy Feed Details"))).toBeInTheDocument();
                    play.expect(play.queryByText(TextMatch.startingWith("Open Feed"))).toBeNull();
                });
    }

    private static Widget basic(final StoryContext context) {
        final InlineLabel lastAction = CellRendererWidgets.lastAction();
        final Spy onOpen = context.fn(ON_OPEN);
        final Spy onCopy = context.fn(ON_COPY);

        final DocRefCell<DocRef> cell = new DocRefCell.Builder<DocRef>()
                .onOpen(docRef -> {
                    onOpen.call(docRef.getType() + " " + docRef.getName());
                    lastAction.setText("Last action: open " + docRef.getType() + " \"" + docRef.getName() + "\"");
                })
                .showIcon(true)
                .docRefFunction(docRef -> docRef)
                .build();

        final FlowPanel column = StoryPanels.column(8);
        for (final DocRef docRef : List.of(FEED_REF, PIPELINE_REF, DICTIONARY_REF, BROKEN_REF)) {
            final FlowPanel box = cellBox(cell, docRef);
            // The cell copies to the clipboard itself, so the story reports the mouse down that the
            // cell copies on
            box.addDomHandler(event -> {
                if (CellRendererWidgets.targetHasClassName(event.getNativeEvent(), COPY_CLASS_NAME)) {
                    onCopy.call(docRef.getName());
                    lastAction.setText("Last action: copy \"" + docRef.getName() + "\"");
                }
            }, MouseDownEvent.getType());
            column.add(box);
        }
        column.add(lastAction);
        return column;
    }

    /// The cell in a grid with a selection model, as Stroom's lists use it.
    private static Widget grid(final StoryContext context) {
        final Spy onOpen = context.fn(ON_OPEN);
        final DocRefCell<DocRef> cell = new DocRefCell.Builder<DocRef>()
                .onOpen(docRef -> onOpen.call(docRef.getType() + " " + docRef.getName()))
                .showIcon(true)
                .docRefFunction(docRef -> docRef)
                .build();

        final DataGrid<DocRef> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addDefaultSelectionModel(false);
        grid.addResizableColumn(new Column<DocRef, DocRef>(cell) {
            @Override
            public DocRef getValue(final DocRef docRef) {
                return docRef;
            }
        }, "Document", CELL_WIDTH_PX);
        grid.setRowData(List.of(FEED_REF, PIPELINE_REF, DICTIONARY_REF));

        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().getStyle().setProperty("height", "200px");
        panel.getElement().getStyle().setProperty("width", (CELL_WIDTH_PX + 40) + "px");
        return panel;
    }

    private static FlowPanel cellBox(final Cell<DocRef> cell, final DocRef docRef) {
        return CellRendererWidgets.cellBox(new CellWidget<>(cell, docRef), CELL_WIDTH_PX);
    }

    /// A monospace list of the context menu items of `FEED_REF`.
    private static Widget contextMenuItems(final DocRefCell<DocRef> cell) {
        final FlowPanel panel = new FlowPanel();
        final Style style = panel.getElement().getStyle();
        style.setProperty("fontSize", "13px");
        style.setProperty("fontFamily", "monospace");
        panel.add(new Label("DocRefCell.getContextMenuItems(FEED_REF):"));
        final HTML list = new HTML();
        // Stroom's CSS gives list items their own font, so the list is set to monospace too
        final StringBuilder html = new StringBuilder("<ul style=\"font-family: monospace\">");
        final List<Item> items = cell.getContextMenuItems(null, FEED_REF);
        for (final Item item : items) {
            html.append("<li style=\"font-family: monospace\">").append(label(item));
            if (item instanceof HasChildren) {
                final List<String> children = new ArrayList<>();
                ((HasChildren) item).getChildren().onSuccess(childItems -> {
                    for (final Item child : childItems) {
                        children.add(label(child));
                    }
                });
                html.append(" › ").append(String.join(", ", children));
            }
            html.append("</li>");
        }
        html.append("</ul>");
        list.setHTML(html.toString());
        panel.add(list);
        return panel;
    }

    /// The label of a menu item (HTML, as GWT's menu shows the text).
    private static String label(final Item item) {
        if (item instanceof MenuItem) {
            return ((MenuItem) item).getText().asString();
        } else if (item instanceof Separator) {
            return "separator";
        }
        return item.getClass().getSimpleName();
    }
}
