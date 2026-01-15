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

package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestPatternUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPatternUtil.class);

    @TestFactory
    Stream<DynamicTest> createPatternFromWildCardFilter() {
        final List<String> months = Arrays.stream(Month.values())
                .map(month -> month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    // Case sensitive
                    final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(filter, true);

                    final List<String> results1 = months.stream()
                            .filter(pattern.asPredicate())
                            .toList();

                    final Predicate<String> predicate = PatternUtil.createPredicate(
                            List.of(filter),
                            Function.identity(),
                            true,
                            true,
                            true);

                    final List<String> results2 = months.stream()
                            .filter(predicate)
                            .toList();

                    Assertions.assertThat(results2)
                            .containsExactlyInAnyOrderElementsOf(results1);
                    return results1;
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase("", Collections.emptyList())
                .addCase("May", List.of("May"))
                .addCase("Jan*", List.of("January"))
                .addCase("Ma*", List.of("March", "May"))
                .addCase("*ry", List.of("January", "February"))
                .addCase("*e*emb*", List.of("September", "December"))
                .addCase("*a*", List.of("January", "February", "March", "May"))
                .addCase("a", Collections.emptyList())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createPatternFromWildCardFilter_caseInsensitive() {
        final List<String> months = Arrays.stream(Month.values())
                .map(month -> month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                            filter, true, false);

                    final List<String> results1 = months.stream()
                            .filter(pattern.asPredicate())
                            .toList();

                    final Predicate<String> predicate = PatternUtil.createPredicate(
                            List.of(filter),
                            Function.identity(),
                            true,
                            true,
                            false);

                    final List<String> results2 = months.stream()
                            .filter(predicate)
                            .toList();
                    Assertions.assertThat(results2)
                            .containsExactlyInAnyOrderElementsOf(results1);
                    return results1;
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase("", Collections.emptyList())
                .addCase("MAY", List.of("May"))
                .addCase("JAN*", List.of("January"))
                .addCase("ma*", List.of("March", "May"))
                .addCase("*Ry", List.of("January", "February"))
                .addCase("*e*emB*", List.of("September", "December"))
                .addCase("*a*", List.of("January", "February", "March", "April", "May", "August"))
                .addCase("a", Collections.emptyList())
                .addCase("m*", List.of("March", "May"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createPatternFromWildCardFilter_funkyChars() {

        final String val1 = "/some/path/file.txt";
        final String val2 = "http://some.domain:1234/some/path?x=1&y=2";
        final String val3 = "C:\\some\\path\\file.txt";
        final String val4 = "[abcd]{1}"; // meant to look like regex but is not

        final List<String> allValues = List.of(val1, val2, val3, val4);

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(filter, true);

                    LOGGER.info("filter: '{}', pattern: '{}'", filter, pattern);

                    return allValues.stream()
                            .filter(pattern.asMatchPredicate())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase("/*/*/file.txt", List.of(val1))
                .addCase("*\\*\\*\\file.txt", List.of(val3))
                .addCase("[abcd]{*}", List.of(val4))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createSqlLikeStringFromWildCardFilter() {

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    return PatternUtil.createSqlLikeStringFromWildCardFilter(filter);
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase("", "")
                .addCase("May", "May")
                .addCase("Jan*", "Jan%")
                .addCase("*ry", "%ry")
                .addCase("*e*emb*", "%e%emb%")
                .addCase("*a*", "%a%")
                .addCase("a", "a")
                .addCase("%", "\\%")
                .addCase("%_%_", "\\%\\_\\%\\_")
                .addCase("_", "\\_")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createPredicate() {
        // Wrap in an AtomicReference to test the toStringFunc arg of createPredicate
        final List<AtomicReference<String>> months = Arrays.stream(Month.values())
                .map(month -> month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .map(AtomicReference::new)
                .toList();

        return TestUtil.buildDynamicTestStream()
                .withInputType(Args.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final Args args = testCase.getInput();
                    final Predicate<AtomicReference<String>> predicate = PatternUtil.createPredicate(
                            args.filters,
                            AtomicReference::get,
                            args.allowWildCards,
                            args.isCompleteMatch,
                            args.isCaseSensitive);

                    return months.stream()
                            .filter(predicate)
                            .map(AtomicReference::get)
                            .toList();
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase(new Args(List.of(""), true, true, true),
                        Collections.emptyList())
                .addCase(new Args(List.of("May", "JUNE"), true, true, true),
                        List.of("May"))
                .addCase(new Args(List.of("May", "JUNE"), true, true, false),
                        List.of("May", "June"))
                .addCase(new Args(List.of("Ma", "JUN"), true, false, false),
                        List.of("March", "May", "June"))
                .addCase(new Args(List.of("Jan*"), true, true, true),
                        List.of("January"))
                .addCase(new Args(List.of("Ma*"), true, true, true),
                        List.of("March", "May"))
                .addCase(new Args(List.of("*ry"), true, true, true),
                        List.of("January", "February"))
                .addCase(new Args(List.of("*e*emb*"), true, true, true),
                        List.of("September", "December"))
                .addCase(new Args(List.of("*ay", "*e*emb*"), true, true, true),
                        List.of("May", "September", "December"))
                .addCase(new Args(List.of("*a*"), true, true, true),
                        List.of("January", "February", "March", "May"))
                .addCase(new Args(List.of("a"), true, true, true),
                        List.of())
                .build();
    }


    // --------------------------------------------------------------------------------


    private record Args(
            List<String> filters,
            boolean allowWildCards,
            boolean isCompleteMatch,
            boolean isCaseSensitive) {

    }
}
