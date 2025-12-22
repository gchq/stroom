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

package stroom.search.extraction;

import stroom.query.api.ExpressionTerm;

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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private List<Function<ExpressionTerm.Builder, ExpressionTerm.Builder>> functions = new ArrayList<>();

        private Builder() {
        }

        private Builder(final ExpressionFilter expressionFilter) {
            functions = new ArrayList<>(expressionFilter.functions);
        }

        public Builder addPrefixIncludeFilter(final String fieldPrefix) {
            final Function<ExpressionTerm.Builder, ExpressionTerm.Builder> function = builder -> {
                final ExpressionTerm expressionTerm = builder.build();
                if (expressionTerm.getField() != null && expressionTerm.getField().startsWith(fieldPrefix)) {
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
                if (expressionTerm.getField() != null && !expressionTerm.getField().startsWith(fieldPrefix)) {
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
