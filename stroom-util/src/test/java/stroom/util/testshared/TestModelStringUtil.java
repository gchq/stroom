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

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.shared.ModelStringUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestModelStringUtil {
    @Test
    public void testCsv() {
        Assert.assertEquals("", ModelStringUtil.formatCsv((Integer) null));
        Assert.assertEquals("1", ModelStringUtil.formatCsv(1L));
        Assert.assertEquals("123", ModelStringUtil.formatCsv(123L));
        Assert.assertEquals("1,234", ModelStringUtil.formatCsv(1234L));
        Assert.assertEquals("123,123", ModelStringUtil.formatCsv(123123L));
        Assert.assertEquals("1,123,123", ModelStringUtil.formatCsv(1123123L));
    }

    @Test
    public void testDurationString() {
        Assert.assertEquals("", ModelStringUtil.formatDurationString(null));
        Assert.assertEquals("-10.0 ms", ModelStringUtil.formatDurationString(-10L));
        Assert.assertEquals("0.0 ms", ModelStringUtil.formatDurationString(0L));
        Assert.assertEquals("1.0 ms", ModelStringUtil.formatDurationString(1L));
        Assert.assertEquals("999 ms", ModelStringUtil.formatDurationString(999L));
        Assert.assertEquals("1.0 s", ModelStringUtil.formatDurationString(1000L));
        Assert.assertEquals("2.0 s", ModelStringUtil.formatDurationString(2000L));
        Assert.assertEquals("10 s", ModelStringUtil.formatDurationString(10000L));
        Assert.assertEquals("1.0 m", ModelStringUtil.formatDurationString(60 * 1000L));
        Assert.assertEquals("1.0 h", ModelStringUtil.formatDurationString(60 * 60 * 1000L));
    }

    @Test
    public void testFormatMetricByteSizeString() {
        Assert.assertEquals("1.0 B", ModelStringUtil.formatMetricByteSizeString(1L));
        Assert.assertEquals("999 B", ModelStringUtil.formatMetricByteSizeString(999L));
        Assert.assertEquals("1.0 K", ModelStringUtil.formatMetricByteSizeString(1000L));
        Assert.assertEquals("1.0 K", ModelStringUtil.formatMetricByteSizeString(1096L));
        Assert.assertEquals("1.1 K", ModelStringUtil.formatMetricByteSizeString(1127L));
        Assert.assertEquals("1.9 K", ModelStringUtil.formatMetricByteSizeString(1946L));
        Assert.assertEquals("10 K", ModelStringUtil.formatMetricByteSizeString(10240L));
    }

    @Test
    public void testFormatIECByteSizeString() {
        Assert.assertEquals("1.0 B", ModelStringUtil.formatIECByteSizeString(1L));
        Assert.assertEquals("999 B", ModelStringUtil.formatIECByteSizeString(999L));
        Assert.assertEquals("1.0 K", ModelStringUtil.formatIECByteSizeString(1024L));
        Assert.assertEquals("1.0 K", ModelStringUtil.formatIECByteSizeString(1126L));
        Assert.assertEquals("1.1 K", ModelStringUtil.formatIECByteSizeString(1127L));
        Assert.assertEquals("1.9 K", ModelStringUtil.formatIECByteSizeString(1946L));
        Assert.assertEquals("10 K", ModelStringUtil.formatIECByteSizeString(10240L));
    }

    @Test
    public void testParseString() {
        Assert.assertNull(ModelStringUtil.parseNumberString(""));
        Assert.assertNull(ModelStringUtil.parseNumberString(" "));
        Assert.assertEquals(1, ModelStringUtil.parseNumberString("1").longValue());
        Assert.assertEquals(1, ModelStringUtil.parseNumberString("1.0").longValue());
        Assert.assertEquals(1000, ModelStringUtil.parseNumberString("1k").longValue());
        Assert.assertEquals(1000, ModelStringUtil.parseNumberString("1 k").longValue());
        Assert.assertEquals(1100, ModelStringUtil.parseNumberString("1.1 k").longValue());
        Assert.assertEquals(1000000, ModelStringUtil.parseNumberString("1m").longValue());
        Assert.assertEquals(1000000, ModelStringUtil.parseNumberString("1M").longValue());
        Assert.assertEquals(199000000000L, ModelStringUtil.parseNumberString("199G").longValue());
        Assert.assertEquals(1000000000000L, ModelStringUtil.parseNumberString("1000G").longValue());
        Assert.assertEquals(1000000000000L, ModelStringUtil.parseNumberString("1T").longValue());

        Assert.assertEquals(1000, ModelStringUtil.parseNumberString("1K").longValue());
    }

    @Test
    public void testParseMetricByteSizeString() {
        Assert.assertNull(ModelStringUtil.parseMetricByteSizeString(""));
        Assert.assertNull(ModelStringUtil.parseMetricByteSizeString(" "));
        Assert.assertEquals(1, ModelStringUtil.parseMetricByteSizeString("1").longValue());
        Assert.assertEquals(1, ModelStringUtil.parseMetricByteSizeString("1bytes").longValue());
        Assert.assertEquals(1, ModelStringUtil.parseMetricByteSizeString("1 bytes").longValue());
        Assert.assertEquals(1000, ModelStringUtil.parseMetricByteSizeString("1kb").longValue());
        Assert.assertEquals(1000, ModelStringUtil.parseMetricByteSizeString("1 KB").longValue());
        Assert.assertEquals(10000, ModelStringUtil.parseMetricByteSizeString("10 KB").longValue());
        Assert.assertEquals(1000000, ModelStringUtil.parseMetricByteSizeString("1 Mb").longValue());
    }

    @Test
    public void testParseIECByteSizeString() {
        Assert.assertNull(ModelStringUtil.parseIECByteSizeString(""));
        Assert.assertNull(ModelStringUtil.parseIECByteSizeString(" "));
        Assert.assertEquals(1, ModelStringUtil.parseIECByteSizeString("1").longValue());
        Assert.assertEquals(1, ModelStringUtil.parseIECByteSizeString("1bytes").longValue());
        Assert.assertEquals(1, ModelStringUtil.parseIECByteSizeString("1 bytes").longValue());
        Assert.assertEquals(1024, ModelStringUtil.parseIECByteSizeString("1KiB").longValue());
        Assert.assertEquals(1024, ModelStringUtil.parseIECByteSizeString("1 KiB").longValue());
        Assert.assertEquals(10240, ModelStringUtil.parseIECByteSizeString("10 KiB").longValue());
        Assert.assertEquals(1048576, ModelStringUtil.parseIECByteSizeString("1 Mib").longValue());
    }

    @Test
    public void testDurationString2() {
        Assert.assertEquals(1000 * 60 * 60 * 24, ModelStringUtil.parseDurationString("1d").longValue());
        Assert.assertEquals(1000 * 60 * 60 * 24, ModelStringUtil.parseDurationString("1 d").longValue());
        Assert.assertEquals(1000 * 60, ModelStringUtil.parseDurationString("1m").longValue());
    }

    @Test
    public void testParseStringAsInt() {
        try {
            ModelStringUtil.parseNumberStringAsInt("1T");
            Assert.fail("Expecting exception");
        } catch (final NumberFormatException nfex) {
        }
        Assert.assertEquals(2000000000, ModelStringUtil.parseNumberStringAsInt("2G").intValue());
        Assert.assertEquals("2,000,000,000", ModelStringUtil.formatCsv(ModelStringUtil.parseNumberStringAsInt("2G")));

    }

    @Test
    public void testParseStringAsDuration() {
        Assert.assertEquals(1L, ModelStringUtil.parseDurationString("1").longValue());
        Assert.assertEquals(10L, ModelStringUtil.parseDurationString("10").longValue());
        Assert.assertEquals(1000L, ModelStringUtil.parseDurationString("1s").longValue());
        Assert.assertEquals(1000L, ModelStringUtil.parseDurationString("1 s").longValue());
        Assert.assertEquals(1L, ModelStringUtil.parseDurationString("1ms").longValue());
        Assert.assertEquals(1L, ModelStringUtil.parseDurationString("1 ms").longValue());
        Assert.assertEquals(60L * 1000L, ModelStringUtil.parseDurationString("1 m").longValue());

    }

    @Test
    public void testParseStringQuotes() {
        Assert.assertNull(ModelStringUtil.parseNumberString("\'\'"));
        Assert.assertNull(ModelStringUtil.parseNumberString("\""));
        Assert.assertEquals(1, ModelStringUtil.parseNumberString("\'1").longValue());
        Assert.assertEquals(1, ModelStringUtil.parseNumberString("\'1.0\'").longValue());
        Assert.assertEquals(1000, ModelStringUtil.parseNumberString("\"1k\"").longValue());
    }

    @Test
    public void testToCamelCase() {
        Assert.assertEquals("xmlSchema", ModelStringUtil.toCamelCase("XMLSchema"));
        Assert.assertEquals("test", ModelStringUtil.toCamelCase("Test"));
        Assert.assertEquals("newClassType", ModelStringUtil.toCamelCase("NewClassType"));
        Assert.assertEquals("temp", ModelStringUtil.toCamelCase("temp"));
    }

    @Test
    public void testToDisplayValue() {
        Assert.assertEquals("XML Schema", ModelStringUtil.toDisplayValue("XMLSchema"));
        Assert.assertEquals("Test", ModelStringUtil.toDisplayValue("Test"));
        Assert.assertEquals("New Class Type", ModelStringUtil.toDisplayValue("NewClassType"));
        Assert.assertEquals("temp", ModelStringUtil.toDisplayValue("temp"));
    }

    @Test
    public void testSortPath() {
        final ArrayList<String> t1 = new ArrayList<String>();
        t1.add("a");
        t1.add("zz");
        t1.add("z");
        t1.add("z/a");
        t1.add("z/b");
        t1.add("za");

        Collections.sort(t1, ModelStringUtil.pathComparator());

        final ArrayList<String> t2 = new ArrayList<String>();
        t2.add("a");
        t2.add("z");
        t2.add("z/a");
        t2.add("z/b");
        t2.add("za");
        t2.add("zz");

        Assert.assertEquals(t2, t1);

    }

}
