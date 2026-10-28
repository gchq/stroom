/*
 * Copyright 2024 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.core.client.ContentManager;
import stroom.core.client.event.WindowCloseEvent;
import stroom.dashboard.client.input.FilterableTable;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dashboard.client.query.SelectionHandlerExpressionBuilder;
import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.vis.VisSelectionModel;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.QLVisResult;
import stroom.query.api.Result;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.TableResult;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.QueryClient;
import stroom.query.client.QueryModel;
import stroom.query.client.ResultComponent;
import stroom.query.client.ResultStoreModel;
import stroom.query.client.SearchErrorListener;
import stroom.query.client.SearchStateListener;
import stroom.query.client.component.QueryDocEditor;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.component.QueryResultVisController;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Listeners;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EmbeddedQueryController
        extends RefreshableDashboardComponentController
        implements SearchStateListener, SearchErrorListener, HasComponentSelection, FilterableTable {

    public static final String TAB_TYPE = "embedded-query-component";


    public static final ComponentType TYPE = new ComponentType(
            5,
            "embedded-query",
            "Embedded Query",
            ComponentUse.PANEL);

    private final EmbeddedQuery view;
    private final QueryClient queryClient;
    private final QueryModel queryModel;
    private final Provider<QueryDocEditor> queryDocEditorProvider;
    private final ContentManager contentManager;
    private boolean queryOnOpen;
    private QueryInfo queryInfo;
    private String query;

    private final Provider<QueryResultTableController> tableControllerProvider;
    private final Provider<QueryResultVisController> visControllerProvider;

    private QueryResultTableController currentTableController;
    private QueryResultVisController currentVisController;
    private final List<HandlerRegistration> tableHandlerRegistrations = new ArrayList<>();

    private List<ErrorMessage> currentErrors;
    private boolean initialised;
    private ExpressionOperator currentSelectionQuery;
    private DocRef loadedQueryRef;
    // Told when the table's columns or data change, e.g. a filter on it
    private final Listeners<Boolean> updateListeners = new Listeners<>();

    /// Made by GIN, with its pane.
    @Inject
    public EmbeddedQueryController(final EventBus eventBus,
                                   final EmbeddedQuery view,
                                   final Provider<EmbeddedQuerySettingsController> settingsControllerProvider,
                                   final Provider<QueryResultTableController> tableControllerProvider,
                                   final Provider<QueryResultVisController> visControllerProvider,
                                   final Provider<QueryDocEditor> queryDocEditorProvider,
                                   final QueryClient queryClient,
                                   final RestFactory restFactory,
                                   final DateTimeSettingsFactory dateTimeSettingsFactory,
                                   final ResultStoreModel resultStoreModel,
                                   final ContentManager contentManager) {
        super(eventBus, settingsControllerProvider);
        this.view = view;
        initWidget(view);
        this.queryClient = queryClient;
        this.tableControllerProvider = tableControllerProvider;
        this.visControllerProvider = visControllerProvider;
        this.queryDocEditorProvider = queryDocEditorProvider;
        this.contentManager = contentManager;

        final stroom.query.client.ResultComponent tableResultConsumer = new ResultComponent() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return NullSafe.get(currentTableController, QueryResultTableController::getRequestedRange);
            }

            @Override
            public GroupSelection getGroupSelection() {
                return NullSafe.get(currentTableController, QueryResultTableController::getGroupSelection);
            }

            @Override
            public void reset() {
                hasData = false;
            }

            @Override
            public void startSearch() {
                hasData = false;
                start = true;
            }

            @Override
            public void endSearch() {
                start = false;
                if (currentTableController != null) {
                    currentTableController.endSearch();
                }
                if (!hasData) {
                    destroyCurrentTable();
                    updateVisibleResult();
                }
            }

            @Override
            public void setData(final Result componentResult) {
                if (componentResult != null) {
                    final TableResult tableResult = (TableResult) componentResult;
                    if (start) {
                        createNewTable();
                        updateVisibleResult();
                        currentTableController.setQueryModel(queryModel);
                        currentTableController.startSearch();
                        start = false;
                    }

                    if (tableResult.getRows() != null && !tableResult.getRows().isEmpty()) {
                        hasData = true;
                    }

                    // Update the columns that are known to the query table preferences.
                    if (tableResult.getColumns() != null && !tableResult.getColumns().isEmpty()) {
                        final QueryTablePreferences queryTablePreferences = QueryTablePreferences
                                .copy(getQuerySettings().getQueryTablePreferences())
                                .columns(tableResult.getColumns())
                                .build();
                        setSettings(getQuerySettings().copy().queryTablePreferences(queryTablePreferences).build());
                    }

                    // Give the new data to the table.
                    currentTableController.setData(componentResult);
                }
//                else {
//                    if (start) {
//                        currentTablePresenter.reset();
//                        currentTablePresenter.endSearch();
//                        start = false;
//                        hasData = false;
//                        destroyCurrentTable();
//                        updateVisibleResult();
//                    }
//                }
            }

            @Override
            public void setQueryModel(final QueryModel queryModel) {
                if (currentTableController != null) {
                    currentTableController.setQueryModel(queryModel);
                }
            }
        };

        final stroom.query.client.ResultComponent visResultConsumer = new ResultComponent() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return NullSafe.get(currentVisController, QueryResultVisController::getRequestedRange);
            }

            @Override
            public GroupSelection getGroupSelection() {
                return NullSafe.get(currentVisController, QueryResultVisController::getGroupSelection);
            }

            @Override
            public void reset() {
                hasData = false;
                if (currentVisController != null) {
                    currentVisController.reset();
                }
            }

            @Override
            public void startSearch() {
                hasData = false;
                start = true;
            }

            @Override
            public void endSearch() {
                start = false;
                if (currentVisController != null) {
                    currentVisController.endSearch();
                }
                if (!hasData) {
                    destroyCurrentVis();
                    updateVisibleResult();
                }
            }

            @Override
            public void setData(final Result componentResult) {
                if (componentResult != null) {
                    final QLVisResult visResult = (QLVisResult) componentResult;

                    if (start) {
                        createNewVis();
                        updateVisibleResult();
                        currentVisController.setQueryModel(queryModel);
                        currentVisController.startSearch();
                        start = false;
                    }

                    if (!NullSafe.isBlankString(visResult.getJsonData())) {
                        hasData = true;
                    }

                    currentVisController.setData(componentResult);
                }
//                else {
//                    if (start) {
//                        currentVisPresenter.clear();
//                        currentVisPresenter.endSearch();
//                        start = false;
//                        hasData = false;
//                        destroyCurrentVis();
//                        updateVisibleResult();
//                    }
//                }
            }

            @Override
            public void setQueryModel(final QueryModel queryModel) {
                if (currentVisController != null) {
                    currentVisController.setQueryModel(queryModel);
                }
            }
        };

        queryModel = new QueryModel(
                eventBus,
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                () -> getQueryTablePreferences()
                        .copy()
                        .selectionFilter(getCurrentSelectionFilter())
                        .build());
        // Shows its requests' progress as this controller's owners do (its context's)
        queryModel.setTaskMonitorFactory(taskMonitor());
        queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, tableResultConsumer);
        queryModel.addResultComponent(QueryModel.VIS_COMPONENT_ID, visResultConsumer);
        queryModel.addSearchStateListener(this);

        listen(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        });
    }

    /// @return Whether the pane shows its query's visualisation, rather than its table.
    public boolean isShowingVis() {
        return getQuerySettings().getShowTable() != Boolean.TRUE && currentVisController != null;
    }

    /// @return Whether the pane's query has a visualisation to show.
    public boolean canShowVis() {
        return currentVisController != null;
    }

    /// Shows the query's table, or its visualisation if it has one.
    ///
    /// @param show Whether to show the table.
    public void showTable(final boolean show) {
        if (show) {
            setSettings(getQuerySettings().copy().showTable(Boolean.TRUE).build());
            onChange();
            updateVisibleResult();
        } else {
            setSettings(getQuerySettings().copy().showTable(null).build());
            onChange();
            updateVisibleResult();
        }
    }

    /// Opens the pane's query: the query document it refers to, or, for one held in the dashboard,
    /// a tab whose Save puts it back into the dashboard.
    public void editQuery() {
        if (getQuerySettings().reference()) {
            OpenDocumentEvent.fire(this, getQuerySettings().getQueryRef(), true);
        } else {
            final QueryDoc doc = getQueryDoc();
            final QueryDocEditor queryDocEditor = queryDocEditorProvider.get();
            // The tab it is shown in, which owns it
            final DocumentEditorHost<QueryDoc> queryTab = own(new DocumentEditorHost<>(
                    getEventBus(), queryDocEditor, QueryDoc.TYPE));
            queryDocEditor.setSaveInterceptor(() -> {
                final QueryDoc embeddedQueryDoc = queryDocEditor.write(doc);
                final EmbeddedQueryComponentSettings settings = getQuerySettings();
                final EmbeddedQueryComponentSettings updatedSettings = settings
                        .copy()
                        .embeddedQueryDoc(embeddedQueryDoc)
                        .build();
                setSettings(updatedSettings);
                updateQueryDoc(embeddedQueryDoc, updatedSettings);
                queryTab.read(embeddedQueryDoc.asDocRef(), embeddedQueryDoc);
                onChange();
            });
            // Editable unless the dashboard is read only (its context narrows it)
            queryTab.setReadOnly(false);
            queryTab.read(doc.asDocRef(), doc);
            contentManager.open(e -> {
                e.getCallback().closeTab(true);
                // Its tab is closed for good
                release(queryTab);
            }, queryTab, queryTab);
        }
    }

    private QueryDoc getQueryDoc() {
        QueryDoc doc = getQuerySettings().getEmbeddedQueryDoc();
        if (doc == null) {
            doc = QueryDoc.builder().uuid("Embedded Query").build();
        }
        return doc
                .copy()
                .name(getDashboardContext().getDashboardDocRef().getName() + " - " + getComponentConfig().getName())
                .build();
    }

    /// Runs the pane's query.
    public void runQuery() {
        run(true, true);
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        super.setDashboardContext(dashboardContext);
        own(dashboardContext.addContextChangeListener(() -> {
            if (initialised) {
                final ExpressionOperator selectionQuery = dashboardContext
                        .createSelectionHandlerExpression(getQuerySettings().getSelectionQuery())
                        .orElse(null);

                if (!Objects.equals(currentSelectionQuery, selectionQuery)) {
                    currentSelectionQuery = selectionQuery;
                    queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
                    run(true, true, selectionQuery);
                }

                if (currentTableController != null) {
                    currentTableController.setDashboardContext(dashboardContext);
                    final ExpressionOperator selectionFilter = resolveSelectionFilter();
                    if (!Objects.equals(currentTableController.getCurrentSelectionFilter(), selectionFilter)) {
                        currentTableController.setCurrentSelectionFilter(selectionFilter);
                        currentTableController.onColumnFilterChange();
                    }
                }
            }

//            if (component instanceof HasAbstractFields) {
//                final VisPresenter visPresenter = (VisPresenter) component;
//                final List<Map<String, String>> selection = visPresenter.getCurrentSelection();
//                String params = "";
//                if (selection != null) {
//                    for (final Map<String, String> map : selection) {
//                        for (final Entry<String, String> entry : map.entrySet()) {
//                            params += entry.getKey() + "=" + entry.getValue() + " ";
//                        }
//                    }
//                }
//                onQuery(params, null);
//            }

//                if (getTextSettings().getTableId() == null) {
//                    if (component instanceof TableController) {
//                        currentTablePresenter = (TableController) component;
//                        update(currentTablePresenter);
//                    }
//                } else if (Objects.equals(getTextSettings().getTableId(), event.getComponentId())) {
//                    if (component instanceof TableController) {
//                        currentTablePresenter = (TableController) component;
//                        update(currentTablePresenter);
//                    }
//                }
//            }
        }));
    }

    // The table's selection filter, or, before the table exists (e.g. for the first search), the
    // one the dashboard's context gives
    private ExpressionOperator getCurrentSelectionFilter() {
        return currentTableController != null
                ? currentTableController.getCurrentSelectionFilter()
                : resolveSelectionFilter();
    }

    private ExpressionOperator resolveSelectionFilter() {
        final DashboardContext dashboardContext = getDashboardContext();
        if (dashboardContext == null) {
            return null;
        }
        return dashboardContext
                .createSelectionHandlerExpression(getQuerySettings().getSelectionFilter())
                .orElse(null);
    }

    private void updateVisibleResult() {
        if (currentVisController != null && getQuerySettings().getShowTable() != Boolean.TRUE) {
            view.setResult(currentVisController);
            currentVisController.refresh();
        } else if (currentTableController != null) {
            view.setResult(currentTableController);
        } else {
            // A released table isn't left on show
            view.setResult(null);
        }
    }

    private void createNewTable() {
        if (currentTableController == null) {
            currentTableController = make(tableControllerProvider);
            currentTableController.setDashboardContext(getDashboardContext());
            // The filter may not depend on a selection, so the new table needs it now, not only when
            // the dashboard's context next changes
            currentTableController.setCurrentSelectionFilter(resolveSelectionFilter());
            currentTableController.setQueryTablePreferencesSupplier(this::getQueryTablePreferences);
            currentTableController.setQueryTablePreferencesConsumer(queryTablePreferences ->
                    setSettings(getQuerySettings().copy().queryTablePreferences(queryTablePreferences).build()));
            currentTableController.setQueryModel(queryModel);
            currentTableController.updateQueryTablePreferences();
            currentTableController.setOnChange(this::onChange);
            tableHandlerRegistrations.add(currentTableController.getSelectionModel()
                    .addSelectionHandler(event ->
                            getDashboardContext().fireComponentChangeEvent(this)));

            if (currentVisController != null) {
                currentTableController.setQueryResultVis(currentVisController);
            }

            // Chain update events.
            tableHandlerRegistrations.add(currentTableController.addUpdateListener(
                    this::fireColumnAndDataUpdate));
        }
    }

    private void destroyCurrentTable() {
        tableHandlerRegistrations.forEach(HandlerRegistration::removeHandler);
        tableHandlerRegistrations.clear();

        if (currentTableController != null) {
            release(currentTableController);
            currentTableController = null;
        }
    }

    private void createNewVis() {
        if (currentVisController == null) {
            final VisSelectionModel visSelectionModel = new VisSelectionModel();
            visSelectionModel.addSelectionHandler(event ->
                    getDashboardContext().fireComponentChangeEvent(EmbeddedQueryController.this));

            currentVisController = make(visControllerProvider);
            currentVisController.setQueryModel(queryModel);
            currentVisController.setVisSelectionModel(visSelectionModel);

            if (currentTableController != null) {
                currentTableController.setQueryResultVis(currentVisController);
            }
        }
    }

    private void destroyCurrentVis() {
        if (currentVisController != null) {
            currentVisController.onRemove();
            release(currentVisController);
            currentVisController = null;
            if (currentTableController != null) {
                currentTableController.setQueryResultVis(null);
            } else if (getQuerySettings().getShowTable() != Boolean.TRUE) {
                // The removed vis is being shown and there is no table to show instead, so clear it
                // rather than leave a vis that will never finish loading
                view.setResult(null);
            }
        }
    }

    @Override
    public void start() {
        if (queryModel.isSearching()) {
            queryModel.stop();
        }
        run(true, true);
    }

    @Override
    public void stop() {
        cancelRefresh();
        queryModel.stop();
    }

    @Override
    public void run(final boolean incremental,
                    final boolean storeHistory) {
        // No point running the search if there is no query
        run(incremental, storeHistory, currentSelectionQuery);
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final ExpressionOperator additionalQueryExpression) {
        if (NullSafe.isNonBlankString(query)) {
            currentErrors = null;

            // Clear the table selection and any markers.
            if (currentTableController != null) {
                currentTableController.reset();
            }

            // Destroy any previous query.
            queryModel.reset(DestroyReason.NO_LONGER_NEEDED);

            // Perform parameter substitution on query.
            final String replaced = ParamUtil
                    .replaceParameters(query, getDashboardContext(), true);

            // Start search.
            final DashboardContext dashboardContext = getDashboardContext();
            queryModel.startNewSearch(getComponentConfig().getId(), getComponentConfig().getName(),
                    replaced,
                    dashboardContext.getParams(),
                    dashboardContext.getResolvedTimeRange(),
                    incremental,
                    storeHistory,
                    queryInfo.getMessage(),
                    additionalQueryExpression);
        }
    }

    @Override
    public void setQueryInfo(final QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    @Override
    public void setQueryOnOpen(final boolean queryOnOpen) {
        this.queryOnOpen = queryOnOpen;
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof EmbeddedQueryComponentSettings)) {
            setSettings(EmbeddedQueryComponentSettings.builder()
                    .build());
        }

        // Fix legacy selection filters.
        setSettings(getQuerySettings()
                .copy()
                .selectionQuery(SelectionHandlerExpressionBuilder
                        .fixLegacySelectionHandlers(getQuerySettings().getSelectionQuery()))
                .selectionFilter(SelectionHandlerExpressionBuilder
                        .fixLegacySelectionHandlers(getQuerySettings().getSelectionFilter()))
                .build());

        loadEmbeddedQuery();
    }

    private void loadEmbeddedQuery() {
        final EmbeddedQueryComponentSettings settings = getQuerySettings();
        final DocRef queryRef = settings.getQueryRef();
        if (settings.reference()) {
            if (!Objects.equals(queryRef, loadedQueryRef)) {
                initialised = false;
                loadedQueryRef = queryRef;
                queryClient.loadQueryDoc(queryRef, guard(result ->
                                updateQueryDoc(result, settings)),
                        new DefaultErrorHandler(this, null),
                        taskMonitor());
            }
        } else if (!Objects.equals(settings.getEmbeddedQueryDoc().asDocRef(), loadedQueryRef)) {
            loadedQueryRef = settings.getEmbeddedQueryDoc().asDocRef();
            updateQueryDoc(settings.getEmbeddedQueryDoc(), settings);
        }
    }

    private void updateQueryDoc(final QueryDoc queryDoc,
                                final EmbeddedQueryComponentSettings settings) {
        if (queryDoc != null) {
            if (settings.getQueryTablePreferences() == null &&
                queryDoc.getQueryTablePreferences() != null) {
                setSettings(settings
                        .copy()
                        .queryTablePreferences(queryDoc.getQueryTablePreferences())
                        .build());
            }

            // Read expression.
            queryModel.init(queryDoc.asDocRef());
            query = queryDoc.getQuery();
            initialised = true;
            if (queryOnOpen || getAutomate().isOpen()) {
                run(true, false);
            }
        }
    }

    @Override
    public void setSettings(final ComponentSettings componentSettings) {
        super.setSettings(componentSettings);
        if (currentTableController != null) {
            currentTableController.refresh();
        }
    }

    @Override
    public ComponentConfig write() {
        // Write expression.
        setSettings(getQuerySettings());
        return super.write();
    }

    @Override
    public boolean isSearching() {
        return queryModel.isSearching();
    }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    @Override
    public Automate getAutomate() {
        // Settings may have no automate (e.g. a dashboard written without one)
        final Automate automate = getQuerySettings().getAutomate();
        return automate != null
                ? automate
                : Automate.builder().build();
    }

    // The settings' table preferences, or the defaults if they have none (e.g. a dashboard written
    // without them), as the results table expects some
    private QueryTablePreferences getQueryTablePreferences() {
        final QueryTablePreferences queryTablePreferences = getQuerySettings().getQueryTablePreferences();
        return queryTablePreferences != null
                ? queryTablePreferences
                : QueryTablePreferences.builder().build();
    }

    private EmbeddedQueryComponentSettings getQuerySettings() {
        return (EmbeddedQueryComponentSettings) getSettings();
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public void onClose() {
        super.onClose();
        queryModel.reset(DestroyReason.TAB_CLOSE);
        destroyCurrentVis();
        initialised = false;
    }

    @Override
    public void onRemove() {
        super.onRemove();
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
        destroyCurrentVis();
        initialised = false;
    }


    @Override
    public void link() {
    }


    @Override
    public void addSearchStateListener(final SearchStateListener listener) {
        queryModel.addSearchStateListener(listener);
    }

    @Override
    public void removeSearchStateListener(final SearchStateListener listener) {
        queryModel.removeSearchStateListener(listener);
    }

    @Override
    public void addSearchErrorListener(final SearchErrorListener listener) {
        queryModel.addSearchErrorListener(listener);
    }

    public void removeSearchErrorListener(final SearchErrorListener listener) {
        queryModel.removeSearchErrorListener(listener);
    }

    @Override
    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {
//        queryModel.setResultStoreInfo(resultStoreInfo);
    }

    @Override
    public void onError(final List<ErrorMessage> errors) {
        currentErrors = errors;
//        setWarningsVisible(currentErrors != null && !currentErrors.isEmpty());
    }

    @Override
    public List<ErrorMessage> getCurrentErrors() {
        return currentErrors;
    }

    @Override
    public boolean getSearchState() {
        return queryModel.isSearching();
    }

    @Override
    public void onSearching(final boolean searching) {
//        tablePresenter.onSearching(searching);

        // If this is the end of a query then schedule a refresh.
        if (!searching) {
            scheduleRefresh();
        }
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        if (currentTableController != null) {
            currentTableController.changeSettings();
            currentTableController.refresh();
        }
        loadEmbeddedQuery();
    }

    @Override
    public List<ColumnRef> getColumnRefs() {
        if (currentVisController != null) {
            return currentVisController.getColumnRefs();
        } else if (currentTableController != null) {
            return currentTableController.getColumnRefs();
        }
        return Collections.emptyList();
    }

    @Override
    public List<ComponentSelection> getSelection() {
        if (currentVisController != null && getQuerySettings().getShowTable() != Boolean.TRUE) {
            return currentVisController.getSelection();
        } else if (currentTableController != null) {
            return currentTableController.getSelection();
        }
        return Collections.emptyList();
    }

    @Override
    public Set<String> getHighlights() {
        if (currentVisController != null) {
            return currentVisController.getHighlights();
        } else if (currentTableController != null) {
            return currentTableController.getHighlights();
        }
        return Collections.emptySet();
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public QueryResultTableController getCurrentTableController() {
        return currentTableController;
    }

    @Override
    public void onContentTabVisible(final boolean visible) {
        if (currentTableController != null) {
            currentTableController.onContentTabVisible(visible);
        }
    }

    @Override
    public List<Column> getColumns() {
        return NullSafe.getOrElse(
                currentTableController,
                QueryResultTableController::getColumns,
                Collections.emptyList());
    }

    @Override
    public Element getFilterButton(final Column column) {
        if (currentTableController == null) {
            return null;
        }
        return currentTableController.getFilterButton(column);
    }

    @Override
    public ColumnValuesDataSupplier getDataSupplier(final Column column,
                                                    final List<ConditionalFormattingRule> conditionalFormattingRules) {
        if (currentTableController == null) {
            return null;
        }
        return currentTableController.getDataSupplier(column, conditionalFormattingRules);
    }

    private void fireColumnAndDataUpdate() {
        updateListeners.fire(true);
    }

    @Override
    public HandlerRegistration addUpdateListener(final Runnable listener) {
        return updateListeners.add(ignored -> listener.run());
    }

    @Override
    public FilterCellManager getFilterCellManager() {
        if (currentTableController == null) {
            return null;
        }
        return currentTableController.getFilterCellManager();
    }

}
