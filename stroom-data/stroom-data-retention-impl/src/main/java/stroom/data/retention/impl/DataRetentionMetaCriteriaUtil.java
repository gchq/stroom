package stroom.data.retention.impl;

import stroom.data.retention.shared.DataRetentionRule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.date.DateUtil;
import stroom.util.Period;

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

        final Builder outer = new ExpressionOperator.Builder(Op.AND);
//                .addOperator(new ExpressionOperator.Builder(Op.NOT).addOperator(inner.build()).build())

        if (rules != null && !rules.isEmpty()) {
            if (rules.size() == 1) {
                outer.addOperator(negateOperator(rules.get(0).getExpression()));
            } else {
                final Builder inner = new ExpressionOperator.Builder(Op.OR);
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

        outer.addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());

        if (period.getFromMs() != null) {
            outer.addTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(period.getFromMs()));
        }

        if (period.getToMs() != null) {
            outer.addTerm(MetaFields.CREATE_TIME, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(period.getToMs()));
        }

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(outer.build());
        findMetaCriteria.obtainPageRequest().setLength(batchSize);

        return findMetaCriteria;
    }

    private static ExpressionOperator negateOperator(final ExpressionOperator expressionOperator) {
        return new ExpressionOperator.Builder(Op.NOT)
                .addOperator(expressionOperator)
                .build();
    }


    /**
     * @param expressionOperator
     * @return True if the operator is an empty AND or OR
     */
    private static boolean canIgnoreOperator(final ExpressionOperator expressionOperator) {
        if ((expressionOperator.getChildren() != null && !expressionOperator.getChildren().isEmpty())
                || (Op.NOT.equals(expressionOperator.op()))) {
            return false;
        } else {
            return true;
        }
    }
}
