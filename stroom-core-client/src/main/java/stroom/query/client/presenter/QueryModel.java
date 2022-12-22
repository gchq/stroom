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

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.instance.client.ClientApplicationInstance;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.TimeRange;
import stroom.query.shared.DestroyQueryRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.client.Console;
import stroom.view.client.presenter.IndexLoader;

import com.google.gwt.core.client.GWT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class QueryModel {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;
    private final ClientApplicationInstance applicationInstance;
    private final IndexLoader indexLoader;
    private String queryUuid;
    private String componentId;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;

    private final ResultConsumer tablePresenter;

    private DashboardSearchResponse currentResponse;
    private QueryKey currentQueryKey;
    private QueryContext currentQueryContext;
    private QuerySearchRequest currentSearch;
    private Boolean mode = false;
    private boolean searching;

    private final List<Consumer<Boolean>> modeListeners = new ArrayList<>();
    private final List<Consumer<List<String>>> errorListeners = new ArrayList<>();


    public QueryModel(final RestFactory restFactory,
                      final ClientApplicationInstance applicationInstance,
                      final IndexLoader indexLoader,
                      final DateTimeSettingsFactory dateTimeSettingsFactory,
                      final QueryResultTablePresenter tablePresenter) {
        this.restFactory = restFactory;
        this.applicationInstance = applicationInstance;
        this.indexLoader = indexLoader;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.tablePresenter = tablePresenter;
    }

    public void init(final String queryUuid,
                     final String componentId) {
        this.queryUuid = queryUuid;
        this.componentId = componentId;
    }


    /**
     * Stop searching, set the search mode to inactive and tell all components
     * that they no longer want data and search has ended.
     */
    public void stop() {
        GWT.log("SearchModel - stop()");
        if (currentQueryKey != null) {
            destroy(currentQueryKey);
            currentQueryKey = null;
        }
        setMode(false);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        tablePresenter.endSearch();

        // Stop polling.
        searching = false;
        currentSearch = null;
    }

    /**
     * Destroy the previous search and ready all components for a new search to
     * begin.
     */
    public void reset() {
        // Stop previous search.
        stop();
    }

    /**
     * Begin executing a new search using the supplied query expression.
     *
     * @param expression The expression to search with.
     */
    public void startNewSearch(final String query,
                               final List<Param> params,
                               final TimeRange timeRange,
                               final boolean incremental,
                               final boolean storeHistory,
                               final String queryInfo) {
        // Destroy the previous search and ready all components for a new search to begin.
        stop();

        GWT.log("SearchModel - startNewSearch()");

//        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
//        if (resultComponentMap != null) {
//            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
//            if (dataSourceRef != null && expression != null) {
//                // Copy the expression.
//                ExpressionOperator currentExpression = ExpressionUtil.copyOperator(expression);
//
        currentQueryContext = QueryContext
                .builder()
                .params(params)
                .timeRange(timeRange)
                .queryInfo(queryInfo)
                .dateTimeSettings(dateTimeSettingsFactory.getDateTimeSettings())
                .build();

        currentSearch = QuerySearchRequest
                .builder()
                .query(query)
                .queryContext(currentQueryContext)
                .incremental(incremental)
                .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                .queryDocUuid(queryUuid)
                .build();
//            }
//        }
//
        if (query != null) {
//            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
//            if (dataSourceRef != null && expression != null) {
            // Let the query presenter know search is active.
            setMode(true);

            // Reset all result components and tell them that search is
            // starting.
            tablePresenter.reset();
            tablePresenter.startSearch();

            // Start polling.
            searching = true;
            poll(storeHistory);
//            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh() {
        final QueryKey queryKey = currentQueryKey;
        if (queryKey != null) {
//            final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
//            if (resultComponentMap != null) {
//                final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
//                if (dataSourceRef != null) {
            // Tell the refreshing component that it should want data.
            tablePresenter.startSearch();

//                    final QuerySearchRequest search = Search
//                            .builder()
//                            .dataSourceRef(currentSearch.getDataSourceRef())
//                            .expression(currentSearch.getExpression())
//                            .componentSettingsMap(resultComponentMap)
//                            .params(currentSearch.getParams())
//                            .timeRange(currentSearch.getTimeRange())
//                            .incremental(true)
//                            .build();
//
//            final List<ComponentResultRequest> requests = new ArrayList<>();
//            final ComponentResultRequest componentResultRequest = tablePresenter.getResultRequest(true);
//            requests.add(componentResultRequest);

            final QuerySearchRequest request = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(false)
                    .openGroups(tablePresenter.getOpenGroups())
                    .requestedRange(tablePresenter.getRequestedRange())
                    .build();

            final Rest<DashboardSearchResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
//                        Result result = null;
                        try {

                            if (response != null && response.getResults() != null) {
                                for (final Result componentResult : response.getResults()) {
                                    tablePresenter.setData(componentResult);
                                    tablePresenter.endSearch();
//                                    if (componentId.equals(componentResult.getComponentId())) {
//                                        result = componentResult;
//                                    }
                                }
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }
//                        tablePresenter.setData(result);
                    })
                    .onFailure(throwable -> {
                        try {
                            if (queryKey.equals(currentQueryKey)) {
                                setErrors(Collections.singletonList(throwable.toString()));
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }
                        tablePresenter.setData(null);
                    })
                    .call(QUERY_RESOURCE)
                    .search(request);
        }
//            }
//        }
    }

    private void destroy(final QueryKey queryKey) {
        final DestroyQueryRequest request = DestroyQueryRequest
                .builder()
                .queryKey(queryKey)
                .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                .queryDocUuid(queryUuid)
                .componentId(componentId)
                .build();
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    if (!response) {
                        Console.log("Unable to destroy search: " + request);
                    }
                })
                .call(QUERY_RESOURCE)
                .destroy(request);
    }

    private void poll(final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final QuerySearchRequest search = currentSearch;
        if (search != null && searching) {
//            final List<ComponentResultRequest> requests = new ArrayList<>();
//            for (final Entry<String, ResultConsumer> entry : componentMap.entrySet()) {
//                final ResultConsumer resultComponent = entry.getValue();
//                final ComponentResultRequest componentResultRequest = resultComponent.getResultRequest(false);
//                requests.add(componentResultRequest);
//            }
            final QuerySearchRequest request = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(storeHistory)
                    .openGroups(tablePresenter.getOpenGroups())
                    .requestedRange(tablePresenter.getRequestedRange())
                    .build();

//            QuerySearchRequest
//                    .builder()
//                    .queryKey(queryKey)
//                    .search(search)
//                    .componentResultRequests(requests)
//                    .dateTimeSettings(dateTimeSettingsFactory.getDateTimeSettings())
//                    .applicationInstanceUuid(applicationInstance.getInstanceUuid())
//                    .dashboardUuid(dashboardUuid)
//                    .componentId(componentId)
//                    .storeHistory(storeHistory)
//                    .build();

            final Rest<DashboardSearchResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
                        GWT.log(response.toString());

                        if (search == currentSearch) {
                            currentQueryKey = response.getQueryKey();

                            try {
                                update(response);
                            } catch (final RuntimeException e) {
                                GWT.log(e.getMessage());
                            }

                            if (searching) {
                                poll(false);
                            }
                        } else {
                            destroy(response.getQueryKey());
                        }
                    })
                    .onFailure(throwable -> {
                        GWT.log(throwable.getMessage());

                        try {
                            if (search == currentSearch) {
                                setErrors(Collections.singletonList(throwable.toString()));
                                searching = false;
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }

                        if (searching) {
                            poll(false);
                        }
                    })
                    .call(QUERY_RESOURCE)
                    .search(request);
        }
    }

//    /**
//     * Creates a result component map for all components.
//     *
//     * @return A result component map.
//     */
//    private Map<String, ComponentSettings> createComponentSettingsMap() {
//        if (componentMap.size() > 0) {
//            final Map<String, ComponentSettings> resultComponentMap = new HashMap<>();
//            for (final Entry<String, ResultConsumer> entry : componentMap.entrySet()) {
//                final String componentId = entry.getKey();
//                final ResultConsumer resultComponent = entry.getValue();
//                final ComponentSettings componentSettings = resultComponent.getSettings();
//                resultComponentMap.put(componentId, componentSettings);
//            }
//            return resultComponentMap;
//        }
//        return null;
//    }

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
                tablePresenter.setData(componentResult);

//                final ResultConsumer resultComponent = componentMap.get(componentResult.getComponentId());
//                if (resultComponent != null) {
//                    resultComponent.setData(componentResult);
//                }
            }
        }

        // Tell all components if we are complete.
        if (response.isComplete()) {
            // Stop the spinner from spinning and tell components that they
            // no longer want data.
            tablePresenter.endSearch();
        }

        setErrors(response.getErrors());

        if (response.isComplete()) {
            // Let the query presenter know search is inactive.
            setMode(false);

            // If we have completed search then stop the task spinner.
            searching = false;
        }
    }

    private void setErrors(final List<String> errors) {
        errorListeners.forEach(listener -> listener.accept(errors));
    }

    public Boolean getMode() {
        return mode;
    }

    private void setMode(final Boolean mode) {
        this.mode = mode;
        modeListeners.forEach(listener -> listener.accept(mode));
    }


//    public boolean isSearching() {
//        return searching;
//    }
//
//    public QueryKey getCurrentQueryKey() {
//        return currentQueryKey;
//    }
//
//    public Search getCurrentSearch() {
//        return currentSearch;
//    }
//
//    public IndexLoader getIndexLoader() {
//        return indexLoader;
//    }
//
//    public DashboardSearchResponse getCurrentResponse() {
//        return currentResponse;
//    }

//    public void addComponent(final String componentId, final ResultConsumer resultComponent) {
//        // Create and assign a new map here to prevent concurrent modification exceptions.
//        final Map<String, ResultConsumer> componentMap = new HashMap<>(this.componentMap);
//        componentMap.put(componentId, resultComponent);
//        this.componentMap = componentMap;
//    }

//    public void removeComponent(final String componentId) {
//        // Create and assign a new map here to prevent concurrent modification exceptions.
//        final Map<String, ResultConsumer> componentMap = new HashMap<>(this.componentMap);
//        componentMap.remove(componentId);
//        this.componentMap = componentMap;
//    }
//
//    public void addModeListener(final Consumer<Boolean> consumer) {
//        modeListeners.add(consumer);
//    }
//
//    public void removeModeListener(final Consumer<Boolean> consumer) {
//        modeListeners.remove(consumer);
//    }
//
//    public void addErrorListener(final Consumer<List<String>> consumer) {
//        errorListeners.add(consumer);
//    }
//
//    public void removeErrorListener(final Consumer<List<String>> consumer) {
//        errorListeners.remove(consumer);
//    }
}
