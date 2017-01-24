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

package stroom.statistics.common;

import org.junit.Test;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.statistics.common.FilterTermsTree.OperatorNode;
import stroom.statistics.common.FilterTermsTree.TermNode;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.test.StroomUnitTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestFilterTermsTreeBuilder extends StroomUnitTest {
    Set<String> fieldBlackList = new HashSet<>(Arrays.asList(StatisticStoreEntity.FIELD_NAME_DATE_TIME));

    /**
     * Verify that a tree of {@link stroom.query.api.ExpressionItem} objects can be converted
     * correctly into a {@link FilterTermsTree}
     */
    @Test
    public void testConvertExpresionItemsTree() {
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

        final ExpressionTerm term4 = buildTerm("term4field", "term4value", Condition.EQUALS);
        final ExpressionTerm term3 = buildTerm("term3field", "term3value", Condition.EQUALS);
        final ExpressionTerm term2 = buildTerm("term2field", "term2value", Condition.EQUALS);

        final String term1value1 = "term1value1";
        final String term1value2 = "term1value2";
        final String term1value3 = "term1value3";

        final ExpressionTerm term1 = buildTerm("term1field", term1value1 + "," + term1value2 + "," + term1value3,
                Condition.IN);

        final ExpressionOperator op3 = new ExpressionOperator(Op.NOT);
        op3.add(term4);

        final ExpressionOperator op2 = new ExpressionOperator(Op.OR);
        op2.add(term2).add(term3).add(op3);

        final ExpressionOperator op1 = new ExpressionOperator(Op.AND);
        op1.add(term1).add(op2);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1);

        final OperatorNode newOp1 = (OperatorNode) filterTermsTree.getRootNode();

        assertEquals(op1.getOp().toString(), newOp1.getFilterOperationMode().toString());

        final OperatorNode newTerm1OpNode = (OperatorNode) newOp1.getChildren().get(0);
        assertEquals(Op.OR.toString(), newTerm1OpNode.getFilterOperationMode().toString());
        assertEquals(3, newTerm1OpNode.getChildren().size());

        final TermNode newTerm1SubTerm1 = (TermNode) newTerm1OpNode.getChildren().get(0);
        final TermNode newTerm1SubTerm2 = (TermNode) newTerm1OpNode.getChildren().get(1);
        final TermNode newTerm1SubTerm3 = (TermNode) newTerm1OpNode.getChildren().get(2);

        assertEquals(term1.getField(), newTerm1SubTerm1.getTag());
        assertEquals(term1value1, newTerm1SubTerm1.getValue());
        assertEquals(term1.getField(), newTerm1SubTerm2.getTag());
        assertEquals(term1value2, newTerm1SubTerm2.getValue());
        assertEquals(term1.getField(), newTerm1SubTerm3.getTag());
        assertEquals(term1value3, newTerm1SubTerm3.getValue());

        final OperatorNode newOp2 = (OperatorNode) newOp1.getChildren().get(1);

        assertEquals(op2.getOp().toString(), newOp2.getFilterOperationMode().toString());

        final TermNode newTerm2 = (TermNode) newOp2.getChildren().get(0);
        final TermNode newTerm3 = (TermNode) newOp2.getChildren().get(1);
        final OperatorNode newOp3 = (OperatorNode) newOp2.getChildren().get(2);

        assertEquals(term2.getField(), newTerm2.getTag());
        assertEquals(term2.getValue(), newTerm2.getValue());
        assertEquals(term3.getField(), newTerm3.getTag());
        assertEquals(term3.getValue(), newTerm3.getValue());
        assertEquals(op3.getOp().toString(), newOp3.getFilterOperationMode().toString());

        final TermNode newTerm4 = (TermNode) newOp3.getChildren().get(0);
        assertEquals(term4.getField(), newTerm4.getTag());
        assertEquals(term4.getValue(), newTerm4.getValue());
    }

    @Test
    public void testEmptyExpressionTree() {
        // AND (op1)

        final ExpressionOperator op1 = new ExpressionOperator(Op.AND);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1);

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

        final ExpressionTerm term1 = buildTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, "123456789",
                Condition.EQUALS);
        final ExpressionTerm term2 = buildTerm("term2field", "1,2", Condition.BETWEEN);

        final ExpressionOperator op1 = new ExpressionOperator(Op.AND);
        op1.add(term1).add(term2);

        FilterTermsTreeBuilder.convertExpresionItemsTree(op1, fieldBlackList);
    }

    @Test
    public void testNonEqualsConditionForDatetimeField() {
        // AND (op1)
        // --term1 - datetime between 1 and 2
        // --term2 - field1 equals 123456789

        final ExpressionTerm term1 = buildTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, "1,2",
                Condition.BETWEEN);
        final ExpressionTerm term2 = buildTerm("term2field", "123456789", Condition.EQUALS);

        final ExpressionOperator op1 = new ExpressionOperator(Op.AND);
        op1.add(term1).add(term2);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1, fieldBlackList);

        // if we get here without an exception then it has worked as planned
        assertTrue(filterTermsTree != null);

    }

    @Test
    public void testInConditionOneValue() {
        final ExpressionTerm term1 = buildTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, "1,2",
                Condition.BETWEEN);
        final ExpressionTerm term2 = buildTerm("term1field", "123456789", Condition.IN);

        final ExpressionOperator op1 = new ExpressionOperator(Op.AND);
        op1.add(term1).add(term2);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1, fieldBlackList);

        final TermNode term2Node = (TermNode) filterTermsTree.getRootNode();

        assertEquals("term1field", term2Node.getTag());
        assertEquals("123456789", term2Node.getValue());

    }

    @Test
    public void testInConditionNoValue() {
        final ExpressionTerm term1 = buildTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, "1,2",
                Condition.BETWEEN);
        final ExpressionTerm term2 = buildTerm("term1field", null, Condition.IN);

        final ExpressionOperator op1 = new ExpressionOperator(Op.AND);
        op1.add(term1).add(term2);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1, fieldBlackList);

        final TermNode term2Node = (TermNode) filterTermsTree.getRootNode();

        assertEquals("term1field", term2Node.getTag());
        assertEquals(null, term2Node.getValue());
    }

    private static ExpressionTerm buildTerm(final String field, final String value, final Condition condition) {
        final ExpressionTerm term = new ExpressionTerm();
        term.setCondition(condition);
        term.setField(field);
        term.setValue(value);
        return term;
    }
}
