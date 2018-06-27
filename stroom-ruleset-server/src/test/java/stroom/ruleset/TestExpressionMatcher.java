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

package stroom.ruleset;

import org.junit.jupiter.api.Test;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.data.store.ExpressionMatcher;
import stroom.data.meta.api.MetaDataSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TestExpressionMatcher {
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
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(MetaDataSource.getFieldMap(), null);
        assertThat(expressionMatcher.match(attributeMap, expression)).isEqualTo(outcome);
    }

    private ExpressionOperator createExpression(final Op op, final String feedName) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(true, op);
        builder.addTerm("Feed", Condition.CONTAINS, feedName);
        return builder.build();
    }

    private Map<String, Object> createAttributeMap() {
        final Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(MetaDataSource.FEED, "TEST_FEED");
        attributeMap.put(MetaDataSource.STREAM_TYPE, "Raw Events");
        return attributeMap;
    }
}
