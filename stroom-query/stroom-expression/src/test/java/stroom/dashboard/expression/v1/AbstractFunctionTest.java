package stroom.dashboard.expression.v1;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractFunctionTest<T extends Function> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFunctionTest.class);

    @TestFactory
    Stream<DynamicTest> functionTests() throws ParseException {

        final T function = getFunctionSupplier().get();

        return getTestCases()
                .map(testCase ->
                        createDynamicTest(function, testCase));
    }

    private DynamicTest createDynamicTest(final T function, final TestCase testCase) {

        return DynamicTest.dynamicTest(
                function.getClass().getSimpleName() + "(" + testCase.getTestVariantName() + ")",
                () -> {
                    try {
                        LOGGER.info("Function: {}, test variant: {}, args: {}, expecting: {}",
                                function.getClass().getSimpleName(),
                                testCase.getTestVariantName(),
                                testCase.getParams()
                                        .stream()
                                        .map(this::argToString)
                                        .collect(Collectors.joining(" ")),
                                argToString(testCase.getExpectedReturn()));

                        if (!testCase.getParams().isEmpty()) {
                            Param[] params = testCase.getParams()
                                    .toArray(new Param[testCase.getParams().size()]);
                            function.setParams(params);
                        }
                        final Generator generator = function.createGenerator();

                        if (!testCase.getAggregateValues().isEmpty()) {
                            LOGGER.info("Aggregate values: {}", testCase.getAggregateValues().stream()
                                    .map(this::argToString)
                                    .collect(Collectors.joining(" ")));
                            testCase.getAggregateValues().forEach(val ->
                                    generator.set(new Val[]{val}));
                        }

                        // Run the function
                        final Val returnVal;
                        if (generator instanceof Selector) {
                            returnVal = ((Selector) generator).select(createSelection(testCase.getAggregateValues()));
                        } else {
                            returnVal = generator.eval();
                        }

                        LOGGER.info("Return val: {}", argToString(returnVal));

                        if (returnVal instanceof ValDouble
                                && testCase.getExpectedReturn() instanceof ValDouble) {
                            double expected = testCase.getExpectedReturn().toDouble();
                            double actual = returnVal.toDouble();
                            Assertions.assertThat(actual)
                                    .isCloseTo(expected, Offset.offset(0.0001));
                        } else {
                            Assertions.assertThat(returnVal)
                                    .isEqualTo(testCase.getExpectedReturn());
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Selection<Val> createSelection(final List<Val> vals) {
        return new Selection<Val>() {
            @Override
            public int size() {
                return vals.size();
            }

            @Override
            public Val get(final int pos) {
                return vals.get(pos);
            }
        };
    }

    Supplier<T> getFunctionSupplier() {
        Class<T> clazz = getFunctionType();
        return () -> {
            try {
                return clazz.getConstructor(String.class)
                        .newInstance(clazz.getSimpleName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    abstract Class<T> getFunctionType();

    abstract Stream<TestCase> getTestCases();

    <T_SELECTION extends Val> Selection<T_SELECTION> buildSelection(final T_SELECTION... values) {
        return new Selection<T_SELECTION>() {
            @Override
            public int size() {
                return values.length;
            }

            @Override
            public T_SELECTION get(final int pos) {
                return values[pos];
            }
        };
    }

    private String argToString(final Param param) {
        final String val = param instanceof ValString
                ? "'" + param.toString() + "'"
                : param.toString();
        return "[" + param.getClass().getSimpleName() +
                ": " + val + "]";
    }

    static class TestCase {

        private final String testVariantName;
        private final Val expectedReturn;
        private final List<Param> params;
        private final List<Val> aggregateValues;

        TestCase(final String testVariantName,
                 final Val expectedReturn,
                 final List<Param> params,
                 final List<Val> aggregateValues) {
            this.testVariantName = testVariantName;
            this.expectedReturn = expectedReturn;
            this.params = params;
            this.aggregateValues = aggregateValues;
        }

        public static TestCase of(final String testVariantName,
                                  final Val expectedReturn,
                                  final List<Param> params) {
            return new TestCase(
                    testVariantName,
                    expectedReturn,
                    params,
                    Collections.emptyList());
        }

        public static TestCase of(final String testVariantName,
                                  final Val expectedReturn,
                                  final Param... params) {
            return new TestCase(
                    testVariantName,
                    expectedReturn,
                    Arrays.asList(params),
                    Collections.emptyList());
        }

        /**
         * Variant for where all args and return are ValString
         */
        public static TestCase of(final String testVariantName,
                                  final String expectedReturn,
                                  final String... params) {
            return new TestCase(
                    testVariantName,
                    ValString.create(expectedReturn),
                    Arrays.stream(params)
                            .map(ValString::create)
                            .collect(Collectors.toList()),
                    Collections.emptyList());
        }

        /**
         * Variant for where the return value is a ValString
         */
        public static TestCase of(final String testVariantName,
                                  final String expectedReturn,
                                  final Param... params) {
            return new TestCase(
                    testVariantName,
                    ValString.create(expectedReturn),
                    Arrays.asList(params),
                    Collections.emptyList());
        }

        public static TestCase ofAggregate(final String testVariantName,
                                           final Val expectedReturn,
                                           final List<Val> values,
                                           final Param... extraParams) {

//            FieldIndex fieldIndex = FieldIndex.forFields("field1");
            Ref ref = new Ref("field1", 0);
            List<Param> params = Stream.concat(Stream.of(ref), Arrays.stream(extraParams))
                    .collect(Collectors.toList());

            return new TestCase(
                    testVariantName,
                    expectedReturn,
                    params,
                    values);
        }

        public static TestCase ofAggregate(final String testVariantName,
                                           final Val expectedReturn,
                                           final Val... values) {
            return ofAggregate(testVariantName, expectedReturn, Arrays.asList(values));
        }

        public String getTestVariantName() {
            return testVariantName;
        }

        public Val getExpectedReturn() {
            return expectedReturn;
        }

        public List<Param> getParams() {
            return params;
        }

        public List<Val> getAggregateValues() {
            return aggregateValues;
        }
    }

}
