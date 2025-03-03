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
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.NullSafe;
import stroom.util.filter.StringPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CompareUtil;

import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ExpressionPredicateFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExpressionPredicateFactory.class);
    private static final String DELIMITER = ",";

    private final WordListProvider wordListProvider;

    @Inject
    public ExpressionPredicateFactory(final WordListProvider wordListProvider) {
        this.wordListProvider = wordListProvider;
    }

    public <T> Optional<Predicate<T>> create(final ExpressionOperator operator,
                                             final ValueFunctionFactories<T> queryFieldIndex,
                                             final DateTimeSettings dateTimeSettings) {
        if (operator == null) {
            return Optional.empty();
        }

        return createPredicate(operator, queryFieldIndex, dateTimeSettings, wordListProvider);
    }

    private <T> Optional<Predicate<T>> createPredicate(final ExpressionItem item,
                                                       final ValueFunctionFactories<T> queryFieldIndex,
                                                       final DateTimeSettings dateTimeSettings,
                                                       final WordListProvider wordListProvider) {
        if (!item.enabled()) {
            return Optional.empty();
        }

        if (item instanceof final ExpressionOperator expressionOperator) {
            return createOperatorPredicate(
                    expressionOperator,
                    queryFieldIndex,
                    dateTimeSettings,
                    wordListProvider);
        } else if (item instanceof final ExpressionTerm expressionTerm) {
            return createTermPredicate(
                    expressionTerm,
                    queryFieldIndex,
                    dateTimeSettings,
                    wordListProvider);
        } else {
            throw new MatchException("Unexpected item type");
        }
    }

    private <T> Optional<Predicate<T>> createOperatorPredicate(final ExpressionOperator operator,
                                                               final ValueFunctionFactories<T> queryFieldIndex,
                                                               final DateTimeSettings dateTimeSettings,
                                                               final WordListProvider wordListProvider) {
        // If the operator is not enabled then ignore this branch.
        if (!operator.enabled()) {
            return Optional.empty();
        }

        // Create child predicates.
        final List<Predicate<T>> predicates;
        if (operator.getChildren() != null && !operator.getChildren().isEmpty()) {
            predicates = new ArrayList<>(operator.getChildren().size());
            for (final ExpressionItem child : operator.getChildren()) {
                Optional<Predicate<T>> optional = createPredicate(
                        child,
                        queryFieldIndex,
                        dateTimeSettings,
                        wordListProvider);
                optional.ifPresent(predicates::add);
            }
        } else {
            predicates = Collections.emptyList();
        }

        return switch (operator.op()) {
            case AND -> {
                if (predicates.isEmpty()) {
                    yield Optional.empty();
                }
                if (predicates.size() == 1) {
                    yield Optional.of(predicates.getFirst());
                }
                yield AndPredicate.create(predicates);
            }
            case OR -> {
                if (predicates.isEmpty()) {
                    yield Optional.empty();
                }
                if (predicates.size() == 1) {
                    yield Optional.of(predicates.getFirst());
                }
                yield OrPredicate.create(predicates);
            }
            case NOT -> {
                if (predicates.size() > 1) {
                    throw new MatchException("Unexpected number of child terms in NOT");
                }
                if (predicates.isEmpty()) {
                    yield Optional.of(t -> false);
                }
                yield NotPredicate.create(predicates.getFirst());
            }
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<Predicate<T>> createTermPredicate(final ExpressionTerm term,
                                                           final ValueFunctionFactories<T> valueFunctionFactories,
                                                           final DateTimeSettings dateTimeSettings,
                                                           final WordListProvider wordListProvider) {
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
            return NotPredicate.create(IsNullPredicate.create(term, valueFunctionFactory.createNullCheck()));
        }

        // Create a query based on the field type and condition.
        final FieldType fieldType = valueFunctionFactory.getFieldType();
        if (FieldType.DATE.equals(fieldType)) {
            return createDateTermPredicate(
                    term,
                    valueFunctionFactory,
                    dateTimeSettings,
                    wordListProvider);
        } else if (fieldType.isNumeric()) {
            return createNumericTermPredicate(
                    term,
                    valueFunctionFactory,
                    wordListProvider);
        } else {
            return createGeneralTermPredicate(
                    term,
                    valueFunctionFactory,
                    dateTimeSettings,
                    wordListProvider);
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<Predicate<T>> createGeneralTermPredicate(final ExpressionTerm term,
                                                                  final ValueFunctionFactory<T> valueFunctionFactory,
                                                                  final DateTimeSettings dateTimeSettings,
                                                                  final WordListProvider wordListProvider) {
        try {
            return createDateTermPredicate(term, valueFunctionFactory, dateTimeSettings, wordListProvider);
        } catch (final RuntimeException e) {
            try {
                return createNumericTermPredicate(term, valueFunctionFactory, wordListProvider);
            } catch (final RuntimeException e2) {
                final Function<T, String> stringExtractor = valueFunctionFactory.createStringExtractor();
                return switch (term.getCondition()) {
                    case EQUALS -> StringEquals.create(term, stringExtractor);
                    case EQUALS_CASE_SENSITIVE -> StringEqualsCaseSensitive.create(term, stringExtractor);
                    case NOT_EQUALS -> NotPredicate.create(StringEquals.create(term, stringExtractor));
                    case CONTAINS -> StringContains.create(term, stringExtractor);
                    case CONTAINS_CASE_SENSITIVE -> StringContainsCaseSensitive.create(term, stringExtractor);
                    case STARTS_WITH -> StringStartsWith.create(term, stringExtractor);
                    case STARTS_WITH_CASE_SENSITIVE -> StringStartsWithCaseSensitive.create(term, stringExtractor);
                    case ENDS_WITH -> StringEndsWith.create(term, stringExtractor);
                    case ENDS_WITH_CASE_SENSITIVE -> StringEndsWithCaseSensitive.create(term, stringExtractor);
                    case MATCHES_REGEX -> StringRegex.create(term, stringExtractor);
                    case MATCHES_REGEX_CASE_SENSITIVE -> StringRegexCaseSensitive.create(term, stringExtractor);
                    case WORD_BOUNDARY -> StringWordBoundary.create(term, stringExtractor);
                    case IN -> StringIn.create(term, stringExtractor);
                    case IN_DICTIONARY -> StringInDictionary.create(term, stringExtractor, wordListProvider);
                    default -> throw e2;
                };
            }
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<Predicate<T>> createDateTermPredicate(final ExpressionTerm term,
                                                               final ValueFunctionFactory<T> valueFunctionFactory,
                                                               final DateTimeSettings dateTimeSettings,
                                                               final WordListProvider wordListProvider) {
        final Function<T, Long> dateExtractor = valueFunctionFactory.createDateExtractor();
        return switch (term.getCondition()) {
            case EQUALS -> DateEquals.create(term, dateExtractor, dateTimeSettings);
            case NOT_EQUALS -> NotPredicate.create(DateEquals.create(term, dateExtractor, dateTimeSettings));
            case GREATER_THAN -> DateGreaterThan.create(term, dateExtractor, dateTimeSettings);
            case GREATER_THAN_OR_EQUAL_TO -> DateGreaterThanOrEqualTo.create(term, dateExtractor, dateTimeSettings);
            case LESS_THAN -> DateLessThan.create(term, dateExtractor, dateTimeSettings);
            case LESS_THAN_OR_EQUAL_TO -> DateLessThanOrEqualTo.create(term, dateExtractor, dateTimeSettings);
            case BETWEEN -> DateBetween.create(term, dateExtractor, dateTimeSettings);
            case IN -> DateIn.create(term, dateExtractor, dateTimeSettings);
            case IN_DICTIONARY -> DateInDictionary.create(term, dateExtractor, dateTimeSettings, wordListProvider);
            default -> throw new MatchException("Unexpected condition '" +
                                                term.getCondition().getDisplayValue() +
                                                "' for " +
                                                valueFunctionFactory.getFieldType() +
                                                " field type");
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<Predicate<T>> createNumericTermPredicate(final ExpressionTerm term,
                                                                  final ValueFunctionFactory<T> valueFunctionFactory,
                                                                  final WordListProvider wordListProvider) {
        final Function<T, Double> numExtractor = valueFunctionFactory.createNumberExtractor();
        return switch (term.getCondition()) {
            case EQUALS -> NumericEquals.create(term, numExtractor);
            case NOT_EQUALS -> NotPredicate.create(NumericEquals.create(term, numExtractor));
            case GREATER_THAN -> NumericGreaterThan.create(term, numExtractor);
            case GREATER_THAN_OR_EQUAL_TO -> NumericGreaterThanOrEqualTo.create(term, numExtractor);
            case LESS_THAN -> NumericLessThan.create(term, numExtractor);
            case LESS_THAN_OR_EQUAL_TO -> NumericLessThanOrEqualTo.create(term, numExtractor);
            case BETWEEN -> NumericBetween.create(term, numExtractor);
            case IN -> NumericIn.create(term, numExtractor);
            case IN_DICTIONARY -> NumericInDictionary.create(term, numExtractor, wordListProvider);
            default -> throw new MatchException("Unexpected condition '" +
                                                term.getCondition().getDisplayValue() +
                                                "' for " +
                                                valueFunctionFactory.getFieldType() +
                                                " field type");
        };
    }


    private static Double getTermNumber(final ExpressionTerm term,
                                        final String value) {
        try {
            return new BigDecimal(value).doubleValue();
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

    private static Double[] getTermNumbers(final ExpressionTerm term,
                                           final Object value) {
        final String[] values = value.toString().split(DELIMITER);
        final Double[] numbers = new Double[values.length];
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

        Function<T, Double> createNumberExtractor();

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

        private static <T> Optional<Predicate<T>> create(final Optional<Predicate<T>> subPredicate) {
            return subPredicate.map(NotPredicate::new);
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

    private abstract static class NumericExpressionTermPredicate<T> extends ExpressionTermPredicate<T> {

        final Double termNum;
        final Function<T, Double> extractionFunction;

        private NumericExpressionTermPredicate(final ExpressionTerm term,
                                               final Function<T, Double> extractionFunction) {
            super(term);
            termNum = getTermNumber(term, term.getValue());
            this.extractionFunction = extractionFunction;
        }
    }

    private static <T> Optional<Predicate<T>> ifValue(final ExpressionTerm term,
                                                      final Supplier<Predicate<T>> supplier) {
        if (NullSafe.isBlankString(term.getValue())) {
            return Optional.empty();
        }
        return Optional.of(supplier.get());
    }

    private static class NumericEquals<T> extends NumericExpressionTermPredicate<T> {

        private NumericEquals(final ExpressionTerm term,
                              final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericEquals<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            try {
                final Double val = extractionFunction.apply(values);
                return Objects.equals(val, termNum);
            } catch (final RuntimeException e) {
                return false;
            }
        }
    }

    private static class NumericGreaterThan<T> extends NumericExpressionTermPredicate<T> {

        private NumericGreaterThan(final ExpressionTerm term,
                                   final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericGreaterThan<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal > 0;
        }
    }

    private static class NumericGreaterThanOrEqualTo<T> extends NumericExpressionTermPredicate<T> {

        private NumericGreaterThanOrEqualTo(final ExpressionTerm term,
                                            final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericGreaterThanOrEqualTo<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal >= 0;
        }
    }

    private static class NumericLessThan<T> extends NumericExpressionTermPredicate<T> {

        private NumericLessThan(final ExpressionTerm term,
                                final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericLessThan<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal < 0;
        }
    }

    private static class NumericLessThanOrEqualTo<T> extends NumericExpressionTermPredicate<T> {

        private NumericLessThanOrEqualTo(final ExpressionTerm term,
                                         final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericLessThanOrEqualTo<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal <= 0;
        }
    }

    private static class NumericBetween<T> extends ExpressionTermPredicate<T> {

        private final Double[] between;
        private final Function<T, Double> extractionFunction;

        private NumericBetween(final ExpressionTerm term,
                               final Function<T, Double> extractionFunction,
                               final Double[] between) {
            super(term);
            this.between = between;
            this.extractionFunction = extractionFunction;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> {
                final Double[] between = getTermNumbers(term, term.getValue());
                if (between.length != 2) {
                    throw new MatchException("2 numbers needed for between query");
                }
                if (CompareUtil.compareDouble(between[0], between[1]) >= 0) {
                    throw new MatchException("From number must be lower than to number");
                }
                return new NumericBetween<>(term, extractionFunction, between);
            });
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            return CompareUtil.compareDouble(val, between[0]) >= 0
                   && CompareUtil.compareDouble(val, between[1]) <= 0;
        }
    }

    private static class NumericIn<T> extends ExpressionTermPredicate<T> {

        private final Double[] in;
        private final Function<T, Double> extractionFunction;

        private NumericIn(final ExpressionTerm term,
                          final Function<T, Double> extractionFunction,
                          final Double[] in) {
            super(term);
            this.in = in;
            this.extractionFunction = extractionFunction;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction) {
            final Double[] in = getTermNumbers(term, term.getValue());
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new NumericIn<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            for (final Double n : in) {
                if (Objects.equals(n, val)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class NumericInDictionary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Double> extractionFunction;
        private final Double[] in;

        private NumericInDictionary(final ExpressionTerm term,
                                    final Function<T, Double> extractionFunction,
                                    final Double[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Double> extractionFunction,
                                                         final WordListProvider wordListProvider) {
            final String[] words;
            if (term.getDocRef() != null) {
                words = wordListProvider.getWords(term.getDocRef());
            } else {
                words = loadDictionary(wordListProvider, term.getValue());
            }
            if (words.length == 0) {
                return Optional.of(values -> false);
            }

            final Double[] in = new Double[words.length];
            for (int i = 0; i < words.length; i++) {
                final String word = words[i];
                in[i] = getTermNumber(term, word);
            }
            return Optional.of(new NumericInDictionary<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            for (final Double n : in) {
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
            return ifValue(term, () -> new DateEquals<>(term, extractionFunction, dateTimeSettings));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return Objects.equals(val, termNum);
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
            return ifValue(term, () -> new DateGreaterThan<>(term, extractionFunction, dateTimeSettings));
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
            return ifValue(term, () -> new DateGreaterThanOrEqualTo<>(term, extractionFunction, dateTimeSettings));
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
            return ifValue(term, () -> new DateLessThan<>(term, extractionFunction, dateTimeSettings));
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
            return ifValue(term, () -> new DateLessThanOrEqualTo<>(term, extractionFunction, dateTimeSettings));
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
            return ifValue(term, () -> {
                final long[] between = getTermDates(term, term.getValue(), dateTimeSettings);
                if (between.length != 2) {
                    throw new MatchException("2 numbers needed for between query");
                }
                if (CompareUtil.compareLong(between[0], between[1]) >= 0) {
                    throw new MatchException("From number must be lower than to number");
                }
                return new DateBetween<>(term, extractionFunction, between);
            });
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

    private static class DateInDictionary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Long> extractionFunction;
        private final long[] in;

        private DateInDictionary(final ExpressionTerm term,
                                 final Function<T, Long> extractionFunction,
                                 final long[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, Long> extractionFunction,
                                                         final DateTimeSettings dateTimeSettings,
                                                         final WordListProvider wordListProvider) {
            final String[] words;
            if (term.getDocRef() != null) {
                words = wordListProvider.getWords(term.getDocRef());
            } else {
                words = loadDictionary(wordListProvider, term.getValue());
            }
            if (words.length == 0) {
                return Optional.of(values -> false);
            }

            final long[] in = new long[words.length];
            for (int i = 0; i < words.length; i++) {
                final String word = words[i];
                in[i] = getTermDate(term, word, dateTimeSettings);
            }
            return Optional.of(new DateInDictionary<>(term, extractionFunction, in));
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

        final String value;
        final Function<T, String> extractionFunction;

        private StringExpressionTermPredicate(final ExpressionTerm term,
                                              final String value,
                                              final Function<T, String> extractionFunction) {
            super(term);
            this.value = value;
            this.extractionFunction = extractionFunction;
        }
    }

    private static class StringEquals<T> extends StringExpressionTermPredicate<T> {

        private StringEquals(final ExpressionTerm term,
                             final String value,
                             final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard equals.
                final String replaced = makePattern(term.getValue());
                if (!Objects.equals(term.getValue(), replaced)) {
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(replaced, Pattern.CASE_INSENSITIVE));
                }

                return new StringEquals<T>(term, term.getValue(), extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            return value.equalsIgnoreCase(extractionFunction.apply(values));
        }
    }

    private static class StringEqualsCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringEqualsCaseSensitive(final ExpressionTerm term,
                                          final String value,
                                          final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard equals.
                final String replaced = makePattern(term.getValue());
                if (!Objects.equals(term.getValue(), replaced)) {
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(replaced));
                }

                return new StringEqualsCaseSensitive<T>(term, term.getValue(), extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            return value.equals(extractionFunction.apply(values));
        }
    }

    private static class StringContains<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String value;

        private StringContains(final ExpressionTerm term,
                               final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            value = term.getValue().toLowerCase();
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard contains.
                final String replaced = makePattern(term.getValue());
                if (!Objects.equals(term.getValue(), replaced)) {
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(".*" + replaced + ".*", Pattern.CASE_INSENSITIVE));
                }

                return new StringContains<>(term, extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.toLowerCase(Locale.ROOT).contains(value);
        }
    }

    private static class StringContainsCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringContainsCaseSensitive(final ExpressionTerm term,
                                            final Function<T, String> extractionFunction) {
            super(term, term.getValue(), extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard contains.
                final String replaced = makePattern(term.getValue());
                if (!Objects.equals(term.getValue(), replaced)) {
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(".*" + replaced + ".*"));
                }

                return new StringContainsCaseSensitive<>(term, extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.contains(value);
        }
    }


    private static class StringStartsWith<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringStartsWith(final ExpressionTerm term,
                                 final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringStartsWith<>(term, extractionFunction));
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

    private static class StringStartsWithCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringStartsWithCaseSensitive(final ExpressionTerm term,
                                              final Function<T, String> extractionFunction) {
            super(term, term.getValue(), extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringStartsWithCaseSensitive<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.startsWith(value);
        }
    }

    private static class StringEndsWith<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringEndsWith(final ExpressionTerm term,
                               final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringEndsWith<>(term, extractionFunction));
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

    private static class StringEndsWithCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringEndsWithCaseSensitive(final ExpressionTerm term,
                                            final Function<T, String> extractionFunction) {
            super(term, term.getValue(), extractionFunction);
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringEndsWithCaseSensitive<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final String val = extractionFunction.apply(values);
            if (val == null) {
                return false;
            }
            return val.endsWith(value);
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
            return ifValue(term, () -> {
                final Pattern pattern = Pattern.compile(term.getValue(), Pattern.CASE_INSENSITIVE);
                return new StringRegex<>(term, extractionFunction, pattern);
            });
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

    private static class StringRegexCaseSensitive<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final Pattern pattern;

        private StringRegexCaseSensitive(final ExpressionTerm term,
                                         final Function<T, String> extractionFunction,
                                         final Pattern pattern) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.pattern = pattern;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                final Pattern pattern = Pattern.compile(term.getValue());
                return new StringRegexCaseSensitive<>(term, extractionFunction, pattern);
            });
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
            return ifValue(term, () -> {
                final Predicate<String> predicate = StringPredicateFactory.createWordBoundaryPredicate(term.getValue());
                return new StringWordBoundary<>(term, extractionFunction, predicate);
            });
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

        private final Function<T, String> extractionFunction;
        private final String[] in;

        private StringIn(final ExpressionTerm term,
                         final Function<T, String> extractionFunction,
                         final String[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction) {
            final String[] in = term.getValue().split(" ");
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new StringIn<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final String value = extractionFunction.apply(values);
            for (final String n : in) {
                if (Objects.equals(n, value)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class StringInDictionary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String[] in;

        private StringInDictionary(final ExpressionTerm term,
                                   final Function<T, String> extractionFunction,
                                   final String[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<Predicate<T>> create(final ExpressionTerm term,
                                                         final Function<T, String> extractionFunction,
                                                         final WordListProvider wordListProvider) {
            final String[] words;
            if (term.getDocRef() != null) {
                words = wordListProvider.getWords(term.getDocRef());
            } else {
                words = loadDictionary(wordListProvider, term.getValue());
            }

            // If there are no terms then always a false match.
            if (words.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new StringIn<>(term, extractionFunction, words));
        }

        @Override
        public boolean test(final T values) {
            final String value = extractionFunction.apply(values);
            for (final String n : in) {
                if (Objects.equals(n, value)) {
                    return true;
                }
            }
            return false;
        }
    }


    public static String makePattern(final String value) {
        int index = 0;
        final char[] chars = value.toCharArray();
        final char[] out = new char[chars.length * 2];
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (c == '\\') {
                if (i < chars.length - 1) {
                    final char c2 = chars[i + 1];
                    if (c2 == '*' || c2 == '?') {
                        out[index++] = c2;
                        i++;
                    } else {
                        out[index++] = c;
                    }
                } else {
                    out[index++] = c;
                }
            } else if (c == '*') {
                out[index++] = '.';
                out[index++] = c;
            } else if (c == '?') {
                out[index++] = '.';
            } else {
                out[index++] = c;
            }
        }
        return new String(out, 0, index);
    }

    private static String[] loadDictionary(final WordListProvider wordListProvider,
                                           final String name) {
        // Try by UUID
        final Optional<DocRef> optionalDocRef = wordListProvider.findByUuid(name);
        final DocRef docRef;
        if (optionalDocRef.isEmpty()) {
            LOGGER.debug(() -> "Unable to load dictionary by UUID '" + name + "'");

            // Try and load a dictionary with the supplied name.
            final List<DocRef> list = wordListProvider.findByName(name);

            if (list == null || list.isEmpty()) {
                final String message = "Dictionary not found with name '" + name
                                       + "'. You might not have permission to access this dictionary";
                LOGGER.debug(() -> message);
                throw new MatchException(message);

            } else {
                if (list.size() > 1) {
                    LOGGER.debug(() -> "Multiple dictionaries found with name '" + name
                                       + "' - using the first one that was created");
                }

                docRef = list.getFirst();
            }
        } else {
            docRef = optionalDocRef.get();
        }

        if (docRef == null) {
            throw new MatchException("Unable to find dictionary " + name);

        } else {
            return wordListProvider.getWords(docRef);
        }
    }
}
