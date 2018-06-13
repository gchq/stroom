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

package stroom.streamstore.server;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.shared.StreamDataSource;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.util.HashMap;
import java.util.Map;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestExpressionMatcher extends StroomUnitTest {
    @Test
    public void testSimpleMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "TEST_FEED"), true);
    }

    @Test
    public void testLeadingWildcardMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "*FEED"), true);
    }

    @Test
    public void testTrailingWildcardMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "TEST*"), true);
    }

    @Test
    public void testNotMatch() {
        test(createAttributeMap(), createExpression(Op.NOT, "TEST_FEED"), false);
    }

    private void test(final Map<String, Object> attributeMap, final ExpressionOperator expression, final boolean outcome) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(StreamDataSource.getFieldMap(), null);
        Assert.assertEquals(outcome, expressionMatcher.match(attributeMap, expression));
    }

    private ExpressionOperator createExpression(final Op op, final String feedName) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(true, op);
        builder.addTerm(StreamDataSource.FEED_NAME, Condition.CONTAINS, feedName);
        return builder.build();
    }

    private Map<String, Object> createAttributeMap() {
        final Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(StreamDataSource.FEED_NAME, "TEST_FEED");
        attributeMap.put(StreamDataSource.STREAM_TYPE_NAME, "Raw Events");
        return attributeMap;
    }
}
