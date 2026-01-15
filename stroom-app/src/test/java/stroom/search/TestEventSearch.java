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
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventSearch extends AbstractSearchTest {

    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private IndexStore indexStore;

    private boolean doneSetup;

    @BeforeEach
    void setup() {
        if (!doneSetup) {
            commonIndexingTestHelper.setup();
            doneSetup = true;
        }
    }

    /**
     * Positive case insensitive test.
     */
    @Test
    void positiveCaseInsensitiveTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 5);
    }

    private void test(final ExpressionOperator.Builder expressionIn, final int expectResultCount) {
        final List<String> componentIds = Collections.singletonList("table-1");
        test(expressionIn, expectResultCount, componentIds, true);
    }

    private void test(final ExpressionOperator.Builder expressionIn,
                      final int expectResultCount,
                      final List<String> componentIds,
                      final boolean extractValues) {
//        // ADDED THIS SECTION TO TEST GUICE VALUE INJECTION.
//        StroomProperties.setOverrideProperty(
//        "stroom.search.impl.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
//        StroomProperties.setOverrideProperty(
//        "stroom.search.impl.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final DocRef indexRef = indexStore.list().get(0);
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);
        assertThat(index).as("Index is null").isNotNull();

        final List<ResultRequest> resultRequests = new ArrayList<>(componentIds.size());

        for (final String componentId : componentIds) {
            final TableSettings tableSettings = createTableSettings(index, extractValues);

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
        final SearchResponse searchResponse = search(searchRequest);

        final Map<String, List<Row>> rows = new HashMap<>();
        if (searchResponse != null) {
            for (final Result result : searchResponse.getResults()) {
                final String componentId = result.getComponentId();
                final TableResult tableResult = (TableResult) result;

                if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                    final OffsetRange range = tableResult.getResultRange();

                    for (long i = range.getOffset(); i < range.getLength(); i++) {
                        List<Row> values = rows.get(componentId);
                        if (values == null) {
                            values = new ArrayList<>();
                            rows.put(componentId, values);
                        }
                        values.add(tableResult.getRows().get((int) i));
                    }
                }
            }
        }

        // // Output the hits.
        // LOGGER.info("Hits found:");
        // for (final TableRow hit : values) {
        // LOGGER.info("\t" + hit);
        // }

        if (expectResultCount == 0) {
            assertThat(rows.size()).isEqualTo(0);
        } else {
            assertThat(rows).hasSize(componentIds.size());
        }

        for (final List<Row> values : rows.values()) {
            if (expectResultCount == 0) {
                assertThat(values.size()).isEqualTo(0);

            } else {
                // Make sure we got what we expected.
                Row firstResult = null;
                if (values != null && values.size() > 0) {
                    firstResult = values.get(0);
                }
                assertThat(firstResult).as("No results found").isNotNull();

                if (extractValues) {
                    final String time = firstResult.getValues().get(1);
                    assertThat(time).as("Incorrect heading").isNotNull();
                    assertThat(values.size()).as("Incorrect number of hits found")
                            .isEqualTo(expectResultCount);
                    boolean found = false;
                    for (final Row hit : values) {
                        final String str = hit.getValues().get(1);
                        if ("2007-03-18T14:34:41.000Z".equals(str)) {
                            found = true;
                        }
                    }
                    assertThat(found).as("Unable to find expected hit").isTrue();
                }
            }
        }
    }

    private TableSettings createTableSettings(final LuceneIndexDoc index, final boolean extractValues) {
        final Column idColumn = Column.builder()
                .id("1")
                .name("IdTreeNode")
                .expression(ParamUtil.create("StreamId"))
                .build();

        final Column timeColumn = Column.builder()
                .id("2")
                .name("Event Time")
                .expression(ParamUtil.create("EventTime"))
                .format(Format.DATE_TIME)
                .build();

        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultPipeline();
        return TableSettings.builder()
                .addColumns(idColumn)
                .addColumns(timeColumn)
                .extractValues(extractValues)
                .extractionPipeline(resultPipeline)
                .build();
    }

    private ExpressionOperator.Builder buildExpression(final String userField,
                                                       final String userTerm,
                                                       final String from,
                                                       final String to,
                                                       final String wordsField,
                                                       final String wordsTerm) {
        return ExpressionOperator.builder()
                .addTerm(userField, Condition.EQUALS, userTerm)
                .addTerm("EventTime", Condition.BETWEEN, from + "," + to)
                .addTerm(wordsField, Condition.EQUALS, wordsTerm);
    }
}
