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

package stroom.statistics.server.common.search;

import org.springframework.stereotype.Component;
import stroom.query.CoprocessorMap;
import stroom.query.SearchDataSourceProvider;
import stroom.query.SearchResultCollector;
import stroom.query.SearchResultHandler;
import stroom.query.shared.QueryKey;
import stroom.query.shared.Search;
import stroom.query.shared.SearchRequest;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.TaskManager;

import javax.inject.Inject;

@Component
public class StatStoreSearchDataSourceProvider implements SearchDataSourceProvider {
    private final TaskManager taskManager;
    private final StatisticStoreCache statisticsDataSourceCache;

    @Inject
    public StatStoreSearchDataSourceProvider(final TaskManager taskManager,
            final StatisticStoreCache statisticsDataSourceCache) {
        this.taskManager = taskManager;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
    }

    @Override
    public SearchResultCollector createCollector(final String userToken, final QueryKey queryKey,
            final SearchRequest searchRequest) {
        final Search search = searchRequest.getSearch();

        final StatisticStoreEntity entity = statisticsDataSourceCache
                .getStatisticsDataSource(search.getDataSourceRef());

        if (entity == null) {
            throw new RuntimeException(
                    String.format("Could not retrieve datasource entity %s from the cache, " +
                                    "try clearing the StatisticsDataSourceCaches",
                            search.getDataSourceRef()));
        }

        // Create a coprocessor map.
        final CoprocessorMap coprocessorMap = new CoprocessorMap(search.getComponentSettingsMap());

        // Create a stat store search task.
        final String searchName = "Search '" + queryKey.toString() + "'";
        final StatStoreSearchTask statStoreSearchTask = new StatStoreSearchTask(userToken, searchName, search,
                entity, coprocessorMap.getMap());

        // Create a handler for search results.
        final SearchResultHandler resultHandler = new SearchResultHandler(coprocessorMap);

        // Create the search result collector.
        final StatStoreSearchResultCollector searchResultCollector = new StatStoreSearchResultCollector(taskManager,
                statStoreSearchTask, resultHandler);

        // Tell the task where results will be collected.
        statStoreSearchTask.setResultCollector(searchResultCollector);

        return searchResultCollector;
    }

    @Override
    public String getEntityType() {
        return StatisticStoreEntity.ENTITY_TYPE;
    }
}
