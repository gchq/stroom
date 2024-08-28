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

package stroom.util.shared.string;

import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CompareUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.shared.string.CIKey.equalsIgnoreCase;
import static stroom.util.shared.string.CIKey.listOf;

public class TestCIKey {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCIKey.class);

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final CIKey str1 = CIKey.of(testCase.getInput()._1());
                    final CIKey str2 = CIKey.of(testCase.getInput()._2());
                    // Make sure the wrappers hold the original value
                    assertThat(str1.get())
                            .isEqualTo(testCase.getInput()._1());
                    assertThat(str2.get())
                            .isEqualTo(testCase.getInput()._2());

                    final boolean areEqual = Objects.equals(str1, str2);
                    final boolean haveEqualHashCode = Objects.equals(str1.hashCode(), str2.hashCode());

                    // If objects are equal, so should the hashes
                    assertThat(haveEqualHashCode)
                            .isEqualTo(areEqual);

                    return areEqual;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), true)
                .addCase(Tuple.of(null, "foo"), false)
                .addCase(Tuple.of("foo", null), false)
                .addCase(Tuple.of("foo", "bar"), false)
                .addCase(Tuple.of("foo", "foox"), false)
                .addCase(Tuple.of("foo", "foo"), true)
                .addCase(Tuple.of("foo", "FOO"), true)
                .addCase(Tuple.of("foo", "Foo"), true)
                .addCase(Tuple.of("foo123", "Foo123"), true)
                .addCase(Tuple.of("123", "123"), true)
                .addCase(Tuple.of("", ""), true)
                .build();
    }

    @Test
    void testWithMap() {

        final Map<CIKey, String> map = new HashMap<>();

        final Consumer<String> putter = str ->
                map.put(CIKey.of(str), str);

        putter.accept("foo"); // first key put to 'foo'
        putter.accept("fOo");
        putter.accept("FOO"); // Last value put to 'foo'
        putter.accept("bar");

        assertThat(map)
                .hasSize(2);
        assertThat(map)
                .containsKeys(
                        CIKey.of("foo"),
                        CIKey.of("bar"));

        assertThat(map.keySet().stream().map(CIKey::get).collect(Collectors.toSet()))
                .contains("foo", "bar");

        assertThat(map.values())
                .contains("FOO", "bar");

        assertThat(map.get(CIKey.of("foo")))
                .isEqualTo("FOO");
        assertThat(map.get(CIKey.of("FOO")))
                .isEqualTo("FOO");
    }

    @TestFactory
    Stream<DynamicTest> testEqualsIgnoreCase() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final CIKey ciKey = CIKey.of(testCase.getInput()._2);
                    boolean isEqual = equalsIgnoreCase(str, ciKey);
                    // Test the other overloaded equalsIgnoreCase methods too
                    assertThat(equalsIgnoreCase(ciKey, str))
                            .isEqualTo(isEqual);
                    assertThat(equalsIgnoreCase(str, ciKey.get()))
                            .isEqualTo(isEqual);
                    return isEqual;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), true)
                .addCase(Tuple.of("", ""), true)
                .addCase(Tuple.of("foo", "foo"), true)
                .addCase(Tuple.of("foo", "FOO"), true)
                .addCase(Tuple.of("FOO", "foo"), true)
                .addCase(Tuple.of("foo", null), false)
                .addCase(Tuple.of(null, "foo"), false)
                .addCase(Tuple.of("foo", "bar"), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsIgnoreCase() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final String subStr = testCase.getInput()._2;
                    final CIKey ciKey = CIKey.of(str);
                    return ciKey.containsIgnoreCase(subStr);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("", ""), true)
                .addCase(Tuple.of("foo", "f"), true)
                .addCase(Tuple.of("foo", "oo"), true)
                .addCase(Tuple.of("FOO", "f"), true)
                .addCase(Tuple.of("FOO", "oo"), true)
                .addCase(Tuple.of("foo", "F"), true)
                .addCase(Tuple.of("foo", "OO"), true)
                .addCase(Tuple.of("foo", "x"), false)
                .addCase(Tuple.of("FOO", "x"), false)
                .addCase(Tuple.of("foo", "X"), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsLowerCase() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final String subStr = testCase.getInput()._2;
                    final CIKey ciKey = CIKey.of(str);
                    return ciKey.containsLowerCase(subStr);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("", ""), true)
                .addCase(Tuple.of("foo", "f"), true)
                .addCase(Tuple.of("foo", "oo"), true)
                .addCase(Tuple.of("FOO", "f"), true)
                .addCase(Tuple.of("FOO", "oo"), true)
                .addCase(Tuple.of("foo", "x"), false)
                .addCase(Tuple.of("FOO", "x"), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIn() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, List<String>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final String key = testCase.getInput()._1;
                    final List<String> keys = testCase.getInput()._2;
                    return CIKey.of(key).in(keys);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("foo", List.of("foo", "bar")), true)
                .addCase(Tuple.of("bar", List.of("foo", "bar")), true)
                .addCase(Tuple.of("BAR", List.of("foo", "bar")), true)
                .addCase(Tuple.of("FOO", List.of("foo", "bar")), true)
                .addCase(Tuple.of("xxx", List.of("foo", "bar")), false)
                .addCase(Tuple.of("", List.of("foo", "bar")), false)
                .addCase(Tuple.of("", List.of("foo", "", "bar")), true)
                .addCase(Tuple.of(null, Arrays.asList("foo", "bar", null)), true)
                .build();
    }

    @Test
    void testListOf() {
        assertThat(listOf("a", "B", "c"))
                .extracting(CIKey::getAsLowerCase)
                .containsExactly("a", "b", "c");

        assertThat(listOf((String[]) null))
                .extracting(CIKey::getAsLowerCase)
                .isEmpty();

        assertThat(listOf())
                .extracting(CIKey::getAsLowerCase)
                .isEmpty();
    }

    @Test
    void testSorting() {

        final Map<CIKey, String> map = new HashMap<>();
        Stream.of("A", "aa", "b", "C", "d", null, "", "1", "0")
                .forEach(str -> map.put(CIKey.of(str), str));

        assertThat(map.keySet()
                .stream()
                .sorted(Comparator.nullsFirst(CIKey.COMPARATOR))
                .toList())
                .extracting(CIKey::get)
                .containsExactly(null, "", "0", "1", "A", "aa", "b", "C", "d");

        assertThat(map.keySet()
                .stream()
                .sorted()
                .toList())
                .extracting(CIKey::getAsLowerCase)
                .containsExactly(null, "", "0", "1", "a", "aa", "b", "c", "d");
    }

    @Test
    void testWithKnownKeys() {
        final Map<String, CIKey> knownCIKeys = Stream.of(
                        "Foo",
                        "Bar")
                .map(CIKey::of)
                .collect(Collectors.toMap(
                        CIKey::get,
                        Function.identity()));

        final CIKey knownCIKey = knownCIKeys.get("Foo");

        final CIKey ciKey = CIKey.of("Foo", knownCIKeys);
        assertThat(ciKey)
                .isSameAs(knownCIKey);

        // Different case so not known
        final CIKey ciKey2 = CIKey.of("foo", knownCIKeys);
        assertThat(ciKey2)
                .isNotSameAs(knownCIKey);
    }

    @Test
    void testWithKnownKeys2() {
        final Map<String, CIKey> knownCIKeys = Stream.of(
                        "Foo",
                        "Bar")
                .map(CIKey::of)
                .collect(Collectors.toMap(
                        CIKey::get,
                        Function.identity()));

        // Not in known keys, so uses one from built-in common keys
        final CIKey ciKey = CIKey.of(CIKeys.UUID.get(), knownCIKeys);
        assertThat(ciKey)
                .isSameAs(CIKeys.UUID);
    }

    @Test
    void testWithCommonKey() {
        // Not in known keys, so uses one from built-in common keys
        final CIKey ciKey = CIKey.of(CIKeys.UUID.get());
        assertThat(ciKey)
                .isSameAs(CIKeys.UUID);
    }

    @Test
    void testSerialisation() throws JsonProcessingException {
        final CIKey ciKey1 = CIKey.of("foo");
        final CIKey ciKey2 = CIKey.of("bar");
        String json = JsonUtil.getMapper()
                .writeValueAsString(ciKey1);
        LOGGER.info("json\n{}", json);

        assertThat(json)
                .isEqualTo("""
                        "foo\"""");

        final CIKey ciKey = JsonUtil.getMapper().readValue(json, CIKey.class);
        assertThat(ciKey)
                .isEqualTo(ciKey1);

        final Map<CIKey, String> map = Map.of(
                ciKey1, "A");

        json = JsonUtil.getMapper()
                .writeValueAsString(map);
        LOGGER.info("json\n{}", json);
        assertThat(json)
                .isEqualTo("""
                        {
                          "foo" : "A"
                        }""");

        final Map<CIKey, String> map2 = JsonUtil.getMapper().readValue(json, new TypeReference<>() {
        });
        assertThat(map2)
                .isEqualTo(map);
    }

    @Test
    void trimmed() {
        CIKey ciKey1 = CIKey.trimmed("  Foo   ");
        CIKey ciKey2 = CIKey.of("Foo");

        assertThat(ciKey1)
                .isEqualTo(ciKey2);
        assertThat(ciKey1)
                .isNotSameAs(ciKey2);
        assertThat(ciKey1.get())
                .isEqualTo("Foo");
        assertThat(ciKey1.getAsLowerCase())
                .isEqualTo("foo");
    }

    @Test
    void testOfLowerCase() {
        final String key = "foo";
        CIKey ciKey = CIKey.ofLowerCase(key);
        assertThat(ciKey.get())
                .isSameAs(key);
        assertThat(ciKey.getAsLowerCase())
                .isSameAs(key);
    }

    @Test
    void testOfDynamicKey() {
        final String key = "UUID";
        CIKey ciKey1 = CIKeys.UUID;
        CIKey ciKey2 = CIKey.of(key);
        CIKey ciKey3 = CIKey.ofDynamicKey(key);
        CIKey ciKey4 = CIKey.ofDynamicKey(key);

        assertThat(ciKey1)
                .isSameAs(ciKey2);
        assertThat(ciKey1)
                .isNotSameAs(ciKey3);
        assertThat(ciKey1)
                .isNotSameAs(ciKey4);

        assertThat(ciKey1)
                .isEqualTo(ciKey3);
        assertThat(ciKey1)
                .isEqualTo(ciKey4);
    }

    @TestFactory
    Stream<DynamicTest> testComparator() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(CIKey.class, CIKey.class)
                .withOutputType(int.class)
                .withTestFunction(testCase -> {
                    final int result = CompareUtil.normalise(CIKey.COMPARATOR.compare(
                            testCase.getInput()._1,
                            testCase.getInput()._2));

                    // Reverse it
                    final int result2 = CompareUtil.normalise(CIKey.COMPARATOR.compare(
                            testCase.getInput()._2,
                            testCase.getInput()._1));

                    if (result == 0) {
                        Assertions.assertThat(result2)
                                .isEqualTo(0);
                    } else {
                        Assertions.assertThat(result2)
                                .isEqualTo(-1 * result);
                    }
                    return result;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, CIKey.ofDynamicKey("a")), -1)
                .addCase(Tuple.of(CIKey.NULL_STRING, CIKey.EMPTY_STRING), -1)
                .addCase(Tuple.of(CIKey.NULL_STRING, CIKey.ofDynamicKey("a")), -1)
                .addCase(Tuple.of(CIKey.NULL_STRING, CIKey.EMPTY_STRING), -1)
                .addCase(Tuple.of(CIKey.ofDynamicKey("aaa"), CIKey.ofDynamicKey("bbb")), -1)
                .addCase(Tuple.of(CIKey.ofDynamicKey("aaa"), CIKey.ofDynamicKey("BBB")), -1)
                .addCase(Tuple.of(CIKey.ofDynamicKey("aaa"), CIKey.ofDynamicKey("AAA")), 0)
                .addCase(Tuple.of(CIKey.ofDynamicKey("a"), CIKey.ofDynamicKey("aaa")), -1)
                .build();
    }
}
