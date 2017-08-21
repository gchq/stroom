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
import org.junit.Test;
import stroom.entity.shared.DocRefUtil;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionBuilder;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.FieldBuilder;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
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
import stroom.query.common.v2.ParamUtil;
import stroom.util.config.StroomProperties;

import javax.annotation.Resource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestEventSearch extends AbstractSearchTest {
    private static boolean doneSetup;
    @Resource
    private CommonIndexingTest commonIndexingTest;
    @Resource
    private IndexService indexService;

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
        final List<String> componentIds = Collections.singletonList("table-1");
        test(expressionIn, expectResultCount, componentIds, true);
    }

    private void test(final ExpressionBuilder expressionIn, final int expectResultCount, final List<String> componentIds,
                      final boolean extractValues) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRefUtil.create(index);

        final List<ResultRequest> resultRequests = new ArrayList<>(componentIds.size());

        for (final String componentId : componentIds) {
            final TableSettings tableSettings = createTableSettings(index, extractValues);

            final ResultRequest tableResultRequest = new ResultRequest(componentId, Collections.singletonList(tableSettings), null, null, ResultRequest.ResultStyle.TABLE, Fetch.CHANGES);
            resultRequests.add(tableResultRequest);
        }

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final Query query = new Query(dataSourceRef, expressionIn.build());
        final SearchRequest searchRequest = new SearchRequest(queryKey, query, resultRequests, ZoneOffset.UTC.getId(), false);
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
            Assert.assertEquals(0, rows.size());
        } else {
            Assert.assertEquals(componentIds.size(), rows.size());
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
                    final String time = firstResult.getValues().get(1);
                    Assert.assertNotNull("Incorrect heading", time);
                    Assert.assertEquals("Incorrect number of hits found", expectResultCount, values.size());
                    boolean found = false;
                    for (final Row hit : values) {
                        final String str = hit.getValues().get(1);
                        if ("2007-03-18T14:34:41.000Z".equals(str)) {
                            found = true;
                        }
                    }
                    Assert.assertTrue("Unable to find expected hit", found);
                }
            }
        }
    }

    private TableSettings createTableSettings(final Index index, final boolean extractValues) {
        final Field idField = new FieldBuilder()
                .name("Id")
                .expression(ParamUtil.makeParam("StreamId"))
                .build();

        final Field timeField = new FieldBuilder()
                .name("Event Time")
                .expression(ParamUtil.makeParam("EventTime"))
                .format(Format.Type.DATE_TIME)
                .build();

        final PipelineEntity resultPipeline = commonIndexingTest.getSearchResultPipeline();
        return new TableSettings(null, Arrays.asList(idField, timeField), extractValues, DocRefUtil.create(resultPipeline), null, null);
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
