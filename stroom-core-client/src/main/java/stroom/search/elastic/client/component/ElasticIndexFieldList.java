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

package stroom.search.elastic.client.component;

import stroom.cell.tickbox.client.TickBoxState;
import stroom.search.elastic.client.model.ElasticFieldListModel.SortColumn;
import stroom.search.elastic.client.model.ElasticFieldRow;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.Layer;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.spinner.client.SmallBusySpinner;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// An Elastic index's Fields tab: filters by name and type above a tree of the index's fields
/// (multi-fields under the field they belong to) and virtual fields, a row each (its name, its
/// Stroom field type, whether it is enabled, which can be changed there, and any problem with it),
/// with their actions; below it, the selected field's Info and Settings panels. A double click on a
/// virtual field renames it. Its controller is [ElasticIndexFieldListController].
public class ElasticIndexFieldList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String DELETE_VIRTUAL_FIELD = "Delete Selected Virtual Field";

    @UiField
    QuickFilter nameFilter;
    @UiField
    DropDownBox typeFilter;
    @UiField
    SmallBusySpinner spinner;
    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addVirtualField;
    @UiField
    ToolbarButton rename;
    @UiField
    ToolbarButton remove;
    @UiField
    ToolbarButton refresh;
    @UiField
    ToolbarButton expandAll;
    @UiField
    ToolbarButton collapseAll;
    @UiField
    DataGrid<ElasticFieldRow> dataGrid;
    @UiField
    FlowPanel details;
    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainer layerContainer;
    @UiField
    Label noSelection;

    private final MultiSelectionModelImpl<ElasticFieldRow> selectionModel;
    private final String removeTitle;
    private Consumer<FieldAction> onAction = action -> {
    };
    private Consumer<ElasticFieldRow> onSelect = row -> {
    };
    private BiConsumer<ElasticFieldRow, Boolean> onEnabledTicked = (row, enabled) -> {
    };
    private BiConsumer<ElasticFieldRow, Boolean> onExpand = (row, expand) -> {
    };
    private BiConsumer<SortColumn, Boolean> onSort = (column, ascending) -> {
    };
    private Predicate<ElasticFieldRow> enabledEditable = row -> false;
    private Consumer<String> onNameFilterChange = text -> {
    };
    private Runnable onOpenTypeFilter = () -> {
    };
    private Consumer<TabData> onDetailsTabSelected = tab -> {
    };

    /// Makes an empty list.
    public ElasticIndexFieldList() {
        initWidget(BINDER.createAndBindUi(this));
        removeTitle = remove.getTitle();
        dataGrid.setPageSize(DataGrid.MASSIVE_LIST_PAGE_SIZE);
        // The list is read from the index, so the pager's own refresh isn't needed: Refresh reads it
        // again from the index's mapping
        pager.getRefreshButton().setVisible(false);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        dataGrid.setTreeRows(ElasticFieldRow::getExpander, (row, expand) -> onExpand.accept(row, expand));
        addColumns();
        dataGrid.sort(findColumn(SortColumn.NAME));

        selectionModel.addSelectionHandler(event -> {
            onSelect.accept(selectionModel.getSelected());
            // Only a virtual field can be renamed, which the controller checks
            if (event.getSelectionType().isDoubleSelect()) {
                onAction.accept(FieldAction.RENAME);
            }
        });
        dataGrid.addColumnSortHandler(event -> onSortChange());
        addVirtualField.setOnClick(() -> onAction.accept(FieldAction.ADD_VIRTUAL_FIELD));
        rename.setOnClick(() -> onAction.accept(FieldAction.RENAME));
        remove.setOnClick(() -> onAction.accept(FieldAction.REMOVE));
        refresh.setOnClick(() -> onAction.accept(FieldAction.REFRESH));
        expandAll.setOnClick(() -> onAction.accept(FieldAction.EXPAND_ALL));
        collapseAll.setOnClick(() -> onAction.accept(FieldAction.COLLAPSE_ALL));

        nameFilter.setOnChange(text -> onNameFilterChange.accept(NullSafe.string(text)));
        typeFilter.setOnOpen(() -> onOpenTypeFilter.run());
        tabBar.setTabPanel(layerContainer.getElement());
        tabBar.addSelectionHandler(event -> {
            if (event.getSelectedItem() != null) {
                onDetailsTabSelected.accept(event.getSelectedItem());
            }
        });
    }

    private void addColumns() {
        // Fields that can't be used as normal are faded, with the reason in the tooltip
        dataGrid.addAutoResizableColumn(
                DataGridUtil.htmlColumnBuilder(ElasticIndexFieldList::renderName)
                        .withSorting(SortColumn.NAME.name())
                        .enabledWhen(row -> row.isEnabled() && row.getStateNote() == null)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The full name of the field. Multi-fields, such as 'title.keyword', are shown " +
                                     "under the field they belong to. Faded fields can't be used as normal: they " +
                                     "are missing from all source indices, the source indices map them to " +
                                     "different types, or they are virtual fields hidden by a mapped field of the " +
                                     "same name. The Status column says which.")
                        .build(),
                350);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ElasticFieldRow::getTypeLabel)
                        .withSorting(SortColumn.TYPE.name())
                        .enabledWhen(ElasticFieldRow::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Field Type")
                        .withToolTip("The Stroom field type the Elasticsearch type maps to. 'Conflict' means the " +
                                     "source indices map the field to different types.")
                        .build(),
                120);
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(
                                (ElasticFieldRow row) -> TickBoxState.fromBoolean(row.isEnabled()))
                        .withSorting(SortColumn.ENABLED.name())
                        .enabledWhen(row -> !isReadOnly() && enabledEditable.test(row))
                        .withFieldUpdater((index, row, value) ->
                                onEnabledTicked.accept(row, TickBoxState.getAsBoolean(value)))
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the field is shown to users and can be used in queries. Disabling a " +
                                     "field also disables its multi-fields. Fields missing from all source indices " +
                                     "and fields with a type conflict can't be changed.")
                        .centerAligned()
                        .build(),
                80);
        // Only problems are shown, so a normal field, mapped or virtual, has no status
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder((ElasticFieldRow row) -> NullSafe.string(row.getStateNote()))
                        .withSorting(SortColumn.STATUS.name())
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("Why a field can't be used as normal: it is missing from all source indices, " +
                                     "the source indices map it to different types, or it is a virtual field " +
                                     "hidden by a mapped field of the same name.")
                        .build(),
                300);
    }

    private Column<ElasticFieldRow, ?> findColumn(final SortColumn sortColumn) {
        for (int i = 0; i < dataGrid.getColumnCount(); i++) {
            final Column<ElasticFieldRow, ?> column = dataGrid.getColumn(i);
            if (column instanceof final OrderByColumn<?, ?> orderByColumn
                && sortColumn.name().equals(orderByColumn.getField())) {
                return column;
            }
        }
        return null;
    }

    private static SafeHtml renderName(final ElasticFieldRow row) {
        final String note = row.getStateNote();
        return new SafeHtmlBuilder()
                .appendHtmlConstant("<div title=\"")
                .appendEscaped(note == null
                        ? row.getName()
                        : row.getName() + " - " + note)
                .appendHtmlConstant("\">")
                .appendEscaped(row.getName())
                .appendHtmlConstant("</div>")
                .toSafeHtml();
    }

    private void onSortChange() {
        final ColumnSortList sortList = dataGrid.getColumnSortList();
        if (sortList.size() > 0) {
            final ColumnSortInfo sortInfo = sortList.get(0);
            if (sortInfo.getColumn() instanceof final OrderByColumn<?, ?> orderByColumn) {
                onSort.accept(SortColumn.valueOf(orderByColumn.getField()), sortInfo.isAscending());
            }
        }
    }

    /// @param indexName The index's name, which names the table.
    public void setIndexName(final String indexName) {
        dataGrid.setTableName("Elastic Index '" + indexName + "' Fields");
    }

    /// Shows the rows, selecting one.
    ///
    /// @param rows     The rows, in order.
    /// @param selected The row to select, or null for none.
    public void setRows(final List<ElasticFieldRow> rows, final ElasticFieldRow selected) {
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
        if (selected != null && rows.contains(selected)) {
            selectionModel.setSelected(selected, new SelectionType(), false);
        } else {
            selectionModel.clear(false);
        }
    }

    /// Scrolls a row into view, e.g. a virtual field just added.
    ///
    /// @param index The row's position in the rows shown.
    public void scrollToRow(final int index) {
        if (index >= 0 && index < dataGrid.getVisibleItemCount()) {
            dataGrid.scrollRowIntoView(index);
        }
    }

    /// Enables the actions that can be taken now, and disables the rest.
    ///
    /// @param actions        The actions that can be taken.
    /// @param removeDeletes  Whether Remove deletes a virtual field, rather than a field's settings.
    public void setAvailableActions(final Set<FieldAction> actions, final boolean removeDeletes) {
        final Set<FieldAction> available = actions.isEmpty()
                ? EnumSet.noneOf(FieldAction.class)
                : EnumSet.copyOf(actions);
        addVirtualField.setEnabled(available.contains(FieldAction.ADD_VIRTUAL_FIELD));
        rename.setEnabled(available.contains(FieldAction.RENAME));
        remove.setEnabled(available.contains(FieldAction.REMOVE));
        remove.setTitle(removeDeletes
                ? DELETE_VIRTUAL_FIELD
                : removeTitle);
        remove.setLabel(removeDeletes
                ? "Delete"
                : "Remove");
        refresh.setEnabled(available.contains(FieldAction.REFRESH));
        expandAll.setEnabled(available.contains(FieldAction.EXPAND_ALL));
        collapseAll.setEnabled(available.contains(FieldAction.COLLAPSE_ALL));
    }

    /// @param onAction Given the action the user takes.
    public void setOnAction(final Consumer<FieldAction> onAction) {
        this.onAction = Objects.requireNonNull(onAction, "onAction");
    }

    /// @param onSelect Given the row the user selects, or null when none is.
    public void setOnSelect(final Consumer<ElasticFieldRow> onSelect) {
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    }

    /// @param enabledEditable Whether a row's Enabled can be changed (never while read only).
    public void setEnabledEditable(final Predicate<ElasticFieldRow> enabledEditable) {
        this.enabledEditable = Objects.requireNonNull(enabledEditable, "enabledEditable");
    }

    /// @param onEnabledTicked Given a row and whether the user ticked or unticked its Enabled.
    public void setOnEnabledTicked(final BiConsumer<ElasticFieldRow, Boolean> onEnabledTicked) {
        this.onEnabledTicked = Objects.requireNonNull(onEnabledTicked, "onEnabledTicked");
    }

    /// @param onExpand Given a row and whether the user expands (true) or collapses it.
    public void setOnExpand(final BiConsumer<ElasticFieldRow, Boolean> onExpand) {
        this.onExpand = Objects.requireNonNull(onExpand, "onExpand");
    }

    /// @param onSort Given the column the user sorts by and whether ascending.
    public void setOnSort(final BiConsumer<SortColumn, Boolean> onSort) {
        this.onSort = Objects.requireNonNull(onSort, "onSort");
    }

    /// @param onNameFilterChange Given the name filter's text when it changes.
    public void setOnNameFilterChange(final Consumer<String> onNameFilterChange) {
        this.onNameFilterChange = Objects.requireNonNull(onNameFilterChange, "onNameFilterChange");
    }

    /// @param onOpenTypeFilter Run when the user opens the type filter.
    public void setOnOpenTypeFilter(final Runnable onOpenTypeFilter) {
        this.onOpenTypeFilter = Objects.requireNonNull(onOpenTypeFilter, "onOpenTypeFilter");
    }

    /// @param text What the type filter shows, e.g. `All types` or the types ticked.
    public void setTypeFilterText(final String text) {
        typeFilter.setText(text);
    }

    /// @return The type filter's button, which its chooser opens below.
    public Element getTypeFilterElement() {
        return typeFilter.getButtonElement();
    }

    /// Clears the name filter, without the filter change callback being told.
    public void clearNameFilter() {
        nameFilter.setText("", false);
    }

    /// @return The task monitor that spins while the fields are read from the index.
    public TaskMonitorFactory getTaskMonitor() {
        return spinner;
    }

    /// @param visible Whether to show the selected field's panels, rather than a prompt to select one.
    public void setDetailsVisible(final boolean visible) {
        details.setVisible(visible);
        noSelection.setVisible(!visible);
    }

    /// @param tab A tab of the selected field's panels.
    public void addDetailsTab(final TabData tab) {
        tabBar.addTab(tab);
    }

    /// Shows one of the selected field's panels.
    ///
    /// @param tab   Its tab.
    /// @param layer The panel.
    public void showDetailsTab(final TabData tab, final Layer layer) {
        tabBar.selectTab(tab);
        layerContainer.show(layer);
    }

    /// @param onDetailsTabSelected Given the tab of the panel the user selects.
    public void setOnDetailsTabSelected(final Consumer<TabData> onDetailsTabSelected) {
        this.onDetailsTabSelected = Objects.requireNonNull(onDetailsTabSelected, "onDetailsTabSelected");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // The Enabled tick boxes are drawn editable or not
        dataGrid.redraw();
    }


    // --------------------------------------------------------------------------------


    /// What can be done in the field list.
    public enum FieldAction {
        /// Add a virtual field.
        ADD_VIRTUAL_FIELD,
        /// Rename the selected virtual field.
        RENAME,
        /// Remove the selected field's settings, or delete it if it is a virtual field.
        REMOVE,
        /// Read the fields again from the index's mapping.
        REFRESH,
        /// Expand every field that has multi-fields.
        EXPAND_ALL,
        /// Collapse every field.
        COLLAPSE_ALL
    }


    interface Binder extends UiBinder<Widget, ElasticIndexFieldList> {

    }
}
