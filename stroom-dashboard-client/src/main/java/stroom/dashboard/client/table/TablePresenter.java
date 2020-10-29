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

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.shared.EventId;
import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ResultComponent;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.dashboard.client.table.cf.ExpressionMatcher;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentResult;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.ConditionalFormattingRule;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DataSourceFieldsMap;
import stroom.dashboard.shared.DownloadSearchResultsAction;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Format;
import stroom.dashboard.shared.Format.Type;
import stroom.dashboard.shared.IndexConstants;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.process.shared.ExpressionUtil;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.shared.v2.ParamUtil;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.Expander;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RandomId;
import stroom.util.shared.Version;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

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
        implements HasDirtyHandlers, ResultComponent {
    public static final ComponentType TYPE = new ComponentType(1, "table", "Table");

    private static final int MIN_EXPANDER_COL_WIDTH = 0;
    private static final Version CURRENT_MODEL_VERSION = new Version(6, 1, 26);

    private final LocationManager locationManager;
    private final TableResultRequest tableResultRequest = new TableResultRequest(0, 100);
    private final List<Column<TableRow, ?>> existingColumns = new ArrayList<>();
    private final List<HandlerRegistration> searchModelHandlerRegistrations = new ArrayList<>();
    private final ButtonView addFieldButton;
    private final ButtonView downloadButton;
    private final ButtonView annotateButton;
    private final Provider<FieldAddPresenter> fieldAddPresenterProvider;
    private final DownloadPresenter downloadPresenter;
    private final AnnotationManager annotationManager;
    private final ClientDispatchAsync dispatcher;
    private final TimeZones timeZones;
    private final FieldsManager fieldsManager;
    private final DataGridView<TableRow> dataGrid;
    private final Column<TableRow, Expander> expanderColumn;

    private int expanderColumnWidth;
    private SearchModel currentSearchModel;
    private FieldAddPresenter fieldAddPresenter;

    private TableComponentSettings tableSettings;
    private boolean ignoreRangeChange;
    private int[] maxResults = TableComponentSettings.DEFAULT_MAX_RESULTS;
    private final Set<String> usedFieldIds = new HashSet<>();

    @Inject
    public TablePresenter(final EventBus eventBus,
                          final TableView view,
                          final ClientSecurityContext securityContext,
                          final LocationManager locationManager,
                          final MenuListPresenter menuListPresenter,
                          final Provider<RenameFieldPresenter> renameFieldPresenterProvider,
                          final Provider<ExpressionPresenter> expressionPresenterProvider,
                          final FormatPresenter formatPresenter,
                          final FilterPresenter filterPresenter,
                          final Provider<FieldAddPresenter> fieldAddPresenterProvider,
                          final Provider<TableSettingsPresenter> settingsPresenterProvider,
                          final DownloadPresenter downloadPresenter,
                          final AnnotationManager annotationManager,
                          final ClientDispatchAsync dispatcher,
                          final ClientPropertyCache clientPropertyCache,
                          final TimeZones timeZones) {
        super(eventBus, view, settingsPresenterProvider);
        this.locationManager = locationManager;
        this.fieldAddPresenterProvider = fieldAddPresenterProvider;
        this.downloadPresenter = downloadPresenter;
        this.annotationManager = annotationManager;
        this.dispatcher = dispatcher;
        this.timeZones = timeZones;
        this.dataGrid = new DataGridViewImpl<>(true, true);

        view.setTableView(dataGrid);

        // Add the 'add field' button.
        addFieldButton = dataGrid.addButton(SvgPresets.ADD);
        addFieldButton.setTitle("Add Field");

        // Download
        downloadButton = dataGrid.addButton(SvgPresets.DOWNLOAD);
        downloadButton.setVisible(securityContext.hasAppPermission(Dashboard.DOWNLOAD_SEARCH_RESULTS_PERMISSION));

        // Annotate
        annotateButton = dataGrid.addButton(SvgPresets.ANNOTATE);
        annotateButton.setVisible(securityContext.hasAppPermission(PermissionNames.ANNOTATIONS));
        annotateButton.setEnabled(false);

        fieldsManager = new FieldsManager(this, menuListPresenter, renameFieldPresenterProvider, expressionPresenterProvider, formatPresenter,
                filterPresenter);
        dataGrid.setHeadingListener(fieldsManager);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    final String value = result.get(ClientProperties.DEFAULT_MAX_RESULTS);
                    if (value != null) {
                        final String[] parts = value.split(",");
                        final int[] arr = new int[parts.length];
                        for (int i = 0; i < arr.length; i++) {
                            arr[i] = Integer.parseInt(parts[i].trim());
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
            tableResultRequest.setGroupOpen(result.getGroupKey(), !value.isExpanded());
            refresh();
        });
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.getSelectionModel().addSelectionHandler(event -> {
            enableAnnotate();
            getComponents().fireComponentChangeEvent(this);
        }));
        registerHandler(dataGrid.addRangeChangeHandler(event -> {
            final com.google.gwt.view.client.Range range = event.getNewRange();
            tableResultRequest.setRange(range.getStart(), range.getLength());
            if (!ignoreRangeChange) {
                refresh();
            }
        }));
        registerHandler(dataGrid.addHyperlinkHandler(event -> getEventBus().fireEvent(event)));
        registerHandler(addFieldButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onAddField(event);
            }
        }));

        registerHandler(downloadButton.addClickHandler(event -> {
            if (currentSearchModel != null) {
                if (currentSearchModel.isSearching()) {
                    ConfirmEvent.fire(TablePresenter.this,
                            "Search still in progress. Do you want to download the current results? Note that these may be incomplete.",
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
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                annotationManager.showAnnotationMenu(event.getNativeEvent(), getSettings(), dataGrid.getSelectionModel().getSelectedItems());
            }
        }));
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        cleanupSearchModelAssociation();
    }

    private void onAddField(final ClickEvent event) {
        if (currentSearchModel != null && fieldAddPresenter == null) {
            fieldAddPresenter = fieldAddPresenterProvider.get();
            final AddSelectionHandler selectionHandler = new AddSelectionHandler(fieldAddPresenter);
            final HandlerRegistration handlerRegistration = fieldAddPresenter
                    .addSelectionChangeHandler(selectionHandler);

            final List<Field> addFields = new ArrayList<>();
            if (currentSearchModel.getIndexLoader().getIndexFieldNames() != null) {
                for (final String indexFieldName : currentSearchModel.getIndexLoader().getIndexFieldNames()) {
                    final Field field = new Field(indexFieldName);
                    final String fieldParam = ParamUtil.makeParam(indexFieldName);

                    if (indexFieldName.startsWith("annotation:")) {
                        final DataSourceField dataSourceField = currentSearchModel.getIndexLoader().getDataSourceFieldsMap().get(indexFieldName);
                        if (dataSourceField != null && dataSourceField.getType() == DataSourceFieldType.DATE_FIELD) {
                            field.setExpression("annotation(formatDate(" + fieldParam + "), ${annotation:Id}, ${StreamId}, ${EventId})");
                        } else {
                            field.setExpression("annotation(" + fieldParam + ", ${annotation:Id}, ${StreamId}, ${EventId})");
                        }
                    } else {
                        field.setExpression(fieldParam);
                    }

                    final DataSourceFieldsMap indexFieldsMap = getIndexFieldsMap();
                    if (indexFieldsMap != null) {
                        final DataSourceField indexField = indexFieldsMap.get(indexFieldName);
                        if (indexField != null) {
                            switch (indexField.getType()) {
                                case ID_FIELD:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                case BOOLEAN_FIELD:
                                    field.setFormat(new Format(Type.GENERAL));
                                    break;
                                case INTEGER_FIELD:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                case LONG_FIELD:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                case FLOAT_FIELD:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                case DOUBLE_FIELD:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                case DATE_FIELD:
                                    field.setFormat(new Format(Type.DATE_TIME));
                                    break;
                                case TEXT_FIELD:
                                    field.setFormat(new Format(Type.GENERAL));
                                    break;
                                case DOC_REF:
                                    field.setFormat(new Format(Type.GENERAL));
                                    break;
                                default:
                                    field.setFormat(new Format(Type.GENERAL));
                                    break;
                            }
                        }
                    }

                    addFields.add(field);
                }
            }

            final Field count = new Field("Count");
            count.setFormat(new Format(Type.NUMBER));
            count.setExpression("count()");
            addFields.add(count);

            final Field countGroups = new Field("Count Groups");
            countGroups.setFormat(new Format(Type.NUMBER));
            countGroups.setExpression("countGroups()");
            addFields.add(countGroups);

            final Field custom = new Field("Custom");
            addFields.add(custom);

            fieldAddPresenter.setFields(addFields);
            fieldAddPresenter.clearSelection();

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    HidePopupEvent.fire(TablePresenter.this, fieldAddPresenter);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    handlerRegistration.removeHandler();
                    fieldAddPresenter = null;
                }
            };

            final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();

            final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 3,
                    target.getAbsoluteTop() + target.getClientHeight() + 1);
            ShowPopupEvent.fire(this, fieldAddPresenter, PopupType.POPUP, popupPosition, popupUiHandlers, target);
        }
    }

    private String createRandomFieldId() {
        String id = getComponentConfig().getId() + "|" + RandomId.createId(5);
        // Make sure we don't duplicate ids.
        while (usedFieldIds.contains(id)) {
            id = getComponentConfig().getId() + "|" + RandomId.createId(5);
        }
        usedFieldIds.add(id);
        return id;
    }

    private void download() {
        if (currentSearchModel != null) {
            final Search activeSearch = currentSearchModel.getActiveSearch();
            final DashboardQueryKey queryKey = currentSearchModel.getCurrentQueryKey();
            if (activeSearch != null && queryKey != null) {
                final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            final TableResultRequest tableResultRequest = new TableResultRequest(0, Integer.MAX_VALUE);
                            tableResultRequest.setTableSettings(TablePresenter.this.tableResultRequest.getTableSettings());
                            tableResultRequest.setFetch(Fetch.ALL);

                            final Map<String, ComponentResultRequest> requestMap = new HashMap<>();
                            requestMap.put(getComponentConfig().getId(), tableResultRequest);

                            final Search search = new Search.Builder()
                                    .dataSourceRef(activeSearch.getDataSourceRef())
                                    .expression(activeSearch.getExpression())
                                    .componentSettingsMap(activeSearch.getComponentSettingsMap())
                                    .paramMap(activeSearch.getParamMap())
                                    .incremental(true)
                                    .storeHistory(false)
                                    .queryInfo(activeSearch.getQueryInfo())
                                    .build();

                            final SearchRequest searchRequest = new SearchRequest(search, requestMap, timeZones.getTimeZone());

                            dispatcher.exec(
                                    new DownloadSearchResultsAction(queryKey, searchRequest, getComponentConfig().getId(),
                                            downloadPresenter.getFileType(), downloadPresenter.isSample(),
                                            downloadPresenter.getPercent(), timeZones.getTimeZone()))
                                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, null, result));
                        }

                        HidePopupEvent.fire(TablePresenter.this, downloadPresenter);
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                    }
                };

                final PopupSize popupSize = new PopupSize(316, 124, false);
                ShowPopupEvent.fire(this, downloadPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Download Options",
                        popupUiHandlers);
            }
        }
    }

    private void enableAnnotate() {
        final List<EventId> idList = annotationManager.getEventIdList(getSettings(), dataGrid.getSelectionModel().getSelectedItems());
        final boolean enabled = idList.size() > 0;
        annotateButton.setEnabled(enabled);
    }


    @Override
    public void startSearch() {
        tableResultRequest.setTableSettings(tableSettings.copy());
    }

    @Override
    public void endSearch() {
    }

    @Override
    public void setWantsData(final boolean wantsData) {
        getView().setRefreshing(wantsData);
        if (wantsData) {
            tableResultRequest.setFetch(Fetch.CHANGES);
        } else {
            tableResultRequest.setFetch(Fetch.NONE);
        }
    }

    @Override
    public void setData(final ComponentResult componentResult) {
        ignoreRangeChange = true;

        try {
            if (componentResult != null) {
                // Don't refresh the table unless the results have changed.
                final TableResult tableResult = (TableResult) componentResult;

                final List<TableRow> values = processData(tableResult.getFields(), tableResult.getRows());
                final OffsetRange<Integer> valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || values.size() > 0) {
                    dataGrid.setRowData(valuesRange.getOffset(), values);
                    dataGrid.setRowCount(tableResult.getTotalResults(), true);
                }

                // Enable download of current results.
                downloadButton.setEnabled(true);
            } else {
                // Disable download of current results.
                downloadButton.setEnabled(false);

                dataGrid.setRowData(0, new ArrayList<>());
                dataGrid.setRowCount(0, true);

                dataGrid.getSelectionModel().clear();
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage());
        }

        ignoreRangeChange = false;
    }

    public static DataSourceField buildDsField(final Field field) {
        Type colType = Optional.ofNullable(field.getFormat())
                .map(Format::getType)
                .orElse(Type.GENERAL);

        try {
            final DataSourceFieldType type;
            switch (colType) {
                case NUMBER:
                    type = DataSourceFieldType.DOUBLE_FIELD;
                    break;
                case DATE_TIME:
                    type = DataSourceFieldType.DATE_FIELD;
                    break;
                default:
                    type = DataSourceFieldType.TEXT_FIELD;
                    break;
            }

            final ExpressionTerm.Condition[] conditions;
            switch (colType) {
                case NUMBER:
                    conditions = new ExpressionTerm.Condition[]{
                            ExpressionTerm.Condition.IN,
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.GREATER_THAN,
                            ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO,
                            ExpressionTerm.Condition.LESS_THAN,
                            ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO};
                    break;
                case DATE_TIME:
                    conditions = new ExpressionTerm.Condition[]{
                            ExpressionTerm.Condition.BETWEEN,
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.GREATER_THAN,
                            ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO,
                            ExpressionTerm.Condition.LESS_THAN,
                            ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO};
                    break;
                default:
                    // CONTAINS only supported for legacy content, not for use in UI
                    conditions = new ExpressionTerm.Condition[]{
                            ExpressionTerm.Condition.IN,
                            ExpressionTerm.Condition.EQUALS};
                    break;
            }

            return new DataSourceField
                    .Builder()
                    .name(field.getName())
                    .type(type)
                    .addConditions(conditions)
                    .build();
        } catch (Exception e) {
            GWT.log(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private List<TableRow> processData(final List<Field> fields, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        int maxGroup = -1;
        final boolean showDetail = tableSettings.showDetail();
        for (final Field field : fields) {
            if (field.getGroup() != null) {
                final int group = field.getGroup();
                if (group > maxGroup) {
                    maxGroup = group;
                }
            }
        }
        int maxDepth = maxGroup;
        if (showDetail) {
            maxDepth++;
        }

        final List<ConditionalFormattingRule> rules = tableSettings.getConditionalFormattingRules();
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(tableSettings.getFields());

        final List<TableRow> processed = new ArrayList<>(values.size());
        int hiddenRowCount = 0;
        for (final Row row : values) {
            boolean hide = false;
            SafeStylesBuilder rowStyle = new SafeStylesBuilder();

            // Conditional formatting
            if (rules != null && rules.size() > 0) {
                try {
                    ConditionalFormattingRule matchingRule = null;
                    for (final ConditionalFormattingRule rule : rules) {
                        try {
                            if (rule.isEnabled()) {
                                final Map<String, Object> fieldIdToValueMap = new HashMap<>();
                                for (int i = 0; i < fields.size() && i < row.getValues().size(); i++) {
                                    final Field field = fields.get(i);
                                    final String value = row.getValues().get(i);
                                    fieldIdToValueMap.put(field.getName(), value);
                                }

                                final ExpressionOperator operator = rule.getExpression();
                                final boolean match = expressionMatcher.match(fieldIdToValueMap, operator);
                                if (match) {
                                    matchingRule = rule;
                                    break;
                                }
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }
                    }
                    if (matchingRule != null) {
                        if (matchingRule.isHide()) {
                            hide = true;
                        } else {
                            if (matchingRule.getBackgroundColor() != null
                                    && !matchingRule.getBackgroundColor().isEmpty()) {
                                rowStyle.trustedBackgroundColor(matchingRule.getBackgroundColor());
                            }
                            if (matchingRule.getTextColor() != null
                                    && !matchingRule.getTextColor().isEmpty()) {
                                rowStyle.trustedColor(matchingRule.getTextColor());
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    GWT.log(e.getMessage());
                }
            }

            if (!hide) {
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

            } else {
                hiddenRowCount++;
            }
        }

        // Add some empty rows to the end if we have some hidden ones.
        // This is rubbish but currently necessary to deal with the way tables work in GWT.
        for (int i = 0; i < hiddenRowCount; i++) {
            processed.add(null);
        }

        // Set the expander column width.
        if (maxDepth > 0) {
            expanderColumnWidth = 16 + (maxDepth * 10);
        } else {
            expanderColumnWidth = MIN_EXPANDER_COL_WIDTH;
        }
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
                    return null;
                }

                return row.getValue(field.getId());
            }
        };

        final FieldHeader fieldHeader = new FieldHeader(fieldsManager, field);
        fieldHeader.setUpdater(value -> dataGrid.redrawHeaders());

        dataGrid.addResizableColumn(column, fieldHeader, field.getWidth());
        existingColumns.add(column);
    }

    void handleFieldRename(final String oldName,
                           final String newName) {
        if (!Objects.equals(oldName, newName)) {
            if (tableSettings != null && tableSettings.getConditionalFormattingRules() != null) {
                final AtomicBoolean wasModified = new AtomicBoolean(false);
                tableSettings.getConditionalFormattingRules().stream()
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
        ExpressionUtil.walkExpressionTree(
                expressionItem,
                null,
                (parent, childOffset, oldTerm) -> {
                    if (Objects.equals(oldTerm.getField(), oldTermName)) {
                        if (parent == null) {
                            throw new RuntimeException("Should not have a term without a parent operator");
                        }

                        final ExpressionTerm newTerm = new ExpressionTerm(
                                oldTerm.getEnabled(),
                                newTermName,
                                oldTerm.getCondition(),
                                oldTerm.getValue(),
                                oldTerm.getDocRef());

                        // Replace the old term with the new one
                        parent.getChildren().set(childOffset, newTerm);
                        wasModified.compareAndSet(false, true);
                    }
                });
        return wasModified.get();
    }

    void redrawHeaders() {
        dataGrid.redrawHeaders();
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
        getComponents().fireComponentChangeEvent(this);
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
        if (tableSettings.getFields() == null) {
            tableSettings.setFields(new ArrayList<>());
        }

        // Update columns.
        updateColumns();
    }

    private void ensureSpecialFields(final String... indexFieldNames) {
        // Get special fields from the current data source.
        final List<DataSourceField> requiredSpecialDsFields = new ArrayList<>();
        final List<Field> requiredSpecialFields = new ArrayList<>();
        // Get all index fields provided by the datasource
        final DataSourceFieldsMap dataSourceFieldsMap = getIndexFieldsMap();
        if (dataSourceFieldsMap != null) {
            for (final String indexFieldName : indexFieldNames) {
                final DataSourceField indexField = dataSourceFieldsMap.get(indexFieldName);
                if (indexField != null) {
                    requiredSpecialDsFields.add(indexField);
                    final Field specialField = buildSpecialField(indexFieldName);
                    requiredSpecialFields.add(specialField);
                }
            }

            // If the fields we want to make special do exist in the current data source then
            // add them.
            if (requiredSpecialFields.size() > 0) {
                // Remove all special fields as we will re-add them with the right names if there are any.
                tableSettings.getFields().removeIf(Field::isSpecial);

                // Prior to the introduction of the special field concept, special fields were
                // treated as invisible fields. For this reason we need to remove old invisible
                // fields if we haven't yet turned them into special fields.
                final Version version = Version.parse(tableSettings.getModelVersion());
                final boolean old = version.lt(CURRENT_MODEL_VERSION);
                if (old) {
                    requiredSpecialDsFields.forEach(requiredSpecialDsField ->
                            tableSettings.getFields().removeIf(field ->
                                    !field.isVisible() && field.getName().equals(requiredSpecialDsField.getName())));
                    tableSettings.setModelVersion(CURRENT_MODEL_VERSION.toString());
                }

                // Add special fields.
                requiredSpecialFields.forEach(field ->
                        tableSettings.getFields().add(field));
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
        final Field specialField = new Field(obfuscatedColumnName);
        specialField.setId(obfuscatedColumnName);
        specialField.setExpression(ParamUtil.makeParam(indexFieldName));
        specialField.setVisible(false);
        specialField.setSpecial(true);
        return specialField;
    }

    private DataSourceFieldsMap getIndexFieldsMap() {
        if (currentSearchModel != null
                && currentSearchModel.getIndexLoader() != null
                && currentSearchModel.getIndexLoader().getDataSourceFieldsMap() != null) {
            return currentSearchModel.getIndexLoader().getDataSourceFieldsMap();
        }

        return null;
    }

    void updateColumns() {
        // Now make sure special fields exist for stream id and event id.
        ensureSpecialFields(IndexConstants.STREAM_ID, IndexConstants.EVENT_ID);

        // Remove existing columns.
        for (final Column<TableRow, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();

        final List<Field> fields = tableSettings.getFields();
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

        tableSettings = getSettings();

        // Ensure all fields have ids.
        if (tableSettings.getFields() != null) {
            tableSettings.getFields().forEach(field -> {
                if (field.getId() == null) {
                    field.setId(createRandomFieldId());
                } else {
                    usedFieldIds.add(field.getId());
                }
            });
        }

        fieldsManager.setTableSettings(tableSettings);
    }

    @Override
    public void write(final ComponentConfig componentConfig) {
        super.write(componentConfig);
        componentConfig.setSettings(tableSettings);
    }

    @Override
    public void link() {
        String queryId = tableSettings.getQueryId();
        queryId = getComponents().validateOrGetFirstComponentId(queryId, QueryPresenter.TYPE.getId());
        tableSettings.setQueryId(queryId);
        setQueryId(queryId);
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        setQueryId(tableSettings.getQueryId());
    }

    @Override
    public ComponentResultRequest getResultRequest() {
        return tableResultRequest;
    }

    @Override
    public ComponentResultRequest createDownloadQueryRequest() {
        final TableResultRequest tableResultRequest = new TableResultRequest(0, Integer.MAX_VALUE);
        tableResultRequest.setTableSettings(tableSettings);
        tableResultRequest.setFetch(Fetch.ALL);
        return tableResultRequest;
    }

    @Override
    public void reset() {
        final int length = Math.max(1, tableResultRequest.getRequestedRange().getLength());
        dataGrid.setRowData(0, new ArrayList<>());
        dataGrid.setRowCount(0, true);
        dataGrid.setVisibleRange(0, length);
        tableResultRequest.setRange(0, length);
    }

    void clearAndRefresh() {
        clear();
    }

    private void refresh() {
        currentSearchModel.refresh(getComponentConfig().getId());
    }

    private void clear() {
        setData(null);
    }

    public List<TableRow> getSelectedRows() {
        return dataGrid.getSelectionModel().getSelectedItems();
    }

    @Override
    public TableComponentSettings getSettings() {
        ComponentSettings settings = getComponentConfig().getSettings();
        if (!(settings instanceof TableComponentSettings)) {
            settings = createSettings();
            getComponentConfig().setSettings(settings);
        }

        return (TableComponentSettings) settings;
    }

    private ComponentSettings createSettings() {
        final TableComponentSettings tableSettings = new TableComponentSettings();

        int[] arr = null;
        if (maxResults != null && maxResults.length > 0) {
            arr = new int[1];
            arr[0] = maxResults[0];
        }

        tableSettings.setMaxResults(arr);
        return tableSettings;
    }

    public Set<String> getHighlights() {
        if (currentSearchModel != null && currentSearchModel.getCurrentResult() != null
                && currentSearchModel.getCurrentResult().getHighlights() != null) {
            return currentSearchModel.getCurrentResult().getHighlights();
        }

        return null;
    }

    public interface TableView extends View {
        void setTableView(View view);

        void setRefreshing(boolean refreshing);
    }

    private class AddSelectionHandler implements SelectionChangeEvent.Handler {
        private final FieldAddPresenter presenter;

        AddSelectionHandler(final FieldAddPresenter presenter) {
            this.presenter = presenter;
        }

        @Override
        public void onSelectionChange(final SelectionChangeEvent event) {
            Field field = presenter.getSelectedObject();
            if (field != null) {
                HidePopupEvent.fire(TablePresenter.this, presenter);

                final String fieldName = field.getName();
                String suffix = "";
                int count = 1;
                final Set<String> currentFields = tableSettings.getFields().stream().map(Field::getName).collect(Collectors.toSet());
                while (currentFields.contains(fieldName + suffix)) {
                    count++;
                    suffix = " " + count;
                }

                field = field.copy();
                field.setName(fieldName + suffix);
                field.setId(createRandomFieldId());
                fieldsManager.addField(field);
            }
        }
    }
}
