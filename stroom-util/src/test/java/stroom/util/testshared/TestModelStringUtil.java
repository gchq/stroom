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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestModelStringUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestModelStringUtil.class);

    @TestFactory
    Stream<DynamicTest> testCsv() {
        return TestUtil.buildDynamicTestStream(Long.class, String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.formatCsv(testCase.getInput()))
                .addCase(null, "")
                .addCase(1L, "1")
                .addCase(123L, "123")
                .addCase(1234L, "1,234")
                .addCase(123123L, "123,123")
                .addCase(1123123L, "1,123,123")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCsv_double() {
        return TestUtil.buildDynamicTestStream(Tuple2.class, String.class)
                .withSimpleEqualityTest(testCase -> {
                    if (testCase.getInput()._1 == null) {
                        return ModelStringUtil.formatCsv(
                                null,
                                (int) testCase.getInput()._2);
                    } else {
                        return ModelStringUtil.formatCsv(
                                (double) testCase.getInput()._1,
                                (int) testCase.getInput()._2);
                    }
                })
                .addCase(Tuple.of(null, 1), "")
                .addCase(Tuple.of(1D,0), "1")
                .addCase(Tuple.of(1D,1), "1.0")
                .addCase(Tuple.of(1D,2), "1.00")
                .addCase(Tuple.of(1.23D,1), "1.2")
                .addCase(Tuple.of(1.29D,1), "1.3")
                .addCase(Tuple.of(1.23D,2), "1.23")
                .addCase(Tuple.of(1.23D,4), "1.2300")
                .addCase(Tuple.of(1234D,0), "1,234")
                .addCase(Tuple.of(1234D,1), "1,234.0")
                .addCase(Tuple.of(123123.123D,3), "123,123.123")
                .addCase(Tuple.of(1123123.999D,0), "1,123,124")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCsv_double_withStripTrailing() {
        return TestUtil.buildDynamicTestStream(Tuple2.class, String.class)
                .withSimpleEqualityTest(testCase -> {
                    if (testCase.getInput()._1 == null) {
                        return ModelStringUtil.formatCsv(
                                null,
                                (int) testCase.getInput()._2,
                                true);
                    } else {
                        return ModelStringUtil.formatCsv(
                                (double) testCase.getInput()._1,
                                (int) testCase.getInput()._2,
                                true);
                    }
                })
                .addCase(Tuple.of(null, 1), "")
                .addCase(Tuple.of(1D,0), "1")
                .addCase(Tuple.of(1D,1), "1")
                .addCase(Tuple.of(1D,2), "1")
                .addCase(Tuple.of(1.23D,1), "1.2")
                .addCase(Tuple.of(1.29D,1), "1.3")
                .addCase(Tuple.of(1.23D,2), "1.23")
                .addCase(Tuple.of(1.23D,4), "1.23")
                .addCase(Tuple.of(1234D,0), "1,234")
                .addCase(Tuple.of(1234D,1), "1,234")
                .addCase(Tuple.of(123123.123D,3), "123,123.123")
                .addCase(Tuple.of(1123123.999D,0), "1,123,124")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormatDurationString() {
        return TestUtil.buildDynamicTestStream(Long.class, String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.formatDurationString(testCase.getInput()))
                .addCase(null, "")
                .addCase(-10L, "-10.0ms")
                .addCase(0L, "0.0ms")
                .addCase(1L, "1.0ms")
                .addCase(999L, "999ms")
                .addCase(1000L, "1.0s")
                .addCase(2000L, "2.0s")
                .addCase(10000L, "10s")
                .addCase(60 * 1000L, "1.0m")
                .addCase(60 * 60 * 1000L, "1.0h")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDurationStringStrippingZeros() {
        return TestUtil.buildDynamicTestStream(Long.class, String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.formatDurationString(testCase.getInput(), true))
                .addCase(null, "")
                .addCase(-10L, "-10ms")
                .addCase(0L, "0ms")
                .addCase(1L, "1ms")
                .addCase(999L, "999ms")
                .addCase(1000L, "1s")
                .addCase(2000L, "2s")
                .addCase(10000L, "10s")
                .addCase(60 * 1000L, "1m")
                .addCase(60 * 60 * 1000L, "1h")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormatMetricByteSizeString() {
        return TestUtil.buildDynamicTestStream(Long.class, String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.formatMetricByteSizeString(testCase.getInput()))
                .addCase(1L, "1.0B")
                .addCase(999L, "999B")
                .addCase(1000L, "1.0K")
                .addCase(1096L, "1.1K")
                .addCase(1127L, "1.1K")
                .addCase(1946L, "1.9K")
                .addCase(1999L, "2.0K")
                .addCase(10240L, "10K")
                .build();

    }

    @TestFactory
    Stream<DynamicTest> testFormatIECByteSizeString() {
        return TestUtil.buildDynamicTestStream(Long.class, String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.formatIECByteSizeString(testCase.getInput()))
                .addCase(1L, "1.0B")
                .addCase(1L, "1.0B")
                .addCase(999L, "999B")
                .addCase(1_024L, "1.0K")
                .addCase(1_126L, "1.1K") // 1.099K
                .addCase(1_127L, "1.1K")
                .addCase(1_946L, "1.9K")
                .addCase(10_240L, "10K")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseString() {
        return TestUtil.buildDynamicTestStream(String.class, Long.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.parseNumberString(testCase.getInput()))
                .addCase("", null)
                .addCase(" ", null)
                .addCase("1", 1L)
                .addCase("1.0", 1L)
                .addCase("1k", 1000L)
                .addCase("1 k", 1000L)
                .addCase("1.1 k", 1100L)
                .addCase("1m", 1000000L)
                .addCase("1M", 1000000L)
                .addCase("199G", 199000000000L)
                .addCase("1000G", 1000000000000L)
                .addCase("1T", 1000000000000L)
                .addCase("1K", 1000L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseMetricByteSizeString() {
        return TestUtil.buildDynamicTestStream(String.class, Long.class)
                .withTest(testCase -> {
                    final Long output = ModelStringUtil.parseMetricByteSizeString(testCase.getInput());
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                    // Now reverse the conversion then reverse the output of that to ensure we
                    // keep getting the same numeric value. There are multiple string inputs for
                    // a single numeric value (e.g. 'xb', 'xbytes', etx.) so can't compare the two
                    // string values.
                    final String input2 = ModelStringUtil.formatMetricByteSizeString(output);
                    final Long output2 = ModelStringUtil.parseMetricByteSizeString(input2);
                    Assertions.assertThat(output2)
                            .isEqualTo(output);
                })
                .addCase("", null)
                .addCase(" ", null)
                .addCase("1", 1L)
                .addCase("1bytes", 1L)
                .addCase("1 bytes", 1L)
                .addCase("1kb", 1000L)
                .addCase("1 KB", 1000L)
                .addCase("10 KB", 10000L)
                .addCase("1 Mb", 1000000L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseIECByteSizeString() {
        return TestUtil.buildDynamicTestStream(String.class, Long.class)
                .withTest(testCase -> {
                    final Long output = ModelStringUtil.parseIECByteSizeString(testCase.getInput());
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                    // Now reverse the conversion then reverse the output of that to ensure we
                    // keep getting the same numeric value. There are multiple string inputs for
                    // a single numeric value (e.g. 'xb', 'xbytes', etx.) so can't compare the two
                    // string values.
                    final String input2 = ModelStringUtil.formatIECByteSizeString(output);
                    final Long output2 = ModelStringUtil.parseIECByteSizeString(input2);
                    Assertions.assertThat(output2)
                            .isEqualTo(output);
                })
                .addCase("", null)
                .addCase(" ", null)
                .addCase("1", 1L)
                .addCase("1bytes", 1L)
                .addCase("1 bytes", 1L)
                .addCase("1KiB", 1024L)
                .addCase("1 KiB", 1024L)
                .addCase("10 KiB", 10240L)
                .addCase("1 Mib", 1048576L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseDurationString() {
        return TestUtil.buildDynamicTestStream(String.class, Long.class)
                .withTest(testCase -> {
                    final Long output = ModelStringUtil.parseDurationString(testCase.getInput());
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                    // Now reverse the conversion then reverse the output of that to ensure we
                    // keep getting the same numeric value.
                    final String input2 = ModelStringUtil.formatDurationString(output);
                    final Long output2 = ModelStringUtil.parseDurationString(input2);
                    Assertions.assertThat(output2)
                            .isEqualTo(output);
                })
                .addCase("1", 1L)
                .addCase("9", 9L)
                .addCase("10", 10L)
                .addCase("1ms", 1L)
                .addCase("1 ms", 1L)
                .addCase("1s", Duration.ofSeconds(1).toMillis())
                .addCase("1 s", Duration.ofSeconds(1).toMillis())
                .addCase("1m", Duration.ofMinutes(1).toMillis())
                .addCase("1 m", Duration.ofMinutes(1).toMillis())
                .addCase("1h", Duration.ofHours(1).toMillis())
                .addCase("1 h", Duration.ofHours(1).toMillis())
                .addCase("1d", Duration.ofDays(1).toMillis())
                .addCase("1 d", Duration.ofDays(1).toMillis())
                .build();
    }

    @Test
    void testParseStringAsInt() {
        try {
            ModelStringUtil.parseNumberStringAsInt("1T");
            fail("Expecting exception");
        } catch (final NumberFormatException nfex) {
            // Ignore errors
        }
        assertThat(ModelStringUtil.parseNumberStringAsInt("2G").intValue()).isEqualTo(2000000000);
        assertThat(ModelStringUtil.formatCsv(ModelStringUtil.parseNumberStringAsInt("2G"))).isEqualTo("2,000,000,000");

    }

    @TestFactory
    Stream<DynamicTest> testParseStringQuotes() {
        return TestUtil.buildDynamicTestStream(String.class, Long.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.parseNumberString(testCase.getInput()))
                .addCase("''", null)
                .addCase("\"", null)
                .addCase("'1", 1L)
                .addCase("'1.0'", 1L)
                .addCase("\"1k\"", 1000L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToCamelCase() {
        return TestUtil.buildDynamicTestStream(String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.toCamelCase(testCase.getInput()))
                .addCase("XMLSchema", "xmlSchema")
                .addCase("Test", "test")
                .addCase("NewClassType", "newClassType")
                .addCase("temp", "temp")
                .build();

    }

    @TestFactory
    Stream<DynamicTest> testToDisplayValue() {
        return TestUtil.buildDynamicTestStream(String.class)
                .withSimpleEqualityTest(testCase ->
                        ModelStringUtil.toDisplayValue(testCase.getInput()))
                .addCase("XMLSchema", "XML Schema")
                .addCase("Test", "Test")
                .addCase("NewClassType", "New Class Type")
                .addCase("newClassType", "new Class Type")
                .addCase("temp", "temp")
                .build();
    }

    @Test
    void testSortPath() {
        final ArrayList<String> t1 = new ArrayList<>();
        t1.add("a");
        t1.add("zz");
        t1.add("z");
        t1.add("z/a");
        t1.add("z/b");
        t1.add("za");

        Collections.sort(t1, ModelStringUtil.pathComparator());

        final ArrayList<String> t2 = new ArrayList<>();
        t2.add("a");
        t2.add("z");
        t2.add("z/a");
        t2.add("z/b");
        t2.add("za");
        t2.add("zz");

        assertThat(t1).isEqualTo(t2);

    }

    @Test
    void testTimeSizeDividerNull() {
        doTest("", null);
    }

    @Test
    void testTimeSizeDivider1() {
        doTest("1", 1L);
    }

    @Test
    void testTimeSizeDivider1000() {
        doTest("1000", 1000L);
    }

    @Test
    void testTimeSizeDivider1Ms() {
        doTest("1MS", 1L);
    }

    @Test
    void testTimeSizeDivider1ms() {
        doTest("1 ms", 1L);
    }

    @Test
    void testTimeSizeDivider1s() {
        doTest("1 s", 1000L);
    }

    @Test
    void testTimeSizeDivider1m() {
        doTest("1 m", 60 * 1000L);
    }

    @Test
    void testTimeSizeDivider1h() {
        doTest("1 h", 60 * 60 * 1000L);
    }

    @Test
    void testTimeSizeDivider1d() {
        doTest("1 d", 24 * 60 * 60 * 1000L);
    }

    private Long doTest(String input, Long expected) {
        Long output = ModelStringUtil.parseDurationString(input);

        assertThat(output).isEqualTo(expected);

        System.out.println(input + " = " + output);

        return output;

    }
}
