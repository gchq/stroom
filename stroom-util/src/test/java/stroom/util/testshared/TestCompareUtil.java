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

package stroom.util.testshared;


import stroom.test.common.TestUtil;
import stroom.util.shared.CompareUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestCompareUtil {

    private static final Person BOB = new Person("Bob", 42);
    private static final Person FRED = new Person("Fred", 37);
    private static final Person NADINE = new Person("Nadine", 37);
    private static final Person JANE = new Person("Jane", 57);

    @Test
    void testStringCompare() {
        assertThat(CompareUtil.compareString(null, null)).isEqualTo(0);
        assertThat(CompareUtil.compareString("A", "A")).isEqualTo(0);
        assertThat(CompareUtil.compareString("A", "a")).isEqualTo(0);
        assertThat(CompareUtil.compareString("A", "B")).isEqualTo(-1);
        assertThat(CompareUtil.compareString("B", "a")).isEqualTo(1);
        assertThat(CompareUtil.compareString("B", null)).isEqualTo(1);
        assertThat(CompareUtil.compareString(null, "B")).isEqualTo(-1);
    }

    @Test
    void testLongCompare() {
        assertThat(CompareUtil.compareLong(null, null)).isEqualTo(0);
        assertThat(CompareUtil.compareLong(1L, 1L)).isEqualTo(0);
        assertThat(CompareUtil.compareLong(1L, 2L)).isEqualTo(-1);
        assertThat(CompareUtil.compareLong(2L, 1L)).isEqualTo(1);
        assertThat(CompareUtil.compareLong(2L, null)).isEqualTo(1);
        assertThat(CompareUtil.compareLong(null, 2L)).isEqualTo(-1);
    }

    @TestFactory
    Stream<DynamicTest> getNullSafeCaseInsensitiveComparator() {

        final Comparator<Wrapper> comparator = CompareUtil.getNullSafeCaseInsensitiveComparator(
                Wrapper::val);

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Wrapper, Wrapper>>() {
                })
                .withOutputType(CompareResult.class)
                .withTestFunction(testCase -> {
                    final Wrapper wrapper1 = testCase.getInput()._1;
                    final Wrapper wrapper2 = testCase.getInput()._2;

                    final int compareResult = comparator.compare(wrapper1, wrapper2);
                    if (compareResult != 0) {
                        // Test the reverse
                        final int compareResult2 = comparator.compare(wrapper2, wrapper1);
                        assertThat(compareResult2)
                                .isEqualTo(compareResult * -1);
                    }
                    return CompareResult.of(compareResult);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), CompareResult.EQUAL)
                .addCase(Tuple.of(null, Wrapper.of(null)), CompareResult.LESS_THAN)
                .addCase(Tuple.of(null, Wrapper.of("aaa")), CompareResult.LESS_THAN)
                .addCase(Tuple.of(Wrapper.of(null), Wrapper.of("aaa")), CompareResult.LESS_THAN)
                .addCase(Tuple.of(Wrapper.of("aaa"), Wrapper.of("aaa")), CompareResult.EQUAL)
                .addCase(Tuple.of(Wrapper.of("AAA"), Wrapper.of("aaa")), CompareResult.EQUAL)
                .addCase(Tuple.of(Wrapper.of("aaa"), Wrapper.of("bbb")), CompareResult.LESS_THAN)
                .build();
    }

    @Test
    void testCombine1() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE);

        final Comparator<Person> comparator = CompareUtil.combine(null, Comparator.comparing(Person::name));
        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(BOB, FRED, JANE, NADINE);
    }

    @Test
    void testCombine2() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE);

        final Comparator<Person> comparator = CompareUtil.combine(Comparator.comparing(Person::name), null);
        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(BOB, FRED, JANE, NADINE);
    }

    @Test
    void testCombine3() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE);

        final Comparator<Person> comparator = CompareUtil.combine(
                Comparator.comparingInt(Person::age),
                Comparator.comparing(Person::name));

        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(FRED, NADINE, BOB, JANE);
    }

    @Test
    void testCombine4() {
        //noinspection ConstantValue
        final Comparator<Person> comparator = CompareUtil.combine(null, null);
        assertThat(comparator)
                .isNull();
    }

    @Test
    void testReverse() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE);
        final Comparator<Person> comparator = CompareUtil.reverseIf(Comparator.comparing(Person::name), false);

        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(BOB, FRED, JANE, NADINE);
    }

    @Test
    void testReverse2() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE);
        final Comparator<Person> comparator = CompareUtil.reverseIf(Comparator.comparing(Person::name), true);

        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(NADINE, JANE, FRED, BOB);
    }

    @Test
    void testReverse3() {
        //noinspection ConstantValue
        final Comparator<Person> comparator = CompareUtil.reverseIf(null, true);
        assertThat(comparator)
                .isNull();
    }

    @Test
    void testReverse4() {
        //noinspection ConstantValue
        final Comparator<Person> comparator = CompareUtil.reverseIf(null, false);
        assertThat(comparator)
                .isNull();
    }

    @Test
    void testNoOpComparator() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE, BOB, NADINE);
        final Comparator<Person> comparator = CompareUtil.noOpComparator();

        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(BOB, FRED, NADINE, JANE, BOB, NADINE);
    }

    @Test
    void testNonNull() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE, BOB, NADINE);
        final Comparator<Person> comparator = CompareUtil.nonNull(Comparator.comparing(Person::name));

        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(BOB, BOB, FRED, JANE, NADINE, NADINE);
    }

    @Test
    void testNonNull2() {
        final List<Person> people = List.of(BOB, FRED, NADINE, JANE, BOB, NADINE);
        final Comparator<Person> comparator = CompareUtil.nonNull(null);

        final List<Person> sorted = people.stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted)
                .containsExactly(BOB, FRED, NADINE, JANE, BOB, NADINE);
    }

    // --------------------------------------------------------------------------------


    private record Wrapper(String val) {

        private static Wrapper of(final String val) {
            return new Wrapper(val);
        }
    }


    // --------------------------------------------------------------------------------


    private enum CompareResult {
        LESS_THAN,
        EQUAL,
        MORE_THAN,
        ;

        private static CompareResult of(final int compareResult) {
            if (compareResult < 0) {
                return LESS_THAN;
            } else if (compareResult == 0) {
                return EQUAL;
            } else {
                return MORE_THAN;
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record Person(String name, int age) {

    }
}
