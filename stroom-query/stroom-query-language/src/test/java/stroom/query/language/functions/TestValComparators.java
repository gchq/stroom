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

package stroom.query.language.functions;

import stroom.test.common.TestUtil;
import stroom.util.logging.LogUtil;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestValComparators {

    private Comparator<Val> comparator;

    @BeforeEach
    void setUp() {
        comparator = ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR;
    }

    @Test
    void testGetComparator1() {

        final Comparator<Val> comparator = ValComparators.getComparatorForTypes(Type.BOOLEAN, Type.STRING, true)
                .orElseThrow();

        assertThat(comparator)
                .isEqualTo(ValComparators.AS_BOOLEAN_COMPARATOR);

        assertThat(comparator.compare(Val.create("true"), Val.create(true)))
                .isEqualTo(0);
    }

    @Test
    void testGetComparator2() {

        final Comparator<Val> comparator = ValComparators.getComparatorForTypes(Type.BOOLEAN, Type.STRING, false)
                .orElseThrow();

        assertThat(comparator)
                .isEqualTo(ValComparators.AS_BOOLEAN_COMPARATOR);

        assertThat(comparator.compare(Val.create("true"), Val.create(true)))
                .isEqualTo(0);
    }

    @Test
    void testGetComparator_null() {

        final Optional<Comparator<Val>> optComparator = ValComparators.getComparatorForTypes(
                Type.ERR, Type.NULL, true);

        assertThat(optComparator)
                .isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> testHasDecimalPart() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Val.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValComparators::hasFractionalPart)
                .withSimpleEqualityAssertion()
                .addCase(ValInteger.create(1), false)
                .addCase(ValLong.create(1L), false)
                .addCase(ValDouble.create(1D), false)
                .addCase(ValDouble.create(2.0D), false)
                .addCase(ValDouble.create(3.00D), false)
                .addCase(ValDouble.create((double) Long.MAX_VALUE), false)
                .addCase(ValFloat.create(1F), false)
                .addCase(ValFloat.create(4.0F), false)
                .addCase(ValDouble.create(1.1D), true)
                .addCase(ValFloat.create(1.1F), true)
                .addCase(ValDouble.create(1.00000000000001D), true)
                .addCase(ValFloat.create(2.0001F), true)
                .addCase(ValNull.INSTANCE, false)
                .addCase(ValString.create("foo"), false)
                .withNameFunction(testCase ->
                        testCase.getInput() + " (" + testCase.getInput().getClass().getSimpleName() + ")")
                .build();
    }

    @Test
    void testCompareAsDouble() {
        // The values in this test came from the keys in the ref data store
        // when the following error happened:
        // java.lang.IllegalArgumentException: Comparison method violates its general contract!
        // at java.util.TimSort.mergeHi(TimSort.java:899)
        //
        // The order of these values (pre-sorting) is important. The number of items in the list
        // appears to impact how TimSort approaches the sort, so reducing the items means
        // the sort may work with a compare method that does not meet the contract.

        final List<ValString> vals = new ArrayList<>(Arrays.asList(
                ValString.create("1"),
                ValString.create("2"),
                ValString.create("3"),
                ValString.create("4"),

                ValString.create("3232235777"),
                ValString.create("3232236805")
        ));

        IntStream.rangeClosed(5, 30)
                .boxed()
                .map(i -> ValString.create("staff" + i))
                .sorted(comparator)
                .forEach(vals::add);

        vals.addAll(Arrays.asList(
                ValString.create("3232238337-3232239362"),
                ValString.create("3232240641-3232241922"),
                ValString.create("3232243201-3232244482")
        ));

        // This one will fail with the existing compareTo method on ValString that tries
        // to compare as a double first.
        doTestContract(vals);

        // Make sure we can sort the full list without exception
        final List<Val> sortedList = vals.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    @TestFactory
    Stream<DynamicTest> testCompareContract_strings() {
        return Stream.of(
                        Tuple.of("0", "-", "a"),
                        Tuple.of("0", "1", "2"),
                        Tuple.of("1", "1.01", "1.1"),
                        Tuple.of("1", "10", "100"),
                        Tuple.of("1", "10", "aaaaaaaa"),
                        Tuple.of("a", "b", "c"),
                        Tuple.of("A", "B", "C"),
                        Tuple.of("A", "b", "C"),
                        Tuple.of("a", "B", "c"),
                        Tuple.of("2", "10", "10-1"),
                        Tuple.of("3", "20", "300"),
                        Tuple.of("3", "20", "foo"),
                        Tuple.of("4", "3232235777", "3232238337-3232239362"))
                .map(values ->
                        DynamicTest.dynamicTest(
                                values.toString(), () ->
                                        doTestContract(
                                                values._1,
                                                values._2,
                                                values._3)));
    }

    @TestFactory
    Stream<DynamicTest> testCompareContract_mixed() {

        return Stream.of(
                        Vals.of("1", 2, "a"),
                        Vals.of("1", 2D, "a"),
                        Vals.of("1", 2D, "2.1"),
                        Vals.of(1.1D, 2D, "2.1"),
                        Vals.of(Instant.ofEpochMilli(1_000), "2000", Duration.ofMillis(3_000)), // 1000, 2000, 3000
                        Vals.of(Instant.ofEpochMilli(20), "300.2", Duration.ofMillis(2_000)), // 20, 300.2, 2000
                        Vals.of(Duration.ofMillis(20), Duration.ofMillis(300), Duration.ofSeconds(2)), // 20, 300, 2000
                        Vals.of(-1, false, true), // -1, 0, 1
                        Vals.of("-1", false, true), // -1, 0, 1
                        Vals.of("1", 2, null),
                        Vals.of("3", 20d, "foo"),
                        Vals.of(1, "D", ValErr.create("a")) // "Err: a", hence comes last
                )
                .map(vals ->
                        DynamicTest.dynamicTest(
                                vals.toString(), () ->
                                        doTestContract(
                                                vals.val1,
                                                vals.val2,
                                                vals.val3)));
    }

    @TestFactory
    Stream<DynamicTest> testAsLongThenStringComparator() {

        comparator = ValComparators.AS_LONG_THEN_CASE_INSENSITIVE_STRING_COMPARATOR;

        return Stream.of(
                        Vals.of("1", 2, "a"),
                        Vals.of(Duration.ofMillis(1), 2, "3"),
                        Vals.of(Instant.ofEpochMilli(1), 2, "3"),
                        Vals.of("1", 2, null)
               )
                .map(vals ->
                        DynamicTest.dynamicTest(
                                vals.toString(), () ->
                                        doTestContract(
                                                vals.val1,
                                                vals.val2,
                                                vals.val3)));
    }

    @TestFactory
    Stream<DynamicTest> testAsDoubleThenStringComparator() {

        comparator = ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR;

        return Stream.of(
                        Vals.of(1L, 1.1D, "1.3"),
                        Vals.of("3", 20, "100"),
                        Vals.of("1", 2d, "a"),
                        Vals.of(Duration.ofMillis(1), 2d, "3"),
                        Vals.of(Instant.ofEpochMilli(1), 2d, "3"),
                        Vals.of("1", 2, null)
                )
                .map(vals ->
                        DynamicTest.dynamicTest(
                                vals.toString(), () ->
                                        doTestContract(
                                                vals.val1,
                                                vals.val2,
                                                vals.val3)));
    }

    @TestFactory
    Stream<DynamicTest> testGenericComparator() {
        comparator = ValComparators.GENERIC_CASE_SENSITIVE_COMPARATOR;
        final Instant now = Instant.now();
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Val.class, Val.class)
                .withOutputType(int.class)
                .withTestFunction(testCase ->
                        comparator.compare(testCase.getInput()._1, testCase.getInput()._2))
                .withAssertions(outcome -> {
                    final Integer actualOutput = outcome.getActualOutput();
                    final Integer expectedOutput = outcome.getExpectedOutput();
                    if (expectedOutput == 0) {
                        Assertions.assertThat(actualOutput)
                                .isZero();
                    } else if (expectedOutput > 0) {
                        Assertions.assertThat(actualOutput)
                                .isGreaterThan(0);
                    } else {
                        Assertions.assertThat(actualOutput)
                                .isLessThan(0);
                    }
                })
                .addCase(twoVals("a", null), -1)
                .addCase(twoVals("1", "a"), -1)
                .addCase(twoVals("a", "a"), 0)
                .addCase(twoVals("A", "a"), -1)
                .addCase(twoVals("2", "10"), -1)
                .addCase(twoVals(2, "10"), -1)
                .addCase(twoVals(2, "2"), 0)
                .addCase(twoVals(2L, 2), 0)
                .addCase(twoVals(2.2, "2.2"), 0)
                .addCase(twoVals(2.2F, 2.2D), 0)
                .addCase(twoVals(Duration.ofMinutes(60), Duration.ofDays(1)), -1)
                .addCase(twoVals(Duration.ofMinutes(60), "60m"), 0)
                .addCase(twoVals(Duration.ofMinutes(1), 60_000), 0)
                .addCase(twoVals(Duration.ofMinutes(1), 60_000L), 0)
                .addCase(twoVals(now, DateUtil.createNormalDateTimeString(now.toEpochMilli())), 0)
                .addCase(twoVals(true, true), 0)
                .addCase(twoVals(true, 1), 0)
                .addCase(twoVals(true, "true"), 0)
                .addCase(twoVals(true, "1"), 0)
                .addCase(twoVals(false, "0"), 0)
                .addCase(twoVals(false, 0), 0)
                .addCase(twoVals(false, "false"), 0)
                .addCase(twoVals(false, "foo"), 0)
                .addCase(twoVals(null, null), 0)
                .addCase(twoVals(ValErr.create("foo"), ValErr.create("foo")), 0)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCompareDoubleWithTolerance() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(double.class, double.class)
                .withOutputType(int.class)
                .withTestFunction(testCase ->
                        ValComparators.compareAsDoublesWithTolerance(
                                ValDouble.create(testCase.getInput()._1),
                                ValDouble.create(testCase.getInput()._2)))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(0d, 0d), 0)
                .addCase(Tuple.of(1d, 0d), 1)
                .addCase(Tuple.of((double) 0.000001f, 0d), 1)
                .addCase(Tuple.of(0d, 1d), -1)
                .addCase(Tuple.of(0d, (double) 0.000001f), -1)
                .addCase(Tuple.of(1d, 1d), 0)
                .addCase(Tuple.of(1.1d, (double) 1.1f), 0)
                .addCase(Tuple.of(1_000.1d, (double) 1_000.1f), 0)
                .addCase(Tuple.of(100_000.1d, (double) 100_000.1f), 0)
                .addCase(Tuple.of(10_000_000.1d, (double) 10_000_000.1f), 0)
                .build();
    }

    private void doTestContract(final List<ValString> vals) {
        // Test every combo of three different values
        // to ensure they satisfy the compare contract
        for (int i = 0; i < vals.size(); i++) {
            for (int j = 0; j < vals.size(); j++) {
                for (int k = 0; k < vals.size(); k++) {
                    if (j != i && k != j && k != i) {

//                        LOGGER.info("{} {} {}",
//                                vals.get(i),
//                                vals.get(j),
//                                vals.get(k));

                        // We want them in ascending order, this may fail
                        // if the contract is not met, depending on the approach sort takes
                        final List<ValString> threeVals = Stream.of(
                                        vals.get(i),
                                        vals.get(j),
                                        vals.get(k))
                                .sorted(comparator)
                                .toList();

                        // Now see if the trio meet the transitive contract
                        doTestContract(
                                threeVals.get(0),
                                threeVals.get(1),
                                threeVals.get(2));
                    }
                }
            }
        }
    }

    private void doTestContract(final Val val1,
                                final Val val2,
                                final Val val3) {

        // Verify the compareTo transitive contract, i.e
        // if x > y and y > z then x > z

        final int result_1_2 = compare(val1, val2);
        final int result_2_3 = compare(val2, val3);
        final int result_1_3 = compare(val1, val3);

        Assertions.assertThat(result_1_2)
                .describedAs(LogUtil.message("Comparing {} with {}", val1, val2))
                .isLessThan(0);
        Assertions.assertThat(result_2_3)
                .describedAs(LogUtil.message("Comparing {} with {}", val2, val3))
                .isLessThan(0);
        Assertions.assertThat(result_1_3)
                .describedAs(LogUtil.message("Comparing {} with {}", val1, val3))
                .isLessThan(0);

        final int result_2_1 = compare(val2, val1);
        final int result_3_2 = compare(val3, val2);
        final int result_3_1 = compare(val3, val1);

        Assertions.assertThat(result_2_1)
                .describedAs(LogUtil.message("Comparing {} with {}", val2, val1))
                .isGreaterThan(0);
        Assertions.assertThat(result_3_2)
                .describedAs(LogUtil.message("Comparing {} with {}", val3, val2))
                .isGreaterThan(0);
        Assertions.assertThat(result_3_1)
                .describedAs(LogUtil.message("Comparing {} with {}", val3, val1))
                .isGreaterThan(0);

        Assertions.assertThat(result_1_2).isEqualTo(-1 * result_2_1);
        Assertions.assertThat(result_2_3).isEqualTo(-1 * result_3_2);
        Assertions.assertThat(result_1_2).isEqualTo(-1 * result_2_1);
    }

    private void doTestContract(final String str1,
                                final String str2,
                                final String str3) {

        final ValString val1 = ValString.create(str1);
        final ValString val2 = ValString.create(str2);
        final ValString val3 = ValString.create(str3);

        doTestContract(val1, val2, val3);
    }

    private int compare(final Val v1, final Val v2) {
        return comparator.compare(v1, v2);
    }

    private Tuple2<Val, Val> twoVals(final Object val1, final Object val2) {
        return Tuple.of(Val.create(val1), Val.create(val2));
    }


    // --------------------------------------------------------------------------------


    private record Vals(Val val1,
                        Val val2,
                        Val val3) {

//        static Vals of(Val val1,
//                       Val val2,
//                       Val val3) {
//            return new Vals(val1, val2, val3);
//        }

        static Vals of(final Object obj1,
                       final Object obj2,
                       final Object obj3) {
            return new Vals(
                    Val.create(obj1),
                    Val.create(obj2),
                    Val.create(obj3));
        }

        @Override
        public String toString() {
            return LogUtil.message("Vals({}({}), {}({}), {}({})",
                    val1.getClass().getSimpleName(),
                    val1,
                    val2.getClass().getSimpleName(),
                    val2,
                    val3.getClass().getSimpleName(),
                    val3);
        }
    }
}
