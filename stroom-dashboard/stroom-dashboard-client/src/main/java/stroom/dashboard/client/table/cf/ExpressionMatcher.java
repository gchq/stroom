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

package stroom.dashboard.client.table.cf;

import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.Map;

public class ExpressionMatcher {
    private static final String DELIMITER = ",";

    private final Map<String, AbstractField> fieldMap;

    public ExpressionMatcher(final Map<String, AbstractField> fieldMap) {
        this.fieldMap = fieldMap;
    }

    public boolean match(final Map<String, Object> attributeMap, final ExpressionItem item) {
        // If the initial item is null or not enabled then don't match.
        if (item == null || !item.enabled()) {
            return false;
        }
        return matchItem(attributeMap, item);
    }

    private boolean matchItem(final Map<String, Object> attributeMap, final ExpressionItem item) {
        if (!item.enabled()) {
            // If the child item is not enabled then return and keep trying to match with other parts of the expression.
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

    private boolean matchOperator(final Map<String, Object> attributeMap, final ExpressionOperator operator) {
        if (operator.getChildren() == null || operator.getChildren().size() == 0) {
            return true;
        }

        switch (operator.getOp()) {
            case AND:
                for (final ExpressionItem child : operator.getChildren()) {
                    if (!matchItem(attributeMap, child)) {
                        return false;
                    }
                }
                return true;
            case OR:
                for (final ExpressionItem child : operator.getChildren()) {
                    if (matchItem(attributeMap, child)) {
                        return true;
                    }
                }
                return false;
            case NOT:
                return operator.getChildren().size() == 1 && !matchItem(attributeMap, operator.getChildren().get(0));
            default:
                throw new MatchException("Unexpected operator type");
        }
    }

    private boolean matchTerm(final Map<String, Object> attributeMap, final ExpressionTerm term) {
        String termField = term.getField();
        final Condition condition = term.getCondition();
        String termValue = term.getValue();

        // Clean strings to remove unwanted whitespace that the user may have
        // added accidentally.
        if (termField != null) {
            termField = termField.trim();
        }
        if (termValue != null) {
            termValue = termValue.trim();
        }

        // Try and find the referenced field.
        if (termField == null || termField.length() == 0) {
            throw new MatchException("Field not set");
        }
        final AbstractField field = fieldMap.get(termField);
        if (field == null) {
            throw new MatchException("Field not found in index: " + termField);
        }
        final String fieldName = field.getName();
        if (termValue == null || termValue.length() == 0) {
            throw new MatchException("Value not set");
        }

        final Object attribute = attributeMap.get(term.getField());
        if (attribute == null) {
            throw new MatchException("Attribute '" + term.getField() + "' not found");
        }

        // Create a query based on the field type and condition.
        if (field.isNumeric()) {
            switch (condition) {
                case EQUALS: {
                    return isStringMatch(termValue, attribute);
//                    final long num1 = getNumber(fieldName, attribute);
//                    final long num2 = getNumber(fieldName, termValue);
//                    return num1 == num2;
                }
                case CONTAINS: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 == num2;
                }
                case GREATER_THAN: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 > num2;
                }
                case GREATER_THAN_OR_EQUAL_TO: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 >= num2;
                }
                case LESS_THAN: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 < num2;
                }
                case LESS_THAN_OR_EQUAL_TO: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 <= num2;
                }
                case BETWEEN: {
                    final long[] between = getNumbers(fieldName, termValue);
                    if (between.length != 2) {
                        throw new MatchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new MatchException("From number must lower than to number");
                    }
                    final long num = getNumber(fieldName, attribute);
                    return num >= between[0] && num <= between[1];
                }
                case IN:
                    return isNumericIn(fieldName, termValue, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + field.getDisplayValue() + " field type");
            }
        } else {
            switch (condition) {
                case EQUALS:
                    return isStringMatch(termValue, attribute);
                case CONTAINS:
                    return isStringMatch(termValue, attribute);
                case IN:
                    return isIn(termValue, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + field.getDisplayValue() + " field type");
            }
        }
    }

    private boolean isNumericIn(final String fieldName, final Object termValue, final Object attribute) {
        final long num = getNumber(fieldName, attribute);
        final long[] in = getNumbers(fieldName, termValue);
        for (final long n : in) {
            if (n == num) {
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
        return termValue.equals(attribute.toString());
    }

    private long getNumber(final String fieldName, final Object value) {
        try {
            if (value instanceof Long) {
                return (Long) value;
            }
            return Long.parseLong(value.toString());
        } catch (final NumberFormatException e) {
            throw new MatchException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\"");
        }
    }

    private long[] getNumbers(final String fieldName, final Object value) {
        final String[] values = value.toString().split(DELIMITER);
        final long[] numbers = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getNumber(fieldName, values[i].trim());
        }

        return numbers;
    }

    private static class MatchException extends RuntimeException {
        MatchException(final String message) {
            super(message);
        }
    }
}