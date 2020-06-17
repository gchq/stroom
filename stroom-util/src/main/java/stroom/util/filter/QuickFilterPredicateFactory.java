package stroom.util.filter;

import stroom.util.string.StringPredicateFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class QuickFilterPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickFilterPredicateFactory.class);

    /**
     * Creates a match predicate based on userInput. userInput may be a single match string
     * e.g. 'event', or a set of optionally qualified match strings, e.g. 'event type:pipe'.
     * If the
     * @param userInput
     * @param fieldMappers
     * @param <T>
     * @return
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
            final List<MatchToken> matchTokens = parseFullInput(userInput.trim());
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
            final List<FilterFieldMapper<T>> defaultFieldMapper = getDefaultFieldMappers(fieldMappers);
            LOGGER.trace("defaultFieldMapper {}", defaultFieldMapper);

            predicate = createDefaultPredicate(userInput, fieldMappers);
        }

        return predicate;
    }

    private static <T> Predicate<T> createDefaultPredicate(final String input,
                                                           final FilterFieldMappers<T> fieldMappers) {
        // Matching on the default field
        final List<FilterFieldMapper<T>> defaultFieldMapper = getDefaultFieldMappers(fieldMappers);
        LOGGER.trace("defaultFieldMapper {}", defaultFieldMapper);

        return createOrPredicate(input, defaultFieldMapper);
    }

    private static <T> Predicate<T> createPredicate(final String input,
                                                    final FilterFieldMapper<T> fieldMapper) {
        LOGGER.trace("Creating fuzzy match predicate for input [{}], fieldMapper {}", input, fieldMapper);
        return StringPredicateFactory.createFuzzyMatchPredicate(
                input,
                fieldMapper.getNullSafeStringValueExtractor());
    }

    private static <T> Predicate<T> createOrPredicate(final String input,
                                                      final List<FilterFieldMapper<T>> fieldMappers) {
        if (fieldMappers == null || fieldMappers.isEmpty()) {
            return obj -> false;
        } else {
            return fieldMappers.stream()
                    .map(fieldMapper -> createPredicate(input, fieldMapper))
                    .reduce(QuickFilterPredicateFactory::orPredicates)
                    .orElse(obj -> false);
        }
    }

    private static <T> List<FilterFieldMapper<T>> getDefaultFieldMappers(
            final FilterFieldMappers<T> fieldMappers) {

        final List<FilterFieldMapper<T>> defaultFieldMappers = fieldMappers.getFieldMappers().stream()
                .filter(fieldMapper2 -> fieldMapper2.getFieldDefinition().isDefaultField())
                .collect(Collectors.toList());

        if (defaultFieldMappers.isEmpty()) {
            throw new RuntimeException("No default field mappers(s) has/have been defined, fieldMappers" + fieldMappers);
        }
        return defaultFieldMappers;
    }


    private static List<MatchToken> parseFullInput(final String userInput) {
        final List<String> tokens = extractTokens(userInput);

        return tokens.stream()
                .map(token -> {
                    try {
                        if (token.contains(":")) {
                            final String[] parts = token.split(":");
                            return new MatchToken(parts[0], parts[1]);
                        } else {
                            return new MatchToken(null, token);
                        }
                    } catch (Exception e) {
                        // Probably due to the user not having finished typing yet
                        LOGGER.trace("Unable to split [{}]", token);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<String> extractTokens(final String userInput) {
        final List<String> tokens = new ArrayList<>();

        int start = 0;
        boolean insideQuotes = false;
        boolean wasInsideQuotes = false;
        for (int current = 0; current < userInput.length(); current++) {
            if (userInput.charAt(current) == '\"') {
                if (!insideQuotes) {
                    // start of quotes
                    wasInsideQuotes = true;
                }
                insideQuotes = !insideQuotes; // toggle state
            }

            boolean atLastChar = (current == userInput.length() - 1);

            if (atLastChar) {
                if (wasInsideQuotes) {
                    // Strip the quotes off
                    tokens.add(userInput.substring(start +1, userInput.length() -1));
                } else {
                    tokens.add(userInput.substring(start));
                }
            } else if (userInput.charAt(current) == ' ' && !insideQuotes) {
                if (wasInsideQuotes) {
                    // Strip the quotes off
                    tokens.add(userInput.substring(start + 1, current - 1));
                    // clear the state
                    wasInsideQuotes = false;
                } else {
                    tokens.add(userInput.substring(start, current));
                }
                start = current + 1;
            }
        }
        LOGGER.trace("tokens {}", tokens);
        return tokens;
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
                LOGGER.trace("Input is blank");
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


    private static class MatchToken {
        private final String qualifier;
        private final String matchInput;

        private MatchToken(final String qualifier, final String input) {
            this.qualifier = qualifier;
            this.matchInput = Objects.requireNonNull(input);
        }

        private boolean isQualified() {
            return qualifier != null;
        }

        @Override
        public String toString() {
            return "[" + qualifier + ":" + matchInput + "]";
        }
    }
}
