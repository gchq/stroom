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
 *
 */

package stroom.search.elastic.search;

import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.SearchResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.search.elastic.ElasticIndexCache;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.task.server.TaskManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@SuppressWarnings("unused") //used by DI
@Component("elasticSearchStoreFactory")
public class ElasticSearchStoreFactory implements StoreFactory {
    public static final String ENTITY_TYPE = ElasticIndex.ENTITY_TYPE;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchStoreFactory.class);
    private static final int SEND_INTERACTIVE_SEARCH_RESULT_FREQUENCY = 500;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final ElasticIndexCache elasticIndexCache;
    private final StroomPropertyService stroomPropertyService;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    public ElasticSearchStoreFactory(final ElasticIndexCache elasticIndexCache,
                                     final StroomPropertyService stroomPropertyService,
                                     final TaskManager taskManager,
                                     @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount,
                                     final SecurityContext securityContext) {
        this.elasticIndexCache = elasticIndexCache;
        this.stroomPropertyService = stroomPropertyService;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public Store create(final SearchRequest searchRequest) {
        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // Get the search.
        final Query query = searchRequest.getQuery();

        // Load the index.
        final ElasticIndex index;
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            index = elasticIndexCache.get(query.getDataSource());
        }

        // Create a coprocessor settings map.
        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);

        // Create an asynchronous search task.
        final String searchName = "Search '" + searchRequest.getKey().toString() + "'";
        final ElasticAsyncSearchTask asyncSearchTask = new ElasticAsyncSearchTask(
                null,
                securityContext.getUserIdentity(),
                searchName,
                query,
                SEND_INTERACTIVE_SEARCH_RESULT_FREQUENCY,
                coprocessorSettingsMap.getMap(),
                searchRequest.getDateTimeLocale(),
                nowEpochMilli
        );

        // Create a handler for search results.
        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final SearchResultHandler resultHandler = new SearchResultHandler(coprocessorSettingsMap, defaultMaxResultsSizes, storeSize);
        final Sizes maxResultSizes = calculateMaxResultSizes(coprocessorSettingsMap);

        // Create the search result collector.
        final ElasticSearchResultCollector collector = ElasticSearchResultCollector.create(taskManager, asyncSearchTask, null, resultHandler, defaultMaxResultsSizes, maxResultSizes, storeSize);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(collector);

        // Start asynchronous search execution.
        collector.start();

        return collector;
    }

    private Sizes calculateMaxResultSizes(final CoprocessorSettingsMap coprocessorSettingsMap) {
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        Sizes currentMaxResultSize = Sizes.create(0);

        // Find the largest maximum result set size of all table consumers
        for (final Entry<CoprocessorKey, CoprocessorSettings> entry : coprocessorSettingsMap.getMap().entrySet()) {
            final CoprocessorSettings settings = entry.getValue();
            if (settings instanceof TableCoprocessorSettings) {
                final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
                final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();

                currentMaxResultSize = Sizes.max(Sizes.create(tableSettings.getMaxResults()), currentMaxResultSize);
            }
        }

        return Sizes.min(currentMaxResultSize, defaultMaxResultsSizes);
    }

    private Sizes getDefaultMaxResultsSizes() {
        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = stroomPropertyService.getProperty(ElasticSearchResultCollector.PROP_KEY_STORE_SIZE);
        return extractValues(value);
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
