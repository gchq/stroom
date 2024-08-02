/*
 * Copyright 2024 Crown Copyright
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

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Format.Type;
import stroom.query.language.functions.DateUtil;
import stroom.util.NullSafe;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.string.CIKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ColumnExpressionMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ColumnExpressionMatcher.class);
    private static final String DELIMITER = ",";

    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    private final Map<CIKey, Column> fieldNameToFieldMap;
    private final DateTimeSettings dateTimeSettings;

    public ColumnExpressionMatcher(final List<Column> columns,
                                   final DateTimeSettings dateTimeSettings) {
        this.dateTimeSettings = dateTimeSettings;
        this.fieldNameToFieldMap = new HashMap<>();
        for (final Column column : NullSafe.list(columns)) {
            // Allow match by id and name.
            fieldNameToFieldMap.putIfAbsent(column.getIdAsCIKey(), column);
            fieldNameToFieldMap.putIfAbsent(column.getNameAsCIKey(), column);
        }
    }

    public boolean match(final Map<CIKey, Object> attributeMap, final ExpressionItem item) {
        // If the initial item is null or not enabled then don't match.
        if (item == null || !item.enabled()) {
            return false;
        }
        return matchItem(attributeMap, item);
    }

    private boolean matchItem(final Map<CIKey, Object> attributeMap, final ExpressionItem item) {
        if (!item.enabled()) {
            // If the child item is not enabled then return and keep trying to match with other parts
            // of the expression.
            return true;
        }

        if (item instanceof ExpressionOperator) {
            return matchOperator(attributeMap, (ExpressionOperator) item);
        } else if (item instanceof ExpressionTerm) {
            return matchTerm(attributeMap, (ExpressionTerm) item);
        } else {
            throw new MatchException("Unexpected item type");
        }
    }

    private boolean matchOperator(final Map<CIKey, Object> attributeMap,
                                  final ExpressionOperator operator) {
        if (operator.getChildren() == null || operator.getChildren().isEmpty()) {
            return true;
        }

        return switch (operator.op()) {
            case AND -> {
                for (final ExpressionItem child : operator.getChildren()) {
                    if (!matchItem(attributeMap, child)) {
                        yield false;
                    }
                }
                yield true;
            }
            case OR -> {
                for (final ExpressionItem child : operator.getChildren()) {
                    if (matchItem(attributeMap, child)) {
                        yield true;
                    }
                }
                yield false;
            }
            case NOT -> operator.getChildren().size() == 1
                    && !matchItem(attributeMap, operator.getChildren().get(0));
        };
    }

    private boolean matchTerm(final Map<CIKey, Object> attributeMap, final ExpressionTerm term) {
        // The term field is the column name, NOT the index field name
        final Condition condition = term.getCondition();

        // Try and find the referenced field.
        if (NullSafe.isBlankString(term.getField())) {
            throw new MatchException("Field not set");
        }
        final String termField = term.getField().trim();
        final CIKey caseInsensitiveTermField = CIKey.of(termField);
        final Column column = fieldNameToFieldMap.get(caseInsensitiveTermField);
        if (column == null) {
            throw new MatchException("Column not found: " + termField);
        }
        final String columnName = column.getName();

        final Object attribute = attributeMap.get(caseInsensitiveTermField);
        if (Condition.IS_NULL.equals(condition)) {
            return attribute == null;
        } else if (Condition.IS_NOT_NULL.equals(condition)) {
            return attribute != null;
        } else if (attribute == null) {
            return false;
        }

        // Try and resolve the term value.
        String termValue = NullSafe.trim(term.getValue());
        if (termValue.isEmpty()) {
            throw new MatchException("Value not set");
        }

        // Substitute with row value if a row value exists.
        final Object rowValue = attributeMap.get(CIKey.of(termValue));
        if (rowValue != null) {
            termValue = rowValue.toString();
        }

        // Create a query based on the field type and condition.
        if (matchesFormatType(column, Format.Type.NUMBER)) {
            return matchNumericColumn(condition, termValue, column, columnName, attribute);
        } else if (matchesFormatType(column, Format.Type.DATE_TIME)) {
            return matchDateField(condition, termValue, column, columnName, attribute);
        } else {
            return matchGeneralField(condition, termValue, column, columnName, attribute);
        }
    }

    private boolean matchGeneralField(final Condition condition,
                                      final String termValue,
                                      final Column column,
                                      final String columnName,
                                      final Object attribute) {
        try {
            return matchDateField(condition, termValue, column, columnName, attribute);
        } catch (final RuntimeException e) {
            try {
                return matchNumericColumn(condition, termValue, column, columnName, attribute);
            } catch (final RuntimeException e2) {
                return switch (condition) {
                    case EQUALS -> isStringMatch(termValue, attribute);
                    case NOT_EQUALS -> !isStringMatch(termValue, attribute);
                    // CONTAINS only supported for legacy content, not for use in UI
                    case CONTAINS -> isStringContainsMatch(termValue, attribute);
                    case IN -> isIn(termValue, attribute);
                    default -> throw e2;
                };
            }
        }
    }

    private boolean matchesFormatType(final Column column, final Type type) {
        return Optional.ofNullable(column)
                .map(Column::getFormat)
                .map(Format::getType)
                .filter(fieldType -> fieldType.equals(type))
                .isPresent();
    }

    private boolean matchDateField(final Condition condition,
                                   final String termValue,
                                   final Column column,
                                   final String columnName,
                                   final Object attribute) {
        switch (condition) {
            case EQUALS: {
                final Long num1 = getDate(columnName, attribute);
                final Long num2 = getDate(termValue);
                return Objects.equals(num1, num2);
            }
            case NOT_EQUALS: {
                final Long num1 = getDate(columnName, attribute);
                final Long num2 = getDate(termValue);
                return !Objects.equals(num1, num2);
            }
            case GREATER_THAN: {
                final Long num1 = getDate(columnName, attribute);
                final Long num2 = getDate(termValue);
                return CompareUtil.compareLong(num1, num2) > 0;
            }
            case GREATER_THAN_OR_EQUAL_TO: {
                final Long num1 = getDate(columnName, attribute);
                final Long num2 = getDate(termValue);
                return CompareUtil.compareLong(num1, num2) >= 0;
            }
            case LESS_THAN: {
                final Long num1 = getDate(columnName, attribute);
                final Long num2 = getDate(termValue);
                return CompareUtil.compareLong(num1, num2) < 0;
            }
            case LESS_THAN_OR_EQUAL_TO: {
                final Long num1 = getDate(columnName, attribute);
                final Long num2 = getDate(termValue);
                return CompareUtil.compareLong(num1, num2) <= 0;
            }
            case BETWEEN: {
                final Long[] between = getDates(termValue);
                if (between.length != 2) {
                    throw new MatchException("2 numbers needed for between query");
                }
                if (CompareUtil.compareLong(between[0], between[1]) >= 0) {
                    throw new MatchException("From number must be lower than to number");
                }
                final Long num = getDate(columnName, attribute);
                return CompareUtil.compareLong(num, between[0]) >= 0
                        && CompareUtil.compareLong(num, between[1]) <= 0;
            }
            default:
                throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                        + column.getFormat().getType().getDisplayValue() + " field type");
        }
    }

    private boolean matchNumericColumn(final Condition condition,
                                       final String termValue,
                                       final Column column,
                                       final String columnName,
                                       final Object attribute) {
        switch (condition) {
            case EQUALS: {
                final BigDecimal num1 = getNumber(columnName, attribute);
                final BigDecimal num2 = getNumber(columnName, termValue);
                return Objects.equals(num1, num2);
            }
            case NOT_EQUALS: {
                final BigDecimal num1 = getNumber(columnName, attribute);
                final BigDecimal num2 = getNumber(columnName, termValue);
                return !Objects.equals(num1, num2);
            }
            case GREATER_THAN: {
                final BigDecimal num1 = getNumber(columnName, attribute);
                final BigDecimal num2 = getNumber(columnName, termValue);
                int compVal = CompareUtil.compareBigDecimal(num1, num2);

                LOGGER.debug(num1 + " " + num2 + " " + compVal);

                return compVal > 0;
            }
            case GREATER_THAN_OR_EQUAL_TO: {
                final BigDecimal num1 = getNumber(columnName, attribute);
                final BigDecimal num2 = getNumber(columnName, termValue);
                return CompareUtil.compareBigDecimal(num1, num2) >= 0;
            }
            case LESS_THAN: {
                final BigDecimal num1 = getNumber(columnName, attribute);
                final BigDecimal num2 = getNumber(columnName, termValue);
                return CompareUtil.compareBigDecimal(num1, num2) < 0;
            }
            case LESS_THAN_OR_EQUAL_TO: {
                final BigDecimal num1 = getNumber(columnName, attribute);
                final BigDecimal num2 = getNumber(columnName, termValue);
                return CompareUtil.compareBigDecimal(num1, num2) <= 0;
            }
            case BETWEEN: {
                final BigDecimal[] between = getNumbers(columnName, termValue);
                if (between.length != 2) {
                    throw new MatchException("2 numbers needed for between query");
                }
                if (CompareUtil.compareBigDecimal(between[0], between[1]) >= 0) {
                    throw new MatchException("From number must be lower than to number");
                }
                final BigDecimal num = getNumber(columnName, attribute);
                return CompareUtil.compareBigDecimal(num, between[0]) >= 0
                        && CompareUtil.compareBigDecimal(num, between[1]) <= 0;
            }
            case IN:
                return isNumericIn(columnName, termValue, attribute);
            default:
                throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                        + column.getFormat().getType().getDisplayValue() + " field type");
        }
    }

    private boolean isNumericIn(final String columnName, final Object termValue, final Object attribute) {
        final BigDecimal num = getNumber(columnName, attribute);
        final BigDecimal[] in = getNumbers(columnName, termValue);
        for (final BigDecimal n : in) {
            if (Objects.equals(n, num)) {
                return true;
            }
        }

        return false;
    }

    private boolean isIn(final Object termValue, final Object attribute) {
        final String[] termValues = termValue.toString().split(" ");
        for (final String tv : termValues) {
            if (isStringMatch(tv, attribute)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStringMatch(final String termValue, final Object attribute) {
        if (termValue.contains("*")) {
            final Pattern pattern = patternCache.computeIfAbsent(termValue, k -> {
                final String regex = k.replaceAll("\\*", ".*");
                return Pattern.compile(regex);
            });
            return pattern.matcher(attribute.toString()).matches();
        }
        return termValue.equals(attribute.toString());
    }

    private boolean isStringContainsMatch(final String termValue, final Object attribute) {
        if (attribute == null && termValue == null) {
            return true;
        } else if (attribute == null) {
            return false;
        } else if (termValue == null || termValue.isEmpty()) {
            return true;
        } else {
            return isStringMatch(termValue, attribute) || attribute.toString().contains(termValue);
        }
    }

    private BigDecimal getNumber(final String columnName, final Object value) {
        if (value == null) {
            return null;
        } else {
            try {
                if (value instanceof Long) {
                    return BigDecimal.valueOf((long) value);
                } else if (value instanceof Double) {
                    return BigDecimal.valueOf((Double) value);
                }
                return new BigDecimal(value.toString());
            } catch (final NumberFormatException e) {
                throw new MatchException(
                        "Expected a numeric value for field \"" + columnName +
                                "\" but was given string \"" + value + "\"");
            }
        }
    }

    private Long getDate(final String columnName, final Object value) {
        if (value == null) {
            return null;
        } else {
            if (value instanceof final String valueStr) {
                try {
                    return DateUtil.parseNormalDateTimeString(valueStr);
                } catch (final NumberFormatException e) {
                    throw new MatchException(
                            "Unable to parse a date/time from value \"" + valueStr + "\"");
                }
            } else {
                throw new MatchException(
                        "Expected a string value for field \"" + columnName + "\" but was given \"" + value
                                + "\" of type " + value.getClass().getName());
            }
        }
    }

    private Long getDate(final Object value) {
        if (value == null) {
            return null;
        } else {
            if (value instanceof final String valueStr) {
                try {
                    final Optional<ZonedDateTime> optionalZonedDateTime =
                            DateExpressionParser.parse(valueStr, dateTimeSettings);
                    final ZonedDateTime zonedDateTime = optionalZonedDateTime.orElseThrow(() ->
                            new NumberFormatException("Unexpected: " + valueStr));
                    return zonedDateTime.toInstant().toEpochMilli();
                } catch (final NumberFormatException e) {
                    throw new MatchException(
                            "Unable to parse a date/time from value \"" + valueStr + "\"");
                }
            } else {
                throw new MatchException(
                        "Expected a string value but was given \"" + value
                                + "\" of type " + value.getClass().getName());
            }
        }
    }

    private BigDecimal[] getNumbers(final String columnName, final Object value) {
        if (value == null) {
            return new BigDecimal[0];
        } else {
            final String[] values = value.toString().split(DELIMITER);
            final BigDecimal[] numbers = new BigDecimal[values.length];
            for (int i = 0; i < values.length; i++) {
                numbers[i] = getNumber(columnName, values[i].trim());
            }

            return numbers;
        }
    }

    private Long[] getDates(final Object value) {
        if (value == null) {
            return new Long[0];
        } else {
            final String[] values = value.toString().split(DELIMITER);
            final Long[] dates = new Long[values.length];
            for (int i = 0; i < values.length; i++) {
                dates[i] = getDate(values[i].trim());
            }

            return dates;
        }
    }

    private static class MatchException extends RuntimeException {

        MatchException(final String message) {
            super(message);
        }
    }
}
