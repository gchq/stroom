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
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DestroySearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.instance.client.ClientApplicationInstance;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.Console;

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
    private final ClientApplicationInstance applicationInstance;
    private final IndexLoader indexLoader;
    private final TimeZones timeZones;
    private String dashboardUuid;
    private String componentId;
    private final UserPreferencesManager userPreferencesManager;
    private Map<String, ResultComponent> componentMap = new HashMap<>();
    private DashboardSearchResponse currentResponse;
    private QueryKey currentQueryKey;
    private Search currentSearch;
    private Mode mode = Mode.INACTIVE;
    private boolean searching;

    private final List<Consumer<Mode>> modeListeners = new ArrayList<>();
    private final List<Consumer<List<String>>> errorListeners = new ArrayList<>();

    public SearchModel(final RestFactory restFactory,
                       final ClientApplicationInstance applicationInstance,
                       final IndexLoader indexLoader,
                       final TimeZones timeZones,
                       final UserPreferencesManager userPreferencesManager) {
        this.restFactory = restFactory;
        this.applicationInstance = applicationInstance;
        this.indexLoader = indexLoader;
        this.timeZones = timeZones;
        this.userPreferencesManager = userPreferencesManager;
    }

    public void init(final String dashboardUuid,
                     final String componentId) {
        this.dashboardUuid = dashboardUuid;
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
        setMode(Mode.INACTIVE);

        // Stop the spinner from spinning and tell components that they no
        // longer want data.
        for (final ResultComponent resultComponent : componentMap.values()) {
            resultComponent.setWantsData(false);
            resultComponent.endSearch();
        }

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
     * Begin executing a new search using the supplied query expression.
     *
     * @param expression The expression to search with.
     */
    public void startNewSearch(final ExpressionOperator expression,
                               final String params,
                               final boolean incremental,
                               final boolean storeHistory,
                               final String queryInfo) {
        // Destroy the previous search and ready all components for a new search to begin.
        stop();

        // Tell every component that it should want data.
        setWantsData(true);

        GWT.log("SearchModel - startNewSearch()");

        final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Create a parameter map.
                Map<String, String> currentParameterMap = ExpressionParamUtil.parse(params);

                // Copy the expression.
                ExpressionOperator currentExpression = ExpressionUtil.copyOperator(expression);

                currentSearch = Search
                        .builder()
                        .dataSourceRef(dataSourceRef)
                        .expression(currentExpression)
                        .componentSettingsMap(resultComponentMap)
                        .params(getParams(currentParameterMap))
                        .incremental(incremental)
                        .queryInfo(queryInfo)
                        .build();
            }
        }

        if (resultComponentMap != null) {
            final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
            if (dataSourceRef != null && expression != null) {
                // Let the query presenter know search is active.
                setMode(Mode.ACTIVE);

                // Reset all result components and tell them that search is
                // starting.
                for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
                    final ResultComponent resultComponent = entry.getValue();
                    resultComponent.reset();
                    resultComponent.startSearch();
                }

                // Start polling.
                searching = true;
                poll(storeHistory);
            }
        }
    }

    /**
     * Refresh the search data for the specified component.
     */
    public void refresh(final String componentId) {
        final QueryKey queryKey = currentQueryKey;
        final ResultComponent resultComponent = componentMap.get(componentId);
        if (resultComponent != null && queryKey != null) {
            final Map<String, ComponentSettings> resultComponentMap = createComponentSettingsMap();
            if (resultComponentMap != null) {
                final DocRef dataSourceRef = indexLoader.getLoadedDataSourceRef();
                if (dataSourceRef != null) {
                    // Tell the refreshing component that it should want data.
                    resultComponent.setWantsData(true);
                    resultComponent.startSearch();

                    final Search search = Search
                            .builder()
                            .dataSourceRef(currentSearch.getDataSourceRef())
                            .expression(currentSearch.getExpression())
                            .componentSettingsMap(resultComponentMap)
                            .params(currentSearch.getParams())
                            .incremental(true)
                            .build();

                    final List<ComponentResultRequest> requests = new ArrayList<>();
                    final ComponentResultRequest componentResultRequest = resultComponent.getResultRequest();
                    requests.add(componentResultRequest);

                    final DashboardSearchRequest request = DashboardSearchRequest
                            .builder()
                            .queryKey(queryKey)
                            .search(search)
                            .componentResultRequests(requests)
                            .dateTimeSettings(getDateTimeSettings())
                            .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                            .dashboardUuid(dashboardUuid)
                            .componentId(componentId)
                            .build();

                    final Rest<DashboardSearchResponse> rest = restFactory.create();
                    rest
                            .onSuccess(response -> {
                                try {
                                    update(response);
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
                            })
                            .call(DASHBOARD_RESOURCE)
                            .search(request);
                }
            }
        }
    }

    private void destroy(final QueryKey queryKey) {
        final DestroySearchRequest request = DestroySearchRequest
                .builder()
                .queryKey(queryKey)
                .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                .dashboardUuid(dashboardUuid)
                .componentId(componentId)
                .build();
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    if (!response) {
                        Console.log("Unable to destroy search: " + request);
                    }
                })
                .call(DASHBOARD_RESOURCE)
                .destroy(request);
    }

    private void poll(final boolean storeHistory) {
        final QueryKey queryKey = currentQueryKey;
        final Search search = currentSearch;
        if (search != null && searching) {
            final List<ComponentResultRequest> requests = new ArrayList<>();
            for (final Entry<String, ResultComponent> entry : componentMap.entrySet()) {
                final ResultComponent resultComponent = entry.getValue();
                final ComponentResultRequest componentResultRequest = resultComponent.getResultRequest();
                requests.add(componentResultRequest);
            }
            final DashboardSearchRequest request = DashboardSearchRequest
                    .builder()
                    .queryKey(queryKey)
                    .search(search)
                    .componentResultRequests(requests)
                    .dateTimeSettings(getDateTimeSettings())
                    .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                    .dashboardUuid(dashboardUuid)
                    .componentId(componentId)
                    .storeHistory(storeHistory)
                    .build();

            final Rest<DashboardSearchResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
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

    private List<Param> getParams(final Map<String, String> parameterMap) {
        final List<Param> params = new ArrayList<>();
        for (final Entry<String, String> entry : parameterMap.entrySet()) {
            params.add(new Param(entry.getKey(), entry.getValue()));
        }
        return params;
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
            componentMap.values().forEach(resultComponent -> {
                // Stop the spinner from spinning and tell components that they
                // no longer want data.
                resultComponent.setWantsData(false);
                resultComponent.endSearch();
            });
        }

        setErrors(response.getErrors());

        if (response.isComplete()) {
            // Let the query presenter know search is inactive.
            setMode(Mode.INACTIVE);

            // If we have completed search then stop the task spinner.
            searching = false;
        }
    }

    private void setErrors(final List<String> errors) {
        errorListeners.forEach(listener -> listener.accept(errors));
    }

    public Mode getMode() {
        return mode;
    }

    private void setMode(final Mode mode) {
        this.mode = mode;
        modeListeners.forEach(listener -> listener.accept(mode));
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
                                                             final String params) {
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
                .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                .search(search)
                .componentResultRequests(requests)
                .dateTimeSettings(getDateTimeSettings())
                .applicationInstanceUuid(applicationInstance.getInstanceUuid())
                .dashboardUuid(dashboardUuid)
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
