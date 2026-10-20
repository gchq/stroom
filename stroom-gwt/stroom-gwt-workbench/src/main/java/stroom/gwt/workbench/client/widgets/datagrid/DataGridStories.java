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
import stroom.data.grid.client.Heading;
import stroom.data.grid.client.HeadingListener;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerViewImpl;
import stroom.data.table.client.MyCellTable;
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
import stroom.util.client.DataGridComparatorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.form.client.FormGroup;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/// Stories for Stroom's [MyDataGrid].
///
/// The grids are built as Stroom's list presenters build them: [MyDataGrid] with columns from
/// [DataGridUtil] (`addColumn`, `addResizableColumn`, `addAutoResizableColumn`), and
/// `addDefaultSelectionModel` for selection. GWT's grid is a pair of tables, so the plays find the
/// rows by the `__gwt_row` attribute GWT gives each body row and a selected row by its
/// `dataGridSelectedRow` class (rows have no `aria-selected`), and the header cells as the `th`
/// elements.
///
/// GWT always renders the whole page, and pages long lists with a pager; there is no mode that
/// renders only the rows in view.
public final class DataGridStories {

    // Spies for what the grid reports
    private static final String ON_SELECTION_CHANGE = "onSelectionChange";
    private static final String ON_SORT_CHANGE = "onSortChange";
    private static final String ON_NAVIGATE = "onNavigate";
    private static final String ON_ROW_DOUBLE_CLICK = "onRowDoubleClick";
    private static final String ON_ROW_CONTEXT = "onRowContext";
    private static final String ON_COLUMN_REORDER = "onColumnReorder";

    // GWT's own markup (see the class comment)
    private static final String ROW = "tr[__gwt_row]";
    private static final String SELECTED_ROW_CLASS = "dataGridSelectedRow";
    private static final String GRID = ".dataGridWidget";
    private static final String HEADER_CELLS = "th";

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

    private DataGridStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/DataGrid", DataGridStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // No args: the columns, rows and sort are objects and functions, which can't be
                // controls; each story builds its own grid
                // Basic grid: no pager, no selection
                .story("BasicGrid", context -> sized(paged(plainGrid(context)), "320px", null))
                // Sortable columns: click a header to sort
                .story("SortableColumns", DataGridStories::sortableColumns)
                // With pager: 5 rows per page
                .story("Pager", DataGridStories::pager)
                // Empty state: no rows
                .story("EmptyState", context -> {
                    final MyDataGrid<Person> grid = personGrid();
                    addPlainColumns(grid);
                    // MyDataGrid's empty table widget is blank (there is no setter for a message)
                    grid.setRowData(0, Collections.emptyList());
                    grid.setRowCount(0);
                    return sized(paged(grid), "200px", null);
                })
                // Multi-row selection: plain click replaces, Ctrl+click toggles, Shift+click extends
                .story("RowSelection", context -> selectionStory(context, true, "Selected rows: "))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Element> grid = play.querySelector(GRID).element();

                    play.click(row(play, 1));
                    expectSelected(play, grid, 1);

                    // Ctrl+click ADDS in multi mode (and toggles the same row back off)
                    ctrlClick(play, 3);
                    expectSelected(play, grid, 1, 3);
                    ctrlClick(play, 3);
                    expectSelected(play, grid, 1);

                    // Shift+click extends from the anchor, which the last ctrl+click moved to row 3
                    ctrlClick(play, 3);
                    expectSelected(play, grid, 1, 3);
                    shiftClick(play, 5);
                    expectSelected(play, grid, 3, 4, 5);

                    // A plain click replaces the lot
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

                    // The modifiers that ADD in multi mode must not add here
                    ctrlClick(play, 4);
                    expectSelected(play, grid, 4);
                    shiftClick(play, 6);
                    expectSelected(play, grid, 6);
                })
                // No selection model: clicking selects nothing
                .story("NoSelection", DataGridStories::noSelection)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());

                    play.click(row(play, 1));
                    ctrlClick(play, 3);

                    // No row has the selected row class (GWT rows never carry aria-selected)
                    play.expect(play.querySelectorAll("tr." + SELECTED_ROW_CLASS)).toHaveLength(0);
                    // MyDataGrid shows a pointer over the rows only when it has a selection model
                    play.expect(row(play, 0).closest("tbody")).toHaveStyle("cursor", "default");
                    play.expect(play.getByText("onSelectionChange fired: 0 time(s)")).toBeInTheDocument();
                    play.expect(play.spy(ON_SELECTION_CHANGE)).not().toHaveBeenCalled();
                })
                // Controlled selection: the host sets the selection as well as reading it
                .story("ControlledSelection", DataGridStories::controlledSelection)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());
                    final Value<Element> grid = play.querySelector(GRID).element();

                    // A click reaches the host ...
                    play.click(row(play, 1));
                    play.waitFor(() -> play.expect(play.getByText("host selection: 1")).toBeInTheDocument());
                    expectSelected(play, grid, 1);

                    // ... and a selection the host sets on its own is painted
                    play.click(play.getByRole("button", "select row 3"));
                    expectSelected(play, grid, 2);

                    play.click(play.getByRole("button", "clear"));
                    expectSelected(play, grid);
                })
                // A grid in a filling FormGroup has a height, and a click selects
                .story("SelectionSurvivesAFillingFormGroup", DataGridStories::fillingFormGroup)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(row(play, 0)).toBeInTheDocument());

                    // The grid occupies the group rather than collapsing to nothing ...
                    // Measured on the grid's own root (dataGridWidget)
                    final Value<Double> gridHeight = play.querySelector(GRID).height();
                    play.waitFor(() -> play.expect("the grid's height", gridHeight::get).toBeGreaterThan(50));

                    // ... a row is actually painted where its rect says it is ...
                    final Value<Element> firstRow = row(play, 0).closest("tr").element();
                    play.expect("the first row contains the element at its centre",
                                    () -> isPaintedAt(firstRow.get()))
                            .toBe(true);

                    // ... and therefore a click selects it
                    play.click(row(play, 0));
                    play.waitFor(() -> play.expect(row(play, 0).closest("tr"))
                            .toHaveClass(SELECTED_ROW_CLASS));
                })
                // Double-click a row
                .story("DoubleClick", DataGridStories::doubleClick)
                // Right-click a row
                .story("ContextMenu", DataGridStories::contextMenu)

                // Row height and virtualisation

                // Default mode: the whole page, each row as tall as its content
                .story("NaturalHeight", context -> sized(paged(noteGrid(false)), "320px", "900px"))
                // Natural height and multi-line: cells wrap
                .story("NaturalHeightMultiLine", context -> sized(paged(noteGrid(true)), "320px", "900px"))
                // Multi-line toggled at runtime
                .story("MultiLineToggled", context -> {
                    final MyDataGrid<Note> grid = noteGrid(false);
                    return toggleFrame(grid, sized(paged(grid), "280px", null));
                })
                // GWT has no virtual mode, so this is BasicGrid's grid in a 900px high frame
                .story("FixedRowsVirtual", context -> sized(paged(plainGrid(context)), "320px", "900px"))
                // GWT pages a long list (100 rows to a page, as Stroom's lists) rather than rendering
                // only the rows in view
                .story("FixedRowsManyRows", DataGridStories::manyRows)
                // Headerless and multi-column
                .story("HeaderlessMultiColumn", DataGridStories::headerless)
                // Stage 1: the fill columns share the surplus width by weight
                .story("ColumnFillWeights", context -> sized(paged(noteGrid(true)), "320px", "900px"))
                .withPlay(play -> {
                    final Query headers = play.querySelectorAll(HEADER_CELLS);
                    play.waitFor(() -> play.expect(headers).toHaveLength(3));
                    final Value<Double> name = headers.nth(0).width();
                    final Value<Double> value = headers.nth(1).width();
                    final Value<Double> description = headers.nth(2).width();
                    play.waitFor(() -> {
                        // Name is fixed at 300; the 900px frame leaves 200 spare over the 700
                        // declared, split 30/70: Value 200+60, Description 200+140
                        play.expect("Name", () -> Math.round(name.get())).toBe(300L);
                        play.expect("Value", () -> Math.round(value.get())).toBe(260L);
                        play.expect("Description", () -> Math.round(description.get())).toBe(340L);
                        play.expect("the total",
                                        () -> Math.round(name.get() + value.get() + description.get()))
                                .toBe(900L);
                    });
                })
                // Stage 2: no fill column, so the declared widths are kept
                .story("ColumnWidthsScaleWithoutFill", context -> {
                    final MyDataGrid<Note> grid = new MyDataGrid<>(new SimpleEventBus());
                    grid.setWidth("100%");
                    grid.setHeight("100%");
                    grid.addResizableColumn(DataGridUtil.textColumnBuilder(Note::getName).build(),
                            "Name", NOTE_COLUMN_WIDTH);
                    grid.addResizableColumn(DataGridUtil.textColumnBuilder(DataGridStories::noteValue).build(),
                            "Value", 200);
                    grid.addResizableColumn(DataGridUtil.textColumnBuilder(Note::getDescription).build(),
                            "Description", 200);
                    grid.setMultiLine(true);
                    grid.setRowData(DataGridRows.notes());
                    return sized(paged(grid), "320px", "900px");
                })
                .withPlay(play -> {
                    final Query headers = play.querySelectorAll(HEADER_CELLS);
                    play.waitFor(() -> play.expect(headers).toHaveLength(3));
                    final Value<Double> name = headers.nth(0).width();
                    final Value<Double> value = headers.nth(1).width();
                    final Value<Double> description = headers.nth(2).width();
                    play.waitFor(() -> {
                        // MyDataGrid sets the table's width to the sum of its columns'
                        // (resizeTableToFitColumns), so without a fill column the declared
                        // 300/200/200 are kept and the table stops short of the 900px frame
                        play.expect("Name", () -> Math.round(name.get())).toBe(300L);
                        play.expect("Value", () -> Math.round(value.get())).toBe(200L);
                        play.expect("Description", () -> Math.round(description.get())).toBe(200L);
                        play.expect("the total",
                                        () -> Math.round(name.get() + value.get() + description.get()))
                                .toBe(700L);
                        play.expect("Name / Value", () -> name.get() / value.get()).toBeCloseTo(1.5, 3);
                        play.expect("Description / Value", () -> description.get() / value.get())
                                .toBeCloseTo(1, 3);
                    });
                })
                // Declared widths that overflow the container are left alone
                .story("ColumnWidthsOverflowUnscaled", context -> sized(paged(noteGrid(true)), "320px", "400px"))
                .withPlay(play -> {
                    final Query headers = play.querySelectorAll(HEADER_CELLS);
                    play.waitFor(() -> play.expect(headers).toHaveLength(3));
                    final Value<Double> name = headers.nth(0).width();
                    final Value<Double> value = headers.nth(1).width();
                    final Value<Double> description = headers.nth(2).width();
                    play.waitFor(() -> {
                        // 700 declared in a 400px frame: no surplus for the fill to share
                        play.expect("Name", () -> Math.round(name.get())).toBe(300L);
                        play.expect("Value", () -> Math.round(value.get())).toBe(200L);
                        play.expect("Description", () -> Math.round(description.get())).toBe(200L);
                    });
                })
                // Resize and reorder: on by default in GWT
                .story("ResizeAndReorder", DataGridStories::resizeAndReorder)
                // Multi-line and virtual: GWT's grid wraps and renders the whole page
                .story("MultiLineSuspendsVirtual", context -> sized(paged(noteGrid(true)), "320px", "900px"))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(ROW)).toBeInTheDocument());
                    // Multi-line won: cells wrap ...
                    // MyDataGrid.setMultiLine adds `multiline` to the grid's root
                    play.expect(play.querySelector(GRID)).toHaveClass("multiline");
                    // ... and the whole page is rendered
                    play.expect(play.querySelectorAll(ROW)).toHaveLength(DataGridRows.notes().size());
                })
                // Row column spans: as MarkerListPresenter spans its summary rows
                .story("RowColumnSpans", DataGridStories::rowColumnSpans)
                .withPlay(play -> {
                    final Query rows = play.querySelectorAll(ROW);
                    play.waitFor(() -> play.expect("the number of rows", () -> rows.count().get())
                            .toBeGreaterThan(1));
                    final Query spanned = play.within(rows.nth(0)).querySelectorAll("td");
                    final Query normal = play.within(rows.nth(1)).querySelectorAll("td");
                    // GWT's setColSpan widens the first cell but still renders
                    // the cells it covers (pushed beyond the table's last column), so the spanning
                    // row has three cells, not one
                    play.expect(spanned).toHaveLength(3);
                    play.expect(normal).toHaveLength(3);
                    // ... and the span is the cell's colspan
                    play.expect(spanned.nth(0)).toHaveAttribute("colspan", "3");
                })
                // Toggling multi-line keeps the top visible row
                .story("VirtualMultiLineToggleKeepsPosition", DataGridStories::toggleKeepsPosition)
                .withPlay(play -> {
                    final Query targetRow = play.querySelector(ROW + "[__gwt_row=\"" + SCROLL_TO_ROW + "\"]");
                    play.waitFor(() -> play.expect(targetRow).toBeInTheDocument());
                    final Value<Element> rowElement = targetRow.element();
                    final Value<Element> scroller = Value.of("the grid's scroller",
                            () -> scroller(rowElement.get()));
                    // GWT renders every row, so scroll to row 100's own offset
                    play.run("scroll to row 100", () -> scroller.get().setScrollTop(rowOffset(rowElement.get())));
                    play.waitFor(() -> play.expect("row 100's offset from the top of the view",
                                    () -> Math.abs(rowOffset(rowElement.get()) - scroller.get().getScrollTop()))
                            .toBeLessThan(2));

                    play.click(play.getByRole("button", TOGGLE_MULTI_LINE));
                    // Row 100 is still the row at the top of the view, not row 0
                    play.waitFor(() -> {
                        play.expect(play.querySelector(GRID)).toHaveClass("multiline");
                        play.expect("row 100's offset from the top of the view",
                                        () -> Math.abs(rowOffset(rowElement.get()) - scroller.get().getScrollTop()))
                                .toBeLessThan(2);
                    });
                    play.expect("the scroll position", () -> scroller.get().getScrollTop()).toBeGreaterThan(0);
                })
                // A header carries a title only when the column declares one
                .story("HeaderTooltipOnlyWhenDeclared", DataGridStories::headerTooltip)
                .withPlay(play -> {
                    final Query headers = play.querySelectorAll(HEADER_CELLS);
                    play.waitFor(() -> play.expect(headers).toHaveLength(2));
                    // No declared tooltip: no title anywhere in the header
                    play.expect(headers.nth(0)).not().toHaveAttribute("title");
                    // GWT's tooltip is a title on a div in the header cell (HeadingBuilder.withToolTip)
                    play.expect(play.within(headers.nth(0)).querySelector("[title]")).toBeNull();
                    // A declared tooltip sits on the inner div, not the header cell
                    play.expect(headers.nth(1)).not().toHaveAttribute("title");
                    play.expect(play.within(headers.nth(1)).querySelector(":scope > div"))
                            .toHaveAttribute("title", "What they do here");
                });
    }

    // --------------------------------------------------------------------------------
    // Stories

    private static Widget sortableColumns(final StoryContext context) {
        final MyDataGrid<Person> grid = personGrid();
        final List<Person> rows = DataGridRows.people();
        final DataGridComparatorFactory<Person> comparators = addSortableColumns(grid);
        final Spy onSortChange = context.fn(ON_SORT_CHANGE);
        // As Stroom's FieldListPresenter: sort the rows on the grid's column sort list.
        // GWT toggles ascending/descending; there is no unsorted third state
        // and no Ctrl-click multi-sort
        grid.addColumnSortHandler(event -> {
            rows.sort(comparators.create());
            grid.setRowData(0, rows);
            final String field = event.getColumn() instanceof OrderByColumn<?, ?>
                    ? ((OrderByColumn<?, ?>) event.getColumn()).getField()
                    : null;
            onSortChange.call(field, event.isSortAscending());
        });
        grid.setRowData(rows);
        return sized(paged(grid), "320px", null);
    }

    private static Widget pager(final StoryContext context) {
        final MyDataGrid<Person> grid = new MyDataGrid<>(new SimpleEventBus(), PAGER_PAGE_SIZE);
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
        final MyDataGrid<Person> grid = new MyDataGrid<>(new SimpleEventBus(), MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        addPlainColumns(grid);
        final PagerViewImpl pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(grid);
        final ListDataProvider<Person> dataProvider = new ListDataProvider<>(DataGridRows.generatedPeople(MANY_ROWS));
        dataProvider.addDataDisplay(grid);
        return sized(pagerView.asWidget(), "320px", "900px");
    }

    /// The `RowSelection` and `SingleSelection` stories.
    private static Widget selectionStory(final StoryContext context,
                                         final boolean allowMultiSelect,
                                         final String prefix) {
        final MyDataGrid<Person> grid = personGrid();
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
        final MyDataGrid<Person> grid = personGrid();
        addPlainColumns(grid);
        grid.setRowData(DataGridRows.people());
        // Registered so the play can check it is never called: with no selection model there is
        // nothing to call it
        context.fn(ON_SELECTION_CHANGE);
        return column(sized(paged(grid), "300px", null), note("onSelectionChange fired: 0 time(s)"));
    }

    private static Widget controlledSelection(final StoryContext context) {
        final MyDataGrid<Person> grid = personGrid();
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

        // The host sets the selection model, as Stroom's presenters do
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
        final MyDataGrid<Person> grid = personGrid();
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
        final MyDataGrid<Person> grid = personGrid();
        addPlainColumns(grid);
        final List<Person> rows = DataGridRows.people();
        grid.setRowData(rows);
        final Label label = note("Last double-clicked: (none)");
        final Spy onRowDoubleClick = context.fn(ON_ROW_DOUBLE_CLICK);
        // Stroom's grids see a double-click as a double select of their
        // selection model, so the row is selected too
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

    private static Widget contextMenu(final StoryContext context) {
        final MyDataGrid<Person> grid = personGrid();
        addPlainColumns(grid);
        final List<Person> rows = DataGridRows.people();
        grid.setRowData(rows);
        final Label label = note("Last right-clicked: (none)");
        final Spy onRowContext = context.fn(ON_ROW_CONTEXT);
        // MyDataGrid has no row context callback; on a right-click it shows its own menu (Copy,
        // Export Table, AI) with a ShowMenuEvent, which isn't shown here. The story reads the row
        // from the event
        grid.addDomHandler(event -> {
            final int index = rowIndexOf(Element.as(event.getNativeEvent().getEventTarget()));
            if (index >= 0 && index < rows.size()) {
                onRowContext.call(index);
                label.setText("Last right-clicked: Row " + (index + 1) + " (" + rows.get(index).getName() + ")");
            }
        }, ContextMenuEvent.getType());
        return column(sized(paged(grid), "300px", null), label);
    }

    private static Widget resizeAndReorder(final StoryContext context) {
        final MyDataGrid<Person> grid = personGrid();
        addPlainColumns(grid);
        grid.setRowData(DataGridRows.people());
        final Spy onColumnReorder = context.fn(ON_COLUMN_REORDER);
        // MyDataGrid moves the column itself and tells its heading listener
        grid.setHeadingListener(new ReorderListener(onColumnReorder));
        return sized(paged(grid), "320px", "900px");
    }

    private static Widget rowColumnSpans(final StoryContext context) {
        final MyDataGrid<Note> grid = noteGrid(false);
        // As MarkerListPresenter.setData: span the row's first cell across all three columns
        grid.getRowElement(0).getCells().getItem(0).setColSpan(3);
        return sized(paged(grid), "320px", "900px");
    }

    private static Widget toggleKeepsPosition(final StoryContext context) {
        final MyDataGrid<Person> grid = new MyDataGrid<>(new SimpleEventBus(), TOGGLE_ROWS);
        grid.setWidth("100%");
        grid.setHeight("100%");
        addPlainColumns(grid);
        grid.setRowData(DataGridRows.generatedPeople(TOGGLE_ROWS));
        return toggleFrame(grid, sized(paged(grid), "280px", null));
    }

    /// As SteppingFilterPresenter's element chooser: a [MyCellTable] whose columns are added
    /// without headers.
    private static Widget headerless(final StoryContext context) {
        final MyCellTable<Note> table = new MyCellTable<>(Integer.MAX_VALUE);
        final Column<Note, String> name = DataGridUtil.textColumnBuilder(Note::getName).build();
        table.addColumn(name);
        table.setColumnWidth(name, NOTE_COLUMN_WIDTH, Unit.PX);
        table.addColumn(DataGridUtil.textColumnBuilder(DataGridStories::noteValue).build());
        table.addColumn(DataGridUtil.textColumnBuilder(Note::getDescription).build());
        table.setWidth("100%", true);
        table.setRowData(DataGridRows.notes());
        // Stroom's headerless grids are plain cell tables, not data grids, so
        // the Value and Description columns share the rest of the width rather than filling 30/70
        return sized(table, "320px", "900px");
    }

    private static Widget headerTooltip(final StoryContext context) {
        final MyDataGrid<Person> grid = personGrid();
        // A bare header, as addResizableColumn(column, String, width) builds it
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getName).build(), "Name", 200);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Person::getRole).build(),
                DataGridUtil.headingBuilder("Role").withToolTip("What they do here").build(),
                120);
        grid.setRowData(DataGridRows.people());
        return sized(paged(grid), "320px", "900px");
    }

    // --------------------------------------------------------------------------------
    // Grids and columns

    private static MyDataGrid<Person> personGrid() {
        final MyDataGrid<Person> grid = new MyDataGrid<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");
        return grid;
    }

    private static MyDataGrid<Person> plainGrid(final StoryContext context) {
        final MyDataGrid<Person> grid = personGrid();
        addPlainColumns(grid);
        grid.setRowData(DataGridRows.people());
        return grid;
    }

    /// Adds the plain columns. MyDataGrid only has a minimum width for its fill columns.
    private static void addPlainColumns(final MyDataGrid<Person> grid) {
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

    /// Adds the sortable columns, sorted as Stroom's list presenters sort, with a
    /// [DataGridComparatorFactory].
    private static DataGridComparatorFactory<Person> addSortableColumns(final MyDataGrid<Person> grid) {
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
        return DataGridUtil.comparatorFactoryBuilder(grid)
                .addField(NAME_FIELD, Person::getName)
                .addField(ROLE_FIELD, Person::getRole)
                .addField(AGE_FIELD, Person::getAge)
                .addField(ACTIVE_FIELD, Person::isActive)
                .build();
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

    /// The notes grid: Name fixed at 300, then Value and Description filling 30/70 with
    /// a minimum of 200, as `PropertyListPresenter`'s.
    private static MyDataGrid<Note> noteGrid(final boolean multiLine) {
        final MyDataGrid<Note> grid = new MyDataGrid<>(new SimpleEventBus());
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Note::getName).build(), "Name", NOTE_COLUMN_WIDTH);
        grid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(DataGridStories::noteValue).build(),
                "Value", 30, 200);
        grid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(Note::getDescription).build(),
                "Description", 70, 200);
        grid.setMultiLine(multiLine);
        grid.setRowData(DataGridRows.notes());
        return grid;
    }

    private static String noteValue(final Note note) {
        return String.valueOf(note.getId());
    }

    // --------------------------------------------------------------------------------
    // Layout

    /// The grid in Stroom's [PagerViewImpl], as Stroom's list presenters show their grids.
    private static Widget paged(final MyDataGrid<?> grid) {
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
    private static Widget toggleFrame(final MyDataGrid<?> grid, final Widget sizedGrid) {
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

    /// A cell of the row with the given index. The cell, not the
    /// row, as GWT's grid only handles events on its cells.
    private static Query row(final Play play, final int index) {
        return play.querySelector(ROW + "[__gwt_row=\"" + index + "\"] td");
    }

    /// A mouse down with Control, which is what the grid's selection reads.
    private static void ctrlClick(final Play play, final int index) {
        play.fireEvent().mouseDown(row(play, index), EventInit.create().ctrlKey().button(0));
    }

    /// A mouse down with Shift.
    private static void shiftClick(final Play play, final int index) {
        play.fireEvent().mouseDown(row(play, index), EventInit.create().shiftKey().button(0));
    }

    /// Waits until exactly the given rows are selected.
    private static void expectSelected(final Play play, final Value<Element> grid, final Integer... indices) {
        final List<Integer> expected = Arrays.asList(indices);
        play.waitFor(() -> play.expect("the selected rows", () -> selectedRows(grid.get())).toEqual(expected));
    }

    /// The indices of the rows with the selected row class, in order.
    private static List<Integer> selectedRows(final Element grid) {
        final List<Integer> selected = new ArrayList<>();
        final NodeList<Element> rows = grid.getElementsByTagName("tr");
        for (int i = 0; i < rows.getLength(); i++) {
            final Element row = rows.getItem(i);
            if (row.hasAttribute("__gwt_row") && row.hasClassName(SELECTED_ROW_CLASS)) {
                selected.add(Integer.valueOf(row.getAttribute("__gwt_row")));
            }
        }
        return selected;
    }

    /// The index of the grid row containing the element, or -1.
    private static int rowIndexOf(final Element element) {
        Element current = element;
        while (current != null) {
            if ("tr".equalsIgnoreCase(current.getTagName()) && current.hasAttribute("__gwt_row")) {
                return Integer.parseInt(current.getAttribute("__gwt_row"));
            }
            current = current.getParentElement();
        }
        return -1;
    }

    /// Whether the element at the row's centre (40px in) is in the row.
    private static boolean isPaintedAt(final Element row) {
        final double left = row.getAbsoluteLeft() - Document.get().getScrollLeft();
        final double top = row.getAbsoluteTop() - Document.get().getScrollTop();
        final Element at = elementFromPoint(left + Math.min(40, row.getOffsetWidth() / 2.0),
                top + row.getOffsetHeight() / 2.0);
        return at != null && row.isOrHasChild(at);
    }

    private static native Element elementFromPoint(double x, double y) /*-{
        return $doc.elementFromPoint(x, y);
    }-*/;

    /// The scrolling element of the grid containing the row: its first ancestor that scrolls.
    private static Element scroller(final Element row) {
        Element current = row.getParentElement();
        while (current != null && current.getScrollHeight() <= current.getClientHeight() + 1) {
            current = current.getParentElement();
        }
        return current;
    }

    /// The row's offset from the top of its table.
    private static int rowOffset(final Element row) {
        final Element table = row.getParentElement().getParentElement();
        return row.getAbsoluteTop() - table.getAbsoluteTop();
    }

    // --------------------------------------------------------------------------------

    /// Records each column moved, as `(from, to)`.
    private static final class ReorderListener implements HeadingListener {

        private final Spy onColumnReorder;

        private ReorderListener(final Spy onColumnReorder) {
            this.onColumnReorder = onColumnReorder;
        }

        @Override
        public void onMoveStart(final NativeEvent event,
                                final Supplier<Heading> heading) {
            // Nothing to do
        }

        @Override
        public void onMoveEnd(final NativeEvent event,
                              final Supplier<Heading> heading) {
            // Nothing to do
        }

        @Override
        public void onShowMenu(final NativeEvent event,
                               final Supplier<Heading> heading) {
            // Nothing to do
        }

        @Override
        public void moveColumn(final int fromIndex, final int toIndex) {
            onColumnReorder.call(fromIndex, toIndex);
        }

        @Override
        public void resizeColumn(final int colIndex, final int size) {
            // Nothing to do
        }
    }
}
