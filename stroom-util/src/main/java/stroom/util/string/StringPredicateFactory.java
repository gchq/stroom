package stroom.util.string;

import stroom.util.ConsoleColour;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.validation.constraints.NotNull;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Useful methods to create various {@link Predicate<String>}
 */
public class StringPredicateFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StringPredicateFactory.class);

    // Treat brackets as word separators, e.g. "Events (XML)"
    private static final Pattern DEFAULT_SEPARATOR_CHAR_CLASS = Pattern.compile("[ _\\-()\\[\\]]");

    private static final Pattern CASE_INSENS_WORD_LETTER_CHAR_CLASS = Pattern.compile("[a-z0-9]");

    // Matches a whole string that is lowerCamelCase or UpperCamelCase
    // It is debatable if we should instead look for the absence of a separator as that may
    // be easier
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile(
            "^([A-Z]+)?[a-z0-9]+(?:(?:\\d)|(?:[A-Z0-9]+[a-z0-9]+))*(?:[A-Z]+)?$");

    // Matches positions in (C|c)amelCase to split into individual words
    // Doesn't cope with abbreviations at the beginning/middle of the string,
    // e.g. SQLScript or SomeSQLScript
    // Pattern also splits on a space to allow us to pre-split the string a bit
    private static final Pattern CAMEL_CASE_SPLIT_PATTERN = Pattern.compile(
            "((?<=[a-z])(?=[A-Z])|(?<=[0-9])(?=[A-Z])|(?<=[a-zA-Z])(?=[0-9])| )");
    private static final Pattern CAMEL_CASE_ABBREVIATIONS_PATTERN = Pattern.compile("([A-Z]+)([A-Z][a-z0-9])");

    // Static util methods only
    private StringPredicateFactory() {
    }

    /**
     * @see StringPredicateFactory#createFuzzyMatchPredicate(String, Pattern)
     */
    public static Predicate<String> createFuzzyMatchPredicate(final String userInput) {
        return createFuzzyMatchPredicate(userInput, DEFAULT_SEPARATOR_CHAR_CLASS);
    }

    /**
     * Creates a fuzzy match {@link Predicate<String>} for userInput.
     * Null userInput results in an always true predicate.
     * Broadly it has five match modes:
     * Regex match: "/(wo|^)man" matches "a woman", "manly"
     * Word boundary match: "?OTheiM" matches "on the mat" in "the cat sat on their mat", but not "the cat sat on there mat"
     * Starts with: "^prefix" matches "PrefixToSomeText" (case insensitive)
     * Ends with "suffix$" matches "TextWithSuffix" (case insensitive)
     * Exact match: "^sometext$" matches "sometext" (case insensitive)
     * Chars anywhere (in order): "aid" matches "A big dog" (case insensitive)
     * See TestStringPredicateFactory for more examples of how the
     * matching works.
     *
     * @param separatorCharacterClass A regex character class, e.g. [ \-_] that defines the separators
     *                                between words in the string(s) under test.
     */
    public static Predicate<String> createFuzzyMatchPredicate(final String userInput,
                                                              final Pattern separatorCharacterClass) {
        LOGGER.trace("Creating predicate for userInput [{}] and separators {}", userInput, separatorCharacterClass);
        Predicate<String> predicate;
        if (userInput == null || userInput.isEmpty()) {
            LOGGER.trace("Creating null input predicate");
            // No input so get everything
            predicate = stringUnderTest -> true;
        } else if (userInput.startsWith("/")) {
            // remove the / marker char from the beginning
            predicate = createRegexPredicate(userInput.substring(1));
        } else if (userInput.startsWith("?")) {
            // remove the ? marker char from the beginning
            predicate = createWordBoundaryPredicate(userInput.substring(1), separatorCharacterClass);
        } else if (userInput.startsWith("^") && userInput.endsWith("$")) {
            predicate = createCaseInsensitiveExactMatchPredicate(userInput);
        } else if (userInput.endsWith("$")) {
            // remove the $ marker char from the end
            predicate = createCaseInsensitiveEndsWithPredicate(userInput.substring(0, userInput.length() - 1));
        } else if (userInput.startsWith("^")) {
            // remove the ^ marker char from the beginning
            predicate = createCaseInsensitiveStartsWithPredicate(userInput.substring(1));
        } else {
            predicate = createCharsAnywherePredicate(userInput);
        }

        if (LOGGER.isTraceEnabled()) {
            return toLoggingPredicate(predicate);
        } else {
            return predicate;
        }
    }

    /**
     * Wraps the passed {@link Predicate} with one that returns result
     * if the value under test is null
     */
    public static <T> Predicate<T> toNullSafePredicate(final boolean resultIfNull,
                                                       final Predicate<T> predicate) {
        return obj -> {
            if (obj == null) {
                return resultIfNull;
            } else {
                return predicate.test(obj);
            }
        };
    }

    public static Predicate<String> toLoggingPredicate(final Predicate<String> predicate) {
        return str -> {
            boolean result = predicate.test(str);
            final ConsoleColour colour = result ? ConsoleColour.GREEN : ConsoleColour.RED;

            String msg = ConsoleColour.colourise(LogUtil.message("String under test [{}], result: {}",
                    str, result), colour);
            LOGGER.trace(msg);
            return result;
        };
    }

    @NotNull
    public static Predicate<String> createCaseInsensitiveStartsWithPredicate(final String userInput) {
        LOGGER.trace("Creating case insensitive starts with predicate");
        // remove the ^ marker char
        final String lowerCaseInput = userInput.toLowerCase();
        return toNullSafePredicate(false, stringUnderTest ->
                stringUnderTest.toLowerCase().startsWith(lowerCaseInput));
    }

    @NotNull
    public static Predicate<String> createCaseInsensitiveEndsWithPredicate(final String userInput) {
        LOGGER.trace("Creating case insensitive ends with predicate");
        final String lowerCaseInput = userInput.toLowerCase();
        return toNullSafePredicate(false, stringUnderTest ->
                stringUnderTest.toLowerCase().endsWith(lowerCaseInput));
    }

    public static Predicate<String> createCaseInsensitiveContainsPredicate(final String userInput) {
        if (userInput == null) {
            return stringUnderTest -> true;
        } else {
            final String lowerCaseInput = userInput.toLowerCase();
            return toNullSafePredicate(false, stringUnderTest ->
                    stringUnderTest.toLowerCase().contains(lowerCaseInput));
        }
    }

    public static Predicate<String> createRegexPredicate(final String userInput) {
        LOGGER.trace("Creating regex predicate for {}", userInput);
        Pattern pattern;
        try {
            pattern = Pattern.compile(userInput, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            LOGGER.trace(() ->
                    LogUtil.message("Invalid pattern {}, due to {}", userInput, e.getMessage()));
            // Bad pattern, can't really raise an exception as the user may have just mis-typed
            // so just return a false predicate
            return str -> false;
        }

        return pattern.asPredicate();
    }

    @NotNull
    private static Predicate<String> createWordBoundaryPredicate(
            final String userInput,
            final Pattern separatorCharacterClass) {

        LOGGER.trace("creating word boundary predicate");
        // Has some uppercase so use word boundaries
        // An upper case letter means the start of a word
        // a lower case letter means the continuation of a word

        // We can use the separator based predicate for both camel case and separated
        // strings as long as we modify the camel case ones first.
        final Predicate<String> separatorPredicate = createSeparatedWordBoundaryPredicate(
                userInput, separatorCharacterClass);

        return toNullSafePredicate(false, stringUnderTest -> {
            if (CAMEL_CASE_PATTERN.matcher(stringUnderTest).matches()) {
                LOGGER.trace("stringUnderTest [{}] is (C|c)amelCase", stringUnderTest);

                // replace stuff like SQLScript with "SQL Script"
                String separatedStringUnderTest = CAMEL_CASE_ABBREVIATIONS_PATTERN
                        .matcher(stringUnderTest)
                        .replaceAll("$1 $2");

                LOGGER.trace("separatedStringUnderTest: [{}]", separatedStringUnderTest);

                // Now split on camel case word boundaries (or spaces added above)
                separatedStringUnderTest = CAMEL_CASE_SPLIT_PATTERN
                        .matcher(separatedStringUnderTest)
                        .replaceAll(" ");

                LOGGER.trace("separatedStringUnderTest: [{}]", separatedStringUnderTest);

                // Now we have split the words with spaces, use the separator predicate
                return separatorPredicate.test(separatedStringUnderTest);
            } else {
                LOGGER.trace("stringUnderTest [{}] has word separators", stringUnderTest);
                return separatorPredicate.test(stringUnderTest);
            }
        });
    }

    @NotNull
    private static Predicate<String> createSeparatedWordBoundaryPredicate(
            final String userInput,
            final Pattern separatorCharacterClass) {

        // Has some uppercase so use word boundaries
        // An upper case letter means the start of a word
        // a lower case letter means the continuation of a word

        final StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < userInput.length(); i++) {
            char chr = userInput.charAt(i);

            if (Character.isUpperCase(chr)) {
                if (i == 0) {
                    // First letter so is either preceded by ^ or by a separator
                    patternBuilder
                            .append("(?:^|") // non-capturing
                            .append(separatorCharacterClass)
                            .append(")");
                } else {
                    // Not the first letter so need the end of the previous word
                    // and a word separator
                    patternBuilder
                            .append(CASE_INSENS_WORD_LETTER_CHAR_CLASS)
                            .append("*")
                            .append(separatorCharacterClass)
                            .append("+"); // one of more separators
                }
                // Start of a word
            }

            if (Character.isLetterOrDigit(chr)) {
                patternBuilder.append(chr);
            } else {
                // Might be a special char so escape it
                patternBuilder.append(Pattern.quote(String.valueOf(chr)));
            }
        }
        final Pattern pattern = Pattern.compile(
                patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
        LOGGER.trace("Using separated word pattern: {} with separators {}",
                pattern, separatorCharacterClass);

        return pattern.asPredicate();
    }

    @NotNull
    private static Predicate<String> createCamelCaseWordBoundaryPredicate(
            final String userInput) {

        // Has some uppercase so use word boundaries
        // An upper case letter means the start of a word
        // a lower case letter means the continuation of a word

        final StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < userInput.length(); i++) {
            char chr = userInput.charAt(i);

            if (Character.isUpperCase(chr)) {
                if (i == 0) {
                    // First letter so is either preceded by ^ or
                    // by the end of the previous word
                    patternBuilder
                            .append("(?:^|[a-z0-9]") // non-capturing, assume numbers part of prev word
                            .append(")");
                } else {
                    // Not the first letter so need the end of the previous word
                    // and a word separator
                    patternBuilder
                            .append("[a-z0-9]")
                            .append("*");
                }
                // Start of a word
            }

            if (Character.isLetterOrDigit(chr)) {
                patternBuilder.append(chr);
            } else {
                // Might be a special char so escape it
                patternBuilder.append(Pattern.quote(String.valueOf(chr)));
            }
        }
        final Pattern pattern = Pattern.compile(patternBuilder.toString());
        LOGGER.trace("Using (C|c)amelCase separated pattern: {}", pattern);
        return toNullSafePredicate(false, pattern.asPredicate());
    }

    @NotNull
    private static Predicate<String> createCharsAnywherePredicate(final String userInput) {
        LOGGER.trace("creating chars appear anywhere in correct order predicate");
        // All lower case so match on each char appearing somewhere in the text
        // in the correct order
        final String lowerCaseInput = userInput.toLowerCase();
        final StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < lowerCaseInput.length(); i++) {
            patternBuilder.append(".*?");

            char chr = userInput.charAt(i);
            if (Character.isLetterOrDigit(chr)) {
                patternBuilder.append(chr);
            } else {
                // Might be a special char so escape it
                patternBuilder.append(Pattern.quote(String.valueOf(chr)));
            }
        }
        patternBuilder.append(".*?");
        final Pattern pattern = Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
        LOGGER.trace("Using pattern: {}", pattern);
        return toNullSafePredicate(false, pattern.asPredicate());
    }


    @NotNull
    private static Predicate<String> createCaseInsensitiveExactMatchPredicate(final String userInput) {
        LOGGER.trace("creating case insensitive exact match predicate");
        final String lowerCaseInput = userInput.substring(1)
                .substring(0, userInput.length() - 2);
        return toNullSafePredicate(false, stringUnderTest ->
                stringUnderTest.toLowerCase().equalsIgnoreCase(lowerCaseInput));
    }


    private static boolean isAllLowerCase(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isUpperCase(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
