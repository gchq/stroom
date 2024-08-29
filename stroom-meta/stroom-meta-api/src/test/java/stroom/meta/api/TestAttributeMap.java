/*
 * Copyright 2024 Crown Copyright
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

package stroom.meta.api;

import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CIKeys;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMap {

    public static final CIKey FOO = CIKey.of("foo");
    public static final CIKey BAR = CIKey.of("bar");

    @Test
    void testSimple() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("person", "person1");

        assertThat(attributeMap.get("person")).isEqualTo("person1");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person1");

        assertThat(attributeMap.keySetAsStrings())
                .isEqualTo(new HashSet<>(Collections.singletonList("person")));

        attributeMap.put("PERSON", "person2");

        assertThat(attributeMap.get("person")).isEqualTo("person2");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person2");

        assertThat(attributeMap.keySetAsStrings())
                .isEqualTo(new HashSet<>(Collections.singletonList("person")));

        AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put("persOn", "person3");
        attributeMap2.put("persOn1", "person4");

        attributeMap.putAll(attributeMap2);

        assertThat(attributeMap.keySetAsStrings())
                .isEqualTo(new HashSet<>(Arrays.asList("person", "persOn1")));
    }

    @Test
    void testRemove() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "a1");
        attributeMap.put("B", "b1");

        attributeMap.remove(CIKey.of("A"));

        assertThat(attributeMap.size())
                .isEqualTo(1);

        attributeMap.remove(" b ");

        assertThat(attributeMap.size())
                .isEqualTo(0);
    }

    @Test
    void testRemoveAll() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "a1");
        attributeMap.put("B", "b1");
        attributeMap.put("c", "c1");

        attributeMap.removeAll(CIKey.listOf("A", "b"));

        assertThat(attributeMap.size())
                .isEqualTo(1);
        assertThat(attributeMap.keySet())
                .contains(CIKey.of("c"));
    }

    @Test
    void testReadWrite() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);
        assertThat(attributeMap.get("a")).isEqualTo("1");
        assertThat(attributeMap.get("b")).isEqualTo("2");
        assertThat(attributeMap.get("z")).isNull();

        assertThat(new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET))
                .isEqualTo("a:1\nb:2\nz\n");
    }

    @Test
    void testtoString() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);

        // AttributeMap's are used in log output and so check that they do output
        // the map values.
        assertThat(attributeMap.toString().contains("b=2")).as(attributeMap.toString()).isTrue();
        assertThat(attributeMap.toString().contains("a=1")).as(attributeMap.toString()).isTrue();
    }

    @Test
    void testTrim() {
        AttributeMap attributeMap = AttributeMap.builder()
                .put(" person ", "person1")
                .put("PERSON", "person2")
                .put("FOOBAR", "1")
                .put("F OOBAR", "2")
                .put(" foobar ", " 3 ")
                .build();

        assertThat(attributeMap.get("PERSON ")).isEqualTo("person2");
        assertThat(attributeMap.get("FOOBAR")).isEqualTo("3");
        attributeMap.get("sss");
    }

    @Test
    void testWriteMultiLineValues() throws IOException {
        AttributeMap attributeMap = AttributeMap.builder()
                .put(FOO, "123")
                .put(CIKeys.FILES, "/some/path/file1,/some/path/file2,/some/path/file3")
                .put(BAR, "456")
                .build();
        final String str = new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET);

        assertThat(str)
                .isEqualTo("""
                        bar:456
                        Files:/some/path/file1,/some/path/file2,/some/path/file3
                        foo:123
                        """);
    }

    @Test
    void testPutCollection() throws IOException {
        AttributeMap attributeMap = AttributeMap.builder()
                .put(FOO, "123")
                .putCollection(CIKeys.FILES, List.of(
                        "/some/path/file1",
                        "/some/path/file2",
                        "/some/path/file3"))
                .put(BAR, "456")
                .build();
        final String str = new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET);

        assertThat(str)
                .isEqualTo("""
                        bar:456
                        Files:/some/path/file1,/some/path/file2,/some/path/file3
                        foo:123
                        """);
    }

    @Test
    void testGetAsCollection() throws IOException {
        AttributeMap attributeMap = AttributeMap.builder()
                .put(FOO, "123")
                .putCollection(CIKeys.FILES, List.of(
                        "/some/path/file1",
                        "/some/path/file2",
                        "/some/path/file3"))
                .put(BAR, "456")
                .build();

        assertThat(attributeMap.get(CIKeys.FILES))
                .isEqualTo("/some/path/file1,/some/path/file2,/some/path/file3");
        assertThat(attributeMap.getValueAsList(CIKeys.FILES))
                .containsExactly(
                        "/some/path/file1",
                        "/some/path/file2",
                        "/some/path/file3");
        assertThat(attributeMap.getValueAsList(FOO))
                .containsExactly("123");
    }

    @Test
    void testZeroBytes() throws IOException {
        final byte[] bytes = "b:2\na:1\nz\n".getBytes(StandardCharsets.UTF_8);
        final ByteArrayInputStream is = new ByteArrayInputStream(bytes) {
            boolean firstRead = true;

            @Override
            public synchronized int read() {
                if (firstRead) {
                    firstRead = false;
                    return 0;
                }

                return super.read();
            }

            @Override
            public int read(final byte[] b) throws IOException {
                if (firstRead) {
                    firstRead = false;
                    return 0;
                }

                return super.read(b);
            }

            @Override
            public synchronized int read(final byte[] b, final int off, final int len) {
                if (firstRead) {
                    firstRead = false;
                    return 0;
                }

                return super.read(b, off, len);
            }
        };

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read(is, attributeMap);
    }

    @Test
    void testEquality1() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                FOO, "123",
                BAR, "456"));
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
                FOO, "123",
                BAR, "456"));

        assertThat(attributeMap1)
                .isEqualTo(attributeMap2);
    }

    @Test
    void testEquality2() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(CIKey.mapOf(
                "fooXXX", "123",
                "bar", "456"));
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(CIKey.mapOf(
                "foo", "123",
                "BAR", "456"));

        assertThat(attributeMap1)
                .isNotEqualTo(attributeMap2);
    }

    @Test
    void testEquality3() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                FOO, "value1",
                BAR, "value2"));
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
                FOO, "VALUE1",
                BAR, "VALUE2"));

        // Value cases not same
        assertThat(attributeMap1)
                .isNotEqualTo(attributeMap2);
    }

    @TestFactory
    Stream<DynamicTest> testGet() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                FOO, "123",
                BAR, "456"));
        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    var attrMap = testCase.getInput()._1;
                    return attrMap.get(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(Tuple.of(attributeMapEmpty, null), null)
                .addCase(Tuple.of(attributeMapEmpty, "foo"), null)
                .addCase(Tuple.of(attributeMap1, ""), null)
                .addCase(Tuple.of(attributeMap1, "foo"), "123")
                .addCase(Tuple.of(attributeMap1, "FOO"), "123")
                .addCase(Tuple.of(attributeMap1, "Foo"), "123")
                .addCase(Tuple.of(attributeMap1, " Foo"), "123")
                .addCase(Tuple.of(attributeMap1, "Foo "), "123")
                .addCase(Tuple.of(attributeMap1, " Foo "), "123")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGet2() {
        final AttributeMap attributeMap1 = AttributeMap.builder()
                .put(FOO, "123")
                .put(BAR, "456")
                .build();
        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, CIKey.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    var attrMap = testCase.getInput()._1;
                    return attrMap.get(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(Tuple.of(attributeMapEmpty, null), null)
                .addCase(Tuple.of(attributeMapEmpty, CIKey.of("foo")), null)
                .addCase(Tuple.of(attributeMap1, CIKey.of("")), null)
                .addCase(Tuple.of(attributeMap1, CIKey.of("foo")), "123")
                .addCase(Tuple.of(attributeMap1, CIKey.of("FOO")), "123")
                .addCase(Tuple.of(attributeMap1, CIKey.of("Foo")), "123")
                .addCase(Tuple.of(attributeMap1, CIKey.trimmed(" Foo")), "123")
                .addCase(Tuple.of(attributeMap1, CIKey.trimmed("Foo ")), "123")
                .addCase(Tuple.of(attributeMap1, CIKey.trimmed(" Foo ")), "123")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsKey() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(CIKey.mapOf(
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var attrMap = testCase.getInput()._1;
                    return attrMap.containsKey(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(attributeMapEmpty, null), false)
                .addCase(Tuple.of(attributeMapEmpty, "foo"), false)
                .addCase(Tuple.of(attributeMap1, ""), false)
                .addCase(Tuple.of(attributeMap1, "foo"), true)
                .addCase(Tuple.of(attributeMap1, "FOO"), true)
                .addCase(Tuple.of(attributeMap1, "Foo"), true)
                .addCase(Tuple.of(attributeMap1, " Foo"), true)
                .addCase(Tuple.of(attributeMap1, "Foo "), true)
                .addCase(Tuple.of(attributeMap1, " Foo "), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsKey2() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(CIKey.mapOf(
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, CIKey.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var attrMap = testCase.getInput()._1;
                    return attrMap.containsKey(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(attributeMapEmpty, null), false)
                .addCase(Tuple.of(attributeMapEmpty, CIKey.of("foo")), false)
                .addCase(Tuple.of(attributeMap1, CIKey.of("")), false)
                .addCase(Tuple.of(attributeMap1, CIKey.of("foo")), true)
                .addCase(Tuple.of(attributeMap1, CIKey.of("FOO")), true)
                .addCase(Tuple.of(attributeMap1, CIKey.of("Foo")), true)
                .addCase(Tuple.of(attributeMap1, CIKey.trimmed(" Foo")), true)
                .addCase(Tuple.of(attributeMap1, CIKey.trimmed("Foo ")), true)
                .addCase(Tuple.of(attributeMap1, CIKey.trimmed(" Foo ")), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsValue() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                FOO, "value1",
                BAR, "value2"));
        attributeMap1.put("NULL", null);

        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var attrMap = testCase.getInput()._1;
                    return attrMap.containsValue(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(attributeMapEmpty, "value1"), false)
                .addCase(Tuple.of(attributeMap1, null), true)
                .addCase(Tuple.of(attributeMap1, ""), false)
                .addCase(Tuple.of(attributeMap1, "value1"), true)
                .addCase(Tuple.of(attributeMap1, "value2"), true)
                .addCase(Tuple.of(attributeMap1, "VALUE1"), false) // wrong case
                .build();
    }

    @Test
    void testPut() {
        final AttributeMap attributeMap1 = new AttributeMap();

        assertThat(attributeMap1.isEmpty())
                .isTrue();

        attributeMap1.put("foo", "value1a");
        assertThat(attributeMap1.size())
                .isEqualTo(1);
        assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1a");
        assertThat(attributeMap1.get(CIKey.of("Foo")))
                .isEqualTo("value1a");

        attributeMap1.put("FOO", "value1b"); // 'same' key, new val
        assertThat(attributeMap1.size())
                .isEqualTo(1);
        assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1b");

        attributeMap1.put(CIKey.of("FoO"), "value1c"); // 'same' key, new val
        assertThat(attributeMap1.size())
                .isEqualTo(1);
        assertThat(attributeMap1.get(CIKey.of("fOO")))
                .isEqualTo("value1c");

        attributeMap1.put("  foo  ", "  value1d  "); // Same after trimming
        assertThat(attributeMap1.size())
                .isEqualTo(1);
        assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1d");
        assertThat(attributeMap1.get(CIKey.of("Foo")))
                .isEqualTo("value1d");

        attributeMap1.put("bar", "value2a");
        assertThat(attributeMap1.size())
                .isEqualTo(2);
        assertThat(attributeMap1.get("BAR"))
                .isEqualTo("value2a");
    }

    @Test
    void testPut_withDateNormalisation() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";

        for (final CIKey key : StandardHeaderArguments.DATE_HEADER_KEYS) {
            attributeMap1.clear();
            assertThat(attributeMap1.isEmpty())
                    .isTrue();

            attributeMap1.put(key, dateStrIn);

            assertThat(attributeMap1.size())
                    .isEqualTo(1);

            assertThat(attributeMap1.get(key))
                    .isEqualTo(dateStrOut);
        }
    }

    @Test
    void testPutDateTime1() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";
        final long epochMs = Instant.parse(dateStrIn).toEpochMilli();
        final CIKey key = FOO;

        assertThat(attributeMap1.isEmpty())
                .isTrue();

        attributeMap1.putDateTime(key, epochMs);

        assertThat(attributeMap1.size())
                .isEqualTo(1);

        assertThat(attributeMap1.get(key))
                .isEqualTo(dateStrOut);
    }

    @Test
    void testPutDateTime2() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";
        final Instant instant = Instant.parse(dateStrIn);
        final CIKey key = FOO;

        assertThat(attributeMap1.isEmpty())
                .isTrue();

        attributeMap1.putDateTime(key, instant);

        assertThat(attributeMap1.size())
                .isEqualTo(1);

        assertThat(attributeMap1.get(key))
                .isEqualTo(dateStrOut);
    }

    @Test
    void testPutCurrentDateTime() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";
        final CIKey key = FOO;

        assertThat(attributeMap1.isEmpty())
                .isTrue();

        final Instant now = Instant.now();
        attributeMap1.putCurrentDateTime(key);

        assertThat(attributeMap1.size())
                .isEqualTo(1);

        final String val = attributeMap1.get(key);
        assertThat(val)
                .isNotNull();
        final Instant instant = Instant.parse(val);
        assertThat(Duration.between(now, instant))
                .isLessThan(Duration.ofMillis(100));
    }

    @Test
    void testAppendDateTime_notPresent() {
        final CIKey key = FOO;
        final Instant instant1 = Instant.now();
        final String str1 = DateUtil.createNormalDateTimeString(instant1);

        final AttributeMap attributeMap = new AttributeMap();

        final String val1 = attributeMap.appendDateTime(key, instant1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(str1);
    }

    @Test
    void testAppendDateTime_present() {
        final CIKey key = FOO;
        final Instant instant1 = Instant.now().minus(1, ChronoUnit.DAYS);
        final String str1 = DateUtil.createNormalDateTimeString(instant1);
        final Instant instant2 = Instant.now();
        final String str2 = DateUtil.createNormalDateTimeString(instant2);

        final AttributeMap attributeMap = new AttributeMap();

        final String val1 = attributeMap.appendDateTime(key, instant1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(str1);

        final String val2 = attributeMap.appendDateTime(key, instant2);

        assertThat(val2)
                .isEqualTo(str1);
        assertThat(attributeMap.get(key))
                .isEqualTo(str1 + AttributeMapUtil.VALUE_DELIMITER + str2);
    }

    @Test
    void testAppendDateTime_present_null() {
        final CIKey key = FOO;
        final Instant instant1 = Instant.now();
        final String str1 = DateUtil.createNormalDateTimeString(instant1);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(key, null);

        final String val1 = attributeMap.appendDateTime(key, instant1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(str1);
    }

    @Test
    void testAppendDateTime_present_emptyString() {
        final CIKey key = FOO;
        final Instant instant1 = Instant.now();
        final String str1 = DateUtil.createNormalDateTimeString(instant1);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(key, "");

        final String val1 = attributeMap.appendDateTime(key, instant1);
        assertThat(val1)
                .isEqualTo("");
        assertThat(attributeMap.get(key))
                .isEqualTo(str1);
    }

    @Test
    void testAppendItem_notPresent() {
        final CIKey key = FOO;
        final String item1 = "1";

        final AttributeMap attributeMap = new AttributeMap();

        final String val1 = attributeMap.appendItem(key, item1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);
    }

    @Test
    void testAppendItem_present() {
        final CIKey key = FOO;
        final String item1 = "1";
        final String item2 = "2";

        final AttributeMap attributeMap = new AttributeMap();

        final String val1 = attributeMap.appendItem(key, item1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);

        final String val2 = attributeMap.appendItem(key, item2);

        assertThat(val2)
                .isEqualTo(item1);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1 + AttributeMapUtil.VALUE_DELIMITER + item2);
    }

    @Test
    void testAppendItem_present_null() {
        final CIKey key = FOO;
        final String item1 = "1";

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(key, null);

        final String val1 = attributeMap.appendItem(key, item1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);
    }

    @Test
    void testAppendItem_present_emptyString() {
        final CIKey key = FOO;
        final String item1 = "1";

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(key, "");

        final String val1 = attributeMap.appendItem(key, item1);
        assertThat(val1)
                .isEqualTo("");
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);
    }

    @Test
    void testAppendItemIf_notPresent() {
        final CIKey key = FOO;
        final String item1 = "1";

        final AttributeMap attributeMap = new AttributeMap();

        assertThat(attributeMap.get(key))
                .isEqualTo(null);

        final String val1 = attributeMap.appendItemIf(key, item1, val -> false);
        assertThat(attributeMap.get(key))
                .isEqualTo(null);
        assertThat(val1)
                .isEqualTo(null);

        final String val2 = attributeMap.appendItemIf(key, item1, val -> !NullSafe.contains(val, "1"));
        assertThat(attributeMap.get(key))
                .isEqualTo("1");
        assertThat(val2)
                .isEqualTo(null);

        assertThat(attributeMap.get(key))
                .isEqualTo("1");
    }

    @Test
    void testAppendItemIf_present() {
        final CIKey key = FOO;
        final String item1 = "1";
        final String item2 = "2";

        final AttributeMap attributeMap = new AttributeMap();

        attributeMap.put(key, item1);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);


        final String val1 = attributeMap.appendItemIf(key, item2, curVal -> !NullSafe.contains(curVal, "1"));
        assertThat(val1)
                .isEqualTo("1");
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);

        final String val2 = attributeMap.appendItemIf(key, item2, curVal -> NullSafe.contains(curVal, "1"));
        assertThat(val1)
                .isEqualTo("1");
        assertThat(attributeMap.get(key))
                .isEqualTo(item1 + AttributeMapUtil.VALUE_DELIMITER + item2);
    }

    @Test
    void testComputeIfAbsent1() {

        final AttributeMap attributeMap1 = new AttributeMap();
        assertThat(attributeMap1.isEmpty())
                .isTrue();
        final AtomicInteger callCount = new AtomicInteger();

        final String computedVal = attributeMap1.computeIfAbsent(FOO, k -> {
            callCount.incrementAndGet();
            return "value(" + k + ")";
        });

        assertThat(computedVal)
                .isEqualTo("value(foo)");
        assertThat(callCount)
                .hasValue(1);
    }

    @Test
    void testComputeIfAbsent2() {

        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.put("foo", "value(initial)");
        assertThat(attributeMap1.size())
                .isEqualTo(1);
        final AtomicInteger callCount = new AtomicInteger();

        final String computedVal = attributeMap1.computeIfAbsent(FOO, k -> {
            callCount.incrementAndGet();
            return "value(" + k + ")";
        });

        assertThat(computedVal)
                .isEqualTo("value(initial)");
        assertThat(callCount)
                .hasValue(0);
    }

    @Test
    void testGetAs() {
        AttributeMap attributeMap = new AttributeMap();
        long nowMs = Instant.now().toEpochMilli();
        attributeMap.putDateTime(FOO, nowMs);

        final Long nowMs2 = attributeMap.getAs(FOO, DateUtil::parseNormalDateTimeString);

        assertThat(nowMs2)
                .isEqualTo(nowMs);
    }

    @Test
    void testFilterIncluding() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "1");
        attributeMap.put("B", "1");
        attributeMap.put("c", "1");
        attributeMap.put("D", "1");
        attributeMap.put("e", "1");

        final AttributeMap map = attributeMap.filterIncluding(CIKey.setOf("b", "d"));

        assertThat(map.keySet())
                .extracting(CIKey::getAsLowerCase)
                .containsExactlyInAnyOrder("b", "d");
    }

    @Test
    void testFilterExcluding() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "1");
        attributeMap.put("B", "1");
        attributeMap.put("c", "1");
        attributeMap.put("D", "1");
        attributeMap.put("e", "1");

        final AttributeMap map = attributeMap.filterExcluding(CIKey.setOf("b", "d"));

        assertThat(map.keySet())
                .extracting(CIKey::getAsLowerCase)
                .containsExactlyInAnyOrder("a", "c", "e");
    }

    @Test
    void testCollector() {

        final AttributeMap attributeMap = Stream.of("a", "B", "c", "D", "e")
                .map(key -> Map.entry(CIKey.of(key), key))
                .collect(AttributeMap.collector());

        assertThat(attributeMap.keySet())
                .containsExactlyInAnyOrder(
                        CIKey.of("a"),
                        CIKey.of("B"),
                        CIKey.of("c"),
                        CIKey.of("D"),
                        CIKey.of("e"));

        assertThat(attributeMap.values())
                .containsExactlyInAnyOrder("a", "B", "c", "D", "e");
    }

    @Test
    void testKeySetAsString() {
        final AttributeMap attributeMap = Stream.of("a", "B", "c", "D", "e")
                .map(key -> Map.entry(CIKey.of(key), key))
                .collect(AttributeMap.collector());

        assertThat(attributeMap.keySetAsStrings())
                .containsExactlyInAnyOrder("a", "B", "c", "D", "e");
    }
}
