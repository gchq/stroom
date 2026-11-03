/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.elastic.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.search.elastic.client.presenter.ElasticFieldListModel.SortColumn;
import stroom.search.elastic.client.presenter.ElasticFieldRow.Kind;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter.ElasticIndexFieldListView;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/// The Fields tab of an Elasticsearch index doc: a tree of the index's fields above a details panel with Info and
/// Settings tabs for the selected field.
///
/// All edits change only the client copy of the doc, which is marked dirty; nothing is sent to the server until the
/// doc is saved.
public class ElasticIndexFieldListPresenter
        extends DocPresenter<ElasticIndexFieldListView, ElasticIndexDoc>
        implements ElasticIndexFieldListUiHandlers, ElasticFieldSettingsListener {

    /// The details tab showing how the field is mapped and statistics about its values.
    public static final TabData INFO_TAB = new TabDataImpl("Info");
    /// The details tab for editing the field's settings.
    public static final TabData SETTINGS_TAB = new TabDataImpl("Settings");

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);

    private static final String ADD_VIRTUAL_FIELD = "Add virtual field";
    private static final String REMOVE_SETTINGS = "Remove settings";
    private static final String DELETE_VIRTUAL_FIELD = "Delete virtual field";

    private final MyDataGrid<ElasticFieldRow> dataGrid;
    private final MultiSelectionModelImpl<ElasticFieldRow> selectionModel;
    private final PagerView pagerView;
    private final ButtonView addVirtualFieldButton;
    private final ButtonView removeButton;
    private final ButtonView refreshButton;
    private final ButtonView expandAllButton;
    private final ButtonView collapseAllButton;
    private final RestFactory restFactory;
    private final ElasticFieldInfoPresenter infoPresenter;
    private final ElasticFieldSettingsPresenter settingsPresenter;
    private final ElasticFieldListModel model = new ElasticFieldListModel();
    private final Set<String> expandedNames = new HashSet<>();

    private Column<ElasticFieldRow, Expander> expanderColumn;
    private String nameFilter = "";
    private List<String> typeFilter = new ArrayList<>();
    private SortColumn sortColumn = SortColumn.NAME;
    private boolean sortAscending = true;
    private String selectedName;
    private Kind selectedKind;
    private TabData selectedTab = INFO_TAB;
    private Supplier<ElasticIndexDoc> currentDocSupplier;

    @Inject
    public ElasticIndexFieldListPresenter(final EventBus eventBus,
                                          final ElasticIndexFieldListView view,
                                          final PagerView pagerView,
                                          final RestFactory restFactory,
                                          final ElasticFieldInfoPresenter infoPresenter,
                                          final ElasticFieldSettingsPresenter settingsPresenter) {
        super(eventBus, view);
        this.pagerView = pagerView;
        this.restFactory = restFactory;
        this.infoPresenter = infoPresenter;
        this.settingsPresenter = settingsPresenter;

        dataGrid = new MyDataGrid<>(this, MyDataGrid.MASSIVE_LIST_PAGE_SIZE);
        dataGrid.setTableName("Elastic Index Fields");
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        addVirtualFieldButton = pagerView.addButton(SvgPresets.ADD.title(ADD_VIRTUAL_FIELD));
        removeButton = pagerView.addButton(SvgPresets.DELETE.title(REMOVE_SETTINGS));
        refreshButton = pagerView.addButton(SvgPresets.REFRESH_BLUE.title(
                "Refresh the field list from the index mapping"));
        expandAllButton = pagerView.addButton(SvgPresets.EXPAND_ALL.title("Expand all"));
        collapseAllButton = pagerView.addButton(SvgPresets.COLLAPSE_ALL.title("Collapse all"));

        addColumns();
        dataGrid.sort(findColumn(SortColumn.NAME));

        settingsPresenter.setListener(this);
        settingsPresenter.setModel(model);

        view.setUiHandlers(this);
        view.setDataGridView(pagerView);
        view.addDetailsTab(INFO_TAB);
        view.addDetailsTab(SETTINGS_TAB);
        view.setInfoView(infoPresenter.getView());
        view.setSettingsView(settingsPresenter.getView());
        view.selectDetailsTab(INFO_TAB);
        view.setDetailsVisible(false);
        updateButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> onRowSelected()));
        registerHandler(dataGrid.addColumnSortHandler(event -> onSort()));
        registerHandler(addVirtualFieldButton.addClickHandler(event -> addVirtualField()));
        registerHandler(removeButton.addClickHandler(event -> removeSelected()));
        registerHandler(refreshButton.addClickHandler(event -> refreshFields()));
        registerHandler(expandAllButton.addClickHandler(event -> {
            expandedNames.addAll(model.getExpandableNames());
            refreshRows();
        }));
        registerHandler(collapseAllButton.addClickHandler(event -> {
            expandedNames.clear();
            refreshRows();
        }));
    }

    /// Sets the supplier of the doc as currently edited across all of its tabs, so that refreshing the field list
    /// uses any unsaved cluster or index name.
    ///
    /// @param currentDocSupplier supplies the doc with all unsaved edits applied
    public void setCurrentDocSupplier(final Supplier<ElasticIndexDoc> currentDocSupplier) {
        this.currentDocSupplier = currentDocSupplier;
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticIndexDoc document, final boolean readOnly) {
        dataGrid.setTableName("Elastic Index '" + docRef.getName() + "' Fields");
        model.read(document.getFields(), document.getFieldSettings());
        model.setMandatoryFieldNames(document.getMandatoryFieldNames());
        infoPresenter.setIndex(docRef);
        settingsPresenter.setIndex(docRef);
        settingsPresenter.setReadOnly(readOnly);
        refreshRows();
        showSelectedField();
        updateButtons();
    }

    @Override
    protected ElasticIndexDoc onWrite(final ElasticIndexDoc document) {
        return document.copy()
                .fieldSettings(model.getSettingsList())
                .build();
    }

    @Override
    public void onNameFilterChange(final String text) {
        nameFilter = NullSafe.string(text);
        refreshRows();
    }

    @Override
    public void onTypeFilterChange(final List<String> types) {
        typeFilter = new ArrayList<>(NullSafe.list(types));
        refreshRows();
    }

    @Override
    public void onDetailsTabSelected(final TabData tab) {
        selectedTab = tab;
        getView().selectDetailsTab(tab);
        infoPresenter.setActive(tab == INFO_TAB);
    }

    @Override
    public void onFieldSettingsChanged(final String fieldName) {
        refreshRows();
        updateButtons();
        onChange();
    }

    @Override
    public void onVirtualFieldRenamed(final String oldName, final String newName) {
        if (oldName != null && oldName.equals(selectedName)) {
            selectedName = newName;
            // A renamed field can't clash with a mapped field, so is now a plain virtual field
            selectedKind = Kind.VIRTUAL;
            infoPresenter.setField(getSelectedRow(), this::getSelectedVirtualGroups);
        }
        onFieldSettingsChanged(newName);
    }

    private void addColumns() {
        expanderColumn = DataGridUtil.expanderColumn(ElasticFieldRow::getExpander);
        expanderColumn.setFieldUpdater((index, row, value) -> {
            if (value != null && !value.isLeaf()) {
                if (value.isExpanded()) {
                    expandedNames.remove(row.getName());
                } else {
                    expandedNames.add(row.getName());
                }
                refreshRows();
            }
        });
        dataGrid.addColumn(expanderColumn, "", 0);

        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(ElasticFieldRow::getName)
                        .withSorting(SortColumn.NAME.name())
                        .enabledWhen(ElasticFieldRow::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The full name of the field. Multi-fields, such as 'title.keyword', are shown " +
                                     "under the field they belong to.")
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
                DataGridUtil.updatableTickBoxColumnBuilder(this::getEnabledState)
                        .withSorting(SortColumn.ENABLED.name())
                        .enabledWhen(this::isEnabledEditable)
                        .withFieldUpdater((index, row, value) -> onEnabledTicked(row, value))
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the field is shown to users and can be used in queries. Disabling a " +
                                     "field also disables its multi-fields. Fields missing from all source indices " +
                                     "and fields with a type conflict can't be changed.")
                        .centerAligned()
                        .build(),
                80);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(ElasticFieldRow::getStatus)
                        .withSorting(SortColumn.STATUS.name())
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("'Configured': the field has settings other than whether it is enabled. " +
                                     "'Virtual': a field defined in Stroom whose values are named queries. " +
                                     "'Missing from all source indices': settings for a field no source index " +
                                     "maps. 'Type conflict': source indices map the field to different types. " +
                                     "'Name clashes with a mapped field': a virtual field hidden by a mapped field " +
                                     "of the same name.")
                        .build(),
                250);
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

    private TickBoxState getEnabledState(final ElasticFieldRow row) {
        return TickBoxState.fromBoolean(row.isEnabled());
    }

    private boolean isEnabledEditable(final ElasticFieldRow row) {
        return !isReadOnly() && row.isEnabledEditable();
    }

    private void onEnabledTicked(final ElasticFieldRow row, final TickBoxState value) {
        if (isEnabledEditable(row)) {
            model.setEnabled(row.getName(), TickBoxState.getAsBoolean(value));
            refreshRows();
            showSelectedField();
            updateButtons();
            onChange();
        } else {
            // Redraw to put back the tick box state the cell has already changed
            refreshRows();
        }
    }

    private void onSort() {
        final ColumnSortList sortList = dataGrid.getColumnSortList();
        if (sortList.size() > 0) {
            final ColumnSortInfo sortInfo = sortList.get(0);
            if (sortInfo.getColumn() instanceof final OrderByColumn<?, ?> orderByColumn) {
                sortColumn = SortColumn.valueOf(orderByColumn.getField());
                sortAscending = sortInfo.isAscending();
                refreshRows();
            }
        }
    }

    private void onRowSelected() {
        final ElasticFieldRow row = selectionModel.getSelected();
        final String name = NullSafe.get(row, ElasticFieldRow::getName);
        final Kind kind = NullSafe.get(row, ElasticFieldRow::getKind);
        if (row == null) {
            selectedName = null;
            selectedKind = null;
            showSelectedField();
        } else if (!name.equals(selectedName) || kind != selectedKind) {
            selectedName = name;
            selectedKind = kind;
            showSelectedField();
        }
        updateButtons();
    }

    private void refreshRows() {
        final List<ElasticFieldRow> rows = model.buildRows(nameFilter,
                typeFilter,
                sortColumn,
                sortAscending,
                expandedNames);

        // As TreeRowHandler does, hide the expander column unless some row can be expanded
        int maxDepth = -1;
        boolean hasParent = false;
        for (final ElasticFieldRow row : rows) {
            if (row.getExpander() != null) {
                maxDepth = Math.max(maxDepth, row.getExpander().getDepth());
                hasParent = hasParent || !row.getExpander().isLeaf();
            }
        }
        if (!hasParent) {
            maxDepth = -1;
        }
        dataGrid.setColumnWidth(expanderColumn, ExpanderCell.getColumnWidth(maxDepth), Unit.PX);
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);

        final ElasticFieldRow selectedRow = getSelectedRow();
        if (selectedRow != null && rows.contains(selectedRow)) {
            selectionModel.setSelected(selectedRow, new SelectionType(), false);
        } else {
            selectionModel.clear(false);
        }

        getView().setTypeFilterOptions(model.getTypeFilterOptions());
    }

    private ElasticFieldRow getSelectedRow() {
        return model.findRow(selectedName, selectedKind);
    }

    private void showSelectedField() {
        ElasticFieldRow row = getSelectedRow();
        if (row == null && selectedName != null) {
            // The field may have changed kind, e.g. a mapped field that has gone from the mapping
            row = model.findRow(selectedName);
            selectedKind = NullSafe.get(row, ElasticFieldRow::getKind);
            if (row == null) {
                selectedName = null;
            }
        }

        getView().setDetailsVisible(row != null);
        settingsPresenter.setField(row);
        infoPresenter.setField(row, this::getSelectedVirtualGroups);
        getView().selectDetailsTab(selectedTab);
        infoPresenter.setActive(row != null && selectedTab == INFO_TAB);
    }

    private List<ElasticVirtualFieldGroup> getSelectedVirtualGroups() {
        return NullSafe.get(model.findSettings(selectedName), ElasticIndexFieldSettings::getVirtualGroups);
    }

    private void updateButtons() {
        final boolean readOnly = isReadOnly();
        final ElasticFieldRow row = getSelectedRow();
        addVirtualFieldButton.setEnabled(!readOnly);
        refreshButton.setEnabled(true);

        if (row != null && row.isVirtual()) {
            removeButton.setTitle(DELETE_VIRTUAL_FIELD);
            removeButton.setEnabled(!readOnly);
        } else {
            removeButton.setTitle(REMOVE_SETTINGS);
            removeButton.setEnabled(!readOnly && row != null && model.hasSettings(row.getName()));
        }
    }

    private void addVirtualField() {
        if (!isReadOnly()) {
            final String name = model.addVirtualField();
            selectedName = name;
            selectedKind = Kind.VIRTUAL;
            selectedTab = SETTINGS_TAB;
            refreshRows();
            showSelectedField();
            updateButtons();
            onChange();
            settingsPresenter.focusVirtualName();
        }
    }

    private void removeSelected() {
        final ElasticFieldRow row = getSelectedRow();
        if (isReadOnly() || row == null) {
            return;
        }
        if (row.isVirtual()) {
            ConfirmEvent.fire(this,
                    "Are you sure you want to delete the virtual field '" + row.getName() + "'?",
                    ok -> {
                        if (ok) {
                            removeSettings(row);
                        }
                    });
        } else {
            removeSettings(row);
        }
    }

    private void removeSettings(final ElasticFieldRow row) {
        model.removeSettings(row.getName());
        if (row.getKind() != Kind.MAPPED) {
            // Virtual fields and missing fields only exist through their settings
            selectedName = null;
            selectedKind = null;
        }
        refreshRows();
        showSelectedField();
        updateButtons();
        onChange();
    }

    private void refreshFields() {
        final ElasticIndexDoc currentDoc = currentDocSupplier != null
                ? currentDocSupplier.get()
                : write(getEntity());
        if (currentDoc == null) {
            return;
        }
        restFactory
                .create(ELASTIC_INDEX_RESOURCE)
                .method(resource -> resource.fetchFields(currentDoc))
                .onSuccess(fields -> {
                    final List<ElasticIndexField> fieldList = NullSafe.list(fields);
                    model.setFields(fieldList);
                    refreshRows();
                    showSelectedField();
                    updateButtons();
                })
                .taskMonitorFactory(pagerView)
                .exec();
    }

    /// The view of the Elasticsearch index Fields tab.
    public interface ElasticIndexFieldListView extends View, HasUiHandlers<ElasticIndexFieldListUiHandlers> {

        /// @param view the view holding the field list and its toolbar
        void setDataGridView(View view);

        /// @param options the values the type filter can match
        void setTypeFilterOptions(List<String> options);

        /// @param tab a tab to add to the details panel
        void addDetailsTab(TabData tab);

        /// @param tab the details tab to show
        void selectDetailsTab(TabData tab);

        /// @param view the content of the Info tab
        void setInfoView(View view);

        /// @param view the content of the Settings tab
        void setSettingsView(View view);

        /// @param visible `true` to show the details tabs, `false` to show a prompt to select a field
        void setDetailsVisible(boolean visible);
    }
}
