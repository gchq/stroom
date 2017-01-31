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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Field;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableResultRequest;
import stroom.query.api.TableSettings;
import stroom.search.server.SearchResource;
import stroom.util.config.StroomProperties;
import stroom.util.shared.ParamUtil;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestEventSearch extends AbstractCoreIntegrationTest {
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

    /**
     * Positive case insensitive test.
     */
    @Test
    public void positiveCaseInsensitiveTest() {
        final ExpressionBuilder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 5);
    }

    private void test(final ExpressionBuilder expressionIn, final int expectResultCount) {
        final String[] compoentIds = new String[]{"table-1"};
        test(expressionIn, expectResultCount, compoentIds, true);
    }

    private void test(final ExpressionBuilder expressionIn, final int expectResultCount, final String[] componentIds,
                      final boolean extractValues) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRefUtil.create(index);

        final ResultRequest[] resultRequests = new ResultRequest[componentIds.length];

        for (int i = 0; i < componentIds.length; i++) {
            final String componentId = componentIds[i];

            final TableSettings tableSettings = createTableSettings(index);
            tableSettings.setExtractValues(extractValues);

            final TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setComponentId(componentId);
            tableResultRequest.setTableSettings(tableSettings);
            tableResultRequest.setFetchData(true);
            resultRequests[i] = tableResultRequest;
        }

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final Query query = new Query(dataSourceRef, expressionIn.build());
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
                        values.add(tableResult.getRows()[(int) i]);
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
            Assert.assertEquals(0, rows.size());
        } else {
            Assert.assertEquals(componentIds.length, rows.size());
        }

        for (final List<Row> values : rows.values()) {
            if (expectResultCount == 0) {
                Assert.assertEquals(0, values.size());

            } else {
                // Make sure we got what we expected.
                Row firstResult = null;
                if (values != null && values.size() > 0) {
                    firstResult = values.get(0);
                }
                Assert.assertNotNull("No results found", firstResult);

                if (extractValues) {
                    final String time = firstResult.getValues()[1];
                    Assert.assertNotNull("Incorrect heading", time);
                    Assert.assertEquals("Incorrect number of hits found", expectResultCount, values.size());
                    boolean found = false;
                    for (final Row hit : values) {
                        final String str = hit.getValues()[1];
                        if ("2007-03-18T14:34:41.000Z".equals(str)) {
                            found = true;
                        }
                    }
                    Assert.assertTrue("Unable to find expected hit", found);
                }
            }
        }
    }

    private TableSettings createTableSettings(final Index index) {
        final TableSettings tableSettings = new TableSettings();

        final Field idField = new Field("Id");
        idField.setExpression(ParamUtil.makeParam("StreamId"));
        tableSettings.addField(idField);

        final Field timeField = new Field("Event Time");
        timeField.setExpression(ParamUtil.makeParam("EventTime"));
        timeField.setFormat(new Format(Format.Type.DATE_TIME));
        tableSettings.addField(timeField);

        final PipelineEntity resultPipeline = commonIndexingTest.getSearchResultPipeline();
        tableSettings.setExtractionPipeline(DocRefUtil.create(resultPipeline));

        return tableSettings;
    }

    private ExpressionBuilder buildExpression(final String userField, final String userTerm, final String from,
                                               final String to, final String wordsField, final String wordsTerm) {
        final ExpressionBuilder operator = new ExpressionBuilder();
        operator.addTerm(userField, Condition.CONTAINS, userTerm);
        operator.addTerm("EventTime", Condition.BETWEEN, from + "," + to);
        operator.addTerm(wordsField, Condition.CONTAINS, wordsTerm);

        return operator;
    }
}
