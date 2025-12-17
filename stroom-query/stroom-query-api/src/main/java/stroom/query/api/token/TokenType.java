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

package stroom.query.api.token;

import stroom.util.shared.NullSafe;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

public enum TokenType {
    // Uncategorized content.
    UNKNOWN,

    OPEN_BRACKET,
    CLOSE_BRACKET,

    // Structure
    TOKEN_GROUP,
    FUNCTION_GROUP,
    FUNCTION_NAME,

    // Strings
    WHITESPACE,
    SINGLE_QUOTED_STRING,
    DOUBLE_QUOTED_STRING,
    STRING,
    PARAM,

    DATE_TIME,
    DURATION,
    NUMBER,


    COMMA,

    // Numeric operators
    ORDER,
    DIVISION,
    MULTIPLICATION,
    MODULUS,
    PLUS,
    MINUS,

    // Conditions
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL_TO,
    LESS_THAN,
    LESS_THAN_OR_EQUAL_TO,
    IS_NULL,
    IS_NOT_NULL,
    IN,
    DICTIONARY,

    // Logic
    AND,
    OR,
    NOT,

    // Other keywords
    FROM,
    WHERE,
    EVAL,
    SELECT,
    SORT,
    GROUP,
    FILTER,
    WINDOW,
    LIMIT,
    HAVING,
    SHOW,

    // Additional tokens
    BY,
    AS,
    BETWEEN,
    BETWEEN_AND,


    // Comments
    COMMENT,
    BLOCK_COMMENT;

    public static final Set<TokenType> ALL_KEYWORDS = Collections.unmodifiableSet(EnumSet.of(
            FROM,
            WHERE,
            FILTER,
            EVAL,
            SELECT,
            SORT,
            GROUP,
            WINDOW,
            LIMIT,
            HAVING,
            SHOW));

    public static final Set<TokenType> EXPRESSION_KEYWORDS = Set.of(
            WHERE,
            FILTER,
            HAVING);

    public static final Set<TokenType> ALL_CONDITIONS = Collections.unmodifiableSet(EnumSet.of(
            EQUALS,
            NOT_EQUALS,
            GREATER_THAN,
            GREATER_THAN_OR_EQUAL_TO,
            LESS_THAN,
            LESS_THAN_OR_EQUAL_TO,
            IS_NULL,
            IS_NOT_NULL,
            IN,
            BETWEEN));

    public static final Set<TokenType> ALL_BODMAS = Collections.unmodifiableSet(EnumSet.of(
            ORDER,
            DIVISION,
            MULTIPLICATION,
            MODULUS,
            PLUS,
            MINUS));

    public static final Set<TokenType> ALL_VALUES = Collections.unmodifiableSet(EnumSet.of(
            SINGLE_QUOTED_STRING,
            DOUBLE_QUOTED_STRING,
            STRING,
            NUMBER));

    public static final Set<TokenType> ALL_STRINGS = Collections.unmodifiableSet(EnumSet.of(
            SINGLE_QUOTED_STRING,
            DOUBLE_QUOTED_STRING,
            STRING,
            PARAM));

    public static final Set<TokenType> ALL_LOGICAL_OPERATORS = Collections.unmodifiableSet(EnumSet.of(
            AND,
            OR,
            NOT));

    public static final Set<TokenType> ALL = Collections.unmodifiableSet(EnumSet.allOf(TokenType.class));

    public static final Set<TokenType> EMPTY = Collections.unmodifiableSet(EnumSet.noneOf(TokenType.class));

    /**
     * WHERE => Set(FROM) means FROM **must** appear before WHERE.
     */
    public static final Map<TokenType, Set<TokenType>> KEYWORDS_REQUIRED_BEFORE = Collections.unmodifiableMap(
            new EnumMap<>(Map.ofEntries(
                    entry(FROM, Collections.emptySet()),
                    entry(WHERE, EnumSet.of(FROM)),
                    entry(AND, EnumSet.of(FROM)),
                    entry(OR, EnumSet.of(FROM)),
                    entry(NOT, EnumSet.of(FROM)),
                    entry(EVAL, EnumSet.of(FROM)),
                    entry(WINDOW, EnumSet.of(FROM)),
                    entry(FILTER, EnumSet.of(FROM)),
                    entry(SORT, EnumSet.of(FROM)),
                    entry(GROUP, EnumSet.of(FROM)),
                    entry(HAVING, EnumSet.of(FROM)),
                    entry(LIMIT, EnumSet.of(FROM)),
                    entry(SELECT, EnumSet.of(FROM)),
                    entry(SHOW, EnumSet.of(FROM, SELECT)))));

    /**
     * EVAL => Set(FROM, WHERE) means FROM and/or WHERE are valid to appear before EVAL.
     */
    public static final Map<TokenType, Set<TokenType>> KEYWORDS_VALID_BEFORE = Collections.unmodifiableMap(
            new EnumMap<>(Map.ofEntries(
                    entry(FROM, Collections.emptySet()),
                    entry(WHERE, EnumSet.of(FROM)),
                    // AND/OR/NOT can appear after WHERE, FILTER and HAVING
                    entry(AND, EnumSet.of(FROM, WHERE, EVAL, WINDOW, FILTER, SORT, GROUP, HAVING)),
                    entry(OR, EnumSet.of(FROM, WHERE, EVAL, WINDOW, FILTER, SORT, GROUP, HAVING)),
                    entry(NOT, EnumSet.of(FROM, WHERE, EVAL, WINDOW, FILTER, SORT, GROUP, HAVING)),
                    entry(EVAL, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL, WINDOW)),
                    entry(WINDOW, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL)),
                    entry(FILTER, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL, WINDOW)),
                    entry(SORT, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL, WINDOW, FILTER, GROUP)),
                    // Multiple 'group by's are allowed for nested grouping
                    entry(GROUP, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL, WINDOW, FILTER, SORT, GROUP)),
                    entry(HAVING, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL, WINDOW, FILTER, SORT, GROUP)),
                    entry(LIMIT, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL, WINDOW, FILTER, SORT, GROUP, HAVING)),
                    entry(SELECT, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL,
                            WINDOW, FILTER, SORT, GROUP, HAVING, LIMIT)),
                    entry(SHOW, EnumSet.of(FROM, WHERE, AND, OR, NOT, EVAL,
                            WINDOW, FILTER, SORT, GROUP, HAVING, SELECT, LIMIT))
            )));

    /**
     * SELECT => Set(LIMIT, SHOW) means LIMIT and/or SHOW are valid to appear after SELECT.
     */
    public static final Map<TokenType, Set<TokenType>> KEYWORDS_VALID_AFTER;

    static {
        // Essentially loop over VALID_PRIOR_KEYWORDS, reversing the associations
        final Map<TokenType, Set<TokenType>> validNextKeywords = new HashMap<>(KEYWORDS_VALID_BEFORE.size());
        KEYWORDS_VALID_BEFORE.forEach((tokenType, validPriorTokens) -> {
            validNextKeywords.computeIfAbsent(tokenType, k -> EnumSet.noneOf(TokenType.class));
            for (final TokenType priorType : validPriorTokens) {
                if (KEYWORDS_VALID_BEFORE.containsKey(priorType)) {
                    validNextKeywords.computeIfAbsent(priorType, k -> EnumSet.noneOf(TokenType.class))
                            .add(tokenType);
                }
            }
        });

        validNextKeywords.forEach((tokenType, tokenTypes) -> {
            validNextKeywords.put(tokenType, set(tokenTypes));
        });
        KEYWORDS_VALID_AFTER = Collections.unmodifiableMap(new EnumMap<>(validNextKeywords));

//        LOGGER.trace("KEYWORDS:\n{}",
//                ALL_KEYWORDS.stream()
//                        .map(keyword -> {
//                            return LogUtil.message("""
//                                            {}
//                                              Required before: {}
//                                              Valid before:    {}
//                                              Valid after:     {}""",
//                                    keyword,
//                                    KEYWORDS_REQUIRED_BEFORE.get(keyword),
//                                    KEYWORDS_VALID_BEFORE.get(keyword),
//                                    KEYWORDS_VALID_AFTER.get(keyword));
//                        })
//                        .collect(Collectors.joining("\n")));
    }

    public static boolean isKeyword(final TokenType tokenType) {
        return tokenType != null && ALL_KEYWORDS.contains(tokenType);
    }

    /**
     * @return A set of {@link TokenType}s that MUST appear in the query before tokenType.
     */
    public static Set<TokenType> getKeywordsRequiredBefore(final TokenType tokenType) {
        return getKeywordSet(tokenType, KEYWORDS_REQUIRED_BEFORE);
    }

    /**
     * @return A set of {@link TokenType}s that are valid to appear in the query before tokenType.
     */
    public static Set<TokenType> getKeywordsValidBefore(final TokenType tokenType) {
        return getKeywordSet(tokenType, KEYWORDS_VALID_BEFORE);
    }

    /**
     * @return A set of {@link TokenType}s that are valid to appear in the query after tokenType.
     */
    public static Set<TokenType> getKeywordsValidAfter(final TokenType tokenType) {
        return getKeywordSet(tokenType, KEYWORDS_VALID_AFTER);
    }

    private static Set<TokenType> getKeywordSet(final TokenType tokenType, final Map<TokenType, Set<TokenType>> map) {
        if (tokenType != null) {
            final Set<TokenType> set = map.get(tokenType);
            if (set != null) {
                return set;
            }
        }
        return Collections.emptySet();
    }

    public static boolean isString(final AbstractToken token) {
        return token != null && ALL_STRINGS.contains(token.getTokenType());
    }

    public static Set<TokenType> exclude(final Set<TokenType> fullSet,
                                         final TokenType... excludedTypes) {
        final Set<TokenType> filteredSet = new HashSet<>(fullSet);
        NullSafe.asList(excludedTypes)
                .forEach(filteredSet::remove);
        return set(filteredSet);
    }

    /**
     * @param contiguous If true, tokenTypes must be seen as a contiguous block rather
     *                   than separated by other {@link TokenType}s.
     * @return True if tokenTypes have been seen in order in lastKeywordSequence
     */
    public static boolean haveSeen(final List<TokenType> tokenTypeSequence,
                                   final boolean contiguous,
                                   final TokenType... tokenTypes) {
        if (NullSafe.isEmptyCollection(tokenTypeSequence)) {
            return false;
        } else if (NullSafe.isEmptyArray(tokenTypes)) {
            return false;
        } else if (tokenTypeSequence.size() < tokenTypes.length) {
            return false;
        } else {
            int startIdx = 0;
            int seenCount = 0;
            for (final TokenType requiredType : tokenTypes) {
                Objects.requireNonNull(requiredType);
                boolean foundType = false;
                for (int j = startIdx; j < tokenTypeSequence.size(); j++) {
                    final TokenType seenType = tokenTypeSequence.get(j);
                    if (requiredType == seenType) {
                        foundType = true;
                        startIdx = j + 1;
                        seenCount++;
                        break;
                    } else if (contiguous && seenCount > 0 && tokenTypes.length > 1) {
                        break;
                    }
                }
                if (!foundType) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * @return True if tokenTypes are the last contiguous sequence of {@link TokenType}s
     * seen at the end of tokenTypeSequence.
     */
    public static boolean haveSeenLast(final List<TokenType> tokenTypeSequence,
                                       final TokenType... tokenTypes) {
        if (NullSafe.isEmptyCollection(tokenTypeSequence)) {
            return false;
        } else if (NullSafe.isEmptyArray(tokenTypes)) {
            return true;
        } else if (tokenTypeSequence.size() < tokenTypes.length) {
            return false;
        } else {
            int seqIdx = tokenTypeSequence.size() - 1;
            for (int i = tokenTypes.length - 1; i >= 0; i--) {
                if (tokenTypeSequence.get(seqIdx--) != tokenTypes[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static Entry<TokenType, Set<TokenType>> entry(final TokenType tokenType,
                                                          final Set<TokenType> set) {
        return Map.entry(tokenType, set(set));
    }

    private static Set<TokenType> set(final Set<TokenType> set) {
        if (NullSafe.isEmptyCollection(set)) {
            return Collections.emptySet();
        } else if (set instanceof EnumSet<TokenType>) {
            return Collections.unmodifiableSet(set);
        } else {
            return Collections.unmodifiableSet(EnumSet.copyOf(set));
        }
    }
}
