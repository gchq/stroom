package stroom.search.extraction;

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

public class ExpressionCopier {
    public ExpressionOperator copy(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = copyOperator(expressionOperator);
        if (builder != null) {
            return builder.build();
        }
        return null;
    }

    protected ExpressionOperator.Builder copyOperator(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = createOperatorBuilder(expressionOperator);

        expressionOperator.getChildren().forEach(child -> {
            if (child instanceof ExpressionOperator) {
                final ExpressionOperator childOperator = (ExpressionOperator) child;

                final ExpressionOperator.Builder copiedOperatorBuilder = copyOperator(childOperator);
                if (copiedOperatorBuilder != null) {
                    ExpressionOperator copiedOperator = copiedOperatorBuilder.build();
                    if (copiedOperator != null) {
                        builder.addOperator(copiedOperator);
                    }
                }

            } else if (child instanceof ExpressionTerm) {
                final ExpressionTerm childTerm = (ExpressionTerm) child;
                final ExpressionTerm.Builder copiedTermBuilder = copyTerm(childTerm);
                if (copiedTermBuilder != null) {
                    ExpressionTerm copiedTerm = copiedTermBuilder.build();
                    if (copiedTerm != null) {
                        builder.addTerm(copiedTerm);
                    }
                }
            }
        });
        return builder;
    }

    protected ExpressionTerm.Builder copyTerm(final ExpressionTerm expressionTerm) {
        return createTermBuilder(expressionTerm);
    }

    public static ExpressionOperator.Builder createOperatorBuilder(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder();
        builder.op(expressionOperator.op());
        builder.enabled(expressionOperator.enabled());
        return builder;
    }

    public static ExpressionTerm.Builder createTermBuilder(final ExpressionTerm expressionTerm) {
        final ExpressionTerm.Builder builder = new ExpressionTerm.Builder();
        builder.enabled(expressionTerm.enabled());
        builder.field(expressionTerm.getField());
        builder.condition(expressionTerm.getCondition());
        builder.value(expressionTerm.getValue());
        builder.docRef(expressionTerm.getDocRef());
        return builder;
    }
}
