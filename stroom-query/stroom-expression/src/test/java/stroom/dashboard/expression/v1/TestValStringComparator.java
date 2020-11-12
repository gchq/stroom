package stroom.dashboard.expression.v1;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Disabled // See https://github.com/gchq/stroom/issues/1923
class TestValStringComparator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValStringComparator.class);

    @Test
    void testCompareAsDouble() {
        // The values in this test came from the keys in the ref data store
        // when the following error happened:
        // java.lang.IllegalArgumentException: Comparison method violates its general contract!
        //	at java.util.TimSort.mergeHi(TimSort.java:899)
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
                .sorted()
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
                .sorted()
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
        // This one will fail with the existing compareTo method on ValString that tries
        // to compare as a double first.
        doTestContract("10", "10-1", "2"); // "10" < "10-1", "10-2" < "2", 10 > 2
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
                                .sorted()
                                .collect(Collectors.toList());

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

        final int result_1_2 = val1.compareTo(val2);
        final int result_2_3 = val2.compareTo(val3);
        final int result_1_3 = val1.compareTo(val3);

        Assertions.assertThat(result_1_2).isLessThan(0);
        Assertions.assertThat(result_2_3).isLessThan(0);
        Assertions.assertThat(result_1_3).isLessThan(0);

        final int result_2_1 = val2.compareTo(val1);
        final int result_3_2 = val3.compareTo(val2);
        final int result_3_1 = val3.compareTo(val1);

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
}