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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.view.client.presenter.IndexLoader;

import com.google.gwt.core.client.GWT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class SearchModel {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final RestFactory restFactory;
    private final IndexLoader indexLoader;
    private String dashboardUuid;
    private String componentId;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ResultStoreModel resultStoreModel;
    private Map<String, ResultComponent> componentMap = new HashMap<>();
    private DashboardSearchResponse currentResponse;
    private String currentNode;
    private QueryKey currentQueryKey;
    private Search currentSearch;
    private Boolean mode = false;
    private boolean searching;

    private final List<Consumer<Boolean>> modeListeners = new ArrayList<>();
    private final List<Consumer<List<String>>> errorListeners = new ArrayList<>();

    public SearchModel(final RestFactory restFactory,
                       final IndexLoader indexLoader,
                       final DateTimeSettingsFactory dateTimeSettingsFactory,
                       final ResultStoreModel resultStoreModel) {
        this.restFactory = restFactory;
        this.indexLoader = indexLoader;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.resultStoreModel = resultStoreModel;
    }

    public void init(final String dashboardUuid,
                     final String componentId) {
        this.dashboardUuid = dashboardUuid;
        this.componentId = componentId;
    }

    /**
     * Stop searching, set the search mode to inactive and tell all components
     * that they no longer want data and search has ended. Do not destroy search results.
     */
    public void stop() {
        GWT.log("SearchModel - stop()");

        terminate(currentNode, currentQueryKey);
        setMode(false);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        for (final ResultComponent resultComponent : componentMap.values()) {
            resultComponent.endSearch();
        }

        // Stop polling.
        searching = false;
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

        setMode(false);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        for (final ResultComponent resultComponent : componentMap.values()) {
            resultComponent.endSearch();
        }

        // Stop polling.
        searching = false;
        currentSearch = null;
    }

    /**
     * Begin executing a new search using the supplied query expression.
     *
     * @param expression The expression to search with.
     */
    public void startNewSearch(final ExpressionOperator expression,
                               final List<Param> params,
                               final TimeRange timeRange,
                               final boolean incremental,
                               final boolean storeHistory,
                               final String queryInfo,
                               final QueryKey resumeQueryKey) {
        GWT.log("SearchModel - startNewSearch()");

        // Destroy the previous search and ready all components for a new search to begin.
        reset(DestroyReason.NO_LONGER_NEEDED);
        currentQueryKey = resumeQueryKey;

        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Copy the expression.
                ExpressionOperator currentExpression = ExpressionUtil.copyOperator(expression);

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
                setMode(true);

                // Reset all result components and tell them that search is
                // starting.
                for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
                    final ResultComponent resultComponent = entry.getValue();
                    resultComponent.reset();
                    resultComponent.startSearch();
                }

                // Start polling.
                searching = true;
                poll(Fetch.ALL, storeHistory);
            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId, final Consumer<Result> resultConsumer) {
        final QueryKey queryKey = currentQueryKey;
        final ResultComponent resultComponent = componentMap.get(componentId);
        if (resultComponent != null && queryKey != null) {
            final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
            if (resultComponentMap != null) {
                final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
                if (dataSourceRef != null) {
                    // Tell the refreshing component that it should want data.
                    resultComponent.startSearch();

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

                    final Rest<DashboardSearchResponse> rest = restFactory.create();
                    rest
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
                                        setErrors(Collections.singletonList(throwable.toString()));
                                    }
                                } catch (final RuntimeException e) {
                                    GWT.log(e.getMessage());
                                }
                                resultConsumer.accept(null);
                            })
                            .call(DASHBOARD_RESOURCE)
                            .search(request);
                }
            }
        }
    }

//    private void terminate(final QueryKey queryKey) {
//        if (queryKey != null) {
//            resultStoreModel.terminate();
//        }
//
////        final DestroySearchRequest request = DestroySearchRequest
////                .builder()
////                .queryKey(queryKey)
////                .dashboardUuid(dashboardUuid)
////                .componentId(componentId)
////                .build();
////        final Rest<Boolean> rest = restFactory.create();
////        rest
////                .onSuccess(response -> {
////                    if (!response) {
////                        Console.log("Unable to destroy search: " + request);
////                    }
////                })
////                .call(DASHBOARD_RESOURCE)
////                .destroy(request);
//    }


//    private void destroy(final QueryKey queryKey) {
//        if (queryKey != null) {
//            resultStoreModel.destroy();
//        }
//
////        final DestroySearchRequest request = DestroySearchRequest
////                .builder()
////                .queryKey(queryKey)
////                .dashboardUuid(dashboardUuid)
////                .componentId(componentId)
////                .build();
////        final Rest<Boolean> rest = restFactory.create();
////        rest
////                .onSuccess(response -> {
////                    if (!response) {
////                        Console.log("Unable to destroy search: " + request);
////                    }
////                })
////                .call(DASHBOARD_RESOURCE)
////                .destroy(request);
//    }


    private void deleteStore(final String node, final QueryKey queryKey, final DestroyReason destroyReason) {
        if (queryKey != null) {
            resultStoreModel.destroy(node, queryKey, destroyReason, (ok) ->
                    GWT.log("Destroyed store " + queryKey));
        }
    }

    private void terminate(final String node, final QueryKey queryKey) {
        if (queryKey != null) {
            resultStoreModel.terminate(node, queryKey, (ok) ->
                    GWT.log("Terminate search " + queryKey));
        }
    }

    private void poll(Fetch fetch, final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final Search search = currentSearch;
        if (search != null && searching) {
            final List<ComponentResultRequest> requests = new ArrayList<>();
            for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
                final ResultComponent resultComponent = entry.getValue();

                if (resultComponent.isPaused()) {
                    fetch = Fetch.NONE;
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

            final Rest<DashboardSearchResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
                        if (search == currentSearch) {
                            currentQueryKey = response.getQueryKey();
                            currentNode = response.getNode();

                            try {
                                update(response);
                            } catch (final RuntimeException e) {
                                GWT.log(e.getMessage());
                            }

                            if (searching) {
                                poll(Fetch.CHANGES, false);
                            }
                        } else {
                            deleteStore(response.getNode(), response.getQueryKey(), DestroyReason.NO_LONGER_NEEDED);
                        }
                    })
                    .onFailure(throwable -> {
                        try {
                            if (search == currentSearch) {
                                setErrors(Collections.singletonList(throwable.toString()));
                                searching = false;
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }

                        if (searching) {
                            poll(Fetch.CHANGES, false);
                        }
                    })
                    .call(DASHBOARD_RESOURCE)
                    .search(request);
        }
    }

    /**
     * Creates a result component map for all components.
     *
     * @return A result component map.
     */
    private Map<String, ComponentSettings> createComponentSettingsMap() {
        if (componentMap.size() > 0) {
            final Map<String, ComponentSettings> resultComponentMap = new HashMap<>();
            for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
                final String componentId = entry.getKey();
                final ResultComponent resultComponent = entry.getValue();
                final ComponentSettings componentSettings = resultComponent.getSettings();
                resultComponentMap.put(componentId, componentSettings);
            }
            return resultComponentMap;
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
                final ResultComponent resultComponent = componentMap.get(componentResult.getComponentId());
                if (resultComponent != null) {
                    resultComponent.setData(componentResult);
                }
            }
        }

        // Tell all components if we are complete.
        if (response.isComplete()) {
            // Stop the spinner from spinning and tell components that they
            // no longer want data.
            componentMap.values().forEach(ResultComponent::endSearch);
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

        if (search == null || componentMap.size() == 0) {
            return null;
        }

        final List<ComponentResultRequest> requests = new ArrayList<>();
        for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
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
                .ownerDocUuid(dashboardUuid)
                .componentId(componentId)
                .build();
    }

    public boolean isSearching() {
        return searching;
    }

    public QueryKey getCurrentQueryKey() {
        return currentQueryKey;
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
        final Map<String, ResultComponent> componentMap = new HashMap<>(this.componentMap);
        componentMap.put(componentId, resultComponent);
        this.componentMap = componentMap;
    }

    public void removeComponent(final String componentId) {
        // Create and assign a new map here to prevent concurrent modification exceptions.
        final Map<String, ResultComponent> componentMap = new HashMap<>(this.componentMap);
        componentMap.remove(componentId);
        this.componentMap = componentMap;
    }

    public void addModeListener(final Consumer<Boolean> consumer) {
        modeListeners.add(consumer);
    }

    public void removeModeListener(final Consumer<Boolean> consumer) {
        modeListeners.remove(consumer);
    }

    public void addErrorListener(final Consumer<List<String>> consumer) {
        errorListeners.add(consumer);
    }

    public void removeErrorListener(final Consumer<List<String>> consumer) {
        errorListeners.remove(consumer);
    }

    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {

    }
}
