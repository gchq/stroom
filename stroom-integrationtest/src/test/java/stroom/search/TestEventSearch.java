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

import stroom.AbstractCoreIntegrationTest;
import stroom.CommonIndexingTest;
import stroom.dashboard.server.ActiveQuery;
import stroom.dashboard.server.QueryMarshaller;
import stroom.dashboard.server.SearchDataSourceProviderRegistry;
import stroom.dashboard.server.SearchResultCreator;
import stroom.dashboard.shared.BasicQueryKey;
import stroom.dashboard.shared.ParamUtil;
import stroom.dashboard.shared.Query;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.entity.shared.DocRef;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.SearchDataSourceProvider;
import stroom.query.SearchResultCollector;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.ComponentSettings;
import stroom.query.shared.Condition;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.Field;
import stroom.query.shared.Format;
import stroom.query.shared.QueryData;
import stroom.query.shared.Search;
import stroom.query.shared.SearchRequest;
import stroom.query.shared.SearchResult;
import stroom.query.shared.TableSettings;
import stroom.search.server.LuceneSearchDataSourceProvider;
import stroom.task.server.TaskManager;
import stroom.util.config.StroomProperties;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedObject;
import stroom.util.thread.ThreadUtil;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

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
    private QueryMarshaller queryMarshaller;
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
        final String[] compoentIds = new String[] { "table-1" };
        test(expressionIn, expectResultCount, compoentIds, true);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount, final String[] compoentIds,
            final boolean extractValues) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", "test");
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", "test");

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRef.create(index);

        final Query query = buildQuery(dataSourceRef, expressionIn);
        final QueryData searchData = query.getQueryData();
        final ExpressionOperator expression = searchData.getExpression();

        final Map<String, ComponentSettings> resultComponentMap = new HashMap<String, ComponentSettings>();
        final Map<String, ComponentResultRequest> componentResultRequests = new HashMap<String, ComponentResultRequest>();
        for (final String componentId : compoentIds) {
            final TableSettings tableSettings = createTableSettings(index);
            tableSettings.setExtractValues(extractValues);
            resultComponentMap.put(componentId, tableSettings);

            final TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings(tableSettings);
            tableResultRequest.setWantsData(true);
            componentResultRequests.put(componentId, tableResultRequest);
        }

        SearchResult result = null;
        boolean complete = false;
        final Map<String, SharedObject> results = new HashMap<String, SharedObject>();

        final Search search = new Search(searchData.getDataSource(), expression, resultComponentMap);
        final SearchRequest searchRequest = new SearchRequest(search, componentResultRequests,
                DateTimeZone.UTC.getID());

        final SearchDataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                .getProvider(LuceneSearchDataSourceProvider.ENTITY_TYPE);
        final SearchResultCollector searchResultCollector = dataSourceProvider.createCollector(null, null,
                new BasicQueryKey(query.getName()), searchRequest);
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
                    for (final Entry<String, SharedObject> entry : result.getResults().entrySet()) {
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

        final Map<String, List<Row>> rows = new HashMap<String, List<Row>>();
        if (result != null) {
            for (final Entry<String, SharedObject> entry : results.entrySet()) {
                final String componentId = entry.getKey();
                final TableResult tableResult = (TableResult) entry.getValue();

                if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                    final OffsetRange<Integer> range = tableResult.getResultRange();

                    for (int i = range.getOffset(); i < range.getLength(); i++) {
                        List<Row> values = rows.get(componentId);
                        if (values == null) {
                            values = new ArrayList<Row>();
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
        tableSettings.setExtractionPipeline(DocRef.create(resultPipeline));

        return tableSettings;
    }

    private Query buildQuery(final DocRef dataSourceRef, final ExpressionOperator expression) {
        final QueryData queryData = new QueryData();
        queryData.setExpression(expression);
        queryData.setDataSource(dataSourceRef);
        Query query = new Query();
        query.setQueryData(queryData);
        query = queryMarshaller.marshal(query);

        return query;
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
        operator.addChild(userId);
        operator.addChild(eventTime);
        operator.addChild(words);

        return operator;
    }
}
