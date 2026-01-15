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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTagCloudSearch extends AbstractSearchTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTagCloudSearch.class);

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

    @Test
    void test() {
        final String componentId = "table-1";

        final DocRef indexRef = indexStore.list().get(0);
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);
        assertThat(index).as("Index is null").isNotNull();

        // Create text column.
        final Column columnText = Column.builder()
                .id("Text")
                .name("Text")
                .expression(ParamUtil.create("Text"))
                .group(0)
                .format(Format.TEXT)
                .build();

        // Create count column.
        final Column columnCount = Column.builder()
                .id("Count")
                .name("Count")
                .expression("count()")
                .format(Format.NUMBER)
                .build();

        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultTextPipeline();
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(columnText)
                .addColumns(columnCount)
                .extractValues(true)
                .extractionPipeline(resultPipeline)
                .build();

        final ExpressionOperator.Builder expression = buildExpression("user5",
                "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z");
        final Query query = Query.builder().dataSource(indexRef).expression(expression.build()).build();

        final ResultRequest tableResultRequest = new ResultRequest(componentId, null,
                Collections.singletonList(tableSettings),
                null,
                null,
                null,
                ResultRequest.ResultStyle.TABLE,
                Fetch.CHANGES,
                null,
                null);

        final List<ResultRequest> resultRequests = Collections.singletonList(tableResultRequest);

        final SearchRequest searchRequest = new SearchRequest(
                null,
                null,
                query,
                resultRequests,
                DateTimeSettings.builder().build(),
                false);
        final SearchResponse searchResponse = search(searchRequest);

        final List<Row> values = new ArrayList<>();
        if (searchResponse != null) {
            final TableResult tableResult = (TableResult) searchResponse.getResults().get(0);
            if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                final OffsetRange range = tableResult.getResultRange();

                for (long i = range.getOffset(); i < range.getLength(); i++) {
                    values.add(tableResult.getRows().get((int) i));
                }
            }
        }

        // Output the values.
        LOGGER.info("Values found:\n" + values);

        // Make sure we got what we expected.
        assertThat(values.size()).as("Incorrect number of hits found").isEqualTo(8);
        final Row firstResult = values.get(0);
        assertThat(firstResult).isNotNull();
        final String text = firstResult.getValues().get(0);
        assertThat(text).as("Incorrect heading").isNotNull();

        // Make sure we got what we expected.
        boolean found = false;
        int count = -1;
        for (final Row row : values) {
            final String field1 = row.getValues().get(0);
            final String field2 = row.getValues().get(1);

            if ("msg=foo bar".equals(field1)) {
                found = true;
                count = Integer.parseInt(field2);
            }
        }
        assertThat(found).as("Unable to find expected value").isTrue();
        assertThat(count).as("Value does not have expected word count").isEqualTo(4);
    }

    private ExpressionOperator.Builder buildExpression(final String user, final String from,
                                                       final String to) {
        final ExpressionOperator.Builder operator = ExpressionOperator.builder();
        operator.addTerm("UserId", Condition.EQUALS, user);
        operator.addTerm("EventTime", Condition.BETWEEN, from + "," + to);

        return operator;
    }
}
