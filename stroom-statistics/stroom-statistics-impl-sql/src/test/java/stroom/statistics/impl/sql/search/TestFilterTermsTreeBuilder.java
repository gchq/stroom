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

package stroom.statistics.impl.sql.search;


import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.statistics.impl.sql.search.FilterTermsTree.OperatorNode;
import stroom.statistics.impl.sql.search.FilterTermsTree.TermNode;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFilterTermsTreeBuilder extends StroomUnitTest {

    private Set<String> fieldBlackList = Set.of(StatisticStoreDoc.FIELD_NAME_DATE_TIME);

    /**
     * Verify that a tree of {@link stroom.query.api.ExpressionItem} objects can be converted
     * correctly into a {@link FilterTermsTree}
     */
    @Test
    void testConvertExpressionItemsTree() {
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

        final ExpressionOperator.Builder op1 = ExpressionOperator.builder()
                .addTerm("term1field", Condition.IN, term1value1 + "," + term1value2 + "," + term1value3)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm("term2field", Condition.EQUALS, "term2value")
                        .addTerm("term3field", Condition.EQUALS, "term3value")
                        .addOperator(ExpressionOperator.builder().op(Op.NOT)
                                .addTerm("term4field", Condition.EQUALS, "term4value")
                                .build())
                        .build());

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(op1.build());

        final OperatorNode newOp1 = (OperatorNode) filterTermsTree.getRootNode();

        assertThat(newOp1.getFilterOperationMode().toString()).isEqualTo(Op.AND.toString());

        final OperatorNode newTerm1OpNode = (OperatorNode) newOp1.getChildren().get(0);
        assertThat(newTerm1OpNode.getFilterOperationMode().toString()).isEqualTo(Op.OR.toString());
        assertThat(newTerm1OpNode.getChildren().size()).isEqualTo(3);

        final TermNode newTerm1SubTerm1 = (TermNode) newTerm1OpNode.getChildren().get(0);
        final TermNode newTerm1SubTerm2 = (TermNode) newTerm1OpNode.getChildren().get(1);
        final TermNode newTerm1SubTerm3 = (TermNode) newTerm1OpNode.getChildren().get(2);

        assertThat(newTerm1SubTerm1.getTag()).isEqualTo("term1field");
        assertThat(newTerm1SubTerm1.getValue()).isEqualTo(term1value1);
        assertThat(newTerm1SubTerm2.getTag()).isEqualTo("term1field");
        assertThat(newTerm1SubTerm2.getValue()).isEqualTo(term1value2);
        assertThat(newTerm1SubTerm3.getTag()).isEqualTo("term1field");
        assertThat(newTerm1SubTerm3.getValue()).isEqualTo(term1value3);

        final OperatorNode newOp2 = (OperatorNode) newOp1.getChildren().get(1);

        assertThat(newOp2.getFilterOperationMode().toString()).isEqualTo(Op.OR.toString());

        final TermNode newTerm2 = (TermNode) newOp2.getChildren().get(0);
        final TermNode newTerm3 = (TermNode) newOp2.getChildren().get(1);
        final OperatorNode newOp3 = (OperatorNode) newOp2.getChildren().get(2);

        assertThat(newTerm2.getTag()).isEqualTo("term2field");
        assertThat(newTerm2.getValue()).isEqualTo("term2value");
        assertThat(newTerm3.getTag()).isEqualTo("term3field");
        assertThat(newTerm3.getValue()).isEqualTo("term3value");
        assertThat(newOp3.getFilterOperationMode().toString()).isEqualTo(Op.NOT.toString());

        final TermNode newTerm4 = (TermNode) newOp3.getChildren().get(0);
        assertThat(newTerm4.getTag()).isEqualTo("term4field");
        assertThat(newTerm4.getValue()).isEqualTo("term4value");
    }

    @Test
    void testEmptyExpressionTree() {
        // AND (op1)
        final ExpressionOperator.Builder op1 = ExpressionOperator.builder();
        FilterTermsTreeBuilder.convertExpresionItemsTree(op1.build());
    }

    /**
     * Should fail as a non-datetime field is using a condition other than
     * equals
     */
    @Test
    void testInvalidCondition() {
        assertThatThrownBy(() -> {
            // AND (op1)
            // --term1 - datetime equals 123456789
            // --term2 - field1 between 1 and 2

            final ExpressionOperator.Builder and = ExpressionOperator.builder();
            and.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.EQUALS, "123456789");
            and.addTerm("term2field", Condition.BETWEEN, "1,2");

            FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(), fieldBlackList);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testNonEqualsConditionForDatetimeField() {
        // AND (op1)
        // --term1 - datetime between 1 and 2
        // --term2 - field1 equals 123456789

        final ExpressionOperator.Builder and = ExpressionOperator.builder();
        and.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, "1,2");
        and.addTerm("term2field", Condition.EQUALS, "123456789");

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(),
                fieldBlackList);

        // if we get here without an exception then it has worked as planned
        assertThat(filterTermsTree != null).isTrue();

    }

    @Test
    void testInConditionOneValue() {
        final ExpressionOperator.Builder and = ExpressionOperator.builder();
        and.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, "1,2");
        and.addTerm("term1field", Condition.IN, "123456789");

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(),
                fieldBlackList);

        final TermNode term2Node = (TermNode) filterTermsTree.getRootNode();

        assertThat(term2Node.getTag()).isEqualTo("term1field");
        assertThat(term2Node.getValue()).isEqualTo("123456789");

    }

    @Test
    void testInConditionNoValue() {
        final ExpressionOperator.Builder and = ExpressionOperator.builder();
        and.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, "1,2");
        and.addTerm("term1field", Condition.IN, "");

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder.convertExpresionItemsTree(and.build(),
                fieldBlackList);

        final TermNode term2Node = (TermNode) filterTermsTree.getRootNode();

        assertThat(term2Node).isNull();
    }
}
