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
 */

package stroom.dashboard.client.embeddedquery;

import stroom.core.client.event.WindowCloseEvent;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter.EmbeddedQueryView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.Queryable;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.vis.VisSelectionModel;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.v2.ColumnRef;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QLVisResult;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.api.v2.TableResult;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryModel;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryResultVisPresenter;
import stroom.query.client.presenter.ResultComponent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.client.presenter.SearchErrorListener;
import stroom.query.client.presenter.SearchStateListener;
import stroom.query.shared.QueryResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class EmbeddedQueryPresenter
        extends AbstractComponentPresenter<EmbeddedQueryView>
        implements Queryable, SearchStateListener, SearchErrorListener, HasComponentSelection {

    public static final String TAB_TYPE = "embedded-query-component";


    public static final ComponentType TYPE = new ComponentType(
            5,
            "embedded-query",
            "Embedded Query",
            ComponentUse.PANEL);


    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);


    private static final TabData TABLE = new TabDataImpl("Table");
    private static final TabData VISUALISATION = new TabDataImpl("Visualisation");
    static final int TEN_SECONDS = 10000;

    private final RestFactory restFactory;
    private final QueryModel queryModel;
    //    private final QueryResultTabsView linkTabsLayoutView;
    private boolean queryOnOpen;
    private QueryInfo queryInfo;
    private String query;

    private final Provider<QueryResultTablePresenter> tablePresenterProvider;
    private final Provider<QueryResultVisPresenter> visPresenterProvider;

    private QueryResultTablePresenter currentTablePresenter;
    private QueryResultVisPresenter currentVisPresenter;
    private HandlerRegistration tableHandlerRegistration;

    private List<String> currentErrors;
    private boolean initialised;
    private Timer autoRefreshTimer;

    @Inject
    public EmbeddedQueryPresenter(final EventBus eventBus,
                                  final EmbeddedQueryView view,
                                  final Provider<EmbeddedQuerySettingsPresenter> settingsPresenterProvider,
                                  final Provider<QueryResultTablePresenter> tablePresenterProvider,
                                  final Provider<QueryResultVisPresenter> visPresenterProvider,
                                  final RestFactory restFactory,
                                  final DateTimeSettingsFactory dateTimeSettingsFactory,
                                  final ResultStoreModel resultStoreModel) {
        super(eventBus, view, settingsPresenterProvider);
        this.restFactory = restFactory;
        this.tablePresenterProvider = tablePresenterProvider;
        this.visPresenterProvider = visPresenterProvider;

        final stroom.query.client.presenter.ResultComponent tableResultConsumer = new ResultComponent() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return GwtNullSafe.get(currentTablePresenter, QueryResultTablePresenter::getRequestedRange);
            }

            @Override
            public Set<String> getOpenGroups() {
                return GwtNullSafe.get(currentTablePresenter, QueryResultTablePresenter::getOpenGroups);
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

                    if (tableResult != null && tableResult.getRows() != null && tableResult.getRows().size() > 0) {
                        hasData = true;
                    }

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
                return GwtNullSafe.get(currentVisPresenter, QueryResultVisPresenter::getRequestedRange);
            }

            @Override
            public Set<String> getOpenGroups() {
                return GwtNullSafe.get(currentVisPresenter, QueryResultVisPresenter::getOpenGroups);
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

                    if (!GwtNullSafe.isBlankString(visResult.getJsonData())) {
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
                resultStoreModel);
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

    private void updateVisibleResult() {
        if (currentVisPresenter != null) {
            getView().setResultView(currentVisPresenter.getView());
        } else if (currentTablePresenter != null) {
            getView().setResultView(currentTablePresenter.getView());
        }
    }

    private void createNewTable() {
        if (currentTablePresenter == null) {
            currentTablePresenter = tablePresenterProvider.get();
            currentTablePresenter.setQueryModel(queryModel);
            currentTablePresenter.setTaskMonitorFactory(this);
            tableHandlerRegistration =
                    currentTablePresenter.getSelectionModel().addSelectionHandler(event ->
                            getComponents().fireComponentChangeEvent(this));
        }
    }

    private void destroyCurrentTable() {
        if (tableHandlerRegistration != null) {
            tableHandlerRegistration.removeHandler();
            tableHandlerRegistration = null;
        }

        if (currentTablePresenter != null) {
//            currentTablePresenter.onRemove();
            currentTablePresenter = null;
        }
    }

    private void createNewVis() {
        if (currentVisPresenter == null) {
            final VisSelectionModel visSelectionModel = new VisSelectionModel();
            visSelectionModel.addSelectionHandler(event -> {
                getComponents().fireComponentChangeEvent(EmbeddedQueryPresenter.this);
            });

            currentVisPresenter = visPresenterProvider.get();
            currentVisPresenter.setQueryModel(queryModel);
            currentVisPresenter.setTaskMonitorFactory(this);
            currentVisPresenter.setVisSelectionModel(visSelectionModel);
        }
    }

    private void destroyCurrentVis() {
        if (currentVisPresenter != null) {
            currentVisPresenter.onRemove();
            currentVisPresenter = null;
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
        queryModel.stop();
    }

    private void run(final boolean incremental,
                     final boolean storeHistory) {
        // No point running the search if there is no query
        run(incremental, storeHistory, Function.identity());
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final Function<ExpressionOperator, ExpressionOperator> expressionDecorator) {
        if (GwtNullSafe.isNonBlankString(query)) {
            currentErrors = null;

            // Clear the table selection and any markers.
            if (currentTablePresenter != null) {
                currentTablePresenter.reset();
            }

            // Destroy any previous query.
            queryModel.reset(DestroyReason.NO_LONGER_NEEDED);

            // Start search.
            final DashboardContext dashboardContext = getDashboardContext();
            queryModel.startNewSearch(
                    query,
                    dashboardContext.getParams(),
                    dashboardContext.getTimeRange(),
                    incremental,
                    storeHistory,
                    queryInfo.getMessage());
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

        loadEmbeddedQuery();
    }

    private void loadEmbeddedQuery() {
        initialised = false;
        final DocRef queryRef = getQuerySettings().getQueryRef();
        if (queryRef != null) {
            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.fetch(queryRef.getUuid()))
                    .onSuccess(result -> {
                        if (result != null) {
                            // Read expression.
                            queryModel.init(result.getUuid());
                            query = result.getQuery();
                            initialised = true;
                            if (queryOnOpen) {
                                run(true, false);
                            }
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
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
    public void onError(final List<String> errors) {
        currentErrors = errors;
//        setWarningsVisible(currentErrors != null && !currentErrors.isEmpty());
    }

    @Override
    public List<String> getCurrentErrors() {
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

    private void scheduleRefresh() {
        // Schedule auto refresh after a query has finished.
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
        }
        autoRefreshTimer = null;

        final Automate automate = getQuerySettings().getAutomate();
        if (initialised && automate.isRefresh()) {
            try {
                final String interval = automate.getRefreshInterval();
                int millis = ModelStringUtil.parseDurationString(interval).intValue();

                // Ensure that the refresh interval is not less than 10 seconds.
                millis = Math.max(millis, TEN_SECONDS);

                autoRefreshTimer = new Timer() {
                    @Override
                    public void run() {
                        if (!initialised) {
                            stop();
                        } else {
                            // Make sure search is currently inactive before we attempt to execute a new query.
                            if (!queryModel.isSearching()) {
                                EmbeddedQueryPresenter.this.run(false, false);
                            }
                        }
                    }
                };
                autoRefreshTimer.schedule(millis);
            } catch (final RuntimeException e) {
                // Ignore as we cannot display this error now.
            }
        }
    }


    @Override
    protected void changeSettings() {
        super.changeSettings();
        loadEmbeddedQuery();
    }

    @Override
    public List<ColumnRef> getColumns() {
        if (currentVisPresenter != null) {
            return currentVisPresenter.getColumns();
        } else if (currentTablePresenter != null) {
            return currentTablePresenter.getColumns();
        }
        return Collections.emptyList();
    }

    @Override
    public List<ComponentSelection> getSelection() {
        if (currentVisPresenter != null) {
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

    public interface EmbeddedQueryView extends View, RequiresResize {

        void setResultView(View view);
    }
}
