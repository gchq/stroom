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

package stroom.expression.matcher;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ExpressionMatcher {

    private static final String DELIMITER = ",";

    private final Map<String, QueryField> fieldMap;
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;
    // TODO Ideally we should be holding a map of Set<S
    private final Map<DocRef, String[]> wordMap = new ConcurrentHashMap<>();
    private final Map<String, Pattern> patternMap = new ConcurrentHashMap<>();
    private final DateTimeSettings dateTimeSettings;

    public ExpressionMatcher(final Map<String, QueryField> fieldMap) {
        this.fieldMap = fieldMap;
        this.wordListProvider = null;
        this.collectionService = null;
        this.dateTimeSettings = DateTimeSettings.builder().build();
    }

    public ExpressionMatcher(final Map<String, QueryField> fieldMap,
                             final WordListProvider wordListProvider,
                             final CollectionService collectionService,
                             final DateTimeSettings dateTimeSettings) {
        this.fieldMap = fieldMap;
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
        this.dateTimeSettings = dateTimeSettings;
    }

    public boolean match(final Map<String, Object> attributeMap, final ExpressionItem item) {
        // If the initial item is null or not enabled then don't match.
        if (item == null || !item.enabled()) {
            return true;
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

    private boolean matchOperator(final Map<String, Object> attributeMap,
                                  final ExpressionOperator operator) {
        if (!operator.hasEnabledChildren()) {
            return true;
        } else {
            final List<ExpressionItem> enabledChildren = operator.getEnabledChildren();
            return switch (operator.op()) {
                case AND -> {
                    for (final ExpressionItem child : enabledChildren) {
                        if (!matchItem(attributeMap, child)) {
                            yield false;
                        }
                    }
                    yield true;
                }
                case OR -> {
                    for (final ExpressionItem child : enabledChildren) {
                        if (matchItem(attributeMap, child)) {
                            yield true;
                        }
                    }
                    yield false;
                }
                case NOT -> enabledChildren.size() == 1
                            && !matchItem(attributeMap, enabledChildren.get(0));
            };
        }
    }

    private boolean matchTerm(final Map<String, Object> attributeMap, final ExpressionTerm term) {
        String termField = term.getField();
        final Condition condition = term.getCondition();
        String termValue = term.getValue();
        final DocRef docRef = term.getDocRef();

        // Clean strings to remove unwanted whitespace that the user may have
        // added accidentally.
        if (termField != null) {
            termField = termField.trim();
        }
        if (termValue != null) {
            termValue = termValue.trim();
        }

        // Try and find the referenced field.
        if (termField == null || termField.isEmpty()) {
            throw new MatchException("Field not set");
        }
        final QueryField field = fieldMap.get(termField);
        if (field == null) {
            throw new MatchException("Field not found in index: " + termField);
        }
        final String fieldName = field.getFldName();

        // Ensure an appropriate termValue has been provided for the condition type.
        if (Condition.IN_DICTIONARY.equals(condition) ||
            Condition.IN_FOLDER.equals(condition) ||
            Condition.IS_DOC_REF.equals(condition) ||
            Condition.OF_DOC_REF.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new MatchException("DocRef not set for field: " + termField);
            }
        } else {
            if (termValue == null || termValue.isEmpty()) {
                throw new MatchException("Value not set");
            }
        }

        final Object attribute = attributeMap.get(term.getField());

        if (Condition.IS_NULL.equals(condition)) {
            // Perform null/not null equality if required.
            return attribute == null;
        } else if (Condition.IS_NOT_NULL.equals(condition)) {
            return attribute != null;
        }

        if (attribute == null) {
            throw new MatchException("Attribute '" + term.getField() + "' not found");
        }

        // Create a query based on the field type and condition.
        if (field.isNumeric()) {
            switch (condition) {
                case EQUALS, CONTAINS: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 == num2;
                }
                case NOT_EQUALS: {
                    final long num1 = getNumber(fieldName, attribute);
                    final long num2 = getNumber(fieldName, termValue);
                    return num1 != num2;
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
                    return isInDictionary(fieldName, docRef, field, attribute);
                case IN_FOLDER:
                    return isInFolder(fieldName, docRef, field, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                             + field.getFldType() + " field type");
            }
        } else if (FieldType.DATE.equals(field.getFldType())) {
            switch (condition) {
                case EQUALS, CONTAINS: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 == date2;
                }
                case NOT_EQUALS: {
                    final long date1 = getDate(fieldName, attribute);
                    final long date2 = getDate(fieldName, termValue);
                    return date1 != date2;
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
                    return isInDictionary(fieldName, docRef, field, attribute);
                case IN_FOLDER:
                    return isInFolder(fieldName, docRef, field, attribute);
                default:
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                             + field.getFldType() + " field type");
            }
        } else {
            switch (condition) {
                case EQUALS, CONTAINS:
                    return isStringMatch(termValue, attribute);
                case NOT_EQUALS:
                    return !isStringMatch(termValue, attribute);
                case IN:
                    return isIn(fieldName, termValue, attribute);
                case IN_DICTIONARY:
                    return isInDictionary(fieldName, docRef, field, attribute);
                case IN_FOLDER:
                    return isInFolder(fieldName, docRef, field, attribute);
                case IS_DOC_REF:
                    return isDocRef(fieldName, docRef, field, attribute);
                default: {
                    if (attribute instanceof final TermMatcher termMatcher) {
                        return termMatcher.match(field, condition, termValue, docRef);
                    }
                    throw new MatchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                             + field.getFldType() + " field type");
                }
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
        // TODO: 07/03/2023 Should we be splitting lines when this is used for IN_DICTIONARY?
        //  I think it shouldn't.
        final String[] termValues = termValue.toString().split(" ");
        for (final String tv : termValues) {
            if (isStringMatch(tv, attribute)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStringMatch(final String termValue, final Object attribute) {
        final Pattern pattern = patternMap.computeIfAbsent(termValue, t ->
                Pattern.compile(t.replaceAll("\\*", ".*"), Pattern.CASE_INSENSITIVE));

        if (attribute instanceof final DocRef docRef) {
            if (pattern.matcher(docRef.getUuid()).matches()) {
                return true;
            }
            return pattern.matcher(docRef.getName()).matches();
        } else if (attribute instanceof final Collection<?> collection) {
            for (final Object o : collection) {
                if (isStringMatch(termValue, o)) {
                    return true;
                }
            }
        }
        return pattern.matcher(attribute.toString()).matches();
    }

    private boolean isInDictionary(final String fieldName, final DocRef docRef,
                                   final QueryField field, final Object attribute) {
        final String[] lines = loadWords(docRef);
        if (lines != null) {
            for (final String line : lines) {
                if (field.isNumeric()) {
                    if (isNumericIn(fieldName, line, attribute)) {
                        return true;
                    }
                } else if (FieldType.DATE.equals(field.getFldType())) {
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

    private boolean isInFolder(final String fieldName,
                               final DocRef docRef,
                               final QueryField field,
                               final Object attribute) {
        if (FieldType.DOC_REF.equals(field.getFldType())) {
            final String type = field.getDocRefType();
            if (type != null && collectionService != null) {
                final Set<DocRef> descendants = collectionService.getDescendants(docRef, type);
                if (descendants != null && descendants.size() > 0) {
                    if (attribute instanceof DocRef) {
                        final String uuid = ((DocRef) attribute).getUuid();
                        if (uuid != null) {
                            for (final DocRef descendant : descendants) {
                                if (uuid.equals(descendant.getUuid())) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isDocRef(final String fieldName, final DocRef docRef,
                             final QueryField field, final Object attribute) {
        if (attribute instanceof DocRef) {
            final String uuid = ((DocRef) attribute).getUuid();
            return (null != uuid && uuid.equals(docRef.getUuid()));
        } else if (attribute instanceof String) {
            // Trying to compare a string to a docRef so assume the string is EITHER the uuid or the name
            // In theory a 'uuid' could match a name but as we use proper uuids it will be fine.
            return Objects.equals(docRef.getName(), attribute)
                   || Objects.equals(docRef.getUuid(), attribute);
        }

        return false;
    }

    private String[] loadWords(final DocRef docRef) {
        if (wordListProvider == null) {
            return null;
        }

        return wordMap.computeIfAbsent(docRef, k -> {
            final String[] words = wordListProvider.getWords(docRef);
            return words;
        });
    }

    private long getDate(final String fieldName, final Object value) {
        try {
            if (value instanceof Long) {
                return (Long) value;
            }

            //empty optional will be caught below
            return DateExpressionParser.getMs(
                    fieldName,
                    value.toString(),
                    dateTimeSettings);
        } catch (final Exception e) {
            throw new MatchException(e.getMessage());
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


    // --------------------------------------------------------------------------------


    private static class MatchException extends RuntimeException {

        MatchException(final String message) {
            super(message);
        }
    }
}
