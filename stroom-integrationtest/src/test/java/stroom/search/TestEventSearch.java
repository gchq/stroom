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
import stroom.dashboard.server.ActiveQuery;
import stroom.dashboard.server.QueryEntityMarshaller;
import stroom.dashboard.server.SearchDataSourceProviderRegistry;
import stroom.dashboard.server.SearchResultCreator;
import stroom.dashboard.shared.BasicQueryKey;
import stroom.dashboard.shared.ComponentResult;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Format;
import stroom.util.shared.ParamUtil;
import stroom.dashboard.shared.QueryEntity;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.entity.shared.DocRefUtil;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.SearchDataSourceProvider;
import stroom.query.SearchResultCollector;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.search.server.LuceneSearchDataSourceProvider;
import stroom.task.server.TaskManager;
import stroom.util.config.StroomProperties;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedObject;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class TestEventSearch extends AbstractCoreIntegrationTest {
    private static boolean doneSetup;
    @Resource
    private CommonIndexingTest commonIndexingTest;
    @Resource
    private IndexService indexService;
    @Resource
    private QueryEntityMarshaller queryEntityMarshaller;
    @Resource
    private SearchDataSourceProviderRegistry searchDataSourceProviderRegistry;
    @Resource
    private TaskManager taskManager;
    @Resource
    private SearchResultCreator searchResultCreator;

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
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 5);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount) {
        final String[] compoentIds = new String[]{"table-1"};
        test(expressionIn, expectResultCount, compoentIds, true);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount, final String[] compoentIds,
                      final boolean extractValues) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRefUtil.create(index);

        final QueryEntity queryEntity = buildQuery(dataSourceRef, expressionIn);
        final Query query = queryEntity.getQuery();
        final ExpressionOperator expression = query.getExpression();

        final Map<String, ComponentSettings> resultComponentMap = new HashMap<>();
        final Map<String, ComponentResultRequest> componentResultRequests = new HashMap<>();
        for (final String componentId : compoentIds) {
            final TableComponentSettings tableSettings = createTableSettings(index);
            tableSettings.setExtractValues(extractValues);
            resultComponentMap.put(componentId, tableSettings);

            final TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings(tableSettings);
            tableResultRequest.setWantsData(true);
            componentResultRequests.put(componentId, tableResultRequest);
        }

        SearchResponse result = null;
        boolean complete = false;
        final Map<String, ComponentResult> results = new HashMap<>();

        final Search search = new Search(query.getDataSource(), expression, resultComponentMap);
        final SearchRequest searchRequest = new SearchRequest(search, componentResultRequests,
                DateTimeZone.UTC.getID());

        final SearchDataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                .getProvider(LuceneSearchDataSourceProvider.ENTITY_TYPE);
        final SearchResultCollector searchResultCollector = dataSourceProvider.createCollector(null, null,
                new BasicQueryKey(queryEntity.getName()), searchRequest);
        final ActiveQuery activeQuery = new ActiveQuery(searchResultCollector);

        // Start asynchronous search execution.
        searchResultCollector.start();

        try {
            while (!complete) {
                result = searchResultCreator.createResult(activeQuery, searchRequest);
                // We need to remember results when they are returned as search
                // will no longer return duplicate results to prevent us
                // overwhelming the UI and transferring unnecessary data to the
                // client.
                if (result.getResults() != null) {
                    for (final Entry<String, ComponentResult> entry : result.getResults().entrySet()) {
                        if (entry.getValue() != null) {
                            results.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                complete = result.isComplete();

                if (!complete) {
                    ThreadUtil.sleep(1000);
                }
            }
        } finally {
            searchResultCollector.destroy();
        }

        final Map<String, List<Row>> rows = new HashMap<>();
        if (result != null) {
            for (final Entry<String, ComponentResult> entry : results.entrySet()) {
                final String componentId = entry.getKey();
                final TableResult tableResult = (TableResult) entry.getValue();

                if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                    final OffsetRange<Integer> range = tableResult.getResultRange();

                    for (int i = range.getOffset(); i < range.getLength(); i++) {
                        List<Row> values = rows.get(componentId);
                        if (values == null) {
                            values = new ArrayList<>();
                            rows.put(componentId, values);
                        }
                        values.add(tableResult.getRows().get(i));
                    }
                }
            }
            complete = result.isComplete();
        }

        // // Output the hits.
        // LOGGER.info("Hits found:");
        // for (final TableRow hit : values) {
        // LOGGER.info("\t" + hit);
        // }

        if (expectResultCount == 0) {
            Assert.assertEquals(0, rows.size());
        } else {
            Assert.assertEquals(compoentIds.length, rows.size());
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
                    final SharedObject time = firstResult.getValues()[1];
                    Assert.assertNotNull("Incorrect heading", time);
                    Assert.assertEquals("Incorrect number of hits found", expectResultCount, values.size());
                    boolean found = false;
                    for (final Row hit : values) {
                        final SharedObject obj = hit.getValues()[1];
                        final String str = obj.toString();
                        if ("2007-03-18T14:34:41.000Z".equals(str)) {
                            found = true;
                        }
                    }
                    Assert.assertTrue("Unable to find expected hit", found);
                }
            }
        }
    }

    private TableComponentSettings createTableSettings(final Index index) {
        final TableComponentSettings tableSettings = new TableComponentSettings();

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

    private QueryEntity buildQuery(final DocRef dataSourceRef, final ExpressionOperator expression) {
        final Query query = new Query(dataSourceRef, expression);
        QueryEntity queryEntity = new QueryEntity();
        queryEntity.setQuery(query);
        queryEntity = queryEntityMarshaller.marshal(queryEntity);

        return queryEntity;
    }

    private ExpressionOperator buildExpression(final String userField, final String userTerm, final String from,
                                               final String to, final String wordsField, final String wordsTerm) {
        final ExpressionTerm userId = new ExpressionTerm();
        userId.setField(userField);
        userId.setCondition(Condition.CONTAINS);
        userId.setValue(userTerm);

        final ExpressionTerm eventTime = new ExpressionTerm();
        eventTime.setField("EventTime");
        eventTime.setCondition(Condition.BETWEEN);
        eventTime.setValue(from + "," + to);

        final ExpressionTerm words = new ExpressionTerm();
        words.setField(wordsField);
        words.setCondition(Condition.CONTAINS);
        words.setValue(wordsTerm);

        final ExpressionOperator operator = new ExpressionOperator();
        operator.add(userId);
        operator.add(eventTime);
        operator.add(words);

        return operator;
    }
}
