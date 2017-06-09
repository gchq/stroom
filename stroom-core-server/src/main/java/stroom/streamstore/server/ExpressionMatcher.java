package stroom.streamstore.server;

import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.shared.DocRef;
import stroom.query.shared.Condition;
import stroom.query.shared.ExpressionItem;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFieldType;
import stroom.util.date.DateUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ExpressionMatcher {
    private static final String DELIMITER = ",";

    private final Map<String, IndexField> indexFieldMap;
    private final DictionaryService dictionaryService;
    private final Map<DocRef, String[]> wordMap = new HashMap<>();
    private final Map<String, Pattern> patternMap = new HashMap<>();

    public ExpressionMatcher(final Map<String, IndexField> indexFieldMap, final DictionaryService dictionaryService) {
        this.indexFieldMap = indexFieldMap;
        this.dictionaryService = dictionaryService;
    }

    public boolean match(final Map<String, Object> attributeMap, final ExpressionItem item) {
        if (!item.isEnabled()) {
            return true;
        }

        if (item instanceof ExpressionOperator) {
            final ExpressionOperator operator = (ExpressionOperator) item;
            if (operator.getChildren() == null || operator.getChildren().size() == 0) {
                return true;
            }

            switch (operator.getType()) {
                case AND:
                    for (final ExpressionItem child : operator.getChildren()) {
                        if (!match(attributeMap, child)) {
                            return false;
                        }
                    }
                    return true;

                case OR:
                    for (final ExpressionItem child : operator.getChildren()) {
                        if (match(attributeMap, child)) {
                            return true;
                        }
                    }
                    return false;
                case NOT:
                    return operator.getChildren().size() == 1 && !match(attributeMap, operator.getChildren().get(0));
            }

        } else if (item instanceof ExpressionTerm) {
            return matchTerm(attributeMap, (ExpressionTerm) item);
        }

        throw new MatchException("Unexpected item type");
    }

    private boolean matchTerm(final Map<String, Object> attributeMap, final ExpressionTerm term) {
        String termField = term.getField();
        final Condition condition = term.getCondition();
        String termValue = term.getValue();
        final DocRef dictionary = term.getDictionary();

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
        final IndexField indexField = indexFieldMap.get(termField);
        if (indexField == null) {
            throw new MatchException("Field not found in index: " + termField);
        }
        final String fieldName = indexField.getFieldName();

        // Ensure an appropriate termValue has been provided for the condition type.
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (dictionary == null || dictionary.getUuid() == null) {
                throw new MatchException("Dictionary not set for field: " + termField);
            }
        } else {
            if (termValue == null || termValue.length() == 0) {
                throw new MatchException("Value not set");
            }
        }

        final Object attribute = attributeMap.get(term.getField());
        if (attribute == null) {
            throw new MatchException("Attribute '" + term.getField() + "' not found");
        }

        // Create a query based on the field type and condition.
        if (indexField.getFieldType().isNumeric()) {
            switch (condition) {
                case EQUALS: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 == num2;
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
                case IN_DICTIONARY:
                    return isInDictionary(fieldName, dictionary, indexField, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldType().getDisplayValue() + " field type");
            }
        } else if (IndexFieldType.DATE_FIELD.equals(indexField.getFieldType())) {
            switch (condition) {
                case EQUALS: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 == date2;
                }
                case CONTAINS: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 == date2;
                }
                case GREATER_THAN: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 > date2;
                }
                case GREATER_THAN_OR_EQUAL_TO: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 >= date2;
                }
                case LESS_THAN: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 < date2;
                }
                case LESS_THAN_OR_EQUAL_TO: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 <= date2;
                }
                case BETWEEN: {
                    final long[] between = getDates(fieldName, termValue);
                    if (between.length != 2) {
                        throw new MatchException("2 dates needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new MatchException("From date must occur before to date");
                    }
                    final long num = getDate(fieldName, attribute);
                    return num >= between[0] && num <= between[1];
                }
                case IN:
                    return isDateIn(fieldName, termValue, attribute);
                case IN_DICTIONARY:
                    return isInDictionary(fieldName, dictionary, indexField, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldType().getDisplayValue() + " field type");
            }
        } else {
            switch (condition) {
                case EQUALS:
                    return isStringMatch(termValue, attribute.toString());
                case CONTAINS:
                    return isStringMatch(termValue, attribute.toString());
                case IN:
                    return isIn(fieldName, termValue, attribute);
                case IN_DICTIONARY:
                    return isInDictionary(fieldName, dictionary, indexField, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldType().getDisplayValue() + " field type");
            }
        }
    }

    private boolean isNumericIn(final String fieldName, final Object termValue, final Object attribute) {
        final long num = getNumber(fieldName, attribute);
        final long[] in = getNumbers(fieldName, termValue);
        if (in != null) {
            for (final long n : in) {
                if (n == num) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isDateIn(final String fieldName, final Object termValue, final Object attribute) {
        final long num = getDate(fieldName, attribute);
        final long[] in = getDates(fieldName, termValue);
        if (in != null) {
            for (final long n : in) {
                if (n == num) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isIn(final String fieldName, final Object termValue, final Object attribute) {
        final String string = attribute.toString();
        final String[] termValues = termValue.toString().split(" ");
        for (final String tv : termValues) {
            if (isStringMatch(tv, string)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStringMatch(final String termValue, final String attribute) {
        final Pattern pattern = patternMap.computeIfAbsent(termValue, t -> Pattern.compile(t.replaceAll("\\*", ".*")));
        return pattern.matcher(attribute).matches();
    }

    private boolean isInDictionary(final String fieldName, final DocRef docRef,
                                   final IndexField indexField, final Object attribute) {
        final String[] lines = loadWords(docRef);
        if (lines != null) {
            for (final String line : lines) {
                if (indexField.getFieldType().isNumeric()) {
                    if (isNumericIn(fieldName, line, attribute)) {
                        return true;
                    }
                } else if (IndexFieldType.DATE_FIELD.equals(indexField.getFieldType())) {
                    if (isDateIn(fieldName, line, attribute)) {
                        return true;
                    }
                } else {
                    if (isIn(fieldName, line, attribute)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String[] loadWords(final DocRef docRef) {
        return wordMap.computeIfAbsent(docRef, k -> {
            final Dictionary dictionary = dictionaryService.loadByUuid(docRef.getUuid());
            if (dictionary != null) {
                final String words = dictionary.getData();
                if (words != null) {
                    return words.trim().split("\n");
                }
            }

            return null;
        });
    }

    private long getDate(final String fieldName, final Object value) {
        try {
            if (value instanceof Long) {
                return (Long) value;
            }
            return DateUtil.parseNormalDateTimeString(value.toString());

//            return new DateExpressionParser().parse(value, timeZoneId, nowEpochMilli).toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new MatchException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    private long[] getDates(final String fieldName, final Object value) {
        final String[] values = value.toString().split(DELIMITER);
        final long[] dates = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            dates[i] = getDate(fieldName, values[i].trim());
        }

        return dates;
    }

    private long getNumber(final String fieldName, final Object value) {
        try {
            if (value instanceof Long) {
                return (Long) value;
            }
            return Long.valueOf(value.toString());
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