package stroom.query.language.functions;

import stroom.test.common.TestUtil;
import stroom.util.logging.LogUtil;

import io.vavr.Tuple;
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
import static stroom.query.language.functions.Val.create;

class TestValComparators {

    private Comparator<Val> comparator = ValComparators.GENERIC_COMPARATOR;

    @BeforeEach
    void setUp() {
        comparator = ValComparators.GENERIC_COMPARATOR;
    }

    @Test
    void testGetComparator() {

        final Comparator<Val> comparator = ValComparators.getComparator(Type.BOOLEAN, Type.STRING)
                .orElseThrow();

        assertThat(comparator.compare(create("true"), create(true)))
                .isEqualTo(0);
    }

    @Test
    void testGetComparator_null() {

        final Optional<Comparator<Val>> optComparator = ValComparators.getComparator(Type.ERR, Type.NULL);

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
        List<Val> sortedList = vals.stream()
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
                        Vals.of("1", 2, ValString.create(null)),
                        Vals.of(1, ValErr.create("a"), "b")
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

        comparator = ValComparators.AS_LONG_THEN_STRING_COMPARATOR;

        return Stream.of(
                        Vals.of("1", 2, "a"),
                        Vals.of(Duration.ofMillis(1), 2, "3"),
                        Vals.of(Instant.ofEpochMilli(1), 2, "3"),
                        Vals.of("1", 2, null),
                        Vals.of("1", 2, ValString.create(null))
               )
                .map(vals ->
                        DynamicTest.dynamicTest(
                                vals.toString(), () ->
                                        doTestContract(
                                                vals.val1,
                                                vals.val2,
                                                vals.val3)));
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


    // --------------------------------------------------------------------------------


    private record Vals(Val val1,
                        Val val2,
                        Val val3) {

//        static Vals of(Val val1,
//                       Val val2,
//                       Val val3) {
//            return new Vals(val1, val2, val3);
//        }

        static Vals of(Object obj1,
                       Object obj2,
                       Object obj3) {
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
