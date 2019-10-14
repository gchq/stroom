package stroom.search.extraction;

import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Builder;

public class ExpressionReplacer extends ExpressionFilter {
    private static final String CURRENT_USER = "currentUser()";

    private final String userId;

    public ExpressionReplacer(final String fieldPrefix, final boolean exclude, final String userId) {
        super(fieldPrefix, exclude);
        this.userId = userId;
    }

    @Override
    public Builder copyTerm(final ExpressionTerm expressionTerm) {
        Builder builder = super.copyTerm(expressionTerm);
        if (expressionTerm.getValue() != null && expressionTerm.getValue().contains(CURRENT_USER)) {
            String value = expressionTerm.getValue();
            value = value.replaceAll(CURRENT_USER, userId);
            builder.value(value);
        }
        return builder;
    }
}
