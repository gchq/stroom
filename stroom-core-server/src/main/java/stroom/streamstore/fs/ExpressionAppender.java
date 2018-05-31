package stroom.streamstore.fs;

import stroom.entity.util.SqlBuilder;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.StreamDataSource;

public class ExpressionAppender {
//    public void append(final SqlBuilder sql, final Set<ExpressionOperator> expressionSet) {
//        if (expressionSet != null && expressionSet.size() > 0) {
//
//        }
//    }
//
    public void append(SqlBuilder sql, final ExpressionOperator operator) {
        if (operator.enabled() && operator.getChildren().size() > 0) {
            switch (operator.getOp()) {
                case AND:
                    sql.append(" AND (");
                    break;
                case OR:
                    sql.append(" OR (");
                    break;
                case NOT:
                    sql.append(" NOT (");
                    break;
            }

            for (final ExpressionItem item : operator.getChildren()) {
                if (item instanceof ExpressionOperator) {
                    final ExpressionOperator child = (ExpressionOperator) item;
                    append(sql, child);

                } else if (item instanceof ExpressionTerm) {
                    final ExpressionTerm child = (ExpressionTerm) item;
                    append(sql, child);
                }
            }

            sql.append(")");
        }
    }

    public void append(SqlBuilder sql, final ExpressionTerm term) {
        if (term.enabled()) {
            switch (term.getField()) {
                case StreamDataSource.FEED:
                sql.append("S." + FeedEntity.FOREIGN_KEY);
                break;
                default: throw new IllegalArgumentException("Unknown field " + term.getField());

            }
            switch (term.getCondition()) {
                case EQUALS:
                    sql.append(" = ");
                    break;
                default: throw new IllegalArgumentException("Unknown operator " + term.getCondition());

            }

            if (term.getField().equals(StreamDataSource.FEED)) {
                sql.arg(term.getValue());
            }
        }
    }
}
