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

package stroom.query.common.v2;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionPredicateFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExpressionPredicateFactory.class);
    private static final String DELIMITER = ",";
    private static final Comparator<Score> SCORE_COMPARATOR = Comparator
            .comparingInt(Score::length)
            .thenComparing(Score::index);

    private final WordListProvider wordListProvider;

    public ExpressionPredicateFactory() {
        this.wordListProvider = null;
    }

    @Inject
    ExpressionPredicateFactory(final WordListProvider wordListProvider) {
        this.wordListProvider = wordListProvider;
    }

    public Stream<String> filterAndSortStream(final Stream<String> stream,
                                              final String filter,
                                              final Optional<Comparator<String>> optionalSecondComparator) {
        final String fieldName = "name";
        final FieldProvider fieldProvider = new SingleFieldProvider(fieldName);
        final ValueFunctionFactories<String> valueFunctionFactory =
                StringValueFunctionFactory.create(QueryField.createText(fieldName));
        return filterAndSortStream(
                stream,
                filter,
                fieldProvider,
                valueFunctionFactory,
                DateTimeSettings.builder().build(),
                optionalSecondComparator);
    }

    public <T> Stream<T> filterAndSortStream(final Stream<T> stream,
                                             final String filter,
                                             final FieldProvider fieldProvider,
                                             final ValueFunctionFactories<T> valueFunctionFactories,
                                             final Optional<Comparator<T>> optionalSecondComparator) {
        return filterAndSortStream(
                stream,
                filter,
                fieldProvider,
                valueFunctionFactories,
                DateTimeSettings.builder().build(),
                optionalSecondComparator);
    }

    public <T> Stream<T> filterAndSortStream(final Stream<T> stream,
                                             final String filter,
                                             final FieldProvider fieldProvider,
                                             final ValueFunctionFactories<T> valueFunctionFactories,
                                             final DateTimeSettings dateTimeSettings,
                                             final Optional<Comparator<T>> optionalSecondComparator) {
        final Optional<ScoringPredicate<T>> optionalScoringPredicate =
                createOptionalScoringPredicate(filter, fieldProvider, valueFunctionFactories, dateTimeSettings);

        // If we have no predicate then just sort and return.
        if (optionalScoringPredicate.isEmpty()) {
            return optionalSecondComparator
                    .map(comparator -> stream.sorted(comparator))
                    .orElse(stream);
        }

        // Create combined comparator.
        final ScoringPredicate<T> scoringPredicate = optionalScoringPredicate.get();
        final Comparator<ScoredObject<T>> scoreComparator = Comparator
                .comparing(ScoredObject::score, SCORE_COMPARATOR);
        final Comparator<ScoredObject<T>> comparator = optionalSecondComparator
                .map(secondComparator -> scoreComparator.thenComparing(scoredObject -> scoredObject.t,
                        secondComparator))
                .orElse(scoreComparator);

        return stream
                .map(t -> new ScoredObject<>(t, scoringPredicate.score(t))) // Wrap and score
                .filter(scoredObject -> scoredObject.score.matches) // Filter scored
                .sorted(comparator) // Sort by score.
                .map(scoredObject -> scoredObject.t); // Unwrap
    }

    public Predicate<String> create(final String filter) {
        return create(filter, Function.identity());
    }

    public <T> Predicate<T> create(final String filter,
                                   final Function<T, String> function) {
        return createOptional(filter, function).orElse(matchAll());
    }

    public <T> Optional<Predicate<T>> createOptional(final String filter,
                                                     final Function<T, String> function) {
        try {
            final String fieldName = "name";
            final FieldProvider fieldProvider = new SingleFieldProvider(fieldName);
            final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                    .create(fieldProvider, filter);
            if (optionalExpressionOperator.isPresent()) {
                final ValueFunctionFactories<String> valueFunctionFactory =
                        StringValueFunctionFactory.create(QueryField.createText(fieldName));
                final Optional<Predicate<String>> predicateOptional = createOptional(
                        optionalExpressionOperator.get(),
                        valueFunctionFactory);
                return predicateOptional
                        .map(predicate -> queryField -> predicate.test(function.apply(queryField)));
            }
            return Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return Optional.of(matchNone());
        }
    }

    public <T> Predicate<T> create(final String filter,
                                   final FieldProvider fieldProvider,
                                   final ValueFunctionFactories<T> valueFunctionFactories,
                                   final DateTimeSettings dateTimeSettings) {
        return createOptionalScoringPredicate(
                filter,
                fieldProvider,
                valueFunctionFactories,
                dateTimeSettings).orElse(matchAll());
    }

    @SuppressWarnings("checkstyle:linelength")
    private <T> Optional<ScoringPredicate<T>> createOptionalScoringPredicate(
            final String filter,
            final FieldProvider fieldProvider,
            final ValueFunctionFactories<T> valueFunctionFactories,
            final DateTimeSettings dateTimeSettings) {

        try {
            final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                    .create(fieldProvider, filter);
            return optionalExpressionOperator.flatMap(expressionOperator -> {
                return createOptionalScoringPredicate(expressionOperator,
                        valueFunctionFactories,
                        dateTimeSettings);
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return Optional.of(matchNone());
        }
    }

    /**
     * Create a Predicate for operator using valueFunctionFactories to provide the values to test against.
     * If operator is null or has no enabled terms it returns a match all predicate.
     */
    public <T> Predicate<T> create(final ExpressionOperator operator,
                                   final ValueFunctionFactories<T> valueFunctionFactories) {
        return create(operator, valueFunctionFactories, DateTimeSettings.builder().build());
    }

    private <T> Optional<Predicate<T>> createOptional(final ExpressionOperator operator,
                                                      final ValueFunctionFactories<T> valueFunctionFactories) {
        return createOptional(operator, valueFunctionFactories, DateTimeSettings.builder().build());
    }

    private <T> Predicate<T> create(final ExpressionOperator operator,
                                    final ValueFunctionFactories<T> valueFunctionFactories,
                                    final DateTimeSettings dateTimeSettings) {
        return createOptionalScoringPredicate(operator, valueFunctionFactories, dateTimeSettings)
                .orElse(matchAll());
    }

    @SuppressWarnings("checkstyle:linelength")
    private <T> Optional<ScoringPredicate<T>> createOptionalScoringPredicate(
            final ExpressionOperator operator,
            final ValueFunctionFactories<T> valueFunctionFactories,
            final DateTimeSettings dateTimeSettings) {

        if (operator == null) {
            return Optional.empty();
        }

        return createScoringPredicate(operator, valueFunctionFactories, dateTimeSettings, wordListProvider);
    }

    public <T> Optional<Predicate<T>> createOptional(final ExpressionOperator operator,
                                                     final ValueFunctionFactories<T> valueFunctionFactories,
                                                     final DateTimeSettings dateTimeSettings) {
        if (operator == null) {
            return Optional.empty();
        }

        return createScoringPredicate(operator, valueFunctionFactories, dateTimeSettings, wordListProvider)
                .map(p -> (Predicate<T>) p);
    }

    @SuppressWarnings("checkstyle:linelength")
    private <T> Optional<ScoringPredicate<T>> createScoringPredicate(
            final ExpressionItem item,
            final ValueFunctionFactories<T> valueFunctionFactories,
            final DateTimeSettings dateTimeSettings,
            final WordListProvider wordListProvider) {

        if (!item.enabled()) {
            return Optional.empty();
        }

        if (item instanceof final ExpressionOperator expressionOperator) {
            return createOperatorPredicate(
                    expressionOperator,
                    valueFunctionFactories,
                    dateTimeSettings,
                    wordListProvider);
        } else if (item instanceof final ExpressionTerm expressionTerm) {
            return createTermPredicate(
                    expressionTerm,
                    valueFunctionFactories,
                    dateTimeSettings,
                    wordListProvider);
        } else {
            throw new MatchException("Unexpected item type");
        }
    }

    private <T> Optional<ScoringPredicate<T>> createOperatorPredicate(
            final ExpressionOperator operator,
            final ValueFunctionFactories<T> valueFunctionFactories,
            final DateTimeSettings dateTimeSettings,
            final WordListProvider wordListProvider) {

        // If the operator is not enabled then ignore this branch.
        if (!operator.enabled()) {
            return Optional.empty();
        }

        // Create child predicates.
        final List<ScoringPredicate<T>> predicates;
        if (operator.getChildren() != null && !operator.getChildren().isEmpty()) {
            predicates = new ArrayList<>(operator.getChildren().size());
            for (final ExpressionItem child : operator.getChildren()) {
                final Optional<ScoringPredicate<T>> optional = createScoringPredicate(
                        child,
                        valueFunctionFactories,
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
                    yield Optional.of(matchNone());
                }
                yield NotPredicate.create(predicates.getFirst());
            }
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<ScoringPredicate<T>> createTermPredicate(
            final ExpressionTerm term,
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
    private <T> Optional<ScoringPredicate<T>> createGeneralTermPredicate(
            final ExpressionTerm term,
            final ValueFunctionFactory<T> valueFunctionFactory,
            final DateTimeSettings dateTimeSettings,
            final WordListProvider wordListProvider) {

        try {
            return createDateTermPredicate(term, valueFunctionFactory, dateTimeSettings, wordListProvider);
        } catch (final RuntimeException e) {
            try {
                return createNumericTermPredicate(term, valueFunctionFactory, wordListProvider);
            } catch (final RuntimeException e2) {
                return createTextTermPredicate(term, valueFunctionFactory, wordListProvider);
            }
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<ScoringPredicate<T>> createDateTermPredicate(
            final ExpressionTerm term,
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
            default -> createTextTermPredicate(term, valueFunctionFactory, wordListProvider);
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<ScoringPredicate<T>> createNumericTermPredicate(
            final ExpressionTerm term,
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
            default -> createTextTermPredicate(term, valueFunctionFactory, wordListProvider);
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private <T> Optional<ScoringPredicate<T>> createTextTermPredicate(
            final ExpressionTerm term,
            final ValueFunctionFactory<T> valueFunctionFactory,
            final WordListProvider wordListProvider) {

        final Function<T, String> stringExtractor = valueFunctionFactory.createStringExtractor();
        return switch (term.getCondition()) {
            case EQUALS -> StringEquals.create(term, stringExtractor);
            case EQUALS_CASE_SENSITIVE -> StringEqualsCaseSensitive.create(term, stringExtractor);
            case NOT_EQUALS -> NotPredicate.create(StringEquals.create(term, stringExtractor));
            case NOT_EQUALS_CASE_SENSITIVE -> NotPredicate.create(
                    StringEqualsCaseSensitive.create(term, stringExtractor));
            case CONTAINS -> StringContains.create(term, stringExtractor);
            case CONTAINS_CASE_SENSITIVE -> StringContainsCaseSensitive.create(term, stringExtractor);
            case GREATER_THAN -> StringGreaterThan.create(term, stringExtractor);
            case GREATER_THAN_OR_EQUAL_TO -> StringGreaterThanOrEqual.create(term, stringExtractor);
            case LESS_THAN -> StringLessThan.create(term, stringExtractor);
            case LESS_THAN_OR_EQUAL_TO -> StringLessThanOrEqual.create(term, stringExtractor);
            case STARTS_WITH -> StringStartsWith.create(term, stringExtractor);
            case STARTS_WITH_CASE_SENSITIVE -> StringStartsWithCaseSensitive.create(term, stringExtractor);
            case ENDS_WITH -> StringEndsWith.create(term, stringExtractor);
            case ENDS_WITH_CASE_SENSITIVE -> StringEndsWithCaseSensitive.create(term, stringExtractor);
            case MATCHES_REGEX -> StringRegex.create(term, stringExtractor);
            case MATCHES_REGEX_CASE_SENSITIVE -> StringRegexCaseSensitive.create(term, stringExtractor);
            case WORD_BOUNDARY -> StringWordBoundary.create(term, stringExtractor);
            case IN -> StringIn.create(term, stringExtractor);
            case IN_DICTIONARY -> StringInDictionary.create(term, stringExtractor, wordListProvider);
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


    private static Score regexScore(final Matcher matcher) {
        int bestMatchLen = -1;
        int bestMatchIdx = -1;

        while (matcher.find()) {
            final int matchLen = matcher.end() - matcher.start();
            final int matchIdx = matcher.start();
            if (bestMatchLen == -1 || matchLen < bestMatchLen) {
                bestMatchLen = matchLen;
                bestMatchIdx = matchIdx;
            }
        }

        if (bestMatchLen == -1) {
            return Score.NONE;
        } else {
            return new Score(true, bestMatchLen, bestMatchIdx);
        }
    }


    public static String replaceWildcards(final String value) {
        boolean escaped = false;

        final char[] chars = value.toCharArray();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (escaped) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(c);
                } else {
                    // Might be a special char so escape it
                    sb.append(Pattern.quote(String.valueOf(c)));
                }
                escaped = false;
            } else if (c == '\\' && chars.length > i + 1 && (chars[i + 1] == '*' || chars[i + 1] == '?')) {
                escaped = true;
            } else if (c == '*') {
                sb.append('.');
                sb.append(c);
            } else if (c == '?') {
                sb.append('.');
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                // Might be a special char so escape it
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    public static boolean containsWildcard(final String value) {
        boolean escaped = false;
        final char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && chars.length > i + 1 && (chars[i + 1] == '*' || chars[i + 1] == '?')) {
                escaped = true;
            } else if (c == '*') {
                return true;
            } else if (c == '?') {
                return true;
            }
        }
        return false;
    }

    public static String unescape(final String value) {
        boolean escaped = false;
        final char[] chars = value.toCharArray();
        final StringBuilder sb = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\' && chars.length > i + 1 && (chars[i + 1] == '*' || chars[i + 1] == '?')) {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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

    private static <T> ScoringPredicate<T> matchAll() {
        return ScoringPredicate.matchAll();
    }

    private static <T> ScoringPredicate<T> matchNone() {
        return ScoringPredicate.matchNone();
    }

    private static <T> Optional<ScoringPredicate<T>> ifValue(final ExpressionTerm term,
                                                             final Supplier<ScoringPredicate<T>> supplier) {
        if (NullSafe.isBlankString(term.getValue())) {
            return Optional.empty();
        }
        return Optional.of(supplier.get());
    }


    // --------------------------------------------------------------------------------


    private static class MatchException extends RuntimeException {

        MatchException(final String message) {
            super(message);
        }
    }


    // --------------------------------------------------------------------------------


    public interface ValueFunctionFactories<T> {

        ValueFunctionFactory<T> get(String fieldName);
    }


    // --------------------------------------------------------------------------------


    public interface ValueFunctionFactory<T> {

        Function<T, Boolean> createNullCheck();

        Function<T, String> createStringExtractor();

        Function<T, Long> createDateExtractor();

        Function<T, Double> createNumberExtractor();

        FieldType getFieldType();
    }


    // --------------------------------------------------------------------------------


    private static class AndPredicate<T> implements ScoringPredicate<T> {

        private final List<ScoringPredicate<T>> subPredicates;

        public AndPredicate(final List<ScoringPredicate<T>> subPredicates) {
            this.subPredicates = subPredicates;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final List<ScoringPredicate<T>> subPredicates) {
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

        @Override
        public Score score(final T t) {
            // Get the best score.
            Score currentScore = null;
            for (final ScoringPredicate<T> valuesPredicate : subPredicates) {
                final Score score = valuesPredicate.score(t);
                // If we get no match then return early.
                if (!score.matches) {
                    return score;
                }
                if (currentScore != null) {
                    if (SCORE_COMPARATOR.compare(currentScore, score) < 0) {
                        currentScore = score;
                    }
                } else {
                    currentScore = score;
                }
            }
            return currentScore;
        }
    }


    // --------------------------------------------------------------------------------


    private static class OrPredicate<T> implements ScoringPredicate<T> {

        private final List<ScoringPredicate<T>> subPredicates;

        public OrPredicate(final List<ScoringPredicate<T>> subPredicates) {
            this.subPredicates = subPredicates;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final List<ScoringPredicate<T>> subPredicates) {
            return Optional.of(new OrPredicate<>(subPredicates));
        }

        @Override
        public boolean test(final T t) {
            for (final ScoringPredicate<T> valuesPredicate : subPredicates) {
                if (valuesPredicate.test(t)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Score score(final T t) {
            // Get the best score.
            Score currentScore = null;
            for (final ScoringPredicate<T> valuesPredicate : subPredicates) {
                final Score score = valuesPredicate.score(t);
                if (score.matches) {
                    if (currentScore != null) {
                        if (SCORE_COMPARATOR.compare(currentScore, score) < 0) {
                            currentScore = score;
                        }
                    } else {
                        currentScore = score;
                    }
                }
            }
            return currentScore == null
                    ? Score.NONE
                    : currentScore;
        }
    }


    // --------------------------------------------------------------------------------


    private static class NotPredicate<T> implements ScoringPredicate<T> {

        private final ScoringPredicate<T> subPredicate;

        public NotPredicate(final ScoringPredicate<T> subPredicate) {
            this.subPredicate = subPredicate;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ScoringPredicate<T> subPredicate) {
            return Optional.of(new NotPredicate<>(subPredicate));
        }

        private static <T> Optional<ScoringPredicate<T>> create(final Optional<ScoringPredicate<T>> subPredicate) {
            return subPredicate.map(NotPredicate::new);
        }

        @Override
        public boolean test(final T values) {
            return !subPredicate.test(values);
        }

        @Override
        public Score score(final T t) {
            // Invert the score.
            final Score score = subPredicate.score(t);
            return new Score(
                    !score.matches,
                    Integer.MAX_VALUE - score.length,
                    Integer.MAX_VALUE - score.index);
        }
    }


    // --------------------------------------------------------------------------------


    private abstract static class ExpressionTermPredicate<T> implements ScoringPredicate<T> {

        final ExpressionTerm term;

        private ExpressionTermPredicate(final ExpressionTerm term) {
            this.term = term;
        }

        @Override
        public String toString() {
            return term.toString();
        }

        @Override
        public Score score(final T t) {
            return test(t)
                    ? Score.MATCH
                    : Score.NONE;
        }
    }


    // --------------------------------------------------------------------------------


    private static class IsNullPredicate<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Boolean> nullCheckFunction;

        private IsNullPredicate(final ExpressionTerm term,
                                final Function<T, Boolean> nullCheckFunction) {
            super(term);
            this.nullCheckFunction = nullCheckFunction;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Boolean> nullCheckFunction) {
            return Optional.of(new IsNullPredicate<>(term, nullCheckFunction));
        }

        @Override
        public boolean test(final T values) {
            return nullCheckFunction.apply(values);
        }
    }


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


    private static class NumericEquals<T> extends NumericExpressionTermPredicate<T> {

        private NumericEquals(final ExpressionTerm term,
                              final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class NumericGreaterThan<T> extends NumericExpressionTermPredicate<T> {

        private NumericGreaterThan(final ExpressionTerm term,
                                   final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericGreaterThan<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            final int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal > 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class NumericGreaterThanOrEqualTo<T> extends NumericExpressionTermPredicate<T> {

        private NumericGreaterThanOrEqualTo(final ExpressionTerm term,
                                            final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericGreaterThanOrEqualTo<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            final int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal >= 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class NumericLessThan<T> extends NumericExpressionTermPredicate<T> {

        private NumericLessThan(final ExpressionTerm term,
                                final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericLessThan<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            final int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal < 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class NumericLessThanOrEqualTo<T> extends NumericExpressionTermPredicate<T> {

        private NumericLessThanOrEqualTo(final ExpressionTerm term,
                                         final Function<T, Double> extractionFunction) {
            super(term, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Double> extractionFunction) {
            return ifValue(term, () -> new NumericLessThanOrEqualTo<>(term, extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            final int compVal = CompareUtil.compareDouble(val, termNum);
            return compVal <= 0;
        }
    }


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Double> extractionFunction) {
            final Double[] in = getTermNumbers(term, term.getValue());
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(matchNone());
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


    // --------------------------------------------------------------------------------


    private static class NumericInDictionary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Double> extractionFunction;
        private final Set<Double> in;

        private NumericInDictionary(final ExpressionTerm term,
                                    final Function<T, Double> extractionFunction,
                                    final Set<Double> in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Double> extractionFunction,
                                                                final WordListProvider wordListProvider) {
            final String[] words;
            if (term.getDocRef() != null) {
                words = wordListProvider.getWords(term.getDocRef());
            } else {
                words = loadDictionary(wordListProvider, term.getValue());
            }
            if (words.length == 0) {
                return Optional.of(matchNone());
            }

            final Set<Double> in = Arrays.stream(words)
                    .filter(NullSafe::isNonBlankString)
                    .map(word -> getTermNumber(term, word))
                    .collect(Collectors.toSet());
            return Optional.of(new NumericInDictionary<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final Double val = extractionFunction.apply(values);
            return in.contains(val);
        }
    }


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class DateGreaterThanOrEqualTo<T> extends DateExpressionTermPredicate<T> {

        private DateGreaterThanOrEqualTo(final ExpressionTerm term,
                                         final Function<T, Long> extractionFunction,
                                         final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class DateLessThan<T> extends DateExpressionTermPredicate<T> {

        private DateLessThan(final ExpressionTerm term,
                             final Function<T, Long> extractionFunction,
                             final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class DateLessThanOrEqualTo<T> extends DateExpressionTermPredicate<T> {

        private DateLessThanOrEqualTo(final ExpressionTerm term,
                                      final Function<T, Long> extractionFunction,
                                      final DateTimeSettings dateTimeSettings) {
            super(term, extractionFunction, dateTimeSettings);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, Long> extractionFunction,
                                                                final DateTimeSettings dateTimeSettings) {
            final long[] in = getTermDates(term, term.getValue(), dateTimeSettings);
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(matchNone());
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


    // --------------------------------------------------------------------------------


    private static class DateInDictionary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, Long> extractionFunction;
        private final Set<Long> in;

        private DateInDictionary(final ExpressionTerm term,
                                 final Function<T, Long> extractionFunction,
                                 final Set<Long> in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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
                return Optional.of(matchNone());
            }

            final Set<Long> in = Arrays.stream(words)
                    .filter(NullSafe::isNonBlankString)
                    .map(word -> getTermDate(term, word, dateTimeSettings))
                    .collect(Collectors.toSet());
            return Optional.of(new DateInDictionary<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final Long val = extractionFunction.apply(values);
            return in.contains(val);
        }
    }


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


    private static class StringEquals<T> extends StringExpressionTermPredicate<T> {

        private StringEquals(final ExpressionTerm term,
                             final String value,
                             final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard equals.
                if (containsWildcard(term.getValue())) {
                    final String wildcardPattern = replaceWildcards(term.getValue());
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(wildcardPattern, Pattern.CASE_INSENSITIVE));
                }

                return new StringEquals<T>(term, unescape(term.getValue()), extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            return value.equalsIgnoreCase(extractionFunction.apply(values));
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringEqualsCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringEqualsCaseSensitive(final ExpressionTerm term,
                                          final String value,
                                          final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard equals.
                if (containsWildcard(term.getValue())) {
                    final String wildcardPattern = replaceWildcards(term.getValue());
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(wildcardPattern));
                }

                return new StringEqualsCaseSensitive<T>(term, unescape(term.getValue()), extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            return value.equals(extractionFunction.apply(values));
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringContains<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String value;

        private StringContains(final ExpressionTerm term,
                               final String value,
                               final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.value = value;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard contains.
                if (containsWildcard(term.getValue())) {
                    final String wildcardPattern = replaceWildcards(term.getValue());
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(".*" + wildcardPattern + ".*", Pattern.CASE_INSENSITIVE));
                }

                return new StringContains<>(term, unescape(term.getValue()).toLowerCase(), extractionFunction);
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


    // --------------------------------------------------------------------------------


    private static class StringContainsCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringContainsCaseSensitive(final ExpressionTerm term,
                                            final String value,
                                            final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                // See if this is a wildcard contains.
                if (containsWildcard(term.getValue())) {
                    final String wildcardPattern = replaceWildcards(term.getValue());
                    return new StringRegex<>(term, extractionFunction,
                            Pattern.compile(".*" + wildcardPattern + ".*"));
                }

                return new StringContainsCaseSensitive<>(term, unescape(term.getValue()), extractionFunction);
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


    // --------------------------------------------------------------------------------


    private static class StringGreaterThan<T> extends StringExpressionTermPredicate<T> {

        private StringGreaterThan(final ExpressionTerm term,
                                  final String value,
                                  final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringGreaterThan<T>(term, term.getValue(), extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            return value.compareTo(extractionFunction.apply(values)) < 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringGreaterThanOrEqual<T> extends StringExpressionTermPredicate<T> {

        private StringGreaterThanOrEqual(final ExpressionTerm term,
                                         final String value,
                                         final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringGreaterThanOrEqual<>(term, term.getValue(), extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            return value.compareTo(extractionFunction.apply(values)) <= 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringLessThan<T> extends StringExpressionTermPredicate<T> {

        private StringLessThan(final ExpressionTerm term,
                               final String value,
                               final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> new StringLessThan<T>(term, term.getValue(), extractionFunction));
        }

        @Override
        public boolean test(final T values) {
            return value.compareTo(extractionFunction.apply(values)) > 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringLessThanOrEqual<T> extends StringExpressionTermPredicate<T> {

        private StringLessThanOrEqual(final ExpressionTerm term,
                                      final String value,
                                      final Function<T, String> extractionFunction) {
            super(term, value, extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            return ifValue(term, () -> {
                return new StringLessThanOrEqual<T>(term, term.getValue(), extractionFunction);
            });
        }

        @Override
        public boolean test(final T values) {
            return value.compareTo(extractionFunction.apply(values)) >= 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringStartsWith<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringStartsWith(final ExpressionTerm term,
                                 final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class StringStartsWithCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringStartsWithCaseSensitive(final ExpressionTerm term,
                                              final Function<T, String> extractionFunction) {
            super(term, term.getValue(), extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class StringEndsWith<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String string;

        private StringEndsWith(final ExpressionTerm term,
                               final Function<T, String> extractionFunction) {
            super(term);
            this.extractionFunction = extractionFunction;
            string = term.getValue().toLowerCase();
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class StringEndsWithCaseSensitive<T> extends StringExpressionTermPredicate<T> {

        private StringEndsWithCaseSensitive(final ExpressionTerm term,
                                            final Function<T, String> extractionFunction) {
            super(term, term.getValue(), extractionFunction);
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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

        @Override
        public Score score(final T t) {
            final String val = extractionFunction.apply(t);
            if (val != null) {
                final Matcher matcher = pattern.matcher(val);
                return regexScore(matcher);
            }
            return Score.NONE;
        }
    }


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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

        @Override
        public Score score(final T t) {
            final String val = extractionFunction.apply(t);
            if (val != null) {
                final Matcher matcher = pattern.matcher(val);
                return regexScore(matcher);
            }
            return Score.NONE;
        }
    }


    // --------------------------------------------------------------------------------


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

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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


    // --------------------------------------------------------------------------------


    private static class StringIn<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final Set<String> in;

        private StringIn(final ExpressionTerm term,
                         final Function<T, String> extractionFunction,
                         final String[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = NullSafe.stream(in)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
                                                                final Function<T, String> extractionFunction) {
            final String[] in = term.getValue().split(" ");
            // If there are no terms then always a false match.
            if (in.length == 0) {
                return Optional.of(matchNone());
            }
            return Optional.of(new StringIn<>(term, extractionFunction, in));
        }

        @Override
        public boolean test(final T values) {
            final String value = extractionFunction.apply(values);
            return in.contains(value);
        }
    }


    // --------------------------------------------------------------------------------


    private static class StringInDictionary<T> extends ExpressionTermPredicate<T> {

        private final Function<T, String> extractionFunction;
        private final String[] in;

        // Not used as far as I can see
        private StringInDictionary(final ExpressionTerm term,
                                   final Function<T, String> extractionFunction,
                                   final String[] in) {
            super(term);
            this.extractionFunction = extractionFunction;
            this.in = in;
        }

        private static <T> Optional<ScoringPredicate<T>> create(final ExpressionTerm term,
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
                return Optional.of(matchNone());
            }
            // Not sure why we are creating a StringIn rather than a StringInDictionary.
            // If we are being consistent then we should probably throw an ex if the line contains
            // a space as that is the delimiter used when handling dicts in lucene searches.
            // Here we are testing against one value, so AND between parts of a line makes no sense.
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


    // --------------------------------------------------------------------------------


    private record Score(boolean matches, int length, int index) {

        private static final Score NONE = new Score(false, 0, 0);
        private static final Score MATCH = new Score(true, 0, 0);
    }


    // --------------------------------------------------------------------------------


    private interface ScoringPredicate<T> extends Predicate<T> {

        @SuppressWarnings("rawtypes")
        static final ScoringPredicate MATCH_NONE = new ScoringPredicate<>() {
            @Override
            public Score score(final Object t) {
                return Score.NONE;
            }

            @Override
            public boolean test(final Object t) {
                return false;
            }
        };
        @SuppressWarnings("rawtypes")
        static final ScoringPredicate MATCH_ALL = new ScoringPredicate<>() {
            @Override
            public Score score(final Object t) {
                return Score.MATCH;
            }

            @Override
            public boolean test(final Object t) {
                return true;
            }
        };

        Score score(T t);

        static <T> ScoringPredicate<T> matchAll() {
            return ScoringPredicate.MATCH_ALL;
        }

        static <T> ScoringPredicate<T> matchNone() {
            return ScoringPredicate.MATCH_NONE;
        }
    }


    // --------------------------------------------------------------------------------


    private record ScoredObject<T>(T t, Score score) {

    }
}
