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

package stroom.search;

import org.junit.Assert;
import stroom.entity.shared.DocRefUtil;
import stroom.index.IndexService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractSearchTest extends AbstractCoreIntegrationTest {
    @Inject
    private LuceneSearchResponseCreatorManager searchResponseCreatorManager;

    protected SearchResponse search(SearchRequest searchRequest) {
        return search(searchRequest, searchResponseCreatorManager);
    }

    protected static SearchResponse search(final SearchRequest searchRequest,
                                           final SearchResponseCreatorManager searchResponseCreatorManager) {
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(
                new SearchResponseCreatorCache.Key(searchRequest));

        SearchResponse response = searchResponseCreator.create(searchRequest);
        try {
            while (!response.complete()) {
                response = searchResponseCreator.create(searchRequest);
            }
        } finally {
            searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(searchRequest.getKey()));
        }

        return response;
    }

    public void testInteractive(
            final ExpressionOperator.Builder expressionIn,
            final int expectResultCount,
            final List<String> componentIds,
            final Function<Boolean, TableSettings> tableSettingsCreator,
            final boolean extractValues,
            final Consumer<Map<String, List<Row>>> resultMapConsumer,
            final int maxShardTasks,
            final int maxExtractionTasks,
            final IndexService indexService) {
        testInteractive(expressionIn, expectResultCount, componentIds, tableSettingsCreator,
                extractValues, resultMapConsumer, maxShardTasks,
                maxExtractionTasks, indexService, searchResponseCreatorManager);
    }

    public static void testInteractive(
            final ExpressionOperator.Builder expressionIn,
            final int expectResultCount,
            final List<String> componentIds,
            final Function<Boolean, TableSettings> tableSettingsCreator,
            final boolean extractValues,
            final Consumer<Map<String, List<Row>>> resultMapConsumer,
            final int maxShardTasks,
            final int maxExtractionTasks,
            final IndexService indexService,
            final SearchResponseCreatorManager searchResponseCreatorManager) {

        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty(
                "stroom.search.shard.concurrentTasks",
                Integer.toString(maxShardTasks),
                StroomProperties.Source.TEST);

        StroomProperties.setOverrideProperty(
                "stroom.search.extraction.concurrentTasks",
                Integer.toString(maxExtractionTasks),
                StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRefUtil.create(index);

        final List<ResultRequest> resultRequests = new ArrayList<>(componentIds.size());

        for (final String componentId : componentIds) {
            final TableSettings tableSettings = tableSettingsCreator.apply(extractValues);

            final ResultRequest tableResultRequest = new ResultRequest(componentId, Collections.singletonList(tableSettings), null, null, ResultRequest.ResultStyle.TABLE, Fetch.CHANGES);
            resultRequests.add(tableResultRequest);
        }

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final Query query = new Query(dataSourceRef, expressionIn.build());
        final SearchRequest searchRequest = new SearchRequest(queryKey, query, resultRequests, ZoneOffset.UTC.getId(), false);
        final SearchResponse searchResponse = AbstractSearchTest.search(searchRequest, searchResponseCreatorManager);

        final Map<String, List<Row>> rows = new HashMap<>();
        if (searchResponse != null && searchResponse.getResults() != null) {
            for (final Result result : searchResponse.getResults()) {
                final String componentId = result.getComponentId();
                final TableResult tableResult = (TableResult) result;

                if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                    final stroom.query.api.v2.OffsetRange range = tableResult.getResultRange();

                    for (long i = range.getOffset(); i < range.getLength(); i++) {
                        final List<Row> values = rows.computeIfAbsent(componentId, k -> new ArrayList<>());
                        values.add(tableResult.getRows().get((int) i));
                    }
                }
            }
        }

        if (expectResultCount == 0) {
            Assert.assertEquals(0, rows.size());
        } else {
            Assert.assertEquals(componentIds.size(), rows.size());
        }
        resultMapConsumer.accept(rows);
    }
}
