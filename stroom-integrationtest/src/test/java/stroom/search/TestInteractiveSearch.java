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
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.shared.DocRefUtil;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Field;
import stroom.query.api.Format;
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
import stroom.search.server.EventRef;
import stroom.search.server.EventRefs;
import stroom.search.server.EventSearchTask;
import stroom.search.server.SearchService;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.util.config.StroomProperties;
import stroom.util.shared.ParamUtil;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TestInteractiveSearch extends AbstractCoreIntegrationTest {
    @Resource
    private CommonIndexingTest commonIndexingTest;
    @Resource
    private IndexService indexService;
    @Resource
    private DictionaryService dictionaryService;
    @Resource
    private TaskManager taskManager;
    @Resource
    private SearchService searchService;

    @Override
    protected boolean doSingleSetup() {
        commonIndexingTest.setup();
        return true;
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

    @Test
    public void positiveCaseInsensitiveTestMultiComponent() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        final String[] compoentIds = new String[]{"table-1", "table-2"};
        test(expression, 5, compoentIds, true);
    }

    /**
     * Positive case insensitive test.
     */
    @Test
    public void positiveCaseInsensitiveTestWithoutExtraction() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        final String[] compoentIds = new String[]{"table-1"};
        test(expression, 5, compoentIds, false);
    }

    /**
     * Positive case insensitive test with wildcard.
     */
    @Test
    public void positiveCaseInsensitiveTest2() {
        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 25);
    }

    /**
     * Negative test for case sensitive field.
     */
    @Test
    public void negativeCaseSensitiveTest() {
        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "e0567");
        test(expression, 0);
    }

    /**
     * Negative test for case sensitive field.
     */
    @Test
    public void negativeCaseSensitiveTest2() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "e0567");
        test(expression, 0);
    }

    /**
     * Positive test case sensitive field.
     */
    @Test
    public void positiveCaseSensitiveTest() {
        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 25);
    }

    /**
     * Test case sensitive field plus other field.
     */
    @Test
    public void positiveCaseSensitiveTest2() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 5);
    }

    /**
     * Test case sensitive field plus other field.
     */
    @Test
    public void positiveCaseSensitiveTest3() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 5);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg");
        test(expression, 4);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTestWithLeadingWildcard() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "*msg");
        test(expression, 4);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void negativeAnalysedFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg foobar");
        test(expression, 0);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTestWithIn() {
        final ExpressionOperator expression = buildInExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg foo bar");
        test(expression, 4);
    }

    /**
     * Negative test on keyword field.
     */
    @Test
    public void negativeKeywordFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "foo");
        test(expression, 0);
    }

    /**
     * Positive test on keyword field.
     */
    @Test
    public void positiveKeywordFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "msg=foo bar");
        test(expression, 4);
    }

    /**
     * Positive test on keyword field.
     */
    @Test
    public void positiveKeywordFieldTestWithLeadingWildcard() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "*foo bar");
        test(expression, 4);
    }

    /**
     * Test not equals.
     */
    @Test
    public void notEqualsTest() {
        final ExpressionTerm eventTime = new ExpressionTerm();
        eventTime.setField("EventTime");
        eventTime.setCondition(Condition.EQUALS);
        eventTime.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.add(eventTime);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.add(not);
        test(expression, 24);
    }

    /**
     * Test exclusion of multiple items.
     */
    @Test
    public void notEqualsTest2() {
        final ExpressionTerm eventTime1 = new ExpressionTerm();
        eventTime1.setField("EventTime");
        eventTime1.setCondition(Condition.EQUALS);
        eventTime1.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionTerm eventTime2 = new ExpressionTerm();
        eventTime2.setField("EventTime");
        eventTime2.setCondition(Condition.EQUALS);
        eventTime2.setValue("2007-01-18T13:56:42.000Z");
        final ExpressionOperator or = new ExpressionOperator(Op.OR);
        or.add(eventTime1);
        or.add(eventTime2);
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.add(or);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.add(not);
        test(expression, 23);
    }

    /**
     * Test exclusion of multiple items.
     */
    @Test
    public void notEqualsTest3() {
        final ExpressionTerm eventTime1 = new ExpressionTerm();
        eventTime1.setField("EventTime");
        eventTime1.setCondition(Condition.EQUALS);
        eventTime1.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.EQUALS);
        user.setValue("user4");
        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.add(eventTime1);
        and.add(user);
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.add(and);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.add(not);
        test(expression, 24);
    }

    /**
     * Test more complex exclusion of multiple items.
     */
    @Test
    public void notEqualsTest4() {
        final ExpressionTerm eventTime1 = new ExpressionTerm();
        eventTime1.setField("EventTime");
        eventTime1.setCondition(Condition.EQUALS);
        eventTime1.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionTerm eventTime2 = new ExpressionTerm();
        eventTime2.setField("EventTime");
        eventTime2.setCondition(Condition.EQUALS);
        eventTime2.setValue("2007-01-18T13:56:42.000Z");
        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.EQUALS);
        user.setValue("user4");
        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.add(eventTime1);
        and.add(user);
        final ExpressionOperator or = new ExpressionOperator(Op.OR);
        or.add(and);
        or.add(eventTime2);
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.add(or);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.add(not);
        test(expression, 23);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest1() {
        Dictionary dic = dictionaryService.create(null, "users");
        dic.setData("user1\nuser2\nuser5");
        dic = dictionaryService.save(dic);

        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.IN_DICTIONARY);
        user.setValue("users");
        user.setDictionary(DocRefUtil.create(dic));

        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.add(user);

        test(and, 15);

        dictionaryService.delete(dic);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest2() {
        Dictionary dic1 = dictionaryService.create(null, "users");
        dic1.setData("user1\nuser2\nuser5");
        dic1 = dictionaryService.save(dic1);

        Dictionary dic2 = dictionaryService.create(null, "command");
        dic2.setData("msg");
        dic2 = dictionaryService.save(dic2);

        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.IN_DICTIONARY);
        user.setDictionary(DocRefUtil.create(dic1));

        final ExpressionTerm command = new ExpressionTerm();
        command.setField("Command");
        command.setCondition(Condition.IN_DICTIONARY);
        command.setValue("command");
        command.setDictionary(DocRefUtil.create(dic2));

        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.add(user);
        and.add(command);

        test(and, 10);

        dictionaryService.delete(dic1);
        dictionaryService.delete(dic2);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest3() {
        Dictionary dic1 = dictionaryService.create(null, "users");
        dic1.setData("user1\nuser2\nuser5");
        dic1 = dictionaryService.save(dic1);

        Dictionary dic2 = dictionaryService.create(null, "command");
        dic2.setData("msg foo bar");
        dic2 = dictionaryService.save(dic2);

        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.IN_DICTIONARY);
        user.setValue("users");
        user.setDictionary(DocRefUtil.create(dic1));

        final ExpressionTerm command = new ExpressionTerm();
        command.setField("Command");
        command.setCondition(Condition.IN_DICTIONARY);
        command.setValue("command");
        command.setDictionary(DocRefUtil.create(dic2));

        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.add(user);
        and.add(command);

        test(and, 10);

        dictionaryService.delete(dic1);
        dictionaryService.delete(dic2);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void testBug173() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "!");
        test(expression, 5);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount) {
        final String[] componentIds = new String[]{"table-1"};
        test(expressionIn, expectResultCount, componentIds, true);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount, final String[] componentIds,
                      final boolean extractValues) {
        testInteractive(expressionIn, expectResultCount, componentIds, extractValues);
        testEvents(expressionIn, expectResultCount);
    }

    private void testInteractive(final ExpressionOperator expressionIn, final int expectResultCount,
                                 final String[] componentIds, final boolean extractValues) {
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
        final Query query = new Query(dataSourceRef, expressionIn);
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
            searchService.terminate(queryKey);
        }

        final Map<String, List<Row>> rows = new HashMap<>();
        if (searchResponse != null && searchResponse.getResults() != null) {
            for (final Result result : searchResponse.getResults()) {
                final String componentId = result.getComponentId();
                final TableResult tableResult = (TableResult) result;

                if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                    final stroom.query.api.OffsetRange range = tableResult.getResultRange();

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

    private void testEvents(final ExpressionOperator expressionIn, final int expectResultCount) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRefUtil.create(index);

        final Query query = new Query(dataSourceRef, expressionIn);

        final CountDownLatch complete = new CountDownLatch(1);

        final EventSearchTask eventSearchTask = new EventSearchTask(null, "test", new FindStreamCriteria(), query,
                new EventRef(1, 1), new EventRef(Long.MAX_VALUE, Long.MAX_VALUE), 1000, 1000, 1000, 100);
        final AtomicReference<EventRefs> results = new AtomicReference<>();
        taskManager.execAsync(eventSearchTask, new TaskCallback<EventRefs>() {
            @Override
            public void onSuccess(final EventRefs result) {
                results.set(result);
                complete.countDown();
            }

            @Override
            public void onFailure(final Throwable t) {
                complete.countDown();
            }
        });

        try {
            complete.await();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final EventRefs result = results.get();

        int count = 0;
        if (result != null) {
            count += result.size();
        }

        Assert.assertEquals(expectResultCount, count);
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

    private ExpressionOperator buildInExpression(final String userField, final String userTerm, final String from,
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
        words.setCondition(Condition.IN);
        words.setValue(wordsTerm);

        final ExpressionOperator operator = new ExpressionOperator();
        operator.add(userId);
        operator.add(eventTime);
        operator.add(words);

        return operator;
    }
}
