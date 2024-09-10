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
import stroom.docref.DocRef;
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
import stroom.task.client.DefaultTaskListener;
import stroom.task.client.HasTaskHandlerFactory;
import stroom.task.client.TaskHandlerFactory;
import stroom.util.shared.TokenError;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class QueryModel implements HasTaskHandlerFactory, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    public static final String TABLE_COMPONENT_ID = "table";
    public static final String VIS_COMPONENT_ID = "vis";


    private final EventBus eventBus;
    private final RestFactory restFactory;
    private DocRef queryDocRef;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ResultStoreModel resultStoreModel;
    private TaskHandlerFactory taskHandlerFactory = new DefaultTaskListener(this);

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
    private final Map<String, ResultComponent> resultComponents = new HashMap<>();

    private final ResultComponent tablePresenter;
    private final ResultComponent visPresenter;

    public QueryModel(final EventBus eventBus,
                      final RestFactory restFactory,
                      final DateTimeSettingsFactory dateTimeSettingsFactory,
                      final ResultStoreModel resultStoreModel,
                      final ResultComponent tablePresenter,
                      final ResultComponent visPresenter) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.resultStoreModel = resultStoreModel;
        this.tablePresenter = tablePresenter;
        this.visPresenter = visPresenter;
        tablePresenter.setQueryModel(this);
        visPresenter.setQueryModel(this);

        resultComponents.put(TABLE_COMPONENT_ID, tablePresenter);
        resultComponents.put(VIS_COMPONENT_ID, visPresenter);
    }

    public void init(final DocRef queryDocRef) {
        this.queryDocRef = queryDocRef;
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
                                .ownerDocRef(queryDocRef)
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
            resultComponents.values().forEach(ResultComponent::reset);
            resultComponents.values().forEach(ResultComponent::startSearch);

            // Start polling.
            polling = true;
            poll(storeHistory);
//            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId,
                        final Consumer<Result> resultConsumer) {
        boolean exec = false;
        final QueryKey queryKey = currentQueryKey;
        final ResultComponent resultComponent = resultComponents.get(componentId);
        if (resultComponent != null && queryKey != null) {
            // Tell the refreshing component that it should want data.
            resultComponent.startSearch();
            final QuerySearchRequest request = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(false)
                    .openGroups(resultComponent.getOpenGroups())
                    .requestedRange(resultComponent.getRequestedRange())
                    .build();

            exec = true;
            restFactory
                    .create(QUERY_RESOURCE)
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
                                setErrors(Collections.singletonList(throwable.toString()));
                            }
                        } catch (final RuntimeException e) {
                            GWT.log(e.getMessage());
                        }
                        resultConsumer.accept(null);
                    })
                    .taskHandlerFactory(taskHandlerFactory)
                    .exec();
        }

        // If no exec happened then let the caller know.
        if (!exec) {
            resultConsumer.accept(null);
        }
    }

    private void deleteStore(final String node, final QueryKey queryKey, final DestroyReason destroyReason) {
        if (queryKey != null) {
            resultStoreModel.destroy(node, queryKey, destroyReason, (ok) ->
                    GWT.log("Destroyed store " + queryKey), taskHandlerFactory);
        }
    }

    private void terminate(final String node, final QueryKey queryKey) {
        if (queryKey != null) {
            resultStoreModel.terminate(node, queryKey, (ok) ->
                    GWT.log("Terminate search " + queryKey), taskHandlerFactory);
        }
    }

    private void poll(final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final QuerySearchRequest search = currentSearch;
        if (search != null && polling) {
            final QuerySearchRequest request = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(storeHistory)
                    .openGroups(tablePresenter.getOpenGroups())
                    .requestedRange(tablePresenter.getRequestedRange())
                    .build();

            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.search(currentNode, request))
                    .onSuccess(response -> {
//                        GWT.log(response.toString());

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
//                        GWT.log(throwable.getMessage());

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
                    .taskHandlerFactory(taskHandlerFactory)
                    .exec();
        }
    }

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
                final ResultComponent resultComponent =
                        resultComponents.get(componentResult.getComponentId());
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

    @Override
    public void setTaskHandlerFactory(final TaskHandlerFactory taskHandlerFactory) {
        this.taskHandlerFactory = taskHandlerFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
