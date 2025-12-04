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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Param;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.client.presenter.SearchErrorListener;
import stroom.query.client.presenter.SearchStateListener;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SearchModel implements HasTaskMonitorFactory, HasHandlers {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final IndexLoader indexLoader;
    private DocRef dashboardDocRef;
    private String componentId;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ResultStoreModel resultStoreModel;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);
    private Map<String, ResultComponent> resultComponents = new HashMap<>();
    private DashboardSearchResponse currentResponse;
    private String currentNode;
    private QueryKey currentQueryKey;
    private Search currentSearch;
    private boolean searching;
    private boolean polling;

    private final List<SearchStateListener> searchStateListeners = new ArrayList<>();
    private final List<SearchErrorListener> errorListeners = new ArrayList<>();

    public SearchModel(final EventBus eventBus,
                       final RestFactory restFactory,
                       final IndexLoader indexLoader,
                       final DateTimeSettingsFactory dateTimeSettingsFactory,
                       final ResultStoreModel resultStoreModel) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.indexLoader = indexLoader;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.resultStoreModel = resultStoreModel;
    }

    public void init(final DocRef dashboardDocRef,
                     final String componentId) {
        this.dashboardDocRef = dashboardDocRef;
        this.componentId = componentId;
    }

    /**
     * Stop searching, set the search mode to inactive and tell all components
     * that they no longer want data and search has ended. Do not destroy search results.
     */
    public void stop() {
        GWT.log("SearchModel - stop()");

        terminate(currentNode, currentQueryKey);
        setSearching(false);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        resultComponents.values().forEach(ResultComponent::endSearch);

        // Stop polling.
        polling = false;
    }

    /**
     * Destroy the previous search and ready all components for a new search to
     * begin.
     */
    public void reset(final DestroyReason destroyReason) {
        GWT.log("SearchModel - reset()");

        // Stop previous search if there is one.
        deleteStore(currentNode, currentQueryKey, destroyReason);
        currentQueryKey = null;
        currentNode = null;

        setSearching(false);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        resultComponents.values().forEach(ResultComponent::endSearch);

        // Stop polling.
        polling = false;
        currentSearch = null;
    }

    /**
     * Begin executing a new search using the supplied query expression.
     */
    public void startNewSearch(final ExpressionOperator expression,
                               final List<Param> params,
                               final TimeRange timeRange,
                               final boolean incremental,
                               final boolean storeHistory,
                               final String queryInfo,
                               final String resumeNode,
                               final QueryKey resumeQueryKey) {
        GWT.log("SearchModel - startNewSearch()");

        // Destroy the previous search and ready all components for a new search to begin.
        reset(DestroyReason.NO_LONGER_NEEDED);

        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // If we are resuming then set the node and query key.
                currentNode = resumeNode;
                currentQueryKey = resumeQueryKey;

                // Copy the expression.
                final ExpressionOperator currentExpression = ExpressionUtil.copyOperator(expression);
                currentSearch = Search
                        .builder()
                        .dataSourceRef(dataSourceRef)
                        .expression(currentExpression)
                        .componentSettingsMap(resultComponentMap)
                        .params(params)
                        .timeRange(timeRange)
                        .incremental(incremental)
                        .queryInfo(queryInfo)
                        .build();
            }
        }

        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Let the query presenter know search is active.
                setSearching(true);

                // Reset all result components and tell them that search is
                // starting.
                resultComponents.values().forEach(ResultComponent::reset);
                resultComponents.values().forEach(ResultComponent::startSearch);

                // Start polling.
                polling = true;
                poll(Fetch.ALL, storeHistory);
            }
        }
    }

    public void forceNewSearch(final String componentId,
                               final Consumer<Result> resultConsumer) {
        if (currentSearch != null) {
            final boolean exec = exec(componentId, resultConsumer, null);
            // If no exec happened then let the caller know.
            if (!exec) {
                resultConsumer.accept(null);
            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId, final Consumer<Result> resultConsumer) {
        boolean exec = false;
        if (currentQueryKey != null) {
            exec = exec(componentId, resultConsumer, currentQueryKey);
        }

        // If no exec happened then let the caller know.
        if (!exec) {
            resultConsumer.accept(null);
        }
    }

    private boolean exec(final String componentId, final Consumer<Result> resultConsumer, final QueryKey queryKey) {
        final ResultComponent resultComponent = resultComponents.get(componentId);
        if (resultComponent == null) {
            return false;
        }

        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
        if (resultComponentMap == null) {
            return false;
        }

        final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
        if (dataSourceRef == null) {
            return false;
        }

        final Search search = Search
                .builder()
                .dataSourceRef(currentSearch.getDataSourceRef())
                .expression(currentSearch.getExpression())
                .componentSettingsMap(resultComponentMap)
                .params(currentSearch.getParams())
                .timeRange(currentSearch.getTimeRange())
                .incremental(true)
                .build();

        final List<ComponentResultRequest> requests = new ArrayList<>();
        final ComponentResultRequest componentResultRequest = resultComponent
                .getResultRequest(Fetch.CHANGES);
        requests.add(componentResultRequest);

        final DashboardSearchRequest request = DashboardSearchRequest
                .builder()
                .searchRequestSource(getSearchRequestSource())
                .queryKey(queryKey)
                .search(search)
                .componentResultRequests(requests)
                .dateTimeSettings(dateTimeSettingsFactory.getDateTimeSettings())
                .build();

        restFactory
                .create(DASHBOARD_RESOURCE)
                .method(res -> res.search(currentNode, request))
                .onSuccess(response -> {
                    Result result = null;
                    try {
                        if (response != null && response.getResults() != null) {
                            for (final Result componentResult : response.getResults()) {
                                if (componentId.equals(componentResult.getComponentId())) {
                                    result = componentResult;
                                }
                            }
                        }
                    } catch (final RuntimeException e) {
                        GWT.log(e.getMessage());
                    }
                    resultConsumer.accept(result);
                })
                .onFailure(throwable -> {
                    try {
                        if (queryKey.equals(currentQueryKey)) {
                            setErrors(Collections.singletonList(
                                    new ErrorMessage(Severity.ERROR, throwable.toString())));
                        }
                    } catch (final RuntimeException e) {
                        GWT.log(e.getMessage());
                    }
                    resultConsumer.accept(null);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();

        return true;
    }

    private void deleteStore(final String node, final QueryKey queryKey, final DestroyReason destroyReason) {
        if (queryKey != null) {
            resultStoreModel.destroy(node, queryKey, destroyReason, (ok) ->
                    GWT.log("Destroyed store " + queryKey), taskMonitorFactory);
        }
    }

    private void terminate(final String node, final QueryKey queryKey) {
        if (queryKey != null) {
            resultStoreModel.terminate(node, queryKey, (ok) ->
                    GWT.log("Terminate search " + queryKey), taskMonitorFactory);
        }
    }

    private void poll(final Fetch initialFetch, final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final Search search = currentSearch;
        if (search != null && polling) {
            final List<ComponentResultRequest> requests = new ArrayList<>();
            for (final Entry<String, ResultComponent> entry : resultComponents.entrySet()) {
                final ResultComponent resultComponent = entry.getValue();

                final Fetch fetch;
                if (resultComponent.isPaused()) {
                    fetch = Fetch.NONE;
                } else {
                    fetch = initialFetch;
                }

                final ComponentResultRequest componentResultRequest = resultComponent.getResultRequest(fetch);
                requests.add(componentResultRequest);
            }
            final DashboardSearchRequest request = DashboardSearchRequest
                    .builder()
                    .searchRequestSource(getSearchRequestSource())
                    .queryKey(queryKey)
                    .search(search)
                    .componentResultRequests(requests)
                    .dateTimeSettings(dateTimeSettingsFactory.getDateTimeSettings())
                    .storeHistory(storeHistory)
                    .build();

            restFactory
                    .create(DASHBOARD_RESOURCE)
                    .method(res -> res.search(currentNode, request))
                    .onSuccess(response -> {
//                        GWT.log(response.toString());

                        if (search == currentSearch) {
                            if (response != null) {
                                currentQueryKey = response.getQueryKey();
                                currentNode = response.getNode();

                                try {
                                    update(response);
                                } catch (final RuntimeException e) {
                                    GWT.log(e.getMessage());
                                }

                                if (polling) {
                                    poll(Fetch.CHANGES, false);
                                }
                            } else {
                                // Tell all components if we are complete.
                                // Stop the spinner from spinning and tell components that they
                                // no longer want data.
                                resultComponents.values().forEach(ResultComponent::endSearch);

                                // Let the query presenter know search is inactive.
                                setSearching(false);

                                // If we have completed search then stop the task spinner.
                                polling = false;
                            }
                        } else if (response != null) {
                            deleteStore(response.getNode(), response.getQueryKey(), DestroyReason.NO_LONGER_NEEDED);
                        }
                    })
                    .onFailure(throwable -> {
//                        GWT.log(throwable.getMessage());

                        try {
                            if (search == currentSearch) {
                                setErrors(Collections.singletonList(
                                        new ErrorMessage(Severity.ERROR, throwable.toString())));
                                polling = false;
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }

                        if (polling) {
                            poll(Fetch.CHANGES, false);
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    /**
     * Creates a result component map for all components.
     *
     * @return A result component map.
     */
    private Map<String, ComponentSettings> createComponentSettingsMap() {
        if (!resultComponents.isEmpty()) {
            return resultComponents.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> entry.getValue().getSettings()));
        }
        return null;
    }

    /**
     * On receiving a search result from the server update all interested
     * components with new data.
     *
     * @param response The search response.
     */
    private void update(final DashboardSearchResponse response) {
        currentResponse = response;

        // Give results to the right components.
        if (response.getResults() != null) {
            for (final Result componentResult : response.getResults()) {
                final ResultComponent resultComponent = resultComponents.get(componentResult.getComponentId());
                if (resultComponent != null) {
                    resultComponent.setData(componentResult);
                }
            }
        }

        // Tell all components if we are complete.
        if (response.isComplete()) {
            // Stop the spinner from spinning and tell components that they
            // no longer want data.
            resultComponents.values().forEach(ResultComponent::endSearch);
        }

        setErrors(response.getErrorMessages());

        if (response.isComplete()) {
            // Let the query presenter know search is inactive.
            setSearching(false);

            // If we have completed search then stop the task spinner.
            polling = false;
        }
    }

    private void setErrors(final List<ErrorMessage> errors) {
        errorListeners.forEach(listener -> listener.onError(errors));
    }

    public boolean isSearching() {
        return searching;
    }

    private void setSearching(final boolean searching) {
        this.searching = searching;
        searchStateListeners.forEach(listener -> listener.onSearching(searching));
    }

    /**
     * Initialises the model for passed expression and current result settings and returns
     * the corresponding {@link DashboardSearchRequest} object
     */
    public DashboardSearchRequest createDownloadQueryRequest(final ExpressionOperator expression,
                                                             final List<Param> params,
                                                             final TimeRange timeRange) {
        Search search = null;
        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Copy the expression.
                final ExpressionOperator currentExpression = ExpressionUtil.copyOperator(expression);

                search = Search
                        .builder()
                        .dataSourceRef(dataSourceRef)
                        .expression(currentExpression)
                        .componentSettingsMap(resultComponentMap)
                        .params(params)
                        .timeRange(timeRange)
                        .build();
            }
        }

        if (search == null || resultComponents.size() == 0) {
            return null;
        }

        final List<ComponentResultRequest> requests = new ArrayList<>();
        for (final Entry<String, ResultComponent> entry : resultComponents.entrySet()) {
            final ResultComponent resultComponent = entry.getValue();
            final ComponentResultRequest componentResultRequest = resultComponent.createDownloadQueryRequest();
            requests.add(componentResultRequest);
        }

        return DashboardSearchRequest
                .builder()
                .searchRequestSource(getSearchRequestSource())
                .search(search)
                .componentResultRequests(requests)
                .dateTimeSettings(dateTimeSettingsFactory.getDateTimeSettings())
                .build();
    }

    public SearchRequestSource getSearchRequestSource() {
        return SearchRequestSource
                .builder()
                .sourceType(SourceType.DASHBOARD_UI)
                .ownerDocRef(dashboardDocRef)
                .componentId(componentId)
                .build();
    }

    public boolean isPolling() {
        return polling;
    }

    public QueryKey getCurrentQueryKey() {
        return currentQueryKey;
    }

    public String getCurrentNode() {
        return currentNode;
    }

    public Search getCurrentSearch() {
        return currentSearch;
    }

    public IndexLoader getIndexLoader() {
        return indexLoader;
    }

    public DashboardSearchResponse getCurrentResponse() {
        return currentResponse;
    }

    public void addComponent(final String componentId, final ResultComponent resultComponent) {
        // Create and assign a new map here to prevent concurrent modification exceptions.
        final Map<String, ResultComponent> componentMap = new HashMap<>(this.resultComponents);
        componentMap.put(componentId, resultComponent);
        this.resultComponents = componentMap;
    }

    public void removeComponent(final String componentId) {
        // Create and assign a new map here to prevent concurrent modification exceptions.
        final Map<String, ResultComponent> componentMap = new HashMap<>(this.resultComponents);
        componentMap.remove(componentId);
        this.resultComponents = componentMap;
    }

    public void addSearchStateListener(final SearchStateListener listener) {
        searchStateListeners.add(listener);
    }

    public void removeSearchStateListener(final SearchStateListener listener) {
        searchStateListeners.remove(listener);
    }

    public void addSearchErrorListener(final SearchErrorListener listener) {
        errorListeners.add(listener);
    }

    public void removeSearchErrorListener(final SearchErrorListener listener) {
        errorListeners.remove(listener);
    }

    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {

    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
