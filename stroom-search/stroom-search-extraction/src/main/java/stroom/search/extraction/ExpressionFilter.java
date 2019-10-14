package stroom.search.extraction;

import stroom.query.api.v2.ExpressionTerm;

public class ExpressionFilter extends ExpressionCopier {
    private final String fieldPrefix;
    private final boolean exclude;

    public ExpressionFilter(final String fieldPrefix, final boolean exclude) {
        this.fieldPrefix = fieldPrefix;
        this.exclude = exclude;
    }

    @Override
    public ExpressionTerm filter(final ExpressionTerm expressionTerm) {
        if (exclude) {
            if (!expressionTerm.getField().startsWith(fieldPrefix)) {
                return expressionTerm;
            }
        } else {
            if (expressionTerm.getField().startsWith(fieldPrefix)) {
                return expressionTerm;
            }
        }

        return null;
    }
}
