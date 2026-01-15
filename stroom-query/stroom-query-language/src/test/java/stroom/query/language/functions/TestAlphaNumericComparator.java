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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class TestAlphaNumericComparator {
//    private final Comparator<Val> comparator = new AlphaNumericComparator();
    private final Comparator<Val> comparator = Comparator.comparing(
            Val::toString,
        Comparator.nullsLast(String::compareToIgnoreCase));

    @Test
    void testCompare() {
        final List<ValString> vals = new ArrayList<>(Arrays.asList(
                ValString.create("1"),
                ValString.create("2"),
                ValString.create("3"),
                ValString.create("3232235777"),
                ValString.create("3232236805"),
                ValString.create("4")
        ));

        vals.addAll(Arrays.asList(
                ValString.create("3232238337-3232239362"),
                ValString.create("3232240641-3232241922"),
                ValString.create("3232243201-3232244482")
        ));

        IntStream.rangeClosed(5, 30)
                .boxed()
                .map(i -> ValString.create("staff" + i))
                .sorted(comparator)
                .forEach(vals::add);



        // This one will fail with the existing compareTo method on ValString that tries
        // to compare as a double first.
        doTestContract(vals);

        // Make sure we can sort the full list without exception
        final List<Val> sortedList = vals.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    @Test
    void testCompareContract() {
        doTestContract("-", "0", "a");
        doTestContract("0", "1", "2");
        doTestContract("1", "1.01", "1.1");
        doTestContract("1", "10", "100");
        doTestContract("1", "10", "aaaaaaaa");
        doTestContract("a", "b", "c");
        doTestContract("A", "B", "C");
        doTestContract("A", "b", "C");
        doTestContract("10", "10-1", "2");
        doTestContract("3232235777", "3232238337-3232239362", "4");
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

    private void doTestContract(final ValString val1,
                                final ValString val2,
                                final ValString val3) {

        // Verify the compareTo transitive contract, i.e
        // if x > y and y > z then x > z

        final int result_1_2 = compare(val1, val2);
        final int result_2_3 = compare(val2, val3);
        final int result_1_3 = compare(val1, val3);

        Assertions.assertThat(result_1_2).isLessThan(0);
        Assertions.assertThat(result_2_3).isLessThan(0);
        Assertions.assertThat(result_1_3).isLessThan(0);

        final int result_2_1 = compare(val2, val1);
        final int result_3_2 = compare(val3, val2);
        final int result_3_1 = compare(val3, val1);

        Assertions.assertThat(result_2_1).isGreaterThan(0);
        Assertions.assertThat(result_3_2).isGreaterThan(0);
        Assertions.assertThat(result_3_1).isGreaterThan(0);

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

    private int compare(final ValString v1, final ValString v2) {
        return comparator.compare(v1, v2);
    }
}
