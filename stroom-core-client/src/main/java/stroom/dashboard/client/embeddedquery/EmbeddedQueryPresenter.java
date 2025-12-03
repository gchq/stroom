/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client.embeddedquery;

import stroom.core.client.ContentManager;
import stroom.core.client.event.WindowCloseEvent;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter.EmbeddedQueryView;
import stroom.dashboard.client.input.FilterableTable;
import stroom.dashboard.client.main.AbstractRefreshableComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dashboard.client.query.SelectionHandlerExpressionBuilder;
import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.TableUpdateEvent;
import stroom.dashboard.client.vis.VisSelectionModel;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
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
import stroom.query.client.QueryClient;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryDocPresenter;
import stroom.query.client.presenter.QueryModel;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryResultVisPresenter;
import stroom.query.client.presenter.ResultComponent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.client.presenter.SearchErrorListener;
import stroom.query.client.presenter.SearchStateListener;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryTablePreferences;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EmbeddedQueryPresenter
        extends AbstractRefreshableComponentPresenter<EmbeddedQueryView>
        implements SearchStateListener, SearchErrorListener, HasComponentSelection, FilterableTable {

    public static final String TAB_TYPE = "embedded-query-component";


    public static final ComponentType TYPE = new ComponentType(
            5,
            "embedded-query",
            "Embedded Query",
            ComponentUse.PANEL);

    private final QueryClient queryClient;
    private final QueryModel queryModel;
    private final Provider<QueryDocPresenter> queryDocPresenterProvider;
    private final ContentManager contentManager;
    //    private final QueryResultTabsView linkTabsLayoutView;
    private boolean queryOnOpen;
    private QueryInfo queryInfo;
    private String query;

    private final Provider<QueryResultTablePresenter> tablePresenterProvider;
    private final Provider<QueryResultVisPresenter> visPresenterProvider;

    private QueryResultTablePresenter currentTablePresenter;
    private QueryResultVisPresenter currentVisPresenter;
    private final List<HandlerRegistration> tableHandlerRegistrations = new ArrayList<>();

    private List<ErrorMessage> currentErrors;
    private boolean initialised;
    private ExpressionOperator currentSelectionQuery;
    private DocRef loadedQueryRef;
    private final EventBus tableEventBus = new SimpleEventBus();

    @Inject
    public EmbeddedQueryPresenter(final EventBus eventBus,
                                  final EmbeddedQueryView view,
                                  final Provider<EmbeddedQuerySettingsPresenter> settingsPresenterProvider,
                                  final Provider<QueryResultTablePresenter> tablePresenterProvider,
                                  final Provider<QueryResultVisPresenter> visPresenterProvider,
                                  final Provider<QueryDocPresenter> queryDocPresenterProvider,
                                  final QueryClient queryClient,
                                  final RestFactory restFactory,
                                  final DateTimeSettingsFactory dateTimeSettingsFactory,
                                  final ResultStoreModel resultStoreModel,
                                  final ContentManager contentManager) {
        super(eventBus, view, settingsPresenterProvider);
        this.queryClient = queryClient;
        this.tablePresenterProvider = tablePresenterProvider;
        this.visPresenterProvider = visPresenterProvider;
        this.queryDocPresenterProvider = queryDocPresenterProvider;
        this.contentManager = contentManager;

        final stroom.query.client.presenter.ResultComponent tableResultConsumer = new ResultComponent() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return NullSafe.get(currentTablePresenter, QueryResultTablePresenter::getRequestedRange);
            }

            @Override
            public GroupSelection getGroupSelection() {
                return NullSafe.get(currentTablePresenter, QueryResultTablePresenter::getGroupSelection);
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
                if (currentTablePresenter != null) {
                    currentTablePresenter.endSearch();
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
                        currentTablePresenter.setQueryModel(queryModel);
                        currentTablePresenter.startSearch();
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
                    currentTablePresenter.setData(componentResult);
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
                if (currentTablePresenter != null) {
                    currentTablePresenter.setQueryModel(queryModel);
                }
            }
        };

        final stroom.query.client.presenter.ResultComponent visResultConsumer = new ResultComponent() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return NullSafe.get(currentVisPresenter, QueryResultVisPresenter::getRequestedRange);
            }

            @Override
            public GroupSelection getGroupSelection() {
                return NullSafe.get(currentVisPresenter, QueryResultVisPresenter::getGroupSelection);
            }

            @Override
            public void reset() {
                hasData = false;
                if (currentVisPresenter != null) {
                    currentVisPresenter.reset();
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
                if (currentVisPresenter != null) {
                    currentVisPresenter.endSearch();
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
                        currentVisPresenter.setQueryModel(queryModel);
                        currentVisPresenter.startSearch();
                        start = false;
                    }

                    if (!NullSafe.isBlankString(visResult.getJsonData())) {
                        hasData = true;
                    }

                    currentVisPresenter.setData(componentResult);
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
                if (currentVisPresenter != null) {
                    currentVisPresenter.setQueryModel(queryModel);
                }
            }
        };

        queryModel = new QueryModel(
                eventBus,
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                () -> getQuerySettings()
                        .getQueryTablePreferences()
                        .copy()
                        .selectionFilter(NullSafe.get(currentTablePresenter,
                                QueryResultTablePresenter::getCurrentSelectionFilter))
                        .build());
        queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, tableResultConsumer);
        queryModel.addResultComponent(QueryModel.VIS_COMPONENT_ID, visResultConsumer);
        queryModel.addSearchStateListener(this);

//        view.setResultView(linkTabsLayoutView);
//
//        linkTabsLayoutView.getTabBar().addTab(TABLE);
//        linkTabsLayoutView.getTabBar().addTab(VISUALISATION);
//        setVisHidden(true);
    }

//    private void setVisHidden(final boolean state) {
//        final boolean currentState = linkTabsLayoutView.getTabBar().isTabHidden(VISUALISATION);
//        if (currentState != state) {
//            linkTabsLayoutView.getTabBar().setTabHidden(VISUALISATION, state);
//            if (state) {
//                selectTab(TABLE);
//            } else {
//                selectTab(VISUALISATION);
//            }
//        }
//    }

    public boolean isShowingVis() {
        return getQuerySettings().getShowTable() != Boolean.TRUE && currentVisPresenter != null;
    }

    public boolean canShowVis() {
        return currentVisPresenter != null;
    }

    public void showTable(final boolean show) {
        if (show) {
            setSettings(getQuerySettings().copy().showTable(Boolean.TRUE).build());
            setDirty(true);
            updateVisibleResult();
        } else {
            setSettings(getQuerySettings().copy().showTable(null).build());
            setDirty(true);
            updateVisibleResult();
        }
    }

    public void editQuery() {
        if (getQuerySettings().reference()) {
            OpenDocumentEvent.fire(this, getQuerySettings().getQueryRef(), true);
        } else {
            final QueryDoc doc = getQueryDoc();
            final QueryDocPresenter queryDocPresenter = queryDocPresenterProvider.get();
            queryDocPresenter.setSaveInterceptor(() -> {
                final QueryDoc embeddedQueryDoc = queryDocPresenter.write(doc);
                final EmbeddedQueryComponentSettings settings = getQuerySettings();
                final EmbeddedQueryComponentSettings updatedSettings = settings
                        .copy()
                        .embeddedQueryDoc(embeddedQueryDoc)
                        .build();
                setSettings(updatedSettings);
                updateQueryDoc(embeddedQueryDoc, updatedSettings);
                queryDocPresenter.read(embeddedQueryDoc.asDocRef(), embeddedQueryDoc, false);
                setDirty(true);
            });
            queryDocPresenter.read(doc.asDocRef(), doc, false);
            contentManager.open(e -> e.getCallback().closeTab(true), queryDocPresenter, queryDocPresenter);
        }
    }

    private QueryDoc getQueryDoc() {
        QueryDoc doc = getQuerySettings().getEmbeddedQueryDoc();
        if (doc == null) {
            doc = QueryDoc.builder().uuid("Embedded Query").build();
        }
        doc.setName(getDashboardContext().getDashboardDocRef().getName() + " - " + getComponentConfig().getName());
        return doc;
    }

    public void runQuery() {
        run(true, true);
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        super.setDashboardContext(dashboardContext);
        registerHandler(dashboardContext.addContextChangeHandler(event -> {
            if (initialised) {
                final ExpressionOperator selectionQuery = dashboardContext
                        .createSelectionHandlerExpression(getQuerySettings().getSelectionQuery())
                        .orElse(null);

                if (!Objects.equals(currentSelectionQuery, selectionQuery)) {
                    currentSelectionQuery = selectionQuery;
                    queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
                    run(true, true, selectionQuery);
                }

                if (currentTablePresenter != null) {
                    currentTablePresenter.setDashboardContext(dashboardContext);
                    final ExpressionOperator selectionFilter = dashboardContext
                            .createSelectionHandlerExpression(getQuerySettings().getSelectionFilter())
                            .orElse(null);
                    if (!Objects.equals(currentTablePresenter.getCurrentSelectionFilter(), selectionFilter)) {
                        currentTablePresenter.setCurrentSelectionFilter(selectionFilter);
                        currentTablePresenter.onColumnFilterChange();
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
//                    if (component instanceof TablePresenter) {
//                        currentTablePresenter = (TablePresenter) component;
//                        update(currentTablePresenter);
//                    }
//                } else if (Objects.equals(getTextSettings().getTableId(), event.getComponentId())) {
//                    if (component instanceof TablePresenter) {
//                        currentTablePresenter = (TablePresenter) component;
//                        update(currentTablePresenter);
//                    }
//                }
//            }
        }));
    }

    private void updateVisibleResult() {
        if (currentVisPresenter != null && getQuerySettings().getShowTable() != Boolean.TRUE) {
            getView().setResultView(currentVisPresenter.getView());
            currentVisPresenter.refresh();
        } else if (currentTablePresenter != null) {
            getView().setResultView(currentTablePresenter.getView());
        }
    }

    private void createNewTable() {
        if (currentTablePresenter == null) {
            currentTablePresenter = tablePresenterProvider.get();
            currentTablePresenter.setDashboardContext(getDashboardContext());
            currentTablePresenter.setQueryTablePreferencesSupplier(() ->
                    getQuerySettings().getQueryTablePreferences());
            currentTablePresenter.setQueryTablePreferencesConsumer(queryTablePreferences ->
                    setSettings(getQuerySettings().copy().queryTablePreferences(queryTablePreferences).build()));
            currentTablePresenter.setQueryModel(queryModel);
            currentTablePresenter.setTaskMonitorFactory(this);
            currentTablePresenter.updateQueryTablePreferences();
            tableHandlerRegistrations.add(currentTablePresenter.addDirtyHandler(e -> setDirty(true)));
            tableHandlerRegistrations.add(currentTablePresenter.getSelectionModel()
                    .addSelectionHandler(event ->
                            getDashboardContext().fireComponentChangeEvent(this)));

            if (currentVisPresenter != null) {
                currentTablePresenter.setQueryResultVisPresenter(currentVisPresenter);
            }

            // Chain update events.
            tableHandlerRegistrations.add(currentTablePresenter.addUpdateHandler(e ->
                    fireColumnAndDataUpdate()));
        }
    }

    private void destroyCurrentTable() {
        tableHandlerRegistrations.forEach(HandlerRegistration::removeHandler);
        tableHandlerRegistrations.clear();

        if (currentTablePresenter != null) {
//            currentTablePresenter.onRemove();
            currentTablePresenter = null;
        }
    }

    private void createNewVis() {
        if (currentVisPresenter == null) {
            final VisSelectionModel visSelectionModel = new VisSelectionModel();
            visSelectionModel.addSelectionHandler(event ->
                    getDashboardContext().fireComponentChangeEvent(EmbeddedQueryPresenter.this));

            currentVisPresenter = visPresenterProvider.get();
            currentVisPresenter.setQueryModel(queryModel);
            currentVisPresenter.setTaskMonitorFactory(this);
            currentVisPresenter.setVisSelectionModel(visSelectionModel);

            if (currentTablePresenter != null) {
                currentTablePresenter.setQueryResultVisPresenter(currentVisPresenter);
            }
        }
    }

    private void destroyCurrentVis() {
        if (currentVisPresenter != null) {
            currentVisPresenter.onRemove();
            currentVisPresenter = null;
            if (currentTablePresenter != null) {
                currentTablePresenter.setQueryResultVisPresenter(null);
            }
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getEventBus().addHandler(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        }));
//        registerHandler(linkTabsLayoutView.getTabBar().addSelectionHandler(e ->
//                selectTab(e.getSelectedItem())));
    }

//    private void selectTab(final TabData tabData) {
//        if (TABLE.equals(tabData)) {
//            linkTabsLayoutView.getTabBar().selectTab(tabData);
//            linkTabsLayoutView.getLayerContainer().show(tablePresenter);
//        } else if (VISUALISATION.equals(tabData)) {
//            linkTabsLayoutView.getTabBar().selectTab(tabData);
//            linkTabsLayoutView.getLayerContainer().show(currentVisPresenter);
//        }
//    }

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
            if (currentTablePresenter != null) {
                currentTablePresenter.reset();
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
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        queryModel.setTaskMonitorFactory(taskMonitorFactory);
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
                queryClient.loadQueryDoc(queryRef, result ->
                                updateQueryDoc(result, settings),
                        new DefaultErrorHandler(this, null),
                        this);
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
            final Automate automate = settings.getAutomate();
            if (queryOnOpen || automate.isOpen()) {
                run(true, false);
            }
        }
    }

    @Override
    public void setSettings(final ComponentSettings componentSettings) {
        super.setSettings(componentSettings);
        if (currentTablePresenter != null) {
            currentTablePresenter.refresh();
        }
    }

    @Override
    public ComponentConfig write() {
        // Write expression.
        setSettings(getQuerySettings()
                .copy()
                .lastQueryKey(queryModel.getCurrentQueryKey())
                .lastQueryNode(queryModel.getCurrentNode())
                .build());
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
        return getQuerySettings().getAutomate();
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
//        tablePresenter.getView().onSearching(searching);

        // If this is the end of a query then schedule a refresh.
        if (!searching) {
            scheduleRefresh();
        }
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        if (currentTablePresenter != null) {
            currentTablePresenter.changeSettings();
            currentTablePresenter.refresh();
        }
        loadEmbeddedQuery();
    }

    @Override
    public List<ColumnRef> getColumnRefs() {
        if (currentVisPresenter != null) {
            return currentVisPresenter.getColumnRefs();
        } else if (currentTablePresenter != null) {
            return currentTablePresenter.getColumnRefs();
        }
        return Collections.emptyList();
    }

    @Override
    public List<ComponentSelection> getSelection() {
        if (currentVisPresenter != null && getQuerySettings().getShowTable() != Boolean.TRUE) {
            return currentVisPresenter.getSelection();
        } else if (currentTablePresenter != null) {
            return currentTablePresenter.getSelection();
        }
        return Collections.emptyList();
    }

    @Override
    public Set<String> getHighlights() {
        if (currentVisPresenter != null) {
            return currentVisPresenter.getHighlights();
        } else if (currentTablePresenter != null) {
            return currentTablePresenter.getHighlights();
        }
        return Collections.emptySet();
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public QueryResultTablePresenter getCurrentTablePresenter() {
        return currentTablePresenter;
    }

    @Override
    public void onContentTabVisible(final boolean visible) {
        if (currentTablePresenter != null) {
            currentTablePresenter.onContentTabVisible(visible);
        }
    }

    @Override
    public List<Column> getColumns() {
        return NullSafe.getOrElse(
                currentTablePresenter,
                QueryResultTablePresenter::getColumns,
                Collections.emptyList());
    }

    @Override
    public Element getFilterButton(final Column column) {
        if (currentTablePresenter == null) {
            return null;
        }
        return currentTablePresenter.getFilterButton(column);
    }

    @Override
    public ColumnValuesDataSupplier getDataSupplier(final Column column,
                                                    final List<ConditionalFormattingRule> conditionalFormattingRules) {
        if (currentTablePresenter == null) {
            return null;
        }
        return currentTablePresenter.getDataSupplier(column, conditionalFormattingRules);
    }

    private void fireColumnAndDataUpdate() {
        TableUpdateEvent.fire(this);
    }

    @Override
    public HandlerRegistration addUpdateHandler(final TableUpdateEvent.Handler handler) {
        return tableEventBus.addHandler(TableUpdateEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        tableEventBus.fireEvent(event);
    }

    @Override
    public FilterCellManager getFilterCellManager() {
        if (currentTablePresenter == null) {
            return null;
        }
        return currentTablePresenter.getFilterCellManager();
    }

    public interface EmbeddedQueryView extends View, RequiresResize {

        void setResultView(View view);
    }
}
