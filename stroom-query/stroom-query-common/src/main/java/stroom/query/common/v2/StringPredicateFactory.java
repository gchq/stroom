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

import stroom.util.ConsoleColour;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Useful methods to create various {@link Predicate<String>}
 */
public class StringPredicateFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StringPredicateFactory.class);

    // Treat brackets as word separators, e.g. "Events (XML)"
    private static final Pattern DEFAULT_SEPARATOR_CHAR_CLASS = Pattern.compile("[ _\\-()\\[\\].]");

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
            "((?<=[a-z])(?=[A-Z])|(?<=[0-9])(?=[A-Z])|(?<=[a-zA-Z])(?=[0-9])| |\\.)");
    private static final Pattern CAMEL_CASE_ABBREVIATIONS_PATTERN = Pattern.compile("([A-Z]+)([A-Z][a-z0-9])");

    // If you change any of these then you also need to change the finding-things.md doc in stroom-docs
    // and the tool tip help in QuickFilterTooltipUtil
    public static final String REGEX_PREFIX = "/";
    public static final String CHARS_ANYWHERE_PREFIX = "~";
    public static final String EXACT_MATCH_PREFIX = "=";
    public static final String STARTS_WITH_PREFIX = "^";
    public static final String ENDS_WITH_PREFIX = "$";
    public static final String WORD_BOUNDARY_PREFIX = "?";
    public static final String WILDCARD_STR = "*";

    public static final List<String> ALL_PREFIXES = List.of(
            REGEX_PREFIX,
            CHARS_ANYWHERE_PREFIX,
            STARTS_WITH_PREFIX,
            WORD_BOUNDARY_PREFIX
    );

    public static final char NOT_OPERATOR_CHAR = '!';
    public static final String NOT_OPERATOR_STR = Character.toString(NOT_OPERATOR_CHAR);

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
     * Word boundary match: "?OTheiM" matches "on the mat" in "the cat sat on their mat", but not
     * "the cat sat on there mat"
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
        // TODO should we trim the input to remove leading/trailing spaces?

        // TODO @AT Need to hold the received input in an obj along with the match mode and input stripped of
        //  all pre/suffixes and available via getStrippedInput() and getLowerCaseStrippedInput().
        //  This will make it clearer as to what input you are dealing with.

        // TODO @AT Rather than constructing a predicate for each match mode we ought to build a mapping func
        //  that will map the input in its original form, i.e. T to an obj containing the original T item,
        //  the result of the match (true/false) and the match quality (match length, match position, matched chars).
        //  The caller would need to provide an extractor func <T, String> so we can get the string to filer on.
        //  Internally we can then map (to our obj), filter (on its true false) and sort (on the match quality).
        //  The caller can then do something like
        //    filterItems(itemStream, extractorFunc).map(FilterResult::getItem).collect(....)
        //  or make use of the filterResult to show the user why a thing matched
        //  All this would save us from having to run a regex on each match result in the compare stage, which is
        //  super inefficient.

        LOGGER.trace("Creating predicate for userInput [{}] and separators {}", userInput, separatorCharacterClass);

        String modifiedInput = userInput;
        boolean isNegated = false;

        Predicate<String> predicate;
        if (modifiedInput == null || modifiedInput.isEmpty()) {
            LOGGER.trace("Creating null input predicate");
            // No input so get everything
            predicate = stringUnderTest -> true;
        } else {
            if (modifiedInput.startsWith(NOT_OPERATOR_STR)) {
                modifiedInput = modifiedInput.substring(1);
                LOGGER.debug("Input after NOT operator removal [{}]", modifiedInput);
                isNegated = true;
            }

            if (modifiedInput.isEmpty()) {
                LOGGER.trace("Creating null input predicate");
                // No input so get everything
                predicate = stringUnderTest -> true;
            } else if (modifiedInput.startsWith(REGEX_PREFIX)) {
                // We must test for this prefix first as you might have '/foobar$' which could be confused
                // with ends with matching
                // remove the / marker char from the beginning
                predicate = createRegexPredicate(modifiedInput.substring(1));
            } else if (modifiedInput.startsWith(CHARS_ANYWHERE_PREFIX)) {
                // remove the ~ marker char from the beginning
                predicate = createCharsAnywherePredicate(modifiedInput.substring(1));
            } else if (modifiedInput.startsWith(WORD_BOUNDARY_PREFIX)) {
                // remove the ? marker char from the beginning
                predicate = createWordBoundaryPredicate(modifiedInput.substring(1), separatorCharacterClass);
                // remove the = marker char from the beginning
            } else if (modifiedInput.startsWith(EXACT_MATCH_PREFIX)) {
                predicate = createCaseInsensitiveExactMatchPredicate(modifiedInput.substring(1));
            } else if (modifiedInput.startsWith(ENDS_WITH_PREFIX)) {
                // remove the $ marker char from the beginning
                predicate = createCaseInsensitiveEndsWithPredicate(modifiedInput.substring(1));
            } else if (modifiedInput.startsWith(STARTS_WITH_PREFIX)) {
                // remove the ^ marker char from the beginning
                predicate = createCaseInsensitiveStartsWithPredicate(modifiedInput.substring(1));
            } else if (modifiedInput.contains(WILDCARD_STR)) {
                // This is intended for expression term values, e.g. specifing a set of feeds in a processor
                // filter expression. The aim is to show the user what values are represented by a wild-carded
                // string, e.g. if they enter 'FEED_*' it can show 'FEED_1', 'FEED_2', etc.
                // This is different behaviour to entering a quick filter term to select a single value.
                // Also, it is not ideal as it is complete match and case sens which is inconsistent with the rest.
                // It is not at all obvious to the user what is going on.
                predicate = createWildCardedPredicate(modifiedInput);
            } else {
                // Would be nice to use chars anywhere for the default but that needs ranked matches which
                // we can't do when filtering the trees
                predicate = createCaseInsensitiveContainsPredicate(modifiedInput);
            }
        }

        if (isNegated) {
            predicate = predicate.negate();
            LOGGER.debug("Negating predicate");
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
            final boolean result = predicate.test(str);
            final ConsoleColour colour = result
                    ? ConsoleColour.GREEN
                    : ConsoleColour.RED;

            final String msg = ConsoleColour.colourise(LogUtil.message("String under test [{}], result: {}",
                    str, result), colour);
            LOGGER.trace(msg);
            return result;
        };
    }

    public static Comparator<String> createMatchComparator(final String userInput) {

        if (userInput == null) {
            return String.CASE_INSENSITIVE_ORDER;
        } else if (userInput.startsWith(REGEX_PREFIX)
                   || userInput.startsWith(STARTS_WITH_PREFIX)
                   || userInput.startsWith(ENDS_WITH_PREFIX)
                   || userInput.startsWith(EXACT_MATCH_PREFIX)
                   || userInput.startsWith(WORD_BOUNDARY_PREFIX)
                   || userInput.startsWith(NOT_OPERATOR_STR)) {
            return String.CASE_INSENSITIVE_ORDER;
        } else {
            // TODO @AT Need to consider how to rank word boundary matches
            // applicable for contains and chars anywhere
            final String strippedUserInput = stripPrefixesAndSuffixes(userInput);
            return createShortestMatchComparator(strippedUserInput);
        }
    }

    /**
     * Filters the stream using a predicate determined by prefixes in the user input. The results
     * may be ranked depending on the predicate used.
     * See {@link StringPredicateFactory#createFuzzyMatchPredicate(String)}
     *
     * @param stream    A stream of strings
     * @param userInput The search term
     * @param limit     Number of items to return
     * @return Filtered and optionally ranked values
     */
    public static List<String> filterAndSort(final Stream<String> stream,
                                             final String userInput,
                                             final long limit) {
        return stream
                .filter(createFuzzyMatchPredicate(userInput))
                .sorted(createMatchComparator(userInput))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static String stripPrefixesAndSuffixes(final String userInput) {
        if (userInput.startsWith(STARTS_WITH_PREFIX) && userInput.endsWith(ENDS_WITH_PREFIX)) {
            return userInput.substring(1, userInput.length() - 1);
        } else {
            for (final String prefix : ALL_PREFIXES) {
                if (userInput.startsWith(prefix)) {
                    return userInput.substring(1);
                }
            }
            return userInput;
        }
    }

//    private static Comparator<String> createContiguousBlockCountComparator(final String userInput) {
//        // TODO @AT null safety
//        final String userInputLower = userInput.toLowerCase();
//        final Comparator<String> naturalOrder = Comparator.naturalOrder();
//        if (userInputLower.length() > 0) {
//            return (str1, str2) -> {
//                final String str1Lower = str1.toLowerCase();
//                final String str2Lower = str2.toLowerCase();
//                final boolean str1ContainsInput = str1Lower.contains(userInput);
//                final boolean str2ContainsInput = str2Lower.contains(userInput);
//
//                if (str1ContainsInput && !str2ContainsInput) {
//                    return -1;
//                } else if (!str1ContainsInput && str2ContainsInput) {
//                    return 1;
//                } else if (str1ContainsInput && str2ContainsInput) {
//                    // Both contain input whole so compare on start index
//                    return Integer.compare(str1Lower.indexOf(userInput), str2Lower.indexOf(userInput));
//                } else {
//                    return compareContiguousBlocks(userInput, naturalOrder, str1Lower, str2Lower);
//                }
//            };
//        } else {
//            return Comparator.naturalOrder();
//        }
//    }

    private static Comparator<String> createShortestMatchComparator(final String strippedUserInput) {

        final String strippedLowerUserInput = strippedUserInput.toLowerCase();

        final Pattern shortestMatchPattern = buildShortestBlocksPattern(strippedLowerUserInput);

        LOGGER.trace(() -> LogUtil.message("Pattern: {}", shortestMatchPattern.toString()));

        return (str1, str2) -> {
            final MatchInfo matchInfo1 = calculateMatchInfo(shortestMatchPattern, str1);
            final MatchInfo matchInfo2 = calculateMatchInfo(shortestMatchPattern, str2);

            if (!matchInfo1.hasMatchInfo() && !matchInfo2.hasMatchInfo()) {
                return 0;
            } else if (!matchInfo1.hasMatchInfo()) {
                return -1;
            } else if (!matchInfo2.hasMatchInfo()) {
                return 1;
            } else {
                final int compareResult = matchInfo1
                        .compareTo(matchInfo2);
                LOGGER.trace(() -> LogUtil.message(
                        "  For input [{}], comparing [{}] [{}] on match length {} vs {}, position {} vs {} == {}",
                        strippedUserInput,
                        str1,
                        str2,
                        matchInfo1.matchLength,
                        matchInfo2.matchLength,
                        matchInfo1.matchIdx,
                        matchInfo2.matchIdx,
                        compareResult));
                return compareResult;
            }
        };
    }

    /**
     * For a given user input this method will return a function that can evaluate a string to see how
     * good the match is. The quality of the match is determined by the length of the matched region
     * (shorter == better) and its distance from the start of the string (closer == better).
     */
    public static Function<String, MatchInfo> createMatchInfoEvaluator(final String userInput) {

        if (userInput.startsWith(CHARS_ANYWHERE_PREFIX)) {
            // chars anywhere matching so score the match base on the shortest match of the supplied
            // letters, e.g. 'oa' input for 'foobar' gives a match len of 3
            final String strippedLowerUserInput = stripPrefixesAndSuffixes(userInput).toLowerCase();
            final Pattern shortestMatchPattern = buildShortestBlocksPattern(strippedLowerUserInput);
            LOGGER.trace(() -> LogUtil.message("Pattern: {}", shortestMatchPattern.toString()));
            return str ->
                    calculateMatchInfo(shortestMatchPattern, str);
        } else if (userInput.startsWith(REGEX_PREFIX)) {
            // User supplied regex so score on the len of the matched region
            final String strippedUserInput = stripPrefixesAndSuffixes(userInput);
            try {
                final Pattern usersPattern = Pattern.compile(strippedUserInput, Pattern.CASE_INSENSITIVE);
                return str ->
                        calculateMatchInfo(usersPattern, str);
            } catch (final PatternSyntaxException e) {
                // This is likely as the user may have not finished typing the regex
                return str -> MatchInfo.noMatchInfo();
            }
        } else {
            // All other modes just return a not found match info, e.g. if we are doing contains
            // it doesn't make much sense to compare the match length as it will be the same
            return str -> MatchInfo.noMatchInfo();
        }
    }

    private static MatchInfo calculateMatchInfo(final Pattern shortestMatchPattern,
                                                final String strLower) {

        final MatchInfo result;
        if (strLower == null) {
            result = MatchInfo.noMatchInfo();
        } else {
            final Matcher matcher = shortestMatchPattern.matcher(strLower);
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
                result = MatchInfo.noMatchInfo();
            } else {
                result = new MatchInfo(bestMatchLen, bestMatchIdx);
            }
        }
        return result;
    }

//    private static int compareContiguousBlocks(final String userInput,
//                                               final Comparator<String> naturalOrder,
//                                               final String str1Lower,
//                                               final String str2Lower) {
//        // input not contained as a whole so now we have to look at >=2 contiguous blocks
//        int lastStr1Idx = 0;
//        int lastStr2Idx = 0;
//        int str1BlockCount = 0;
//        int str2BlockCount = 0;
//        final int inputLen = userInput.length();
//        final int[] str1BlockStartPositions = new int[inputLen];
//        final int[] str2BlockStartPositions = new int[inputLen];
//
//        for (int i = 0; i < inputLen; i++) {
//            final char currInputChar = userInput.charAt(i);
//
//            final int currStr1Idx = str1Lower.indexOf(currInputChar, lastStr1Idx);
//            if (currStr1Idx != -1 && (i == 0 || currStr1Idx - lastStr1Idx > 1)) {
//                // first user input char or a new contiguous block
//                str1BlockStartPositions[str1BlockCount] = currStr1Idx;
//                str1BlockCount++;
//            }
//
//            final int currStr2Idx = str2Lower.indexOf(currInputChar, lastStr2Idx);
//            if (currStr2Idx != -1 && (i == 0 || currStr2Idx - lastStr2Idx > 1)) {
//                // first user input char or a new contiguous block
//                str2BlockStartPositions[str2BlockCount] = currStr2Idx;
//                str2BlockCount++;
//            }
//            lastStr1Idx = currStr1Idx;
//            lastStr2Idx = currStr2Idx;
//
//            // TODO @AT We could maybe shortcut here if the input chars remaining are less than
//            //  the difference between the two block counts
//        }
//
//        LOGGER.trace("Input [{}], comparing [{}] [{}], block counts {} vs {}",
//                userInput,
//                str1Lower,
//                str2Lower,
//                str1BlockCount,
//                str2BlockCount);
//
//        final int compareResult;
//        if (str1BlockCount == str2BlockCount) {
//            // same block count so compare based on proximity of blocks
//            final int blockCount = str1BlockCount;
//            if (blockCount > 0) {
//                int str1FirstBlockIdx = str1BlockStartPositions[0];
//                int str2FirstBlockIdx = str2BlockStartPositions[0];
//                int str1BlockDistance = str1BlockStartPositions[blockCount - 1] - str1FirstBlockIdx;
//                int str2BlockDistance = str2BlockStartPositions[blockCount - 1] - str2FirstBlockIdx;
//
//                if (str1BlockDistance == str2BlockDistance) {
//
//                    compareResult = Integer.compare(str1FirstBlockIdx, str2FirstBlockIdx);
//                    LOGGER.trace(
//                            "  For input [{}], comparing [{}] [{}] on first block position {} vs {} == {}",
//                            userInput,
//                            str1Lower,
//                            str2Lower,
//                            str1FirstBlockIdx,
//                            str2FirstBlockIdx,
//                            compareResult);
//                } else {
//                    compareResult = Integer.compare(str1BlockDistance, str2BlockDistance);
//                    LOGGER.trace("  For input [{}], comparing [{}] [{}] on block distance {} vs {} == {}",
//                            userInput,
//                            str1Lower,
//                            str2Lower,
//                            str1BlockDistance,
//                            str2BlockDistance,
//                            compareResult);
//                }
//            } else {
//                return naturalOrder.compare(str1Lower, str2Lower);
//            }
//        } else {
//            compareResult = Integer.compare(str1BlockCount, str2BlockCount);
//            LOGGER.trace("  For input [{}], comparing [{}] [{}] on block count {} vs {} == {}",
//                    userInput, str1Lower, str2Lower, str1BlockCount, str2BlockCount, compareResult);
//        }
//        return compareResult;
//    }

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
        final Pattern pattern;
        try {
            pattern = Pattern.compile(userInput, Pattern.CASE_INSENSITIVE);
        } catch (final Exception e) {
            LOGGER.trace(() ->
                    LogUtil.message("Invalid pattern {}, due to {}", userInput, e.getMessage()));
            // Bad pattern, can't really raise an exception as the user may have just mis-typed
            // so just return a false predicate
            return str -> false;
        }

        final Predicate<String> predicate;
        try {
            predicate = pattern.asPredicate();
        } catch (final Exception e) {
            LOGGER.trace(() ->
                    LogUtil.message("Error converting pattern {} to predicate, due to {}", userInput, e.getMessage()));
            return str -> false;
        }
        return toNullSafePredicate(false, predicate);
    }

    public static Predicate<String> createWordBoundaryPredicate(final String userInput) {
        return createWordBoundaryPredicate(userInput, DEFAULT_SEPARATOR_CHAR_CLASS);
    }

    @NotNull
    public static Predicate<String> createWordBoundaryPredicate(
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

            // First split the string being tested on the separators, then see if any of the
            // parts are camel case and if so add spaces to split the camel case parts.
            // e.g. stroom.someProp.maxFileSize => "stroom some prop max file size"
            // This allows us to deal with strings that are a mix of delimited and camel case.
            final String[] separatedParts = separatorCharacterClass.split(stringUnderTest);
            final String cleanedString = Arrays.stream(separatedParts)
                    .map(StringPredicateFactory::cleanStringForWordBoundaryMatching)
                    .collect(Collectors.joining(" "));

            LOGGER.trace("cleaned stringUnderTest [{}] => [{}] has word separators",
                    stringUnderTest, cleanedString);
            return separatorPredicate.test(cleanedString);
        });
    }

    private static String cleanStringForWordBoundaryMatching(final String str) {
        if (CAMEL_CASE_PATTERN.matcher(str).matches()) {
            LOGGER.trace("str [{}] is (C|c)amelCase", str);

            // replace stuff like SQLScript with "SQL Script"
            String separatedStr = CAMEL_CASE_ABBREVIATIONS_PATTERN
                    .matcher(str)
                    .replaceAll("$1 $2");

            LOGGER.trace("separatedStr: [{}]", separatedStr);

            // Now split on camel case word boundaries (or spaces added above)
            separatedStr = CAMEL_CASE_SPLIT_PATTERN
                    .matcher(separatedStr)
                    .replaceAll(" ");

            LOGGER.trace("separatedStr: [{}]", separatedStr);

            return separatedStr;
        } else {
            return str;
        }
    }

    @NotNull
    private static Predicate<String> createSeparatedWordBoundaryPredicate(
            final String userInput,
            final Pattern separatorCharacterClass) {

        // Has some uppercase so use word boundaries
        // An upper case letter means the start of a word
        // a lower case letter means the continuation of a word
        // A digit after a letter means the start of a word
        // A digit after a digit means the continuation of a word.

        final String separator = "\\W";

        final StringBuilder patternBuilder = new StringBuilder();
        char lastChr = 0;
        for (int i = 0; i < userInput.length(); i++) {
            final char chr = userInput.charAt(i);

            if (Character.isUpperCase(chr)
                || (Character.isDigit(chr) && Character.isLetter(lastChr))) {
                if (i == 0) {
                    // First letter so is either preceded by ^ or by a separator
                    patternBuilder
                            .append("(?:^|") // non-capturing
                            .append(separator)
                            .append("+")
                            .append(")");
                } else {
                    // Not the first letter so need the end of the previous word
                    // and a word separator
                    patternBuilder
//                            .append(CASE_INSENS_WORD_LETTER_CHAR_CLASS)
                            .append(".*?")
                            .append(separator)
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
            lastChr = chr;
        }
        final Pattern pattern = Pattern.compile(
                patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
        LOGGER.trace("Using separated word pattern: {} with separators {}",
                pattern, separator);

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
            final char chr = userInput.charAt(i);

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
        LOGGER.trace("Creating chars appear anywhere in correct order predicate");
        // All lower case so match on each char appearing somewhere in the text
        // in the correct order
        final String lowerCaseInput = userInput.toLowerCase();
        final Pattern pattern = buildCharsAnywherePattern(lowerCaseInput);
        LOGGER.trace("Using case insensitive pattern: {}", pattern);
        return toNullSafePredicate(false, pattern.asPredicate());
    }

    private static Pattern buildCharsAnywherePattern(final String lowerCaseInput) {
        final StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < lowerCaseInput.length(); i++) {
            patternBuilder.append(".*?"); // no-greedy match all
            final char chr = lowerCaseInput.charAt(i);
            if (Character.isLetterOrDigit(chr)) {
                patternBuilder.append(chr);
            } else {
                // Might be a special char so escape it
                patternBuilder.append(Pattern.quote(String.valueOf(chr)));
            }
        }
        patternBuilder.append(".*?");
        return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * 'map' => 'm[^m]*?a[^a]*?p'
     */
    private static Pattern buildShortestBlocksPattern(final String lowerCaseInput) {
        final StringBuilder patternBuilder = new StringBuilder();
        final int inputLen = lowerCaseInput.length();
        for (int i = 0; i < inputLen; i++) {
//            patternBuilder.append(".*?"); // no-greedy match all

            final char chr = lowerCaseInput.charAt(i);
            if (Character.isLetterOrDigit(chr)) {
                patternBuilder.append(chr);
            } else {
                // Might be a special char so escape it
                patternBuilder.append(Pattern.quote(String.valueOf(chr)));
            }
            if (i < inputLen - 1) {
                if (Character.isLetterOrDigit(chr)) {
                    patternBuilder.append("[^");
                    patternBuilder.append(chr);
                    patternBuilder.append("]*?"); // match as few chars between letters as poss
                } else {
                    // Might be a special char so escape it
                    patternBuilder.append("[^");
                    patternBuilder.append(Pattern.quote(String.valueOf(chr)));
                    patternBuilder.append("]*?"); // match as few chars between letters as poss
                }
            }
        }
        return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    @NotNull
    private static Predicate<String> createWildCardedPredicate(final String userInput) {
        LOGGER.trace("Creating case sensitive wild-carded predicate");
        // Like a case sensitive exact match but with wildcards
        final StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < userInput.length(); i++) {

            final char chr = userInput.charAt(i);
            if (chr == '*') {
                patternBuilder.append(".*?"); // no-greedy match all
            } else if (Character.isLetterOrDigit(chr)) {
                patternBuilder.append(chr);
            } else {
                // Might be a special char so escape it
                patternBuilder.append(Pattern.quote(String.valueOf(chr)));
            }
        }
        final Pattern pattern = Pattern.compile(patternBuilder.toString());
        LOGGER.trace("Using pattern: {}", pattern);
        // Use asMatchPredicate rather than asPredicate so we match on the full string
        return toNullSafePredicate(false, pattern.asMatchPredicate());
    }

    @NotNull
    private static Predicate<String> createCaseInsensitiveExactMatchPredicate(final String userInput) {
        LOGGER.trace("Creating case insensitive exact match predicate");
        final String lowerCaseInput = userInput.toLowerCase();
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

    public static final class MatchInfo implements Comparable<MatchInfo> {

        private static final MatchInfo NO_MATCH_INFO = new MatchInfo(-1, -1);
        private final int matchLength;
        private final int matchIdx;

        // Smallest match length is better, e.g. 'map', vs 'm    a     p'
        // then compare on proximity to start of the string
        private static final Comparator<MatchInfo> COMPARATOR = Comparator
                .nullsLast(Comparator.comparingInt(MatchInfo::getMatchLength)
                        .thenComparingInt(MatchInfo::getMatchIdx));

        private MatchInfo(final int matchLength, final int matchIdx) {
            this.matchLength = matchLength;
            this.matchIdx = matchIdx;
        }

        public static MatchInfo noMatchInfo() {
            return NO_MATCH_INFO;
        }

        @Override
        public int compareTo(final MatchInfo other) {
            return COMPARATOR.compare(this, other);
        }

        public int getMatchLength() {
            return matchLength;
        }

        public int getMatchIdx() {
            return matchIdx;
        }

        public boolean hasMatchInfo() {
            return matchLength != -1;
        }

        public static MatchInfo addScores(final MatchInfo matchInfo1, final MatchInfo matchInfo2) {

            // e.g. if we are ORing two default fields then one may match but the other not, so
            // ignore the non-matching one.

            if (matchInfo1.hasMatchInfo() && matchInfo2.hasMatchInfo()) {
                return new MatchInfo(
                        matchInfo1.matchLength + matchInfo2.matchLength,
                        matchInfo1.matchIdx + matchInfo2.matchIdx);
            } else if (matchInfo1.hasMatchInfo()) {
                return matchInfo1;
            } else {
                return matchInfo2;
            }
        }

        public static MatchInfo bestMatch(final MatchInfo matchInfo1, final MatchInfo matchInfo2) {

            // e.g. if we are ORing two default fields then one may match but the other not, so
            // ignore the non-matching one.

            if (matchInfo1.hasMatchInfo() && matchInfo2.hasMatchInfo()) {
                final int compareResult = matchInfo1.compareTo(matchInfo2);
                if (compareResult < 0) {
                    return matchInfo1;
                } else if (compareResult > 0) {
                    return matchInfo2;
                } else {
                    // equal
                    return matchInfo1;
                }
            } else if (matchInfo1.hasMatchInfo()) {
                return matchInfo1;
            } else {
                return matchInfo2;
            }
        }

        @Override
        public String toString() {
            return "len[" + matchLength + "] pos[" + matchIdx + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MatchInfo matchInfo = (MatchInfo) o;
            return matchLength == matchInfo.matchLength && matchIdx == matchInfo.matchIdx;
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchLength, matchIdx);
        }
    }
}
