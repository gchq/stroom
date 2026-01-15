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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import com.google.inject.TypeLiteral;
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatCsv(testCase.getInput()))
                .withSimpleEqualityAssertion()
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
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Double, Integer>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    if (testCase.getInput()._1 == null) {
                        return ModelStringUtil.formatCsv(
                                null,
                                testCase.getInput()._2);
                    } else {
                        return ModelStringUtil.formatCsv(
                                testCase.getInput()._1,
                                testCase.getInput()._2);
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, 1), "")
                .addCase(Tuple.of(1D, 0), "1")
                .addCase(Tuple.of(1D, 1), "1.0")
                .addCase(Tuple.of(1D, 2), "1.00")
                .addCase(Tuple.of(1.23D, 1), "1.2")
                .addCase(Tuple.of(1.29D, 1), "1.3")
                .addCase(Tuple.of(1.23D, 2), "1.23")
                .addCase(Tuple.of(1.23D, 4), "1.2300")
                .addCase(Tuple.of(1234D, 0), "1,234")
                .addCase(Tuple.of(1234D, 1), "1,234.0")
                .addCase(Tuple.of(123123.123D, 3), "123,123.123")
                .addCase(Tuple.of(1123123.999D, 0), "1,123,124")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCsv_double_withStripTrailing() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Double, Integer>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    if (testCase.getInput()._1 == null) {
                        return ModelStringUtil.formatCsv(
                                null,
                                testCase.getInput()._2,
                                true);
                    } else {
                        return ModelStringUtil.formatCsv(
                                testCase.getInput()._1,
                                testCase.getInput()._2,
                                true);
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, 1), "")
                .addCase(Tuple.of(1D, 0), "1")
                .addCase(Tuple.of(1D, 1), "1")
                .addCase(Tuple.of(1D, 2), "1")
                .addCase(Tuple.of(1.23D, 1), "1.2")
                .addCase(Tuple.of(1.29D, 1), "1.3")
                .addCase(Tuple.of(1.23D, 2), "1.23")
                .addCase(Tuple.of(1.23D, 4), "1.23")
                .addCase(Tuple.of(1234D, 0), "1,234")
                .addCase(Tuple.of(1234D, 1), "1,234")
                .addCase(Tuple.of(123123.123D, 3), "123,123.123")
                .addCase(Tuple.of(1123123.999D, 0), "1,123,124")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormatDurationString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatDurationString(testCase.getInput()))
                .withSimpleEqualityAssertion()
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatDurationString(testCase.getInput(), true))
                .withSimpleEqualityAssertion()
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatMetricByteSizeString(testCase.getInput()))
                .withSimpleEqualityAssertion()
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatIECByteSizeString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(1L, "1.0B")
                .addCase(999L, "999B")
                .addCase(1_024L, "1.0K")
                .addCase(1_126L, "1.1K") // 1.099K
                .addCase(1_127L, "1.1K")
                .addCase(1_946L, "1.9K")
                .addCase(10_240L, "10K")
                .addCase(20_508_468_838L, "19G")
                .addCase(9_878_424_780L, "9.2G")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormatIECByteSizeString2() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatIECByteSizeString(testCase.getInput(), true))
                .withSimpleEqualityAssertion()
                .addCase(1L, "1B")
                .addCase(999L, "999B")
                .addCase(1_024L, "1K")
                .addCase(1_126L, "1.1K") // 1.099K
                .addCase(1_127L, "1.1K")
                .addCase(1_946L, "1.9K")
                .addCase(10_240L, "10K")
                .addCase(1024 * 1024L, "1M")
                .addCase(20_508_468_838L, "19G")
                .addCase(9_878_424_780L, "9.2G")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormatIECByteSizeString3() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.formatIECByteSizeString(
                                testCase.getInput(), false, 6))
                .withSimpleEqualityAssertion()
                .addCase(1L, "1.0B")
                .addCase(999L, "999.0B")
                .addCase(1_024L, "1.0K")
                .addCase(1_126L, "1.09961K")
                .addCase(1_127L, "1.10059K")
                .addCase(1_946L, "1.90039K")
                .addCase(10_240L, "10.0K")
                .addCase(1024 * 1024L, "1.0M")
                .addCase(20_508_468_838L, "19.1000G")
                .addCase(9_878_424_780L, "9.20000G")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseThenFormatIECByteSizeString() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(int.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final long sizeInBytes = ModelStringUtil.parseIECByteSizeString(testCase.getInput()._2);
                    final String output = ModelStringUtil.formatIECByteSizeString(
                            sizeInBytes, true, testCase.getInput()._1);
                    LOGGER.debug("sigFig: {}, input: {}, bytes: {}, output: {}",
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            ModelStringUtil.formatCsv(sizeInBytes),
                            output);
                    return output;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(3, "1B"), "1B")
                .addCase(Tuple.of(3, "1.0B"), "1B")
                .addCase(Tuple.of(3, "999B"), "999B")
                .addCase(Tuple.of(3, "1023B"), "1023B")
                .addCase(Tuple.of(3, "1024B"), "1K")
                .addCase(Tuple.of(3, "0.001K"), "1B")
                .addCase(Tuple.of(3, "0.01K"), "10B")
                .addCase(Tuple.of(3, "0.1K"), "102B")
                .addCase(Tuple.of(3, "1K"), "1K")
                .addCase(Tuple.of(3, "1.0K"), "1K")
                .addCase(Tuple.of(3, "1.1K"), "1.1K")
                .addCase(Tuple.of(3, "1.11K"), "1.11K")
                .addCase(Tuple.of(3, "1.111K"), "1.11K")
                .addCase(Tuple.of(3, "1.2K"), "1.2K")
                .addCase(Tuple.of(3, "1.3K"), "1.3K")
                .addCase(Tuple.of(3, "1.4K"), "1.4K")
                .addCase(Tuple.of(3, "1.5K"), "1.5K")
                .addCase(Tuple.of(3, "1.6K"), "1.6K")
                .addCase(Tuple.of(3, "1.9K"), "1.9K")
                .addCase(Tuple.of(3, "1.91K"), "1.91K")
                .addCase(Tuple.of(3, "1.912K"), "1.91K")
                .addCase(Tuple.of(3, "10.0K"), "10K")
                .addCase(Tuple.of(3, "19.1G"), "19.1G")
                .addCase(Tuple.of(3, "119.1G"), "119G")
                .addCase(Tuple.of(3, "1009.1G"), "1009G")
                .addCase(Tuple.of(3, "9.2G"), "9.2G")

                .addCase(Tuple.of(2, "1023B"), "1023B")
                .addCase(Tuple.of(1, "1023B"), "1023B")
                .addCase(Tuple.of(2, "1.22222K"), "1.2K")
                .addCase(Tuple.of(3, "1.22222K"), "1.22K")
                .addCase(Tuple.of(4, "1.22222K"), "1.222K")
                .addCase(Tuple.of(5, "1.22222K"), "1.2217K")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.parseNumberString(testCase.getInput()))
                .withSimpleEqualityAssertion()
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.parseMetricByteSizeString(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());
                    // Now reverse the conversion then reverse the output of that to ensure we
                    // keep getting the same numeric value. There are multiple string inputs for
                    // a single numeric value (e.g. 'xb', 'xbytes', etx.) so can't compare the two
                    // string values.
                    final String input2 = ModelStringUtil.formatMetricByteSizeString(testOutcome.getActualOutput());
                    final Long output2 = ModelStringUtil.parseMetricByteSizeString(input2);

                    Assertions.assertThat(output2)
                            .isEqualTo(testOutcome.getActualOutput());
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.parseIECByteSizeString(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());
                    // Now reverse the conversion then reverse the output of that to ensure we
                    // keep getting the same numeric value. There are multiple string inputs for
                    // a single numeric value (e.g. 'xb', 'xbytes', etx.) so can't compare the two
                    // string values.
                    final String input2 = ModelStringUtil.formatIECByteSizeString(testOutcome.getActualOutput());
                    final Long output2 = ModelStringUtil.parseIECByteSizeString(input2);
                    Assertions.assertThat(output2)
                            .isEqualTo(testOutcome.getActualOutput());
                })
                .addCase("", null)
                .addCase(" ", null)
                .addCase("1", 1L)
                .addCase("1b", 1L)
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.parseDurationString(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());
                    // Now reverse the conversion then reverse the output of that to ensure we
                    // keep getting the same numeric value.
                    final String input2 = ModelStringUtil.formatDurationString(testOutcome.getActualOutput());
                    final Long output2 = ModelStringUtil.parseDurationString(input2);
                    assertThat(output2)
                            .isEqualTo(testOutcome.getActualOutput());
                })
                .addCase(null, null)
                .addCase("", null)
                .addCase("   ", null)
                .addCase("1", 1L)
                .addCase("9", 9L)
                .addCase("10", 10L)
                .addCase("1000", 1_000L)
                .addCase("1ms", 1L)
                .addCase("1MS", 1L)
                .addCase("1 ms", 1L)
                .addCase("1s", Duration.ofSeconds(1).toMillis())
                .addCase("1S", Duration.ofSeconds(1).toMillis())
                .addCase("1 s", Duration.ofSeconds(1).toMillis())
                .addCase("1m", Duration.ofMinutes(1).toMillis())
                .addCase("1M", Duration.ofMinutes(1).toMillis())
                .addCase("1 m", Duration.ofMinutes(1).toMillis())
                .addCase("1h", Duration.ofHours(1).toMillis())
                .addCase("1H", Duration.ofHours(1).toMillis())
                .addCase("1 h", Duration.ofHours(1).toMillis())
                .addCase("1d", Duration.ofDays(1).toMillis())
                .addCase("1D", Duration.ofDays(1).toMillis())
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
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.parseNumberString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase("''", null)
                .addCase("\"", null)
                .addCase("'1", 1L)
                .addCase("'1.0'", 1L)
                .addCase("\"1k\"", 1000L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToCamelCase() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.toCamelCase(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase("XMLSchema", "xmlSchema")
                .addCase("Test", "test")
                .addCase("NewClassType", "newClassType")
                .addCase("temp", "temp")
                .build();

    }

    @TestFactory
    Stream<DynamicTest> testToDisplayValue() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        ModelStringUtil.toDisplayValue(testCase.getInput()))
                .withSimpleEqualityAssertion()
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

    @TestFactory
    Stream<DynamicTest> testFormatIECByteSizeStringWithSizeIndicator() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final long sizeInBytes = ModelStringUtil.parseIECByteSizeString(testCase.getInput());
                    final String output = ModelStringUtil.formatIECByteSizeStringWithSizeIndicator(sizeInBytes);
                    LOGGER.debug("input: {}, bytes: {}, output: {}",
                            testCase.getInput(),
                            ModelStringUtil.formatCsv(sizeInBytes),
                            output);
                    return output;
                })
                .withSimpleEqualityAssertion()
                .addCase("1B", "1.0B ▎")
                .addCase("1K", "1.0K ▌")
                .addCase("1M", "1.0M ▊")
                .addCase("1G", "1.0G █")
                .addCase("1T", "1.0T █")
                .build();
    }
}
