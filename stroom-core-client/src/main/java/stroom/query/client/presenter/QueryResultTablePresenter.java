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
import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.table.AnnotationManager;
import stroom.dashboard.client.table.ColumnFilterPresenter;
import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.DownloadPresenter;
import stroom.dashboard.client.table.FormatPresenter;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.TableRowStyles;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.dashboard.shared.ColumnValues;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MessagePanel;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.presenter.TableRow.Cell;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryColumnValuesRequest;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.query.shared.QueryTablePreferences;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class QueryResultTablePresenter
        extends MyPresenterWidget<QueryResultTableView>
        implements ResultComponent, HasComponentSelection, HasDirtyHandlers {

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
    private Set<String> openGroups = null;

    private final ButtonView downloadButton;
    private final DownloadPresenter downloadPresenter;
    private final AnnotationManager annotationManager;
    private final InlineSvgToggleButton valueFilterButton;
    private final ButtonView annotateButton;

    private Supplier<QueryTablePreferences> queryTablePreferencesSupplier;
    private Consumer<QueryTablePreferences> queryTablePreferencesConsumer;
    private final QueryTableColumnsManager columnsManager;
    private final List<com.google.gwt.user.cellview.client.Column<TableRow, ?>> existingColumns = new ArrayList<>();
    private List<Column> currentColumns = Collections.emptyList();
    private QueryResultVisPresenter queryResultVisPresenter;
    private ExpressionOperator currentSelectionFilter;
    private final TableRowStyles tableRowStyles;
    private DashboardContext dashboardContext;
    private DocRef currentDataSource;

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
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        this.downloadPresenter = downloadPresenter;
        this.annotationManager = annotationManager;
        tableRowStyles = new TableRowStyles(userPreferencesManager);

        this.pagerView = pagerView;
        this.dataGrid = new MyDataGrid<>();
        dataGrid.addStyleName("TablePresenter");
        dataGrid.setRowStyles(tableRowStyles);
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<TableRow> selectionEventManager = new DataGridSelectionEventManager<>(
                dataGrid,
                selectionModel,
                false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);


        columnsManager = new QueryTableColumnsManager(
                this,
                formatPresenter,
                rulesPresenterProvider,
                columnFilterPresenter,
                columnValuesFilterPresenter);
        dataGrid.setHeadingListener(columnsManager);
        columnsManager.setColumnsStartIndex(1);


        pagerView.setDataWidget(dataGrid);
        tableView.setTableView(pagerView);

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
        expanderColumn.setFieldUpdater((index, result, value) -> {
            toggleOpenGroup(result.getGroupKey());
            refresh();
        });

        pagerView.getRefreshButton().setAllowPause(true);

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
        annotateButton.setVisible(securityContext
                .hasAppPermission(AppPermission.ANNOTATIONS));

        annotationManager.setDataSourceSupplier(() -> currentDataSource);
        annotationManager.setColumnSupplier(() -> currentColumns);
    }

    public DashboardContext getDashboardContext() {
        return dashboardContext;
    }

    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }

    private void toggleOpenGroup(final String group) {
        openGroup(group, !isGroupOpen(group));
    }

    private void openGroup(final String group, final boolean open) {
        if (openGroups == null) {
            openGroups = new HashSet<>();
        }

        if (open) {
            openGroups.add(group);
        } else {
            openGroups.remove(group);
        }
    }

    private boolean isGroupOpen(final String group) {
        return openGroups != null && openGroups.contains(group);
    }

//    public void setRequestedRange(final OffsetRange requestedRange) {
//        this.requestedRange = requestedRange;
//    }

    @Override
    public Set<String> getOpenGroups() {
        return openGroups;
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
                final List<Long> annotationIdList = annotationManager.getAnnotationIdList(
                        selectionModel.getSelectedItems());
                if (annotationIdList.size() == 1) {
                    annotationManager.editAnnotation(annotationIdList.get(0));
                }
            }
        }));
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
        tableRowStyles.setConditionalFormattingRules(queryTablePreferences.getConditionalFormattingRules());
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
                List<Column> columns = tableResult.getColumns();

                if (columns != null && queryTablePreferences != null && queryTablePreferences.getColumns() != null) {

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

                final List<TableRow> values = processData(tableResult.getColumns(), tableResult.getRows());
                final OffsetRange valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || !values.isEmpty()) {
                    tableRowStyles.setConditionalFormattingRules(getQueryTablePreferences()
                            .getConditionalFormattingRules());
                    dataGrid.setRowData((int) valuesRange.getOffset(), values);
                    dataGrid.setRowCount(tableResult.getTotalResults().intValue(), true);
                }

                // Enable download of current results.
                downloadButton.setEnabled(true);

                // Show errors if there are any.
                messagePanel.showMessage(tableResult.getErrors());

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
        }
    }

    private void addExpanderColumn() {
        dataGrid.addColumn(expanderColumn, "<br/>", expanderColumnWidth);
        existingColumns.add(expanderColumn);
    }

    private void addColumn(final Column column) {
        final com.google.gwt.user.cellview.client.Column<TableRow, SafeHtml> col =
                new com.google.gwt.user.cellview.client.Column<TableRow, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final TableRow row) {
                        if (row == null) {
                            return SafeHtmlUtil.NBSP;
                        }

                        return row.getValue(column.getId());
                    }
                };

        final ColumnHeader columnHeader = new ColumnHeader(column, columnsManager);
        dataGrid.addResizableColumn(col, columnHeader, column.getWidth());
        existingColumns.add(col);
    }

    private List<TableRow> processData(final List<Column> columns, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        int maxGroup = -1;
        final boolean showDetail = false; //getTableSettings().showDetail();
        for (final Column column : columns) {
            if (column.getGroup() != null) {
                maxGroup = Math.max(maxGroup, column.getGroup());
            }
        }

        int maxDepth = -1;
        if (maxGroup > 0 && showDetail) {
            maxDepth = maxGroup + 1;
        } else if (maxGroup > 0) {
            maxDepth = maxGroup;
        } else if (maxGroup == 0 && showDetail) {
            maxDepth = 1;
        }

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

                final String style = stylesBuilder.toSafeStyles().asString();

                final TableRow.Cell cell = new TableRow.Cell(value, style);
                cellsMap.put(column.getName(), cell);
                cellsMap.put(column.getId(), cell);
            }

            // Create an expander for the row.
            Expander expander = null;
            if (row.getDepth() < maxDepth) {
                final boolean open = isGroupOpen(row.getGroupKey());
                expander = new Expander(row.getDepth(), open, false);
            } else if (row.getDepth() > 0) {
                expander = new Expander(row.getDepth(), false, true);
            }

            processed.add(new TableRow(
                    expander,
                    row.getGroupKey(),
                    row.getAnnotationId(),
                    cellsMap,
                    row.getMatchingRule()));
        }

        // Set the expander column width.
        expanderColumnWidth = ExpanderCell.getColumnWidth(maxDepth);
        dataGrid.setColumnWidth(expanderColumn, expanderColumnWidth, Unit.PX);

        return processed;
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
    public List<ColumnRef> getColumns() {
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
    }

    QueryTablePreferences getQueryTablePreferences() {
        return queryTablePreferencesSupplier.get();
    }

    void setQueryTablePreferences(final QueryTablePreferences queryTablePreferences) {
        queryTablePreferencesConsumer.accept(queryTablePreferences);
    }

    @Override
    public List<ComponentSelection> getSelection() {
        final List<ColumnRef> columns = NullSafe.list(getColumns());
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

    public ColumnValuesDataSupplier getDataSupplier(final Column column) {
        return new QueryTableColumnValuesDataSupplier(restFactory,
                currentSearchModel,
                column);
    }

    public void setQuery(final String query) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetchDataSourceFromQueryString(query))
                .onSuccess(result -> currentDataSource = result)
                .taskMonitorFactory(this)
                .exec();
    }

    public static class QueryTableColumnValuesDataSupplier extends ColumnValuesDataSupplier {

        private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

        private final RestFactory restFactory;
        private final QueryModel searchModel;
        private final QuerySearchRequest searchRequest;


        public QueryTableColumnValuesDataSupplier(
                final RestFactory restFactory,
                final QueryModel searchModel,
                final stroom.query.api.Column column) {
            super(column.copy().build());
            this.restFactory = restFactory;
            this.searchModel = searchModel;

            QuerySearchRequest querySearchRequest = null;
            final QueryKey queryKey = searchModel.getCurrentQueryKey();
            final QuerySearchRequest currentSearch = searchModel.getCurrentSearch();
            if (queryKey != null && currentSearch != null) {
                querySearchRequest = currentSearch
                        .copy()
                        .queryKey(queryKey)
                        .storeHistory(false)
                        .requestedRange(OffsetRange.UNBOUNDED)
                        .build();
            }
            searchRequest = querySearchRequest;
        }

        @Override
        protected void exec(final Range range,
                            final Consumer<ColumnValues> dataConsumer,
                            final RestErrorHandler errorHandler) {
            if (searchRequest == null) {
                dataConsumer.accept(new ColumnValues(Collections.emptyList(), PageResponse.empty()));

            } else {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final QueryColumnValuesRequest columnValuesRequest = new QueryColumnValuesRequest(
                        searchRequest,
                        getColumn(),
                        getNameFilter(),
                        pageRequest);

                restFactory
                        .create(QUERY_RESOURCE)
                        .method(res -> res.getColumnValues(searchModel.getCurrentNode(),
                                columnValuesRequest))
                        .onSuccess(dataConsumer)
                        .taskMonitorFactory(getTaskMonitorFactory())
                        .exec();
            }
        }
    }
}
