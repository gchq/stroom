/*
 * Copyright 2016 Crown Copyright
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestCompareUtil {

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
                        Assertions.assertThat(compareResult2)
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


    // --------------------------------------------------------------------------------


    private record Wrapper(String val) {

        private static Wrapper of(final String val) {
            return new Wrapper(val);
        }
    }

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
}
