package stroom.meta.api;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMap {

    @Test
    void testSimple() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("person", "person1");

        assertThat(attributeMap.get("person")).isEqualTo("person1");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person1");

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Collections.singletonList("person")));

        attributeMap.put("PERSON", "person2");

        assertThat(attributeMap.get("person")).isEqualTo("person2");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person2");

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Collections.singletonList("PERSON")));

        AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put("persOn", "person3");
        attributeMap2.put("persOn1", "person4");

        attributeMap.putAll(attributeMap2);

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Arrays.asList("persOn", "persOn1")));
    }

    @Test
    void testRemove() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "a1");
        attributeMap.put("B", "b1");

        attributeMap.removeAll(Arrays.asList("A", "b"));

        assertThat(attributeMap.size()).isEqualTo(0);
    }

    @Test
    void testReadWrite() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);
        assertThat(attributeMap.get("a")).isEqualTo("1");
        assertThat(attributeMap.get("b")).isEqualTo("2");
        assertThat(attributeMap.get("z")).isNull();

        assertThat(new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET)).isEqualTo(
                "a:1\nb:2\nz\n");
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
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(" person ", "person1");
        attributeMap.put("PERSON", "person2");
        attributeMap.put("FOOBAR", "1");
        attributeMap.put("F OOBAR", "2");
        attributeMap.put(" foobar ", " 3 ");

        assertThat(attributeMap.get("PERSON ")).isEqualTo("person2");
        assertThat(attributeMap.get("FOOBAR")).isEqualTo("3");
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

        Assertions.assertThat(attributeMap1)
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

        Assertions.assertThat(attributeMap1)
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
        Assertions.assertThat(attributeMap1)
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
                    var attrMap = testCase.getInput()._1;
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
                    var attrMap = testCase.getInput()._1;
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

        Assertions.assertThat(attributeMap1)
                .isEmpty();

        attributeMap1.put("foo", "value1a");
        Assertions.assertThat(attributeMap1)
                .hasSize(1);
        Assertions.assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1a");

        attributeMap1.put("FOO", "value1b"); // 'same' key, new val
        Assertions.assertThat(attributeMap1)
                .hasSize(1);
        Assertions.assertThat(attributeMap1.get("Foo"))
                .isEqualTo("value1b");

        attributeMap1.put("bar", "value2a");
        Assertions.assertThat(attributeMap1)
                .hasSize(2);
        Assertions.assertThat(attributeMap1.get("BAR"))
                .isEqualTo("value2a");
    }

    @Test
    void testComputeIfAbsent1() {

        final AttributeMap attributeMap1 = new AttributeMap();
        Assertions.assertThat(attributeMap1)
                .isEmpty();
        final AtomicInteger callCount = new AtomicInteger();

        final String computedVal = attributeMap1.computeIfAbsent("foo", k -> {
            callCount.incrementAndGet();
            return "value(" + k + ")";
        });

        Assertions.assertThat(computedVal)
                .isEqualTo("value(foo)");
        Assertions.assertThat(callCount)
                .hasValue(1);
    }

    @Test
    void testComputeIfAbsent2() {

        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.put("foo", "value(initial)");
        Assertions.assertThat(attributeMap1)
                .hasSize(1);
        final AtomicInteger callCount = new AtomicInteger();

        final String computedVal = attributeMap1.computeIfAbsent("foo", k -> {
            callCount.incrementAndGet();
            return "value(" + k + ")";
        });

        Assertions.assertThat(computedVal)
                .isEqualTo("value(initial)");
        Assertions.assertThat(callCount)
                .hasValue(0);
    }
}
