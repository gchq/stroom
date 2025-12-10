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

package stroom.security.shared;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides mechanisms for filtering data based on a quick filter terms, e.g.
 * 'foo bar type:feed'.
 * Multiple terms can be used delimited with spaces. Terms can be qualified with the name of the
 * field. If not qualified then the default field(s) are used. Terms can be prefixed/suffixed with special
 * characters that will change the match mode, e.g. regex, negation.
 * <p>
 * To see how it works in action run QuickFilterTestBed#main()
 */
public class QuickFilterExpressionParser {

    private static final char QUALIFIER_DELIMITER_CHAR = ':';
    private static final char SPLIT_CHAR = ' ';
    private static final char QUOTE_CHAR = '\"';
    private static final char ESCAPE_CHAR = '\\';

    private static final String QUALIFIER_DELIMITER_STR = Character.toString(QUALIFIER_DELIMITER_CHAR);
    private static final String QUOTE_STR = Character.toString(QUOTE_CHAR);
    private static final String ESCAPED_QUOTE_STR = Character.toString(ESCAPE_CHAR) + QUOTE_CHAR;

    private QuickFilterExpressionParser() {
        // Utility.
    }

    public static ExpressionOperator parse(final String userInput,
                                           final Set<QueryField> defaultFields,
                                           final Map<String, QueryField> fieldMap) {

        // user input like 'vent type:pipe' or just 'vent'

        // If the default field mapper is two fields, field1 & field2 then "bad stuff" is really
        // (field1:bad OR field2:bad) AND (field1:stuff OR field2:stuff)

        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        if (NullSafe.isNonBlankString(userInput)) {
            extractMatchTokens(userInput, defaultFields, fieldMap, builder);
        }
        return builder.build();
    }

    private static void extractMatchTokens(final String userInput,
                                           final Set<QueryField> defaultFields,
                                           final Map<String, QueryField> fieldMap,
                                           final ExpressionOperator.Builder builder) {
        final List<String> parts = splitInput(userInput);
        for (final String part : parts) {
            try {
                if (part.contains(QUALIFIER_DELIMITER_STR)) {
                    final String[] subParts = part.split(QUALIFIER_DELIMITER_STR);
                    if (part.endsWith(QUALIFIER_DELIMITER_STR)) {
                        final String fieldName = subParts[0];
                        final QueryField field = fieldMap.get(fieldName);
                        if (field == null) {
                            throw new RuntimeException("Unknown qualifier '" + fieldName
                                                       + "'. Valid qualifiers: " + fieldMap.keySet());
                        }
                        builder.addTerm(fieldName, Condition.EQUALS, "");

                    } else if (part.startsWith(QUALIFIER_DELIMITER_STR)) {
                        throw new RuntimeException("Invalid token " + part);
                    } else {
                        final String fieldName = subParts[0];
                        final QueryField field = fieldMap.get(fieldName);
                        if (field == null) {
                            throw new RuntimeException("Unknown qualifier '" + fieldName
                                                       + "'. Valid qualifiers: " + fieldMap.keySet());
                        }
                        final String fieldValue = subParts[1];
                        addTerm(builder, field, fieldValue);
                    }
                } else {
                    if (defaultFields == null || defaultFields.isEmpty()) {
                        throw new RuntimeException("No default fields defined");
                    }

                    if (defaultFields.size() == 1) {
                        addTerm(builder, defaultFields.iterator().next(), part);
                    } else {
                        final ExpressionOperator.Builder eb = ExpressionOperator.builder().op(Op.OR);
                        for (final QueryField field : defaultFields) {
                            addTerm(eb, field, part);
                        }
                        builder.addOperator(eb.build());
                    }
                }
            } catch (final Exception e) {
                // Probably due to the user not having finished typing yet
                throw new RuntimeException("Unable to split [" + part + "], due to " + e.getMessage(), e);
            }
        }
    }

    private static void addTerm(final ExpressionOperator.Builder builder,
                                final QueryField queryField,
                                final String value) {
        if (FieldType.TEXT.equals(queryField.getFldType())) {
            builder.addTerm(queryField.getFldName(), Condition.EQUALS, wildcard(value));
        } else {
            builder.addTerm(queryField.getFldName(), Condition.EQUALS, value);
        }
    }

    private static String wildcard(String value) {
        if (!value.endsWith("*")) {
            value = value + "*";
        }
        if (!value.startsWith("*")) {
            value = "*" + value;
        }
        return value;
    }

    /**
     * Split the input on spaces with each chunk optionally enclosed with a double quotes.
     * Should ignore leading, trailing repeated spaces.
     *
     * @return An empty list if it can't parse
     */
    private static List<String> splitInput(final String userInput) {
        final List<String> tokens = new ArrayList<>();
        final String cleanedInput = userInput.trim();

        int start = 0;
        boolean insideQuotes = false;
        boolean wasInsideQuotes = false;
        char lastChar = 0;
        int unEscapedQuoteCount = 0;

        try {
            for (int current = 0; current < cleanedInput.length(); current++) {
                final char currentChar = cleanedInput.charAt(current);
                if (currentChar == QUOTE_CHAR && lastChar != ESCAPE_CHAR) {
                    unEscapedQuoteCount++;
                    if (!insideQuotes) {
                        // start of quotes
                        wasInsideQuotes = true;
                    }
                    insideQuotes = !insideQuotes; // toggle state
                }

                final boolean atLastChar = (current == cleanedInput.length() - 1);

                if (atLastChar) {
                    if (wasInsideQuotes) {
                        // Strip the quotes off
                        tokens.add(deEscape(cleanedInput.substring(start + 1, cleanedInput.length() - 1)));
                    } else {
                        tokens.add(deEscape(cleanedInput.substring(start)));
                    }
                } else if (currentChar == SPLIT_CHAR && !insideQuotes) {
                    // allow for multiple spaces
                    if (currentChar != lastChar) {
                        if (wasInsideQuotes) {
                            // Strip the quotes off
                            tokens.add(deEscape(cleanedInput.substring(start + 1, current - 1)));
                            // clear the state
                            wasInsideQuotes = false;
                        } else {
                            tokens.add(deEscape(cleanedInput.substring(start, current)));
                        }
                    }
                    start = current + 1;
                }
                lastChar = currentChar;
            }
            if (unEscapedQuoteCount % 2 != 0) {
//                LOGGER.trace("Odd number of quotes ({}) in input [{}], can't parse",
//                        unEscapedQuoteCount, userInput);
                tokens.clear();
            }
        } catch (final Exception e) {
//            LOGGER.trace("Unable to parse [{}] due to: {}", userInput, e.getMessage(), e);
            // Don't want to throw as it may be unfinished user input.
        }
//        LOGGER.trace("tokens {}", tokens);
        return tokens;
    }

    private static String deEscape(final String input) {
        return input.replace(ESCAPED_QUOTE_STR, QUOTE_STR);
    }
}
