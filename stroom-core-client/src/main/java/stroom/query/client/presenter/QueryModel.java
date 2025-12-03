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

package stroom.query.client.presenter;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Param;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TimeRange;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.query.shared.QueryTablePreferences;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class QueryModel implements HasTaskMonitorFactory, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    public static final String TABLE_COMPONENT_ID = "table";
    public static final String VIS_COMPONENT_ID = "vis";

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private DocRef queryDocRef;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final ResultStoreModel resultStoreModel;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    private String currentNode;
    private QueryKey currentQueryKey;
    private QuerySearchRequest currentSearch;
    private boolean searching;
    private boolean polling;
    private SourceType sourceType = SourceType.QUERY_UI;
    private Set<String> currentHighlights;
    private final Supplier<QueryTablePreferences> queryTablePreferencesSupplier;

    private final List<SearchStateListener> searchStateListeners = new ArrayList<>();
    private final List<SearchErrorListener> errorListeners = new ArrayList<>();
    private final List<TokenErrorListener> tokenErrorListeners = new ArrayList<>();
    private final Map<String, ResultComponent> resultComponents = new HashMap<>();

    public QueryModel(final EventBus eventBus,
                      final RestFactory restFactory,
                      final DateTimeSettingsFactory dateTimeSettingsFactory,
                      final ResultStoreModel resultStoreModel,
                      final Supplier<QueryTablePreferences> queryTablePreferencesSupplier) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.resultStoreModel = resultStoreModel;
        this.queryTablePreferencesSupplier = queryTablePreferencesSupplier;
    }

    public void addResultComponent(final String componentId, final ResultComponent resultComponent) {
        resultComponent.setQueryModel(this);
        resultComponents.put(componentId, resultComponent);
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
        currentHighlights = null;
    }

    /**
     * Begin executing a new search using the supplied query expression.
     */
    public void startNewSearch(final String componentId,
                               final String componentName,
                               final String query,
                               final List<Param> params,
                               final TimeRange timeRange,
                               final boolean incremental,
                               final boolean storeHistory,
                               final String queryInfo,
                               final ExpressionOperator additionalQueryExpression) {
        GWT.log("SearchModel - startNewSearch()");

        // Destroy the previous search and ready all components for a new search to begin.
        reset(DestroyReason.NO_LONGER_NEEDED);

        final QueryContext currentQueryContext = QueryContext
                .builder()
                .params(params)
                .timeRange(timeRange)
                .queryInfo(queryInfo)
                .dateTimeSettings(dateTimeSettingsFactory.getDateTimeSettings())
                .additionalQueryExpression(additionalQueryExpression)
                .build();

        currentSearch = QuerySearchRequest
                .builder()
                .searchRequestSource(
                        SearchRequestSource
                                .builder()
                                .componentId(componentId)
                                .componentName(componentName)
                                .sourceType(sourceType)
                                .ownerDocRef(queryDocRef)
                                .build())
                .query(query)
                .queryContext(currentQueryContext)
                .incremental(incremental)
                .queryTablePreferences(queryTablePreferencesSupplier.get())
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

    private boolean exec(final String componentId,
                         final Consumer<Result> resultConsumer,
                         final QueryKey queryKey) {
        final ResultComponent resultComponent = resultComponents.get(componentId);
        if (resultComponent == null) {
            return false;
        }

        final QuerySearchRequest request = currentSearch
                .copy()
                .queryKey(queryKey)
                .storeHistory(false)
                .groupSelection(resultComponent.getGroupSelection())
                .requestedRange(resultComponent.getRequestedRange())
                .queryTablePreferences(queryTablePreferencesSupplier.get())
                .build();
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.search(currentNode, request))
                .onSuccess(response -> {
                    Result result = null;
                    try {
                        if (response != null && response.getResults() != null) {
                            currentHighlights = response.getHighlights();
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

    private void poll(final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final QuerySearchRequest search = currentSearch;
        if (search != null && polling) {
            final ResultComponent tablePresenter = resultComponents.get(TABLE_COMPONENT_ID);
            final GroupSelection groupSelection = NullSafe
                    .getOrElse(tablePresenter, ResultComponent::getGroupSelection, new GroupSelection());
            final OffsetRange requestedRange = NullSafe
                    .getOrElse(tablePresenter, ResultComponent::getRequestedRange, OffsetRange.UNBOUNDED);

            final QuerySearchRequest request = currentSearch
                    .copy()
                    .queryKey(queryKey)
                    .storeHistory(storeHistory)
                    .groupSelection(groupSelection)
                    .requestedRange(requestedRange)
                    .queryTablePreferences(queryTablePreferencesSupplier.get())
                    .build();

            restFactory
                    .create(QUERY_RESOURCE)
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
                                    poll(false);
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
                        } else {
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
                            poll(false);
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
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

        setErrors(response.getErrorMessages());

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

    private void setErrors(final List<ErrorMessage> errors) {
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
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }

    public QueryKey getCurrentQueryKey() {
        return currentQueryKey;
    }

    public QuerySearchRequest getCurrentSearch() {
        return currentSearch;
    }

    public String getCurrentNode() {
        return currentNode;
    }

    public Set<String> getCurrentHighlights() {
        return currentHighlights;
    }
}
