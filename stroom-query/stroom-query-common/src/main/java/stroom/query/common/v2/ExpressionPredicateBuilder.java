/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.query.common.v2;

import stroom.datasource.api.v2.FieldType;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.NullSafe;
import stroom.util.filter.StringPredicateFactory;
import stroom.util.shared.CompareUtil;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ExpressionPredicateBuilder {

    private static final String DELIMITER = ",";

    public static <T> Optional<Predicate<T>> create(final ExpressionOperator operator,
                                                    final ValueFunctionFactories<T> queryFieldIndex,
                                                    final DateTimeSettings dateTimeSettings) {
        if (operator == null) {
            return Optional.empty();
        }

        return createPredicate(operator, queryFieldIndex, dateTimeSettings);
    }

    private static <T> Optional<Predicate<T>> createPredicate(final ExpressionItem item,
                                                              final ValueFunctionFactories<T> queryFieldIndex,
                                                              final DateTimeSettings dateTimeSettings) {
        if (!item.enabled()) {
            return Optional.empty();
        }

        if (item instanceof ExpressionOperator) {
            return createOperatorPredicate((ExpressionOperator) item, queryFieldIndex, dateTimeSettings);
        } else if (item instanceof ExpressionTerm) {
            return createTermPredicate((ExpressionTerm) item, queryFieldIndex, dateTimeSettings);
        } else {
            throw new MatchException("Unexpected item type");
        }
    }

    private static <T> Optional<Predicate<T>> createOperatorPredicate(final ExpressionOperator operator,
                                                                      final ValueFunctionFactories<T> queryFieldIndex,
                                                                      final DateTimeSettings dateTimeSettings) {
        if (!operator.enabled() || operator.getChildren() == null || operator.getChildren().isEmpty()) {
            return Optional.empty();
        }

        return switch (operator.op()) {
            case AND -> {
                final List<Predicate<T>> predicates = new ArrayList<>(operator.getChildren().size());
                for (final ExpressionItem child : operator.getChildren()) {
                    Optional<Predicate<T>> optional = createPredicate(child, queryFieldIndex, dateTimeSettings);
                    optional.ifPresent(predicates::add);
                }
                if (predicates.isEmpty()) {
                    yield Optional.empty();
                }
                if (predicates.size() == 1) {
                    yield Optional.of(predicates.getFirst());
                }
                yield AndPredicate.create(predicates);
            }
            case OR -> {
                final List<Predicate<T>> predicates = new ArrayList<>(operator.getChildren().size());
                for (final ExpressionItem child : operator.getChildren()) {
                    Optional<Predicate<T>> optional = createPredicate(child, queryFieldIndex, dateTimeSettings);
                    optional.ifPresent(predicates::add);
                }
                if (predicates.isEmpty()) {
                    yield Optional.empty();
                }
                if (predicates.size() == 1) {
                    yield Optional.of(predicates.getFirst());
                }
                yield OrPredicate.create(predicates);
            }
            case NOT -> {
                if (operator.getChildren().size() > 1) {
                    throw new MatchException("Unexpected number of child terms in NOT");
                }
                final Optional<Predicate<T>> optionalChildPredicate =
                        createPredicate(operator.getChildren().getFirst(), queryFieldIndex, dateTimeSettings);
                if (optionalChildPredicate.isEmpty()) {
                    yield Optional.empty();
                }
                yield NotPredicate.create(optionalChildPredicate.get());
            }
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static <T> Optional<Predicate<T>> createTermPredicate(final ExpressionTerm term,
                                                                  final ValueFunctionFactories<T> valueFunctionFactories,
                                                                  final DateTimeSettings dateTimeSettings) {
        if (!term.enabled()) {
            return Optional.empty();
        }

        // The term field is the column name, NOT the index field name
        final Condition condition = term.getCondition();

        // Try and find the referenced field.
        String termField = term.getField();
        if (NullSafe.isBlankString(termField)) {
            throw new MatchException("Field not set");
        }
        termField = termField.trim();
        final ValueFunctionFactory<T> valueFunctionFactory = valueFunctionFactories.get(termField);
        if (valueFunctionFactory == null) {
            throw new MatchException("Field not found: " + termField);
        }

        if (Condition.IS_NULL.equals(condition)) {
            return IsNullPredicate.create(term, valueFunctionFactory.createNullCheck());
        } else if (Condition.IS_NOT_NULL.equals(condition)) {
            return IsNotNullPredicate.create(term, valueFunctionFactory.createNullCheck());
        }

        // Try and resolve the term value.
        String termValue = term.getValue();
        if (NullSafe.isBlankString(termValue)) {
            throw new MatchException("Value not set");
        }

        // Create a query based on the field type and condition.
//        final FieldType fieldType = valueFunctionFactory.getFieldType();
//        if (FieldType.DATE.equals(fieldType)) {
//            return createDateTermPredicate(term, valueFunctionFactory, dateTimeSettings);
//        } else if (fieldType.isNumeric()) {
//            return createNumericTermPredicate(term, valueFunctionFactory);
//        } else {
        return createGeneralTermPredicate(term, valueFunctionFactory, dateTimeSettings);
//        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static <T> Optional<Predicate<T>> createGeneralTermPredicate(final ExpressionTerm term,
                                                                         final ValueFunctionFactory<T> valueFunctionFactory,
                                                                         final DateTimeSettings dateTimeSettings) {
        try {
            return createDateTermPredicate(term, valueFunctionFactory, dateTimeSettings);
        } catch (final RuntimeException e) {
            try {
                return createNumericTermPredicate(term, valueFunctionFactory);
            } catch (final RuntimeException e2) {
                final Function<T, String> stringExtractor = valueFunctionFactory.createStringExtractor();
                return switch (term.getCondition()) {
                    case EQUALS -> StringEquals.create(term, stringExtractor);
                    case NOT_EQUALS -> StringNotEquals.create(term, stringExtractor);
                    case CONTAINS -> StringContains.create(term, stringExtractor);
                    case STARTS_WITH -> StringStartsWith.create(term, stringExtractor);
                    case ENDS_WITH -> StringEndsWith.create(term, stringExtractor);
                    case MATCHES_REGEX -> StringRegex.create(term, stringExtractor);
                    case WORD_BOUNDARY -> StringWordBoundary.create(term, stringExtractor);
                    case IN -> StringIn.create(term, stringExtractor);
                    default -> throw e2;
                };
            }
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static <T> Optional<Predicate<T>> createDateTermPredicate(final ExpressionTerm term,
                                                                      final ValueFunctionFactory<T> valueFunctionFactory,
                                                                      final DateTimeSettings dateTimeSettings) {
        final Function<T, Long> dateExtractor = valueFunctionFactory.createDateExtractor();
        return switch (term.getCondition()) {
            case EQUALS -> DateEquals.create(term, dateExtractor, dateTimeSettings);
            case NOT_EQUALS -> DateNotEquals.create(term, dateExtractor, dateTimeSettings);
            case GREATER_THAN -> DateGreaterThan.create(term, dateExtractor, dateTimeSettings);
            case GREATER_THAN_OR_EQUAL_TO -> DateGreaterThanOrEqualTo.create(term, dateExtractor, dateTimeSettings);
            case LESS_THAN -> DateLessThan.create(term, dateExtractor, dateTimeSettings);
            case LESS_THAN_OR_EQUAL_TO -> DateLessThanOrEqualTo.create(term, dateExtractor, dateTimeSettings);
            case BETWEEN -> DateBetween.create(term, dateExtractor, dateTimeSettings);
            case IN -> DateIn.create(term, dateExtractor, dateTimeSettings);
            default -> throw new MatchException("Unexpected condition '" +
                    term.getCondition().getDisplayValue() +
                    "' for " +
                    valueFunctionFactory.getFieldType() +
                    " field type");
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static <T> Optional<Predicate<T>> createNumericTermPredicate(final ExpressionTerm term,
                                                                         final ValueFunctionFactory<T> valueFunctionFactory) {
        final Function<T, BigDecimal> numExtractor = valueFunctionFactory.createNumberExtractor();
        return switch (term.getCondition()) {
            case EQUALS -> NumericEquals.create(term, numExtractor);
            case NOT_EQUALS -> NumericNotEquals.create(term, numExtractor);
            case GREATER_THAN -> NumericGreaterThan.create(term, numExtractor);
            case GREATER_THAN_OR_EQUAL_TO -> NumericGreaterThanOrEqualTo.create(term, numExtractor);
            case LESS_THAN -> NumericLessThan.create(term, numExtractor);
            case LESS_THAN_OR_EQUAL_TO -> NumericLessThanOrEqualTo.create(term, numExtractor);
            case BETWEEN -> NumericBetween.create(term, numExtractor);
            case IN -> NumericIn.create(term, numExtractor);
            default -> throw new MatchException("Unexpected condition '" +
                    term.getCondition().getDisplayValue() +
                    "' for " +
                    valueFunctionFactory.getFieldType() +
                    " field type");
        };
    }


    private static BigDecimal getTermNumber(final ExpressionTerm term,
                                            final String value) {
        try {
            return new BigDecimal(value);
        } catch (final NumberFormatException e) {
            throw new MatchException(
                    "Expected a numeric value for field \"" + term.getField() +
                            "\" but was given string \"" + value + "\"");
        }
    }

    private static long getTermDate(final ExpressionTerm term,
                                    final Object value,
                                    final DateTimeSettings dateTimeSettings) {
        if (value instanceof final String valueStr) {
            try {
                final Optional<ZonedDateTime> optionalZonedDateTime =
                        DateExpressionParser.parse(valueStr, dateTimeSettings);
                final ZonedDateTime zonedDateTime = optionalZonedDateTime.orElseThrow(() ->
                        new NumberFormatException("Unexpected: " + valueStr));
                return zonedDateTime.toInstant().toEpochMilli();
            } catch (final NumberFormatException e) {
                throw new MatchException(
                        "Unable to parse a date/time from value for field \"" +
                                term.getField() +
                                "\" but was given string \"" +
                                value +
                                "\"");
            }
        } else {
            throw new MatchException(
                    "Expected a string value for field \"" +
                            term.getField() +
                            "\" but was given \"" +
                            value +
                            "\" of type " +
                            value.getClass().getName());
        }
    }

    private static BigDecimal[] getTermNumbers(final ExpressionTerm term,
                                               final Object value) {
        final String[] values = value.toString().split(DELIMITER);
        final BigDecimal[] numbers = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getTermNumber(term, values[i].trim());
        }

        return numbers;
    }

    private static long[] getTermDates(final ExpressionTerm term,
                                       final Object value,
                                       final DateTimeSettings dateTimeSettings) {
        final String[] values = value.toString().split(DELIMITER);
        final long[] dates = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            dates[i] = getTermDate(term, values[i].trim(), dateTimeSettings);
        }

        return dates;
    }

    private static class MatchException extends RuntimeException {

        MatchException(final String message) {
            super(message);
        }
    }

    public interface ValueFunctionFactories<T> {

        ValueFunctionFactory<T> get(String fieldName);
    }

    public interface ValueFunctionFactory<T> {

        Function<T, Boolean> createNullCheck();

        Function<T, String> createStringExtractor();

        Function<T, Long> createDateExtractor();

        Function<T, BigDecimal> createNumberExtractor();

        FieldType getFieldType();
    }

    private static class AndPredicate<T> implements Predicate<T> {

        private final List<Predicate<T>> subPredicates;

        public AndPredicate(final List<Predicate<T>> subPredicates) {
            this.subPredicates = subPredicates;
        }

        private static <T> Optional<Predicate<T>> create(final List<Predicate<T>> subPredicates) {
            return Optional.of(new AndPredicate<>(subPredicates));
        }

        @Override
        public boolean test(final T values) {
            for (final Predicate<T> valuesPredicate : subPredicates) {
                if (!valuesPredicate.test(values)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class OrPredicate<T> implements Predicate<T> {

        private final List<Predicate<T>> subPredicates;

        public OrPredicate(final List<Predicate<T>> subPredicates) {
            this.subPredicates = subPredicates;
        }

        private static <T> Optional<Predicate<T>> create(final List<Predicate<T>> subPredicates) {
            return Optional.of(new OrPredicate<>(subPredicates));
        }

        @Override
        public boolean test(final T values) {
            for (final Predicate<T> valuesPredicate : subPredicates) {
                if (valuesPredicate.test(values)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class NotPredicate<T> implements Predicate<T> {

        private final Predicate<T> subPredicate;

        public NotPredicate(final Predicate<T> subPredicate) {
            this.subPredicate = subPredicate;
        }

        private static <T> Optional<Predicate<T>> create(final Predicate<T> subPredicate) {
            return Optional.of(new NotPredicate<>(subPredicate));
        }

        @Override
        public boolean test(final T values) {
            return !subPredicate.test(values);
        }
    }

    private abstract static class ExpressionTermPredicate<T> implements Predicate<T> {

        final ExpressionTerm term;

        private ExpressionTermPredicate(final ExpressionTerm term) {
            this.term = term;
        }

        @Override
        public String toString() {
            return term.toString();
        }
    }

    private static class IsNullPredicate<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Boolean> nullCheckFunction;

        private IsNullPredicate(final ExpressionTerm term,
                                final Function<T, Boolean> nullCheckFunction) {
            super(term);
            this.nullCheckFunction = nullCheckFunction;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Boolean> nullCheckFunction) {
            return Optional.of(new IsNullPredicate<>(term, nullCheckFunction));
        }

        @Override
        public boolean test(final T values) {
            return nullCheckFunction.apply(values);
        }
    }

    private static class IsNotNullPredicate<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Boolean> nullCheckFunction;

        private IsNotNullPredicate(final ExpressionTerm term,
                                   final Function<T, Boolean> nullCheckFunction) {
            super(term);
            this.nullCheckFunction = nullCheckFunction;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Boolean> nullCheckFunction) {
            return Optional.of(new IsNotNullPredicate<>(term, nullCheckFunction));
        }

        @Override
        public boolean test(final T values) {
            return !nullCheckFunction.apply(values);
        }
    }

    private abstract static class NumericExpressionTermPredicate<T> extends ExpressionTermPredicate<T> {

        final BigDecimal termNum;
        final Function<T, BigDecimal> extractionFunction;

        private NumericExpressionTermPredicate(final ExpressionTerm term,
                                               final Function<T, BigDecimal> extractionFunction) {
            super(term);
            termNum = getTermNumber(term, term.getValue());
            this.extractionFunction = extractionFunction;
        }
    }

    private static class NumericEquals<T> extends NumericExpressionTermPredicate<T> {

        private NumericEquals(final ExpressionTerm term,
                              final Function<T, BigDecimal> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            return Optional.of(new NumericEquals<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            return Objects.equals(val, termNum);
        }
    }

    private static class NumericNotEquals<T> extends NumericExpressionTermPredicate<T> {

        private NumericNotEquals(final ExpressionTerm term,
                                 final Function<T, BigDecimal> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            return Optional.of(new NumericNotEquals<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            return !Objects.equals(val, termNum);
        }
    }

    private static class NumericGreaterThan<T> extends NumericExpressionTermPredicate<T> {

        private NumericGreaterThan(final ExpressionTerm term,
                                   final Function<T, BigDecimal> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            return Optional.of(new NumericGreaterThan<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareBigDecimal(val, termNum);
            return compVal > 0;
        }
    }

    private static class NumericGreaterThanOrEqualTo<T> extends NumericExpressionTermPredicate<T> {

        private NumericGreaterThanOrEqualTo(final ExpressionTerm term,
                                            final Function<T, BigDecimal> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            return Optional.of(new NumericGreaterThanOrEqualTo<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareBigDecimal(val, termNum);
            return compVal >= 0;
        }
    }

    private static class NumericLessThan<T> extends NumericExpressionTermPredicate<T> {

        private NumericLessThan(final ExpressionTerm term,
                                final Function<T, BigDecimal> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            return Optional.of(new NumericLessThan<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareBigDecimal(val, termNum);
            return compVal < 0;
        }
    }

    private static class NumericLessThanOrEqualTo<T> extends NumericExpressionTermPredicate<T> {

        private NumericLessThanOrEqualTo(final ExpressionTerm term,
                                         final Function<T, BigDecimal> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            return Optional.of(new NumericLessThanOrEqualTo<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareBigDecimal(val, termNum);
            return compVal <= 0;
        }
    }

    private static class NumericBetween<T> extends ExpressionTermPredicate<T> {

        private final BigDecimal[] between;
        private final Function<T, BigDecimal> extractionFunction;

        private NumericBetween(final ExpressionTerm term,
                               final Function<T, BigDecimal> extractionFunction,
                               final BigDecimal[] between) {
            super(term);
            this.between = between;
            this.extractionFunction = extractionFunction;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            final BigDecimal[] between = getTermNumbers(term, term.getValue());
            if (between.length != 2) {
                throw new MatchException("2 numbers needed for between query");
            }
            if (CompareUtil.compareBigDecimal(between[0], between[1]) >= 0) {
                throw new MatchException("From number must be lower than to number");
            }
            return Optional.of(new NumericBetween<>(term, extractionFunction, between));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            return CompareUtil.compareBigDecimal(val, between[0]) >= 0
                    && CompareUtil.compareBigDecimal(val, between[1]) <= 0;
        }
    }

    private static class NumericIn<T> extends ExpressionTermPredicate<T> {

        private final BigDecimal[] in;
        private final Function<T, BigDecimal> extractionFunction;

        private NumericIn(final ExpressionTerm term,
                          final Function<T, BigDecimal> extractionFunction,
                          final BigDecimal[] in) {
            super(term);
            this.in = in;
            this.extractionFunction = extractionFunction;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, BigDecimal> extractionFunction) {
            final BigDecimal[] in = getTermNumbers(term, term.getValue());
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new NumericIn<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final BigDecimal val = extractionFunction.apply(values);
            for (final BigDecimal n : in) {
                if (Objects.equals(n, val)) {
                    return true;
                }
            }
            return false;
        }
    }


    private abstract static class DateExpressionTermPredicate<T> extends ExpressionTermPredicate<T> {

        final Long termNum;
        final Function<T, Long> extractionFunction;

        private DateExpressionTermPredicate(final ExpressionTerm term,
                                            final Function<T, Long> extractionFunction,
                                            final DateTimeSettings dateTimeSettings) {
            super(term);
            termNum = getTermDate(term, term.getValue(), dateTimeSettings);
            this.extractionFunction = extractionFunction;
        }
    }

    private static class DateEquals<T> extends DateExpressionTermPredicate<T> {

        private DateEquals(final ExpressionTerm term,
                           final Function<T, Long> extractionFunction,
                           final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateEquals<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return Objects.equals(val, termNum);
        }
    }

    private static class DateNotEquals<T> extends DateExpressionTermPredicate<T> {

        private DateNotEquals(final ExpressionTerm term,
                              final Function<T, Long> extractionFunction,
                              final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateNotEquals<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return !Objects.equals(val, termNum);
        }
    }

    private static class DateGreaterThan<T> extends DateExpressionTermPredicate<T> {

        private DateGreaterThan(final ExpressionTerm term,
                                final Function<T, Long> extractionFunction,
                                final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateGreaterThan<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return CompareUtil.compareLong(val, termNum) > 0;
        }
    }

    private static class DateGreaterThanOrEqualTo<T> extends DateExpressionTermPredicate<T> {

        private DateGreaterThanOrEqualTo(final ExpressionTerm term,
                                         final Function<T, Long> extractionFunction,
                                         final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateGreaterThanOrEqualTo<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return CompareUtil.compareLong(val, termNum) >= 0;
        }
    }

    private static class DateLessThan<T> extends DateExpressionTermPredicate<T> {

        private DateLessThan(final ExpressionTerm term,
                             final Function<T, Long> extractionFunction,
                             final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateLessThan<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return CompareUtil.compareLong(val, termNum) < 0;
        }
    }

    private static class DateLessThanOrEqualTo<T> extends DateExpressionTermPredicate<T> {

        private DateLessThanOrEqualTo(final ExpressionTerm term,
                                      final Function<T, Long> extractionFunction,
                                      final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateLessThanOrEqualTo<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return CompareUtil.compareLong(val, termNum) <= 0;
        }
    }

    private static class DateBetween<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Long> extractionFunction;
        private final long[] between;

        private DateBetween(final ExpressionTerm term,
                            final Function<T, Long> extractionFunction,
                            final long[] between) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.between = between;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            final long[] between = getTermDates(term, term.getValue(), dateTimeSettings);
            if (between.length != 2) {
                throw new MatchException("2 numbers needed for between query");
            }
            if (CompareUtil.compareLong(between[0], between[1]) >= 0) {
                throw new MatchException("From number must be lower than to number");
            }
            return Optional.of(new DateBetween<>(term, extractionFunction, between));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return CompareUtil.compareLong(val, between[0]) >= 0
                    && CompareUtil.compareLong(val, between[1]) <= 0;
        }
    }

    private static class DateIn<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Long> extractionFunction;
        private final long[] in;

        private DateIn(final ExpressionTerm term,
                       final Function<T, Long> extractionFunction,
                       final long[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings) {
            final long[] in = getTermDates(term, term.getValue(), dateTimeSettings);
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new DateIn<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            for (final long n : in) {
                if (Objects.equals(n, val)) {
                    return true;
                }
            }
            return false;
        }
    }


    private abstract static class StringExpressionTermPredicate<T> extends ExpressionTermPredicate<T> {

        final String string;
        final Function<T, String> extractionFunction;

        private StringExpressionTermPredicate(final ExpressionTerm term,
                                              final Function<T, String> extractionFunction) {
            super(term);
            string = term.getValue();
            this.extractionFunction = extractionFunction;
        }
    }

    private static class StringEquals<T> extends StringExpressionTermPredicate<T> {

        private StringEquals(final ExpressionTerm term,
                             final Function<T, String> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringEquals<>(term, extractionFunction));
            } else {
                return Optional.of(new StringEqualsIgnoreCase<>(term, extractionFunction));
            }
        }

        @Override
        public boolean test(final T values) {
            return string.equals(extractionFunction.apply(values));
        }
    }

    private static class StringEqualsIgnoreCase<T> extends StringExpressionTermPredicate<T> {

        private StringEqualsIgnoreCase(final ExpressionTerm term,
                                       final Function<T, String> extractionFunction) {
            super(term, extractionFunction);
        }

        @Override
        public boolean test(final T values) {
            return string.equalsIgnoreCase(extractionFunction.apply(values));
        }
    }

    private static class StringNotEquals<T> extends StringExpressionTermPredicate<T> {

        private final Predicate<T> predicate;

        private StringNotEquals(final ExpressionTerm term,
                                final Function<T, String> extractionFunction,
                                final Predicate<T> predicate) {
            super(term, extractionFunction);
            this.predicate = predicate;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            final Optional<Predicate<T>> predicate = StringEquals.create(term, extractionFunction);
            return predicate.map(valuesPredicate -> new StringNotEquals<>(term, extractionFunction, valuesPredicate));
        }

        @Override
        public boolean test(final T values) {
            return !predicate.test(values);
        }
    }

    private static class StringContains<T> extends StringExpressionTermPredicate<T> {

        private StringContains(final ExpressionTerm term,
                               final Function<T, String> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringContains<>(term, extractionFunction));
            } else {
                return Optional.of(new StringContainsIgnoreCase<>(term, extractionFunction));
            }
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.contains(string);
        }
    }

    private static class StringContainsIgnoreCase<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringContainsIgnoreCase(final ExpressionTerm term,
                                         final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.toLowerCase(Locale.ROOT).contains(string);
        }
    }

    private static class StringStartsWith<T> extends StringExpressionTermPredicate<T> {

        private StringStartsWith(final ExpressionTerm term,
                                 final Function<T, String> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringStartsWith<>(term, extractionFunction));
            } else {
                return Optional.of(new StringStartsWithIgnoreCase<>(term, extractionFunction));
            }
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.startsWith(string);
        }
    }

    private static class StringStartsWithIgnoreCase<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringStartsWithIgnoreCase(final ExpressionTerm term,
                                           final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.toLowerCase(Locale.ROOT).startsWith(string);
        }
    }

    private static class StringEndsWith<T> extends StringExpressionTermPredicate<T> {

        private StringEndsWith(final ExpressionTerm term,
                               final Function<T, String> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringEndsWith<>(term, extractionFunction));
            } else {
                return Optional.of(new StringEndsWithIgnoreCase<>(term, extractionFunction));
            }
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.endsWith(string);
        }
    }

    private static class StringEndsWithIgnoreCase<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringEndsWithIgnoreCase(final ExpressionTerm term,
                                         final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.toLowerCase(Locale.ROOT).endsWith(string);
        }
    }

    private static class StringRegex<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final Pattern pattern;

        private StringRegex(final ExpressionTerm term,
                            final Function<T, String> extractionFunction,
                            final Pattern pattern) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.pattern = pattern;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }
            int flags = 0;
            if (!term.isCaseSensitive()) {
                flags = Pattern.CASE_INSENSITIVE;
            }
            final Pattern pattern = Pattern.compile(term.getValue(), flags);
            return Optional.of(new StringRegex<>(term, extractionFunction, pattern));
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return pattern.matcher(val).find();
        }
    }

    private static class StringWordBoundary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final Predicate<String> predicate;

        private StringWordBoundary(final ExpressionTerm term,
                                   final Function<T, String> extractionFunction,
                                   final Predicate<String> predicate) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.predicate = predicate;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }
            final Predicate<String> predicate = StringPredicateFactory.createWordBoundaryPredicate(term.getValue());
            return Optional.of(new StringWordBoundary<>(term, extractionFunction, predicate));
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return predicate.test(val);
        }
    }

    private static class StringIn<T> extends ExpressionTermPredicate<T> {

        private final List<Predicate<T>> subPredicates;

        private StringIn(final ExpressionTerm term,
                         final List<Predicate<T>> subPredicates) {
            super(term);
            this.subPredicates = subPredicates;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            final String[] in = term.getValue().split(" ");
            final List<Predicate<T>> subPredicates = new ArrayList<>(in.length);
            for (final String str : in) {
                final ExpressionTerm subTerm = ExpressionTerm
                        .builder()
                        .field(term.getField())
                        .condition(Condition.EQUALS)
                        .value(str)
                        .caseSensitive(term.getCaseSensitive())
                        .build();
                final Optional<Predicate<T>> subPredicate = StringEquals.create(subTerm, extractionFunction);
                subPredicate.ifPresent(subPredicates::add);
            }
            if (subPredicates.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new StringIn<>(term, subPredicates));
        }

        @Override
        public boolean test(final T values) {
            for (final Predicate<T> predicate : subPredicates) {
                if (predicate.test(values)) {
                    return true;
                }
            }
            return false;
        }
    }
}
