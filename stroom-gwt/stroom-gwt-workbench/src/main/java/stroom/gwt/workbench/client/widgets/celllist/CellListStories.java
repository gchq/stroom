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

package stroom.gwt.workbench.client.widgets.celllist;

import stroom.data.grid.client.PagerViewImpl;
import stroom.data.table.client.MyCellTable;
import stroom.explorer.client.presenter.FindCellTable;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// Stories of a list shaped like Stroom's find result lists: a [FindCellTable] (a [MyCellTable]
/// with one bare column and no header, and single selection) in a [PagerViewImpl], as
/// `FindDocResultListPresenter` builds it.
public final class CellListStories {

    // Arg names
    private static final String EMPTY = "empty";
    private static final String ON_SELECTION_CHANGE = "onSelectionChange";

    private static final List<String> FRUITS = Arrays.asList("Apple", "Banana", "Cherry", "Damson", "Elderberry");
    private static final String EMPTY_MESSAGE = "Nothing to show.";

    private CellListStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/CellList", CellListStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.bool(EMPTY).description("Show no items.").defaultSummary("false"))
                .argType(ArgType.action(ON_SELECTION_CHANGE)
                        .description("Called with the selected row's index when the selection changes."))
                // A single-column list: a pager over natively-scrolling, auto-height rows
                .story("Default", CellListStories::fromArgs)
                .withPlay(play -> {
                    // All items render as rows
                    for (final String fruit : FRUITS) {
                        play.expect(play.getByText(fruit)).toBeInTheDocument();
                    }
                    // The first row is the (controlled) selection.
                    // The rows are a CellTable's <tr>s (no role="row"), and the selected class is the
                    // CellTable style's cellTableSelectedRow
                    final MyCellTable.DefaultResources resources = GWT.create(MyCellTable.DefaultResources.class);
                    play.expect(play.getByText("Apple").closest("tr"))
                            .toHaveClass(resources.cellTableStyle().cellTableSelectedRow());
                })
                // Empty state shows the caller's message
                .story("Empty", CellListStories::fromArgs)
                .withArgs(Args.of(EMPTY, true))
                .withPlay(play -> play.expect(play.getByText(EMPTY_MESSAGE)).toBeInTheDocument());
    }

    /// A list made from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final boolean empty = context.getArgs().getBoolean(EMPTY);
        final List<String> items = empty
                ? Collections.emptyList()
                : FRUITS;

        final FindCellTable<String> cellTable = new FindCellTable<>();
        cellTable.addColumn(new Column<String, String>(new ItemCell()) {
            @Override
            public String getValue(final String item) {
                return item;
            }
        });
        // Stroom's find lists show nothing when empty; the story uses CellTable's empty table widget
        // to show a message
        cellTable.setEmptyTableWidget(new Label(EMPTY_MESSAGE));
        cellTable.setRowData(0, items);
        cellTable.setRowCount(items.size(), true);

        final Spy onSelectionChange = context.fn(ON_SELECTION_CHANGE);
        cellTable.getSelectionModel().addSelectionHandler(event -> {
            final String selected = cellTable.getSelectionModel().getSelected();
            onSelectionChange.call(selected == null
                    ? null
                    : items.indexOf(selected));
        });
        // Start with the first row selected
        if (!items.isEmpty()) {
            cellTable.getSelectionModel().setSelected(items.get(0));
        }

        final PagerViewImpl pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(cellTable);

        // <div style={{display: 'flex', height: 260, width: 340, border: ...}}>
        final FlowPanel box = new FlowPanel();
        final Style style = box.getElement().getStyle();
        style.setProperty("display", "flex");
        style.setProperty("height", "260px");
        style.setProperty("width", "340px");
        style.setProperty("border", "1px solid var(--dialog__border-color, #555)");
        final Widget pager = pagerView.asWidget();
        pager.getElement().getStyle().setProperty("flex", "1");
        box.add(pager);
        return box;
    }

    // --------------------------------------------------------------------------------

    /// Renders an item as `<div style="padding: 6px 10px">item</div>`.
    private static final class ItemCell extends AbstractCell<String> {

        @Override
        public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
            if (value != null) {
                sb.appendHtmlConstant("<div style=\"padding: 6px 10px\">");
                sb.appendEscaped(value);
                sb.appendHtmlConstant("</div>");
            }
        }
    }
}
