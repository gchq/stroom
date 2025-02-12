package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
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
                valuesPredicate = expressionPredicateFactory.create(
                        valueFilter,
                        valueFunctionFactories,
                        dateTimeSettings);
            }
        }

        return valuesPredicate.orElse(values -> true);
    }
}
