package stroom.util.string;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestStringPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringPredicateFactory.class);

    @TestFactory
    List<DynamicTest> fuzzyMatcherTestFactory() {
        List<DynamicTest> tests = new ArrayList<>();
        tests.addAll(List.of(

                makeTest("Starts with",
                        "^this_",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "THIS_IS_MY_FEED_TOO",
                                "this_is_my_feed_too"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeTest("Starts with (caret)",
                        "^^this_",
                        List.of("^THIS_IS_MY_FEED",
                                "^this_is_my_feed",
                                "^THIS_IS_MY_FEED_TOO",
                                "^this_is_my_feed_too"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeTest("Ends with",
                        "feed$",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "so_is_this_is_my_feed"),
                        List.of("THIS_IS_MY_FEED_NOT")),

                makeTest("Ends with (dollar)",
                        "feed$$",
                        List.of("THIS_IS_MY_FEED$",
                                "this_is_my_feed$",
                                "SO_IS_THIS_IS_MY_FEED$",
                                "so_is_this_is_my_feed$"),
                        List.of("THIS_IS_MY_FEED_NOT")),

                makeTest("Exact match",
                        "^this_is_my_feed$",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed"),
                        List.of("NOT_THIS_IS_MY_FEED", "NOT_THIS_IS_MY_FEED_NOT", "THIS_IS_MY_FEED_NOT")),

                makeTest("Exact match (caret, dollar)",
                        "^^this_is_my_feed$$",
                        List.of("^THIS_IS_MY_FEED$",
                                "^this_is_my_feed$"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeTest("Chars anywhere 1",
                        "timf",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "timf",
                                "TIMF",
                                "th  i   s i  s m  y feed" ),
                        List.of("NOT_THIS_IS_MY_XEED", "fmit", "FMIT")),

                makeTest("Chars anywhere 1 (upper case)",
                        "TIMF",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "timf",
                                "TIMF",
                                "th  i   s i  s m  y feed" ),
                        List.of("NOT_THIS_IS_MY_XEED", "fmit", "FMIT")),

                makeTest("Chars anywhere 2",
                        "t_i_m_f",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("NOT_THIS_IS_MY_XEED", "timf")),

                makeTest("Chars anywhere 2 (upper case)",
                        "T_I_M_F",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("NOT_THIS_IS_MY_XEED", "timf")),

                makeTest("Chars anywhere (numbers)",
                        "99",
                        List.of("THIS_IS_FEED_99",
                                "99_THIS_IS_FEED",
                                "THIS_IS_99_FEED"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeTest("Chars anywhere (special chars)",
                        "(xml)",
                        List.of("Events (XML)",
                                "Events (XML) too",
                                "(XML) Events"),
                        List.of("Events XML")),

                makeTest("Word boundary match 1",
                        "?TIMF",
                        List.of("THIS_IS_MY_FEED",
                                "THIS__IS__MY__FEED",
                                "THIS-IS-MY-FEED",
                                "THIS  IS  MY  FEED",
                                "THIS IS MY FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("timf", "TIMF")),

                makeTest("Word boundary match 2",
                        "?ThIsMF",
                        List.of("THIS_IS_MY_FEED",
                                "THIS-IS-MY-FEED",
                                "THIS IS MY FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("TXHIS_IS_MY_FEED", "timf", "TIMF")),

                makeTest("Word boundary match 3",
                        "?OTheiMa",
                        List.of("the cat sat on their mat",
                                "on their mat",
                                "Of their magic"),
                        List.of("the cat sat on the mat", "sat on there mat", "ON THE MIX")),

                makeTest("Word boundary match 4",
                        "?OTheiMa",
                        List.of("theCatSatOnTheirMat",
                                "TheCatSatOnTheirMat",
                                "OfTheirMagic"),
                        List.of("theCatSatOnTheMat", "satOnThereMat", "OnTheMix", "on their moat")),

                makeTest("Word boundary match 5",
                        "?CPSP",
                        List.of("CountPipelineSQLPipe",
                                "CountPipelineSwimPipe"),
                        List.of("CountPipelineSoQueueLongPipe")),

                makeTest("Word boundary (brackets)",
                        "?Xml",
                        List.of("Events (XML)",
                                "Events (XML) too",
                                "Events [XML] too",
                                "Events XML",
                                "(XML) Events",
                                "(XML)"),
                        List.of("XXML")),

                makeTest("Word boundary match (numbers)",
                        "?A99",
                        List.of("THIS_IS_MY_FEED_a99",
                                "a99_this_is_my_feed",
                                "IS_THIS_IS_a99_FEED"),
                        List.of("TXHIS_IS_MY_FEED", "timf", "TIMF")),

                makeTest("Single letter (lower case)",
                        "b",
                        List.of("B", "BCD", "ABC", "b", "bcd", "abc"),
                        List.of("A", "C")),

                makeTest("Single letter (upper case)",
                        "B",
                        List.of("B", "BCD", "XX_BCD", "ABC"),
                        List.of("A", "C")),

                makeTest("Regex partial match",
                        "/(wo)?man$",
                        List.of("a Man",
                                "MAN",
                                "A Woman",
                                "human"),
                        List.of("A MAN WALKED BY",
                                "WOMAN ",
                                "Manly")),

                makeTest("Regex full match",
                        "/^(wo)?man$",
                        List.of("Man",
                                "MAN",
                                "Woman"),
                        List.of("A MAN WALKED BY",
                                "WOMAN ",
                                "human",
                                "Manly")),

                makeTest("Invalid Regex, nothing will match",
                        "/(wo?man$",
                        List.of(),
                        List.of("MAN",
                                "A MAN",
                                "A MAN WALKED BY",
                                "WOMAN")),

                makeTest("No user input",
                        "",
                        List.of("B", "BCD", "XX_BCD"),
                        Collections.emptyList()),

                makeTest("Null/empty items",
                        "a",
                        List.of("A", "ABCD", "abcd", "dcba"),
                        Arrays.asList("", null))
        ));

        return tests;
    }

    private void doFuzzyMatchTest(final String userInput,
                                  final List<String> expectedMatches,
                                  final List<String> expectedNonMatches) {

        final List<String> actualMatches = Stream.concat(expectedMatches.stream(),
                expectedNonMatches.stream())
                .filter(StringPredicateFactory.createFuzzyMatchPredicate(userInput))
                .collect(Collectors.toList());

        Assertions.assertThat(actualMatches)
                .containsExactlyInAnyOrderElementsOf(expectedMatches);
    }

    private DynamicTest makeTest(final String testName,
                                 final String userInput,
                                 final List<String> expectedMatches,
                                 final List<String> expectedNonMatches) {
        return DynamicTest.dynamicTest(testName, () ->
                doFuzzyMatchTest(userInput, expectedMatches, expectedNonMatches));
    }

}