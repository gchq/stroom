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
import stroom.datasource.api.v2.QueryField;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ExpressionPredicateBuilder {

    private static final String DELIMITER = ",";

    public static Optional<ValuesPredicate> create(final ExpressionOperator operator,
                                                   final QueryFieldIndex queryFieldIndex,
                                                   final DateTimeSettings dateTimeSettings) {
        if (operator == null) {
            return Optional.empty();
        }

        return createPredicate(operator, queryFieldIndex, dateTimeSettings);
    }

    private static Optional<ValuesPredicate> createPredicate(final ExpressionItem item,
                                                             final QueryFieldIndex queryFieldIndex,
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

    private static Optional<ValuesPredicate> createOperatorPredicate(final ExpressionOperator operator,
                                                                     final QueryFieldIndex queryFieldIndex,
                                                                     final DateTimeSettings dateTimeSettings) {
        if (!operator.enabled() || operator.getChildren() == null || operator.getChildren().isEmpty()) {
            return Optional.empty();
        }

        return switch (operator.op()) {
            case AND -> {
                final List<ValuesPredicate> predicates = new ArrayList<>(operator.getChildren().size());
                for (final ExpressionItem child : operator.getChildren()) {
                    Optional<ValuesPredicate> optional = createPredicate(child, queryFieldIndex, dateTimeSettings);
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
                final List<ValuesPredicate> predicates = new ArrayList<>(operator.getChildren().size());
                for (final ExpressionItem child : operator.getChildren()) {
                    Optional<ValuesPredicate> optional = createPredicate(child, queryFieldIndex, dateTimeSettings);
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
                final Optional<ValuesPredicate> optionalChildPredicate =
                        createPredicate(operator.getChildren().getFirst(), queryFieldIndex, dateTimeSettings);
                if (optionalChildPredicate.isEmpty()) {
                    yield Optional.empty();
                }
                yield NotPredicate.create(optionalChildPredicate.get());
            }
        };
    }

    private static Optional<ValuesPredicate> createTermPredicate(final ExpressionTerm term,
                                                                 final QueryFieldIndex queryFieldIndex,
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
        final QueryFieldPosition queryFieldPosition = queryFieldIndex.get(termField);
        if (queryFieldPosition == null) {
            throw new MatchException("QueryField not found: " + termField);
        }

        if (Condition.IS_NULL.equals(condition)) {
            return IsNullPredicate.create(term, queryFieldPosition);
        } else if (Condition.IS_NOT_NULL.equals(condition)) {
            return IsNotNullPredicate.create(term, queryFieldPosition);
        }

        // Try and resolve the term value.
        String termValue = term.getValue();
        if (NullSafe.isBlankString(termValue)) {
            throw new MatchException("Value not set");
        }

        // Create a query based on the field type and condition.
        final QueryField queryField = queryFieldPosition.queryField();
        final FieldType fieldType = queryField.getFldType();
        if (FieldType.DATE.equals(fieldType)) {
            return createDateTermPredicate(term, queryFieldPosition, dateTimeSettings);
        } else if (fieldType.isNumeric()) {
            return createNumericTermPredicate(term, queryFieldPosition);
        } else {
            return createGeneralTermPredicate(term, queryFieldPosition, dateTimeSettings);
        }
    }

    private static Optional<ValuesPredicate> createGeneralTermPredicate(final ExpressionTerm term,
                                                                        final QueryFieldPosition queryFieldPosition,
                                                                        final DateTimeSettings dateTimeSettings) {
        try {
            return createDateTermPredicate(term, queryFieldPosition, dateTimeSettings);
        } catch (final RuntimeException e) {
            try {
                return createNumericTermPredicate(term, queryFieldPosition);
            } catch (final RuntimeException e2) {
                return switch (term.getCondition()) {
                    case EQUALS -> StringEquals.create(term, queryFieldPosition);
                    case NOT_EQUALS -> StringNotEquals.create(term, queryFieldPosition);
                    case CONTAINS -> StringContains.create(term, queryFieldPosition);
                    case STARTS_WITH -> StringStartsWith.create(term, queryFieldPosition);
                    case ENDS_WITH -> StringEndsWith.create(term, queryFieldPosition);
                    case MATCHES_REGEX -> StringRegex.create(term, queryFieldPosition);
                    case WORD_BOUNDARY -> StringWordBoundary.create(term, queryFieldPosition);
                    case IN -> StringIn.create(term, queryFieldPosition);
                    default -> throw e2;
                };
            }
        }
    }

    private static Optional<ValuesPredicate> createDateTermPredicate(final ExpressionTerm term,
                                                                     final QueryFieldPosition queryFieldPosition,
                                                                     final DateTimeSettings dateTimeSettings) {
        return switch (term.getCondition()) {
            case EQUALS -> DateEquals.create(term, queryFieldPosition, dateTimeSettings);
            case NOT_EQUALS -> DateNotEquals.create(term, queryFieldPosition, dateTimeSettings);
            case GREATER_THAN -> DateGreaterThan.create(term, queryFieldPosition, dateTimeSettings);
            case GREATER_THAN_OR_EQUAL_TO ->
                    DateGreaterThanOrEqualTo.create(term, queryFieldPosition, dateTimeSettings);
            case LESS_THAN -> DateLessThan.create(term, queryFieldPosition, dateTimeSettings);
            case LESS_THAN_OR_EQUAL_TO -> DateLessThanOrEqualTo.create(term, queryFieldPosition, dateTimeSettings);
            case BETWEEN -> DateBetween.create(term, queryFieldPosition, dateTimeSettings);
            case IN -> DateIn.create(term, queryFieldPosition, dateTimeSettings);
            default -> throw new MatchException("Unexpected condition '" +
                    term.getCondition().getDisplayValue() +
                    "' for " +
                    queryFieldPosition +
                    " field type");
        };
    }

    private static Optional<ValuesPredicate> createNumericTermPredicate(final ExpressionTerm term,
                                                                        final QueryFieldPosition queryFieldPosition) {
        return switch (term.getCondition()) {
            case EQUALS -> NumericEquals.create(term, queryFieldPosition);
            case NOT_EQUALS -> NumericNotEquals.create(term, queryFieldPosition);
            case GREATER_THAN -> NumericGreaterThan.create(term, queryFieldPosition);
            case GREATER_THAN_OR_EQUAL_TO -> NumericGreaterThanOrEqualTo.create(term, queryFieldPosition);
            case LESS_THAN -> NumericLessThan.create(term, queryFieldPosition);
            case LESS_THAN_OR_EQUAL_TO -> NumericLessThanOrEqualTo.create(term, queryFieldPosition);
            case BETWEEN -> NumericBetween.create(term, queryFieldPosition);
            case IN -> NumericIn.create(term, queryFieldPosition);
            default -> throw new MatchException("Unexpected condition '" +
                    term.getCondition().getDisplayValue() +
                    "' for " +
                    queryFieldPosition +
                    " field type");
        };
    }


    private static BigDecimal getTermNumber(final QueryFieldPosition queryFieldPosition,
                                            final String value) {
        try {
            return new BigDecimal(value);
        } catch (final NumberFormatException e) {
            throw new MatchException(
                    "Expected a numeric value for field \"" + queryFieldPosition +
                            "\" but was given string \"" + value + "\"");
        }
    }

    private static long getTermDate(final QueryFieldPosition queryFieldPosition,
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
                                queryFieldPosition +
                                "\" but was given string \"" +
                                value +
                                "\"");
            }
        } else {
            throw new MatchException(
                    "Expected a string value for field \"" +
                            queryFieldPosition +
                            "\" but was given \"" +
                            value +
                            "\" of type " +
                            value.getClass().getName());
        }
    }

    private static BigDecimal[] getTermNumbers(final QueryFieldPosition queryFieldPosition,
                                               final Object value) {
        final String[] values = value.toString().split(DELIMITER);
        final BigDecimal[] numbers = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getTermNumber(queryFieldPosition, values[i].trim());
        }

        return numbers;
    }

    private static long[] getTermDates(final QueryFieldPosition queryFieldPosition,
                                       final Object value,
                                       final DateTimeSettings dateTimeSettings) {
        final String[] values = value.toString().split(DELIMITER);
        final long[] dates = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            dates[i] = getTermDate(queryFieldPosition, values[i].trim(), dateTimeSettings);
        }

        return dates;
    }

    private static class MatchException extends RuntimeException {

        MatchException(final String message) {
            super(message);
        }
    }

    public interface QueryFieldIndex {

        QueryFieldPosition get(String fieldName);
    }

    public record QueryFieldPosition(QueryField queryField, int index) {

    }

    public interface Values {

        String getString(QueryFieldPosition queryFieldPosition);

        BigDecimal getNumber(QueryFieldPosition queryFieldPosition);

        Long getDate(QueryFieldPosition queryFieldPosition);

        boolean isNull(QueryFieldPosition queryFieldPosition);
    }

    public interface ValuesPredicate extends Predicate<Values> {

    }

    private static class AndPredicate implements ValuesPredicate {

        private final List<ValuesPredicate> subPredicates;

        public AndPredicate(final List<ValuesPredicate> subPredicates) {
            this.subPredicates = subPredicates;
        }

        private static Optional<ValuesPredicate> create(final List<ValuesPredicate> subPredicates) {
            return Optional.of(new AndPredicate(subPredicates));
        }

        @Override
        public boolean test(final Values values) {
            for (final ValuesPredicate valuesPredicate : subPredicates) {
                if (!valuesPredicate.test(values)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class OrPredicate implements ValuesPredicate {

        private final List<ValuesPredicate> subPredicates;

        public OrPredicate(final List<ValuesPredicate> subPredicates) {
            this.subPredicates = subPredicates;
        }

        private static Optional<ValuesPredicate> create(final List<ValuesPredicate> subPredicates) {
            return Optional.of(new OrPredicate(subPredicates));
        }

        @Override
        public boolean test(final Values values) {
            for (final ValuesPredicate valuesPredicate : subPredicates) {
                if (valuesPredicate.test(values)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class NotPredicate implements ValuesPredicate {

        private final ValuesPredicate subPredicate;

        public NotPredicate(final ValuesPredicate subPredicate) {
            this.subPredicate = subPredicate;
        }

        private static Optional<ValuesPredicate> create(final ValuesPredicate subPredicate) {
            return Optional.of(new NotPredicate(subPredicate));
        }

        @Override
        public boolean test(final Values values) {
            return !subPredicate.test(values);
        }
    }

    private abstract static class ExpressionTermPredicate implements ValuesPredicate {

        final ExpressionTerm term;
        final QueryFieldPosition queryFieldPosition;

        private ExpressionTermPredicate(final ExpressionTerm term,
                                        final QueryFieldPosition queryFieldPosition) {
            this.term = term;
            this.queryFieldPosition = queryFieldPosition;
        }

        @Override
        public String toString() {
            return term.toString();
        }
    }

    private static class IsNullPredicate extends ExpressionTermPredicate {

        private IsNullPredicate(final ExpressionTerm term,
                                final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new IsNullPredicate(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            return values.isNull(queryFieldPosition);
        }
    }

    private static class IsNotNullPredicate extends ExpressionTermPredicate {

        private IsNotNullPredicate(final ExpressionTerm term,
                                   final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new IsNotNullPredicate(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            return !values.isNull(queryFieldPosition);
        }
    }

    private abstract static class NumericExpressionTermPredicate extends ExpressionTermPredicate {

        final BigDecimal termNum;

        private NumericExpressionTermPredicate(final ExpressionTerm term,
                                               final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
            termNum = getTermNumber(queryFieldPosition, term.getValue());
        }
    }

    private static class NumericEquals extends NumericExpressionTermPredicate {

        private NumericEquals(final ExpressionTerm term,
                              final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new NumericEquals(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            return Objects.equals(num, termNum);
        }
    }

    private static class NumericNotEquals extends NumericExpressionTermPredicate {

        private NumericNotEquals(final ExpressionTerm term,
                                 final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new NumericNotEquals(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            return !Objects.equals(num, termNum);
        }
    }

    private static class NumericGreaterThan extends NumericExpressionTermPredicate {

        private NumericGreaterThan(final ExpressionTerm term,
                                   final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new NumericGreaterThan(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            int compVal = CompareUtil.compareBigDecimal(num, termNum);
            return compVal > 0;
        }
    }

    private static class NumericGreaterThanOrEqualTo extends NumericExpressionTermPredicate {

        private NumericGreaterThanOrEqualTo(final ExpressionTerm term,
                                            final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new NumericGreaterThanOrEqualTo(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            int compVal = CompareUtil.compareBigDecimal(num, termNum);
            return compVal >= 0;
        }
    }

    private static class NumericLessThan extends NumericExpressionTermPredicate {

        private NumericLessThan(final ExpressionTerm term,
                                final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new NumericLessThan(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            int compVal = CompareUtil.compareBigDecimal(num, termNum);
            return compVal < 0;
        }
    }

    private static class NumericLessThanOrEqualTo extends NumericExpressionTermPredicate {

        private NumericLessThanOrEqualTo(final ExpressionTerm term,
                                         final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            return Optional.of(new NumericLessThanOrEqualTo(term, queryFieldPosition));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            int compVal = CompareUtil.compareBigDecimal(num, termNum);
            return compVal <= 0;
        }
    }

    private static class NumericBetween extends ExpressionTermPredicate {

        private final BigDecimal[] between;

        private NumericBetween(final ExpressionTerm term,
                               final QueryFieldPosition queryFieldPosition,
                               final BigDecimal[] between) {
            super(term, queryFieldPosition);
            this.between = between;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            final BigDecimal[] between = getTermNumbers(queryFieldPosition, term.getValue());
            if (between.length != 2) {
                throw new MatchException("2 numbers needed for between query");
            }
            if (CompareUtil.compareBigDecimal(between[0], between[1]) >= 0) {
                throw new MatchException("From number must be lower than to number");
            }
            return Optional.of(new NumericBetween(term, queryFieldPosition, between));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            return CompareUtil.compareBigDecimal(num, between[0]) >= 0
                    && CompareUtil.compareBigDecimal(num, between[1]) <= 0;
        }
    }

    private static class NumericIn extends ExpressionTermPredicate {

        private final BigDecimal[] in;

        private NumericIn(final ExpressionTerm term,
                          final QueryFieldPosition queryFieldPosition,
                          final BigDecimal[] in) {
            super(term, queryFieldPosition);
            this.in = in;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            final BigDecimal[] in = getTermNumbers(queryFieldPosition, term.getValue());
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new NumericIn(term, queryFieldPosition, in));
        }

        @Override
        public boolean test(final Values values) {
            final BigDecimal num = values.getNumber(queryFieldPosition);
            for (final BigDecimal n : in) {
                if (Objects.equals(n, num)) {
                    return true;
                }
            }
            return false;
        }
    }


    private abstract static class DateExpressionTermPredicate extends ExpressionTermPredicate {

        final Long termNum;

        private DateExpressionTermPredicate(final ExpressionTerm term,
                                            final QueryFieldPosition queryFieldPosition,
                                            final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition);
            termNum = getTermDate(queryFieldPosition, term.getValue(), dateTimeSettings);
        }
    }

    private static class DateEquals extends DateExpressionTermPredicate {

        private DateEquals(final ExpressionTerm term,
                           final QueryFieldPosition queryFieldPosition,
                           final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition, dateTimeSettings);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateEquals(term, queryFieldPosition, dateTimeSettings));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return Objects.equals(num, termNum);
        }
    }

    private static class DateNotEquals extends DateExpressionTermPredicate {

        private DateNotEquals(final ExpressionTerm term,
                              final QueryFieldPosition queryFieldPosition,
                              final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition, dateTimeSettings);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateNotEquals(term, queryFieldPosition, dateTimeSettings));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return !Objects.equals(num, termNum);
        }
    }

    private static class DateGreaterThan extends DateExpressionTermPredicate {

        private DateGreaterThan(final ExpressionTerm term,
                                final QueryFieldPosition queryFieldPosition,
                                final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition, dateTimeSettings);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateGreaterThan(term, queryFieldPosition, dateTimeSettings));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return CompareUtil.compareLong(num, termNum) > 0;
        }
    }

    private static class DateGreaterThanOrEqualTo extends DateExpressionTermPredicate {

        private DateGreaterThanOrEqualTo(final ExpressionTerm term,
                                         final QueryFieldPosition queryFieldPosition,
                                         final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition, dateTimeSettings);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateGreaterThanOrEqualTo(term, queryFieldPosition, dateTimeSettings));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return CompareUtil.compareLong(num, termNum) >= 0;
        }
    }

    private static class DateLessThan extends DateExpressionTermPredicate {

        private DateLessThan(final ExpressionTerm term,
                             final QueryFieldPosition queryFieldPosition,
                             final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition, dateTimeSettings);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateLessThan(term, queryFieldPosition, dateTimeSettings));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return CompareUtil.compareLong(num, termNum) < 0;
        }
    }

    private static class DateLessThanOrEqualTo extends DateExpressionTermPredicate {

        private DateLessThanOrEqualTo(final ExpressionTerm term,
                                      final QueryFieldPosition queryFieldPosition,
                                      final DateTimeSettings dateTimeSettings) {
            super(term, queryFieldPosition, dateTimeSettings);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            return Optional.of(new DateLessThanOrEqualTo(term, queryFieldPosition, dateTimeSettings));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return CompareUtil.compareLong(num, termNum) <= 0;
        }
    }

    private static class DateBetween extends ExpressionTermPredicate {

        private final long[] between;

        private DateBetween(final ExpressionTerm term,
                            final QueryFieldPosition queryFieldPosition,
                            final long[] between) {
            super(term, queryFieldPosition);
            this.between = between;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            final long[] between = getTermDates(queryFieldPosition, term.getValue(), dateTimeSettings);
            if (between.length != 2) {
                throw new MatchException("2 numbers needed for between query");
            }
            if (CompareUtil.compareLong(between[0], between[1]) >= 0) {
                throw new MatchException("From number must be lower than to number");
            }
            return Optional.of(new DateBetween(term, queryFieldPosition, between));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            return CompareUtil.compareLong(num, between[0]) >= 0
                    && CompareUtil.compareLong(num, between[1]) <= 0;
        }
    }

    private static class DateIn extends ExpressionTermPredicate {

        private final long[] in;

        private DateIn(final ExpressionTerm term,
                       final QueryFieldPosition queryFieldPosition,
                       final long[] in) {
            super(term, queryFieldPosition);
            this.in = in;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition,
                                                        final DateTimeSettings dateTimeSettings) {
            final long[] in = getTermDates(queryFieldPosition, term.getValue(), dateTimeSettings);
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(values -> false);
            }
            return Optional.of(new DateIn(term, queryFieldPosition, in));
        }

        @Override
        public boolean test(final Values values) {
            final Long num = values.getDate(queryFieldPosition);
            for (final long n : in) {
                if (Objects.equals(n, num)) {
                    return true;
                }
            }
            return false;
        }
    }


    private abstract static class StringExpressionTermPredicate extends ExpressionTermPredicate {

        final String string;

        private StringExpressionTermPredicate(final ExpressionTerm term,
                                              final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
            string = term.getValue();
        }
    }

    private static class StringEquals extends StringExpressionTermPredicate {

        private StringEquals(final ExpressionTerm term,
                             final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringEquals(term, queryFieldPosition));
            } else {
                return Optional.of(new StringEqualsIgnoreCase(term, queryFieldPosition));
            }
        }

        @Override
        public boolean test(final Values values) {
            return string.equals(values.getString(queryFieldPosition));
        }
    }

    private static class StringEqualsIgnoreCase extends StringExpressionTermPredicate {

        private StringEqualsIgnoreCase(final ExpressionTerm term,
                                       final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        @Override
        public boolean test(final Values values) {
            return string.equalsIgnoreCase(values.getString(queryFieldPosition));
        }
    }

    private static class StringNotEquals extends StringExpressionTermPredicate {

        private final ValuesPredicate predicate;

        private StringNotEquals(final ExpressionTerm term,
                                final QueryFieldPosition queryFieldPosition,
                                final ValuesPredicate predicate) {
            super(term, queryFieldPosition);
            this.predicate = predicate;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            final Optional<ValuesPredicate> predicate = StringEquals.create(term, queryFieldPosition);
            return predicate.map(valuesPredicate -> new StringNotEquals(term, queryFieldPosition, valuesPredicate));
        }

        @Override
        public boolean test(final Values values) {
            return !predicate.test(values);
        }
    }

    private static class StringContains extends StringExpressionTermPredicate {

        private StringContains(final ExpressionTerm term,
                               final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringContains(term, queryFieldPosition));
            } else {
                return Optional.of(new StringContainsIgnoreCase(term, queryFieldPosition));
            }
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return valString.contains(string);
        }
    }

    private static class StringContainsIgnoreCase extends ExpressionTermPredicate {

        private final String string;

        private StringContainsIgnoreCase(final ExpressionTerm term,
                                         final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
            string = term.getValue().toLowerCase();
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return valString.toLowerCase(Locale.ROOT).contains(string);
        }
    }

    private static class StringStartsWith extends StringExpressionTermPredicate {

        private StringStartsWith(final ExpressionTerm term,
                                 final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringStartsWith(term, queryFieldPosition));
            } else {
                return Optional.of(new StringStartsWithIgnoreCase(term, queryFieldPosition));
            }
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return valString.startsWith(string);
        }
    }

    private static class StringStartsWithIgnoreCase extends ExpressionTermPredicate {

        private final String string;

        private StringStartsWithIgnoreCase(final ExpressionTerm term,
                                           final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
            string = term.getValue().toLowerCase();
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return valString.toLowerCase(Locale.ROOT).startsWith(string);
        }
    }

    private static class StringEndsWith extends StringExpressionTermPredicate {

        private StringEndsWith(final ExpressionTerm term,
                               final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }

            if (term.isCaseSensitive()) {
                return Optional.of(new StringEndsWith(term, queryFieldPosition));
            } else {
                return Optional.of(new StringEndsWithIgnoreCase(term, queryFieldPosition));
            }
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return valString.endsWith(string);
        }
    }

    private static class StringEndsWithIgnoreCase extends ExpressionTermPredicate {

        private final String string;

        private StringEndsWithIgnoreCase(final ExpressionTerm term,
                                         final QueryFieldPosition queryFieldPosition) {
            super(term, queryFieldPosition);
            string = term.getValue().toLowerCase();
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return valString.toLowerCase(Locale.ROOT).endsWith(string);
        }
    }

    private static class StringRegex extends ExpressionTermPredicate {

        private final Pattern pattern;

        private StringRegex(final ExpressionTerm term,
                            final QueryFieldPosition queryFieldPosition,
                            final Pattern pattern) {
            super(term, queryFieldPosition);
            this.pattern = pattern;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }
            int flags = 0;
            if (!term.isCaseSensitive()) {
                flags = Pattern.CASE_INSENSITIVE;
            }
            final Pattern pattern = Pattern.compile(term.getValue(), flags);
            return Optional.of(new StringRegex(term, queryFieldPosition, pattern));
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return pattern.matcher(valString).find();
        }
    }

    private static class StringWordBoundary extends ExpressionTermPredicate {

        private final Predicate<String> predicate;

        private StringWordBoundary(final ExpressionTerm term,
                                   final QueryFieldPosition queryFieldPosition,
                                   final Predicate<String> predicate) {
            super(term, queryFieldPosition);
            this.predicate = predicate;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            // If the term value is null or empty then always match.
            if (NullSafe.isBlankString(term.getValue())) {
                return Optional.empty();
            }
            final Predicate<String> predicate = StringPredicateFactory.createWordBoundaryPredicate(term.getValue());
            return Optional.of(new StringWordBoundary(term, queryFieldPosition, predicate));
        }

        @Override
        public boolean test(final Values values) {
            final String valString = values.getString(queryFieldPosition);
            if (valString == null) {
                return false;
            }
            return predicate.test(valString);
        }
    }

    private static class StringIn extends ExpressionTermPredicate {

        private final List<ValuesPredicate> subPredicates;

        private StringIn(final ExpressionTerm term,
                         final QueryFieldPosition queryFieldPosition,
                         final List<ValuesPredicate> subPredicates) {
            super(term, queryFieldPosition);
            this.subPredicates = subPredicates;
        }

        private static Optional<ValuesPredicate> create(final ExpressionTerm term,
                                                        final QueryFieldPosition queryFieldPosition) {
            final String[] in = term.getValue().split(" ");
            final List<ValuesPredicate> subPredicates = new ArrayList<>(in.length);
            for (final String str : in) {
                final ExpressionTerm subTerm = ExpressionTerm
                        .builder()
                        .field(term.getField())
                        .condition(Condition.EQUALS)
                        .value(str)
                        .caseSensitive(term.getCaseSensitive())
                        .build();
                final Optional<ValuesPredicate> subPredicate = StringEquals.create(subTerm, queryFieldPosition);
                subPredicate.ifPresent(subPredicates::add);
            }
            if (subPredicates.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new StringIn(term, queryFieldPosition, subPredicates));
        }

        @Override
        public boolean test(final Values values) {
            for (final ValuesPredicate predicate : subPredicates) {
                if (predicate.test(values)) {
                    return true;
                }
            }
            return false;
        }
    }
}
