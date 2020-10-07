package stroom.util.filter;

import stroom.util.string.StringPredicateFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QuickFilterPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickFilterPredicateFactory.class);

    private static final char QUALIFIER_DELIMITER_CHAR = ':';
    private static final char SPLIT_CHAR = ' ';
    private static final char QUOTE_CHAR = '\"';
    private static final char ESCAPE_CHAR = '\\';

    private static final String QUALIFIER_DELIMITER_STR = Character.toString(QUALIFIER_DELIMITER_CHAR);
    private static final String QUOTE_STR = Character.toString(QUOTE_CHAR);
    private static final String ESCAPED_QUOTE_STR = Character.toString(ESCAPE_CHAR) + QUOTE_CHAR;

    private static final Pattern QUALIFIER_DELIMITER_PATTERN = Pattern.compile(QUALIFIER_DELIMITER_STR);

    /**
     * Creates a match predicate based on userInput. userInput may be a single match string
     * e.g. 'event', or a set of optionally qualified match strings, e.g. 'event type:pipe'.
     * See the test for valid examples.
     *
     * Where multiple fields are used in the filter they are combined using OR.
     *
     * This class delegates the matching of each field to {@link StringPredicateFactory}.
     */
    public static <T> Predicate<T> createFuzzyMatchPredicate(final String userInput,
                                                             final FilterFieldMappers<T> fieldMappers) {
       LOGGER.trace("userInput [{}], mappers {}", userInput, fieldMappers);

       // user input like 'vent type:pipe' or just 'vent'

        final Predicate<T> predicate;
        if (userInput == null || userInput.isEmpty()) {
            LOGGER.trace("Null/empty input");
            predicate = obj -> true;
        } else if (userInput.contains(":") || userInput.contains("\"")) {
            LOGGER.trace("Found at least one qualified field or quoted values");
            // We have some qualified fields so parse them
            final List<MatchToken> matchTokens = extractMatchTokens(userInput.trim());
            LOGGER.trace("Parsed matchTokens {}", matchTokens);

            if (!matchTokens.isEmpty()) {
                predicate = buildCompoundPredicate(matchTokens, fieldMappers);
            } else {
                // Couldn't parse so nothing should match
                predicate = obj -> false;
            }
        } else {
            LOGGER.trace("Doing default field only matching");
            // Matching on the default field
            final Collection<FilterFieldMapper<T>> defaultFieldMapper = getDefaultFieldMappers(fieldMappers);
            LOGGER.trace("defaultFieldMapper {}", defaultFieldMapper);

            predicate = createDefaultPredicate(userInput, fieldMappers);
        }

        return predicate;
    }


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
                fieldMapper.getNullSafeStringValueExtractor());
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
            throw new RuntimeException("No default field mappers(s) has/have been defined, fieldMappers" + fieldMappers);
        }
        return defaultFieldMappers;
    }

    static List<MatchToken> extractMatchTokens(final String userInput) {
        final List<String> tokens = splitInput(userInput);

        return tokens.stream()
                .map(token -> {
                    try {
                        if (token.contains(QUALIFIER_DELIMITER_STR)) {
                            final String[] parts = QUALIFIER_DELIMITER_PATTERN.split(token);
                            if (token.endsWith(QUALIFIER_DELIMITER_STR)) {
                                return new MatchToken(parts[0], "");
                            } else if (token.startsWith(QUALIFIER_DELIMITER_STR)) {
                                throw new RuntimeException("Invalid token " + token);
                            } else {
                                return new MatchToken(parts[0], parts[1]);
                            }
                        } else {
                            return new MatchToken(null, token);
                        }
                    } catch (Exception e) {
                        // Probably due to the user not having finished typing yet
                        LOGGER.trace("Unable to split [{}], due to {}", token, e.getMessage(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Split the input on spaces with each chunk optionally enclosed with a double quotes.
     * Should ignore leading, trailing repeated spaces.
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

            if (!matchToken.matchInput.isBlank()) {
                if (!matchToken.isQualified()) {
                    final Predicate<T> unqualifiedPredicate = createDefaultPredicate(matchToken.matchInput, fieldMappers);
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


    public static class MatchToken {
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

        @Override
        public String toString() {
            return "[" + (qualifier != null ? qualifier : "") + "]:[" + matchInput + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final MatchToken that = (MatchToken) o;
            return Objects.equals(qualifier, that.qualifier) &&
                    matchInput.equals(that.matchInput);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qualifier, matchInput);
        }
    }
}
