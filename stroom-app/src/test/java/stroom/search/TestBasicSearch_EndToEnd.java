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


import org.junit.jupiter.api.Test;
import stroom.dashboard.shared.DataSourceFieldsMap;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.search.impl.IndexDataSourceFieldUtil;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestBasicSearch_EndToEnd extends AbstractCoreIntegrationTest {
    @Inject
    private IndexStore indexStore;
    @Inject
    private IndexShardService indexShardService;
    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;

    @Override
    protected boolean onAfterSetup() {
        commonIndexingTestHelper.setup();
        return true;
    }

    @Test
    void testFindIndexedFields() {
        final DocRef indexRef = indexStore.list().get(0);
        final IndexDoc index = indexStore.readDocument(indexRef);

        // Create a map of index fields keyed by name.
        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap(IndexDataSourceFieldUtil.getDataSourceFields(index, null));
        final AbstractField actual = dataSourceFieldsMap.get("Action");

        final AbstractField expected = new TextField("Action", true, actual.getConditions());

        assertThat(actual).as("Expected to index action").isEqualTo(expected);
    }

    @Test
    void testTermQuery() {
        final ExpressionOperator.Builder expression = new ExpressionOperator.Builder();
        expression.addTerm("UserId", Condition.EQUALS, "user5");

        test(expression, 1, 5);
    }

    @Test
    void testPhraseQuery() {
        final String field = "Command";

        final ExpressionOperator.Builder expression = new ExpressionOperator.Builder();
        expression.addTerm(field, Condition.EQUALS, "service");
        expression.addTerm(field, Condition.EQUALS, "cwhp");
        expression.addTerm(field, Condition.EQUALS, "authorize");
        expression.addTerm(field, Condition.EQUALS, "deviceGroup");

        test(expression, 1, 23);
    }

    @Test
    void testBooleanQuery() {
        final String field = "Command";
        final ExpressionOperator.Builder expression = new ExpressionOperator.Builder()
                .addOperator(new ExpressionOperator.Builder(Op.AND)
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
        final ExpressionOperator.Builder orCondition = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);
        orCondition.addTerm("UserId", Condition.EQUALS, "user6");

        final ExpressionOperator.Builder andCondition = orCondition.addOperator(new ExpressionOperator.Builder(Op.AND)
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
        final ExpressionOperator.Builder expression = new ExpressionOperator.Builder();
        expression.addTerm("EventTime", Condition.BETWEEN, "2007-08-18T13:21:48.000Z,2007-08-18T13:23:49.000Z");

        test(expression, 1, 2);
    }

    private void test(final ExpressionOperator.Builder expression, final long expectedStreams, final long expectedEvents) {
        final List<IndexShard> list = indexShardService.find(new FindIndexShardCriteria());
        for (final IndexShard indexShard : list) {
            System.out.println("Using index " + IndexShardUtil.getIndexPath(indexShard));
        }
    }
}
