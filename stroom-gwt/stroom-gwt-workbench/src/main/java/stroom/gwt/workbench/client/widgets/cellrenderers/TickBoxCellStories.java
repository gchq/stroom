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

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;

import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Stories for Stroom's [TickBoxCell], the tick box of its grids and trees (the form tick box,
/// `CustomCheckBox`, is in `Widgets/Inputs/TickBox`).
///
/// Each row's tick box is ticked or not, and the header's is half-ticked when some of the rows are
/// ticked but not all, as `ColumnSelectionPresenter` builds them. Clicking the header ticks all the
/// rows, or unticks them if they all are. A grid the user can't change has tick boxes with no border
/// that can't be clicked, and no header tick box, as `DataGridUtil.updatableTickBoxColumnBuilder`
/// and `DocumentCreatePermissionsListPresenter` build them.
public final class TickBoxCellStories {

    private static final String ON_CHANGE = "onChange";
    private static final List<String> NAMES = List.of("Alpha", "Bravo", "Charlie", "Delta");
    private static final String TICKED = "Ticked";
    private static final String HALF_TICKED = "Half-Ticked";
    private static final String NOT_TICKED = "Not Ticked";
    private static final String HEADER_TICK_BOX = "th .tickBox";

    private TickBoxCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/TickBoxCell", TickBoxCellStories.class)
                .layout(StoryLayout.PADDED)
                // Some rows ticked, so the header is half-ticked
                .story("InGrid", context -> grid(context, true, "Bravo"))
                .withPlay(TickBoxCellStories::playInGrid)
                // A grid the user can't change: read-only tick boxes (the normal box with a grey tick), no
                // header tick box, and clicks do nothing
                .story("ReadOnly", context -> grid(context, false, "Bravo", "Delta"))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getAllByTitle(TICKED)).toHaveLength(2));
                    play.expect(play.querySelectorAll(".tickBox-noBorder")).toHaveLength(NAMES.size());
                    // To assistive technology, each is a check box that can't be changed
                    play.expect(play.getAllByRole("checkbox")).toHaveLength(NAMES.size());
                    play.expect(rowTickBox(play, "Bravo")).toBeChecked();
                    play.expect(rowTickBox(play, "Bravo")).toHaveAttribute("aria-readonly", "true");
                    play.expect(play.querySelectorAll(HEADER_TICK_BOX)).toHaveLength(0);
                    play.click(rowTickBox(play, "Alpha"));
                    play.expect(play.getAllByTitle(TICKED)).toHaveLength(2);
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                });
    }

    private static void playInGrid(final Play play) {
        final Spy onChange = play.spy(ON_CHANGE);
        play.waitFor(() -> play.expect(play.querySelector(HEADER_TICK_BOX))
                .toHaveAttribute("title", HALF_TICKED));
        play.expect(rowTickBox(play, "Bravo")).toHaveAttribute("title", TICKED);
        // To assistive technology, each is a check box: the half-ticked header is 'mixed'
        play.expect(play.getAllByRole("checkbox")).toHaveLength(NAMES.size() + 1);
        play.expect(play.querySelector(HEADER_TICK_BOX)).toHaveAttribute("aria-checked", "mixed");
        play.expect(rowTickBox(play, "Bravo")).toBeChecked();
        play.expect(rowTickBox(play, "Alpha")).not().toBeChecked();
        play.expect(rowTickBox(play, "Alpha")).toHaveAttribute("aria-readonly", "false");

        // A half-ticked header ticks every row
        play.click(play.querySelector(HEADER_TICK_BOX));
        play.waitFor(() -> play.expect(onChange).toHaveBeenLastCalledWith("Alpha,Bravo,Charlie,Delta"));
        play.expect(play.querySelector(HEADER_TICK_BOX)).toHaveAttribute("title", TICKED);
        play.expect(play.querySelector(HEADER_TICK_BOX)).toBeChecked();
        play.expect(play.getAllByTitle(TICKED)).toHaveLength(NAMES.size() + 1);

        // A ticked header unticks them all
        play.click(play.querySelector(HEADER_TICK_BOX));
        play.waitFor(() -> play.expect(onChange).toHaveBeenLastCalledWith(""));
        play.expect(play.querySelector(HEADER_TICK_BOX)).toHaveAttribute("title", NOT_TICKED);

        // Ticking one row half-ticks the header
        play.click(rowTickBox(play, "Charlie"));
        play.waitFor(() -> play.expect(onChange).toHaveBeenLastCalledWith("Charlie"));
        play.expect(play.querySelector(HEADER_TICK_BOX)).toHaveAttribute("title", HALF_TICKED);
    }

    /// @return The tick box in the row with the name.
    private static Query rowTickBox(final Play play, final String name) {
        return play.within(play.getByText(name).closest("tr")).querySelector(".tickBox");
    }

    /// A grid of [#NAMES] with a tick box column, as Stroom's list presenters build them.
    ///
    /// @param updatable Whether the user can change the ticks: if so, the header has a tick box.
    /// @param ticked    The names that start ticked.
    private static Widget grid(final StoryContext context, final boolean updatable, final String... ticked) {
        final Spy onChange = context.fn(ON_CHANGE);
        final Set<String> selected = new LinkedHashSet<>(List.of(ticked));
        final MyDataGrid<String> grid = new MyDataGrid<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");

        final TickBoxCell.Appearance appearance = updatable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();
        final Column<String, TickBoxState> column = new Column<String, TickBoxState>(
                TickBoxCell.create(appearance, false, false, updatable)) {
            @Override
            public TickBoxState getValue(final String name) {
                return TickBoxState.fromBoolean(selected.contains(name));
            }
        };
        if (updatable) {
            final Header<TickBoxState> header = new Header<TickBoxState>(TickBoxCell.create(false, false)) {
                @Override
                public TickBoxState getValue() {
                    if (selected.size() == NAMES.size()) {
                        return TickBoxState.TICK;
                    } else if (!selected.isEmpty()) {
                        return TickBoxState.HALF_TICK;
                    }
                    return TickBoxState.UNTICK;
                }
            };
            header.setUpdater(value -> {
                selected.clear();
                if (TickBoxState.TICK.equals(value)) {
                    selected.addAll(NAMES);
                }
                onChange.call(String.join(",", inOrder(selected)));
                grid.redraw();
            });
            column.setFieldUpdater((index, name, value) -> {
                if (value.toBoolean()) {
                    selected.add(name);
                } else {
                    selected.remove(name);
                }
                onChange.call(String.join(",", inOrder(selected)));
                grid.redrawHeaders();
            });
            grid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);
        } else {
            grid.addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);
        }
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((String name) -> name).build(), "Name", 200);
        grid.setRowData(NAMES);

        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().getStyle().setProperty("height", "200px");
        panel.getElement().getStyle().setProperty("width", "300px");
        return panel;
    }

    /// @return The names in [#NAMES]' order.
    private static List<String> inOrder(final Set<String> names) {
        final List<String> ordered = new ArrayList<>();
        for (final String name : NAMES) {
            if (names.contains(name)) {
                ordered.add(name);
            }
        }
        return ordered;
    }
}
