package stroom.util.filter;

import stroom.util.filter.StringPredicateFactory.MatchInfo;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides mechanisms for filtering data based on a quick filter terms, e.g.
 * 'foo bar type:feed'.
 * Multiple terms can be used delimited with spaces. Terms can be qualified with the name of the
 * field. If not qualified then the default field(s) are used. Terms can be prefixed/suffixed with special
 * characters that will change the match mode, e.g. regex, negation.
 * <p>
 * To see how it works in action run QuickFilterTestBed#main()
 */
public class QuickFilterPredicateFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QuickFilterPredicateFactory.class);

    private static final char QUALIFIER_DELIMITER_CHAR = ':';
    private static final char SPLIT_CHAR = ' ';
    private static final char QUOTE_CHAR = '\"';
    private static final char ESCAPE_CHAR = '\\';

    private static final String QUALIFIER_DELIMITER_STR = Character.toString(QUALIFIER_DELIMITER_CHAR);
    private static final String QUOTE_STR = Character.toString(QUOTE_CHAR);
    private static final String ESCAPED_QUOTE_STR = Character.toString(ESCAPE_CHAR) + QUOTE_CHAR;

    private static final Pattern QUALIFIER_DELIMITER_PATTERN = Pattern.compile(QUALIFIER_DELIMITER_STR);

    /**
     * {@link QuickFilterPredicateFactory#filterStream} should be preferred over this method
     * as it incorporates sorting of the results by match quality. This method can be used in
     * situations where sorting of the results is not applicable (e.g. in the explorer tree)
     * and just the predicate is required.
     * <p>
     * Creates a match predicate based on userInput. userInput may be a single match string
     * e.g. 'event', or a set of optionally qualified match strings, e.g. 'event type:pipe'.
     * See the test for valid examples.
     * <p>
     * Where multiple fields are used in the filter they are combined using AND. Where the
     * field mappers define multiple fields as the default then these default fields will
     * be combined with an OR.
     * Space is used as a delimiter for query terms, so 'foo bar type:xslt' is the same as
     * 'name contains foo AND name contains bar AND type contains xslt'.
     * If you want space to be treated as a space then surround with quotes, e.g.
     * '"foo bar"' means 'name contains "foo bar"'.
     * <p>
     * This class delegates the matching of each field to {@link StringPredicateFactory}.
     */
    public static <T> Predicate<T> createFuzzyMatchPredicate(final String userInput,
                                                             final FilterFieldMappers<T> fieldMappers) {
        LOGGER.trace("userInput [{}], mappers {}", userInput, fieldMappers);

        // user input like 'vent type:pipe' or just 'vent'

        // If the default field mapper is two fields, field1 & field2 then "bad stuff" is really
        // (field1:bad OR field2:bad) AND (field1:stuff OR field2:stuff)

        final Predicate<T> predicate;
        if (userInput == null || userInput.isBlank()) {
            LOGGER.trace("Null/empty input");
            // blank input so include everything
            predicate = obj -> true;
        } else {
            // We have some qualified fields so parse them
            final List<MatchToken> matchTokens = extractMatchTokens(userInput, fieldMappers);
            LOGGER.trace("Parsed matchTokens {}", matchTokens);

            if (!matchTokens.isEmpty()) {
                predicate = buildCompoundPredicate(matchTokens, fieldMappers);
            } else {
                // Couldn't parse (user may be part way through typing) so nothing should match
                predicate = obj -> false;
            }
        }

        return predicate;
    }

    public static String fullyQualifyInput(final String userInput,
                                           final FilterFieldMappers<?> fieldMappers) {
        if (userInput == null) {
            LOGGER.trace("Null input");
            return null;
        } else if (userInput.isBlank()) {
            LOGGER.trace("Blank input");
            return "";
        } else {
            final List<MatchToken> matchTokens = extractMatchTokens(userInput, fieldMappers);
            return matchTokens.stream()
                    .map(matchToken -> {
                        if (matchToken.isQualified()) {
                            return matchToken.qualifier + ":" + matchToken.matchInput;
                        } else {
                            final String expandedTerms = fieldMappers.getDefaultFieldMappers()
                                    .stream()
                                    .map(filterFieldMapper ->
                                            filterFieldMapper.getFieldDefinition().getFilterQualifier()
                                                    + ":" + matchToken.matchInput)
                                    .collect(Collectors.joining(" OR "));
                            if (fieldMappers.getDefaultFieldMappers().size() > 1) {

                                return "(" + expandedTerms + ")";
                            } else {
                                return expandedTerms;
                            }
                        }
                    })
                    .collect(Collectors.joining(" AND "));
        }
    }

    /**
     * When you have a stream of strings that you want to filter
     */
    public static Stream<String> filterStream(final String userInput,
                                              final Stream<String> stream) {
        // This method is preferable to using StringPredicateFactory directly as it means we
        // can cope with multiple ANDed terms, e.g. 'foo bar' which means
        // 'value contains foo AND value contains bar'

        return filterStream(userInput, FilterFieldMappers.singleStringField(), stream, null);
    }

    /**
     * When you have a stream of strings that you want to filter
     */
    public static Stream<String> filterStream(final String userInput,
                                              final Stream<String> stream,
                                              final Comparator<String> comparator) {
        // This method is preferable to using StringPredicateFactory directly as it means we
        // can cope with multiple ANDed terms, e.g. 'foo bar' which means
        // 'value contains foo AND value contains bar'

        return filterStream(userInput, FilterFieldMappers.singleStringField(), stream, comparator);
    }

    public static <T> Stream<T> filterStream(final String userInput,
                                             final FilterFieldMappers<T> fieldMappers,
                                             final Stream<T> stream) {
        return filterStream(userInput, fieldMappers, stream, null);
    }

    /**
     * Filters a {@link Stream} of T using
     *
     * @param userInput
     * @param fieldMappers
     * @param stream
     * @param comparator   A caller supplied comparator to sort the stream. If null then
     *                     the results may be first sorted according to match quality then
     *                     by the default fields.
     * @param <T>
     * @return
     */
    public static <T> Stream<T> filterStream(final String userInput,
                                             final FilterFieldMappers<T> fieldMappers,
                                             final Stream<T> stream,
                                             final Comparator<T> comparator) {

        final Stream<T> outputStream;
        if (userInput == null || userInput.isBlank()) {
            // no terms so nothing to filter out or filtered results to sort
            LOGGER.trace("Null/empty input");
            if (comparator != null) {
                outputStream = stream.sorted(comparator);
            } else {
                outputStream = stream;
            }
        } else {
            final List<MatchToken> matchTokens = extractMatchTokens(userInput, fieldMappers);
            LOGGER.trace("Parsed matchTokens {}", matchTokens);

            final Map<MatchToken, Function<T, MatchInfo>> matchInfoEvaluators;
            if (comparator == null) {
                // For each match token we need a func to give us the match info for an filtered item
                // The func will need to check the match quality of one or more fields of the item
                // and return the aggregate
                matchInfoEvaluators = buildMatchInfoEvaluators(fieldMappers, matchTokens);
            } else {
                // Caller supplied comparator so not ranking so no need to populate
                matchInfoEvaluators = Collections.emptyMap();
            }

            if (!matchTokens.isEmpty()) {
                final Predicate<T> predicate = buildCompoundPredicate(matchTokens, fieldMappers);

                final Comparator<FilterMatch<T>> effectiveComparator;
                if (comparator == null) {
                    // First sort by match quality
                    Comparator<FilterMatch<T>> matchQualityComparator = Comparator.nullsLast(FilterMatch::comparator);

                    // Then sort by the default field(s), which ensures if the mode used doesn't rank results
                    // we still get a sensible result order
                    for (final FilterFieldMapper<T> defaultFieldMapper : fieldMappers.getDefaultFieldMappers()) {
                        matchQualityComparator = matchQualityComparator.thenComparing(filterMatch ->
                                        defaultFieldMapper.extractFieldValue(filterMatch.filteredItem),
                                Comparator.nullsLast(String::compareToIgnoreCase));
                    }

                    effectiveComparator = Comparator.nullsLast(matchQualityComparator);
                } else {
                    // Use the caller's supplied comparator
                    effectiveComparator = Comparator.comparing(FilterMatch::getFilteredItem, comparator);
                }

                final Consumer<FilterMatch<T>> peekFunc = LOGGER.isTraceEnabled()
                        ? filterMatch -> LOGGER.trace(filterMatch.toString())
                        : filterMatch -> {
                            // do nowt
                        };

                // Filter the items using the user's filter term(s)
                // Determine the quality of the match for each filtered item
                // Sort each item on the match quality
                // TODO @AT Really we should determine the match info as part of the predicate
                //  as the current approach is not the most efficient
                outputStream = stream
                        .filter(predicate)
                        .map((T filteredItem) -> {
                            final MatchInfo matchInfo;
                            if (comparator == null) {
                                matchInfo = calculateAggregateMatchInfo(
                                        filteredItem,
                                        matchInfoEvaluators);
                                LOGGER.trace(() ->
                                        LogUtil.message("Item {}, match info {}", filteredItem, matchInfo));
                            } else {
                                matchInfo = MatchInfo.noMatchInfo();
                            }
                            return FilterMatch.of(filteredItem, matchInfo);
                        })
                        .sorted(effectiveComparator)
                        .peek(peekFunc)
                        .map(FilterMatch::getFilteredItem);
            } else {
                // Couldn't parse (user may be part way through typing) so nothing should match
                outputStream = stream.limit(0);
            }
        }
        return outputStream;
    }

    private static <T> Map<MatchToken, Function<T, MatchInfo>> buildMatchInfoEvaluators(
            final FilterFieldMappers<T> fieldMappers,
            final List<MatchToken> matchTokens) {

        final Map<MatchToken, Function<T, MatchInfo>> matchInfoEvaluators;
        matchInfoEvaluators = matchTokens.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        matchToken -> {
                            final Collection<FilterFieldMapper<T>> mappers = matchToken.isQualified()
                                    ? List.of(fieldMappers.get(matchToken.qualifier))
                                    : fieldMappers.getDefaultFieldMappers();

                            final Function<String, MatchInfo> matchInfoEvaluator = StringPredicateFactory
                                    .createMatchInfoEvaluator(matchToken.matchInput);

                            // if mappers is >1 then we are dealing with default fields so need to pick the
                            // best match
                            return (T filteredItem) ->
                                    mappers.stream()
                                            .map(mapper -> {
                                                final String fieldValue = mapper.extractFieldValue(
                                                        filteredItem);
                                                return matchInfoEvaluator.apply(fieldValue);
                                            })
                                            .filter(MatchInfo::hasMatchInfo)
                                            .reduce(MatchInfo::bestMatch)
                                            .orElse(MatchInfo.noMatchInfo());
                        }));
        return matchInfoEvaluators;
    }

//    private static <T> Comparator<T> createMatchComparator(final List<MatchToken> matchTokens,
//                                                           final FilterFieldMappers<T> fieldMappers) {
//        final Comparator<T> comparator;
//        if (matchTokens.isEmpty()) {
//            LOGGER.trace("Null/empty input, no sorting");
//            comparator = (o1, o2) -> 0;
//        } else if (matchTokens.size() == 1) {
//            // Only one field so can compare on the
//            comparator = createComparator(matchTokens.get(0), fieldMappers);
//        } else {
//            // TODO @AT How do we compare a match on multiple fields?
//            //  Could add up all match lengths and match positions and compare on the aggregates.
//            comparator = (o1, o2) -> 0;
//        }
//        return comparator;
//    }

//    public static <T> Comparator<T> createComparator(final MatchToken matchToken,
//                                                     final FilterFieldMappers<T> fieldMappers) {
//
//        final Function<String, MatchInfo> matchInfoEvaluator =
//                StringPredicateFactory.createMatchInfoEvaluator(matchToken.matchInput);
//
//        final Collection<FilterFieldMapper<T>> filterFieldMapperCollection = matchToken.isQualified()
//                ? List.of(fieldMappers.get(matchToken.qualifier))
//                : fieldMappers.getDefaultFieldMappers();
//
//        return (T obj1, T obj2) -> {
//
//            final Optional<MatchInfo> optMatchInfo1 = calculateAggregateMatchInfo(
//                    obj1,
//                    filterFieldMapperCollection,
//                    matchInfoEvaluator);
//            final Optional<MatchInfo> optMatchInfo2 = calculateAggregateMatchInfo(
//                    obj2,
//                    filterFieldMapperCollection,
//                    matchInfoEvaluator);
//
//            if (optMatchInfo1.isEmpty() && optMatchInfo2.isEmpty()) {
//                return 0;
//            } else if (optMatchInfo1.isEmpty()) {
//                return -1;
//            } else if (optMatchInfo2.isEmpty()) {
//                return 1;
//            } else {
//                return optMatchInfo1.get()
//                        .compareTo(optMatchInfo2.get());
//            }
//        };
//    }

    private static <T> MatchInfo calculateAggregateMatchInfo(
            final T filteredItem,
            final Map<MatchToken, Function<T, MatchInfo>> matchInfoEvaluators) {

        return matchInfoEvaluators.keySet().stream()
                .map(matchToken ->
                        matchInfoEvaluators.get(matchToken).apply(filteredItem))
                .filter(MatchInfo::hasMatchInfo)
                .reduce(MatchInfo::addScores)
                .orElse(MatchInfo.noMatchInfo());
    }

//    private static <T> Optional<MatchInfo> calculateAggregateMatchInfo(
//            final T object,
//            final Collection<FilterFieldMapper<T>> mappers,
//            final Function<String, MatchInfo> matchInfoEvaluator) {
//
//        return mappers.stream()
//                .map(mapper -> mapper.extractFieldValue(object))
//                .map(matchInfoEvaluator)
//                .filter(MatchInfo::wasFound)
//                .reduce(MatchInfo::aggregate);
//    }

    private static <T> Predicate<T> createDefaultPredicate(final String input,
                                                           final FilterFieldMappers<T> fieldMappers) {
        // Matching on the default field
        final Collection<FilterFieldMapper<T>> defaultFieldMapper = getDefaultFieldMappers(fieldMappers);
        LOGGER.trace("defaultFieldMapper {}", defaultFieldMapper);

        if (input.startsWith(StringPredicateFactory.NOT_OPERATOR_STR)) {
            LOGGER.debug("Negated match so AND the default fields");
            return createAndPredicate(input, defaultFieldMapper);
        } else {
            LOGGER.debug("Normal match so OR the default fields");
            return createOrPredicate(input, defaultFieldMapper);
        }
    }

    private static <T> Predicate<T> createPredicate(final String input,
                                                    final FilterFieldMapper<T> fieldMapper) {
        LOGGER.trace("Creating fuzzy match predicate for input [{}], fieldMapper {}", input, fieldMapper);
        return StringPredicateFactory.createFuzzyMatchPredicate(
                input,
                fieldMapper::extractFieldValue);
    }

    private static <T> Predicate<T> createOrPredicate(final String input,
                                                      final Collection<FilterFieldMapper<T>> fieldMappers) {
        if (fieldMappers == null || fieldMappers.isEmpty()) {
            return obj -> false;
        } else {
            return fieldMappers.stream()
                    .map(fieldMapper -> createPredicate(input, fieldMapper))
                    .reduce(QuickFilterPredicateFactory::orPredicates)
                    .orElse(obj -> false);
        }
    }

    private static <T> Predicate<T> createAndPredicate(final String input,
                                                       final Collection<FilterFieldMapper<T>> fieldMappers) {
        if (fieldMappers == null || fieldMappers.isEmpty()) {
            return obj -> false;
        } else {
            return fieldMappers.stream()
                    .map(fieldMapper -> createPredicate(input, fieldMapper))
                    .reduce(QuickFilterPredicateFactory::andPredicates)
                    .orElse(obj -> false);
        }
    }

    private static <T> Collection<FilterFieldMapper<T>> getDefaultFieldMappers(
            final FilterFieldMappers<T> fieldMappers) {

        final Collection<FilterFieldMapper<T>> defaultFieldMappers = fieldMappers.getDefaultFieldMappers();

        if (defaultFieldMappers.isEmpty()) {
            throw new RuntimeException(
                    "No default field mappers(s) has/have been defined, fieldMappers" + fieldMappers);
        }
        return defaultFieldMappers;
    }

    static List<MatchToken> extractMatchTokens(final String userInput, final FilterFieldMappers<?> filterFieldMappers) {
        if (userInput == null || userInput.isBlank()) {
            return Collections.emptyList();
        } else {
            final List<String> parts = splitInput(userInput);

            final List<MatchToken> tokens = parts.stream()
                    .map(part -> {
                        try {
                            if (part.contains(QUALIFIER_DELIMITER_STR)) {
                                final String[] subParts = QUALIFIER_DELIMITER_PATTERN.split(part);
                                if (part.endsWith(QUALIFIER_DELIMITER_STR)) {
                                    return new MatchToken(subParts[0], "");
                                } else if (part.startsWith(QUALIFIER_DELIMITER_STR)) {
                                    throw new RuntimeException("Invalid token " + part);
                                } else {
                                    return new MatchToken(subParts[0], subParts[1]);
                                }
                            } else {
                                return new MatchToken(null, part);
                            }
                        } catch (Exception e) {
                            // Probably due to the user not having finished typing yet
                            LOGGER.trace("Unable to split [{}], due to {}", part, e.getMessage(), e);
                            return null;
                        }
                    })
                    .collect(Collectors.toList());

            final boolean badInput = tokens.stream()
                    .anyMatch(token -> {
                        if (token == null) {
                            LOGGER.trace("Null token");
                            return true;
                        } else if (token.isQualified() && !filterFieldMappers.hasField(token.qualifier)) {
                            LOGGER.trace(() -> "Unknown qualifier '" + token.qualifier
                                    + "'. Valid qualifiers: " + filterFieldMappers.getFieldQualifiers());
                            return true;
                        } else {
                            return false;
                        }
                    });

            if (badInput) {
                // Found some bad input so return no tokens
                return Collections.emptyList();
            } else {
                return tokens.stream()
                        .filter(token -> !token.isTermBlank()) // no point doing anything with 'name:'
                        .collect(Collectors.toList());
            }
        }
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

                boolean atLastChar = (current == cleanedInput.length() - 1);

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
                LOGGER.trace("Odd number of quotes ({}) in input [{}], can't parse",
                        unEscapedQuoteCount, userInput);
                tokens.clear();
            }
        } catch (Exception e) {
            LOGGER.trace("Unable to parse [{}] due to: {}", userInput, e.getMessage(), e);
            // Don't want to throw as it may be unfinished user input.
        }
        LOGGER.trace("tokens {}", tokens);
        return tokens;
    }

    private static String deEscape(final String input) {
        return input.replace(ESCAPED_QUOTE_STR, QUOTE_STR);
    }

    private static <T> Predicate<T> buildCompoundPredicate(final List<MatchToken> matchTokens,
                                                           final FilterFieldMappers<T> fieldMappers) {

        Predicate<T> compoundPredicate = null;
        boolean badMatchStringFound = false;

        for (final MatchToken matchToken : matchTokens) {

            if (!matchToken.isTermBlank()) {
                if (!matchToken.isQualified()) {
                    final Predicate<T> unqualifiedPredicate = createDefaultPredicate(
                            matchToken.matchInput,
                            fieldMappers);
                    compoundPredicate = andPredicates(compoundPredicate, unqualifiedPredicate);
                } else {
                    // A qualified token, so get its mapper
                    final FilterFieldMapper<T> fieldMapper = fieldMappers.get(matchToken.qualifier);

                    LOGGER.trace("qualifiedMatchString: {}, fieldMapper: {}", matchToken, fieldMapper);

                    if (fieldMapper != null) {
                        final Predicate<T> fieldPredicate = createPredicate(matchToken.matchInput, fieldMapper);
                        compoundPredicate = andPredicates(compoundPredicate, fieldPredicate);
                    } else {
                        LOGGER.trace("No mapper found for field {}", matchToken);
                        // Bad input so no point parsing the rest
                        badMatchStringFound = true;
                        break;
                    }
                }
            } else {
                LOGGER.trace("Input is blank, treating as always true");
                if (compoundPredicate == null) {
                    compoundPredicate = andPredicates(compoundPredicate, obj -> true);
                }
            }
        }
        if (badMatchStringFound || compoundPredicate == null) {
            compoundPredicate = obj -> false;
        }
        return compoundPredicate;
    }

    /**
     * Chains predicates together using an AND. predicate2 will be tested after predicate1.
     *
     * @return A single predicate representing predicate1 AND predicate2.
     */
    private static <T> Predicate<T> andPredicates(final Predicate<T> predicate1,
                                                  final Predicate<T> predicate2) {
        if (predicate1 == null && predicate2 == null) {
            return null;
        } else {
            if (predicate1 == null) {
                return predicate2;
            } else {
                return predicate1.and(predicate2);
            }
        }
    }

    /**
     * Chains predicates together using an OR. predicate2 will be tested after predicate1.
     *
     * @return A single predicate representing predicate1 OR predicate2.
     */
    private static <T> Predicate<T> orPredicates(final Predicate<T> predicate1,
                                                 final Predicate<T> predicate2) {
        if (predicate1 == null && predicate2 == null) {
            return null;
        } else {
            if (predicate1 == null) {
                return predicate2;
            } else {
                return predicate1.or(predicate2);
            }
        }
    }

    // pkg private for testing
    static class MatchToken {

        private final String qualifier;
        private final String matchInput;

        private MatchToken(final String qualifier, final String input) {
            this.qualifier = qualifier;
            this.matchInput = Objects.requireNonNull(input);
        }

        public static MatchToken of(final String matchInput) {
            return new MatchToken(null, matchInput);
        }

        public static MatchToken of(final String qualifier, final String matchInput) {
            return new MatchToken(qualifier, matchInput);
        }

        private boolean isQualified() {
            return qualifier != null;
        }

        private boolean isTermBlank() {
            return matchInput.isBlank();
        }

        @Override
        public String toString() {
            return "[" + (qualifier != null
                    ? qualifier
                    : "") + "]:[" + matchInput + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MatchToken that = (MatchToken) o;
            return Objects.equals(qualifier, that.qualifier) &&
                    matchInput.equals(that.matchInput);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qualifier, matchInput);
        }
    }

    private static class FilterMatch<T> {

        final T filteredItem;
        final MatchInfo matchInfo;

        private FilterMatch(final T filteredItem, final MatchInfo matchInfo) {
            this.filteredItem = Objects.requireNonNull(filteredItem);
            this.matchInfo = Objects.requireNonNull(matchInfo);
        }

        private static <T> FilterMatch<T> of(final T filteredItem, final MatchInfo matchInfo) {
            return new FilterMatch<>(filteredItem, matchInfo);
        }

        public T getFilteredItem() {
            return filteredItem;
        }

        public MatchInfo getMatchInfo() {
            return matchInfo;
        }

        private boolean hasMatchInfo() {
            return matchInfo != null && matchInfo.hasMatchInfo();
        }

        public static <T> int comparator(final FilterMatch<T> filterMatch1,
                                         final FilterMatch<T> filterMatch2) {
            if (!filterMatch1.hasMatchInfo() && !filterMatch2.hasMatchInfo()) {
                return 0;
            } else if (!filterMatch1.hasMatchInfo()) {
                return -1;
            } else if (!filterMatch2.hasMatchInfo()) {
                return 1;
            } else {
                return filterMatch1.matchInfo.compareTo(filterMatch2.matchInfo);
            }
        }

        @Override
        public String toString() {
            return "FilterMatch{" +
                    "filteredItem=" + filteredItem +
                    ", matchInfo=" + matchInfo +
                    '}';
        }
    }
}
