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

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;

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

        if (expressionOperator.getChildren() == null) {
            return builder;
        }

        expressionOperator.getChildren().forEach(child -> {
            if (child instanceof ExpressionOperator) {
                final ExpressionOperator childOperator = (ExpressionOperator) child;

                final ExpressionOperator.Builder copiedOperatorBuilder = copyOperator(childOperator);
                if (copiedOperatorBuilder != null) {
                    final ExpressionOperator copiedOperator = copiedOperatorBuilder.build();
                    if (copiedOperator != null) {
                        builder.addOperator(copiedOperator);
                    }
                }

            } else if (child instanceof ExpressionTerm) {
                final ExpressionTerm childTerm = (ExpressionTerm) child;
                final ExpressionTerm.Builder copiedTermBuilder = copyTerm(childTerm);
                if (copiedTermBuilder != null) {
                    final ExpressionTerm copiedTerm = copiedTermBuilder.build();
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
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.op(expressionOperator.op());
        builder.enabled(expressionOperator.enabled());
        return builder;
    }

    public static ExpressionTerm.Builder createTermBuilder(final ExpressionTerm expressionTerm) {
        final ExpressionTerm.Builder builder = ExpressionTerm.builder();
        builder.enabled(expressionTerm.enabled());
        builder.field(expressionTerm.getField());
        builder.condition(expressionTerm.getCondition());
        builder.value(expressionTerm.getValue());
        builder.docRef(expressionTerm.getDocRef());
        return builder;
    }
}
