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
import stroom.annotation.shared.EventId;
import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.Components;
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
import stroom.dashboard.shared.IndexConstants;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.expression.api.DateTimeSettings;
import stroom.item.client.SelectionPopup;
import stroom.preferences.client.UserPreferencesManager;
import stroom.processor.shared.ProcessorExpressionUtil;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ColumnRef;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Format.Type;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.client.DataSourceClient;
import stroom.query.client.presenter.ColumnHeader;
import stroom.query.client.presenter.DynamicColumnSelectionListModel;
import stroom.query.client.presenter.DynamicColumnSelectionListModel.ColumnSelectionItem;
import stroom.query.client.presenter.TableRow;
import stroom.query.client.presenter.TimeZones;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.Expander;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Version;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
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
        implements HasDirtyHandlers, ResultComponent, HasComponentSelection {

    public static final String TAB_TYPE = "table-component";
    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    public static final ComponentType TYPE = new ComponentType(1, "table", "Table", ComponentUse.PANEL);
    private static final Version CURRENT_MODEL_VERSION = new Version(6, 1, 26);

    private final PagerView pagerView;
    private final DataSourceClient dataSourceClient;
    private final LocationManager locationManager;
    private TableResultRequest tableResultRequest = TableResultRequest.builder()
            .requestedRange(OffsetRange.ZERO_1000)
            .build();
    private final List<com.google.gwt.user.cellview.client.Column<TableRow, ?>> existingColumns = new ArrayList<>();
    private final List<HandlerRegistration> searchModelHandlerRegistrations = new ArrayList<>();
    private final ButtonView addColumnButton;
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
    private final TableRowStyles tableRowStyles;

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
                          final DataSourceClient dataSourceClient) {
        super(eventBus, view, settingsPresenterProvider);
        this.pagerView = pagerView;
        this.locationManager = locationManager;
        this.downloadPresenter = downloadPresenter;
        this.annotationManager = annotationManager;
        this.restFactory = restFactory;
        this.timeZones = timeZones;
        this.userPreferencesManager = userPreferencesManager;
        this.columnSelectionListModel = columnSelectionListModel;
        this.dataSourceClient = dataSourceClient;
        tableRowStyles = new TableRowStyles(userPreferencesManager);

        columnSelectionListModel.setTaskMonitorFactory(this);

        dataGrid = new MyDataGrid<>();
        dataGrid.addStyleName("TablePresenter");
        dataGrid.setRowStyles(tableRowStyles);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        view.setTableView(pagerView);

        // Add the 'add column' button.
        addColumnButton = pagerView.addButton(SvgPresets.ADD);
        addColumnButton.setTitle("Add Column");

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
        annotateButton.setVisible(securityContext
                .hasAppPermission(AppPermission.ANNOTATIONS));
        annotateButton.setEnabled(false);

        columnsManager = new ColumnsManager(
                this,
                renameColumnPresenterProvider,
                expressionPresenterProvider,
                formatPresenter,
                tableFilterPresenter);
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
        expanderColumn.setFieldUpdater((index, result, value) -> {
            tableResultRequest = tableResultRequest
                    .copy()
                    .openGroup(result.getGroupKey(), !value.isExpanded())
                    .build();
            refresh();
        });

        pagerView.getRefreshButton().setAllowPause(true);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableAnnotate();
            getComponents().fireComponentChangeEvent(this);
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
        registerHandler(dataGrid.addHyperlinkHandler(event -> getEventBus().fireEvent(event)));
        registerHandler(addColumnButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAddColumn(event);
            }
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
                annotationManager.showAnnotationMenu(event.getNativeEvent(),
                        getTableComponentSettings(),
                        selectionModel.getSelectedItems());
            }
        }));

        registerHandler(pagerView.getRefreshButton().addClickHandler(event -> {
            setPause(!pause, true);
        }));
    }

    @Override
    public void setComponents(final Components components) {
        super.setComponents(components);

        registerHandler(components.addComponentChangeHandler(event -> {
            final ExpressionOperator selectionFilter = SelectionHandlerExpressionBuilder
                    .create(components.getComponents(), getTableComponentSettings().getSelectionFilter())
                    .orElse(null);
            if (!Objects.equals(currentSelectionFilter, selectionFilter)) {
                currentSelectionFilter = selectionFilter;
                onColumnFilterChange();
            }
        }));
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
            columnSelectionListModel.setDataSourceRef(currentSearchModel.getIndexLoader().getLoadedDataSourceRef());

            if (addColumnPopup == null) {
                addColumnPopup = new SelectionPopup<>();
                addColumnPopup.init(columnSelectionListModel);
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
                                                .tableSettings(getTableSettings())
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
        return Optional.ofNullable(getComponents().get(componentId))
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

    private void enableAnnotate() {
        final List<EventId> eventIdList = annotationManager.getEventIdList(getTableComponentSettings(),
                selectionModel.getSelectedItems());
        final List<Long> annotationIdList = annotationManager.getAnnotationIdList(getTableComponentSettings(),
                selectionModel.getSelectedItems());
        final boolean enabled = eventIdList.size() > 0 || annotationIdList.size() > 0;
        annotateButton.setEnabled(enabled);
    }

    @Override
    public void startSearch() {
        tableResultRequest = tableResultRequest
                .copy()
                .tableSettings(getTableSettings())
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

    private void setDataInternal(final Result componentResult) {
        ignoreRangeChange = true;

        try {
            if (componentResult != null) {
                // Don't refresh the table unless the results have changed.
                final TableResult tableResult = (TableResult) componentResult;

                final List<TableRow> values = processData(tableResult.getColumns(), tableResult.getRows());
                final OffsetRange valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || values.size() > 0) {
                    tableRowStyles.setConditionalFormattingRules(getTableSettings()
                            .getConditionalFormattingRules());
                    dataGrid.setRowData((int) valuesRange.getOffset(), values);
                    dataGrid.setRowCount(tableResult.getTotalResults().intValue(), true);
                }

                // Enable download of current results.
                downloadButton.setEnabled(true);
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

    public static QueryField buildDsField(final Column column) {
        Type colType = Optional.ofNullable(column.getFormat())
                .map(Format::getType)
                .orElse(Type.GENERAL);

        try {
            switch (colType) {
                case NUMBER:
                    return QueryField.createLong(column.getName());

                case DATE_TIME:
                    return QueryField.createDate(column.getName());

                default:
                    // CONTAINS only supported for legacy content, not for use in UI
                    return QueryField
                            .builder()
                            .fldName(column.getName())
                            .fldType(FieldType.TEXT)
                            .conditionSet(ConditionSet.BASIC_TEXT)
                            .queryable(true)
                            .build();

            }
        } catch (Exception e) {
            GWT.log(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private List<TableRow> processData(final List<Column> columns, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
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

        final List<TableRow> processed = new ArrayList<>(values.size());
        for (final Row row : values) {
            final Map<String, TableRow.Cell> cellsMap = new HashMap<>();
            for (int i = 0; i < columns.size() && i < row.getValues().size(); i++) {
                final Column column = columns.get(i);
                final String value = row.getValues().get(i) != null
                        ? row.getValues().get(i)
                        : "";

                SafeStylesBuilder stylesBuilder = new SafeStylesBuilder();

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
                final boolean open = tableResultRequest.isGroupOpen(row.getGroupKey());
                expander = new Expander(row.getDepth(), open, false);
            } else if (row.getDepth() > 0) {
                expander = new Expander(row.getDepth(), false, true);
            }

            processed.add(new TableRow(
                    expander,
                    row.getGroupKey(),
                    cellsMap,
                    row.getMatchingRule()));
        }

        // Set the expander column width.
        expanderColumnWidth = ExpanderCell.getColumnWidth(maxDepth);
        dataGrid.setColumnWidth(expanderColumn, expanderColumnWidth, Unit.PX);

        return processed;
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

    void handleFieldRename(final String oldName,
                           final String newName) {
        if (!Objects.equals(oldName, newName)) {
            if (getTableComponentSettings() != null &&
                getTableComponentSettings().getConditionalFormattingRules() != null) {
                final AtomicBoolean wasModified = new AtomicBoolean(false);
                getTableComponentSettings().getConditionalFormattingRules().stream()
                        .map(ConditionalFormattingRule::getExpression)
                        .forEach(expressionOperator -> {
                            boolean wasRuleModified = renameField(expressionOperator, oldName, newName);
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
            final Component component = getComponents().get(queryId);
            if (component instanceof QueryPresenter) {
                final QueryPresenter queryPresenter = (QueryPresenter) component;
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

    private void ensureSpecialFields(final String... indexFieldNames) {
        // Remove all special fields as we will re-add them.
        getTableComponentSettings().getColumns().removeIf(Column::isSpecial);

        final Optional<Integer> maxGroup = getTableComponentSettings()
                .getColumns()
                .stream()
                .map(Column::getGroup)
                .filter(Objects::nonNull)
                .max(Integer::compareTo);
        if (getTableComponentSettings().showDetail() || maxGroup.isEmpty()) {
            final List<Column> requiredSpecialColumns = new ArrayList<>();
            for (final String indexFieldName : indexFieldNames) {
                final Column specialColumn = buildSpecialColumn(indexFieldName);
                requiredSpecialColumns.add(specialColumn);
            }

            // If the fields we want to make special do exist in the current data source then
            // add them.
            if (requiredSpecialColumns.size() > 0) {
                // Prior to the introduction of the special field concept, special fields were
                // treated as invisible fields. For this reason we need to remove old invisible
                // fields if we haven't yet turned them into special fields.
                final Version version = Version.parse(getTableComponentSettings().getModelVersion());
                final boolean old = version.lt(CURRENT_MODEL_VERSION);
                if (old) {
                    requiredSpecialColumns.forEach(requiredSpecialDsColumn ->
                            getTableComponentSettings().getColumns().removeIf(column ->
                                    !column.isVisible() && column.getName().equals(requiredSpecialDsColumn.getName())));
                    setSettings(getTableComponentSettings()
                            .copy()
                            .modelVersion(CURRENT_MODEL_VERSION.toString())
                            .build());
                }

                // Add special fields.
                requiredSpecialColumns.forEach(column ->
                        getTableComponentSettings().getColumns().add(column));
            }
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

    public static Column buildSpecialColumn(final String indexFieldName) {
        final String reservedColumnName = IndexConstants.generateReservedColumnName(indexFieldName);
        return Column.builder()
                .id(reservedColumnName)
                .name(reservedColumnName)
                .expression(ParamSubstituteUtil.makeParam(indexFieldName))
                .visible(false)
                .special(true)
                .build();
    }

    void updateColumns() {
        // Now make sure special fields exist for stream id and event id.
        ensureSpecialFields(IndexConstants.STREAM_ID, IndexConstants.EVENT_ID, "Id");

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
                .build();

        ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof TableComponentSettings)) {
            setSettings(createSettings());
        }

        // Update the page size for the data grid.
        updatePageSize();

        // Ensure all fields have ids.
        final Set<String> usedFieldIds = new HashSet<>();
        if (getTableComponentSettings().getColumns() != null) {
            final String reservedStreamId = IndexConstants.RESERVED_STREAM_ID_FIELD_NAME;
            final String reservedEventId = IndexConstants.RESERVED_EVENT_ID_FIELD_NAME;

            final List<Column> columns = new ArrayList<>();
            getTableComponentSettings().getColumns().forEach(column -> {
                Column col = column;
                if (reservedStreamId.equals(col.getName())) {
                    col = buildSpecialColumn(IndexConstants.STREAM_ID);
                } else if (reservedEventId.equals(col.getName())) {
                    col = buildSpecialColumn(IndexConstants.EVENT_ID);
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
    }

    public TableComponentSettings getTableComponentSettings() {
        return (TableComponentSettings) getSettings();
    }

    @Override
    public void link() {
        String queryId = getTableComponentSettings().getQueryId();
        queryId = getComponents().validateOrGetLastComponentId(queryId, QueryPresenter.TYPE.getId());
        setSettings(getTableComponentSettings().copy().queryId(queryId).build());
        setQueryId(queryId);
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        final TableComponentSettings tableComponentSettings = getTableComponentSettings();
        setQueryId(tableComponentSettings.getQueryId());
        updatePageSize();

        // Update styles and re-render
        tableRowStyles.setConditionalFormattingRules(getTableSettings().getConditionalFormattingRules());
        dataGrid.redraw();
    }

    private void updatePageSize() {
        final TableComponentSettings tableComponentSettings = getTableComponentSettings();
        final int start = dataGrid.getVisibleRange().getStart();
        dataGrid.setVisibleRange(new Range(
                start,
                tableComponentSettings.getPageSize() == null
                        ? 100
                        : tableComponentSettings.getPageSize()));
    }

    @Override
    public boolean isPaused() {
        return pause;
    }

    @Override
    public ComponentResultRequest getResultRequest(final Fetch fetch) {
        return tableResultRequest.copy().tableSettings(getTableSettings()).fetch(fetch).build();
    }

    public TableSettings getTableSettings() {
        TableSettings tableSettings = getTableComponentSettings()
                .copy()
                .buildTableSettings();
        return tableSettings.copy().aggregateFilter(currentSelectionFilter).build();
    }

    @Override
    public ComponentResultRequest createDownloadQueryRequest() {
        return tableResultRequest
                .copy()
                .requestedRange(OffsetRange.UNBOUNDED)
                .tableSettings(getTableSettings())
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
        refresh();
        getComponents().fireComponentChangeEvent(this);
    }

    public TableSectionElement getTableHeadElement() {
        return dataGrid.getTableHeadElement();
    }

    public void setFocused(final boolean focused) {
        dataGrid.setFocused(focused);
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
                }
                pagerView.getRefreshButton().setRefreshing(currentSearchModel.isSearching());
            });
        }
    }

    void clear() {
        setDataInternal(null);
    }

    @Override
    public List<ColumnRef> getColumns() {
        return GwtNullSafe.list(getTableComponentSettings().getColumns())
                .stream()
                .map(col -> new ColumnRef(col.getId(), col.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ComponentSelection> getSelection() {
        return GwtNullSafe.list(selectionModel.getSelectedItems())
                .stream()
                .map(tableRow -> {
                    final Map<String, String> values = new HashMap<>();
                    final List<ColumnRef> columns = GwtNullSafe.list(getColumns());

                    for (final ColumnRef column : columns) {
                        if (column.getId() != null) {
                            final String value = tableRow.getText(column.getId());
                            if (value != null) {
                                values.computeIfAbsent(column.getId(), k -> value);
                            }
                        }
                    }
                    for (final ColumnRef column : columns) {
                        if (column.getName() != null) {
                            final String value = tableRow.getText(column.getName());
                            if (value != null) {
                                values.computeIfAbsent(column.getName(), k -> value);
                            }
                        }
                    }

                    return new ComponentSelection(values);
                })
                .collect(Collectors.toList());
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

    public interface TableView extends View {

        void setTableView(View view);
    }
}
