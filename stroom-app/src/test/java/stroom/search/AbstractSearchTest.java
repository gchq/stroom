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

package stroom.search;


import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ResultStoreManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ErrorMessage;

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

public abstract class AbstractSearchTest extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSearchTest.class);

    @Inject
    private ResultStoreManager resultStoreManager;

    protected static SearchResponse search(final SearchRequest searchRequest,
                                           final ResultStoreManager resultStoreManager) {
        final SearchResponse response = resultStoreManager.search(searchRequest);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        if (!response.complete()) {
            throw new RuntimeException("NOT COMPLETE");
        }

        return response;
    }

    public static void testInteractive(
            final ExpressionOperator.Builder expressionIn,
            final int expectResultCount,
            final List<String> componentIds,
            final Function<Boolean, TableSettings> tableSettingsCreator,
            final boolean extractValues,
            final Consumer<Map<String, List<Row>>> resultMapConsumer,
            final IndexStore indexStore,
            final ResultStoreManager searchResponseCreatorManager) {

        assertThat(indexStore.list())
                .as("Check indexStore is not empty")
                .isNotEmpty();

        final DocRef indexRef = indexStore.list().get(0);
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);
        assertThat(index).as("Index is null").isNotNull();

        final List<ResultRequest> resultRequests = new ArrayList<>(componentIds.size());

        for (final String componentId : componentIds) {
            final TableSettings tableSettings = tableSettingsCreator.apply(extractValues);

            final ResultRequest tableResultRequest = new ResultRequest(componentId, null,
                    Collections.singletonList(tableSettings),
                    null,
                    null,
                    null,
                    ResultRequest.ResultStyle.TABLE,
                    Fetch.CHANGES,
                    null,
                    null);
            resultRequests.add(tableResultRequest);
        }

        final Query query = Query.builder().dataSource(indexRef).expression(expressionIn.build()).build();
        final SearchRequest searchRequest = new SearchRequest(
                null,
                null,
                query,
                resultRequests,
                DateTimeSettings.builder().build(),
                false);

        try {
            final String json = JsonUtil.writeValueAsString(searchRequest);
            LOGGER.info(json);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        final SearchResponse searchResponse = AbstractSearchTest
                .search(searchRequest, searchResponseCreatorManager);

        assertThat(searchResponse).as("Search response is null").isNotNull();
        if (searchResponse.getErrorMessages() != null && searchResponse.getErrorMessages().size() > 0) {
            final List<String> messages = searchResponse.getErrorMessages().stream()
                    .map(ErrorMessage::getMessage).toList();
            final String errors = String.join(", ", messages);
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
            assertThat(count).as("Correct number of results found").isEqualTo(expectResultCount);
        }
        resultMapConsumer.accept(rows);
    }

    protected SearchResponse search(final SearchRequest searchRequest) {
        return search(searchRequest, resultStoreManager);
    }

    public void testInteractive(
            final ExpressionOperator.Builder expressionIn,
            final int expectResultCount,
            final List<String> componentIds,
            final Function<Boolean, TableSettings> tableSettingsCreator,
            final boolean extractValues,
            final Consumer<Map<String, List<Row>>> resultMapConsumer,
            final IndexStore indexStore) {
        testInteractive(expressionIn, expectResultCount, componentIds, tableSettingsCreator,
                extractValues, resultMapConsumer, indexStore, resultStoreManager);
    }
}
