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

package stroom.statistics.sql.search;

import org.junit.Assert;
import org.junit.Test;

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.statistics.sql.entity.StatisticStoreEntityService;
import stroom.statistics.sql.search.FilterTermsTree.OperatorNode;
import stroom.statistics.sql.search.FilterTermsTree.TermNode;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.test.StroomUnitTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TestFilterTermsTreeBuilder extends StroomUnitTest {
    Set<String> fieldBlackList = new HashSet<>(Arrays.asList(StatisticStoreEntity.FIELD_NAME_DATE_TIME));

    /**
     * Verify that a tree of {@link stroom.query.api.v2.ExpressionItem} objects can be converted
     * correctly into a {@link FilterTermsTree}
     */
    @Test
    public void testConvertExpressionItemsTree() {
        // AND (op1)
        // --term1field IN term1value1,term1value2,term1value3
        // --OR (op2)
        // ----term2field=term2value
        // ----term3field=term3value
        // ----NOT (op3)
        // ---- term4field=term4value

        // should convert to

        // AND (op1)
        // --OR
        // ----term1field=term1value1
        // ----term1field=term1value2
        // ----term1field=term1value3
        // --OR (op2)
        // ----term2field=term2value
        // ----term3field=term3value
        // ----NOT (op3)
        // ---- term4field=term4value

        final String term1value1 = "term1value1";
        final String term1value2 = "term1value2";
        final String term1value3 = "term1value3";

        final ExpressionOperator.Builder op1 = new ExpressionOperator.Builder(Op.AND)
                .addTerm("term1field", Condition.IN, term1value1 + "," + term1value2 + "," + term1value3)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm("term2field", Condition.EQUALS, "term2value")
                        .addTerm("term3field", Condition.EQUALS, "term3value")
                        .addOperator(new ExpressionOperator.Builder(Op.NOT)
                                .addTerm("term4field", Condition.EQUALS, "term4value")
                                .build())
                        .build());

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1.build());

        final OperatorNode newOp1 = (OperatorNode) filterTermsTree.getRootNode();

        Assert.assertEquals(Op.AND.toString(), newOp1.getFilterOperationMode().toString());

        final OperatorNode newTerm1OpNode = (OperatorNode) newOp1.getChildren().get(0);
        Assert.assertEquals(Op.OR.toString(), newTerm1OpNode.getFilterOperationMode().toString());
        Assert.assertEquals(3, newTerm1OpNode.getChildren().size());

        final TermNode newTerm1SubTerm1 = (TermNode) newTerm1OpNode.getChildren().get(0);
        final TermNode newTerm1SubTerm2 = (TermNode) newTerm1OpNode.getChildren().get(1);
        final TermNode newTerm1SubTerm3 = (TermNode) newTerm1OpNode.getChildren().get(2);

        Assert.assertEquals("term1field", newTerm1SubTerm1.getTag());
        Assert.assertEquals(term1value1, newTerm1SubTerm1.getValue());
        Assert.assertEquals("term1field", newTerm1SubTerm2.getTag());
        Assert.assertEquals(term1value2, newTerm1SubTerm2.getValue());
        Assert.assertEquals("term1field", newTerm1SubTerm3.getTag());
        Assert.assertEquals(term1value3, newTerm1SubTerm3.getValue());

        final OperatorNode newOp2 = (OperatorNode) newOp1.getChildren().get(1);

        Assert.assertEquals(Op.OR.toString(), newOp2.getFilterOperationMode().toString());

        final TermNode newTerm2 = (TermNode) newOp2.getChildren().get(0);
        final TermNode newTerm3 = (TermNode) newOp2.getChildren().get(1);
        final OperatorNode newOp3 = (OperatorNode) newOp2.getChildren().get(2);

        Assert.assertEquals("term2field", newTerm2.getTag());
        Assert.assertEquals("term2value", newTerm2.getValue());
        Assert.assertEquals("term3field", newTerm3.getTag());
        Assert.assertEquals("term3value", newTerm3.getValue());
        Assert.assertEquals(Op.NOT.toString(), newOp3.getFilterOperationMode().toString());

        final TermNode newTerm4 = (TermNode) newOp3.getChildren().get(0);
        Assert.assertEquals("term4field", newTerm4.getTag());
        Assert.assertEquals("term4value", newTerm4.getValue());
    }

    @Test
    public void testEmptyExpressionTree() {
        // AND (op1)
        final ExpressionOperator.Builder op1 = new ExpressionOperator.Builder(Op.AND);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1.build());

    }

    /**
     * Should fail as a non-datetime field is using a condition other than
     * equals
     */
    @Test(expected = RuntimeException.class)
    public void testInvalidCondition() {
        // AND (op1)
        // --term1 - datetime equals 123456789
        // --term2 - field1 between 1 and 2

        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.EQUALS, "123456789");
        and.addTerm("term2field", Condition.BETWEEN, "1,2");

        FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(), fieldBlackList);
    }

    @Test
    public void testNonEqualsConditionForDatetimeField() {
        // AND (op1)
        // --term1 - datetime between 1 and 2
        // --term2 - field1 equals 123456789

        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, "1,2");
        and.addTerm("term2field", Condition.EQUALS, "123456789");

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(), fieldBlackList);

        // if we get here without an exception then it has worked as planned
        Assert.assertTrue(filterTermsTree != null);

    }

    @Test
    public void testInConditionOneValue() {
        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, "1,2");
        and.addTerm("term1field", Condition.IN, "123456789");

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(), fieldBlackList);

        final TermNode term2Node = (TermNode) filterTermsTree.getRootNode();

        Assert.assertEquals("term1field", term2Node.getTag());
        Assert.assertEquals("123456789", term2Node.getValue());

    }

    @Test
    public void testInConditionNoValue() {
        final ExpressionOperator.Builder and = new ExpressionOperator.Builder(Op.AND);
        and.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, "1,2");
        and.addTerm("term1field", Condition.IN, "");

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(), fieldBlackList);

        final TermNode term2Node = (TermNode) filterTermsTree.getRootNode();

        Assert.assertNull(term2Node);
    }
}
