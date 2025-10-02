/*
 * Copyright 2016 Crown Copyright
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

package stroom.query.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationFields;
import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.input.FilterableTable;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.table.ColumnFilterPresenter;
import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.DownloadPresenter;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.dashboard.client.table.FormatPresenter;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.TableCollapseButton;
import stroom.dashboard.client.table.TableExpandButton;
import stroom.dashboard.client.table.TableRowStyles;
import stroom.dashboard.client.table.TableUpdateEvent;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.data.grid.client.MessagePanel;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.presenter.TableRow.Cell;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.query.shared.QueryTablePreferences;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class QueryResultTablePresenter
        extends MyPresenterWidget<QueryResultTableView>
        implements ResultComponent, HasComponentSelection, HasDirtyHandlers, HasHandlers, FilterableTable {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private int expanderColumnWidth;
    private final com.google.gwt.user.cellview.client.Column<TableRow, Expander> expanderColumn;

    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final PagerView pagerView;
    private final MyDataGrid<TableRow> dataGrid;
    private final MultiSelectionModelImpl<TableRow> selectionModel;

    private QueryModel currentSearchModel;
    private boolean ignoreRangeChange;
    private boolean pause;

    private OffsetRange requestedRange = OffsetRange.ZERO_100;
    private GroupSelection groupSelection = new GroupSelection();

    private final TableExpandButton expandButton;
    private final TableCollapseButton collapseButton;

    private final ButtonView downloadButton;
    private final DownloadPresenter downloadPresenter;
    private final AnnotationManager annotationManager;
    private final InlineSvgToggleButton valueFilterButton;
    private final ButtonView annotateButton;
    private final EventBus eventBus;

    private Supplier<QueryTablePreferences> queryTablePreferencesSupplier;
    private Consumer<QueryTablePreferences> queryTablePreferencesConsumer;
    private final QueryTableColumnsManager columnsManager;
    private final List<com.google.gwt.user.cellview.client.Column<TableRow, ?>> existingColumns = new ArrayList<>();
    private List<Column> currentColumns = Collections.emptyList();
    private QueryResultVisPresenter queryResultVisPresenter;
    private ExpressionOperator currentSelectionFilter;
    private final TableRowStyles rowStyles;
    private DashboardContext dashboardContext;
    private DocRef currentDataSource;
    private int maxDepth;

    private boolean tableIsVisible = true;
    private boolean annotationChanged;

    @Inject
    public QueryResultTablePresenter(final EventBus eventBus,
                                     final RestFactory restFactory,
                                     final LocationManager locationManager,
                                     final QueryResultTableView tableView,
                                     final PagerView pagerView,
                                     final DownloadPresenter downloadPresenter,
                                     final AnnotationManager annotationManager,
                                     final ClientSecurityContext securityContext,
                                     final FormatPresenter formatPresenter,
                                     final Provider<RulesPresenter> rulesPresenterProvider,
                                     final ColumnFilterPresenter columnFilterPresenter,
                                     final ColumnValuesFilterPresenter columnValuesFilterPresenter,
                                     final UserPreferencesManager userPreferencesManager) {
        super(eventBus, tableView);
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        this.downloadPresenter = downloadPresenter;
        this.annotationManager = annotationManager;
        rowStyles = new TableRowStyles(userPreferencesManager);
        annotationManager.setTaskMonitorFactory(this);

        this.pagerView = pagerView;
        this.dataGrid = new MyDataGrid<>(this);
        dataGrid.addStyleName("TablePresenter");
        dataGrid.setRowStyles(rowStyles);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        tableView.setTableView(pagerView);

        columnsManager = new QueryTableColumnsManager(eventBus,
                this,
                formatPresenter,
                rulesPresenterProvider,
                columnFilterPresenter,
                columnValuesFilterPresenter);
        dataGrid.setHeadingListener(columnsManager);
        columnsManager.setColumnsStartIndex(1);

        // Expander column.
        expanderColumn = new com.google.gwt.user.cellview.client.Column<TableRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TableRow row) {
                if (row == null) {
                    return null;
                }
                return row.getExpander();
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            toggle(row);
            refresh();
        });

        pagerView.getRefreshButton().setAllowPause(true);

        expandButton = TableExpandButton.create();
        pagerView.addButton(expandButton);

        collapseButton = TableCollapseButton.create();
        pagerView.addButton(collapseButton);

        // Download
        downloadButton = pagerView.addButton(SvgPresets.DOWNLOAD);
        downloadButton.setVisible(securityContext.hasAppPermission(AppPermission.DOWNLOAD_SEARCH_RESULTS_PERMISSION));

        // Filter values
        valueFilterButton = new InlineSvgToggleButton();
        valueFilterButton.setSvg(SvgImage.FILTER);
        valueFilterButton.setTitle("Filter Values");
        pagerView.addButton(valueFilterButton);

        // Annotate
        annotateButton = pagerView.addButton(SvgPresets.ANNOTATE);
        annotateButton.setVisible(annotationManager.isEnabled());

        annotationManager.setColumnSupplier(() -> currentColumns);
    }

    public DashboardContext getDashboardContext() {
        return dashboardContext;
    }

    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }

    private void toggle(final TableRow row) {
        if (groupSelection.isGroupOpen(row.getGroupKey(), row.getDepth())) {
            groupSelection.close(row.getGroupKey());
        } else {
            groupSelection.open(row.getGroupKey());
        }
    }

//    public void setRequestedRange(final OffsetRange requestedRange) {
//        this.requestedRange = requestedRange;
//    }

    @Override
    public GroupSelection getGroupSelection() {
        return groupSelection;
    }

    @Override
    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addRangeChangeHandler(event -> {
            final com.google.gwt.view.client.Range range = event.getNewRange();
            requestedRange = new OffsetRange(range.getStart(), range.getLength());
            if (!ignoreRangeChange) {
                refresh();
            }
        }));
        registerHandler(dataGrid.addHyperlinkHandler(event -> HyperlinkEvent
                .fire(this, event.getHyperlink(), event.getTaskMonitorFactory(), getDashboardContext())));

//        registerHandler(dataGrid.addColumnSortHandler(event -> {
//            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
//                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
//                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
//                refresh();
//            }
//        }));

        registerHandler(pagerView.getRefreshButton().addClickHandler(event -> setPause(!pause, true)));

        registerHandler(expandButton.addClickHandler(event -> {
            groupSelection = expandButton.expand(groupSelection, maxDepth);
            refresh();
        }));

        registerHandler(collapseButton.addClickHandler(event -> {
            groupSelection = collapseButton.collapse(groupSelection);
            refresh();
        }));

        registerHandler(downloadButton.addClickHandler(event -> {
            if (currentSearchModel != null) {
                if (currentSearchModel.isSearching()) {
                    ConfirmEvent.fire(QueryResultTablePresenter.this,
                            "Search still in progress. Do you want to download the current results? " +
                            "Note that these may be incomplete.",
                            ok -> {
                                if (ok) {
                                    download();
                                }
                            });
                } else {
                    download();
                }
            }
        }));

        registerHandler(valueFilterButton.addClickHandler(event -> toggleApplyValueFilters()));

        registerHandler(annotateButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                annotationManager.showAnnotationMenu(event.getNativeEvent(),
                        selectionModel.getSelectedItems());
            }
        }));

        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                final Set<Long> annotationIdList = annotationManager.getAnnotationIds(
                        selectionModel.getSelectedItems());
                if (annotationIdList.size() == 1) {
                    annotationManager.editAnnotation(annotationIdList.iterator().next());
                }
            }
        }));

        registerHandler(getEventBus().addHandler(AnnotationChangeEvent.getType(), e ->
                onAnnotationChange()));
    }

    private void onAnnotationChange() {
        try {
            annotationChanged = true;
            if (tableIsVisible) {
                annotationChanged = false;
                final DocRef dataSource = currentDataSource;
                if (dataSource != null &&
                    AnnotationFields.ANNOTATIONS_PSEUDO_DOC_REF.getType().equals(dataSource.getType())) {
                    // If this is an annotations data source then force a new search.
                    forceNewSearch();
                } else if (getCurrentColumns()
                        .stream()
                        .anyMatch(col ->
                                NullSafe.getOrElse(col, Column::getExpression, "")
                                        .contains(AnnotationDecorationFields.ANNOTATION_FIELD_PREFIX))) {
                    // If the table contains annotations fields then just refresh to redecorate.
                    refresh();
                }
            }
        } catch (final RuntimeException e) {
            GWT.log(e.getMessage());
        }
    }

    public void toggleApplyValueFilters() {
        final QueryTablePreferences queryTablePreferences = getQueryTablePreferences();
        final boolean applyValueFilters = !queryTablePreferences.applyValueFilters();
        setQueryTablePreferences(queryTablePreferences.copy().applyValueFilters(applyValueFilters).build());
        setDirty(true);
        refresh();
        setApplyValueFilters(applyValueFilters);
    }

    private void setApplyValueFilters(final boolean applyValueFilters) {
        valueFilterButton.setState(applyValueFilters);
        if (applyValueFilters) {
            dataGrid.addStyleName("applyValueFilters");
        } else {
            dataGrid.removeStyleName("applyValueFilters");
        }
    }

    public void changeSettings() {
        final QueryTablePreferences queryTablePreferences = getQueryTablePreferences();
        updatePageSize(queryTablePreferences);

        // Update styles and re-render
        rowStyles.setConditionalFormattingRules(queryTablePreferences.getConditionalFormattingRules());
    }

    private void updatePageSize(final QueryTablePreferences queryTablePreferences) {
        final int start = dataGrid.getVisibleRange().getStart();
        dataGrid.setVisibleRange(new Range(
                start,
                NullSafe.getOrElse(queryTablePreferences, QueryTablePreferences::getPageSize, 100)));
    }

    private void setPause(final boolean pause,
                          final boolean refresh) {
        // If currently paused then refresh if we are allowed.
        if (refresh && this.pause) {
            refresh();
        }
        this.pause = pause;
        pagerView.getRefreshButton().setPaused(this.pause);
    }

    public void setFocused(final boolean focused) {
        dataGrid.setFocused(focused);
    }

    public void forceNewSearch() {
        if (currentSearchModel != null) {
            pagerView.getRefreshButton().setRefreshing(true);
            currentSearchModel.forceNewSearch(QueryModel.TABLE_COMPONENT_ID, result -> {
                try {
                    if (result != null) {
                        setDataInternal(result);
                    }
                } catch (final Exception e) {
                    GWT.log(e.getMessage());
                } finally {
                    pagerView.getRefreshButton().setRefreshing(currentSearchModel.isSearching());
                }
            });
        } else {
            RefreshRequestEvent.fire(this);
        }
    }

    public void refresh() {
        if (currentSearchModel != null) {
            pagerView.getRefreshButton().setRefreshing(true);
            currentSearchModel.refresh(QueryModel.TABLE_COMPONENT_ID, result -> {
                try {
                    if (result != null) {
                        setDataInternal(result);
                    }
                } catch (final Exception e) {
                    GWT.log(e.getMessage());
                }
                pagerView.getRefreshButton().setRefreshing(currentSearchModel.isSearching());
            });
        } else {
            RefreshRequestEvent.fire(this);
        }
    }

    public void onColumnFilterChange() {
        reset();
        refresh();
        final QueryResultVisPresenter queryResultVisPresenter = this.queryResultVisPresenter;
        if (queryResultVisPresenter != null) {
            queryResultVisPresenter.refresh();
        }
    }


//    @Override
//    protected void addColumns(final boolean allowSelectAll) {
//        addSelectedColumn(allowSelectAll);
//
//        addInfoColumn();
//
//        addCreatedColumn();
//        addStreamTypeColumn();
//        addFeedColumn();
//        addPipelineColumn();
//
//        addRightAlignedAttributeColumn(
//                "Raw",
//                MetaFields.RAW_SIZE,
//                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Disk",
//                MetaFields.FILE_SIZE,
//                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Read",
//                MetaFields.REC_READ,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Write",
//                MetaFields.REC_WRITE,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Fatal",
//                MetaFields.REC_FATAL,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addRightAlignedAttributeColumn(
//                "Error",
//                MetaFields.REC_ERROR,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addRightAlignedAttributeColumn(
//                "Warn",
//                MetaFields.REC_WARN,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addRightAlignedAttributeColumn(
//                "Info",
//                MetaFields.REC_INFO,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addAttributeColumn(
//                "Retention",
//                DataRetentionFields.RETENTION_AGE_FIELD,
//                Function.identity(),
//                ColumnSizeConstants.SMALL_COL);
//
//        addEndColumn();
//    }


//    @Override
//    public ComponentResultRequest getResultRequest(final boolean ignorePause) {
//        return null;
//    }

    @Override
    public void reset() {
        selectionModel.clear(true);

        final long length = Math.max(1, requestedRange.getLength());

        // Reset the data grid paging.
        if (dataGrid.getVisibleRange().getStart() > 0) {
            dataGrid.setVisibleRange(0, (int) length);
        }

        requestedRange = new OffsetRange(0L, length);
    }

    @Override
    public void startSearch() {
//        final TableSettings tableSettings = getTableSettings()
//                .copy()
//                .buildTableSettings();
//        tableResultRequest = tableResultRequest
//                .copy()
//                .tableSettings(tableSettings)
//                .build();

        setPause(false, false);
        pagerView.getRefreshButton().setRefreshing(true);
    }

    @Override
    public void endSearch() {
        pagerView.getRefreshButton().setRefreshing(false);
    }

    @Override
    public void setData(final Result componentResult) {
        GWT.log("setData");

        if (!pause) {
            setDataInternal(componentResult);
        }
    }

    private void setDataInternal(final Result componentResult) {
        GWT.log("setDataInternal");
        final MessagePanel messagePanel = pagerView.getMessagePanel();
        messagePanel.hideMessage();

        final QueryTablePreferences queryTablePreferences = getQueryTablePreferences();
        ignoreRangeChange = true;
        try {
            if (componentResult != null) {
                // Don't refresh the table unless the results have changed.
                final TableResult tableResult = (TableResult) componentResult;

                // Get result columns.
                List<Column> columns = NullSafe.list(tableResult.getColumns());
                if (queryTablePreferences != null && queryTablePreferences.getColumns() != null) {

                    // Create a map of the result columns by id and remember the order that the result has them in.
                    final Map<String, ColAndPosition> mapped = new HashMap<>();
                    int position = 0;
                    for (final Column column : columns) {
                        mapped.put(column.getId(), new ColAndPosition(position++, column));
                    }

                    final List<ColAndPosition> fixedColumns = new ArrayList<>(columns.size());

                    // Add columns based on preference order and add preferred widths.
                    for (final Column prefColumn : queryTablePreferences.getColumns()) {
                        final ColAndPosition colAndPosition = mapped.remove(prefColumn.getId());
                        if (colAndPosition != null) {
                            final Column col = colAndPosition.column;
                            fixedColumns.add(new ColAndPosition(
                                    colAndPosition.position,
                                    col
                                            .copy()
                                            .width(prefColumn.getWidth())
                                            .visible(prefColumn.isVisible())
                                            .format(prefColumn.getFormat())
                                            .build()));
                        }
                    }

                    // Add in columns that we didn't have a preference for in the most sensible position.
                    mapped.values().forEach(colAndPosition -> {
                        int insertPos = 0;
                        for (int i = 0; i < fixedColumns.size(); i++) {
                            if (fixedColumns.get(i).position < colAndPosition.position) {
                                insertPos = i + 1;
                            } else {
                                break;
                            }
                        }

                        fixedColumns.add(insertPos, colAndPosition);
                    });

                    columns = fixedColumns
                            .stream()
                            .map(c -> c.column)
                            .collect(Collectors.toList());


//                    // Adjust result columns to match UI preferences.
//                    final Map<String, Column> prefs = queryTablePreferences
//                            .getColumns()
//                            .stream()
//                            .collect(Collectors.toMap(Column::getId, c -> c));
//
//                    final List<Column> fixedColumns = new ArrayList<>(columns.size());
//                    columns.forEach(column -> {
//                        final Column pref = prefs.get(column.getId());
//                        if (pref != null) {
//                            fixedColumns.add(column.copy().width(pref.getWidth()).build());
//                        } else {
//                            fixedColumns.add(column);
//                        }
//                    });
//                    columns = fixedColumns;
                }


                updateColumns(columns);

                final List<TableRow> values = processData(columns, tableResult.getRows());
                final OffsetRange valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || !values.isEmpty()) {
                    rowStyles.setConditionalFormattingRules(getQueryTablePreferences()
                            .getConditionalFormattingRules());
                    dataGrid.setRowData((int) valuesRange.getOffset(), values);
                    dataGrid.setRowCount(tableResult.getTotalResults().intValue(), true);
                }

                // Enable download of current results.
                downloadButton.setEnabled(true);

                // Show errors if there are any.
                messagePanel.showMessage(tableResult.getErrorMessages());

                fireColumnAndDataUpdate();

            } else {
                // Disable download of current results.
                downloadButton.setEnabled(false);

                dataGrid.setRowData(0, new ArrayList<>());
                dataGrid.setRowCount(0, true);
                selectionModel.clear();
            }
        } catch (final RuntimeException e) {
            GWT.log(e.getMessage());
        }

        ignoreRangeChange = false;
    }

    private void removeAllColumns() {
        for (final com.google.gwt.user.cellview.client.Column<TableRow, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();
    }

    void updateColumns(final List<Column> columns) {
        if (!Objects.equals(currentColumns, columns)) {
            currentColumns = columns;

            // Remove existing columns.
            removeAllColumns();

            // Add expander column.
            addExpanderColumn();

            // Add columns.
            for (final Column column : columns) {
                // Only include the field if it is supposed to be visible.
                if (column.isVisible()) {
                    addColumn(column);
                }
            }

//                dataGrid.redrawHeaders();
            dataGrid.resizeTableToFitColumns();

            fireColumnAndDataUpdate();
        }
    }

    private void addExpanderColumn() {
        dataGrid.addColumn(expanderColumn, "<br/>", expanderColumnWidth);
        existingColumns.add(expanderColumn);
    }

    private void addColumn(final Column column) {
        final com.google.gwt.user.cellview.client.Column<TableRow, TableRow> col =
                new com.google.gwt.user.cellview.client.IdentityColumn<TableRow>(
                        new TableRowCell(annotationManager, column));
        final ColumnHeader columnHeader = new ColumnHeader(column, columnsManager);
        dataGrid.addResizableColumn(col, columnHeader, column.getWidth());
        existingColumns.add(col);
    }

    private List<TableRow> processData(final List<Column> columns, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        maxDepth = getMaxDepth(columns);

        final List<TableRow> processed = new ArrayList<>(values.size());
        for (final Row row : values) {
            final Map<String, Cell> cellsMap = new HashMap<>();
            for (int i = 0; i < columns.size() && i < row.getValues().size(); i++) {
                final Column column = columns.get(i);
                final String value = row.getValues().get(i) != null
                        ? row.getValues().get(i)
                        : "";

                final SafeStylesBuilder stylesBuilder = new SafeStylesBuilder();

                // Wrap
                if (column.getFormat() != null &&
                    column.getFormat().getWrap() != null &&
                    column.getFormat().getWrap()) {
                    stylesBuilder.whiteSpace(Style.WhiteSpace.NORMAL);
                }
                // Grouped
                if (column.getGroup() != null && column.getGroup() >= row.getDepth()) {
                    stylesBuilder.fontWeight(Style.FontWeight.BOLD);
                }

                final SafeStyles style = stylesBuilder.toSafeStyles();

                final TableRow.Cell cell = new TableRow.Cell(value, style);
                cellsMap.put(column.getName(), cell);
                cellsMap.put(column.getId(), cell);
            }

            // Create an expander for the row.
            Expander expander = null;
            if (row.getDepth() < maxDepth) {
                final boolean open = groupSelection.isGroupOpen(row.getGroupKey(), row.getDepth());
                expander = new Expander(row.getDepth(), open, false);
            } else if (row.getDepth() > 0) {
                expander = new Expander(row.getDepth(), false, true);
            }

            processed.add(new TableRow(
                    expander,
                    row.getGroupKey(),
                    row.getAnnotationId(),
                    cellsMap,
                    row.getMatchingRule(),
                    row.getDepth()));
        }

        // Set the expander column width.
        expanderColumnWidth = ExpanderCell.getColumnWidth(maxDepth);
        dataGrid.setColumnWidth(expanderColumn, expanderColumnWidth, Unit.PX);

        expandButton.update(groupSelection, maxDepth);
        collapseButton.update(groupSelection, maxDepth);

        return processed;
    }

    private int getMaxDepth(final List<Column> columns) {
        int maxGroup = -1;
        for (final Column column : columns) {
            if (column.getGroup() != null) {
                maxGroup = Math.max(maxGroup, column.getGroup());
            }
        }

        int maxDepth = -1;
        if (maxGroup > 0) {
            maxDepth = maxGroup;
        }
        return maxDepth;
    }

    public HandlerRegistration addRefreshRequestHandler(final RefreshRequestEvent.Handler handler) {
        return addHandler(RefreshRequestEvent.getType(), handler);
    }

    public MultiSelectionModelImpl<TableRow> getSelectionModel() {
        return selectionModel;
    }

    @Override
    public void setQueryModel(final QueryModel queryModel) {
        this.currentSearchModel = queryModel;
    }

    private void download() {
        if (currentSearchModel != null) {
            final QueryKey queryKey = currentSearchModel.getCurrentQueryKey();
            final QuerySearchRequest currentSearch = currentSearchModel.getCurrentSearch();
            if (queryKey != null && currentSearch != null) {
                final QuerySearchRequest request = currentSearch
                        .copy()
                        .queryKey(queryKey)
                        .storeHistory(false)
                        .requestedRange(OffsetRange.UNBOUNDED)
                        .build();

                downloadPresenter.setShowDownloadAll(false);
                ShowPopupEvent.builder(downloadPresenter)
                        .popupType(PopupType.OK_CANCEL_DIALOG)
                        .caption("Download Options")
                        .onShow(e -> downloadPresenter.getView().focus())
                        .onHideRequest(e -> {
                            if (e.isOk()) {
                                final DownloadQueryResultsRequest downloadSearchResultsRequest =
                                        new DownloadQueryResultsRequest(
                                                request,
                                                downloadPresenter.getFileType(),
                                                downloadPresenter.isSample(),
                                                downloadPresenter.getPercent());
                                restFactory
                                        .create(QUERY_RESOURCE)
                                        .method(res -> res.downloadSearchResults(
                                                currentSearchModel.getCurrentNode(),
                                                downloadSearchResultsRequest))
                                        .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager,
                                                this,
                                                result))
                                        .taskMonitorFactory(this)
                                        .exec();
                            }
                            e.hide();
                        })
                        .fire();
            }
        }
    }

    @Override
    public List<ColumnRef> getColumnRefs() {
        return NullSafe.list(currentColumns)
                .stream()
                .map(col -> new ColumnRef(col.getId(), col.getName()))
                .collect(Collectors.toList());
    }

    public List<Column> getCurrentColumns() {
        return new ArrayList<>(currentColumns);
    }

    public void setPreferredColumns(final List<Column> columns) {
        setQueryTablePreferences(getQueryTablePreferences().copy().columns(columns).build());
        currentColumns = columns;
        setDirty(true);

        fireColumnAndDataUpdate();
    }

    QueryTablePreferences getQueryTablePreferences() {
        return queryTablePreferencesSupplier.get();
    }

    void setQueryTablePreferences(final QueryTablePreferences queryTablePreferences) {
        queryTablePreferencesConsumer.accept(queryTablePreferences);
    }

    @Override
    public List<ComponentSelection> getSelection() {
        final List<ColumnRef> columns = NullSafe.list(getColumnRefs());
        return stroom.query.client.presenter.TableComponentSelection.create(columns, selectionModel.getSelectedItems());
    }

    @Override
    public Set<String> getHighlights() {
        return NullSafe.get(currentSearchModel, QueryModel::getCurrentHighlights);
    }

    public void setQueryTablePreferencesSupplier(final Supplier<QueryTablePreferences> queryTablePreferencesSupplier) {
        this.queryTablePreferencesSupplier = queryTablePreferencesSupplier;
    }

    public void setQueryTablePreferencesConsumer(final Consumer<QueryTablePreferences> queryTablePreferencesConsumer) {
        this.queryTablePreferencesConsumer = queryTablePreferencesConsumer;
    }

    public void updateQueryTablePreferences() {
        // Change value filter state.
        final QueryTablePreferences queryTablePreferences = queryTablePreferencesSupplier.get();
        setApplyValueFilters(queryTablePreferences.applyValueFilters());
        updatePageSize(queryTablePreferences);
        refresh();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setDirty(final boolean dirty) {
        DirtyEvent.fire(this, dirty);
    }

    public void setQueryResultVisPresenter(final QueryResultVisPresenter queryResultVisPresenter) {
        this.queryResultVisPresenter = queryResultVisPresenter;
    }

    public void setCurrentSelectionFilter(final ExpressionOperator currentSelectionFilter) {
        this.currentSelectionFilter = currentSelectionFilter;
    }

    public ExpressionOperator getCurrentSelectionFilter() {
        return currentSelectionFilter;
    }

    public interface QueryResultTableView extends View {

        void setTableView(View view);
    }

    private static class ColAndPosition {

        private final int position;
        private final Column column;

        public ColAndPosition(final int position, final Column column) {
            this.position = position;
            this.column = column;
        }
    }

    @Override
    public ColumnValuesDataSupplier getDataSupplier(final Column column,
                                                    final List<ConditionalFormattingRule> conditionalFormattingRules) {
        return new QueryTableColumnValuesDataSupplier(restFactory,
                currentSearchModel,
                column,
                conditionalFormattingRules);
    }

    public void setQuery(final String query) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetchDataSourceFromQueryString(query))
                .onSuccess(result -> currentDataSource = result)
                .taskMonitorFactory(this)
                .exec();
    }

    public void onContentTabVisible(final boolean visible) {
        tableIsVisible = visible;
        if (visible) {
            if (annotationChanged) {
                onAnnotationChange();
            }
        }
    }

    @Override
    public Element getFilterButton(final Column column) {
        final int index = columnsManager.getColumnIndex(column);
        if (index >= 0) {
            final Element thead = dataGrid.getTableHeadElement().cast();
            final Element tr = thead.getChild(0).cast();
            final Element th = tr.getChild(index).cast();
            return ElementUtil.findChild(th, "column-valueFilterIcon");
        }

        return null;
    }

    @Override
    public List<Column> getColumns() {
        return getCurrentColumns();
    }

    @Override
    public FilterCellManager getFilterCellManager() {
        return columnsManager;
    }

    private void fireColumnAndDataUpdate() {
        TableUpdateEvent.fire(this);
    }

    @Override
    public HandlerRegistration addUpdateHandler(final TableUpdateEvent.Handler handler) {
        return eventBus.addHandler(TableUpdateEvent.getType(), handler);
    }
}
