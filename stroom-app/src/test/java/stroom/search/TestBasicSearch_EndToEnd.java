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
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.PathCreator;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestBasicSearch_EndToEnd extends AbstractCoreIntegrationTest {

    @Inject
    private IndexStore indexStore;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private PathCreator pathCreator;
    @Inject
    private IndexFieldService indexFieldService;

    private boolean doneSetup;

    @BeforeEach
    void setup() {
        if (!doneSetup) {
            commonIndexingTestHelper.setup();
            doneSetup = true;
        }
    }

    @Test
    void testFindIndexedFields() {
        final DocRef indexRef = indexStore.list().getFirst();
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);

        // Create a map of index fields keyed by name.
        final FindFieldCriteria findFieldCriteria =
                new FindFieldCriteria(PageRequest.unlimited(), FindFieldCriteria.DEFAULT_SORT_LIST, indexRef);
        final Map<String, IndexField> dataSourceFieldsMap = indexFieldService.findFields(findFieldCriteria)
                .getValues()
                .stream()
                .collect(Collectors.toMap(IndexField::getFldName, Function.identity()));
        final IndexField actual = dataSourceFieldsMap.get("Action");
        final IndexField expected = IndexFieldImpl
                .builder()
                .fldName("Action")
                .fldType(FieldType.TEXT)
                .analyzerType(actual.getAnalyzerType())
                .indexed(true)
                .build();
        assertThat(actual).as("Expected to index action").isEqualTo(expected);
    }

    @Test
    void testTermQuery() {
        final ExpressionOperator.Builder expression = ExpressionOperator.builder();
        expression.addTerm("UserId", Condition.EQUALS, "user5");

        test(expression, 1, 5);
    }

    @Test
    void testPhraseQuery() {
        final String field = "Command";

        final ExpressionOperator.Builder expression = ExpressionOperator.builder();
        expression.addTerm(field, Condition.EQUALS, "service");
        expression.addTerm(field, Condition.EQUALS, "cwhp");
        expression.addTerm(field, Condition.EQUALS, "authorize");
        expression.addTerm(field, Condition.EQUALS, "deviceGroup");

        test(expression, 1, 23);
    }

    @Test
    void testBooleanQuery() {
        final String field = "Command";
        final ExpressionOperator.Builder expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .addTerm(field, Condition.EQUALS, "service")
                        .addTerm(field, Condition.EQUALS, "cwhp")
                        .addTerm(field, Condition.EQUALS, "authorize")
                        .addTerm(field, Condition.EQUALS, "deviceGroup")
                        .build())
                .addTerm("UserId", Condition.EQUALS, "user5");
        test(expression, 1, 5);
    }

    @Test
    void testNestedBooleanQuery() {
        // Create an or query.
        final ExpressionOperator.Builder orCondition = ExpressionOperator.builder().op(Op.OR);
        orCondition.addTerm("UserId", Condition.EQUALS, "user6");

        final ExpressionOperator.Builder andCondition = orCondition.addOperator(ExpressionOperator.builder()
                .addTerm("UserId", Condition.EQUALS, "user1")
                .build());

        // Check there are 4 events.
        test(andCondition, 1, 4);

        // Create an and query.
        andCondition.addTerm("HostName", Condition.EQUALS, "e6sm01");

        // There should be two events.
        test(andCondition, 1, 2);

        // There should be two events.
        test(orCondition, 1, 2);

        // There should be four events.
        test(orCondition, 1, 4);
    }

    @Test
    void testRangeQuery() {
        final ExpressionOperator.Builder expression = ExpressionOperator.builder();
        expression.addTerm("EventTime", Condition.BETWEEN, "2007-08-18T13:21:48.000Z,2007-08-18T13:23:49.000Z");

        test(expression, 1, 2);
    }

    private void test(final ExpressionOperator.Builder expression,
                      final long expectedStreams,
                      final long expectedEvents) {
        final ResultPage<IndexShard> resultPage = indexShardDao.find(FindIndexShardCriteria.matchAll());
        for (final IndexShard indexShard : resultPage.getValues()) {
            System.out.println("Using index " + IndexShardUtil.getIndexPath(indexShard, pathCreator));
        }
    }
}
