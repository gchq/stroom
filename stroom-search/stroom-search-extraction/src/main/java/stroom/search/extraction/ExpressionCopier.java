package stroom.search.extraction;

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

public class ExpressionCopier {
    public ExpressionOperator copy(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = copyOperator(expressionOperator);
        ExpressionOperator copiedOperator = build(builder);
        if (copiedOperator != null) {
            copiedOperator = filter(copiedOperator);
        }
        return copiedOperator;
    }

    public ExpressionOperator.Builder copyOperator(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder();
        builder.op(expressionOperator.getOp());
        builder.enabled(expressionOperator.enabled());

        expressionOperator.getChildren().forEach(child -> {
            if (child instanceof ExpressionOperator) {
                final ExpressionOperator childOperator = (ExpressionOperator) child;

                final ExpressionOperator.Builder copiedOperatorBuilder = copyOperator(childOperator);
                if (copiedOperatorBuilder != null) {
                    ExpressionOperator copiedOperator = build(copiedOperatorBuilder);
                    if (copiedOperator != null) {
                        copiedOperator = filter(childOperator);
                        if (copiedOperator != null) {
                            builder.addOperator(copiedOperator);
                        }
                    }
                }

            } else if (child instanceof ExpressionTerm) {
                final ExpressionTerm childTerm = (ExpressionTerm) child;
                final ExpressionTerm.Builder copiedTermBuilder = copyTerm(childTerm);
                if (copiedTermBuilder != null) {
                    ExpressionTerm copiedTerm = build(copiedTermBuilder);
                    if (copiedTerm != null) {
                        copiedTerm = filter(copiedTerm);
                        if (copiedTerm != null) {
                            builder.addTerm(copiedTerm);
                        }
                    }
                }
            }
        });
        return builder;
    }

    public ExpressionTerm.Builder copyTerm(final ExpressionTerm expressionTerm) {
        final ExpressionTerm.Builder builder = new ExpressionTerm.Builder();
        builder.enabled(expressionTerm.getEnabled());
        builder.field(expressionTerm.getField());
        builder.condition(expressionTerm.getCondition());
        builder.value(expressionTerm.getValue());
        builder.docRef(expressionTerm.getDocRef());
        return builder;
    }

    public ExpressionOperator build(final ExpressionOperator.Builder builder) {
        return builder.build();
    }

    public ExpressionTerm build(final ExpressionTerm.Builder builder) {
        return builder.build();
    }

    public ExpressionOperator filter(final ExpressionOperator expressionOperator) {
        return expressionOperator;
    }

    public ExpressionTerm filter(final ExpressionTerm expressionTerm) {
        return expressionTerm;
    }
}
