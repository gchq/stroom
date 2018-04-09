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

package stroom.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.logging.SearchEventLog;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.QueryEntity;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchBusPollAction;
import stroom.dashboard.shared.SearchBusPollResult;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.datasource.DataSourceProvider;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@TaskHandlerBean(task = SearchBusPollAction.class)
class SearchBusPollActionHandler extends AbstractTaskHandler<SearchBusPollAction, SearchBusPollResult> {
    private transient static final Logger LOGGER = LoggerFactory.getLogger(SearchBusPollActionHandler.class);

    private final QueryService queryService;
    private final SearchEventLog searchEventLog;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final ActiveQueriesManager activeQueriesManager;
    private final SearchRequestMapper searchRequestMapper;
    private final Security security;

    @Inject
    SearchBusPollActionHandler(final QueryService queryService,
                               final SearchEventLog searchEventLog,
                               final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                               final ActiveQueriesManager activeQueriesManager,
                               final SearchRequestMapper searchRequestMapper,
                               final Security security) {
        this.queryService = queryService;
        this.searchEventLog = searchEventLog;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.activeQueriesManager = activeQueriesManager;
        this.searchRequestMapper = searchRequestMapper;
        this.security = security;
    }

    @Override
    public SearchBusPollResult exec(final SearchBusPollAction action) {
        return security.secureResult(() -> {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            return security.useAsReadResult(() -> {
                if (LOGGER.isDebugEnabled()) {
                    final StringBuilder sb = new StringBuilder(
                            "Only the following search queries should be active for session '");
                    sb.append(action.getUserToken());
                    sb.append("'\n");
                    for (final DashboardQueryKey queryKey : action.getSearchActionMap().keySet()) {
                        sb.append("\t");
                        sb.append(queryKey.toString());
                    }
                    LOGGER.debug(sb.toString());
                }

                final String searchSessionId = action.getUserToken() + "_" + action.getApplicationInstanceId();
                final ActiveQueries activeQueries = activeQueriesManager.get(searchSessionId);
                final Map<DashboardQueryKey, SearchResponse> searchResultMap = new HashMap<>();

//            // Fix query keys so they have session and user info.
//            for (final Entry<DashboardQueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
//                final QueryKey queryKey = entry.getValues().getQueryKey();
//                queryKey.setSessionId(action.getSessionId());
//                queryKey.setUserId(action.getUserId());
//            }

                // Kill off any queries that are no longer required by the UI.
                activeQueries.destroyUnusedQueries(action.getSearchActionMap().keySet());

                // Get query results for every active query.
                for (final Entry<DashboardQueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
                    final DashboardQueryKey queryKey = entry.getKey();

                    final SearchRequest searchRequest = entry.getValue();

                    if (searchRequest != null && searchRequest.getSearch() != null) {
                        final SearchResponse searchResponse = processRequest(activeQueries, queryKey, searchRequest);
                        if (searchResponse != null) {
                            searchResultMap.put(queryKey, searchResponse);
                        }
                    }
                }

                return new SearchBusPollResult(searchResultMap);
            });
        });
    }

    private SearchResponse processRequest(final ActiveQueries activeQueries, final DashboardQueryKey queryKey, final SearchRequest searchRequest) {
        SearchResponse result;

        boolean newSearch = false;
        final Search search = searchRequest.getSearch();

        try {
            synchronized (SearchBusPollActionHandler.class) {
                // Make sure we have active queries for all current UI queries.
                // Note: This also ensures that the active query cache is kept alive
                // for all open UI components.
                final ActiveQuery activeQuery = activeQueries.getExistingQuery(queryKey);

                // If the query doesn't have an active query for this query key then
                // this is new.
                if (activeQuery == null) {
                    newSearch = true;

                    // Store the new active query for this query.
                    activeQueries.addNewQuery(queryKey, search.getDataSourceRef());

                    // Add this search to the history so the user can get back to this
                    // search again.
                    storeSearchHistory(queryKey, search);
                }
            }

            // Perform the search or update results.
            final DocRef dataSourceRef = search.getDataSourceRef();
            if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                throw new RuntimeException("No search data source has been specified");
            }

            // Get the data source provider for this query.
            final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                    .getDataSourceProvider(dataSourceRef)
                    .orElseThrow(() ->
                            new RuntimeException("No search provider found for '" + dataSourceRef.getType() + "' data source"));

            stroom.query.api.v2.SearchRequest mappedRequest = searchRequestMapper.mapRequest(queryKey, searchRequest);
            stroom.query.api.v2.SearchResponse searchResponse = dataSourceProvider.search(mappedRequest);
            result = new SearchResponseMapper().mapResponse(searchResponse);

            if (newSearch) {
                // Log this search action for the current user.
                searchEventLog.search(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo());
            }

        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);

            if (newSearch) {
                searchEventLog.search(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo(), e);
            }

            result = new SearchResponse();
            if (e.getMessage() == null) {
                result.setErrors(e.getClass().getName());
            } else {
                result.setErrors(e.getClass().getName() + ": " + e.getMessage());
            }
            result.setComplete(true);
        }

        return result;
    }

    private void storeSearchHistory(final DashboardQueryKey queryKey, final Search search) {
        // We only want to record search history for user initiated searches.
        if (search.isStoreHistory()) {
            try {
                // Add this search to the history so the user can get back to
                // this search again.
                List<Param> params;
                if (search.getParamMap() != null && search.getParamMap().size() > 0) {
                    params = new ArrayList<>(search.getParamMap().size());
                    for (final Entry<String, String> entry : search.getParamMap().entrySet()) {
                        params.add(new Param(entry.getKey(), entry.getValue()));
                    }
                } else {
                    params = null;
                }

                final Query query = new Query(search.getDataSourceRef(), search.getExpression(), params);

                final QueryEntity queryEntity = queryService.create("History");

                queryEntity.setDashboardUuid(queryKey.getDashboardUuid());
                queryEntity.setQueryId(queryKey.getQueryId());
                queryEntity.setQuery(query);
                queryService.save(queryEntity);

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
