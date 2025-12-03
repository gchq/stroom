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

import com.google.common.math.DoubleMath;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;

abstract class AbstractEqualityFunctionTest<T extends AbstractEqualityFunction>
        extends AbstractFunctionTest<T> {

    static final Instant TODAY = Instant.now().truncatedTo(ChronoUnit.DAYS);
    static final Instant TOMORROW = Instant.now().truncatedTo(ChronoUnit.DAYS)
            .plus(1, ChronoUnit.DAYS);

    abstract Stream<Values> getTestCaseValues();

    abstract String getOperator();

    boolean addInverseTest() {
        return true;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return getTestCaseValues()
                .flatMap(values -> {
                    final Object param1 = values.param1;
                    final Object param2 = values.param2;
                    if (!addInverseTest() || param1 == null || param2 == null) {
                        return Stream.of(values);
                    } else if (ValComparators.haveType(param1, param2, String.class)
                               && String.CASE_INSENSITIVE_ORDER.compare(
                            param1.toString(),
                            param1.toString()) == 0) {
                        return Stream.of(values);
                    } else if (Objects.equals(param1, param2)) {
                        return Stream.of(values);
                    } else if ((param1 instanceof Number
                                && DoubleMath.fuzzyEquals(
                            ((Number) param1).doubleValue(),
                            ((Number) param2).doubleValue(),
                            Val.FLOATING_POINT_EQUALITY_TOLERANCE))) {
                        return Stream.of(values);
                    } else {
                        // Add a test for the inverse, e.g. for 2 > 1 => true, add 1 > 2 => false
                        return Stream.of(
                                values,
                                values.inverse());
                    }
                })
                .map(values -> buildCase(
                        values.param1(),
                        values.param2(),
                        values.expectedOutput()));
    }

    protected TestCase buildCase(final Object param1,
                                 final Object param2,
                                 final boolean expectedOutcome) {
        return TestCase.convert(
                param1
                + " (" + getParamType(param1) + ") "
                + getOperator() + " "
                + param2
                + " (" + getParamType(param2) + ")"
                + " => "
                + expectedOutcome,
                expectedOutcome,
                param1,
                param2);
    }

    private String getParamType(final Object param) {
        if (param == null) {
            return "null";
        }
        return param.getClass().getSimpleName();
    }


    // --------------------------------------------------------------------------------


    protected record Values(Object param1, Object param2, boolean expectedOutput) {

        static Values of(final Object param1, final Object param2, final boolean expectedOutput) {
            return new Values(param1, param2, expectedOutput);
        }

        Values inverse() {
            return new Values(param2, param1, !expectedOutput);
        }
    }
}
