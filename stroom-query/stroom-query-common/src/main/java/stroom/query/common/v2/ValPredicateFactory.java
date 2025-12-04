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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.language.functions.Val;

import jakarta.inject.Inject;

import java.util.Optional;
import java.util.function.Predicate;

public class ValPredicateFactory {

    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    public ValPredicateFactory(final ExpressionPredicateFactory expressionPredicateFactory) {
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    public Predicate<Val> createValPredicate(final Column column,
                                             final String filter,
                                             final DateTimeSettings dateTimeSettings) {
        Optional<Predicate<Val>> valuesPredicate = Optional.empty();
        final ExpressionOperator.Builder valueFilterBuilder = ExpressionOperator.builder();
        if (filter != null) {
            final Optional<ExpressionOperator> operator = SimpleStringExpressionParser.create(
                    new SingleFieldProvider(column.getId()),
                    filter);
            operator.ifPresent(valueFilterBuilder::addOperator);
            final ExpressionOperator valueFilter = valueFilterBuilder.build();
            if (ExpressionUtil.hasTerms(valueFilter)) {

                // Create the field position map for the new columns.
                final ValueFunctionFactory<Val> valValFunctionFactory = new ValFunctionFactory(column);
                final ValueFunctionFactories<Val> valueFunctionFactories = name -> valValFunctionFactory;
                valuesPredicate = expressionPredicateFactory.createOptional(
                        valueFilter,
                        valueFunctionFactories,
                        dateTimeSettings);
            }
        }

        return valuesPredicate.orElse(values -> true);
    }
}
