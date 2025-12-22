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

package stroom.util.shared;

import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestSeverity {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSeverity.class);

    @Test
    void testSeverities() {
        Assertions.assertThat(Severity.SEVERITIES)
                .containsExactly(
                        Severity.FATAL_ERROR,
                        Severity.ERROR,
                        Severity.WARNING,
                        Severity.INFO);
    }

    @Test
    void testDefaultSort() {
        final List<Severity> list = new ArrayList<>();
        list.add(Severity.INFO);
        list.add(Severity.FATAL_ERROR);
        list.add(Severity.WARNING);
        list.add(Severity.ERROR);
        list.add(Severity.INFO);
        list.add(Severity.WARNING);
        list.add(Severity.FATAL_ERROR);

        // Doesn't support nulls
        Collections.sort(list);

        LOGGER.debug("list:\n{}", list.stream()
                .map(Severity::toString)
                .collect(Collectors.joining("\n")));

        Assertions.assertThat(list)
                .containsExactly(
                        Severity.INFO,
                        Severity.INFO,
                        Severity.WARNING,
                        Severity.WARNING,
                        Severity.ERROR,
                        Severity.FATAL_ERROR,
                        Severity.FATAL_ERROR);
    }

    @Test
    void testCustomSort() {
        final List<Severity> list = new ArrayList<>();
        list.add(Severity.INFO);
        list.add(Severity.FATAL_ERROR);
        list.add(Severity.WARNING);
        list.add(null);
        list.add(Severity.ERROR);
        list.add(Severity.INFO);
        list.add(Severity.WARNING);
        list.add(Severity.FATAL_ERROR);

        list.sort(Severity.LOW_TO_HIGH_COMPARATOR);

        Assertions.assertThat(list)
                .containsExactly(
                        null,
                        Severity.INFO,
                        Severity.INFO,
                        Severity.WARNING,
                        Severity.WARNING,
                        Severity.ERROR,
                        Severity.FATAL_ERROR,
                        Severity.FATAL_ERROR);
    }

    @TestFactory
    Stream<DynamicTest> testMaxSeverity() {
        final Severity defaultSeverity = Severity.INFO;
        final List<Severity> onlyNulls = new ArrayList<>();
        onlyNulls.add(null);
        onlyNulls.add(null);
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Severity>>(){})
                .withOutputType(Severity.class)
                .withTestFunction(testCase ->
                        Severity.getMaxSeverity(testCase.getInput(), defaultSeverity))
                .withSimpleEqualityAssertion()
                .addNamedCase("Null list", null, defaultSeverity)
                .addNamedCase("Empty list", Collections.emptyList(), defaultSeverity)
                .addNamedCase("Only nulls in list", onlyNulls, defaultSeverity)
                .addNamedCase("One item INFO", List.of(Severity.INFO), defaultSeverity)
                .addNamedCase("One item WARNING", List.of(Severity.WARNING), Severity.WARNING)
                .addNamedCase("Many items",
                        List.of(
                                Severity.INFO,
                                Severity.FATAL_ERROR,
                                Severity.WARNING,
                                Severity.INFO,
                                Severity.WARNING,
                                Severity.FATAL_ERROR,
                                Severity.ERROR,
                                Severity.INFO),
                        Severity.FATAL_ERROR)
                .build();
    }

//    @TestFactory
//    Stream<DynamicTest> testCompare() {
//        return TestUtil.buildDynamicTestStream()
//                .withWrappedInputType(new TypeLiteral<Tuple3<
//                                        Severity,
//                                        Severity,
//                                        Function<Severity, Boolean>>>(){})
//                .withOutputType(Boolean.class)
//                .withTestFunction(testCase -> {
//                    final Severity severity1 = testCase.getInput()._1;
//                    final Severity severity2 = testCase.getInput()._2;
//                    final Function<Severity, Boolean> func = testCase.getInput()._3;
//                    func.apply()
//
//                })
//
//                .build();
//    }

    @TestFactory
    Stream<DynamicTest> testGreaterThan() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1.greaterThan(testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(Severity.INFO, Severity.INFO), false)
                .addCase(Tuple.of(Severity.INFO, Severity.WARNING), false)
                .addCase(Tuple.of(Severity.WARNING, Severity.INFO), true)
                .addCase(Tuple.of(Severity.ERROR, Severity.WARNING), true)
                .addCase(Tuple.of(Severity.FATAL_ERROR, Severity.ERROR), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGreaterThanOrEqual() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1.greaterThanOrEqual(testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(Severity.INFO, Severity.INFO), true)
                .addCase(Tuple.of(Severity.INFO, Severity.WARNING), false)
                .addCase(Tuple.of(Severity.WARNING, Severity.INFO), true)
                .addCase(Tuple.of(Severity.ERROR, Severity.WARNING), true)
                .addCase(Tuple.of(Severity.FATAL_ERROR, Severity.ERROR), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLessThanOrEqual() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1.lessThanOrEqual(testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(Severity.INFO, Severity.INFO), true)
                .addCase(Tuple.of(Severity.INFO, Severity.WARNING), true)
                .addCase(Tuple.of(Severity.WARNING, Severity.INFO), false)
                .addCase(Tuple.of(Severity.ERROR, Severity.WARNING), false)
                .addCase(Tuple.of(Severity.FATAL_ERROR, Severity.ERROR), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLessThan() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1.lessThan(testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(Severity.INFO, Severity.INFO), false)
                .addCase(Tuple.of(Severity.INFO, Severity.WARNING), true)
                .addCase(Tuple.of(Severity.WARNING, Severity.INFO), false)
                .addCase(Tuple.of(Severity.ERROR, Severity.WARNING), false)
                .addCase(Tuple.of(Severity.FATAL_ERROR, Severity.ERROR), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetSeverity() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Severity.class)
                .withTestFunction(testCase ->
                        Severity.getSeverity(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", null)
                .addCase(" inFO ", Severity.INFO)
                .addCase("  WaRN", Severity.WARNING)
                .addCase(" eRRor ", Severity.ERROR)
                .addCase("   faTAL ", Severity.FATAL_ERROR)
                .addCase("FOO", null)
                .build();
    }

    @Test
    void testGetSeverity2() {
        for (final Severity severity : Severity.values()) {
            final String displayValue = severity.getDisplayValue();

            // getSeverity uses a hard coded if else approach, so make sure it covers all severities.
            final Severity severity2 = Severity.getSeverity(displayValue);
            Assertions.assertThat(severity2)
                    .isEqualTo(severity);
        }
    }

    @TestFactory
    Stream<DynamicTest> testAtLeast() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Severity.class)
                .withTestFunction(testCase -> {
                    final Severity severity = testCase.getInput()._1;
                    final Severity minSeverity = testCase.getInput()._2;
                    return severity
                            .atLeast(minSeverity);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(Severity.WARNING, null), Severity.WARNING)
                .addCase(Tuple.of(Severity.WARNING, Severity.WARNING), Severity.WARNING)
                .addCase(Tuple.of(Severity.WARNING, Severity.ERROR), Severity.ERROR)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetMaxSeverity() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Severity.class)
                .withTestFunction(testCase -> Severity.getMaxSeverity(testCase.getInput()._1, testCase.getInput()._2)
                        .orElse(null))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), null)
                .addCase(Tuple.of(null, Severity.ERROR), Severity.ERROR)
                .addCase(Tuple.of(Severity.ERROR, null), Severity.ERROR)
                .addCase(Tuple.of(Severity.ERROR, Severity.ERROR), Severity.ERROR)
                .addCase(Tuple.of(Severity.WARNING, Severity.ERROR), Severity.ERROR)
                .addCase(Tuple.of(Severity.ERROR, Severity.WARNING), Severity.ERROR)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetMaxSeverity2() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Severity.class, Severity.class)
                .withOutputType(Severity.class)
                .withTestFunction(testCase -> testCase.getInput()._1.getMaxSeverity(testCase.getInput()._2)
                        .orElse(null))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(Severity.ERROR, null), Severity.ERROR)
                .addCase(Tuple.of(Severity.ERROR, Severity.ERROR), Severity.ERROR)
                .addCase(Tuple.of(Severity.WARNING, Severity.ERROR), Severity.ERROR)
                .addCase(Tuple.of(Severity.ERROR, Severity.WARNING), Severity.ERROR)
                .build();
    }
}
