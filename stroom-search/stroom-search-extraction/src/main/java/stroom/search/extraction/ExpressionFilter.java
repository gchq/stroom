package stroom.search.extraction;

import stroom.query.api.v2.ExpressionTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class ExpressionFilter extends ExpressionCopier {
    private final List<Function<ExpressionTerm.Builder, ExpressionTerm.Builder>> functions;

    private ExpressionFilter(final List<Function<ExpressionTerm.Builder, ExpressionTerm.Builder>> functions) {
        this.functions = functions;
    }

    @Override
    protected ExpressionTerm.Builder copyTerm(final ExpressionTerm expressionTerm) {
        // Apply filters.
        if (expressionTerm != null) {
            ExpressionTerm.Builder builder = super.copyTerm(expressionTerm);
            for (final Function<ExpressionTerm.Builder, ExpressionTerm.Builder> function : functions) {
                if (builder == null) {
                    return null;
                }
                builder = function.apply(builder);
            }
            return builder;
        }

        return null;
    }

    public static class Builder {
        private final List<Function<ExpressionTerm.Builder, ExpressionTerm.Builder>> functions = new ArrayList<>();

        public Builder addPrefixIncludeFilter(final String fieldPrefix) {
            final Function<ExpressionTerm.Builder, ExpressionTerm.Builder> function = builder -> {
                final ExpressionTerm expressionTerm = builder.build();
                if (expressionTerm.getField().startsWith(fieldPrefix)) {
                    return builder;
                }
                return null;
            };
            functions.add(function);
            return this;
        }

        public Builder addPrefixExcludeFilter(final String fieldPrefix) {
            final Function<ExpressionTerm.Builder, ExpressionTerm.Builder> function = builder -> {
                final ExpressionTerm expressionTerm = builder.build();
                if (!expressionTerm.getField().startsWith(fieldPrefix)) {
                    return builder;
                }
                return null;
            };
            functions.add(function);
            return this;
        }

        public Builder addReplacementFilter(final String find, final String replace) {
            final Pattern pattern = Pattern.compile(find, Pattern.LITERAL);
            final Function<ExpressionTerm.Builder, ExpressionTerm.Builder> function = builder -> {
                final ExpressionTerm expressionTerm = builder.build();
                if (expressionTerm.getValue() != null) {
                    builder.value(pattern.matcher(expressionTerm.getValue()).replaceAll(replace));
                }
                return builder;
            };
            functions.add(function);
            return this;
        }

        public ExpressionFilter build() {
            return new ExpressionFilter(new ArrayList<>(functions));
        }
    }
}
