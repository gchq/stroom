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


import org.junit.jupiter.api.Test;
import stroom.util.shared.ModelStringUtil;

import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestModelStringUtil {
    @Test
    void testCsv() {
        assertThat(ModelStringUtil.formatCsv((Integer) null)).isEqualTo("");
        assertThat(ModelStringUtil.formatCsv(1L)).isEqualTo("1");
        assertThat(ModelStringUtil.formatCsv(123L)).isEqualTo("123");
        assertThat(ModelStringUtil.formatCsv(1234L)).isEqualTo("1,234");
        assertThat(ModelStringUtil.formatCsv(123123L)).isEqualTo("123,123");
        assertThat(ModelStringUtil.formatCsv(1123123L)).isEqualTo("1,123,123");
    }

    @Test
    void testDurationString() {
        assertThat(ModelStringUtil.formatDurationString(null)).isEqualTo("");
        assertThat(ModelStringUtil.formatDurationString(-10L)).isEqualTo("-10.0ms");
        assertThat(ModelStringUtil.formatDurationString(0L)).isEqualTo("0.0ms");
        assertThat(ModelStringUtil.formatDurationString(1L)).isEqualTo("1.0ms");
        assertThat(ModelStringUtil.formatDurationString(999L)).isEqualTo("999ms");
        assertThat(ModelStringUtil.formatDurationString(1000L)).isEqualTo("1.0s");
        assertThat(ModelStringUtil.formatDurationString(2000L)).isEqualTo("2.0s");
        assertThat(ModelStringUtil.formatDurationString(10000L)).isEqualTo("10s");
        assertThat(ModelStringUtil.formatDurationString(60 * 1000L)).isEqualTo("1.0m");
        assertThat(ModelStringUtil.formatDurationString(60 * 60 * 1000L)).isEqualTo("1.0h");
    }

    @Test
    void testDurationStringStrippingZeros() {
        assertThat(ModelStringUtil.formatDurationString(null, true)).isEqualTo("");
        assertThat(ModelStringUtil.formatDurationString(-10L, true)).isEqualTo("-10ms");
        assertThat(ModelStringUtil.formatDurationString(0L, true)).isEqualTo("0ms");
        assertThat(ModelStringUtil.formatDurationString(1L, true)).isEqualTo("1ms");
        assertThat(ModelStringUtil.formatDurationString(999L, true)).isEqualTo("999ms");
        assertThat(ModelStringUtil.formatDurationString(1000L, true)).isEqualTo("1s");
        assertThat(ModelStringUtil.formatDurationString(2000L, true)).isEqualTo("2s");
        assertThat(ModelStringUtil.formatDurationString(10000L, true)).isEqualTo("10s");
        assertThat(ModelStringUtil.formatDurationString(60 * 1000L, true)).isEqualTo("1m");
        assertThat(ModelStringUtil.formatDurationString(60 * 60 * 1000L, true)).isEqualTo("1h");
    }

    @Test
    void testFormatMetricByteSizeString() {
        assertThat(ModelStringUtil.formatMetricByteSizeString(1L)).isEqualTo("1.0B");
        assertThat(ModelStringUtil.formatMetricByteSizeString(999L)).isEqualTo("999B");
        assertThat(ModelStringUtil.formatMetricByteSizeString(1000L)).isEqualTo("1.0K");
        assertThat(ModelStringUtil.formatMetricByteSizeString(1096L)).isEqualTo("1.0K");
        assertThat(ModelStringUtil.formatMetricByteSizeString(1127L)).isEqualTo("1.1K");
        assertThat(ModelStringUtil.formatMetricByteSizeString(1946L)).isEqualTo("1.9K");
        assertThat(ModelStringUtil.formatMetricByteSizeString(10240L)).isEqualTo("10K");
    }

    @Test
    void testFormatIECByteSizeString() {
        assertThat(ModelStringUtil.formatIECByteSizeString(1L)).isEqualTo("1.0B");
        assertThat(ModelStringUtil.formatIECByteSizeString(999L)).isEqualTo("999B");
        assertThat(ModelStringUtil.formatIECByteSizeString(1024L)).isEqualTo("1.0K");
        assertThat(ModelStringUtil.formatIECByteSizeString(1126L)).isEqualTo("1.0K");
        assertThat(ModelStringUtil.formatIECByteSizeString(1127L)).isEqualTo("1.1K");
        assertThat(ModelStringUtil.formatIECByteSizeString(1946L)).isEqualTo("1.9K");
        assertThat(ModelStringUtil.formatIECByteSizeString(10240L)).isEqualTo("10K");
    }

    @Test
    void testParseString() {
        assertThat(ModelStringUtil.parseNumberString("")).isNull();
        assertThat(ModelStringUtil.parseNumberString(" ")).isNull();
        assertThat(ModelStringUtil.parseNumberString("1").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseNumberString("1.0").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseNumberString("1k").longValue()).isEqualTo(1000);
        assertThat(ModelStringUtil.parseNumberString("1 k").longValue()).isEqualTo(1000);
        assertThat(ModelStringUtil.parseNumberString("1.1 k").longValue()).isEqualTo(1100);
        assertThat(ModelStringUtil.parseNumberString("1m").longValue()).isEqualTo(1000000);
        assertThat(ModelStringUtil.parseNumberString("1M").longValue()).isEqualTo(1000000);
        assertThat(ModelStringUtil.parseNumberString("199G").longValue()).isEqualTo(199000000000L);
        assertThat(ModelStringUtil.parseNumberString("1000G").longValue()).isEqualTo(1000000000000L);
        assertThat(ModelStringUtil.parseNumberString("1T").longValue()).isEqualTo(1000000000000L);

        assertThat(ModelStringUtil.parseNumberString("1K").longValue()).isEqualTo(1000);
    }

    @Test
    void testParseMetricByteSizeString() {
        assertThat(ModelStringUtil.parseMetricByteSizeString("")).isNull();
        assertThat(ModelStringUtil.parseMetricByteSizeString(" ")).isNull();
        assertThat(ModelStringUtil.parseMetricByteSizeString("1").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseMetricByteSizeString("1bytes").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseMetricByteSizeString("1 bytes").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseMetricByteSizeString("1kb").longValue()).isEqualTo(1000);
        assertThat(ModelStringUtil.parseMetricByteSizeString("1 KB").longValue()).isEqualTo(1000);
        assertThat(ModelStringUtil.parseMetricByteSizeString("10 KB").longValue()).isEqualTo(10000);
        assertThat(ModelStringUtil.parseMetricByteSizeString("1 Mb").longValue()).isEqualTo(1000000);
    }

    @Test
    void testParseIECByteSizeString() {
        assertThat(ModelStringUtil.parseIECByteSizeString("")).isNull();
        assertThat(ModelStringUtil.parseIECByteSizeString(" ")).isNull();
        assertThat(ModelStringUtil.parseIECByteSizeString("1").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseIECByteSizeString("1bytes").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseIECByteSizeString("1 bytes").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseIECByteSizeString("1KiB").longValue()).isEqualTo(1024);
        assertThat(ModelStringUtil.parseIECByteSizeString("1 KiB").longValue()).isEqualTo(1024);
        assertThat(ModelStringUtil.parseIECByteSizeString("10 KiB").longValue()).isEqualTo(10240);
        assertThat(ModelStringUtil.parseIECByteSizeString("1 Mib").longValue()).isEqualTo(1048576);
    }

    @Test
    void testDurationString2() {
        assertThat(ModelStringUtil.parseDurationString("1d").longValue()).isEqualTo(1000 * 60 * 60 * 24);
        assertThat(ModelStringUtil.parseDurationString("1 d").longValue()).isEqualTo(1000 * 60 * 60 * 24);
        assertThat(ModelStringUtil.parseDurationString("1m").longValue()).isEqualTo(1000 * 60);
    }

    @Test
    void testParseStringAsInt() {
        try {
            ModelStringUtil.parseNumberStringAsInt("1T");
            fail("Expecting exception");
        } catch (final NumberFormatException nfex) {
        }
        assertThat(ModelStringUtil.parseNumberStringAsInt("2G").intValue()).isEqualTo(2000000000);
        assertThat(ModelStringUtil.formatCsv(ModelStringUtil.parseNumberStringAsInt("2G"))).isEqualTo("2,000,000,000");

    }

    @Test
    void testParseStringAsDuration() {
        assertThat(ModelStringUtil.parseDurationString("1").longValue()).isEqualTo(1L);
        assertThat(ModelStringUtil.parseDurationString("10").longValue()).isEqualTo(10L);
        assertThat(ModelStringUtil.parseDurationString("1s").longValue()).isEqualTo(1000L);
        assertThat(ModelStringUtil.parseDurationString("1 s").longValue()).isEqualTo(1000L);
        assertThat(ModelStringUtil.parseDurationString("1ms").longValue()).isEqualTo(1L);
        assertThat(ModelStringUtil.parseDurationString("1 ms").longValue()).isEqualTo(1L);
        assertThat(ModelStringUtil.parseDurationString("1 m").longValue()).isEqualTo(60L * 1000L);

    }

    @Test
    void testParseStringQuotes() {
        assertThat(ModelStringUtil.parseNumberString("\'\'")).isNull();
        assertThat(ModelStringUtil.parseNumberString("\"")).isNull();
        assertThat(ModelStringUtil.parseNumberString("\'1").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseNumberString("\'1.0\'").longValue()).isEqualTo(1);
        assertThat(ModelStringUtil.parseNumberString("\"1k\"").longValue()).isEqualTo(1000);
    }

    @Test
    void testToCamelCase() {
        assertThat(ModelStringUtil.toCamelCase("XMLSchema")).isEqualTo("xmlSchema");
        assertThat(ModelStringUtil.toCamelCase("Test")).isEqualTo("test");
        assertThat(ModelStringUtil.toCamelCase("NewClassType")).isEqualTo("newClassType");
        assertThat(ModelStringUtil.toCamelCase("temp")).isEqualTo("temp");
    }

    @Test
    void testToDisplayValue() {
        assertThat(ModelStringUtil.toDisplayValue("XMLSchema")).isEqualTo("XML Schema");
        assertThat(ModelStringUtil.toDisplayValue("Test")).isEqualTo("Test");
        assertThat(ModelStringUtil.toDisplayValue("NewClassType")).isEqualTo("New Class Type");
        assertThat(ModelStringUtil.toDisplayValue("temp")).isEqualTo("temp");
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
