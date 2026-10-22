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

import stroom.data.grid.client.ColumnBuilder;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerViewImpl;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRows.Note;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRows.Person;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.form.client.FormGroup;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// Stories for the new [DataGridWidget], the replacement for `MyDataGrid` described in
/// `stroom-core-client-widget/DATA_GRID_DESIGN.md`.
///
/// The stories share their names, rows and frame sizes with `Widgets/Data Grid/DataGrid`
/// ([DataGridStories]), so each scenario can be opened for both grids and compared. The plays check
/// the new grid's behaviour through its own markup: rows are `[data-row]` elements with
/// `aria-selected`, and header cells have the `columnheader` role.
///
/// Stories that only the new grid can show (virtual mode, percent columns, keyboard movement) are
/// added at the end. The column resize, move and context menu stories are built by
/// [ColumnInteractionGrids].
public final class DataGridWidgetStories {

    // Spies for what the grid reports
    private static final String ON_SELECTION_CHANGE = "onSelectionChange";
    private static final String ON_SORT_CHANGE = "onSortChange";
    private static final String ON_NAVIGATE = "onNavigate";
    private static final String ON_ROW_DOUBLE_CLICK = "onRowDoubleClick";

    // The grid's markup
    private static final String GRID = ".data-grid";
    private static final String SCROLLER = ".data-grid__scroller";
    private static final String ROW = ".data-grid__body [data-row]";
    private static final String GRID_ROW = ".data-grid__row";
    private static final String HEADER_CELLS = "[role=\"columnheader\"]";
    private static final String KEYBOARD_ROW_CLASS = "data-grid__row--keyboard";

    // Field names of the sortable columns
    private static final String NAME_FIELD = "Name";
    private static final String ROLE_FIELD = "Role";
    private static final String AGE_FIELD = "Age";
    private static final String ACTIVE_FIELD = "Active";

    private static final int MANY_ROWS = 20000;
    private static final int TOGGLE_ROWS = 500;
    private static final int SCROLL_TO_ROW = 100;
    private static final int PAGER_PAGE_SIZE = 5;
    private static final int NOTE_COLUMN_WIDTH = 300;

    private static final String TOGGLE_MULTI_LINE = "Multi line";
    private static final String TOGGLE_SINGLE_LINE = "Single line";

    private DataGridWidgetStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/DataGridWidget", DataGridWidgetStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Basic grid: no selection
                .story("BasicGrid", context -> sized(paged(plainGrid()), "320px", null))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelectorAll(ROW)).toHaveLength(15));
                    play.expect(play.querySelectorAll(HEADER_CELLS)).toHaveLength(5);
                    // Natural mode by default
                    play.expect(play.querySelector(GRID)).not().toHaveClass("data-grid--virtual");
                })
                // Sortable columns: unsorted -> ascending -> descending -> unsorted
                .story("SortableColumns", DataGridWidgetStories::sortableColumns)
                .withPlay(play -> {
                    final Query age = play.getByText(AGE_FIELD, HEADER_CELLS + " *");
                    play.waitFor(() -> play.expect(firstName(play)).toHaveTextContent("Alice Johnson"));

                    play.click(age);
                    play.waitFor(() -> play.expect(firstName(play)).toHaveTextContent("Liam Jackson"));
                    play.expect(headerCell(play, 3)).toHaveAttribute("aria-sort", "ascending");

                    play.click(age);
                    play.waitFor(() -> play.expect(firstName(play)).toHaveTextContent("Henry Moore"));
                    play.expect(headerCell(play, 3)).toHaveAttribute("aria-sort", "descending");

                    // The third click removes the sort, so the rows go back to their own order
                    play.click(age);
                    play.waitFor(() -> play.expect(firstName(play)).toHaveTextContent("Alice Johnson"));
                    play.expect(headerCell(play, 3)).toHaveAttribute("aria-sort", "none");
                    play.expect(play.spy(ON_SORT_CHANGE)).toHaveBeenCalledTimes(3);
                })
                // A default sort (R-H4b, D10): shown from the start, without a sort event, and back
                // when a click leaves nothing sorted, with one
                .story("DefaultSort", DataGridWidgetStories::defaultSort)
                .withPlay(play -> {
                    final Query age = play.getByText(AGE_FIELD, HEADER_CELLS + " *");
                    play.waitFor(() -> play.expect(firstName(play)).toHaveTextContent("Olivia Martin"));
                    play.expect(headerCell(play, 1)).toHaveAttribute("aria-sort", "descending");
                    play.expect(play.spy(ON_SORT_CHANGE)).not().toHaveBeenCalled();

                    play.click(age);
                    play.waitFor(() -> play.expect(headerCell(play, 3)).toHaveAttribute("aria-sort", "ascending"));
                    play.expect(headerCell(play, 1)).toHaveAttribute("aria-sort", "none");
                    play.click(age);
                    play.waitFor(() -> play.expect(headerCell(play, 3)).toHaveAttribute("aria-sort", "descending"));

                    // The third click would leave nothing sorted: the default comes back
                    play.click(age);
                    play.waitFor(() -> play.expect(headerCell(play, 1)).toHaveAttribute("aria-sort", "descending"));
                    play.expect(headerCell(play, 3)).toHaveAttribute("aria-sort", "none");
                    play.waitFor(() -> play.expect(firstName(play)).toHaveTextContent("Olivia Martin"));
                    play.expect(play.spy(ON_SORT_CHANGE)).toHaveBeenLastCalledWith(1);
                })
                // With pager: 5 rows per page
                .story("Pager", DataGridWidgetStories::pager)
                .withPlay(play -> play.waitFor(() -> play.expect(play.querySelectorAll(ROW))
                        .toHaveLength(PAGER_PAGE_SIZE)))
                // Empty state: no rows, blank as MyDataGrid's
                .story("EmptyState", context -> {
                    final DataGridWidget<Person> grid = personGrid();
                    addPlainColumns(grid);
                    grid.setRowData(0, Collections.emptyList());
                    grid.setRowCount(0);
                    return sized(paged(grid), "200px", null);
                })
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelectorAll(HEADER_CELLS)).toHaveLength(5));
                    play.expect(play.querySelectorAll(ROW)).toHaveLength(0);
                })
                // Multi-row selection: plain click replaces, Ctrl+click toggles, Shift+click extends
                .story("RowSelection", context -> selectionStory(context, true, "Selected rows: "))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Element> grid = play.querySelector(GRID).element();

                    play.click(row(play, 1));
                    expectSelected(play, grid, 1);

                    ctrlClick(play, 3);
                    expectSelected(play, grid, 1, 3);
                    ctrlClick(play, 3);
                    expectSelected(play, grid, 1);

                    ctrlClick(play, 3);
                    expectSelected(play, grid, 1, 3);
                    shiftClick(play, 5);
                    expectSelected(play, grid, 3, 4, 5);

                    play.click(row(play, 2));
                    expectSelected(play, grid, 2);
                })
                // Single-row selection: Ctrl and Shift do not add
                .story("SingleSelection", context -> selectionStory(context, false, "Selected row: "))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Element> grid = play.querySelector(GRID).element();

                    play.click(row(play, 0));
                    expectSelected(play, grid, 0);
                    play.click(row(play, 2));
                    expectSelected(play, grid, 2);
                    ctrlClick(play, 4);
                    expectSelected(play, grid, 4);
                    shiftClick(play, 6);
                    expectSelected(play, grid, 6);
                })
                // No selection model: clicking selects nothing
                .story("NoSelection", DataGridWidgetStories::noSelection)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());

                    play.click(row(play, 1));
                    ctrlClick(play, 3);

                    // Without a selection model rows carry no aria-selected at all
                    play.expect(play.querySelectorAll(ROW + "[aria-selected]")).toHaveLength(0);
                    play.expect(row(play, 0).closest(GRID_ROW)).toHaveStyle("cursor", "default");
                    play.expect(play.spy(ON_SELECTION_CHANGE)).not().toHaveBeenCalled();
                })
                // Controlled selection: the host sets the selection as well as reading it
                .story("ControlledSelection", DataGridWidgetStories::controlledSelection)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Element> grid = play.querySelector(GRID).element();

                    play.click(row(play, 1));
                    play.waitFor(() -> play.expect(play.getByText("host selection: 1")).toBeInTheDocument());
                    expectSelected(play, grid, 1);

                    play.click(play.getByRole("button", "select row 3"));
                    expectSelected(play, grid, 2);

                    play.click(play.getByRole("button", "clear"));
                    expectSelected(play, grid);
                })
                // A grid in a filling FormGroup has a height, and a click selects
                .story("SelectionSurvivesAFillingFormGroup", DataGridWidgetStories::fillingFormGroup)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Double> gridHeight = play.querySelector(GRID).height();
                    play.waitFor(() -> play.expect("the grid's height", gridHeight::get).toBeGreaterThan(50));

                    final Value<Element> firstRow = row(play, 0).closest(GRID_ROW).element();
                    play.expect("the first row contains the element at its centre",
                                    () -> isPaintedAt(firstRow.get()))
                            .toBe(true);

                    play.click(row(play, 0));
                    play.waitFor(() -> play.expect(row(play, 0).closest(GRID_ROW))
                            .toHaveAttribute("aria-selected", "true"));
                })
                // Double-click a row: a double select of the selection model
                .story("DoubleClick", DataGridWidgetStories::doubleClick)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 3)).toBeInTheDocument());
                    play.click(row(play, 3));
                    play.click(row(play, 3));
                    play.waitFor(() -> play.expect(play.spy(ON_ROW_DOUBLE_CLICK)).toHaveBeenCalledWith(3));
                })
                // Right-click a row: the grid's own menu (Copy, Export Table, Ask Stroom AI About)
                .story("ContextMenu", ColumnInteractionGrids::contextMenu)
                .withPlay(ColumnInteractionGrids::contextMenuPlay)

                // Row height and virtualisation

                // Default mode: the whole page, each row as tall as its content
                .story("NaturalHeight", context -> sized(paged(noteGrid(false)), "320px", "900px"))
                // Natural height and multi-line: cells wrap, so rows grow
                .story("NaturalHeightMultiLine", context -> sized(paged(noteGrid(true)), "320px", "900px"))
                .withPlay(play -> {
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect(rows).toHaveLength(4));
                    // The long description wraps, so its row is taller than a one-line row
                    final Value<Double> shortRow = rows.nth(0).height();
                    final Value<Double> longRow = rows.nth(1).height();
                    play.waitFor(() -> play.expect("the wrapped row's height over a single line's",
                                    () -> longRow.get() / shortRow.get())
                            .toBeGreaterThan(1.5));
                })
                // Multi-line toggled at runtime
                .story("MultiLineToggled", context -> {
                    final DataGridWidget<Note> grid = noteGrid(false);
                    return toggleFrame(grid, sized(paged(grid), "280px", null));
                })
                // Virtual mode with a page that fits: every row is rendered, at a fixed height
                .story("FixedRowsVirtual", context -> {
                    final DataGridWidget<Person> grid = plainGrid();
                    grid.setVirtual(true);
                    return sized(paged(grid), "320px", "900px");
                })
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(GRID)).toHaveClass("data-grid--virtual"));
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect("the number of rows", () -> rows.count().get())
                            .toBeGreaterThan(5));
                    play.expect("row heights are equal",
                                    () -> rows.nth(0).height().get() - rows.nth(4).height().get())
                            .toBe(0.0);
                })
                // Virtual mode with 20,000 rows on one page: only the rows in view are rendered
                .story("FixedRowsManyRows", DataGridWidgetStories::manyRows)
                .withPlay(play -> {
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    play.expect("the number of rendered rows", () -> rows.count().get()).toBeLessThan(60);

                    // Scroll to the end: the last row is rendered and the first is not
                    final Value<Element> scroller = play.querySelector(SCROLLER).element();
                    play.run("scroll to the end",
                            () -> scroller.get().setScrollTop(scroller.get().getScrollHeight()));
                    play.waitFor(() -> play.expect(row(play, MANY_ROWS - 1)).toBeInTheDocument());
                    play.expect(play.querySelector(ROW + "[data-row=\"0\"]")).toBeNull();
                    play.expect("the number of rendered rows", () -> rows.count().get()).toBeLessThan(60);
                })
                // Headerless and multi-column
                .story("HeaderlessMultiColumn", DataGridWidgetStories::headerless)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelectorAll(ROW)).toHaveLength(4));
                    play.expect(play.querySelector(".data-grid__head")).toHaveStyle("display", "none");
                })
                // The fill columns share the surplus width by weight
                .story("ColumnFillWeights", context -> sized(paged(noteGrid(true)), "320px", "900px"))
                .withPlay(play -> expectHeaderWidths(play, 300L, 260L, 340L))
                // No fill column, so the declared widths are kept and the table stops short
                .story("ColumnWidthsScaleWithoutFill", context -> {
                    final DataGridWidget<Note> grid = noteGridWithoutFill();
                    return sized(paged(grid), "320px", "900px");
                })
                .withPlay(play -> expectHeaderWidths(play, 300L, 200L, 200L))
                // Declared widths that overflow the container are left alone
                .story("ColumnWidthsOverflowUnscaled", context -> sized(paged(noteGrid(true)), "320px", "400px"))
                .withPlay(play -> expectHeaderWidths(play, 300L, 200L, 200L))
                // Resize and reorder: on by default, as in GWT
                .story("ResizeAndReorder", ColumnInteractionGrids::resizeAndReorder)
                .withPlay(ColumnInteractionGrids::resizeAndReorderPlay)
                // Multi-line and virtual: multi-line wins, the whole page is rendered and wraps
                .story("MultiLineSuspendsVirtual", context -> {
                    final DataGridWidget<Note> grid = noteGrid(true);
                    grid.setVirtual(true);
                    return sized(paged(grid), "320px", "900px");
                })
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelectorAll(ROW)).toHaveLength(4));
                    play.expect(play.querySelector(GRID)).toHaveClass("data-grid--multiline");
                    play.expect(play.querySelector(GRID)).not().toHaveClass("data-grid--virtual");
                })
                // Row column spans: as MarkerListPresenter spans its summary rows
                .story("RowColumnSpans", DataGridWidgetStories::rowColumnSpans)
                .withPlay(play -> {
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect("the number of rows", () -> rows.count().get())
                            .toBeGreaterThan(1));
                    final Query spanned = play.within(rows.nth(0)).querySelectorAll(".data-grid__cell");
                    final Query normal = play.within(rows.nth(1)).querySelectorAll(".data-grid__cell");
                    // Unlike GWT's colspan, the covered cells are not rendered at all
                    play.expect(spanned).toHaveLength(1);
                    play.expect(normal).toHaveLength(3);
                    play.expect(spanned.nth(0)).toHaveAttribute("aria-colspan", "3");
                })
                // Switching between virtual and wrapped natural rendering keeps the top visible row
                .story("VirtualMultiLineToggleKeepsPosition", DataGridWidgetStories::toggleKeepsPosition)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(GRID)).toHaveClass("data-grid--virtual"));
                    final Value<Element> scroller = play.querySelector(SCROLLER).element();
                    final Value<Element> firstRow = play.querySelector(ROW + "[data-row=\"0\"]").element();
                    // Rows are a fractional height (23.0625px at the default density), so use the
                    // exact height rather than the rounded offsetHeight
                    play.run("scroll to row 100", () -> scroller.get()
                            .setScrollTop((int) Math.round(SCROLL_TO_ROW * exactHeight(firstRow.get()))));
                    final Query targetRow = play.querySelector(ROW + "[data-row=\"" + SCROLL_TO_ROW + "\"]");
                    play.waitFor(() -> play.expect(targetRow).toBeInTheDocument());
                    play.waitFor(() -> play.expect("row 100's distance from the top of the view",
                                    () -> Math.abs(offsetInView(targetRow.element().get(), scroller.get())))
                            .toBeLessThan(2));

                    play.click(play.getByRole("button", TOGGLE_MULTI_LINE));
                    play.waitFor(() -> {
                        play.expect(play.querySelector(GRID)).not().toHaveClass("data-grid--virtual");
                        play.expect("row 100's distance from the top of the view",
                                        () -> Math.abs(offsetInView(targetRow.element().get(), scroller.get())))
                                .toBeLessThan(2);
                    });
                })
                // A header carries a title only when the column declares one
                .story("HeaderTooltipOnlyWhenDeclared", DataGridWidgetStories::headerTooltip)
                .withPlay(play -> {
                    final Query headers = play.querySelectorAll(HEADER_CELLS);
                    play.waitFor(() -> play.expect(headers).toHaveLength(2));
                    play.expect(headers.nth(0)).not().toHaveAttribute("title");
                    play.expect(play.within(headers.nth(0)).querySelector("[title]")).toBeNull();
                    play.expect(headers.nth(1)).not().toHaveAttribute("title");
                    play.expect(play.within(headers.nth(1)).querySelector(":scope > div"))
                            .toHaveAttribute("title", "What they do here");
                })

                // New in DataGridWidget

                // Percent columns: a share of the grid's initial width, then plain pixels
                .story("PercentColumns", DataGridWidgetStories::percentColumns)
                .withPlay(play -> expectHeaderWidths(play, 200L, 400L, 200L))
                // Keyboard: the selection follows the row focus (see DataGridKeyboard, and
                // Widgets/Data Grid/DataGridKeyboard for the rest of its keys); Ctrl moves without
                // choosing, and Enter chooses the focused row
                .story("KeyboardNavigation", context -> selectionStory(context, true, "Selected rows: "))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Element> grid = play.querySelector(GRID).element();

                    play.click(row(play, 0));
                    expectSelected(play, grid, 0);

                    // A key at a time, each on the row the last moved the focus to
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(row(play, 1).closest(GRID_ROW)).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(row(play, 2).closest(GRID_ROW)).toHaveClass(KEYBOARD_ROW_CLASS));
                    play.expect(row(play, 2).closest(GRID_ROW)).toHaveFocus();
                    expectSelected(play, grid, 2);

                    play.keyboard("{Control>}{ArrowDown}{/Control}");
                    play.waitFor(() -> play.expect(row(play, 3).closest(GRID_ROW)).toHaveClass(KEYBOARD_ROW_CLASS));
                    expectSelected(play, grid, 2);
                    play.keyboard("{Enter}");
                    expectSelected(play, grid, 3);

                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect(row(play, 14).closest(GRID_ROW)).toHaveClass(KEYBOARD_ROW_CLASS));
                    expectSelected(play, grid, 14);
                })

                // Stroom's interactive cells, unchanged, in the new grid (see InteractiveCellGrids)

                // TickBoxCell with a select-all header: ticking does not select the row, and a
                // refused tick is put right when the rows are sent again
                .story("TickBoxColumn", InteractiveCellGrids::tickBoxGrid)
                .withPlay(play -> {
                    final Spy onChange = play.spy(InteractiveCellGrids.ON_CHANGE);
                    play.waitFor(() -> play.expect(play.querySelector(InteractiveCellGrids.HEADER_TICK_BOX))
                            .toHaveAttribute("title", InteractiveCellGrids.HALF_TICKED));
                    play.expect(InteractiveCellGrids.rowTickBox(play, "Bob Smith"))
                            .toHaveAttribute("title", InteractiveCellGrids.TICKED);

                    // Ticking a row reaches the field updater, and the header follows
                    play.click(InteractiveCellGrids.rowTickBox(play, "Carol Williams"));
                    play.waitFor(() -> play.expect(onChange).toHaveBeenLastCalledWith("Bob Smith,Carol Williams"));
                    play.expect(InteractiveCellGrids.rowTickBox(play, "Carol Williams"))
                            .toHaveAttribute("title", InteractiveCellGrids.TICKED);
                    // ... and does not select the row
                    play.expect(InteractiveCellGrids.rowOf(play, "Carol Williams"))
                            .toHaveAttribute("aria-selected", "false");

                    // The tick box shows the new tick at once, but this change is refused and the
                    // rows are sent again, so it must go back to unticked
                    play.click(InteractiveCellGrids.rowTickBox(play, InteractiveCellGrids.LOCKED));
                    play.waitFor(() -> play.expect(InteractiveCellGrids.rowTickBox(play, InteractiveCellGrids.LOCKED))
                            .toHaveAttribute("title", InteractiveCellGrids.NOT_TICKED));
                    play.expect(onChange).toHaveBeenCalledTimes(1);

                    // The header ticks every row
                    play.click(play.querySelector(InteractiveCellGrids.HEADER_TICK_BOX));
                    play.waitFor(() -> play.expect(play.querySelector(InteractiveCellGrids.HEADER_TICK_BOX))
                            .toHaveAttribute("title", InteractiveCellGrids.TICKED));
                    play.expect(play.getAllByTitle(InteractiveCellGrids.TICKED)).toHaveLength(5);
                })
                // ValueSpinnerCell in a grid that polls: the spinner keeps the focus while its row
                // is redrawn, and an edit reaches the field updater
                .story("ValueSpinnerColumn", InteractiveCellGrids::valueSpinnerGrid)
                .withPlay(play -> {
                    final Query input = play.within(InteractiveCellGrids.rowOf(play, "Alice Johnson"))
                            .querySelector("input");
                    play.waitFor(() -> play.expect(input).toBeInTheDocument());
                    play.click(input);
                    play.expect(input).toHaveFocus();
                    // Clicking the spinner does not select the row
                    play.expect(InteractiveCellGrids.rowOf(play, "Alice Johnson"))
                            .toHaveAttribute("aria-selected", "false");

                    // Several polls rewrite the row; the spinner still has the focus
                    play.sleep(1000);
                    play.expect(play.within(InteractiveCellGrids.rowOf(play, "Alice Johnson"))
                                    .querySelector(".data-grid__cell[data-col=\"2\"]"))
                            .not().toHaveTextContent("Poll 0");
                    play.expect(input).toHaveFocus();

                    play.clear(input);
                    play.type(input, "42");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.spy(InteractiveCellGrids.ON_CHANGE))
                            .toHaveBeenLastCalledWith("Alice Johnson", 42));
                })
                // ExpanderCell: the expander opens and closes the tree without selecting the row
                .story("ExpanderColumn", InteractiveCellGrids::expanderGrid)
                .withPlay(play -> {
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect(rows).toHaveLength(2));
                    final Query engineering = InteractiveCellGrids.rowOf(play, "Engineering");
                    play.click(play.within(engineering).querySelector(".expanderIcon"));
                    play.waitFor(() -> play.expect(rows).toHaveLength(4));
                    play.expect(play.getByText("Alice Johnson")).toBeInTheDocument();
                    play.expect(engineering).toHaveAttribute("aria-selected", "false");

                    play.click(play.within(engineering).querySelector(".expanderIcon"));
                    play.waitFor(() -> play.expect(rows).toHaveLength(2));
                })
                // A grid with an expander column is a treegrid: its rows say their level and whether
                // they are expanded, and on a row Right expands it (or, expanded, moves into its
                // cells), and Left collapses it (or, collapsed or a leaf, goes up to its parent)
                .story("TreeKeys", InteractiveCellGrids::expanderGrid)
                .withPlay(play -> {
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect(rows).toHaveLength(2));
                    play.expect(play.querySelector(SCROLLER)).toHaveAttribute("role", "treegrid");
                    final Query engineering = InteractiveCellGrids.rowOf(play, "Engineering");
                    play.expect(engineering).toHaveAttribute("aria-level", "1");
                    play.expect(engineering).toHaveAttribute("aria-expanded", "false");
                    // The expander says what a press does, to the mouse; the row tells assistive
                    // technology (aria-expanded), so the expander is hidden from it
                    final Query expander = play.within(engineering).querySelector(".expanderCell");
                    play.expect(expander).toHaveAttribute("title", "Expand");
                    play.expect(expander).toHaveAttribute("aria-hidden", "true");

                    play.click(play.getByText("Engineering"));
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(rows).toHaveLength(4));
                    play.expect(engineering).toHaveAttribute("aria-expanded", "true");
                    play.expect(engineering).toHaveFocus();
                    play.expect(play.within(engineering).querySelector(".expanderCell"))
                            .toHaveAttribute("title", "Collapse");

                    play.keyboard("{ArrowDown}");
                    final Query alice = InteractiveCellGrids.rowOf(play, "Alice Johnson");
                    play.waitFor(() -> play.expect(alice).toHaveFocus());
                    play.expect(alice).toHaveAttribute("aria-level", "2");
                    play.expect(alice).not().toHaveAttribute("aria-expanded");

                    // A leaf: Left goes up to its parent, then Left collapses the parent
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(engineering).toHaveFocus());
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(rows).toHaveLength(2));
                    play.expect(engineering).toHaveAttribute("aria-expanded", "false");
                    play.expect(engineering).toHaveFocus();

                    // Right expands it again, and Right again, now it is expanded, moves into its
                    // first cell (its expander's)
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(rows).toHaveLength(4));
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(play.within(engineering).querySelector("[data-col=\"0\"]"))
                            .toHaveFocus());
                })
                // CommandLinkCell: its open button runs the command without selecting the row,
                // while a click on its text selects the row
                .story("CommandLinkColumn", InteractiveCellGrids::commandLinkGrid)
                .withPlay(play -> {
                    final Query row = InteractiveCellGrids.rowOf(play, "Carol Williams");
                    play.waitFor(() -> play.expect(row).toBeInTheDocument());
                    play.click(play.within(row).querySelector(".commandLinkOpen"));
                    play.waitFor(() -> play.expect(play.spy(InteractiveCellGrids.ON_OPEN))
                            .toHaveBeenCalledWith("Carol Williams"));
                    play.expect(row).toHaveAttribute("aria-selected", "false");

                    play.click(play.getByText("Carol Williams"));
                    play.waitFor(() -> play.expect(row).toHaveAttribute("aria-selected", "true"));
                })
                // ActionMenuCell: the row's menu opens and its item runs, without selecting the row
                .story("ActionMenuColumn", InteractiveCellGrids::actionMenuGrid)
                .withPlay(play -> {
                    final Query row = InteractiveCellGrids.rowOf(play, "Bob Smith");
                    play.waitFor(() -> play.expect(row).toBeInTheDocument());
                    play.click(play.within(row).getByTitle("Actions..."));
                    play.waitFor(() -> play.expect(play.screen().getByText("Copy Rule")).toBeVisible());
                    play.click(play.screen().getByText("Copy Rule"));
                    play.waitFor(() -> play.expect(play.spy(InteractiveCellGrids.ON_ACTION))
                            .toHaveBeenCalledWith("copy", "Bob Smith"));
                    play.expect(row).toHaveAttribute("aria-selected", "false");
                })
                // Resize and reorder switched off, as the dashboard does outside design mode
                .story("ResizeAndReorderDisabled", ColumnInteractionGrids::resizeAndReorderDisabled)
                .withPlay(ColumnInteractionGrids::resizeAndReorderDisabledPlay)
                // A header with a live text box, as the dashboard's filter row
                .story("HeaderInput", ColumnInteractionGrids::headerInput)
                .withPlay(ColumnInteractionGrids::headerInputPlay);
    }

    // --------------------------------------------------------------------------------
    // Stories

    private static Widget sortableColumns(final StoryContext context) {
        final DataGridWidget<Person> grid = personGrid();
        final List<Person> original = DataGridRows.people();
        addSortableColumns(grid);
        final Spy onSortChange = context.fn(ON_SORT_CHANGE);
        // As Stroom's client-sorted list presenters: the data model sorts on the grid's sort list
        grid.addColumnSortHandler(event -> {
            final List<Person> rows = new ArrayList<>(original);
            final Comparator<Person> comparator = comparator(grid.getColumnSortList());
            if (comparator != null) {
                rows.sort(comparator);
            }
            grid.setRowData(0, rows);
            onSortChange.call(event.getColumnSortList().size());
        });
        grid.setRowData(new ArrayList<>(original));
        return sized(paged(grid), "320px", null);
    }

    // The sortable grid with a default sort, Name descending, which its first rows are sorted by
    private static Widget defaultSort(final StoryContext context) {
        final DataGridWidget<Person> grid = personGrid();
        final List<Person> original = DataGridRows.people();
        addSortableColumns(grid);
        grid.setDefaultSort(new ColumnSortInfo(grid.getColumn(1), false));
        final Spy onSortChange = context.fn(ON_SORT_CHANGE);
        grid.addColumnSortHandler(event -> {
            grid.setRowData(0, sortedBy(original, grid));
            onSortChange.call(event.getColumnSortList().size());
        });
        grid.setRowData(sortedBy(original, grid));
        return sized(paged(grid), "320px", null);
    }

    private static List<Person> sortedBy(final List<Person> original, final DataGridWidget<Person> grid) {
        final List<Person> rows = new ArrayList<>(original);
        final Comparator<Person> comparator = comparator(grid.getColumnSortList());
        if (comparator != null) {
            rows.sort(comparator);
        }
        return rows;
    }

    private static Widget pager(final StoryContext context) {
        final DataGridWidget<Person> grid = new DataGridWidget<>(new SimpleEventBus(), PAGER_PAGE_SIZE);
        grid.setWidth("100%");
        grid.setHeight("100%");
        addPlainColumns(grid);
        final PagerViewImpl pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(grid);
        final ListDataProvider<Person> dataProvider = new ListDataProvider<>(DataGridRows.people());
        dataProvider.addDataDisplay(grid);
        final Spy onNavigate = context.fn(ON_NAVIGATE);
        grid.addRangeChangeHandler(event -> onNavigate.call(event.getNewRange().getStart()));
        return sized(pagerView.asWidget(), "260px", null);
    }

    private static Widget manyRows(final StoryContext context) {
        final DataGridWidget<Person> grid = new DataGridWidget<>(new SimpleEventBus(),
                DataGridWidget.MASSIVE_LIST_PAGE_SIZE);
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.setVirtual(true);
        addPlainColumns(grid);
        final PagerViewImpl pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(grid);
        final ListDataProvider<Person> dataProvider = new ListDataProvider<>(DataGridRows.generatedPeople(MANY_ROWS));
        dataProvider.addDataDisplay(grid);
        return sized(pagerView.asWidget(), "320px", "900px");
    }

    /// The `RowSelection`, `SingleSelection` and `KeyboardNavigation` stories.
    private static Widget selectionStory(final StoryContext context,
                                         final boolean allowMultiSelect,
                                         final String prefix) {
        final DataGridWidget<Person> grid = personGrid();
        addPlainColumns(grid);
        final List<Person> rows = DataGridRows.people();
        grid.setRowData(rows);
        final MultiSelectionModelImpl<Person> selectionModel = grid.addDefaultSelectionModel(allowMultiSelect);
        final Label label = note(prefix + "(none)");
        final Spy onSelectionChange = context.fn(ON_SELECTION_CHANGE);
        selectionModel.addSelectionHandler(event -> {
            final List<Integer> indices = selectedIndices(rows, selectionModel);
            onSelectionChange.call((Object) indices.toArray(new Integer[0]));
            label.setText(prefix + (indices.isEmpty()
                    ? "(none)"
                    : join(indices, 1)));
        });
        return column(sized(paged(grid), "300px", null), label);
    }

    private static Widget noSelection(final StoryContext context) {
        final DataGridWidget<Person> grid = plainGrid();
        context.fn(ON_SELECTION_CHANGE);
        return column(sized(paged(grid), "300px", null), note("onSelectionChange fired: 0 time(s)"));
    }

    private static Widget controlledSelection(final StoryContext context) {
        final DataGridWidget<Person> grid = personGrid();
        addPlainColumns(grid);
        final List<Person> rows = DataGridRows.people();
        grid.setRowData(rows);
        final MultiSelectionModelImpl<Person> selectionModel = grid.addDefaultSelectionModel(false);
        final Label label = note("host selection: (none)");
        final Spy onSelectionChange = context.fn(ON_SELECTION_CHANGE);
        selectionModel.addSelectionHandler(event -> {
            final List<Integer> indices = selectedIndices(rows, selectionModel);
            onSelectionChange.call((Object) indices.toArray(new Integer[0]));
            label.setText("host selection: " + (indices.isEmpty()
                    ? "(none)"
                    : join(indices, 0)));
        });

        final Button selectRow3 = new Button("select row 3");
        selectRow3.addClickHandler(event -> selectionModel.setSelected(rows.get(2)));
        final Button clear = new Button("clear");
        clear.addClickHandler(event -> selectionModel.clear());
        final FlowPanel buttons = new FlowPanel();
        buttons.add(selectRow3);
        buttons.add(clear);
        return column(buttons, sized(paged(grid), "300px", null), label);
    }

    /// As Stroom's views put a grid in a filling form group, e.g. `DataRetentionRuleViewImpl`'s
    /// `<form:FormGroup addStyleNames="dock-max"><g:SimplePanel styleName="max"/>`.
    private static Widget fillingFormGroup(final StoryContext context) {
        final DataGridWidget<Person> grid = personGrid();
        addPlainColumns(grid);
        final List<Person> rows = DataGridRows.people();
        grid.setRowData(rows);
        final MultiSelectionModelImpl<Person> selectionModel = grid.addDefaultSelectionModel(false);
        final Spy onSelectionChange = context.fn(ON_SELECTION_CHANGE);
        selectionModel.addSelectionHandler(event ->
                onSelectionChange.call((Object) selectedIndices(rows, selectionModel).toArray(new Integer[0])));

        final SimplePanel gridPanel = new SimplePanel(paged(grid));
        gridPanel.setStyleName("max");
        final FormGroup formGroup = new FormGroup();
        formGroup.setIdentity("fillingGroup");
        formGroup.setLabel("Users and Groups");
        formGroup.addStyleName("dock-max");
        formGroup.add(gridPanel);

        final FlowPanel outer = new FlowPanel();
        outer.setStyleName("max form dock-container-vertical");
        outer.getElement().getStyle().setHeight(320, Unit.PX);
        outer.getElement().getStyle().setProperty("minHeight", "0");
        outer.add(formGroup);
        return outer;
    }

    private static Widget doubleClick(final StoryContext context) {
        final DataGridWidget<Person> grid = personGrid();
        addPlainColumns(grid);
        final List<Person> rows = DataGridRows.people();
        grid.setRowData(rows);
        final Label label = note("Last double-clicked: (none)");
        final Spy onRowDoubleClick = context.fn(ON_ROW_DOUBLE_CLICK);
        final MultiSelectionModelImpl<Person> selectionModel = grid.addDefaultSelectionModel(false);
        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                final Person person = selectionModel.getSelected();
                final int index = rows.indexOf(person);
                onRowDoubleClick.call(index);
                label.setText("Last double-clicked: Row " + (index + 1) + " (" + person.getName() + ")");
            }
        });
        return column(sized(paged(grid), "300px", null), label);
    }

    private static Widget rowColumnSpans(final StoryContext context) {
        final DataGridWidget<Note> grid = noteGrid(false);
        // As MarkerListPresenter.setData: span the first row's first cell across all three columns
        grid.setRowSpans((row, absIndex) -> absIndex == 0
                ? Collections.singletonMap(0, 3)
                : Collections.emptyMap());
        return sized(paged(grid), "320px", "900px");
    }

    private static Widget toggleKeepsPosition(final StoryContext context) {
        final DataGridWidget<Person> grid = new DataGridWidget<>(new SimpleEventBus(), TOGGLE_ROWS);
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.setVirtual(true);
        addPlainColumns(grid);
        grid.setRowData(DataGridRows.generatedPeople(TOGGLE_ROWS));
        return toggleFrame(grid, sized(paged(grid), "280px", null));
    }

    /// As SteppingFilterPresenter's element chooser, but as a [DataGridWidget] with the header
    /// hidden rather than a cell table.
    private static Widget headerless(final StoryContext context) {
        final DataGridWidget<Note> grid = noteGrid(false);
        grid.setHideHeader(true);
        return sized(grid, "320px", "900px");
    }

    private static Widget headerTooltip(final StoryContext context) {
        final DataGridWidget<Person> grid = personGrid();
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getRole).build(),
                DataGridUtil.headingBuilder("Role").withToolTip("What they do here").build(),
                120);
        grid.setRowData(DataGridRows.people());
        return sized(paged(grid), "320px", "900px");
    }

    private static Widget percentColumns(final StoryContext context) {
        final DataGridWidget<Note> grid = new DataGridWidget<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addPercentColumn(DataGridUtil.textColumnBuilder(Note::getName).build(), "Name", 25);
        grid.addPercentColumn(DataGridUtil.textColumnBuilder(Note::getDescription).build(), "Description", 50);
        grid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(DataGridWidgetStories::noteValue).build(),
                "Value", 100);
        grid.setRowData(DataGridRows.notes());
        // 800 wide: Name 25% = 200, Description 50% = 400, Value fills the remaining 200
        return sized(paged(grid), "320px", "800px");
    }

    // --------------------------------------------------------------------------------
    // Grids and columns

    private static DataGridWidget<Person> personGrid() {
        final DataGridWidget<Person> grid = new DataGridWidget<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");
        return grid;
    }

    private static DataGridWidget<Person> plainGrid() {
        final DataGridWidget<Person> grid = personGrid();
        addPlainColumns(grid);
        grid.setRowData(DataGridRows.people());
        return grid;
    }

    private static void addPlainColumns(final DataGridWidget<Person> grid) {
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
        // Not resizable
        grid.addColumn(DataGridUtil.textColumnBuilder(Person::getActiveText).centerAligned().build(),
                DataGridUtil.headingBuilder("Active").centerAligned().build(),
                80);
    }

    private static void addSortableColumns(final DataGridWidget<Person> grid) {
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((Person person) -> String.valueOf(person.getId()))
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("ID").rightAligned().build(),
                60);
        grid.addResizableColumn(sortableColumn(Person::getName, NAME_FIELD, false),
                DataGridUtil.headingBuilder("Name").build(),
                200);
        grid.addResizableColumn(sortableColumn(Person::getRole, ROLE_FIELD, false),
                DataGridUtil.headingBuilder("Role").build(),
                120);
        grid.addResizableColumn(sortableColumn((Person person) -> String.valueOf(person.getAge()), AGE_FIELD, true),
                DataGridUtil.headingBuilder("Age").rightAligned().build(),
                70);
        grid.addResizableColumn(sortableColumn(Person::getActiveText, ACTIVE_FIELD, false),
                DataGridUtil.headingBuilder("Active").centerAligned().build(),
                80);
    }

    private static Column<Person, String> sortableColumn(
            final Function<Person, String> valueExtractor,
            final String field,
            final boolean rightAligned) {
        final ColumnBuilder<Person, String, Cell<String>> builder =
                DataGridUtil.textColumnBuilder(valueExtractor).withSorting(field);
        if (rightAligned) {
            builder.rightAligned();
        }
        return builder.build();
    }

    /// The comparator for a sort list, as `DataGridComparatorFactory` builds one, or `null` if
    /// nothing is sorted.
    private static Comparator<Person> comparator(final ColumnSortList sortList) {
        final Map<String, Comparator<Person>> fields = new HashMap<>();
        fields.put(NAME_FIELD, Comparator.comparing(Person::getName));
        fields.put(ROLE_FIELD, Comparator.comparing(Person::getRole));
        fields.put(AGE_FIELD, Comparator.comparingInt(Person::getAge));
        fields.put(ACTIVE_FIELD, Comparator.comparing(Person::isActive));

        Comparator<Person> result = null;
        for (int i = 0; i < sortList.size(); i++) {
            final ColumnSortInfo info = sortList.get(i);
            if (info.getColumn() instanceof final OrderByColumn<?, ?> orderByColumn) {
                Comparator<Person> comparator = fields.get(orderByColumn.getField());
                if (comparator != null) {
                    if (!info.isAscending()) {
                        comparator = comparator.reversed();
                    }
                    result = result == null
                            ? comparator
                            : result.thenComparing(comparator);
                }
            }
        }
        return result;
    }

    /// The notes grid: Name fixed at 300, then Value and Description filling 30/70 with a minimum
    /// of 200, as `PropertyListPresenter`'s.
    private static DataGridWidget<Note> noteGrid(final boolean multiLine) {
        final DataGridWidget<Note> grid = new DataGridWidget<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Note::getName).build(), "Name", NOTE_COLUMN_WIDTH);
        grid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(DataGridWidgetStories::noteValue).build(),
                "Value", 30, 200);
        grid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(Note::getDescription).build(),
                "Description", 70, 200);
        grid.setMultiLine(multiLine);
        grid.setRowData(DataGridRows.notes());
        return grid;
    }

    private static DataGridWidget<Note> noteGridWithoutFill() {
        final DataGridWidget<Note> grid = new DataGridWidget<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Note::getName).build(), "Name", NOTE_COLUMN_WIDTH);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(DataGridWidgetStories::noteValue).build(),
                "Value", 200);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Note::getDescription).build(),
                "Description", 200);
        grid.setMultiLine(true);
        grid.setRowData(DataGridRows.notes());
        return grid;
    }

    private static String noteValue(final Note note) {
        return String.valueOf(note.getId());
    }

    // --------------------------------------------------------------------------------
    // Layout

    /// The grid in Stroom's [PagerViewImpl], as Stroom's list presenters show their grids.
    private static Widget paged(final DataGridWidget<?> grid) {
        final PagerViewImpl pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(grid);
        return pagerView.asWidget();
    }

    /// Equivalent of `<div style={{height, width}}>`.
    private static Widget sized(final Widget widget, final String height, final String width) {
        final SimplePanel panel = new SimplePanel(widget);
        panel.getElement().getStyle().setProperty("height", height);
        if (width != null) {
            panel.getElement().getStyle().setProperty("width", width);
        }
        return panel;
    }

    /// A 900px frame with a `Multi line`/`Single line` toggle button above the grid.
    private static Widget toggleFrame(final DataGridWidget<?> grid, final Widget sizedGrid) {
        final Button toggle = new Button(TOGGLE_MULTI_LINE);
        toggle.addClickHandler(event -> {
            final boolean multiLine = TOGGLE_MULTI_LINE.equals(toggle.getText());
            grid.setMultiLine(multiLine);
            toggle.setText(multiLine
                    ? TOGGLE_SINGLE_LINE
                    : TOGGLE_MULTI_LINE);
        });
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", "320px");
        frame.getElement().getStyle().setProperty("width", "900px");
        frame.add(toggle);
        frame.add(sizedGrid);
        return frame;
    }

    /// Equivalent of `<div style={{display: 'flex', flexDirection: 'column', gap: 12}}>`.
    private static Widget column(final Widget... widgets) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("display", "flex");
        panel.getElement().getStyle().setProperty("flexDirection", "column");
        panel.getElement().getStyle().setProperty("gap", "12px");
        for (final Widget widget : widgets) {
            panel.add(widget);
        }
        return panel;
    }

    /// Equivalent of `<p style={{fontSize: '0.8rem', color: 'var(--text-color, #aaa)', margin: 0}}>`.
    private static Label note(final String text) {
        final Label label = new Label(text);
        label.getElement().getStyle().setProperty("fontSize", "0.8rem");
        label.getElement().getStyle().setProperty("color", "var(--text-color, #aaa)");
        label.getElement().getStyle().setProperty("margin", "0");
        return label;
    }

    // --------------------------------------------------------------------------------
    // Selection helpers

    private static List<Integer> selectedIndices(final List<Person> rows,
                                                 final MultiSelectionModelImpl<Person> selectionModel) {
        final List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (selectionModel.isSelected(rows.get(i))) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static String join(final List<Integer> indices, final int offset) {
        final StringBuilder sb = new StringBuilder();
        for (final Integer index : indices) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(index + offset);
        }
        return sb.toString();
    }

    // --------------------------------------------------------------------------------
    // Play helpers

    /// The first cell of the row with the given page index.
    private static Query row(final Play play, final int index) {
        return play.querySelector(ROW + "[data-row=\"" + index + "\"] .data-grid__cell");
    }

    /// The Name cell (the second column) of the first row.
    private static Query firstName(final Play play) {
        return play.querySelector(ROW + "[data-row=\"0\"] .data-grid__cell[data-col=\"1\"]");
    }

    private static Query headerCell(final Play play, final int index) {
        return play.querySelector(HEADER_CELLS + "[data-hcol=\"" + index + "\"]");
    }

    private static void ctrlClick(final Play play, final int index) {
        play.fireEvent().mouseDown(row(play, index), EventInit.create().ctrlKey().button(0));
    }

    private static void shiftClick(final Play play, final int index) {
        play.fireEvent().mouseDown(row(play, index), EventInit.create().shiftKey().button(0));
    }

    /// Waits until exactly the given rows are selected.
    private static void expectSelected(final Play play, final Value<Element> grid, final Integer... indices) {
        final List<Integer> expected = Arrays.asList(indices);
        play.waitFor(() -> play.expect("the selected rows", () -> selectedRows(grid.get())).toEqual(expected));
    }

    /// The page indices of the rows marked `aria-selected="true"`, in order.
    private static List<Integer> selectedRows(final Element grid) {
        final List<Integer> selected = new ArrayList<>();
        final NodeList<Element> rows = grid.getElementsByTagName("div");
        for (int i = 0; i < rows.getLength(); i++) {
            final Element row = rows.getItem(i);
            if (row.hasAttribute("data-row") && "true".equals(row.getAttribute("aria-selected"))) {
                selected.add(Integer.valueOf(row.getAttribute("data-row")));
            }
        }
        return selected;
    }

    /// Waits until the header cells have the given widths, rounded to whole pixels.
    private static void expectHeaderWidths(final Play play, final Long... widths) {
        final Query headers = play.querySelectorAll(HEADER_CELLS);
        play.waitFor(() -> play.expect(headers).toHaveLength(widths.length));
        play.waitFor(() -> {
            for (int i = 0; i < widths.length; i++) {
                final Value<Double> width = headers.nth(i).width();
                play.expect("column " + i, () -> Math.round(width.get())).toBe(widths[i]);
            }
        });
    }

    /// Whether the element at the row's centre (40px in) is in the row.
    private static boolean isPaintedAt(final Element row) {
        final double left = row.getAbsoluteLeft() - Document.get().getScrollLeft();
        final double top = row.getAbsoluteTop() - Document.get().getScrollTop();
        final Element at = elementFromPoint(left + Math.min(40, row.getOffsetWidth() / 2.0),
                top + row.getOffsetHeight() / 2.0);
        return at != null && row.isOrHasChild(at);
    }

    private static native double exactHeight(Element element) /*-{
        return element.getBoundingClientRect().height;
    }-*/;

    private static native Element elementFromPoint(double x, double y) /*-{
        return $doc.elementFromPoint(x, y);
    }-*/;

    /// The row's distance from the top of the visible rows, i.e. from the bottom of the sticky header.
    private static int offsetInView(final Element row, final Element scroller) {
        final Element head = scroller.getFirstChildElement();
        return row.getAbsoluteTop() - (scroller.getAbsoluteTop() + head.getOffsetHeight());
    }
}
