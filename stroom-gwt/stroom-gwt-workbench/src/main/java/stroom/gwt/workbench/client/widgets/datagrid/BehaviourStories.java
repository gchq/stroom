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

import stroom.gwt.workbench.client.widgets.datagrid.DataGridRows.Person;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Button;
import stroom.widget.datagrid.client.DataGrid;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/// The behaviour baseline of design Phase 0, as stories with plays: what happens to the keyboard
/// row, the focus and the scroll position when new data arrives (R-S12, R-D7).
///
/// Each story is shown for [DataGrid] in natural ("New") and virtual ("Virtual") mode, with
/// the same play. The behaviour was measured on the old grid (`MyDataGrid`, since removed) first;
/// the plays record it, so the grid is still held to it.
///
/// The buttons send data as a controller does: the same rows again (a poll with no change), every
/// row's values changed (new row objects, so new keys), the first three rows only, and back.
/// Rebuild columns removes and re-adds every column, as the dashboard's `updateColumns` does.
public final class BehaviourStories {

    private static final int ROWS = 500;
    private static final String SAME = "Send the same rows";
    private static final String CHANGED = "Send changed rows";
    private static final String FEWER = "Send three rows";
    private static final String RESTORE = "Send all rows";
    private static final String REBUILD = "Rebuild columns";
    private static final String OUTSIDE = "Somewhere else";
    private static final String GRID_ROWS = ".data-grid__row--even, .data-grid__row--odd";
    private static final String FRAME_CLASS = "behaviour-grid-frame";
    private static final String PADDED_CLASS = "behaviour-padded-cell";

    private BehaviourStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/Behaviour", BehaviourStories.class)
                .layout(StoryLayout.PADDED)
                .story("KeyboardRowNew", context -> frame("new"))
                .withPlay(BehaviourStories::keyboardRowPlay)
                .story("KeyboardRowVirtual", context -> frame("virtual"))
                .withPlay(BehaviourStories::keyboardRowPlay)
                .story("ScrollPositionNew", context -> frame("new"))
                .withPlay(BehaviourStories::scrollPositionPlay)
                .story("ScrollPositionVirtual", context -> frame("virtual"))
                .withPlay(BehaviourStories::scrollPositionPlay)
                .story("HeaderFollowsDensityNew", context -> frame("new"))
                .withPlay(BehaviourStories::headerFollowsDensityPlay)
                .story("RowsFollowDensityVirtual", context -> frame("virtual-padded"))
                .withPlay(BehaviourStories::rowsFollowDensityPlay)
                .story("LoadedOnEveryLoadNew", context -> frame("new-loaded"))
                .withPlay(BehaviourStories::loadedOnEveryLoadPlay);
    }

    // --------------------------------------------------------------------------------
    // Plays

    /// R-S12: the keyboard row starts at 0, is shown only while the grid has the focus, follows its
    /// row's key when data arrives, and goes back to the first row (keeping the focus) when its row
    /// has gone.
    private static void keyboardRowPlay(final Play play) {
        final Value<Element> grid = play.querySelector("." + FRAME_CLASS).element();
        play.waitFor(() -> play.expect(row(play, 5)).toBeInTheDocument());
        play.expect("the keyboard row shown", () -> keyboardRow(grid.get())).toBe(-1);

        play.click(row(play, 5));
        expectKeyboard(play, grid, 5, 5);

        // Focus elsewhere: the keyboard row is no longer shown, and a poll doesn't take the focus
        play.click(play.getByRole("button", OUTSIDE));
        expectKeyboard(play, grid, -1, -1);
        play.click(play.getByRole("button", SAME));
        expectKeyboard(play, grid, -1, -1);

        play.click(row(play, 5));
        expectKeyboard(play, grid, 5, 5);
        // The same rows: it stays on its row. The buttons don't take the focus, as a poll wouldn't
        clickKeepingFocus(play, SAME);
        expectKeyboard(play, grid, 5, 5);
        // New row objects: its row has gone, so it goes back to the first row, keeping the focus
        clickKeepingFocus(play, CHANGED);
        expectKeyboard(play, grid, 0, 0);
    }

    /// R-D7: the scroll position is kept when the same or changed rows arrive and when the columns
    /// are rebuilt; a page too short to scroll is at the top.
    private static void scrollPositionPlay(final Play play) {
        final Value<Element> grid = play.querySelector("." + FRAME_CLASS).element();
        play.waitFor(() -> play.expect(row(play, 5)).toBeInTheDocument());
        play.run("scroll the rows to 4000px", () -> findScroller(grid.get()).setScrollTop(4000));
        expectScrollTop(play, grid, 4000);
        clickKeepingFocus(play, SAME);
        expectScrollTop(play, grid, 4000);
        clickKeepingFocus(play, CHANGED);
        expectScrollTop(play, grid, 4000);
        clickKeepingFocus(play, REBUILD);
        expectScrollTop(play, grid, 4000);
        clickKeepingFocus(play, FEWER);
        expectScrollTop(play, grid, 0);
        clickKeepingFocus(play, RESTORE);
        expectScrollTop(play, grid, 0);
    }

    /// The header's height, fixed to whole pixels, follows its content when the density
    /// preference changes while the grid is shown, as when the user changes it.
    private static void headerFollowsDensityPlay(final Play play) {
        final Value<Element> grid = play.querySelector("." + FRAME_CLASS).element();
        play.waitFor(() -> play.expect(row(play, 5)).toBeInTheDocument());
        final Value<Boolean> compact = play.capture("compact at first", BehaviourStories::isCompact);
        final Value<Integer> before = play.capture("the header's height", () -> headerHeight(grid.get()));

        play.run("change the density", () -> setCompact(!compact.get()));
        play.waitFor(() -> play.expect("the header's height fits its row",
                () -> headerFitsRow(grid.get())).toBe(true));
        play.expect("the header's height changed", () -> headerHeight(grid.get()) != before.get()).toBe(true);

        play.run("change the density back", () -> setCompact(compact.get()));
        play.waitFor(() -> play.expect("the header's height is as it was",
                () -> headerHeight(grid.get()) == before.get()).toBe(true));
    }

    /// Virtual rows have a fixed, measured height. It follows the cells' content when the density
    /// preference changes while the grid is shown: here a cell padded by the density's row
    /// padding, as a dashboard table's cells are.
    private static void rowsFollowDensityPlay(final Play play) {
        final Value<Element> grid = play.querySelector("." + FRAME_CLASS).element();
        play.waitFor(() -> play.expect(row(play, 5)).toBeInTheDocument());
        final Value<Boolean> compact = play.capture("compact at first", BehaviourStories::isCompact);
        final Value<Integer> before = play.capture("the first row's height", () -> rowHeight(grid.get()));
        play.expect("the first row's height fits its cells", () -> rowFitsCells(grid.get())).toBe(true);

        play.run("change the density", () -> setCompact(!compact.get()));
        play.waitFor(() -> play.expect("the first row's height fits its cells",
                () -> rowFitsCells(grid.get())).toBe(true));
        play.expect("the first row's height changed", () -> rowHeight(grid.get()) != before.get()).toBe(true);

        play.run("change the density back", () -> setCompact(compact.get()));
        play.waitFor(() -> play.expect("the first row's height is as it was",
                () -> rowHeight(grid.get()) == before.get()).toBe(true));
    }

    /// The grid says it has loaded (`LoadingStateChangeEvent`, LOADED) each time rows arrive, even the
    /// same rows on the same page, as GWT's grid does: screens act on it after every load, e.g. to
    /// choose the first row or check what was loaded after a refresh.
    private static void loadedOnEveryLoadPlay(final Play play) {
        play.waitFor(() -> play.expect(row(play, 5)).toBeInTheDocument());
        play.waitFor(() -> play.expect("the loads counted", BehaviourStories::loadedCount).toBeGreaterThan(0));
        final Value<Integer> before = play.capture("the loads counted", BehaviourStories::loadedCount);
        clickKeepingFocus(play, SAME);
        play.waitFor(() -> play.expect("the loads counted", () -> loadedCount() - before.get()).toBe(1));
        clickKeepingFocus(play, SAME);
        play.waitFor(() -> play.expect("the loads counted", () -> loadedCount() - before.get()).toBe(2));
    }

    private static native int loadedCount() /*-{
        var label = $doc.querySelector("[data-testid='loaded']");
        return label ? parseInt(label.getAttribute("data-count"), 10) : 0;
    }-*/;

    /// The first cell of a row: GWT's grid only responds to a press on a cell, not on the row.
    private static Query row(final Play play, final int index) {
        return play.within(play.querySelectorAll(GRID_ROWS).nth(index)).querySelector("td, .data-grid__cell");
    }

    /// Fires a button's click without the press moving the focus, as data arriving from a poll
    /// would not.
    private static void clickKeepingFocus(final Play play, final String button) {
        play.fireEvent().click(play.getByRole("button", button));
    }

    private static void expectKeyboard(final Play play,
                                       final Value<Element> grid,
                                       final int shown,
                                       final int focused) {
        play.waitFor(() -> {
            play.expect("the keyboard row shown", () -> keyboardRow(grid.get())).toBe(shown);
            play.expect("the row with the focus", () -> focusedRow(grid.get())).toBe(focused);
        });
    }

    private static void expectScrollTop(final Play play, final Value<Element> grid, final int top) {
        play.waitFor(() -> play.expect("the scroll position",
                () -> {
                    final Element scroller = findScroller(grid.get());
                    return scroller != null
                            ? scroller.getScrollTop()
                            : 0;
                }).toBe(top));
    }

    private static native boolean isCompact() /*-{
        return $doc.documentElement.classList.contains("stroom-density-compact");
    }-*/;

    private static native void setCompact(boolean compact) /*-{
        $doc.documentElement.classList.toggle("stroom-density-compact", compact);
    }-*/;

    private static native int headerHeight(Element root) /*-{
        return root.querySelector(".data-grid__head").offsetHeight;
    }-*/;

    // Whether the header's fixed height is its row's height, to the pixel
    private static native boolean headerFitsRow(Element root) /*-{
        var head = root.querySelector(".data-grid__head");
        var row = root.querySelector(".data-grid__header-row");
        return Math.abs(head.offsetHeight - row.getBoundingClientRect().height) < 1;
    }-*/;

    private static native int rowHeight(Element root) /*-{
        return Math.round(root.querySelector(".data-grid__row--even").getBoundingClientRect().height);
    }-*/;

    // Whether the first row's height is that of its tallest cell, the padded one: its content and
    // the padding round it up to the cell, to the pixel. (The cells stretch to the row, so can't
    // be measured themselves.)
    private static native boolean rowFitsCells(Element root) /*-{
        var row = root.querySelector(".data-grid__row--even");
        var padded = row.querySelector(".behaviour-padded-cell");
        var natural = padded.getBoundingClientRect().height;
        for (var el = padded.parentElement; el !== row; el = el.parentElement) {
            var style = $wnd.getComputedStyle(el);
            natural += parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);
        }
        return Math.abs(row.getBoundingClientRect().height - natural) < 1;
    }-*/;

    /// @return The index among the grid's rows of the one styled as the keyboard row, or -1.
    private static native int keyboardRow(Element root) /*-{
        var rows = root.querySelectorAll(".data-grid__row--even, .data-grid__row--odd");
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].classList.contains("data-grid__row--keyboard")) {
                return i;
            }
        }
        return -1;
    }-*/;

    /// @return The index among the grid's rows of the one containing the focus, or -1.
    private static native int focusedRow(Element root) /*-{
        var rows = root.querySelectorAll(".data-grid__row--even, .data-grid__row--odd");
        var active = $doc.activeElement;
        for (var i = 0; i < rows.length; i++) {
            if (active && rows[i].contains(active)) {
                return i;
            }
        }
        return -1;
    }-*/;

    // The descendant, no taller than the grid, that scrolls vertically the furthest: the new
    // grid's scroller or the old grid's body scroller (GWT's resize detectors are far taller).
    private static native Element findScroller(Element root) /*-{
        var best = null;
        var bestRange = 0;
        var all = root.querySelectorAll("*");
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var style = $wnd.getComputedStyle(el);
            var range = el.scrollHeight - el.clientHeight;
            if ((style.overflowY === "auto" || style.overflowY === "scroll") && style.visibility !== "hidden"
                && el.clientHeight <= root.clientHeight && range > bestRange) {
                best = el;
                bestRange = range;
            }
        }
        return best;
    }-*/;

    // --------------------------------------------------------------------------------
    // The grid and its buttons

    private static Widget frame(final String kind) {
        final List<Person> rows = DataGridRows.generatedPeople(ROWS);
        final GridAdapter grid = new NewGrid(kind.startsWith("virtual"), kind.endsWith("padded"));
        // Counts the grid's LOADED events, from the first rows on
        final Label loaded = new Label();
        if (kind.endsWith("loaded")) {
            loaded.getElement().setAttribute("data-testid", "loaded");
            loaded.getElement().setAttribute("data-count", "0");
            grid.addLoadingStateChangeHandler(event -> {
                if (event.getLoadingState() == LoadingState.LOADED) {
                    final int count = Integer.parseInt(loaded.getElement().getAttribute("data-count")) + 1;
                    loaded.getElement().setAttribute("data-count", String.valueOf(count));
                    loaded.setText("Loaded " + count + " times");
                }
            });
        }
        grid.setRows(rows);

        final SimplePanel gridFrame = new SimplePanel(grid.widget());
        gridFrame.setHeight("400px");
        gridFrame.setWidth("700px");
        gridFrame.addStyleName(FRAME_CLASS);

        final FlowPanel buttons = new FlowPanel();
        buttons.add(button(SAME, () -> grid.setRows(new ArrayList<>(rows))));
        buttons.add(button(CHANGED, () -> grid.setRows(changed(rows))));
        buttons.add(button(FEWER, () -> grid.setRows(new ArrayList<>(rows.subList(0, 3)))));
        buttons.add(button(RESTORE, () -> grid.setRows(new ArrayList<>(rows))));
        buttons.add(button(REBUILD, grid::rebuildColumns));
        buttons.add(button(OUTSIDE, () -> {
            // Only takes the focus, when clicked
        }));

        final FlowPanel panel = new FlowPanel();
        panel.add(gridFrame);
        panel.add(buttons);
        if (kind.endsWith("loaded")) {
            panel.add(loaded);
        }
        return panel;
    }

    private static List<Person> changed(final List<Person> rows) {
        final List<Person> changed = new ArrayList<>(rows.size());
        for (final Person person : rows) {
            changed.add(new Person(person.getId(),
                    person.getName() + " (changed)",
                    person.getRole(),
                    person.getAge(),
                    person.isActive()));
        }
        return changed;
    }

    private static void removeAll(final IntSupplier count, final Runnable removeFirst) {
        while (count.getAsInt() > 0) {
            removeFirst.run();
        }
    }

    private static Button button(final String text, final Runnable action) {
        final Button button = new Button(text);
        button.addClickHandler(event -> action.run());
        return button;
    }

    private static void addColumns(final ColumnAdder adder) {
        adder.add(DataGridUtil.textColumnBuilder((Person person) -> String.valueOf(person.getId()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("ID").rightAligned().build(),
                60);
        adder.add(DataGridUtil.textColumnBuilder(Person::getName).build(),
                DataGridUtil.headingBuilder("Name").build(),
                200);
        adder.add(DataGridUtil.textColumnBuilder(Person::getRole).build(),
                DataGridUtil.headingBuilder("Role").build(),
                120);
    }


    // --------------------------------------------------------------------------------


    /// The operations the stories need on the grid.
    private interface GridAdapter {

        Widget widget();

        void setRows(List<Person> rows);

        void rebuildColumns();

        HandlerRegistration addLoadingStateChangeHandler(LoadingStateChangeEvent.Handler handler);
    }


    // --------------------------------------------------------------------------------


    /// Adds a resizable column to the grid.
    private interface ColumnAdder {

        void add(Column<Person, ?> column, Header<?> header, int width);
    }


    // --------------------------------------------------------------------------------


    private static final class NewGrid implements GridAdapter {

        private final DataGrid<Person> grid = new DataGrid<>(ROWS);

        private NewGrid(final boolean virtual, final boolean padded) {
            grid.setWidth("100%");
            grid.setHeight("100%");
            grid.setVirtual(virtual);
            grid.addDefaultSelectionModel(true);
            addColumns(grid::addResizableColumn);
            if (padded) {
                // Taller than a line of text by the density's row padding
                grid.addResizableColumn(DataGridUtil.htmlColumnBuilder((Person person) ->
                                SafeHtmlUtils.fromTrustedString(
                                        "<div class=\"" + PADDED_CLASS + "\""
                                        + " style=\"padding: var(--row__padding--vertical) 0\">"
                                        + (person.isActive()
                                                ? "Active"
                                                : "Inactive")
                                        + "</div>"))
                                .build(),
                        DataGridUtil.headingBuilder("Status").build(),
                        100);
            }
        }

        @Override
        public Widget widget() {
            return grid;
        }

        @Override
        public void setRows(final List<Person> rows) {
            grid.setRowData(0, rows);
            grid.setRowCount(rows.size(), true);
        }

        @Override
        public void rebuildColumns() {
            removeAll(grid::getColumnCount, () -> grid.removeColumn(grid.getColumn(0)));
            addColumns(grid::addResizableColumn);
            grid.resizeTableToFitColumns();
        }

        @Override
        public HandlerRegistration addLoadingStateChangeHandler(final LoadingStateChangeEvent.Handler handler) {
            return grid.addLoadingStateChangeHandler(handler);
        }
    }
}
