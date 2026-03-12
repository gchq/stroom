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

package stroom.util;

import stroom.test.common.TestUtil;
import stroom.util.PredicateUtil.CountingBiPredicate;
import stroom.util.PredicateUtil.CountingPredicate;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestPredicateUtil {

    private static final Predicate<String> APPLE_PREDICATE = "apple"::equalsIgnoreCase;
    private static final Predicate<String> ORANGE_PREDICATE = "orange"::equalsIgnoreCase;
    private static final Predicate<String> PEAR = "pear"::equalsIgnoreCase;

    private static final List<String> ALL_FRUITS = List.of(
            "apple",
            "orange",
            "pear",
            "banana",
            "kiwi"
    );

    @TestFactory
    Stream<DynamicTest> andPredicates() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Predicate<String>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final Predicate<String> predicate = PredicateUtil.andPredicates(
                            testCase.getInput(), val -> true);
                    return ALL_FRUITS.stream()
                            .filter(predicate)
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, ALL_FRUITS)
                .addCase(Collections.emptyList(), ALL_FRUITS)
                .addNamedCase("apple", List.of(APPLE_PREDICATE), List.of("apple"))
                .addNamedCase("apple+orange", List.of(APPLE_PREDICATE, ORANGE_PREDICATE), Collections.emptyList())
                .addNamedCase(
                        "2 predicates",
                        List.of(v -> v.endsWith("e"),
                                v -> v.contains("p")
                        ),
                        List.of("apple"))
                .addNamedCase(
                        "3 predicates",
                        List.of(v -> v.contains("e"),
                                v -> v.contains("a"),
                                v -> v.contains("p")
                        ),
                        List.of("apple", "pear"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> orPredicates() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Predicate<String>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final Predicate<String> predicate = PredicateUtil.orPredicates(
                            testCase.getInput(), val -> true);
                    return ALL_FRUITS.stream()
                            .filter(predicate)
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, ALL_FRUITS)
                .addCase(Collections.emptyList(), ALL_FRUITS)
                .addCase(List.of(APPLE_PREDICATE), List.of("apple"))
                .addCase(List.of(APPLE_PREDICATE, ORANGE_PREDICATE), List.of("apple", "orange"))
                .addCase(
                        List.of(v -> v.endsWith("e"),
                                v -> v.contains("k")
                        ),
                        List.of("apple", "orange", "kiwi"))
                .addCase(
                        List.of(v -> v.contains("e"),
                                v -> v.contains("a"),
                                v -> v.contains("p")
                        ),
                        List.of("apple", "orange", "pear", "banana"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCaseSensitiveContainsPredicate() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final String subStr = testCase.getInput()._2;
                    final Predicate<String> predicate = PredicateUtil.caseSensitiveContainsPredicate(subStr);
                    return predicate.test(str);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of("foorbar", null), false)
                .addCase(Tuple.of(null, "foobar"), false)
                .addCase(Tuple.of("foobar", "foo"), true)
                .addCase(Tuple.of("foobar", "ob"), true)
                .addCase(Tuple.of("foobar", "foobar"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCaseInsensitiveContainsPredicate() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final String subStr = testCase.getInput()._2;
                    final Predicate<String> predicate = PredicateUtil.caseInsensitiveContainsPredicate(subStr);
                    return predicate.test(str);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of("FOORBAR", null), false)
                .addCase(Tuple.of(null, "foobar"), false)
                .addCase(Tuple.of("FOOBAR", "foo"), true)
                .addCase(Tuple.of("FOOBAR", "ob"), true)
                .addCase(Tuple.of("FOOBAR", "foobar"), true)
                .build();
    }

    @Test
    void testCountingPredicate() {
        final Predicate<Integer> isEvenPredicate = (Integer i) -> i % 2 == 0;
        final CountingPredicate<Integer> countingPredicate = PredicateUtil.countingPredicate(isEvenPredicate);
        assertPredicate(countingPredicate, 1, false, 0);
        assertPredicate(countingPredicate, 1, false, 0);
        assertPredicate(countingPredicate, 2, true, 1);
        assertPredicate(countingPredicate, 2, true, 2);
        assertPredicate(countingPredicate, 3, false, 2);
        assertPredicate(countingPredicate, 4, true, 3);
        assertPredicate(countingPredicate, 4, true, 4);
        countingPredicate.reset();
        assertThat(countingPredicate.intValue())
                .isEqualTo(0);
    }

    @Test
    void testCountingPredicate_reverse() {
        final Predicate<Integer> isEvenPredicate = (Integer i) -> i % 2 == 0;
        final CountingPredicate<Integer> countingPredicate = PredicateUtil.countingPredicate(
                isEvenPredicate, false);
        assertPredicate(countingPredicate, 1, false, 1);
        assertPredicate(countingPredicate, 1, false, 2);
        assertPredicate(countingPredicate, 2, true, 2);
        assertPredicate(countingPredicate, 2, true, 2);
        assertPredicate(countingPredicate, 3, false, 3);
        assertPredicate(countingPredicate, 4, true, 3);
        assertPredicate(countingPredicate, 4, true, 3);
        countingPredicate.reset();
        assertThat(countingPredicate.intValue())
                .isEqualTo(0);
    }

    @Test
    void testCountingBiPredicate() {
        final BiPredicate<Integer, Integer> bothEvenPredicate = (i1, i2) ->
                (i1 % 2 == 0) && (i2 % 2 == 0);
        final CountingBiPredicate<Integer, Integer> countingPredicate = PredicateUtil.countingBiPredicate(
                bothEvenPredicate);
        assertBiPredicate(countingPredicate, 1, 1, false, 0);
        assertBiPredicate(countingPredicate, 1, 2, false, 0);
        assertBiPredicate(countingPredicate, 2, 2, true, 1);
        assertBiPredicate(countingPredicate, 2, 2, true, 2);
        assertBiPredicate(countingPredicate, 3, 2, false, 2);
        assertBiPredicate(countingPredicate, 4, 3, false, 2);
        assertBiPredicate(countingPredicate, 4, 4, true, 3);
        countingPredicate.reset();
        assertThat(countingPredicate.intValue())
                .isEqualTo(0);
    }

    @Test
    void testCountingBiPredicate_reversed() {
        final BiPredicate<Integer, Integer> bothEvenPredicate = (i1, i2) ->
                (i1 % 2 == 0) && (i2 % 2 == 0);
        final CountingBiPredicate<Integer, Integer> countingPredicate = PredicateUtil.countingBiPredicate(
                bothEvenPredicate, false);
        assertBiPredicate(countingPredicate, 1, 1, false, 1);
        assertBiPredicate(countingPredicate, 1, 2, false, 2);
        assertBiPredicate(countingPredicate, 2, 2, true, 2);
        assertBiPredicate(countingPredicate, 2, 2, true, 2);
        assertBiPredicate(countingPredicate, 3, 2, false, 3);
        assertBiPredicate(countingPredicate, 4, 3, false, 4);
        assertBiPredicate(countingPredicate, 4, 4, true, 4);
        countingPredicate.reset();
        assertThat(countingPredicate.intValue())
                .isEqualTo(0);
    }

    private void assertPredicate(final CountingPredicate<Integer> predicate,
                                 final int val,
                                 final boolean expectedResult,
                                 final int expectedCount) {
        final boolean result = predicate.test(val);
        assertThat(result)
                .isEqualTo(expectedResult);
        assertThat(predicate.intValue())
                .isEqualTo(expectedCount);
    }

    private void assertBiPredicate(final CountingBiPredicate<Integer, Integer> predicate,
                                   final int val1,
                                   final int val2,
                                   final boolean expectedResult,
                                   final int expectedCount) {
        final boolean result = predicate.test(val1, val2);
        assertThat(result)
                .isEqualTo(expectedResult);
        assertThat(predicate.intValue())
                .isEqualTo(expectedCount);
    }
}
