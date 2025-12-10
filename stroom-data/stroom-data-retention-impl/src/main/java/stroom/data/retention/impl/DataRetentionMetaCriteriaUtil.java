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

package stroom.data.retention.impl;

import stroom.data.retention.shared.DataRetentionRule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Builder;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.Period;
import stroom.util.date.DateUtil;

import java.util.List;

public final class DataRetentionMetaCriteriaUtil {

    private DataRetentionMetaCriteriaUtil() {
    }

    public static FindMetaCriteria createCriteria(final Period period,
                                                  final List<DataRetentionRule> rules,
                                                  final int batchSize) {
        // We should only be dealing with active rules at this point

        // We add the rules within a NOT so the data matching each rule's expression is
        // NOT deleted in the period. A rule defines what is to be retained.

        final Builder outer = ExpressionOperator.builder();
//                .addOperator(ExpressionOperator.builder().op(Op.NOT).addOperator(inner.build()).build())

        if (rules != null && !rules.isEmpty()) {
            if (rules.size() == 1) {
                outer.addOperator(negateOperator(rules.get(0).getExpression()));
            } else {
                final Builder inner = ExpressionOperator.builder().op(Op.OR);
                for (final DataRetentionRule rule : rules) {
                    // Ignore empty AND{} or OR{} as they just equal true
//                    if (!canIgnoreOperator(rule.getExpression())) {
                    // expression has children or is a NOT
                    inner.addOperator(rule.getExpression());
//                    }
                }

                final ExpressionOperator innerOperator = inner.build();
//                if (!canIgnoreOperator(innerOperator)) {
                outer.addOperator(negateOperator(innerOperator));
//                }
            }
        }

        outer.addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());

        if (period.getFromMs() != null) {
            outer.addDateTerm(MetaFields.CREATE_TIME,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(period.getFromMs()));
        }

        if (period.getToMs() != null) {
            outer.addDateTerm(MetaFields.CREATE_TIME,
                    Condition.LESS_THAN,
                    DateUtil.createNormalDateTimeString(period.getToMs()));
        }

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(outer.build());
        findMetaCriteria.obtainPageRequest().setLength(batchSize);

        return findMetaCriteria;
    }

    private static ExpressionOperator negateOperator(final ExpressionOperator expressionOperator) {
        return ExpressionOperator.builder().op(Op.NOT)
                .addOperator(expressionOperator)
                .build();
    }


    /**
     * @param expressionOperator
     * @return True if the operator is an empty AND or OR
     */
    private static boolean canIgnoreOperator(final ExpressionOperator expressionOperator) {
        return (expressionOperator.getChildren() == null || expressionOperator.getChildren().isEmpty())
                && (!Op.NOT.equals(expressionOperator.op()));
    }
}
