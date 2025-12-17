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

package stroom.meta.api;

import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMap {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Test
    void testSimple() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("person", "person1");

        assertThat(attributeMap.get("person")).isEqualTo("person1");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person1");

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Collections.singletonList("person")));

        attributeMap.put("PERSON", "person2");

        assertThat(attributeMap.get("person")).isEqualTo("person2");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person2");

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Collections.singletonList("PERSON")));

        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put("persOn", "person3");
        attributeMap2.put("persOn1", "person4");

        attributeMap.putAll(attributeMap2);

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Arrays.asList("persOn", "persOn1")));
    }

    @Test
    void testRemove() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "a1");
        attributeMap.put("B", "b1");

        attributeMap.removeAll(Arrays.asList("A", "b"));

        assertThat(attributeMap.size()).isEqualTo(0);
    }

    @Test
    void testReadWrite() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);
        assertThat(attributeMap.get("a")).isEqualTo("1");
        assertThat(attributeMap.get("b")).isEqualTo("2");
        assertThat(attributeMap.get("z")).isNull();

        assertThat(new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET)).isEqualTo(
                "a:1\nb:2\nz\n");
    }

    @Test
    void testtoString() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);

        // AttributeMap's are used in log output and so check that they do output
        // the map values.
        assertThat(attributeMap.toString().contains("b=2")).as(attributeMap.toString()).isTrue();
        assertThat(attributeMap.toString().contains("a=1")).as(attributeMap.toString()).isTrue();
    }

    @Test
    void testTrim() {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put(" person ", "person1")
                .put("PERSON", "person2")
                .put("FOOBAR", "1")
                .put("F OOBAR", "2")
                .put(" foobar ", " 3 ")
                .build();

        assertThat(attributeMap.get("PERSON ")).isEqualTo("person2");
        assertThat(attributeMap.get("FOOBAR")).isEqualTo("3");
    }

    @Test
    void testWriteMultiLineValues() throws IOException {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put("foo", "123")
                .put("files", "/some/path/file1,/some/path/file2,/some/path/file3")
                .put("bar", "456")
                .build();
        final String str = new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET);

        assertThat(str)
                .isEqualTo("""
                        bar:456
                        files:/some/path/file1,/some/path/file2,/some/path/file3
                        foo:123
                        """);
    }

    @Test
    void testPutCollection() throws IOException {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put("foo", "123")
                .putCollection("files", List.of(
                        "/some/path/file1",
                        "/some/path/file2",
                        "/some/path/file3"))
                .put("bar", "456")
                .build();
        final String str = new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET);

        assertThat(str)
                .isEqualTo("""
                        bar:456
                        files:/some/path/file1,/some/path/file2,/some/path/file3
                        foo:123
                        """);
    }

    @Test
    void testGetAsCollection() throws IOException {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put("foo", "123")
                .putCollection("files", List.of(
                        "/some/path/file1",
                        "/some/path/file2",
                        "/some/path/file3"))
                .put("bar", "456")
                .build();

        assertThat(attributeMap.get("files"))
                .isEqualTo("/some/path/file1,/some/path/file2,/some/path/file3");
        assertThat(attributeMap.getAsList("files"))
                .containsExactly(
                        "/some/path/file1",
                        "/some/path/file2",
                        "/some/path/file3");
        assertThat(attributeMap.getAsList("foo"))
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
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
                "FOO", "123",
                "BAR", "456"));

        assertThat(attributeMap1)
                .isEqualTo(attributeMap2);
    }

    @Test
    void testEquality2() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "fooXXX", "123",
                "bar", "456"));
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
                "FOO", "123",
                "BAR", "456"));

        assertThat(attributeMap1)
                .isNotEqualTo(attributeMap2);
    }

    @Test
    void testEquality3() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "foo", "value1",
                "bar", "value2"));
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
                "foo", "VALUE1",
                "bar", "VALUE2"));

        // Value cases not same
        assertThat(attributeMap1)
                .isNotEqualTo(attributeMap2);
    }

    @TestFactory
    Stream<DynamicTest> testGet() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final AttributeMap attrMap = testCase.getInput()._1;
                    return attrMap.get(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(Tuple.of(attributeMapEmpty, null), NullPointerException.class)
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
    Stream<DynamicTest> testContainsKey() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AttributeMap attrMap = testCase.getInput()._1;
                    return attrMap.containsKey(testCase.getInput()._2);
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(Tuple.of(attributeMapEmpty, null), NullPointerException.class)
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
    Stream<DynamicTest> testContainsValue() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "foo", "value1",
                "bar", "value2"));
        attributeMap1.put("NULL", null);

        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(AttributeMap.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AttributeMap attrMap = testCase.getInput()._1;
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

        assertThat(attributeMap1)
                .isEmpty();

        attributeMap1.put("foo", "value1a");
        assertThat(attributeMap1)
                .hasSize(1);
        assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1a");

        attributeMap1.put("FOO", "value1b"); // 'same' key, new val
        assertThat(attributeMap1)
                .hasSize(1);
        assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1b");

        attributeMap1.put("bar", "value2a");
        assertThat(attributeMap1)
                .hasSize(2);
        assertThat(attributeMap1.get("BAR"))
                .isEqualTo("value2a");
    }

    @Test
    void testPut_withDateNormalisation() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";

        for (final String key : StandardHeaderArguments.DATE_HEADER_KEYS) {
            attributeMap1.clear();
            assertThat(attributeMap1)
                    .isEmpty();

            attributeMap1.put(key, dateStrIn);

            assertThat(attributeMap1)
                    .hasSize(1);

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
        final String key = "foo";

        assertThat(attributeMap1)
                .isEmpty();

        attributeMap1.putDateTime(key, epochMs);

        assertThat(attributeMap1)
                .hasSize(1);

        assertThat(attributeMap1.get(key))
                .isEqualTo(dateStrOut);
    }

    @Test
    void testPutDateTime2() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";
        final Instant instant = Instant.parse(dateStrIn);
        final String key = "foo";

        assertThat(attributeMap1)
                .isEmpty();

        attributeMap1.putDateTime(key, instant);

        assertThat(attributeMap1)
                .hasSize(1);

        assertThat(attributeMap1.get(key))
                .isEqualTo(dateStrOut);
    }

    @Test
    void testPutCurrentDateTime() {
        final AttributeMap attributeMap1 = new AttributeMap();
        final String dateStrIn = "2010-01-01T23:59:59.123456+00:00";
        final String dateStrOut = "2010-01-01T23:59:59.123Z";
        final String key = "foo";

        assertThat(attributeMap1)
                .isEmpty();

        final Instant now = Instant.now();
        attributeMap1.putCurrentDateTime(key);

        assertThat(attributeMap1)
                .hasSize(1);

        final String val = attributeMap1.get(key);
        assertThat(val)
                .isNotNull();
        final Instant instant = Instant.parse(val);
        assertThat(Duration.between(now, instant))
                .isLessThan(Duration.ofMillis(100));
    }

    @Test
    void testAppendDateTime_notPresent() {
        final String key = "foo";
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
        final String key = "foo";
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
                .isEqualTo(str1 + AttributeMap.VALUE_DELIMITER + str2);
    }

    @Test
    void testAppendDateTime_present_null() {
        final String key = "foo";
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
        final String key = "foo";
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
        final String key = "foo";
        final String item1 = "1";

        final AttributeMap attributeMap = new AttributeMap();

        final String val1 = attributeMap.appendItem(key, item1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);
    }

    @Test
    void testAppendItemIfDifferent_present() {
        final String key = "foo";
        final String item1 = "1";
        final String item2 = "2";

        final AttributeMap attributeMap = new AttributeMap();

        final String val1 = attributeMap.appendItemIfDifferent(key, item1);
        assertThat(val1)
                .isEqualTo(null);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1);

        final String val2 = attributeMap.appendItemIfDifferent(key, item2);

        assertThat(val2)
                .isEqualTo(item1);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1 + AttributeMap.VALUE_DELIMITER + item2);

        // Append same thing again
        final String val3 = attributeMap.appendItemIfDifferent(key, item2);
        assertThat(val3)
                .isEqualTo(item1 + AttributeMap.VALUE_DELIMITER + item2);
        assertThat(attributeMap.get(key))
                .isEqualTo(item1 + AttributeMap.VALUE_DELIMITER + item2);
    }

    @Test
    void testAppendItem_present_null() {
        final String key = "foo";
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
        final String key = "foo";
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
        final String key = "foo";
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
        final String key = "foo";
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
                .isEqualTo(item1 + AttributeMap.VALUE_DELIMITER + item2);
    }

    @Test
    void testComputeIfAbsent1() {

        final AttributeMap attributeMap1 = new AttributeMap();
        assertThat(attributeMap1)
                .isEmpty();
        final AtomicInteger callCount = new AtomicInteger();

        final String computedVal = attributeMap1.computeIfAbsent("foo", k -> {
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
        assertThat(attributeMap1)
                .hasSize(1);
        final AtomicInteger callCount = new AtomicInteger();

        final String computedVal = attributeMap1.computeIfAbsent("foo", k -> {
            callCount.incrementAndGet();
            return "value(" + k + ")";
        });

        assertThat(computedVal)
                .isEqualTo("value(initial)");
        assertThat(callCount)
                .hasValue(0);
    }

    @Test
    void testUUid() {
        final AttributeMap attributeMap = new AttributeMap();
        final String val1 = attributeMap.putRandomUuidIfAbsent("foo");
        assertThat(val1)
                .matches(UUID_PATTERN);
        assertThat(attributeMap.get("foo"))
                .isEqualTo(val1);
        final String val2 = attributeMap.putRandomUuidIfAbsent("foo");
        assertThat(val2)
                .isEqualTo(val1);
        assertThat(attributeMap.get("foo"))
                .isEqualTo(val1);
    }
}
