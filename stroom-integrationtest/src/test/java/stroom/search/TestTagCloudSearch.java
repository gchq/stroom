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

package stroom.search;

import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonIndexingTest;
import stroom.entity.shared.DocRefUtil;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionBuilder;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Field;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.search.server.SearchResource;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ParamUtil;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestTagCloudSearch extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestTagCloudSearch.class);
    private static boolean doneSetup;
    @Resource
    private CommonIndexingTest commonIndexingTest;
    @Resource
    private IndexService indexService;
    @Resource
    private SearchResource searchService;

    @Override
    public void onBefore() {
        if (!doneSetup) {
            doneSetup = true;
            commonIndexingTest.setup();
        }
    }

    @Test
    public void test() throws Exception {
        final String componentId = "table-1";

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRefUtil.create(index);

        // Create text field.
        final Field fldText = new Field("Text");
        fldText.setExpression(ParamUtil.makeParam("Text"));
        fldText.setGroup(0);
        fldText.setFormat(new Format(Format.Type.TEXT));

        // Create count field.
        final Field fldCount = new Field("Count");
        fldCount.setExpression("count()");
        fldCount.setFormat(new Format(Format.Type.NUMBER));

        final TableSettings tableSettings = new TableSettings();
        tableSettings.addField(fldText);
        tableSettings.addField(fldCount);

        final PipelineEntity resultPipeline = commonIndexingTest.getSearchResultTextPipeline();
        tableSettings.setExtractionPipeline(DocRefUtil.create(resultPipeline));

        final ExpressionBuilder expression = buildExpression("user5", "2000-01-01T00:00:00.000Z", "2016-01-02T00:00:00.000Z");
        final Query query = new Query(dataSourceRef, expression.build());

        final ResultRequest tableResultRequest = new ResultRequest(componentId, tableSettings);

        final ResultRequest[] resultRequests = new ResultRequest[1];
        resultRequests[0] = tableResultRequest;

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
//        final Query query = new Query(dataSourceRef, expression);
        final SearchRequest searchRequest = new SearchRequest(queryKey, query, resultRequests, DateTimeZone.UTC.getID());

        SearchResponse searchResponse = searchService.search(searchRequest);

        try {
            while (!searchResponse.complete()) {
                searchResponse = searchService.search(searchRequest);

                if (!searchResponse.complete()) {
                    ThreadUtil.sleep(1000);
                }
            }
        } finally {
            searchService.destroy(queryKey);
        }

        final List<Row> values = new ArrayList<>();
        if (searchResponse != null) {
            final TableResult tableResult = (TableResult) searchResponse.getResults()[0];
            if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                final OffsetRange range = tableResult.getResultRange();

                for (long i = range.getOffset(); i < range.getLength(); i++) {
                    values.add(tableResult.getRows()[(int) i]);
                }
            }
        }

        // Output the values.
        LOGGER.info("Values found:\n" + values);

        // Make sure we got what we expected.
        Assert.assertEquals("Incorrect number of hits found", 8, values.size());
        final Row firstResult = values.get(0);
        Assert.assertNotNull(firstResult);
        final String text = firstResult.getValues()[0];
        Assert.assertNotNull("Incorrect heading", text);

        // Make sure we got what we expected.
        boolean found = false;
        int count = -1;
        for (final Row row : values) {
            final String field1 = row.getValues()[0].toString();
            final String field2 = row.getValues()[1].toString();

            if ("msg=foo bar".equals(field1.toString())) {
                found = true;
                count = Integer.parseInt(field2);
            }
        }
        Assert.assertTrue("Unable to find expected value", found);
        Assert.assertEquals("Value does not have expected word count", 4, count);
    }

    private ExpressionBuilder buildExpression(final String user, final String from,
                                              final String to) {
        final ExpressionBuilder operator = new ExpressionBuilder();
        operator.addTerm("UserId", Condition.CONTAINS, user);
        operator.addTerm("EventTime", Condition.BETWEEN, from + "," + to);

        return operator;
    }
}