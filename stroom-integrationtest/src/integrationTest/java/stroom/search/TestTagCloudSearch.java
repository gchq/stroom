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

package stroom.search;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.DocRefUtil;
import stroom.index.IndexStore;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.IndexDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.shared.v2.ParamUtil;

import javax.inject.Inject;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TestTagCloudSearch extends AbstractSearchTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTagCloudSearch.class);
    private static boolean doneSetup;
    @Inject
    private CommonIndexingTest commonIndexingTest;
    @Inject
    private IndexStore indexStore;

    @Override
    public void onBefore() {
        if (!doneSetup) {
            doneSetup = true;
            commonIndexingTest.setup();
        }
    }

    @Test
    public void test() {
        final String componentId = "table-1";

        final DocRef indexRef = indexStore.list().get(0);
        final IndexDoc index = indexStore.readDocument(indexRef);
        Assert.assertNotNull("Index is null", index);

        // Create text field.
        final Field fldText = new Field.Builder()
                .name("Text")
                .expression(ParamUtil.makeParam("Text"))
                .group(0)
                .format(Format.Type.TEXT)
                .build();

        // Create count field.
        final Field fldCount = new Field.Builder()
                .name("Count")
                .expression("count()")
                .format(Format.Type.NUMBER)
                .build();

        final DocRef resultPipeline = commonIndexingTest.getSearchResultTextPipeline();
        final TableSettings tableSettings = new TableSettings(null, Arrays.asList(fldText, fldCount), true, resultPipeline, null, null);

        final ExpressionOperator.Builder expression = buildExpression("user5", "2000-01-01T00:00:00.000Z", "2016-01-02T00:00:00.000Z");
        final Query query = new Query(indexRef, expression.build());

        final ResultRequest tableResultRequest = new ResultRequest(componentId, Collections.singletonList(tableSettings), null, null, ResultRequest.ResultStyle.TABLE, Fetch.CHANGES);

        final List<ResultRequest> resultRequests = Collections.singletonList(tableResultRequest);

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
//        final Query query = new Query(dataSourceRef, expression);
        final SearchRequest searchRequest = new SearchRequest(queryKey, query, resultRequests, ZoneOffset.UTC.getId(), false);
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
        Assert.assertEquals("Incorrect number of hits found", 8, values.size());
        final Row firstResult = values.get(0);
        Assert.assertNotNull(firstResult);
        final String text = firstResult.getValues().get(0);
        Assert.assertNotNull("Incorrect heading", text);

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
        Assert.assertTrue("Unable to find expected value", found);
        Assert.assertEquals("Value does not have expected word count", 4, count);
    }

    private ExpressionOperator.Builder buildExpression(final String user, final String from,
                                                       final String to) {
        final ExpressionOperator.Builder operator = new ExpressionOperator.Builder();
        operator.addTerm("UserId", Condition.CONTAINS, user);
        operator.addTerm("EventTime", Condition.BETWEEN, from + "," + to);

        return operator;
    }
}