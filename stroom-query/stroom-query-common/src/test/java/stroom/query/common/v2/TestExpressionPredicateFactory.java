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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.UserTimeZone;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExpressionPredicateFactory.class);

    private static final ValueFunctionFactories<String> TEXT_VALUE_FUNCTION_FACTORIES = fieldName ->
            new StringValueFunctionFactory(QueryField.builder().fldName("test").fldType(FieldType.TEXT).build());

    private static final ValueFunctionFactories<String> DATE_VALUE_FUNCTION_FACTORIES = fieldName ->
            new StringValueFunctionFactory(QueryField.builder().fldName("test").fldType(FieldType.DATE).build());

    @TestFactory
    List<DynamicTest> textMatcherTestFactory() {

        // Each test is run in normal ("foorbar") and negated form ("!foorbar")
        return new ArrayList<>(List.of(
                makeStringMatchTest("Contains",
                        "map",
                        List.of("map",
                                "a map",
                                "mapping"),
                        List.of("maap")),

                makeStringMatchTest("Contains with operator",
                        "+map",
                        List.of("map",
                                "a map",
                                "mapping"),
                        List.of("maap")),

                makeStringMatchTest("Contains with operator (case sensitive)",
                        "=+map",
                        List.of("map",
                                "a map",
                                "mapping"),
                        List.of("MAP",
                                "A MAP",
                                "MAAP")),

                makeStringMatchTest("Starts with",
                        "^this_",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "THIS_IS_MY_FEED_TOO",
                                "this_is_my_feed_too"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeStringMatchTest("Starts with (case sensitive)",
                        "=^this_",
                        List.of("this_is_my_feed",
                                "this_is_my_feed_too"),
                        List.of("THIS_IS_MY_FEED",
                                "THIS_IS_MY_FEED_TOO")),

                makeStringMatchTest("Starts with (caret)",
                        "^^this_",
                        List.of("^THIS_IS_MY_FEED",
                                "^this_is_my_feed",
                                "^THIS_IS_MY_FEED_TOO",
                                "^this_is_my_feed_too"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeStringMatchTest("Ends with",
                        "$feed",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "so_is_this_is_my_feed"),
                        List.of("THIS_IS_MY_FEED_NOT")),

                makeStringMatchTest("Ends with (case sensitive)",
                        "=$feed",
                        List.of("this_is_my_feed",
                                "so_is_this_is_my_feed"),
                        List.of("THIS_IS_MY_FEED",
                                "SO_IS_THIS_IS_MY_FEED")),

                makeStringMatchTest("Ends with (dollar)",
                        "$feed$",
                        List.of("THIS_IS_MY_FEED$",
                                "this_is_my_feed$",
                                "SO_IS_THIS_IS_MY_FEED$",
                                "so_is_this_is_my_feed$"),
                        List.of("THIS_IS_MY_FEED_NOT")),

                makeStringMatchTest("Exact match",
                        "=this_is_my_feed",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed"),
                        List.of("NOT_THIS_IS_MY_FEED", "NOT_THIS_IS_MY_FEED_NOT", "THIS_IS_MY_FEED_NOT")),

                makeStringMatchTest("Greater than",
                        ">DD",
                        List.of("EE",
                                "FF"),
                        List.of("CC", "DD")),

                makeStringMatchTest("Greater than or equals",
                        ">=DD",
                        List.of("DD",
                                "EE",
                                "FF"),
                        List.of("CC")),

                makeStringMatchTest("Less than",
                        "<DD",
                        List.of("BB",
                                "CC"),
                        List.of("DD", "EE")),

                makeStringMatchTest("Less than or equals",
                        "<=DD",
                        List.of("BB",
                                "DD",
                                "DD"),
                        List.of("EE")),

                makeStringMatchTest("Exact match (case sensitive)",
                        "==this_is_my_feed",
                        List.of("this_is_my_feed"),
                        List.of("THIS_IS_MY_FEED")),

                makeStringMatchTest("Chars anywhere 1",
                        "~timf",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "timf",
                                "TIMF",
                                "th  i   s i  s m  y feed"),
                        List.of("NOT_THIS_IS_MY_XEED", "fmit", "FMIT")),

                makeStringMatchTest("Chars anywhere 1 (upper case)",
                        "~TIMF",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "timf",
                                "TIMF",
                                "th  i   s i  s m  y feed"),
                        List.of("NOT_THIS_IS_MY_XEED", "fmit", "FMIT")),

                makeStringMatchTest("Chars anywhere 2",
                        "~t_i_m_f",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("NOT_THIS_IS_MY_XEED", "timf")),

                makeStringMatchTest("Chars anywhere 2 (upper case)",
                        "~T_I_M_F",
                        List.of("THIS_IS_MY_FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("NOT_THIS_IS_MY_XEED", "timf")),

                makeStringMatchTest("Chars anywhere (numbers)",
                        "~99",
                        List.of("THIS_IS_FEED_99",
                                "99_THIS_IS_FEED",
                                "THIS_IS_99_FEED"),
                        List.of("NOT_THIS_IS_MY_FEED")),

                makeStringMatchTest("Chars anywhere (special chars)",
                        "~(xml)",
                        List.of("Events (XML)",
                                "Events (XML) too",
                                "(XML) Events"),
                        List.of("Events XML")),

                makeStringMatchTest("Word boundary match 1",
                        "?TIMF",
                        List.of("THIS_IS_MY_FEED",
                                "THIS__IS__MY__FEED",
                                "THIS-IS-MY-FEED",
                                "THIS  IS  MY  FEED",
                                "this.is.my.feed",
                                "THIS IS MY FEED",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED",
                                "SO_IS_THIS_IS_MY_FEED_TOO"),
                        List.of("timf", "TIMF")),

                makeStringMatchTest("Word boundary match 2",
                        "?ThIsMF",
                        List.of("THIS_IS_MY_FEED",
                                "THIS-IS-MY-FEED",
                                "THIS IS MY FEED",
                                "this.is.my.feed",
                                "this_is_my_feed",
                                "SO_IS_THIS_IS_MY_FEED"),
                        List.of("TXHIS_IS_MY_FEED", "timf", "TIMF")),

                makeStringMatchTest("Word boundary match 3",
                        "?OTheiMa",
                        List.of("the cat sat on their mat",
                                "on their mat",
                                "Of their magic"),
                        List.of("the cat sat on the mat", "sat on there mat", "ON THE MIX")),

                makeStringMatchTest("Word boundary match 4",
                        "?OTheiMa",
                        List.of("theCatSatOnTheirMat",
                                "TheCatSatOnTheirMat",
                                "OfTheirMagic"),
                        List.of("theCatSatOnTheMat", "satOnThereMat", "OnTheMix", "on their moat")),

                makeStringMatchTest("Word boundary match 5",
                        "?CPSP",
                        List.of("CountPipelineSQLPipe",
                                "CountPipelineSwimPipe",
                                "CountPipelineSoQueueLongPipe"),
                        List.of("CountPipelineSoQueueLong")),

                makeStringMatchTest("Word boundary match 6 (camel + delimited) ",
                        "?JDCN",
                        List.of("stroom.job.db.connection.jdbcDriverClassName"),
                        List.of("stroom.job.db.connection.jdbcDriverPassword")),

                makeStringMatchTest("Word boundary match 7 (camel + delimited) ",
                        "?SJDCJDCN",
                        List.of("stroom.job.db.connection.jdbcDriverClassName"),
                        List.of("stroom.job.db.connection.jdbcDriverPassword")),

                makeStringMatchTest("Word boundary match 8",
                        "?MFN",
                        List.of("MY_FEED NAME"),
                        List.of("MY FEEDNAME")),

                makeStringMatchTest("Word boundary match 9 (one word)",
                        "?A",
                        List.of("alpha",
                                "alpha bravo",
                                "bravo alpha"),
                        List.of("bravo")),

                makeStringMatchTest("Word boundary (brackets)",
                        "?Xml",
                        List.of("Events (XML)",
                                "Events (XML) too",
                                "Events [XML] too",
                                "Events XML",
                                "(XML) Events",
                                "(XML)"),
                        List.of("XXML")),

                makeStringMatchTest("Word boundary match (numbers)",
                        "?A99",
                        List.of("THIS_IS_MY_FEED_a99",
                                "a99_this_is_my_feed",
                                "IS_THIS_IS_a99_FEED"),
                        List.of("TXHIS_IS_MY_FEED", "timf", "TIMF")),

                makeStringMatchTest("Word boundary match (numbers) 2",
                        "?TIM",
                        List.of("THIS_IS_MY_FEED_a99",
                                "a99_this_is_my_feed",
                                "TXHIS_IS_MY_FEED",
                                "IS_THIS_IS_a99_MY_FEED"),
                        List.of("IS_THIS_IS_a99_A_FEED", "timf", "TIMF")),

                makeStringMatchTest("Single letter (lower case)",
                        "b",
                        List.of("B", "BCD", "ABC", "b", "bcd", "abc"),
                        List.of("A", "C")),

                makeStringMatchTest("Single letter (upper case)",
                        "B",
                        List.of("B", "BCD", "XX_BCD", "ABC"),
                        List.of("A", "C")),

                makeStringMatchTest("Regex partial match",
                        "/(wo)?man$",
                        List.of("a Man",
                                "MAN",
                                "A Woman",
                                "human"),
                        List.of("A MAN WALKED BY",
                                "WOMAN ",
                                "Manly")),

                makeStringMatchTest("Regex full match",
                        "/^(wo)?man$",
                        List.of("Man",
                                "MAN",
                                "Woman"),
                        List.of("A MAN WALKED BY",
                                "WOMAN ",
                                "human",
                                "Manly")),

//                makeFuzzyMatchTest("Invalid Regex, nothing will match",
//                        "/(wo?man$",
//                        List.of(),
//                        List.of("MAN",
//                                "A MAN",
//                                "A MAN WALKED BY",
//                                "WOMAN")),

                makeStringMatchTest("Regex with null values",
                        "/^man",
                        List.of("MAN"),
                        Arrays.asList(null,
                                "A MAN",
                                "WOMAN")),

                makeStringMatchTest("No user input",
                        "",
                        List.of("B", "BCD", "XX_BCD"),
                        Collections.emptyList()),

                makeStringMatchTest("Null/empty items",
                        "a",
                        List.of("A", "ABCD", "abcd", "dcba"),
                        Arrays.asList("", null))
        ));
    }

    @TestFactory
    List<DynamicTest> dateMatcherTestFactory() {

        // Each test is run in normal ("foorbar") and negated form ("!foorbar")
        return new ArrayList<>(List.of(

                makeDateMatchTest("Date equals",
                        "=\"2000-01-01T00:00:00.000Z\"",
                        List.of("2000-01-01T00:00:00.000Z"),
                        List.of("2001-01-01T00:00:00.000Z")),

                makeDateMatchTest("Date equals date expression",
                        "=week()",
                        List.of("1999-12-27T00:00:00.000Z"),
                        List.of("2001-01-01T00:00:00.000Z")),

                makeDateMatchTest("Date greater than",
                        ">\"2000-01-01T00:00:00.000Z\"",
                        List.of("2001-01-01T00:00:00.000Z"),
                        List.of("1999-01-01T00:00:00.000Z")),

                makeDateMatchTest("Date greater than or equal to",
                        ">=week()",
                        List.of("1999-12-27T00:00:00.000Z",
                                "2001-01-01T00:00:00.000Z"),
                        List.of("1999-01-01T00:00:00.000Z")),

                makeDateMatchTest("Date less than",
                        "<\"2000-01-01T00:00:00.000Z\"",
                        List.of("1999-01-01T00:00:00.000Z"),
                        List.of("2001-01-01T00:00:00.000Z")),

                makeDateMatchTest("Date less than or equal to",
                        "<=week()",
                        List.of("1999-12-27T00:00:00.000Z",
                                "1999-01-01T00:00:00.000Z"),
                        List.of("2001-01-01T00:00:00.000Z"))
        ));
    }

    @TestFactory
    List<DynamicTest> comparatorTestFactory() {
        return new ArrayList<>(List.of(
                makeComparatorTest("1",
                        "catmat",
                        List.of(
                                "catmat",
                                "the catmat",
                                "cat mat",
                                "the cat mat",
                                "the cat the mat",
                                "the cat on the mat",
                                "the cat sat on the mat"
                        ))
        ));
    }

    @Test
    void testWildcardReplacement() {
        testWildcardReplacement("this*", "this.*");
        testWildcardReplacement("this?", "this.");
        testWildcardReplacement("this\\*", "this*");
        testWildcardReplacement("this\\?", "this?");
        testWildcardReplacement("this\\", "this\\");
        testWildcardReplacement("th*is", "th.*is");
        testWildcardReplacement("th?is", "th.is");
        testWildcardReplacement("th\\*is", "th*is");
        testWildcardReplacement("th\\?is", "th?is");
        testWildcardReplacement("th\\is", "th\\is");
        testWildcardReplacement("*user1 (xxx) yyy*", ".*user1\\Q \\E\\Q(\\Exxx\\Q)\\E\\Q \\Eyyy.*");
    }

    @Test
    void testNullExpression() {
        final QueryField field = QueryField.createText("myField");
        final ValueFunctionFactories<String> valueFunctionFactories = StringValueFunctionFactory.create(field);

        final ExpressionOperator expressionOperator = null;

        final Predicate<String> stringPredicate = new ExpressionPredicateFactory().create(
                expressionOperator,
                valueFunctionFactories);

        assertThat(stringPredicate.test("foo"))
                .isTrue();
        assertThat(stringPredicate.test("bar"))
                .isTrue();
    }

    @Test
    void testNullOperator() {
        final QueryField field = QueryField.createText("myField");
        final ValueFunctionFactories<String> valueFunctionFactories = StringValueFunctionFactory.create(field);

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(null)
                .build();

        final Predicate<String> stringPredicate = new ExpressionPredicateFactory().create(
                expressionOperator,
                valueFunctionFactories);

        assertThat(stringPredicate.test("foo"))
                .isTrue();
        assertThat(stringPredicate.test("bar"))
                .isTrue();
    }

    @Test
    void testEmptyOperator_and() {
        final QueryField field = QueryField.createText("myField");
        final ValueFunctionFactories<String> valueFunctionFactories = StringValueFunctionFactory.create(field);

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.AND)
                .build();

        final Predicate<String> stringPredicate = new ExpressionPredicateFactory().create(expressionOperator,
                valueFunctionFactories);

        assertThat(stringPredicate.test("foo"))
                .isTrue();
        assertThat(stringPredicate.test("bar"))
                .isTrue();
    }

    @Test
    void testEmptyOperator_or() {
        final QueryField field = QueryField.createText("myField");
        final ValueFunctionFactories<String> valueFunctionFactories = StringValueFunctionFactory.create(field);

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.OR)
                .build();

        final Predicate<String> stringPredicate = new ExpressionPredicateFactory().create(expressionOperator,
                valueFunctionFactories);

        assertThat(stringPredicate.test("foo"))
                .isTrue();
        assertThat(stringPredicate.test("bar"))
                .isTrue();
    }

    @Test
    void testCreate() {
        final QueryField field = QueryField.createText("myField");
        final ValueFunctionFactories<String> valueFunctionFactories = StringValueFunctionFactory.create(field);

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(ExpressionTerm.equals(field.getFldName(), "foo"))
                .build();

        final Predicate<String> stringPredicate = new ExpressionPredicateFactory().create(expressionOperator,
                valueFunctionFactories);

        assertThat(stringPredicate.test("foo"))
                .isTrue();
        assertThat(stringPredicate.test("bar"))
                .isFalse();
    }

    private void testWildcardReplacement(final String in, final String expected) {
        final String out;
        if (ExpressionPredicateFactory.containsWildcard(in)) {
            out = ExpressionPredicateFactory.replaceWildcards(in);
        } else {
            out = ExpressionPredicateFactory.unescape(in);
        }
        assertThat(out).isEqualTo(expected);
    }

    private void doStringMatchTest(final String userInput,
                                   final List<String> expectedMatches,
                                   final List<String> expectedNonMatches) {

        LOGGER.info("Testing input [{}]", userInput);
        final List<String> actualMatches = filterAndSortStrings(
                userInput,
                Stream.concat(expectedMatches.stream(), expectedNonMatches.stream()))
                .toList();

        assertThat(actualMatches)
                .containsExactlyInAnyOrderElementsOf(expectedMatches);

        final String negatedInput = StringPredicateFactory.NOT_OPERATOR_STR + userInput;

        LOGGER.info("Testing negated input [{}]", negatedInput);
        final List<String> actualNegatedMatches = filterAndSortStrings(
                negatedInput,
                Stream.concat(expectedMatches.stream(), expectedNonMatches.stream()))
                .toList();

        assertThat(actualNegatedMatches)
                .containsExactlyInAnyOrderElementsOf(expectedNonMatches);
    }

    private Stream<String> filterAndSortStrings(final String userInput, final Stream<String> stream) {
        return new ExpressionPredicateFactory()
                .filterAndSortStream(stream, userInput, Optional.of(Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private DynamicTest makeDateMatchTest(final String testName,
                                          final String userInput,
                                          final List<String> expectedMatches,
                                          final List<String> expectedNonMatches) {
        return DynamicTest.dynamicTest(testName, () ->
                doDateMatchTest(userInput, expectedMatches, expectedNonMatches));
    }


    private void doDateMatchTest(final String userInput,
                                 final List<String> expectedMatches,
                                 final List<String> expectedNonMatches) {

        LOGGER.info("Testing input [{}]", userInput);
        final List<String> actualMatches = filterAndSortDates(
                userInput,
                Stream.concat(expectedMatches.stream(), expectedNonMatches.stream()))
                .toList();

        assertThat(actualMatches)
                .containsExactlyInAnyOrderElementsOf(expectedMatches);

        final String negatedInput = StringPredicateFactory.NOT_OPERATOR_STR + userInput;

        LOGGER.info("Testing negated input [{}]", negatedInput);
        final List<String> actualNegatedMatches = filterAndSortDates(
                negatedInput,
                Stream.concat(expectedMatches.stream(), expectedNonMatches.stream()))
                .toList();

        assertThat(actualNegatedMatches)
                .containsExactlyInAnyOrderElementsOf(expectedNonMatches);
    }

    private Stream<String> filterAndSortDates(final String userInput, final Stream<String> stream) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .referenceTime(DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"))
                .timeZone(UserTimeZone.utc())
                .build();
        return new ExpressionPredicateFactory().filterAndSortStream(
                stream,
                userInput,
                new SingleFieldProvider("test"),
                DATE_VALUE_FUNCTION_FACTORIES,
                dateTimeSettings,
                Optional.of(Comparator.naturalOrder()));
    }

    private void doComparatorTest(final String userInput,
                                  final List<String> expectedOrderedValues) {

        LOGGER.info("Testing input [{}]", userInput);

        final Comparator<String> comparator = StringPredicateFactory.createMatchComparator(userInput);
        final List<String> actualOrderedValues = expectedOrderedValues.stream()
                .sorted(comparator)
                .collect(Collectors.toList());

        assertThat(actualOrderedValues)
                .isEqualTo(expectedOrderedValues);
    }

    private DynamicTest makeStringMatchTest(final String testName,
                                            final String userInput,
                                            final List<String> expectedMatches,
                                            final List<String> expectedNonMatches) {
        return DynamicTest.dynamicTest(testName, () ->
                doStringMatchTest(userInput, expectedMatches, expectedNonMatches));
    }

    private DynamicTest makeComparatorTest(final String testName,
                                           final String userInput,
                                           final List<String> expectedOrderedValues) {
        return DynamicTest.dynamicTest(testName, () ->
                doComparatorTest(userInput, expectedOrderedValues));
    }

//    public static void main(String[] args) {
//
//        List<String> classNames;
//        try (ScanResult result = new ClassGraph()
//                .acceptPackages("stroom")
//                .enableClassInfo()
//                .ignoreClassVisibility()
//                .scan()) {
//
//            classNames = result.getAllClasses().stream()
//                    .map(ClassInfo::getName)
//                    .collect(Collectors.toList());
//        }
//
//        final Scanner scanner = new Scanner(System.in);
//        do {
//            System.out.println("Enter your search term:");
//            final String userInput = scanner.nextLine();
//            final Predicate<String> fuzzyMatchPredicate = StringPredicateFactory.createFuzzyMatchPredicate(userInput);
//            final Comparator<String> comparator = StringPredicateFactory.createMatchComparator(userInput);
//
//            final List<String> fullList = classNames.stream()
//                    .filter(fuzzyMatchPredicate)
//                    .sorted(comparator)
//                    .collect(Collectors.toList());
//
//            final String outputStr = fullList.stream()
//                    .limit(20)
//                    .collect(Collectors.joining("\n"));
//
//            System.out.println("Results [" + fullList.size() + "]:\n" + outputStr);
//        } while (scanner.hasNext());
//    }

}
