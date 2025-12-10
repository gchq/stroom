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

package stroom.util.io;

import stroom.test.common.TestUtil;
import stroom.util.logging.LogUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class TestByteSizeUnit {

    private static final List<ByteSizeUnit> METRIC_UNITS = List.of(
            ByteSizeUnit.KILOBYTE,
            ByteSizeUnit.MEGABYTE,
            ByteSizeUnit.GIGABYTE,
            ByteSizeUnit.TERABYTE,
            ByteSizeUnit.PETABYTE,
            ByteSizeUnit.EXABYTE);

    private static final List<ByteSizeUnit> IEC_UNITS = List.of(
            ByteSizeUnit.KIBIBYTE,
            ByteSizeUnit.MEBIBYTE,
            ByteSizeUnit.GIBIBYTE,
            ByteSizeUnit.TEBIBYTE,
            ByteSizeUnit.PEBIBYTE,
            ByteSizeUnit.EXBIBYTE);

    @TestFactory
    Stream<DynamicTest> testFromShortName() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(ByteSizeUnit.class)
                .withTestFunction(testCase -> ByteSizeUnit.fromShortName(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase("KiB", ByteSizeUnit.KIBIBYTE)
                .addCase("MiB", ByteSizeUnit.MEBIBYTE)
                .addCase("GiB", ByteSizeUnit.GIBIBYTE)
                .addCase("TiB", ByteSizeUnit.TEBIBYTE)
                .addCase("PiB", ByteSizeUnit.PEBIBYTE)
                .addCase("EiB", ByteSizeUnit.EXBIBYTE)
                .addCase("kB", ByteSizeUnit.KILOBYTE)
                .addCase("MB", ByteSizeUnit.MEGABYTE)
                .addCase("GB", ByteSizeUnit.GIGABYTE)
                .addCase("TB", ByteSizeUnit.TERABYTE)
                .addCase("PB", ByteSizeUnit.PETABYTE)
                .addCase("EB", ByteSizeUnit.EXABYTE)
                .addThrowsCase("foobar", IllegalArgumentException.class)
                .build();
    }

    @TestFactory
    Iterable<DynamicTest> testLongBytes_Iec() {
        final List<DynamicTest> tests = new ArrayList<>(IEC_UNITS.size());
        for (int i = 0; i < IEC_UNITS.size(); i++) {
            final int pow = i + 1;
            final ByteSizeUnit byteSizeUnit = IEC_UNITS.get(i);
            tests.add(DynamicTest.dynamicTest(
                    LogUtil.message("Unit: {}, pow: {}", byteSizeUnit, pow),
                    () -> {
                        final long bytes = byteSizeUnit.longBytes();
                        Assertions.assertThat(bytes)
                                .isEqualTo((long) Math.pow(1024, pow));
                    }
            ));
        }
        return tests;
    }

    @TestFactory
    Iterable<DynamicTest> testLongBytes_Metric() {
        final List<DynamicTest> tests = new ArrayList<>(METRIC_UNITS.size());
        for (int i = 0; i < METRIC_UNITS.size(); i++) {
            final int pow = i + 1;
            final ByteSizeUnit byteSizeUnit = METRIC_UNITS.get(i);
            tests.add(DynamicTest.dynamicTest(
                    LogUtil.message("Unit: {}, pow: {}", byteSizeUnit, pow),
                    () -> {
                        final long bytes = byteSizeUnit.longBytes();
                        Assertions.assertThat(bytes)
                                .isEqualTo((long) Math.pow(1000, pow));
                    }
            ));
        }
        return tests;
    }

    @TestFactory
    Stream<DynamicTest> testUnitValue() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(ByteSizeUnit.class, long.class)
                .withOutputType(double.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1.unitValue(testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(ByteSizeUnit.BYTE, 256L), 256d)
                .addCase(Tuple.of(ByteSizeUnit.KILOBYTE, 1024L), 1.024d)
                .addCase(Tuple.of(ByteSizeUnit.KIBIBYTE, 1024L), 1d)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLongValue2() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(ByteSizeUnit.class, long.class)
                .withOutputType(long.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1.longBytes(testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(ByteSizeUnit.BYTE, 256L), 256L)
                .addCase(Tuple.of(ByteSizeUnit.KILOBYTE, 1L), 1000L)
                .addCase(Tuple.of(ByteSizeUnit.KIBIBYTE, 1L), 1024L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIntBytes() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(int.class, ByteSizeUnit.class)
                .withOutputType(int.class)
                .withTestFunction(testCase -> {
                    final int value = testCase.getInput()._1;
                    final ByteSizeUnit byteSizeUnit = testCase.getInput()._2;
                    final int intResult = byteSizeUnit.intBytes(value);
                    final long longResult = byteSizeUnit.longBytes(value);
                    Assertions.assertThat(intResult)
                            .isEqualTo(longResult);
                    return intResult;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(1, ByteSizeUnit.BYTE), 1)
                .addCase(Tuple.of(1, ByteSizeUnit.KIBIBYTE), 1024)
                .addCase(Tuple.of(1, ByteSizeUnit.KILOBYTE), 1000)
                .addCase(Tuple.of(1, ByteSizeUnit.MEBIBYTE), 1024 * 1024)
                .addCase(Tuple.of(1, ByteSizeUnit.MEGABYTE), 1000 * 1000)
                .addCase(Tuple.of(1, ByteSizeUnit.GIBIBYTE), 1024 * 1024 * 1024)
                .addCase(Tuple.of(1, ByteSizeUnit.GIGABYTE), 1000 * 1000 * 1000)
                .addCase(Tuple.of(5, ByteSizeUnit.BYTE), 5)
                .addCase(Tuple.of(5, ByteSizeUnit.KIBIBYTE), 5 * 1024)
                .addCase(Tuple.of(5, ByteSizeUnit.KILOBYTE), 5 * 1000)
                .addCase(Tuple.of(5, ByteSizeUnit.MEBIBYTE), 5 * 1024 * 1024)
                .addCase(Tuple.of(5, ByteSizeUnit.MEGABYTE), 5 * 1000 * 1000)
                .build();
    }
}
