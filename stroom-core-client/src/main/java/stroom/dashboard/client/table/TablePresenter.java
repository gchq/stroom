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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.shared.EventId;
import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.HasSelection;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.ResultComponent;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.query.QueryPresenter;
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
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.expression.api.DateTimeSettings;
import stroom.item.client.SelectionBoxModel;
import stroom.item.client.presenter.SelectionPopupView;
import stroom.preferences.client.UserPreferencesManager;
import stroom.processor.shared.ProcessorExpressionUtil;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Field.Builder;
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
import stroom.query.client.presenter.FieldHeader;
import stroom.query.client.presenter.TableRow;
import stroom.query.client.presenter.TimeZones;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.Expander;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.Version;
import stroom.view.client.presenter.DataSourceFieldsMap;
import stroom.view.client.presenter.IndexLoader;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class TablePresenter extends AbstractComponentPresenter<TableView>
        implements HasDirtyHandlers, ResultComponent, HasSelection, TableUiHandlers {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    public static final ComponentType TYPE = new ComponentType(1, "table", "Table", ComponentUse.PANEL);
    private static final Version CURRENT_MODEL_VERSION = new Version(6, 1, 26);

    private final LocationManager locationManager;
    private TableResultRequest tableResultRequest = TableResultRequest.builder()
            .requestedRange(OffsetRange.ZERO_1000)
            .build();
    private final List<Column<TableRow, ?>> existingColumns = new ArrayList<>();
    private final List<HandlerRegistration> searchModelHandlerRegistrations = new ArrayList<>();
    private final ButtonView addFieldButton;
    private final ButtonView downloadButton;
    private final ButtonView annotateButton;
    private final Provider<FieldAddPresenter> fieldAddPresenterProvider;
    private final DownloadPresenter downloadPresenter;
    private final AnnotationManager annotationManager;
    private final RestFactory restFactory;
    private final TimeZones timeZones;
    private final UserPreferencesManager userPreferencesManager;
    private final FieldsManager fieldsManager;
    private final MyDataGrid<TableRow> dataGrid;
    private final MultiSelectionModelImpl<TableRow> selectionModel;
    private final Column<TableRow, Expander> expanderColumn;

    private int expanderColumnWidth;
    private SearchModel currentSearchModel;
    private FieldAddPresenter fieldAddPresenter;
    private boolean ignoreRangeChange;
    private long[] maxResults = TableComponentSettings.DEFAULT_MAX_RESULTS;
    private boolean pause;
    private int currentRequestCount;

    @Inject
    public TablePresenter(final EventBus eventBus,
                          final TableView view,
                          final PagerView pagerView,
                          final ClientSecurityContext securityContext,
                          final LocationManager locationManager,
                          final Provider<RenameFieldPresenter> renameFieldPresenterProvider,
                          final Provider<ColumnFunctionEditorPresenter> expressionPresenterProvider,
                          final FormatPresenter formatPresenter,
                          final FilterPresenter filterPresenter,
                          final Provider<FieldAddPresenter> fieldAddPresenterProvider,
                          final Provider<TableSettingsPresenter> settingsPresenterProvider,
                          final DownloadPresenter downloadPresenter,
                          final AnnotationManager annotationManager,
                          final RestFactory restFactory,
                          final UiConfigCache clientPropertyCache,
                          final TimeZones timeZones,
                          final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view, settingsPresenterProvider);
        this.locationManager = locationManager;
        this.fieldAddPresenterProvider = fieldAddPresenterProvider;
        this.downloadPresenter = downloadPresenter;
        this.annotationManager = annotationManager;
        this.restFactory = restFactory;
        this.timeZones = timeZones;
        this.userPreferencesManager = userPreferencesManager;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        view.setTableView(pagerView);
        view.setUiHandlers(this);

        // Add the 'add field' button.
        addFieldButton = pagerView.addButton(SvgPresets.ADD);
        addFieldButton.setTitle("Add Field");

        // Download
        downloadButton = pagerView.addButton(SvgPresets.DOWNLOAD);
        downloadButton.setVisible(securityContext.hasAppPermission(PermissionNames.DOWNLOAD_SEARCH_RESULTS_PERMISSION));

        // Annotate
        annotateButton = pagerView.addButton(SvgPresets.ANNOTATE);
        annotateButton.setVisible(securityContext.hasAppPermission(PermissionNames.ANNOTATIONS));
        annotateButton.setEnabled(false);

        fieldsManager = new FieldsManager(
                this,
                renameFieldPresenterProvider,
                expressionPresenterProvider,
                formatPresenter,
                filterPresenter);
        dataGrid.setHeadingListener(fieldsManager);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    final String value = result.getDefaultMaxResults();
                    if (value != null) {
                        final String[] parts = value.split(",");
                        final long[] arr = new long[parts.length];
                        for (int i = 0; i < arr.length; i++) {
                            arr[i] = Long.parseLong(parts[i].trim());
                        }
                        maxResults = arr;
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(TablePresenter.this, caught.getMessage(), null));


        // Expander column.
        expanderColumn = new Column<TableRow, Expander>(new ExpanderCell()) {
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
        registerHandler(addFieldButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAddField(event);
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

        registerHandler(annotateButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                annotationManager.showAnnotationMenu(event.getNativeEvent(),
                        getTableSettings(),
                        selectionModel.getSelectedItems());
            }
        }));
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        cleanupSearchModelAssociation();
    }

    @Override
    public void onPause() {
        if (pause) {
            this.pause = false;
            refresh();
        } else {
            this.pause = true;
        }
        getView().setPaused(this.pause);
    }

    private void onAddField(final ClickEvent event) {
        if (currentSearchModel != null && fieldAddPresenter == null) {
            fieldAddPresenter = fieldAddPresenterProvider.get();

            final List<Field> annotationFields = new ArrayList<>();
            final List<Field> dataFields = new ArrayList<>();
            final List<Field> otherFields = new ArrayList<>();

            final IndexLoader indexLoader = currentSearchModel.getIndexLoader();
            if (indexLoader.getIndexFieldNames() != null) {
                final DataSourceFieldsMap indexFieldsMap = indexLoader.getDataSourceFieldsMap();

                for (final String indexFieldName : indexLoader.getIndexFieldNames()) {
                    final Builder fieldBuilder = Field.builder();
                    fieldBuilder.name(indexFieldName);

                    if (indexFieldsMap != null) {
                        final AbstractField indexField = indexFieldsMap.get(indexFieldName);
                        if (indexField != null) {
                            switch (indexField.getFieldType()) {
                                case DATE:
                                    fieldBuilder.format(Format.DATE_TIME);
                                    break;
                                case INTEGER:
                                case LONG:
                                case FLOAT:
                                case DOUBLE:
                                case ID:
                                    fieldBuilder.format(Format.NUMBER);
                                    break;
                                default:
                                    fieldBuilder.format(Format.GENERAL);
                                    break;
                            }
                        }
                    }

                    final String expression;
                    if (indexFieldName.startsWith("annotation:") && indexFieldsMap != null) {
                        // Turn 'annotation:.*' fields into annotation links that make use of either the special
                        // eventId/streamId fields (so event results can link back to annotations) OR
                        // the annotation:Id field so Annotations datasource results can link back.
                        expression = buildAnnotationFieldExpression(indexFieldsMap, indexFieldName);
                        fieldBuilder.expression(expression);
                        annotationFields.add(fieldBuilder.build());
                    } else {
                        expression = ParamSubstituteUtil.makeParam(indexFieldName);
                        fieldBuilder.expression(expression);
                        dataFields.add(fieldBuilder.build());
                    }
                }
            }

            final Field count = Field.builder()
                    .name("Count")
                    .format(Format.NUMBER)
                    .expression("count()")
                    .build();
            otherFields.add(count);

            final Field countGroups = Field.builder()
                    .name("Count Groups")
                    .format(Format.NUMBER)
                    .expression("countGroups()")
                    .build();
            otherFields.add(countGroups);

            final Field custom = Field.builder()
                    .name("Custom")
                    .build();
            otherFields.add(custom);

            final SelectionBoxModel<Field> model = new SelectionBoxModel<>();
            addFieldGroup(model, otherFields, "Counts");
            addFieldGroup(model, dataFields, "Data Source");
            addFieldGroup(model, annotationFields, "Annotations");

            final SelectionPopupView popupView = fieldAddPresenter.getView();

            final Element target = event.getNativeEvent().getEventTarget().cast();
            popupView.setModel(model);
            popupView.addAutoHidePartner(target);
            final PopupPosition popupPosition = new PopupPosition(
                    target.getAbsoluteLeft() - 3,
                    target.getAbsoluteTop() + target.getClientHeight() + 1);
            popupView.show(popupPosition);

            final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
            handlerRegistrations.add(model.addValueChangeHandler(e -> {
                final Field field = model.getValue();
                if (field != null) {
                    HidePopupEvent.builder(fieldAddPresenter).fire();
                    fieldsManager.addField(field);
                }
                popupView.hide();
            }));
            handlerRegistrations.add(popupView.addCloseHandler(closeEvent -> {
                for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                    handlerRegistration.removeHandler();
                }
                handlerRegistrations.clear();
                fieldAddPresenter = null;
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

    private void addFieldGroup(final SelectionBoxModel<Field> model,
                               final List<Field> fields,
                               final String groupName) {
        if (fields.size() > 0) {
            fields.sort(Comparator.comparing(Field::getName, String.CASE_INSENSITIVE_ORDER));
//            items.add(new GroupHeading(items.size(), groupName));
            model.addItems(fields);
        }
    }

    private String buildAnnotationFieldExpression(final DataSourceFieldsMap indexFieldsMap,
                                                  final String indexFieldName) {
        final AbstractField dataSourceField = indexFieldsMap.get(indexFieldName);
        String fieldParam = ParamSubstituteUtil.makeParam(indexFieldName);
        if (dataSourceField != null && FieldType.DATE.equals(dataSourceField.getFieldType())) {
            fieldParam = "formatDate(" + fieldParam + ")";
        }
        final Set<String> allFieldNames = indexFieldsMap.keySet();

        final List<String> params = new ArrayList<>();
        params.add(fieldParam);
        addFieldIfPresent(params, allFieldNames, "annotation:Id");
        addFieldIfPresent(params, allFieldNames, "StreamId");
        addFieldIfPresent(params, allFieldNames, "EventId");

        final String argsStr = String.join(", ", params);
        return "annotation(" + argsStr + ")";
    }

    private void addFieldIfPresent(final List<String> params,
                                   final Set<String> allFields,
                                   final String fieldName) {
        if (allFields.contains(fieldName)) {
            params.add(ParamSubstituteUtil.makeParam(fieldName));
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
                                                .tableSettings(((TableComponentSettings) tableSettings.getValue())
                                                        .copy().buildTableSettings())
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
                                final Rest<ResourceGeneration> rest = restFactory.create();
                                rest
                                        .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager,
                                                null,
                                                result))
                                        .call(DASHBOARD_RESOURCE)
                                        .downloadSearchResults(
                                                currentSearchModel.getCurrentNode(),
                                                downloadSearchResultsRequest);
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
        final List<EventId> eventIdList = annotationManager.getEventIdList(getTableSettings(),
                selectionModel.getSelectedItems());
        final List<Long> annotationIdList = annotationManager.getAnnotationIdList(getTableSettings(),
                selectionModel.getSelectedItems());
        final boolean enabled = eventIdList.size() > 0 || annotationIdList.size() > 0;
        annotateButton.setEnabled(enabled);
    }

    @Override
    public void startSearch() {
        final TableSettings tableSettings = getTableSettings()
                .copy()
                .buildTableSettings();
        tableResultRequest = tableResultRequest
                .copy()
                .tableSettings(tableSettings)
                .build();

        getView().setRefreshing(true);
    }

    @Override
    public void endSearch() {
        getView().setRefreshing(false);
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

                final List<TableRow> values = processData(tableResult.getFields(), tableResult.getRows());
                final OffsetRange valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || values.size() > 0) {
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

    public static AbstractField buildDsField(final Field field) {
        Type colType = Optional.ofNullable(field.getFormat())
                .map(Format::getType)
                .orElse(Type.GENERAL);

        try {
            switch (colType) {
                case NUMBER:
                    return new LongField(field.getName(), true);

                case DATE_TIME:
                    return new DateField(field.getName(), true);

                default:
                    // CONTAINS only supported for legacy content, not for use in UI
                    final List<Condition> conditionList = new ArrayList<>();
                    conditionList.add(Condition.IN);
                    conditionList.add(Condition.EQUALS);
                    return new TextField(field.getName(), true, conditionList);

            }
        } catch (Exception e) {
            GWT.log(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private List<TableRow> processData(final List<Field> fields, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        int maxGroup = -1;
        final boolean showDetail = getTableSettings().showDetail();
        for (final Field field : fields) {
            if (field.getGroup() != null) {
                maxGroup = Math.max(maxGroup, field.getGroup());
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
            SafeStylesBuilder rowStyle = new SafeStylesBuilder();

            // Row styles.
            if (row.getBackgroundColor() != null
                    && !row.getBackgroundColor().isEmpty()) {
                rowStyle.trustedBackgroundColor(row.getBackgroundColor());
            }
            if (row.getTextColor() != null
                    && !row.getTextColor().isEmpty()) {
                rowStyle.trustedColor(row.getTextColor());
            }

            final Map<String, TableRow.Cell> cellsMap = new HashMap<>();
            for (int i = 0; i < fields.size() && i < row.getValues().size(); i++) {
                final Field field = fields.get(i);
                final String value = row.getValues().get(i) != null
                        ? row.getValues().get(i)
                        : "";

                SafeStylesBuilder stylesBuilder = new SafeStylesBuilder();
                stylesBuilder.append(rowStyle.toSafeStyles());

                // Wrap
                if (field.getFormat() != null && field.getFormat().getWrap() != null && field.getFormat().getWrap()) {
                    stylesBuilder.whiteSpace(Style.WhiteSpace.NORMAL);
                }
                // Grouped
                if (field.getGroup() != null && field.getGroup() >= row.getDepth()) {
                    stylesBuilder.fontWeight(Style.FontWeight.BOLD);
                }

                final String style = stylesBuilder.toSafeStyles().asString();

                final TableRow.Cell cell = new TableRow.Cell(value, style);
                cellsMap.put(field.getId(), cell);
            }

            // Create an expander for the row.
            Expander expander = null;
            if (row.getDepth() < maxDepth) {
                final boolean open = tableResultRequest.isGroupOpen(row.getGroupKey());
                expander = new Expander(row.getDepth(), open, false);
            } else if (row.getDepth() > 0) {
                expander = new Expander(row.getDepth(), false, true);
            }

            processed.add(new TableRow(expander, row.getGroupKey(), cellsMap));
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

    private void addColumn(final Field field) {
        final Column<TableRow, SafeHtml> column = new Column<TableRow, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final TableRow row) {
                if (row == null) {
                    return SafeHtmlUtil.NBSP;
                }

                return row.getValue(field.getId());
            }
        };

        final FieldHeader fieldHeader = new FieldHeader(field);
        dataGrid.addResizableColumn(column, fieldHeader, field.getWidth());
        existingColumns.add(column);
    }

    void handleFieldRename(final String oldName,
                           final String newName) {
        if (!Objects.equals(oldName, newName)) {
            if (getTableSettings() != null && getTableSettings().getConditionalFormattingRules() != null) {
                final AtomicBoolean wasModified = new AtomicBoolean(false);
                getTableSettings().getConditionalFormattingRules().stream()
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
        if (getTableSettings().getFields() == null) {
            setSettings(getTableSettings().copy().fields(new ArrayList<>()).build());
        }

        // Update the extraction pipeline if the loaded data source has changed.
        if (currentSearchModel != null) {
            final IndexLoader indexLoader = currentSearchModel.getIndexLoader();
            if (!Objects.equals(getTableSettings().getDataSourceRef(),
                    indexLoader.getLoadedDataSourceRef())) {
                TableComponentSettings.Builder builder = getTableSettings().copy();
                builder.dataSourceRef(indexLoader.getLoadedDataSourceRef());
                DocRef docRef = indexLoader.getDefaultExtractionPipeline();
                if (docRef != null) {
                    builder.extractionPipeline(docRef).extractValues(true);
                }
                setSettings(builder.build());
            }
        }

        // Update columns.
        updateColumns();
    }

    private void ensureSpecialFields(final String... indexFieldNames) {
        // Remove all special fields as we will re-add them with the right names if there are any.
        getTableSettings().getFields().removeIf(Field::isSpecial);

        // Get special fields from the current data source.
        final List<AbstractField> requiredSpecialDsFields = new ArrayList<>();
        final List<Field> requiredSpecialFields = new ArrayList<>();
        // Get all index fields provided by the datasource
        final DataSourceFieldsMap dataSourceFieldsMap = getIndexFieldsMap();
        if (dataSourceFieldsMap != null) {
            for (final String indexFieldName : indexFieldNames) {
                final AbstractField indexField = dataSourceFieldsMap.get(indexFieldName);
                if (indexField != null) {
                    requiredSpecialDsFields.add(indexField);
                    final Field specialField = buildSpecialField(indexFieldName);
                    requiredSpecialFields.add(specialField);
                }
            }

            // If the fields we want to make special do exist in the current data source then
            // add them.
            if (requiredSpecialFields.size() > 0) {
                // Prior to the introduction of the special field concept, special fields were
                // treated as invisible fields. For this reason we need to remove old invisible
                // fields if we haven't yet turned them into special fields.
                final Version version = Version.parse(getTableSettings().getModelVersion());
                final boolean old = version.lt(CURRENT_MODEL_VERSION);
                if (old) {
                    requiredSpecialDsFields.forEach(requiredSpecialDsField ->
                            getTableSettings().getFields().removeIf(field ->
                                    !field.isVisible() && field.getName().equals(requiredSpecialDsField.getName())));
                    setSettings(getTableSettings()
                            .copy()
                            .modelVersion(CURRENT_MODEL_VERSION.toString())
                            .build());
                }

                // Add special fields.
                requiredSpecialFields.forEach(field ->
                        getTableSettings().getFields().add(field));
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
        }
    }

    public static Field buildSpecialField(final String indexFieldName) {
        final String obfuscatedColumnName = IndexConstants.generateObfuscatedColumnName(indexFieldName);
        return Field.builder()
                .id(obfuscatedColumnName)
                .name(obfuscatedColumnName)
                .expression(ParamSubstituteUtil.makeParam(indexFieldName))
                .visible(false)
                .special(true)
                .build();
    }

    DataSourceFieldsMap getIndexFieldsMap() {
        if (currentSearchModel != null
                && currentSearchModel.getIndexLoader() != null
                && currentSearchModel.getIndexLoader().getDataSourceFieldsMap() != null) {
            return currentSearchModel.getIndexLoader().getDataSourceFieldsMap();
        }

        return null;
    }

    void updateColumns() {
        // Now make sure special fields exist for stream id and event id.
        ensureSpecialFields(IndexConstants.STREAM_ID, IndexConstants.EVENT_ID, "Id");

        // Remove existing columns.
        for (final Column<TableRow, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();

        final List<Field> fields = getTableSettings().getFields();
        addExpanderColumn();
        fieldsManager.setFieldsStartIndex(1);

        // Add fields as columns.
        for (final Field field : fields) {
            // Only include the field if it is supposed to be visible.
            if (field.isVisible()) {
                addColumn(field);
            }
        }

        dataGrid.resizeTableToFitColumns();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public ComponentType getType() {
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
        if (getTableSettings().getFields() != null) {
            final String obfuscatedStreamId = IndexConstants.generateObfuscatedColumnName(IndexConstants.STREAM_ID);
            final String obfuscatedEventId = IndexConstants.generateObfuscatedColumnName(IndexConstants.EVENT_ID);

            final List<Field> fields = new ArrayList<>();
            getTableSettings().getFields().forEach(field -> {
                Field f = field;
                if (obfuscatedStreamId.equals(f.getName())) {
                    f = buildSpecialField(IndexConstants.STREAM_ID);
                } else if (obfuscatedEventId.equals(f.getName())) {
                    f = buildSpecialField(IndexConstants.EVENT_ID);
                } else if (field.getId() == null) {
                    f = field.copy().id(fieldsManager.createRandomFieldId(usedFieldIds)).build();
                }
                usedFieldIds.add(field.getId());
                fields.add(f);
            });
            setSettings(getTableSettings().copy().fields(fields).build());
        }
    }

    public TableComponentSettings getTableSettings() {
        return (TableComponentSettings) getSettings();
    }

    @Override
    public void link() {
        String queryId = getTableSettings().getQueryId();
        queryId = getComponents().validateOrGetLastComponentId(queryId, QueryPresenter.TYPE.getId());
        setSettings(getTableSettings().copy().queryId(queryId).build());
        setQueryId(queryId);
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        final TableComponentSettings tableComponentSettings = getTableSettings();
        setQueryId(tableComponentSettings.getQueryId());
        updatePageSize();
    }

    private void updatePageSize() {
        final TableComponentSettings tableComponentSettings = getTableSettings();
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
        return tableResultRequest.copy().fetch(fetch).build();
    }

    @Override
    public ComponentResultRequest createDownloadQueryRequest() {
        final TableSettings tableSettings = getTableSettings()
                .copy()
                .buildTableSettings();
        return tableResultRequest
                .copy()
                .requestedRange(OffsetRange.UNBOUNDED)
                .tableSettings(tableSettings)
                .fetch(Fetch.ALL)
                .build();
    }

    @Override
    public void reset() {
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

    @Override
    public List<AbstractField> getFields() {
        final List<AbstractField> abstractFields = new ArrayList<>();
        final List<Field> fields = getTableSettings().getFields();
        if (fields != null && fields.size() > 0) {
            for (final Field field : fields) {
                abstractFields.add(new TextField(field.getName(), true));
            }
        }
        return abstractFields;
    }

    @Override
    public List<Map<String, String>> getSelection() {
        final List<Map<String, String>> list = new ArrayList<>();
        final List<Field> fields = getTableSettings().getFields();
        for (final TableRow tableRow : getSelectedRows()) {
            final Map<String, String> map = new HashMap<>();
            for (final Field field : fields) {
                map.put(field.getName(), tableRow.getText(field.getId()));
            }
            list.add(map);
        }
        return list;
    }

    private void refresh() {
        if (currentSearchModel != null) {
            currentRequestCount++;
            getView().setPaused(pause && currentRequestCount == 0);
            getView().setRefreshing(true);
            currentSearchModel.refresh(getComponentConfig().getId(), result -> {
                try {
                    if (result != null) {
                        setDataInternal(result);
                    }
                } catch (final Exception e) {
                    GWT.log(e.getMessage());
                }
                currentRequestCount--;
                getView().setPaused(pause && currentRequestCount == 0);
                getView().setRefreshing(currentSearchModel.isSearching());
            });
        }
    }

    void clear() {
        setDataInternal(null);
    }

    public List<TableRow> getSelectedRows() {
        return selectionModel.getSelectedItems();
    }

    private TableComponentSettings createSettings() {
        List<Long> arr = null;
        if (maxResults != null && maxResults.length > 0) {
            arr = new ArrayList<>();
            arr.add(maxResults[0]);
        }

        return TableComponentSettings.builder().maxResults(arr).build();
    }

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
        addFieldButton.setVisible(designMode);
    }

    public interface TableView extends View, HasUiHandlers<TableUiHandlers> {

        void setTableView(View view);

        void setRefreshing(boolean refreshing);

        void setPaused(boolean paused);
    }
}