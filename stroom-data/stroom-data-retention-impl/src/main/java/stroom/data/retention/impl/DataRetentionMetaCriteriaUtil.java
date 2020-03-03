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

    public static FindMetaCriteria createCriteria(final Period period, final List<DataRetentionRule> rules, final int batchSize) {
        // Ignore rules if none are active.
        final Builder inner = new ExpressionOperator.Builder(Op.OR);
        for (final DataRetentionRule rule : rules) {
            inner.addOperator(rule.getExpression());
        }

        final Builder outer = new ExpressionOperator.Builder(Op.AND)
                .addOperator(new ExpressionOperator.Builder(Op.NOT).addOperator(inner.build()).build())
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
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
}
