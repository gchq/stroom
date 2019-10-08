package stroom.search.extraction;

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;

public class ExpressionFilter {
    public static ExpressionOperator filter(final ExpressionOperator expressionOperator, final String fieldPrefix, final boolean exclude) {
        Builder builder = new Builder();
        filter(expressionOperator, builder, fieldPrefix, exclude);
        return builder.build();
    }

    private static void filter(final ExpressionOperator expressionOperator, final Builder builder, final String fieldPrefix, final boolean exclude) {
        builder.op(expressionOperator.getOp());
        builder.enabled(expressionOperator.enabled());

        expressionOperator.getChildren().forEach(child -> {
            if (child instanceof ExpressionOperator) {
                final ExpressionOperator childOperator = (ExpressionOperator) child;

                final Builder childBuilder = new Builder();
                filter(childOperator, childBuilder, fieldPrefix, exclude);
                builder.addOperator(childBuilder.build());

            } else if (child instanceof ExpressionTerm) {
                final ExpressionTerm childTerm = (ExpressionTerm) child;
                if (exclude) {
                    if (!childTerm.getField().startsWith(fieldPrefix)) {
                        builder.addTerm(childTerm);
                    }
                } else {
                    if (childTerm.getField().startsWith(fieldPrefix)) {
                        builder.addTerm(childTerm);
                    }
                }
            }
        });
    }
}
