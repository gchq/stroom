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


import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractSearchTest2 extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSearchTest2.class);

    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private ResultStoreManager searchResponseCreatorManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;

    protected static SearchResponse search(final SearchRequest searchRequest,
                                           final ResultStoreManager searchResponseCreatorManager) {
        final SearchResponse response = searchResponseCreatorManager.search(searchRequest);
        if (!response.complete()) {
            throw new RuntimeException("NOT COMPLETE");
        }
        searchResponseCreatorManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);

        return response;
    }

    public void testInteractive(
            final String queryString,
            final int expectResultCount,
            final List<String> componentIds,
            final Function<Boolean, TableSettings> tableSettingsCreator,
            final boolean extractValues,
            final Consumer<Map<String, List<Row>>> resultMapConsumer,
            final IndexStore indexStore,
            final ResultStoreManager searchResponseCreatorManager) {

        final DocRef indexRef = indexStore.list().get(0);
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);
        assertThat(index).as("Index is null").isNotNull();

//        final List<ResultRequest> resultRequests = new ArrayList<>(componentIds.size());
//
//        for (final String componentId : componentIds) {
//            final TableSettings tableSettings = tableSettingsCreator.apply(extractValues);
//
//            final ResultRequest tableResultRequest = new ResultRequest(componentId,
//                    Collections.singletonList(tableSettings),
//                    null,
//                    null,
//                    ResultRequest.ResultStyle.TABLE,
//                    Fetch.CHANGES);
//            resultRequests.add(tableResultRequest);
//        }
//
//        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
//        final Query query = Query.builder().dataSource(indexRef).build();
        SearchRequest searchRequest = new SearchRequest(
                null,
                null,
                null,
                null,
                DateTimeSettings.builder().build(),
                false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(searchRequest);
        searchRequest = searchRequestFactory.create(queryString, searchRequest, expressionContext);

        // Add extraction pipeline.
        // TODO : @66 REPLACE WITH VIEW BASED EXTRACTION
        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultPipeline();
        ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
        TableSettings tableSettings = resultRequest.getMappings().get(0);
        tableSettings = tableSettings.copy().extractionPipeline(resultPipeline).build();
        resultRequest = resultRequest.copy().mappings(Collections.singletonList(tableSettings)).build();
        searchRequest = searchRequest.copy().resultRequests(Collections.singletonList(resultRequest)).build();

        try {
            final String json = JsonUtil.writeValueAsString(searchRequest);
            LOGGER.info(json);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        final SearchResponse searchResponse = AbstractSearchTest2
                .search(searchRequest, searchResponseCreatorManager);

        assertThat(searchResponse).as("Search response is null").isNotNull();
        if (searchResponse.getErrors() != null && searchResponse.getErrors().size() > 0) {
            final String errors = String.join(", ", searchResponse.getErrors());
            assertThat(errors).as("Found errors: " + errors).isBlank();
        }
        assertThat(searchResponse.complete()).as("Search is not complete").isTrue();
        assertThat(searchResponse.getResults()).as("Search response has null results").isNotNull();

        final Map<String, List<Row>> rows = new HashMap<>();
        for (final Result result : searchResponse.getResults()) {
            final String componentId = result.getComponentId();
            final TableResult tableResult = (TableResult) result;

            if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                final stroom.query.api.OffsetRange range = tableResult.getResultRange();

                for (long i = range.getOffset(); i < range.getLength(); i++) {
                    final List<Row> values = rows.computeIfAbsent(componentId, k -> new ArrayList<>());
                    values.add(tableResult.getRows().get((int) i));
                }
            }
        }

        if (expectResultCount == 0) {
            assertThat(rows).isEmpty();
        } else {
            assertThat(rows).hasSize(componentIds.size());

            final int count = rows.values().iterator().next().size();
            assertThat(count).as("Incorrect number of results found").isEqualTo(expectResultCount);
        }
        resultMapConsumer.accept(rows);
    }

    protected SearchResponse search(final SearchRequest searchRequest) {
        return search(searchRequest, searchResponseCreatorManager);
    }

    public void testInteractive(
            final String queryString,
            final int expectResultCount,
            final List<String> componentIds,
            final Function<Boolean, TableSettings> tableSettingsCreator,
            final boolean extractValues,
            final Consumer<Map<String, List<Row>>> resultMapConsumer,
            final IndexStore indexStore) {
        testInteractive(queryString, expectResultCount, componentIds, tableSettingsCreator,
                extractValues, resultMapConsumer, indexStore, searchResponseCreatorManager);
    }
}
