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

import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.table.TimeZones;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.client.KVMapUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class SearchModel {
    private final SearchBus searchBus;
    private final QueryPresenter queryPresenter;
    private final IndexLoader indexLoader;
    private final TimeZones timeZones;
    private Map<String, ResultComponent> componentMap = new HashMap<>();
    private Map<String, String> currentParameterMap;
    private ExpressionOperator currentExpression;
    private SearchResponse currentResult;
    private DashboardUUID dashboardUUID;
    private DashboardQueryKey currentQueryKey;
    private Search currentSearch;
    private Search activeSearch;
    private Mode mode = Mode.INACTIVE;

    public SearchModel(final SearchBus searchBus,
                       final QueryPresenter queryPresenter,
                       final IndexLoader indexLoader,
                       final TimeZones timeZones) {
        this.searchBus = searchBus;
        this.queryPresenter = queryPresenter;
        this.indexLoader = indexLoader;
        this.timeZones = timeZones;
    }

    /**
     * Stop searching, set the search mode to inactive and tell all components
     * that they no longer want data and search has ended.
     */
    public void destroy() {
        if (currentQueryKey != null) {
            searchBus.remove(currentQueryKey);
        }
        setMode(Mode.INACTIVE);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        for (final ResultComponent resultComponent : componentMap.values()) {
            resultComponent.setWantsData(false);
            resultComponent.endSearch();
        }
    }

    /**
     * Destroy the previous search and ready all components for a new search to
     * begin.
     */
    private void reset() {
        // Destroy previous search.
        destroy();

        // Tell every component that it should want data.
        setWantsData(true);
    }

    /**
     * Run a search with the provided expression, returning results for all
     * components.
     */
    public void search(final ExpressionOperator expression,
                       final String params,
                       final boolean incremental,
                       final boolean storeHistory,
                       final String queryInfo) {
        // Toggle the request mode or start a new search.
        switch (mode) {
            case ACTIVE:
                // Tell every component not to want data.
                setWantsData(false);
                setMode(Mode.PAUSED);
                break;
            case INACTIVE:
                reset();
                startNewSearch(expression, params, incremental, storeHistory, queryInfo);
                break;
            case PAUSED:
                // Tell every component that it should want data.
                setWantsData(true);
                setMode(Mode.ACTIVE);
                break;
        }
    }

    /**
     * Prepares the necessary parts for a search without actually starting the search. Intended
     * for use when you just want the complete {@link SearchRequest} object
     */
    private Map<String, ComponentSettings> initModel(final ExpressionOperator expression,
                                                     final String params,
                                                     final boolean incremental,
                                                     final boolean storeHistory,
                                                     final String queryInfo) {

        final Map<String, ComponentSettings> resultComponentMap = createResultComponentMap();
        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Create a parameter map.
                currentParameterMap = KVMapUtil.parse(params);

                // Replace any parameters in the expression.
                final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(expression.getEnabled(), expression.getOp());
                replaceExpressionParameters(builder, expression, currentParameterMap);
                currentExpression = builder.build();

                currentQueryKey = DashboardQueryKey.create(
                        dashboardUUID.getUUID(),
                        dashboardUUID.getDashboardId(),
                        dashboardUUID.getComponentId());

                currentSearch = new Search.Builder()
                        .dataSourceRef(dataSourceRef)
                        .expression(currentExpression)
                        .componentSettingsMap(resultComponentMap)
                        .paramMap(currentParameterMap)
                        .incremental(incremental)
                        .storeHistory(storeHistory)
                        .queryInfo(queryInfo)
                        .build();
            }
        }
        return resultComponentMap;
    }

    /**
     * Begin executing a new search using the supplied query expression.
     *
     * @param expression The expression to search with.
     */
    private void startNewSearch(final ExpressionOperator expression,
                                final String params,
                                final boolean incremental,
                                final boolean storeHistory,
                                final String queryInfo) {

        final Map<String, ComponentSettings> resultComponentMap = initModel(
                expression,
                params,
                incremental,
                storeHistory,
                queryInfo);

        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                activeSearch = currentSearch;

                // Let the query presenter know search is active.
                setMode(Mode.ACTIVE);

                // Reset all result components and tell them that search is
                // starting.
                for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
                    final ResultComponent resultComponent = entry.getValue();
                    resultComponent.reset();
                    resultComponent.startSearch();
                }

                // Register this new query so that the bus can perform the
                // search.
                searchBus.put(currentQueryKey, this);
                searchBus.poll();
            }
        }
    }

    private void replaceExpressionParameters(final ExpressionOperator.Builder builder,
                                             final ExpressionOperator operator,
                                             final Map<String, String> paramMap) {
        if (operator.getChildren() != null) {
            for (ExpressionItem child : operator.getChildren()) {
                if (child instanceof ExpressionOperator) {
                    final ExpressionOperator childOperator = (ExpressionOperator) child;
                    final ExpressionOperator.Builder childBuilder = new ExpressionOperator.Builder(childOperator.getOp())
                            .enabled(childOperator.getEnabled());
                    builder.addOperator(childBuilder.build());
                    replaceExpressionParameters(childBuilder, childOperator, paramMap);
                } else if (child instanceof ExpressionTerm) {
                    final ExpressionTerm term = (ExpressionTerm) child;
                    final String value = term.getValue();
                    final String replaced = KVMapUtil.replaceParameters(value, paramMap);
                    builder.addOperator(new ExpressionTerm.Builder()
                            .enabled(term.getEnabled())
                            .field(term.getField())
                            .condition(term.getCondition())
                            .value(replaced)
                            .dictionary(term.getDictionary())
                            .build());
                }
            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId) {
        final ResultComponent resultComponent = componentMap.get(componentId);
        if (resultComponent != null) {
            final Map<String, ComponentSettings> resultComponentMap = createResultComponentMap();
            if (resultComponentMap != null) {
                final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
                if (dataSourceRef != null) {
                    currentSearch = new Search.Builder()
                            .dataSourceRef(dataSourceRef)
                            .expression(currentExpression)
                            .componentSettingsMap(resultComponentMap)
                            .paramMap(currentParameterMap)
                            .incremental(true)
                            .storeHistory(false)
                            .build();
                    activeSearch = currentSearch;

                    // Tell the refreshing component that it should want data.
                    resultComponent.setWantsData(true);
                    resultComponent.startSearch();
                    searchBus.poll();
                }
            }
        }
    }

    /**
     * Creates a result component map for all components.
     *
     * @return A result component map.
     */
    private Map<String, ComponentSettings> createResultComponentMap() {
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
     * Method to update the wantsData state for all interested components.
     *
     * @param wantsData
     */
    private void setWantsData(final boolean wantsData) {
        // Tell every component that it should want data.
        for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
            final ResultComponent resultComponent = entry.getValue();
            resultComponent.setWantsData(wantsData);
        }
    }

    /**
     * On receiving a search result from the server update all interested
     * components with new data.
     *
     * @param result
     */
    void update(final SearchResponse result) {
        currentResult = result;

        for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
            final String componentId = entry.getKey();
            final ResultComponent resultComponent = entry.getValue();
            if (result.getResults() != null && result.getResults().containsKey(componentId)) {
                final String json = result.getResults().get(componentId);
                resultComponent.setData(json);
            }

            if (result.isComplete()) {
                // Stop the spinner from spinning and tell components that they
                // no longer want data.
                resultComponent.setWantsData(false);
                resultComponent.endSearch();
            }
        }

        queryPresenter.setErrors(result.getErrors());

        if (result.isComplete()) {
            // Let the query presenter know search is inactive.
            setMode(Mode.INACTIVE);

            // If we have completed search then stop the task spinner.
            currentSearch = null;
        }
    }

    public Mode getMode() {
        return mode;
    }

    private void setMode(final Mode mode) {
        this.mode = mode;
        queryPresenter.setMode(mode);
    }

    /**
     * The search bus calls this method to get the search request for this
     * search model.
     *
     * @return
     */
    SearchRequest getCurrentRequest() {
        final Search search = currentSearch;
        if (search == null || componentMap.size() == 0) {
            return null;
        }

        final Map<String, ComponentResultRequest> requestMap = new HashMap<>();
        for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
            final String componentId = entry.getKey();
            final ResultComponent resultComponent = entry.getValue();
            final ComponentResultRequest componentResultRequest = resultComponent.getResultRequest();
            requestMap.put(componentId, componentResultRequest);
        }

        return new SearchRequest(search, requestMap, timeZones.getTimeZone());
    }

    /**
     * Initialises the model for passed expression and current result settings and returns
     * the corresponding {@link SearchRequest} object
     */
    public SearchRequest buildSearchRequest(final ExpressionOperator expression,
                                            final String params,
                                            final boolean incremental,
                                            final boolean storeHistory,
                                            final String searchPurpose) {

        initModel(expression, params, incremental, storeHistory, searchPurpose);

        return getCurrentRequest();
    }

    public boolean isSearching() {
        return currentSearch != null;
    }

    public DashboardQueryKey getCurrentQueryKey() {
        return currentQueryKey;
    }

    public Search getActiveSearch() {
        return activeSearch;
    }

    public IndexLoader getIndexLoader() {
        return indexLoader;
    }

    public void setDashboardUUID(final DashboardUUID dashboardUUID) {
        this.dashboardUUID = dashboardUUID;
        destroy();
        currentQueryKey = DashboardQueryKey.create(
                dashboardUUID.getUUID(),
                dashboardUUID.getDashboardId(),
                dashboardUUID.getComponentId());
    }

    public SearchResponse getCurrentResult() {
        return currentResult;
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

    public enum Mode {
        ACTIVE, INACTIVE, PAUSED
    }
}
