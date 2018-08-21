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
import stroom.dictionary.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.index.IndexStore;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableSettings;
import stroom.query.shared.v2.ParamUtil;
import stroom.security.UserTokenUtil;
import stroom.task.TaskManager;
import stroom.task.api.TaskCallback;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TestInteractiveSearch extends AbstractSearchTest {
    @Inject
    private CommonIndexingTest commonIndexingTest;
    @Inject
    private IndexStore indexStore;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private TaskManager taskManager;

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
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 5);
    }

    @Test
    public void positiveCaseInsensitiveTestMultiComponent() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        final List<String> componentIds = Arrays.asList("table-1", "table-2");
        test(expression, 5, componentIds, true);
    }

    /**
     * Positive case insensitive test.
     */
    @Test
    public void positiveCaseInsensitiveTestWithoutExtraction() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        final List<String> componentIds = Collections.singletonList("table-1");
        test(expression, 5, componentIds, false);
    }

    /**
     * Positive case insensitive test with wildcard.
     */
    @Test
    public void positiveCaseInsensitiveTest2() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 25);
    }

    /**
     * Negative test for case sensitive field.
     */
    @Test
    public void negativeCaseSensitiveTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "e0567");
        test(expression, 0);
    }

    /**
     * Negative test for case sensitive field.
     */
    @Test
    public void negativeCaseSensitiveTest2() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "e0567");
        test(expression, 0);
    }

    /**
     * Positive test case sensitive field.
     */
    @Test
    public void positiveCaseSensitiveTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 25);
    }

    /**
     * Test case sensitive field plus other field.
     */
    @Test
    public void positiveCaseSensitiveTest2() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 5);
    }

    /**
     * Test case sensitive field plus other field.
     */
    @Test
    public void positiveCaseSensitiveTest3() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 5);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg");
        test(expression, 4);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTestWithLeadingWildcard() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "*msg");
        test(expression, 4);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void negativeAnalysedFieldTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg foobar");
        test(expression, 0);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTestWithIn() {
        final ExpressionOperator.Builder expression = buildInExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg foo bar");
        test(expression, 4);
    }

    /**
     * Negative test on keyword field.
     */
    @Test
    public void negativeKeywordFieldTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "foo");
        test(expression, 0);
    }

    /**
     * Positive test on keyword field.
     */
    @Test
    public void positiveKeywordFieldTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "msg=foo bar");
        test(expression, 4);
    }

    /**
     * Positive test on keyword field.
     */
    @Test
    public void positiveKeywordFieldTestWithLeadingWildcard() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "*foo bar");
        test(expression, 4);
    }

    /**
     * Test not equals.
     */
    @Test
    public void notEqualsTest() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.addOperator(new ExpressionOperator.Builder(Op.NOT)
                .addTerm("EventTime", Condition.EQUALS, "2007-08-18T13:50:56.000Z")
                .build());
        test(expression, 24);
    }

    /**
     * Test exclusion of multiple items.
     */
    @Test
    public void notEqualsTest2() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567")
                .addOperator(new ExpressionOperator.Builder(Op.NOT)
                        .addOperator(new ExpressionOperator.Builder(Op.OR)
                                .addTerm("EventTime", Condition.EQUALS, "2007-08-18T13:50:56.000Z")
                                .addTerm("EventTime", Condition.EQUALS, "2007-01-18T13:56:42.000Z")
                                .build())
                        .build());
        test(expression, 23);
    }

    /**
     * Test exclusion of multiple items.
     */
    @Test
    public void notEqualsTest3() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567")
                .addOperator(new ExpressionOperator.Builder(Op.NOT)
                        .addOperator(new ExpressionOperator.Builder(Op.AND)
                                .addTerm("EventTime", Condition.EQUALS, "2007-08-18T13:50:56.000Z")
                                .addTerm("UserId", Condition.EQUALS, "user4")
                                .build())
                        .build());
        test(expression, 24);
    }

    /**
     * Test more complex exclusion of multiple items.
     */
    @Test
    public void notEqualsTest4() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567")
                .addOperator(new ExpressionOperator.Builder(Op.NOT)
                        .addOperator(new ExpressionOperator.Builder(Op.OR)
                                .addOperator(new ExpressionOperator.Builder(Op.AND)
                                        .addTerm("EventTime", Condition.EQUALS, "2007-08-18T13:50:56.000Z")
                                        .addTerm("UserId", Condition.EQUALS, "user4")
                                        .build())
                                .addTerm("EventTime", Condition.EQUALS, "2007-01-18T13:56:42.000Z")
                                .build())
                        .build());
        test(expression, 23);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest1() {
        final DocRef docRef = dictionaryStore.createDocument("users");
        final DictionaryDoc dic = dictionaryStore.readDocument(docRef);
        dic.setData("user1\nuser2\nuser5");
        dictionaryStore.writeDocument(dic);

        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addDictionaryTerm("UserId", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dic));

        test(and, 15);

        dictionaryStore.deleteDocument(dic.getUuid());
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest2() {
        final DocRef docRef1 = dictionaryStore.createDocument("users");
        DictionaryDoc dic1 = dictionaryStore.readDocument(docRef1);
        dic1.setData("user1\nuser2\nuser5");
        dictionaryStore.writeDocument(dic1);

        final DocRef docRef2 = dictionaryStore.createDocument("command");
        DictionaryDoc dic2 = dictionaryStore.readDocument(docRef2);
        dic2.setData("msg");
        dictionaryStore.writeDocument(dic2);

        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addDictionaryTerm("UserId", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dic1));
        and.addDictionaryTerm("Command", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dic2));

        test(and, 10);

        dictionaryStore.deleteDocument(dic1.getUuid());
        dictionaryStore.deleteDocument(dic2.getUuid());
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest3() {
        final DocRef docRef1 = dictionaryStore.createDocument("users");
        DictionaryDoc dic1 = dictionaryStore.readDocument(docRef1);
        dic1.setData("user1\nuser2\nuser5");
        dictionaryStore.writeDocument(dic1);

        final DocRef docRef2 = dictionaryStore.createDocument("command");
        DictionaryDoc dic2 = dictionaryStore.readDocument(docRef2);
        dic2.setData("msg foo bar");
        dictionaryStore.writeDocument(dic2);

        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addDictionaryTerm("UserId", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dic1));
        and.addDictionaryTerm("Command", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dic2));

        test(and, 10);

        dictionaryStore.deleteDocument(dic1.getUuid());
        dictionaryStore.deleteDocument(dic2.getUuid());
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void testBug173() {
        final ExpressionOperator.Builder expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "!");
        test(expression, 5);
    }

    private void test(final ExpressionOperator.Builder expressionIn, final int expectResultCount) {
        final List<String> componentIds = Collections.singletonList("table-1");
        test(expressionIn, expectResultCount, componentIds, true);
    }

    private void test(final ExpressionOperator.Builder expressionIn, final int expectResultCount, final List<String> componentIds,
                      final boolean extractValues) {
        testInteractive(expressionIn, expectResultCount, componentIds, extractValues);
        testEvents(expressionIn, expectResultCount);
    }

    private void testInteractive(final ExpressionOperator.Builder expressionIn,
                                 final int expectResultCount,
                                 final List<String> componentIds,
                                 final boolean extractValues) {

        // code to test the results when they come back
        Consumer<Map<String, List<Row>>> resultMapConsumer = resultMap -> {
            for (final List<Row> values : resultMap.values()) {
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
        };

        testInteractive(expressionIn,
                expectResultCount,
                componentIds,
                this::createTableSettings,
                extractValues,
                resultMapConsumer,
                1,
                1,
                indexStore);
    }

    private void testEvents(final ExpressionOperator.Builder expressionIn, final int expectResultCount) {
        // ADDED THIS SECTION TO TEST GUICE VALUE INJECTION.
//        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
//        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final DocRef indexRef = indexStore.list().get(0);
        Assert.assertNotNull("Index is null", indexRef);

        final Query query = new Query(indexRef, expressionIn.build());

        final CountDownLatch complete = new CountDownLatch(1);

        final EventSearchTask eventSearchTask = new EventSearchTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, query,
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
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }

        final EventRefs result = results.get();

        int count = 0;
        if (result != null) {
            count += result.size();
        }

        Assert.assertEquals(expectResultCount, count);
    }

    private TableSettings createTableSettings(final boolean extractValues) {
        final Field idField = new Field.Builder()
                .name("IdTreeNode")
                .expression(ParamUtil.makeParam("StreamId"))
                .build();

        final Field timeField = new Field.Builder()
                .name("Event Time")
                .expression(ParamUtil.makeParam("EventTime"))
                .format(Format.Type.DATE_TIME)
                .build();

        final DocRef resultPipeline = commonIndexingTest.getSearchResultPipeline();
        return new TableSettings(
                null,
                Arrays.asList(idField, timeField),
                extractValues,
                resultPipeline,
                null,
                null);
    }

    private ExpressionOperator.Builder buildExpression(final String userField, final String userTerm, final String from,
                                                       final String to, final String wordsField, final String wordsTerm) {
        final ExpressionOperator.Builder operator = new ExpressionOperator.Builder();
        operator.addTerm(userField, Condition.CONTAINS, userTerm);
        operator.addTerm("EventTime", Condition.BETWEEN, from + "," + to);
        operator.addTerm(wordsField, Condition.CONTAINS, wordsTerm);
        return operator;
    }

    private ExpressionOperator.Builder buildInExpression(final String userField, final String userTerm, final String from,
                                                         final String to, final String wordsField, final String wordsTerm) {
        final ExpressionOperator.Builder operator = new ExpressionOperator.Builder();
        operator.addTerm(userField, Condition.CONTAINS, userTerm);
        operator.addTerm("EventTime", Condition.BETWEEN, from + "," + to);
        operator.addTerm(wordsField, Condition.IN, wordsTerm);

        return operator;
    }
}
