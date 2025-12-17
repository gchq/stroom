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

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractFunctionTest<T extends Function> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFunctionTest.class);

    @TestFactory
    Stream<DynamicTest> functionTests() {
        return getTestCases()
                .map(testCase ->
                        createDynamicTest(() -> getFunctionSupplier().get(), testCase));
    }

    private DynamicTest createDynamicTest(final Supplier<T> functionSupplier, final TestCase testCase) {
        final T function = functionSupplier.get();
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
                            final Param[] params = testCase.getParams().toArray(new Param[0]);
                            function.setParams(params);
                        }
                        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
                        function.addValueReferences(valueReferenceIndex);
                        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
                        final Generator generator = function.createGenerator();

                        if (!testCase.getAggregateValues().isEmpty()) {
                            LOGGER.info("Aggregate values: {}", testCase.getAggregateValues().stream()
                                    .map(this::argToString)
                                    .collect(Collectors.joining(" ")));
                            testCase.getAggregateValues().forEach(val ->
                                    generator.set(Val.of(val), storedValues));
                        }

                        // Run the function
                        final Val returnVal;

                        // Create a set of child values.
                        final List<StoredValues> childValues = new ArrayList<>();
                        testCase.getAggregateValues().forEach(val -> {
                            final StoredValues values = valueReferenceIndex.createStoredValues();
                            generator.set(Val.of(val), values);
                            childValues.add(values);
                        });
                        final Supplier<ChildData> childDataSupplier =
                                AbstractExpressionParserTest.createChildDataSupplier(childValues);

                        returnVal = generator.eval(storedValues, childDataSupplier);

                        LOGGER.info("Return val: {}", argToString(returnVal));

                        if (returnVal instanceof ValDouble
                                && testCase.getExpectedReturn() instanceof ValDouble) {
                            final double expected = testCase.getExpectedReturn().toDouble();
                            final double actual = returnVal.toDouble();
                            Assertions.assertThat(actual)
                                    .isCloseTo(expected, Offset.offset(0.0001));
                        } else if (returnVal == ValErr.INSTANCE) {
                            // No msg provided so just check it is a ValErr
                            Assertions.assertThat(returnVal)
                                    .isInstanceOf(ValErr.class);
                        } else if (returnVal instanceof ValErr) {
                            // Not ValErr.INSTANCE so check returned message contains expected msg
                            Assertions.assertThat(returnVal.toString())
                                    .containsIgnoringCase(testCase.getExpectedReturn().toString());
                        } else {
                            Assertions.assertThat(returnVal)
                                    .isEqualTo(testCase.getExpectedReturn());
                        }
                    } catch (final ParseException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    Supplier<T> getFunctionSupplier() {
        final Class<T> clazz = getFunctionType();
        return () -> {
            try {
                return clazz.getConstructor(ExpressionContext.class, String.class)
                        .newInstance(new ExpressionContext(), clazz.getSimpleName());
            } catch (final Exception e) {
                // Ignore
            }

            try {
                return clazz.getConstructor(String.class)
                        .newInstance(clazz.getSimpleName());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    abstract Class<T> getFunctionType();

    abstract Stream<TestCase> getTestCases();

    private String argToString(final Param param) {
        final String val = param instanceof ValString
                ? "'" + param + "'"
                : param.toString();
        return "[" + param.getClass().getSimpleName() +
                ": " + val + "]";
    }


    // --------------------------------------------------------------------------------


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

        public static TestCase convert(final String testVariantName,
                                       final Object expectedReturn,
                                       final Object param1) {
            return new TestCase(
                    testVariantName,
                    Val.create(expectedReturn),
                    Collections.singletonList(Val.create(param1)),
                    Collections.emptyList());
        }

        public static TestCase convert(final String testVariantName,
                                       final Object expectedReturn,
                                       final Object param1,
                                       final Object param2) {
            return new TestCase(
                    testVariantName,
                    Val.create(expectedReturn),
                    Arrays.asList(Val.create(param1), Val.create(param2)),
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
            final Ref ref = new Ref("field1", 0);
            final List<Param> params = Stream.concat(Stream.of(ref), Arrays.stream(extraParams))
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
