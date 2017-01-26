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

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonIndexingTest;
import stroom.dashboard.shared.DataSourceFieldsMap;
import stroom.datasource.api.DataSourceField;
import stroom.datasource.api.DataSourceField.DataSourceFieldType;
import stroom.index.server.IndexShardUtil;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.search.server.IndexDataSourceFieldUtil;

import javax.annotation.Resource;
import java.util.List;

public class TestBasicSearch_EndToEnd extends AbstractCoreIntegrationTest {
    @Resource
    private IndexService indexService;
    @Resource
    private IndexShardService indexShardService;
    @Resource
    private CommonIndexingTest commonIndexingTest;

    @Override
    protected boolean doSingleSetup() {
        commonIndexingTest.setup();
        return true;
    }

    @Test
    public void testFindIndexedFields() {
        final Index index = indexService.find(new FindIndexCriteria()).getFirst();

        // Create a map of index fields keyed by name.
        final DataSourceFieldsMap dataSourceFieldsMap = new DataSourceFieldsMap(IndexDataSourceFieldUtil.getDataSourceFields(index));
        final DataSourceField actual = dataSourceFieldsMap.get("Action");
        final DataSourceField expected = new DataSourceField(DataSourceFieldType.FIELD, "Action", true, actual.getConditions());

        Assert.assertEquals("Expected to index action", expected, actual);
    }

    @Test
    public void testTermQuery() throws Exception {
        final ExpressionTerm content1 = new ExpressionTerm();
        content1.setField("UserId");
        content1.setValue("user5");

        final ExpressionOperator expression = new ExpressionOperator();
        expression.add(content1);

        test(expression, 1, 5);
    }

    @Test
    public void testPhraseQuery() throws Exception {
        final String field = "Command";

        final ExpressionTerm content1 = new ExpressionTerm();
        content1.setField(field);
        content1.setValue("service");
        final ExpressionTerm content2 = new ExpressionTerm();
        content2.setField(field);
        content2.setValue("cwhp");
        final ExpressionTerm content3 = new ExpressionTerm();
        content3.setField(field);
        content3.setValue("authorize");
        final ExpressionTerm content4 = new ExpressionTerm();
        content4.setField(field);
        content4.setValue("deviceGroup");

        final ExpressionOperator expression = new ExpressionOperator();
        expression.add(content1);
        expression.add(content2);
        expression.add(content3);
        expression.add(content4);

        test(expression, 1, 23);
    }

    @Test
    public void testBooleanQuery() throws Exception {
        final String field = "Command";

        final ExpressionTerm content1 = new ExpressionTerm();
        content1.setField(field);
        content1.setValue("service");
        final ExpressionTerm content2 = new ExpressionTerm();
        content2.setField(field);
        content2.setValue("cwhp");
        final ExpressionTerm content3 = new ExpressionTerm();
        content3.setField(field);
        content3.setValue("authorize");
        final ExpressionTerm content4 = new ExpressionTerm();
        content4.setField(field);
        content4.setValue("deviceGroup");
        final ExpressionTerm content5 = new ExpressionTerm();
        content5.setField("UserId");
        content5.setValue("user5");

        final ExpressionOperator innerAndCondition = new ExpressionOperator();
        innerAndCondition.add(content1);
        innerAndCondition.add(content2);
        innerAndCondition.add(content3);
        innerAndCondition.add(content4);

        final ExpressionOperator expression = new ExpressionOperator();
        expression.add(innerAndCondition);
        expression.add(content5);

        test(expression, 1, 5);
    }

    @Test
    public void testNestedBooleanQuery() throws Exception {
        final ExpressionTerm content = new ExpressionTerm();
        content.setField("UserId");
        content.setValue("user1");

        final ExpressionOperator andCondition = new ExpressionOperator();
        andCondition.add(content);

        // Check there are 4 events.
        test(andCondition, 1, 4);

        // Create an and query.
        final ExpressionTerm content2 = new ExpressionTerm();
        content2.setField("HostName");
        content2.setValue("e6sm01");
        andCondition.add(content2);

        // There should be two events.
        test(andCondition, 1, 2);

        // Create an or query.
        final ExpressionTerm content3 = new ExpressionTerm();
        content3.setField("UserId");
        content3.setValue("user6");

        final ExpressionOperator orCondition = new ExpressionOperator(ExpressionOperator.Op.OR);
        orCondition.add(content3);

        // There should be two events.
        test(orCondition, 1, 2);

        // Add the and to the or.
        orCondition.add(andCondition);

        // There should be four events.
        test(orCondition, 1, 4);
    }

    @Test
    public void testRangeQuery() throws Exception {
        final ExpressionTerm dateRange = new ExpressionTerm();
        dateRange.setField("EventTime");
        dateRange.setCondition(Condition.BETWEEN);
        dateRange.setValue("2007-08-18T13:21:48.000Z,2007-08-18T13:23:49.000Z");

        final ExpressionOperator expression = new ExpressionOperator();
        expression.add(dateRange);

        test(expression, 1, 2);
    }

    private void test(final ExpressionOperator expression, final long expectedStreams, final long expectedEvents)
            throws Exception {
        final Index index = indexService.find(new FindIndexCriteria()).getFirst();

        final List<IndexShard> list = indexShardService.find(new FindIndexShardCriteria());
        for (final IndexShard indexShard : list) {
            System.out.println("Using index " + IndexShardUtil.getIndexPath(indexShard));
        }
    }
}
