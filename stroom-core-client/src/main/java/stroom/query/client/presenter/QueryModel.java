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
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TimeRange;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.task.client.TaskListener;
import stroom.util.shared.TokenError;

import com.google.gwt.core.client.GWT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryModel {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    public static final String TABLE_COMPONENT_ID = "table";
    public static final String VIS_COMPONENT_ID = "vis";


    private final RestFactory restFactory;
    private String queryUuid;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ResultStoreModel resultStoreModel;
    private TaskListener taskListener;

    private final ResultConsumer tablePresenter;

    private String currentNode;
    private QueryKey currentQueryKey;
    private QueryContext currentQueryContext;
    private QuerySearchRequest currentSearch;
    private boolean searching;
    private boolean polling;
    private SourceType sourceType = SourceType.QUERY_UI;

    private final List<SearchStateListener> searchStateListeners = new ArrayList<>();
    private final List<SearchErrorListener> errorListeners = new ArrayList<>();
    private final List<TokenErrorListener> tokenErrorListeners = new ArrayList<>();
    private final Map<String, ResultConsumer> resultConsumers = new HashMap<>();

    public QueryModel(final RestFactory restFactory,
                      final DateTimeSettingsFactory dateTimeSettingsFactory,
                      final ResultStoreModel resultStoreModel,
                      final ResultConsumer tablePresenter,
                      final ResultConsumer visPresenter) {
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.resultStoreModel = resultStoreModel;
        this.tablePresenter = tablePresenter;
        resultConsumers.put(TABLE_COMPONENT_ID, tablePresenter);
        resultConsumers.put(VIS_COMPONENT_ID, visPresenter);
    }

    public void init(final String queryUuid) {
        this.queryUuid = queryUuid;
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
        resultConsumers.values().forEach(ResultConsumer::endSearch);

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
        resultConsumers.values().forEach(ResultConsumer::endSearch);

        // Stop polling.
        polling = false;
        currentSearch = null;
    }

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

//
////    public void destroy() {
////        if (currentQueryKey != null) {
////            destroy(currentQueryKey);
////            currentQueryKey = null;
////        }
////    }
//
//    /**
//     * Destroy the previous search and ready all components for a new search to
//     * begin.
//     */
//    public void reset() {
//        // Stop previous search.
//        stop();
//    }

    /**
     * Begin executing a new search using the supplied query expression.
     */
    public void startNewSearch(final String query,
                               final List<Param> params,
                               final TimeRange timeRange,
                               final boolean incremental,
                               final boolean storeHistory,
                               final String queryInfo) {
        GWT.log("SearchModel - startNewSearch()");

        // Destroy the previous search and ready all components for a new search to begin.
        reset(DestroyReason.NO_LONGER_NEEDED);

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
                .searchRequestSource(
                        SearchRequestSource
                                .builder()
                                .sourceType(sourceType)
                                .ownerDocUuid(queryUuid)
                                .build())
                .query(query)
                .queryContext(currentQueryContext)
                .incremental(incremental)
                .build();
//            }
//        }
//
        if (query != null) {
//            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
//            if (dataSourceRef != null && expression != null) {
            // Let the query presenter know search is active.
            setSearching(true);

            // Reset all result components and tell them that search is
            // starting.
            resultConsumers.values().forEach(ResultConsumer::reset);
            resultConsumers.values().forEach(ResultConsumer::startSearch);

            // Start polling.
            polling = true;
            poll(storeHistory);
//            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId) {
        final QueryKey queryKey = currentQueryKey;
        final ResultConsumer resultConsumer = resultConsumers.get(componentId);
        if (resultConsumer != null && queryKey != null) {
            // Tell the refreshing component that it should want data.
            resultConsumer.startSearch();
            final QuerySearchRequest request = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(false)
                    .openGroups(resultConsumer.getOpenGroups())
                    .requestedRange(resultConsumer.getRequestedRange())
                    .build();

            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.search(currentNode, request))
                    .onSuccess(response -> {
                        try {
                            if (response != null && response.getResults() != null) {
                                for (final Result componentResult : response.getResults()) {
                                    if (componentId.equals(componentResult.getComponentId())) {
                                        resultConsumer.setData(componentResult);
                                        resultConsumer.endSearch();
                                    }
                                }
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }
                    })
                    .onFailure(throwable -> {
                        try {
                            if (queryKey.equals(currentQueryKey)) {
                                setErrors(Collections.singletonList(throwable.toString()));
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }
                        resultConsumer.setData(null);
                    })
                    .taskListener(taskListener)
                    .exec();
        }
    }

//    private void destroy(final QueryKey queryKey) {
//        final DestroyQueryRequest request = DestroyQueryRequest
//                .builder()
//                .queryKey(queryKey)
//                .queryDocUuid(queryUuid)
//                .componentId(componentId)
//                .build();
//        restFactory
//                .builder()
//                .forBoolean()
//                .onSuccess(response -> {
//                    if (!response) {
//                        Console.log("Unable to destroy search: " + request);
//                    }
//                })
//                .call(QUERY_RESOURCE)
//                .destroy(request);
//    }

    private void poll(final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final QuerySearchRequest search = currentSearch;
        if (search != null && polling) {
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
//                    .dashboardUuid(dashboardUuid)
//                    .componentId(componentId)
//                    .storeHistory(storeHistory)
//                    .build();

            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.search(currentNode, request))
                    .onSuccess(response -> {
                        GWT.log(response.toString());

                        if (search == currentSearch) {
                            currentQueryKey = response.getQueryKey();
                            currentNode = response.getNode();

                            try {
                                update(response);
                            } catch (final RuntimeException e) {
                                GWT.log(e.getMessage());
                            }

                            if (polling) {
                                poll(false);
                            }
                        } else {
                            deleteStore(response.getNode(), response.getQueryKey(), DestroyReason.NO_LONGER_NEEDED);
                        }
                    })
                    .onFailure(throwable -> {
                        GWT.log(throwable.getMessage());

                        try {
                            if (search == currentSearch) {
                                setErrors(Collections.singletonList(throwable.toString()));
                                polling = false;
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }

                        if (polling) {
                            poll(false);
                        }
                    })
                    .taskListener(taskListener)
                    .exec();
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
        // Give results to the right components.
        if (response.getResults() != null) {
            for (final Result componentResult : response.getResults()) {
                final ResultConsumer resultConsumer =
                        resultConsumers.get(componentResult.getComponentId());
                if (resultConsumer != null) {
                    resultConsumer.setData(componentResult);
                }
            }
        }

        // Tell all components if we are complete.
        if (response.isComplete()) {
            // Stop the spinner from spinning and tell components that they
            // no longer want data.
            resultConsumers.values().forEach(ResultConsumer::endSearch);
        }

        setErrors(response.getErrors());
        if (response.getTokenError() != null) {
            setTokenErrors(response.getTokenError());
        }

        if (response.isComplete()) {
            // Let the query presenter know search is inactive.
            setSearching(false);

            // If we have completed search then stop the task spinner.
            polling = false;
        }
    }

    private void setErrors(final List<String> errors) {
        errorListeners.forEach(listener -> listener.onError(errors));
    }

    private void setTokenErrors(final TokenError tokenError) {
        tokenErrorListeners.forEach(listener -> listener.onError(tokenError));
    }

    public boolean isSearching() {
        return searching;
    }

    private void setSearching(final boolean searching) {
        this.searching = searching;
        searchStateListeners.forEach(listener -> listener.onSearching(searching));
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

    public void addTokenErrorListener(final TokenErrorListener listener) {
        tokenErrorListeners.add(listener);
    }

    public void removeTokenErrorListener(final TokenErrorListener listener) {
        tokenErrorListeners.remove(listener);
    }

    public void setSourceType(final SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener = taskListener;
    }
}
