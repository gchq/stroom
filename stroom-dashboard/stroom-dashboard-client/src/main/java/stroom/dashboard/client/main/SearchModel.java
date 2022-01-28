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

import stroom.dashboard.client.table.TimeZones;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Result;
import stroom.ui.config.shared.UserPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class SearchModel {

    private final SearchBus searchBus;
    private final IndexLoader indexLoader;
    private final TimeZones timeZones;
    private final UserPreferencesManager userPreferencesManager;
    private Map<String, ResultComponent> componentMap = new HashMap<>();
    private Map<String, String> currentParameterMap;
    private ExpressionOperator currentExpression;
    private DashboardSearchResponse currentResult;
    private DashboardUUID dashboardUUID;
    private DashboardQueryKey currentQueryKey;
    private Search currentSearch;
    private Search activeSearch;
    private Mode mode = Mode.INACTIVE;

    private final List<Consumer<Mode>> modeListeners = new ArrayList<>();
    private final List<Consumer<List<String>>> errorListeners = new ArrayList<>();

    public SearchModel(final SearchBus searchBus,
                       final IndexLoader indexLoader,
                       final TimeZones timeZones,
                       final UserPreferencesManager userPreferencesManager) {
        this.searchBus = searchBus;
        this.indexLoader = indexLoader;
        this.timeZones = timeZones;
        this.userPreferencesManager = userPreferencesManager;
    }

    /**
     * Stop searching, set the search mode to inactive and tell all components
     * that they no longer want data and search has ended.
     */
    public void stop() {
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

        // Force a poll to ensure any running query is destroyed.
        searchBus.poll();
    }

    /**
     * Destroy the previous search and ready all components for a new search to
     * begin.
     */
    public void reset() {
        // Stop previous search.
        stop();

        // Tell every component that it should want data.
        setWantsData(true);
    }

    public void pause() {
        // Tell every component not to want data.
        setWantsData(false);
        setMode(Mode.PAUSED);
    }

    public void resume() {
        // Tell every component that it should want data.
        setWantsData(true);
        setMode(Mode.ACTIVE);
    }

    /**
     * Prepares the necessary parts for a search without actually starting the search. Intended
     * for use when you just want the complete {@link DashboardSearchRequest} object
     */
    private Map<String, ComponentSettings> initModel(final ExpressionOperator expression,
                                                     final String params,
                                                     final boolean incremental,
                                                     final boolean storeHistory,
                                                     final String queryInfo) {

        final Map<String, ComponentSettings> componentSettingsMap = createComponentSettingsMap();
        if (componentSettingsMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Create a parameter map.
                currentParameterMap = ExpressionParamUtil.parse(params);

                // Copy the expression.
                currentExpression = ExpressionUtil.copyOperator(expression);

                currentQueryKey = new DashboardQueryKey(
                        dashboardUUID.getUUID(),
                        dashboardUUID.getDashboardUuid(),
                        dashboardUUID.getComponentId());

                currentSearch = Search
                        .builder()
                        .dataSourceRef(dataSourceRef)
                        .expression(currentExpression)
                        .componentSettingsMap(componentSettingsMap)
                        .params(getParams(currentParameterMap))
                        .incremental(incremental)
                        .storeHistory(storeHistory)
                        .queryInfo(queryInfo)
                        .build();
            }
        }
        return componentSettingsMap;
    }

    private List<Param> getParams(final Map<String, String> parameterMap) {
        final List<Param> params = new ArrayList<>();
        for (final Entry<String, String> entry : parameterMap.entrySet()) {
            params.add(new Param(entry.getKey(), entry.getValue()));
        }
        return params;
    }

    /**
     * Begin executing a new search using the supplied query expression.
     *
     * @param expression The expression to search with.
     */
    public void startNewSearch(final ExpressionOperator expression,
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

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId) {
        final ResultComponent resultComponent = componentMap.get(componentId);
        if (resultComponent != null) {
            final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
            if (resultComponentMap != null) {
                final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
                if (dataSourceRef != null) {
                    currentSearch = Search
                            .builder()
                            .dataSourceRef(dataSourceRef)
                            .expression(currentExpression)
                            .componentSettingsMap(resultComponentMap)
                            .params(getParams(currentParameterMap))
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
     * Method to update the wantsData state for all interested components.
     *
     * @param wantsData True if you want all components to be ready to receive data.
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
     * @param result The search response.
     */
    void update(final DashboardSearchResponse result) {
        currentResult = result;

        // Give results to the right components.
        if (result.getResults() != null) {
            for (final Result componentResult : result.getResults()) {
                final ResultComponent resultComponent = componentMap.get(componentResult.getComponentId());
                if (resultComponent != null) {
                    resultComponent.setData(componentResult);
                }
            }
        }

        // Tell all components if we are complete.
        if (result.isComplete()) {
            componentMap.values().forEach(resultComponent -> {
                // Stop the spinner from spinning and tell components that they
                // no longer want data.
                resultComponent.setWantsData(false);
                resultComponent.endSearch();
            });
        }

        errorListeners.forEach(listener -> listener.accept(result.getErrors()));

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
        modeListeners.forEach(listener -> listener.accept(mode));
    }

    /**
     * The search bus calls this method to get the search request for this
     * search model.
     *
     * @return The current search request.
     */
    DashboardSearchRequest getCurrentRequest() {
        final Search search = currentSearch;
        final List<ComponentResultRequest> requests = new ArrayList<>();
        for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
            final ResultComponent resultComponent = entry.getValue();
            final ComponentResultRequest componentResultRequest = resultComponent.getResultRequest();
            requests.add(componentResultRequest);
        }

        return new DashboardSearchRequest(currentQueryKey, search, requests, getDateTimeSettings());
    }

    private DateTimeSettings getDateTimeSettings() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentPreferences();
        return DateTimeSettings
                .builder()
                .dateTimePattern(userPreferences.getDateTimePattern())
                .timeZone(userPreferences.getTimeZone())
                .localZoneId(timeZones.getLocalTimeZoneId())
                .build();
    }

    /**
     * Initialises the model for passed expression and current result settings and returns
     * the corresponding {@link DashboardSearchRequest} object
     */
    public DashboardSearchRequest createDownloadQueryRequest(final ExpressionOperator expression,
                                                             final String params,
                                                             final boolean incremental,
                                                             final boolean storeHistory,
                                                             final String queryInfo) {
        Search search = null;
        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Create a parameter map.
                final Map<String, String> currentParameterMap = ExpressionParamUtil.parse(params);

                // Copy the expression.
                final ExpressionOperator currentExpression = ExpressionUtil.copyOperator(expression);

                search = Search
                        .builder()
                        .dataSourceRef(dataSourceRef)
                        .expression(currentExpression)
                        .componentSettingsMap(resultComponentMap)
                        .params(getParams(currentParameterMap))
                        .incremental(incremental)
                        .storeHistory(storeHistory)
                        .queryInfo(queryInfo)
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

        return new DashboardSearchRequest(currentQueryKey, search, requests, getDateTimeSettings());
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
        stop();
        currentQueryKey = new DashboardQueryKey(
                dashboardUUID.getUUID(),
                dashboardUUID.getDashboardUuid(),
                dashboardUUID.getComponentId());
    }

    public DashboardSearchResponse getCurrentResult() {
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

    public void addModeListener(final Consumer<Mode> consumer) {
        modeListeners.add(consumer);
    }

    public void removeModeListener(final Consumer<Mode> consumer) {
        modeListeners.remove(consumer);
    }

    public void addErrorListener(final Consumer<List<String>> consumer) {
        errorListeners.add(consumer);
    }

    public void removeErrorListener(final Consumer<List<String>> consumer) {
        errorListeners.remove(consumer);
    }

    public enum Mode {
        ACTIVE,
        INACTIVE,
        PAUSED
    }
}
