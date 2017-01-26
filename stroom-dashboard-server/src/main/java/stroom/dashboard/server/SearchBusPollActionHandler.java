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

package stroom.dashboard.server;

import org.springframework.context.annotation.Scope;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.QueryEntity;
import stroom.dashboard.shared.SharedQueryKey;
import stroom.dashboard.shared.QueryService;
import stroom.dashboard.shared.SearchBusPollAction;
import stroom.dashboard.shared.SearchBusPollResult;
import stroom.dashboard.shared.Sort;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.VisResultRequest;
import stroom.logging.SearchEventLog;
import stroom.query.api.DocRef;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.query.api.ResultRequest;
import stroom.query.api.TableSettings;
import stroom.security.SecurityContext;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.OffsetRange;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@TaskHandlerBean(task = SearchBusPollAction.class)
@Scope(value = StroomScope.TASK)
class SearchBusPollActionHandler extends AbstractTaskHandler<SearchBusPollAction, SearchBusPollResult> {
    private transient static final StroomLogger LOGGER = StroomLogger.getLogger(SearchBusPollActionHandler.class);

    private final QueryService queryService;
    private final SearchEventLog searchEventLog;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final ActiveQueriesManager searchSessionManager;
    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final SecurityContext securityContext;

    @Inject
    SearchBusPollActionHandler(final QueryService queryService,
                               final SearchEventLog searchEventLog,
                               final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                               final ActiveQueriesManager searchSessionManager,
                               final ClusterResultCollectorCache clusterResultCollectorCache, final SecurityContext securityContext) {
        this.queryService = queryService;
        this.searchEventLog = searchEventLog;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.searchSessionManager = searchSessionManager;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.securityContext = securityContext;
    }

    @Override
    public SearchBusPollResult exec(final SearchBusPollAction action) {
        try {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            securityContext.elevatePermissions();

            if (LOGGER.isDebugEnabled()) {
                final StringBuilder sb = new StringBuilder(
                        "Only the following search queries should be active for session '");
                sb.append(action.getSessionId());
                sb.append("'\n");
                for (final QueryKey queryKey : action.getSearchActionMap().keySet()) {
                    sb.append("\t");
                    sb.append(queryKey.toString());
                }
                LOGGER.debug(sb.toString());
            }

            final String searchSessionId = action.getSessionId() + "_" + action.getApplicationInstanceId();
            final ActiveQueries searchSession = searchSessionManager.get(searchSessionId);
            final Map<QueryKey, SearchResponse> searchResultMap = new HashMap<>();

            // Fix query keys so they have session and user info.
            for (final Entry<QueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
                final QueryKey queryKey = entry.getKey();
                queryKey.setSessionId(action.getSessionId());
                queryKey.setUserId(action.getUserId());
            }

            // Kill off any queries that are no longer required by the UI.
            searchSession.destroyUnusedQueries(action.getSearchActionMap().keySet());

            // Get query results for every active query.
            for (final Entry<QueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
                final QueryKey queryKey = entry.getKey();

                final SearchRequest searchRequest = entry.getValue();

                if (searchRequest != null && searchRequest.getSearch() != null) {
                    final SearchResponse searchResponse = processRequest(searchSession, queryKey, searchRequest);
                    if (searchResponse != null) {
                        searchResultMap.put(queryKey, searchResponse);
                    }
                }
            }

            return new SearchBusPollResult(searchResultMap);
        } finally {
            securityContext.restorePermissions();
        }
    }

    private SearchResponse processRequest(final ActiveQueries activeQueries, final QueryKey queryKey, final SearchRequest searchRequest) {
        SearchResponse result = null;

        boolean newSearch = false;
        final Search search = searchRequest.getSearch();

        try {
            synchronized (SearchBusPollActionHandler.class) {
                // Make sure we have active queries for all current UI queries.
                // Note: This also ensures that the active query cache is kept alive
                // for all open UI components.
                ActiveQuery activeQuery = activeQueries.getExistingQuery(queryKey);

                // If the query doesn't have an active query for this query key then
                // this is new.
                if (activeQuery == null) {
                    newSearch = true;

                    // Store the new active query for this query.
                    activeQuery = activeQueries.addNewQuery(queryKey, search.getDataSourceRef());

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
            final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry.getDataSourceProvider(dataSourceRef);

            if (dataSourceProvider == null) {
                throw new RuntimeException(
                    "No search provider found for '" + dataSourceRef.getType() + "' data source");
            }

//            result = dataSourceProvider.search(mapRequest(searchRequest));

            if (newSearch) {
                // Log this search action for the current user.
                searchEventLog.search(search.getDataSourceRef(), search.getExpression());
            }

        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);

            if (newSearch) {
                searchEventLog.search(search.getDataSourceRef(), search.getExpression(), e);
            }

            result = new SearchResponse();
            result.setErrors(e.getMessage());
            result.setComplete(true);
        }

        return result;
    }

    private void storeSearchHistory(final QueryKey queryKey, final Search search) {
        try {
            if (queryKey instanceof SharedQueryKey) {
                final SharedQueryKey queryKeyImpl = (SharedQueryKey) queryKey;

                // Add this search to the history so the user can get back to
                // this search again.
                Param[] params;
                if (search.getParamMap() != null && search.getParamMap().size() > 0) {
                    params = new Param[search.getParamMap().size()];
                    int i = 0;
                    for (final Entry<String, String> entry : search.getParamMap().entrySet()) {
                        params[i++] = new Param(entry.getKey(), entry.getValue());
                    }
                } else {
                    params = null;
                }

                final Query query = new Query(search.getDataSourceRef(), search.getExpression(), params);
                final QueryEntity queryEntity = queryService.create(null, "History");

                queryEntity.setDashboard(Dashboard.createStub(queryKeyImpl.getDashboardId()));
                queryEntity.setQuery(query);
                queryService.save(queryEntity);
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private stroom.query.api.SearchRequest mapRequest(final QueryKey queryKey, final SearchRequest searchRequest) {
        if (searchRequest == null) {
            return null;
        }

        final stroom.query.api.SearchRequest copy = new stroom.query.api.SearchRequest();
        copy.setKey(queryKey);
        copy.setQuery(mapQuery(searchRequest));
        copy.setResultRequests(mapResultRequests(searchRequest));
        copy.setDateTimeLocale(searchRequest.getDateTimeLocale());
        copy.setIncremental(searchRequest.getSearch().getIncremental());

        return copy;
    }

    private Query mapQuery(final SearchRequest searchRequest) {
        if (searchRequest.getSearch() == null) {
            return null;
        }

        final Query query = new Query();
        query.setDataSource(searchRequest.getSearch().getDataSourceRef());

        query.setExpression(searchRequest.getSearch().getExpression());

        if (searchRequest.getSearch().getParamMap() != null && searchRequest.getSearch().getParamMap().size() > 0) {
            Param[] params = new Param[searchRequest.getSearch().getParamMap().size()];
            int i = 0;
            for (final Entry<String, String> entry : searchRequest.getSearch().getParamMap().entrySet()) {
                final Param param = new Param(entry.getKey(), entry.getValue());
                params[i++] = param;
            }

            query.setParams(params);
        }

        return query;
    }

    private ResultRequest[] mapResultRequests(final SearchRequest searchRequest) {
        if (searchRequest.getComponentResultRequests() == null || searchRequest.getComponentResultRequests().size() == 0) {
            return null;
        }

            final ResultRequest[] resultRequests = new ResultRequest[searchRequest.getComponentResultRequests().size()];
            int i = 0;
            for (final Entry<String, ComponentResultRequest> entry : searchRequest.getComponentResultRequests().entrySet()) {
                final String componentId = entry.getKey();
                final ComponentResultRequest componentResultRequest = entry.getValue();
                if (componentResultRequest instanceof TableResultRequest) {
                    final TableResultRequest tableResultRequest = (TableResultRequest) componentResultRequest;
                    final stroom.query.api.TableResultRequest copy = new stroom.query.api.TableResultRequest();
                    copy.setComponentId(componentId);
                    copy.setFetchData(tableResultRequest.wantsData());
                    copy.setTableSettings(mapTableSettings(tableResultRequest.getTableSettings()));
                    copy.setRequestedRange(mapOffsetRange(tableResultRequest.getRequestedRange()));
                    copy.setOpenGroups(mapCollection(tableResultRequest.getOpenGroups()));
                    resultRequests[i++] = copy;

                } else if (componentResultRequest instanceof VisResultRequest) {
                    final VisResultRequest visResultRequest = (VisResultRequest) componentResultRequest;
                    final stroom.query.api.VisResultRequest copy = new stroom.query.api.VisResultRequest();
                    copy.setComponentId(componentId);
                    copy.setFetchData(visResultRequest.wantsData());
                    copy.setTableSettings(mapTableSettings(visResultRequest.getVisDashboardSettings().getTableSettings()));
//                    copy.setStructure(visResultRequest.getVisDashboardSettings().getJSON());
//                    copy.setParams(visResultRequest.getVisDashboardSettings().getJSON());
                    resultRequests[i++] = copy;
                }
            }

         return resultRequests;
    }

    private TableSettings mapTableSettings(final TableComponentSettings tableComponentSettings) {
        if (tableComponentSettings == null) {
            return null;
        }

        final TableSettings tableSettings = new TableSettings();
        tableSettings.setQueryId(tableComponentSettings.getQueryId());
        tableSettings.setFields(mapFields(tableComponentSettings.getFields()));
        tableSettings.setExtractValues(tableComponentSettings.getExtractValues());
        tableSettings.setExtractionPipeline(tableComponentSettings.getExtractionPipeline());
        tableSettings.setMaxResults(mapIntArray(tableComponentSettings.getMaxResults()));
        tableSettings.setShowDetail(tableComponentSettings.getShowDetail());

        return tableSettings;
    }

    private stroom.query.api.Field[] mapFields(final List<Field> fields) {
        if (fields == null || fields.size() == 0) {
            return null;
        }

        stroom.query.api.Field[] arr = new stroom.query.api.Field[fields.size()];
        int i = 0;
        for (final Field field : fields) {
            stroom.query.api.Field fld = new stroom.query.api.Field();
            fld.setName(field.getName());
            fld.setExpression(field.getExpression());
//            fld.setSort(field.getSort());
//            fld.setFilter(field.getFilter());
//            fld.setFormat(field.getFormat());
            fld.setGroup(field.getGroup());
            arr[i++] = fld;
        }

        return arr;
    }

    private Integer[] mapIntArray(final int[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }

        final Integer[] copy = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            copy[i] = arr[i];
        }

        return copy;
    }

    private <T> T[] mapCollection(final Collection<T> collection) {
//        if (collection == null || collection.size() == 0) {
            return null;
//        }
//
//        T[] copy = new T[collection.size()];
//        int i = 0;
//        for (final T t : collection) {
//            copy[i++] = t;
//        }
//
//        return copy;
    }

    private stroom.query.api.OffsetRange mapOffsetRange(final  OffsetRange<Integer> offsetRange) {
        if (offsetRange == null) {
            return null;
        }

        final stroom.query.api.OffsetRange copy = new stroom.query.api.OffsetRange(offsetRange.getOffset(), offsetRange.getLength());
        return copy;
    }

    private stroom.query.api.Sort mapSort(final Sort sort) {
        if (sort == null) {
            return null;
        }

        final stroom.query.api.Sort copy = new stroom.query.api.Sort();
//        copy.setDirection(sort.getDirection());
        copy.setOrder(sort.getOrder());

        return copy;
    }
}
