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
import stroom.dashboard.server.QueryMarshaller;
import stroom.dashboard.server.SearchDataSourceProviderRegistry;
import stroom.dashboard.server.SearchResultCreator;
import stroom.dashboard.shared.*;
import stroom.entity.shared.DocRef;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.SearchDataSourceProvider;
import stroom.query.SearchResultCollector;
import stroom.query.shared.*;
import stroom.search.server.LuceneSearchDataSourceProvider;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedObject;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class TestTagCloudSearch extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestTagCloudSearch.class);
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

    @Test
    public void test() throws Exception {
        final String componentId = "table-1";

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRef.create(index);

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
        tableSettings.setExtractionPipeline(DocRef.create(resultPipeline));

        final Query query = buildQuery(dataSourceRef, "user5", "2000-01-01T00:00:00.000Z", "2016-01-02T00:00:00.000Z");
        final QueryData searchData = query.getQueryData();
        final ExpressionOperator expression = searchData.getExpression();

        final Map<String, ComponentSettings> resultComponentMap = new HashMap<String, ComponentSettings>();
        resultComponentMap.put(componentId, tableSettings);

        final TableResultRequest tableResultRequest = new TableResultRequest();
        tableResultRequest.setTableSettings(tableSettings);
        tableResultRequest.setWantsData(true);
        final Map<String, ComponentResultRequest> componentResultRequests = new HashMap<String, ComponentResultRequest>();
        componentResultRequests.put(componentId, tableResultRequest);

        SearchResult result = null;
        boolean complete = false;
        final Map<String, SharedObject> results = new HashMap<>();

        final Search search = new Search(searchData.getDataSource(), expression, resultComponentMap, true);
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

        final List<Row> values = new ArrayList<>();
        if (result != null) {
            final TableResult tableResult = (TableResult) results.get(componentId);
            if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                final OffsetRange<Integer> range = tableResult.getResultRange();

                for (int i = range.getOffset(); i < range.getLength(); i++) {
                    values.add(tableResult.getRows().get(i));
                }
            }
            complete = result.isComplete();
        }

        // Output the values.
        LOGGER.info("Values found:\n" + values);

        // Make sure we got what we expected.
        Assert.assertEquals("Incorrect number of hits found", 8, values.size());
        final Row firstResult = values.get(0);
        Assert.assertNotNull(firstResult);
        final SharedObject text = firstResult.getValues()[0];
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

    private Query buildQuery(final DocRef dataSourceRef, final String user, final String from,
            final String to) {
        Query query = new Query();

        final ExpressionTerm userId = new ExpressionTerm();
        userId.setField("UserId");
        userId.setCondition(Condition.CONTAINS);
        userId.setValue(user);

        final ExpressionTerm eventTime = new ExpressionTerm();
        eventTime.setField("EventTime");
        eventTime.setCondition(Condition.BETWEEN);
        eventTime.setValue(from + "," + to);

        final ExpressionOperator operator = new ExpressionOperator();
        operator.addChild(userId);
        operator.addChild(eventTime);

        final QueryData queryData = new QueryData();
        queryData.setExpression(operator);
        queryData.setDataSource(dataSourceRef);
        query.setQueryData(queryData);
        query = queryMarshaller.marshal(query);

        return query;
    }
}
