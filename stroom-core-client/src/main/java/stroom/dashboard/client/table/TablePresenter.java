/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.client.table;

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationFields;
import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.input.FilterableTable;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.ResultComponent;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.query.SelectionHandlerExpressionBuilder;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
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
import stroom.index.shared.IndexConstants;
import stroom.item.client.SelectionPopup;
import stroom.preferences.client.UserPreferencesManager;
import stroom.processor.shared.ProcessorExpressionUtil;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.GroupSelection;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.Row;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DataSourceClient;
import stroom.query.client.presenter.AnnotationManager;
import stroom.query.client.presenter.ColumnHeader;
import stroom.query.client.presenter.DynamicColumnSelectionListModel;
import stroom.query.client.presenter.DynamicColumnSelectionListModel.ColumnSelectionItem;
import stroom.query.client.presenter.TableComponentSelection;
import stroom.query.client.presenter.TableRow;
import stroom.query.client.presenter.TableRowCell;
import stroom.query.client.presenter.TimeZones;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;
import stroom.util.shared.Version;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TablePresenter extends AbstractComponentPresenter<TableView>
        implements HasDirtyHandlers, ResultComponent, HasComponentSelection, HasHandlers, FilterableTable {

    public static final String TAB_TYPE = "table-component";
    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    public static final ComponentType TYPE = new ComponentType(1, "table", "Table", ComponentUse.PANEL);
    private static final Version CURRENT_MODEL_VERSION = new Version(6, 1, 26);

    private final EventBus eventBus;
    private final PagerView pagerView;
    private final DataSourceClient dataSourceClient;
    private final LocationManager locationManager;
    private TableResultRequest tableResultRequest = TableResultRequest.builder()
            .requestedRange(OffsetRange.ZERO_1000)
            .build();
    private GroupSelection groupSelection = new GroupSelection();
    private final List<com.google.gwt.user.cellview.client.Column<TableRow, ?>> existingColumns = new ArrayList<>();
    private final List<HandlerRegistration> searchModelHandlerRegistrations = new ArrayList<>();
    private final ButtonView addColumnButton;
    private final TableExpandButton expandButton;
    private final TableCollapseButton collapseButton;
    private final ButtonView downloadButton;
    private final InlineSvgToggleButton valueFilterButton;
    private final ButtonView annotateButton;
    private final DownloadPresenter downloadPresenter;
    private final AnnotationManager annotationManager;
    private final RestFactory restFactory;
    private final TimeZones timeZones;
    private final UserPreferencesManager userPreferencesManager;
    private final DynamicColumnSelectionListModel columnSelectionListModel;
    private final ColumnsManager columnsManager;
    private final MyDataGrid<TableRow> dataGrid;
    private final MultiSelectionModelImpl<TableRow> selectionModel;
    private final com.google.gwt.user.cellview.client.Column<TableRow, Expander> expanderColumn;

    private int expanderColumnWidth;
    private SearchModel currentSearchModel;
    private boolean ignoreRangeChange;
    private long[] maxResults = TableComponentSettings.DEFAULT_MAX_RESULTS;
    private boolean pause;
    private SelectionPopup<Column, ColumnSelectionItem> addColumnPopup;
    private ExpressionOperator currentSelectionFilter;
    private final TableRowStyles rowStyles;
    private boolean initialised;
    private int maxDepth;

    private boolean tableIsVisible = true;
    private boolean annotationChanged;

    @Inject
    public TablePresenter(final EventBus eventBus,
                          final TableView view,
                          final PagerView pagerView,
                          final ClientSecurityContext securityContext,
                          final LocationManager locationManager,
                          final Provider<RenameColumnPresenter> renameColumnPresenterProvider,
                          final Provider<ColumnFunctionEditorPresenter> expressionPresenterProvider,
                          final FormatPresenter formatPresenter,
                          final TableFilterPresenter tableFilterPresenter,
                          final Provider<TableSettingsPresenter> settingsPresenterProvider,
                          final DownloadPresenter downloadPresenter,
                          final AnnotationManager annotationManager,
                          final RestFactory restFactory,
                          final UiConfigCache clientPropertyCache,
                          final TimeZones timeZones,
                          final UserPreferencesManager userPreferencesManager,
                          final DynamicColumnSelectionListModel columnSelectionListModel,
                          final DataSourceClient dataSourceClient,
                          final ColumnValuesFilterPresenter columnValuesFilterPresenter) {
        super(eventBus, view, settingsPresenterProvider);
        this.eventBus = eventBus;
        this.pagerView = pagerView;
        this.locationManager = locationManager;
        this.downloadPresenter = downloadPresenter;
        this.annotationManager = annotationManager;
        this.restFactory = restFactory;
        this.timeZones = timeZones;
        this.userPreferencesManager = userPreferencesManager;
        this.columnSelectionListModel = columnSelectionListModel;
        this.dataSourceClient = dataSourceClient;
        rowStyles = new TableRowStyles(userPreferencesManager);

        columnSelectionListModel.setTaskMonitorFactory(this);
        annotationManager.setTaskMonitorFactory(this);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.addStyleName("TablePresenter");
        dataGrid.setRowStyles(rowStyles);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        view.setTableView(pagerView);

        // Add the 'add column' button.
        addColumnButton = pagerView.addButton(SvgPresets.ADD);
        addColumnButton.setTitle("Add Column");

        expandButton = TableExpandButton.create();
        pagerView.addButton(expandButton);

        collapseButton = TableCollapseButton.create();
        pagerView.addButton(collapseButton);

        // Download
        downloadButton = pagerView.addButton(SvgPresets.DOWNLOAD);
        downloadButton.setVisible(securityContext
                .hasAppPermission(AppPermission.DOWNLOAD_SEARCH_RESULTS_PERMISSION));

        // Filter values
        valueFilterButton = new InlineSvgToggleButton();
        valueFilterButton.setSvg(SvgImage.FILTER);
        valueFilterButton.setTitle("Filter Values");
        pagerView.addButton(valueFilterButton);

        // Annotate
        annotateButton = pagerView.addButton(SvgPresets.ANNOTATE);
        annotateButton.setVisible(annotationManager.isEnabled());

        columnsManager = new ColumnsManager(eventBus,
                this,
                renameColumnPresenterProvider,
                expressionPresenterProvider,
                formatPresenter,
                tableFilterPresenter,
                columnValuesFilterPresenter);
        dataGrid.setHeadingListener(columnsManager);

        clientPropertyCache.get(result -> {
            if (result != null) {
                final String value = result.getDefaultMaxResults();
                if (value != null) {
                    final String[] parts = value.split(",");
                    final long[] arr = new long[parts.length];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = Long.parseLong(parts[i].trim());
                    }
                    maxResults = arr;
                }
            }
        }, pagerView);

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
            tableResultRequest = tableResultRequest
                    .copy()
                    .groupSelection(groupSelection)
                    .build();
            refresh();
        });

        pagerView.getRefreshButton().setAllowPause(true);
        annotationManager.setColumnSupplier(() -> getTableComponentSettings().getColumns());
    }

    private void toggle(final TableRow row) {
        if (groupSelection.isGroupOpen(row.getGroupKey(), row.getDepth())) {
            groupSelection.close(row.getGroupKey());
        } else {
            groupSelection.open(row.getGroupKey());
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> {
            getDashboardContext().fireComponentChangeEvent(this);
            if (event.getSelectionType().isDoubleSelect()) {
                final Set<Long> annotationIdList = annotationManager.getAnnotationIds(
                        selectionModel.getSelectedItems());
                if (annotationIdList.size() == 1) {
                    annotationManager.editAnnotation(annotationIdList.iterator().next());
                }
            }
        }));
        registerHandler(dataGrid.addRangeChangeHandler(event -> {
            final Range range = event.getNewRange();
            tableResultRequest = tableResultRequest
                    .copy()
                    .requestedRange(new OffsetRange(range.getStart(), range.getLength()))
                    .build();
            if (!ignoreRangeChange) {
                refresh();
            }
        }));
        registerHandler(dataGrid.addHyperlinkHandler(event -> HyperlinkEvent
                .fire(this, event.getHyperlink(), event.getTaskMonitorFactory(), getDashboardContext())));
        registerHandler(addColumnButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAddColumn(event);
            }
        }));

        registerHandler(expandButton.addClickHandler(event -> {
            groupSelection = expandButton.expand(groupSelection, maxDepth);

            tableResultRequest = tableResultRequest
                    .copy()
                    .groupSelection(groupSelection)
                    .build();
            refresh();
        }));

        registerHandler(collapseButton.addClickHandler(event -> {
            groupSelection = collapseButton.collapse(groupSelection);

            tableResultRequest = tableResultRequest
                    .copy()
                    .groupSelection(groupSelection)
                    .build();
            refresh();
        }));

        registerHandler(downloadButton.addClickHandler(event -> {
            if (currentSearchModel != null) {
                if (currentSearchModel.isPolling()) {
                    ConfirmEvent.fire(TablePresenter.this,
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
                annotationManager.showAnnotationMenu(event.getNativeEvent(), selectionModel.getSelectedItems());
            }
        }));

        registerHandler(pagerView.getRefreshButton().addClickHandler(event -> setPause(!pause, true)));

        registerHandler(getEventBus().addHandler(AnnotationChangeEvent.getType(), e ->
                onAnnotationChange()));
    }

    private void onAnnotationChange() {
        try {
            annotationChanged = true;
            if (tableIsVisible) {
                annotationChanged = false;
                final DocRef dataSource = NullSafe
                        .get(currentSearchModel, SearchModel::getIndexLoader, IndexLoader::getLoadedDataSourceRef);
                if (dataSource != null &&
                    AnnotationFields.ANNOTATIONS_PSEUDO_DOC_REF.getType().equals(dataSource.getType())) {
                    // If this is an annotations data source then force a new search.
                    forceNewSearch();
                } else if (columnsManager
                        .getColumns()
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

    @Override
    public void onContentTabVisible(final boolean visible) {
        tableIsVisible = visible;
        if (visible) {
            if (annotationChanged) {
                onAnnotationChange();
            }
        }
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        super.setDashboardContext(dashboardContext);
        registerHandler(getDashboardContext().addContextChangeHandler(event -> {
            if (initialised && updateSelectionFilter()) {
//                reset();
                refresh();
            }
        }));
    }

    private boolean updateSelectionFilter() {
        final ExpressionOperator selectionFilter = getDashboardContext()
                .createSelectionHandlerExpression(getTableComponentSettings().getSelectionFilter())
                .orElse(null);
        if (!Objects.equals(currentSelectionFilter, selectionFilter)) {
            currentSelectionFilter = selectionFilter;
            return true;
        }
        return false;
    }

    public void toggleApplyValueFilters() {
        final boolean applyValueFilters = !getTableComponentSettings().applyValueFilters();
        setSettings(getTableComponentSettings()
                .copy()
                .applyValueFilters(applyValueFilters)
                .build());
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

    private void setPause(final boolean pause,
                          final boolean refresh) {
        // If currently paused then refresh if we are allowed.
        if (refresh && this.pause) {
            refresh();
        }
        this.pause = pause;
        pagerView.getRefreshButton().setPaused(this.pause);
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        cleanupSearchModelAssociation();
    }

    private void onAddColumn(final ClickEvent event) {
        if (currentSearchModel != null) {
            final DocRef dataSource = currentSearchModel.getIndexLoader().getLoadedDataSourceRef();
            final boolean changedDataSource = !Objects.equals(columnSelectionListModel.getDataSourceRef(), dataSource);
            columnSelectionListModel.setDataSourceRef(dataSource);

            if (addColumnPopup == null) {
                addColumnPopup = new SelectionPopup<>();
                addColumnPopup.init(columnSelectionListModel);
            } else if (changedDataSource) {
                addColumnPopup.refresh();
            }

            final Element target = event.getNativeEvent().getEventTarget().cast();
            addColumnPopup.addAutoHidePartner(target);
            addColumnPopup.show(target);

            final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
            final MultiSelectionModel<ColumnSelectionItem> selectionModel = addColumnPopup.getSelectionModel();
            handlerRegistrations.add(selectionModel.addSelectionHandler(e -> {
                final ColumnSelectionItem item = selectionModel.getSelected();
                if (item != null && !item.isHasChildren()) {
                    final Column column = item.getColumn();
                    if (column != null) {
                        columnsManager.addColumn(column);
                    }
                    addColumnPopup.hide();
                }
            }));
            handlerRegistrations.add(addColumnPopup.addCloseHandler(closeEvent -> {
                for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                    handlerRegistration.removeHandler();
                }
                handlerRegistrations.clear();
                target.focus();
            }));
            registerHandler(() -> {
                for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                    handlerRegistration.removeHandler();
                }
                handlerRegistrations.clear();
            });
        }
    }

    private void download() {
        if (currentSearchModel != null) {
            final QueryKey queryKey = currentSearchModel.getCurrentQueryKey();
            final Search currentSearch = currentSearchModel.getCurrentSearch();
            if (queryKey != null && currentSearch != null) {
                ShowPopupEvent.builder(downloadPresenter)
                        .popupType(PopupType.OK_CANCEL_DIALOG)
                        .caption("Download Options")
                        .onShow(e -> downloadPresenter.getView().focus())
                        .onHideRequest(e -> {
                            if (e.isOk()) {
                                final List<ComponentResultRequest> requests = new ArrayList<>();
                                currentSearch.getComponentSettingsMap().entrySet()
                                        .stream()
                                        .filter(settings -> settings.getValue() instanceof TableComponentSettings)
                                        .forEach(tableSettings -> requests.add(TableResultRequest
                                                .builder()
                                                .componentId(tableSettings.getKey())
                                                .requestedRange(OffsetRange.UNBOUNDED)
                                                .tableName(getTableName(tableSettings.getKey()))
                                                .tableSettings(resolveTableSettings())
                                                .fetch(Fetch.ALL)
                                                .build()));

                                final Search search = Search
                                        .builder()
                                        .dataSourceRef(currentSearch.getDataSourceRef())
                                        .expression(currentSearch.getExpression())
                                        .componentSettingsMap(currentSearch.getComponentSettingsMap())
                                        .params(currentSearch.getParams())
                                        .timeRange(currentSearch.getTimeRange())
                                        .incremental(true)
                                        .queryInfo(currentSearch.getQueryInfo())
                                        .build();

                                final DashboardSearchRequest searchRequest = DashboardSearchRequest
                                        .builder()
                                        .searchRequestSource(currentSearchModel.getSearchRequestSource())
                                        .queryKey(queryKey)
                                        .search(search)
                                        .componentResultRequests(requests)
                                        .dateTimeSettings(getDateTimeSettings())
                                        .build();

                                final DownloadSearchResultsRequest downloadSearchResultsRequest =
                                        new DownloadSearchResultsRequest(
                                                searchRequest,
                                                getComponentConfig().getId(),
                                                downloadPresenter.getFileType(),
                                                downloadPresenter.downloadAllTables(),
                                                downloadPresenter.isSample(),
                                                downloadPresenter.getPercent());
                                restFactory
                                        .create(DASHBOARD_RESOURCE)
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

    private String getTableName(final String componentId) {
        return Optional.ofNullable(getDashboardContext().getComponents().get(componentId))
                .map(component -> component.getComponentConfig().getName())
                .orElse(null);
    }

    private DateTimeSettings getDateTimeSettings() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        return DateTimeSettings
                .builder()
                .dateTimePattern(userPreferences.getDateTimePattern())
                .timeZone(userPreferences.getTimeZone())
                .localZoneId(timeZones.getLocalTimeZoneId())
                .build();
    }

    @Override
    public void startSearch() {
        tableResultRequest = tableResultRequest
                .copy()
                .tableSettings(resolveTableSettings())
                .build();

        setPause(false, false);
        pagerView.getRefreshButton().setRefreshing(true);
    }

    @Override
    public void endSearch() {
        pagerView.getRefreshButton().setRefreshing(false);
    }

    @Override
    public void setData(final Result componentResult) {
        if (!pause) {
            setDataInternal(componentResult);
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
    public FilterCellManager getFilterCellManager() {
        return columnsManager;
    }

    private void setDataInternal(final Result componentResult) {
        ignoreRangeChange = true;
        final MessagePanel messagePanel = pagerView.getMessagePanel();
        messagePanel.hideMessage();

        try {
            if (componentResult != null) {
                // Don't refresh the table unless the results have changed.
                final TableResult tableResult = (TableResult) componentResult;

                final List<TableRow> values = processData(tableResult.getColumns(), tableResult.getRows());
                final OffsetRange valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || !values.isEmpty()) {
                    rowStyles.setConditionalFormattingRules(getTableComponentSettings()
                            .getConditionalFormattingRules());
                    dataGrid.setRowData((int) valuesRange.getOffset(), values);
                    dataGrid.setRowCount(tableResult.getTotalResults().intValue(), true);
                }

                // Enable download of current results.
                downloadButton.setEnabled(true);

                // Show errors if there are any.
                messagePanel.showMessage(tableResult.getErrorMessages());

            } else {
                // Disable download of current results.
                downloadButton.setEnabled(false);

                dataGrid.setRowData(0, new ArrayList<>());
                dataGrid.setRowCount(0, true);

                selectionModel.clear();
            }

            fireColumnAndDataUpdate();

        } catch (final RuntimeException e) {
            GWT.log(e.getMessage());
        }

        ignoreRangeChange = false;
    }

    public static QueryField buildDsField(final Column column) {
        final Type colType = Optional.ofNullable(column.getFormat())
                .map(Format::getType)
                .orElse(Type.GENERAL);

        try {
            return switch (colType) {
                case NUMBER -> QueryField.createLong(column.getName());
                case DATE_TIME -> QueryField.createDate(column.getName());
                default -> QueryField
                        .builder()
                        .fldName(column.getName())
                        .fldType(FieldType.TEXT)
                        .conditionSet(ConditionSet.ALL_UI_TEXT)
                        .queryable(true)
                        .build();
            };
        } catch (final Exception e) {
            GWT.log(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private List<TableRow> processData(final List<Column> columns, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        maxDepth = getMaxDepth(columns);

        final List<TableRow> processed = new ArrayList<>(values.size());
        for (final Row row : values) {
            final Map<String, TableRow.Cell> cellsMap = new HashMap<>();
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
        final boolean showDetail = getTableComponentSettings().showDetail();
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
        return maxDepth;
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

    void handleFieldRename(final String oldName,
                           final String newName) {
        if (!Objects.equals(oldName, newName)) {
            if (getTableComponentSettings() != null &&
                getTableComponentSettings().getConditionalFormattingRules() != null) {
                final AtomicBoolean wasModified = new AtomicBoolean(false);
                getTableComponentSettings().getConditionalFormattingRules().stream()
                        .map(ConditionalFormattingRule::getExpression)
                        .forEach(expressionOperator -> {
                            final boolean wasRuleModified = renameField(expressionOperator, oldName, newName);
                            if (wasRuleModified) {
                                wasModified.compareAndSet(false, true);
                            }
                        });
                if (wasModified.get()) {
                    setDirty(true);
                }
            }
        }
    }

    private boolean renameField(final ExpressionItem expressionItem,
                                final String oldTermName,
                                final String newTermName) {
        final AtomicBoolean wasModified = new AtomicBoolean(false);
        ProcessorExpressionUtil.walkExpressionTree(
                expressionItem,
                null,
                (parent, childOffset, oldTerm) -> {
                    if (Objects.equals(oldTerm.getField(), oldTermName)) {
                        if (parent == null) {
                            throw new RuntimeException("Should not have a term without a parent operator");
                        }

                        final ExpressionTerm newTerm = oldTerm.copy().field(newTermName).build();

                        // Replace the old term with the new one
                        parent.getChildren().set(childOffset, newTerm);
                        wasModified.compareAndSet(false, true);
                    }
                });
        return wasModified.get();
    }

    private void setQueryId(final String queryId) {
        cleanupSearchModelAssociation();

        if (queryId != null) {
            final Component component = getDashboardContext().getComponents().get(queryId);
            if (component instanceof final QueryPresenter queryPresenter) {
                currentSearchModel = queryPresenter.getSearchModel();
                if (currentSearchModel != null) {
                    currentSearchModel.addComponent(getComponentConfig().getId(), this);
                }
            }
        }

        if (currentSearchModel != null) {
            searchModelHandlerRegistrations
                    .add(currentSearchModel.getIndexLoader().addChangeDataHandler(event -> updateFields()));
        }

        updateFields();

        // Not sure what this was needed for....
//        getComponents().fireComponentChangeEvent(this);
    }

    private void cleanupSearchModelAssociation() {
        if (currentSearchModel != null) {
            // Remove this component from the list of components the search
            // model expects to update.
            currentSearchModel.removeComponent(getComponentConfig().getId());

            // Clear any existing handler registrations on the search model.
            for (final HandlerRegistration handlerRegistration : searchModelHandlerRegistrations) {
                handlerRegistration.removeHandler();
            }
            searchModelHandlerRegistrations.clear();

            currentSearchModel = null;
        }
    }

    private void updateFields() {
        if (getTableComponentSettings().getColumns() == null) {
            setSettings(getTableComponentSettings().copy().columns(new ArrayList<>()).build());
        }

        if (currentSearchModel != null) {
            final IndexLoader indexLoader = currentSearchModel.getIndexLoader();

            // See if the datasource changed.
            final DocRef currentDataSource = getTableComponentSettings().getDataSourceRef();
            final DocRef loadedDataSource = indexLoader.getLoadedDataSourceRef();
            if (!Objects.equals(currentDataSource, loadedDataSource)) {
                dataSourceClient.fetchDefaultExtractionPipeline(loadedDataSource, extractionPipeline -> {
                    final TableComponentSettings.Builder builder = getTableComponentSettings().copy();

                    // Update data source ref so the table has an idea where it will be receiving data from.
                    builder.dataSourceRef(loadedDataSource);

                    // Update the extraction pipeline.
                    if (extractionPipeline != null && getTableComponentSettings().useDefaultExtractionPipeline()) {
                        builder.extractionPipeline(extractionPipeline).extractValues(true);
                    }
                    setSettings(builder.build());
                }, this);
            }
        }

        // Update columns.
        updateColumns();
    }

    private void ensureSpecialColumns() {
        // Remove all special fields as we will re-add them.
        getTableComponentSettings().getColumns().removeIf(Column::isSpecial);

        final Optional<Integer> maxGroup = getTableComponentSettings()
                .getColumns()
                .stream()
                .map(Column::getGroup)
                .filter(Objects::nonNull)
                .max(Integer::compareTo);

        // Prior to the introduction of the special field concept, special fields were
        // treated as invisible fields. For this reason we need to remove old invisible
        // fields if we haven't yet turned them into special fields.
        final Version version = Version.parse(getTableComponentSettings().getModelVersion());
        final boolean old = version.lt(CURRENT_MODEL_VERSION);
        if (old) {
            getTableComponentSettings().getColumns().removeIf(column ->
                    !column.isVisible() && (column.getName().equals("Id") ||
                                            column.getName().equals(IndexConstants.STREAM_ID) ||
                                            column.getName().equals(IndexConstants.EVENT_ID) ||
                                            column.getName().startsWith("__")));
            setSettings(getTableComponentSettings()
                    .copy()
                    .modelVersion(CURRENT_MODEL_VERSION.toString())
                    .build());
        }

        if (getTableComponentSettings().showDetail() || maxGroup.isEmpty()) {
            // Add special fields.
            getTableComponentSettings().getColumns().add(SpecialColumns.RESERVED_ID_COLUMN);
            getTableComponentSettings().getColumns().add(SpecialColumns.RESERVED_STREAM_ID_COLUMN);
            getTableComponentSettings().getColumns().add(SpecialColumns.RESERVED_EVENT_ID_COLUMN);
        }

//        GWT.log(tableSettings.getFields().stream()
//                .map(field ->
//                        String.join(
//                                ", ",
//                                field.getId(),
//                                field.getName(),
//                                Boolean.toString(field.isVisible()),
//                                Boolean.toString(field.isSpecial())))
//                .collect(Collectors.joining("\n")));
//        }
    }

    void updateColumns() {
        // Now make sure special fields exist for stream id and event id.
        ensureSpecialColumns();

        // Remove existing columns.
        for (final com.google.gwt.user.cellview.client.Column<TableRow, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();

        final List<Column> columns = getTableComponentSettings().getColumns();
        addExpanderColumn();
        columnsManager.setColumnsStartIndex(1);

        // Add fields as columns.
        for (final Column column : columns) {
            // Only include the field if it is supposed to be visible.
            if (column.isVisible()) {
                addColumn(column);
            }
        }

        dataGrid.resizeTableToFitColumns();
        fireColumnAndDataUpdate();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        tableResultRequest = tableResultRequest
                .copy()
                .componentId(componentConfig.getId())
                .tableName(componentConfig.getName())
                .build();

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof TableComponentSettings)) {
            setSettings(createSettings());
        }

        // Fix legacy selection filters.
        setSettings(getTableComponentSettings()
                .copy()
                .selectionFilter(SelectionHandlerExpressionBuilder
                        .fixLegacySelectionHandlers(getTableComponentSettings().getSelectionFilter()))
                .build());

        // Update the page size for the data grid.
        updatePageSize();

        // Fix historic conditional formatting rule ids.
        fixRuleIds(getTableComponentSettings().getConditionalFormattingRules());

        // Ensure all fields have ids.
        final Set<String> usedFieldIds = new HashSet<>();
        if (getTableComponentSettings().getColumns() != null) {
            final String reservedStreamId = SpecialColumns.RESERVED_STREAM_ID;
            final String reservedEventId = SpecialColumns.RESERVED_EVENT_ID;

            final List<Column> columns = new ArrayList<>();
            getTableComponentSettings().getColumns().forEach(column -> {
                Column col = column;
                if (reservedStreamId.equals(col.getName())) {
                    col = SpecialColumns.RESERVED_STREAM_ID_COLUMN;
                } else if (reservedEventId.equals(col.getName())) {
                    col = SpecialColumns.RESERVED_EVENT_ID_COLUMN;
                } else if (column.getId() == null) {
                    col = column.copy().id(columnsManager.createRandomColumnId(usedFieldIds)).build();
                }
                usedFieldIds.add(column.getId());
                columns.add(col);
            });
            setSettings(getTableComponentSettings().copy().columns(columns).build());
        }

        // Change value filter state.
        setApplyValueFilters(getTableComponentSettings().applyValueFilters());
        initialised = true;
    }

    @Override
    public void onClose() {
        super.onClose();
        initialised = false;
    }

    @Override
    public void onRemove() {
        super.onRemove();
        initialised = false;
    }

    /**
     * Fix for old rules that contained duplicate rule ids.
     *
     * @param rules The rule list to fix.
     */
    private void fixRuleIds(final List<ConditionalFormattingRule> rules) {
        if (rules != null) {
            final Set<String> idSet = new HashSet<>();
            final List<ConditionalFormattingRule> fixedRules = new ArrayList<>(rules.size());
            for (final ConditionalFormattingRule rule : rules) {
                ConditionalFormattingRule fixed = rule;
                while (!idSet.add(fixed.getId())) {
                    fixed = fixed.copy().id(RandomId.createId(5)).build();
                }
                fixedRules.add(fixed);
            }
            if (!Objects.equals(rules, fixedRules)) {
                GWT.log("Fixed conditional formatting rules");
                setSettings(getTableComponentSettings().copy().conditionalFormattingRules(fixedRules).build());
            }
        }
    }

    public TableComponentSettings getTableComponentSettings() {
        return (TableComponentSettings) getSettings();
    }

    @Override
    public void link() {
        String queryId = getTableComponentSettings().getQueryId();
        queryId = getDashboardContext().getComponents().validateOrGetLastComponentId(queryId,
                QueryPresenter.TYPE.getId());
        setSettings(getTableComponentSettings().copy().queryId(queryId).build());
        setQueryId(queryId);
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        final TableComponentSettings tableComponentSettings = getTableComponentSettings();
        setQueryId(tableComponentSettings.getQueryId());
        updatePageSize();
        updateSelectionFilter();

        // Update styles and re-render
        rowStyles.setConditionalFormattingRules(tableComponentSettings.getConditionalFormattingRules());
        dataGrid.redraw();
    }

    private void updatePageSize() {
        final TableComponentSettings tableComponentSettings = getTableComponentSettings();
        final int start = dataGrid.getVisibleRange().getStart();
        dataGrid.setVisibleRange(new Range(
                start,
                NullSafe.getOrElse(tableComponentSettings, TableComponentSettings::getPageSize, 100)));
    }

    @Override
    public boolean isPaused() {
        return pause;
    }

    @Override
    public ComponentResultRequest getResultRequest(final Fetch fetch) {
        return tableResultRequest.copy().tableSettings(resolveTableSettings()).fetch(fetch).build();
    }

    /**
     * Get the table component settings and perform parameter replacement on columns etc.
     *
     * @return Resolved table settings.
     */
    public TableSettings resolveTableSettings() {
        final TableComponentSettings tableComponentSettings = getTableComponentSettings();
        final TableComponentSettings.Builder builder = tableComponentSettings.copy();

        // Resolve parameters in columns.
        final DashboardContext dashboardContext = getDashboardContext();
        final List<Column> columnsIn = tableComponentSettings.getColumns();
        if (columnsIn != null) {
            final List<Column> columnsOut = new ArrayList<>(columnsIn.size());
            columnsIn.forEach(column -> {
                final Column.Builder columnBuilder = column.copy();
                if (column.getExpression() != null) {
                    columnBuilder.expression(ParamUtil.replaceParameters(column.getExpression(),
                            dashboardContext,
                            true));
                }
                if (column.getFilter() != null) {
                    columnBuilder.filter(new IncludeExcludeFilter(
                            ParamUtil.replaceParameters(column.getFilter().getIncludes(),
                                    dashboardContext,
                                    true),
                            ParamUtil.replaceParameters(column.getFilter().getExcludes(),
                                    dashboardContext,
                                    true),
                            column.getFilter().getIncludeDictionaries(),
                            column.getFilter().getExcludeDictionaries()));
                }
                if (column.getColumnFilter() != null) {
                    columnBuilder.columnFilter(new ColumnFilter(ParamUtil
                            .replaceParameters(column.getColumnFilter().getFilter(),
                                    dashboardContext,
                                    true)));
                }
                columnsOut.add(columnBuilder.build());
            });
            builder.columns(columnsOut);
        }

        // Resolve parameters in conditional formatting.
        final List<ConditionalFormattingRule> rulesIn = tableComponentSettings.getConditionalFormattingRules();
        if (rulesIn != null) {
            final List<ConditionalFormattingRule> rulesOut = new ArrayList<>(rulesIn.size());
            rulesIn.forEach(rule -> {
                rulesOut.add(rule.copy().expression(dashboardContext.replaceExpression(rule.getExpression(),
                        true)).build());
            });
            builder.conditionalFormattingRules(rulesOut);
        }

        ExpressionOperator aggregateFilter = currentSelectionFilter;
        if (currentSelectionFilter != null) {
            aggregateFilter = dashboardContext.replaceExpression(aggregateFilter, true);
        }

        return builder.buildTableSettings().copy().aggregateFilter(aggregateFilter).build();
    }

    @Override
    public ComponentResultRequest createDownloadQueryRequest() {
        return tableResultRequest
                .copy()
                .requestedRange(OffsetRange.UNBOUNDED)
                .tableSettings(resolveTableSettings())
                .fetch(Fetch.ALL)
                .build();
    }

    @Override
    public void reset() {
        selectionModel.clear(true);

        final long length = Math.max(1, tableResultRequest.getRequestedRange().getLength());

        // Reset the data grid paging.
        if (dataGrid.getVisibleRange().getStart() > 0) {
            dataGrid.setVisibleRange(0, (int) length);
        }

        tableResultRequest = tableResultRequest
                .copy()
                .requestedRange(new OffsetRange(0L, length))
                .build();
    }

    void refresh() {
        refresh(() -> {
        });
    }

    void onColumnFilterChange() {
        reset();
        refresh();
        getDashboardContext().fireComponentChangeEvent(this);
    }

    public void setFocused(final boolean focused) {

        dataGrid.setFocused(focused);
    }

    void forceNewSearch() {
        if (currentSearchModel != null) {
            pagerView.getRefreshButton().setRefreshing(true);
            currentSearchModel.forceNewSearch(getComponentConfig().getId(), result -> {
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
        }
    }

    void refresh(final Runnable afterRefresh) {
        if (currentSearchModel != null) {
            pagerView.getRefreshButton().setRefreshing(true);
            currentSearchModel.refresh(getComponentConfig().getId(), result -> {
                try {
                    if (result != null) {
                        setDataInternal(result);
                    }
                } catch (final Exception e) {
                    GWT.log(e.getMessage());
                } finally {
                    afterRefresh.run();
                    pagerView.getRefreshButton().setRefreshing(currentSearchModel.isSearching());
                }
            });
        }
    }

    @Override
    public List<ColumnRef> getColumnRefs() {
        return NullSafe.list(getTableComponentSettings().getColumns())
                .stream()
                .map(col -> new ColumnRef(col.getId(), col.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ComponentSelection> getSelection() {
        final List<ColumnRef> columns = NullSafe.list(getColumnRefs());
        return TableComponentSelection.create(columns, selectionModel.getSelectedItems());
    }

    private TableComponentSettings createSettings() {
        List<Long> arr = null;
        if (maxResults != null && maxResults.length > 0) {
            arr = new ArrayList<>();
            arr.add(maxResults[0]);
        }

        return TableComponentSettings.builder().maxResults(arr).build();
    }

    @Override
    public Set<String> getHighlights() {
        if (currentSearchModel != null
            && currentSearchModel.getCurrentResponse() != null
            && currentSearchModel.getCurrentResponse().getHighlights() != null) {
            return currentSearchModel.getCurrentResponse().getHighlights();
        }

        return null;
    }

    @Override
    public void setDesignMode(final boolean designMode) {
        super.setDesignMode(designMode);
        dataGrid.setAllowMove(designMode);
        dataGrid.setAllowHeaderSelection(designMode);
        addColumnButton.setVisible(designMode);
    }

    public SearchModel getCurrentSearchModel() {
        return currentSearchModel;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    @Override
    public synchronized void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        columnSelectionListModel.setTaskMonitorFactory(taskMonitorFactory);
    }

    @Override
    public List<Column> getColumns() {
        return NullSafe.getOrElse(
                getTableComponentSettings(),
                TableComponentSettings::getColumns,
                Collections.emptyList());
    }

    public interface TableView extends View {

        void setTableView(View view);
    }

    @Override
    public ColumnValuesDataSupplier getDataSupplier(final Column column,
                                                    final List<ConditionalFormattingRule> conditionalFormattingRules) {
        return new TableColumnValuesDataSupplier(restFactory,
                currentSearchModel,
                column,
                resolveTableSettings(),
                getDateTimeSettings(),
                getTableName(getId()),
                conditionalFormattingRules);
    }

    private void fireColumnAndDataUpdate() {
        TableUpdateEvent.fire(this);
    }

    @Override
    public HandlerRegistration addUpdateHandler(final TableUpdateEvent.Handler handler) {
        return eventBus.addHandler(TableUpdateEvent.getType(), handler);
    }
}
