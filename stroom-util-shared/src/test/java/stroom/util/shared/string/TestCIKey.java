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

package stroom.util.shared.string;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(TestCIKeys.CI_KEYS_RESOURCE_LOCK)
@Execution(ExecutionMode.SAME_THREAD) // clearCommonKeys breaks other tests
public class TestCIKey {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCIKey.class);

    @BeforeEach
    void setUp() {
        // As we are dealing with a static map, one test may impact another, so always
        // start with an empty map.  Call addCommonKey to preload the map as required.
        CIKeys.clearCommonKeys();
    }

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final CIKey str1 = CIKey.of(testCase.getInput()._1());
                    final CIKey str2 = CIKey.of(testCase.getInput()._2());
                    // Make sure the wrappers hold the original value
                    assertThat(NullSafe.get(str1, CIKey::get))
                            .isEqualTo(testCase.getInput()._1());
                    assertThat(NullSafe.get(str2, CIKey::get))
                            .isEqualTo(testCase.getInput()._2());

                    final boolean areEqual = Objects.equals(str1, str2);
                    final boolean haveEqualHashCode = Objects.equals(
                            NullSafe.get(str1, Object::hashCode),
                            NullSafe.get(str2, Object::hashCode));

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
    void testOf() {
        final String str = "MrFlibble";
        // Not a common key
        assertThat(CIKeys.getCommonKey(str))
                .isNull();

        final CIKey ciKey1 = CIKey.of(str);
        final CIKey ciKey2 = CIKey.of(str);

        // Not in common keys so two different instances
        assertThat(ciKey2)
                .isEqualTo(ciKey1)
                .isNotSameAs(ciKey1);

        final CIKey ciKey3 = CIKey.internStaticKey(str);
        assertThat(ciKey3)
                .isEqualTo(ciKey1)
                .isEqualTo(ciKey2)
                .isNotSameAs(ciKey1)
                .isNotSameAs(ciKey2);

        // Now in common keys, so same instance as ciKey3
        final CIKey ciKey4 = CIKey.of(str);
        assertThat(ciKey4)
                .isEqualTo(ciKey1)
                .isEqualTo(ciKey2)
                .isEqualTo(ciKey3)
                .isNotSameAs(ciKey1)
                .isNotSameAs(ciKey2)
                .isSameAs(ciKey3);

        // Different case, so it can't use the same instance as in common keys
        final CIKey ciKey5 = CIKey.of(str.toUpperCase());
        assertThat(ciKey5)
                .isEqualTo(ciKey1)
                .isEqualTo(ciKey2)
                .isEqualTo(ciKey3)
                .isEqualTo(ciKey4)
                .isNotSameAs(ciKey1)
                .isNotSameAs(ciKey2)
                .isNotSameAs(ciKey3)
                .isNotSameAs(ciKey4);
    }

    @Test
    void testOfIgnoringCase() {
        final String str = "MrFlibble";
        final CIKey ciKey1 = CIKey.internStaticKey(str);

        final CIKey ciKey2 = CIKey.of(str);
        assertThat(ciKey2)
                .isEqualTo(ciKey1)
                .isSameAs(ciKey1);

        final CIKey ciKey3 = CIKey.ofIgnoringCase(str);
        assertThat(ciKey3)
                .isEqualTo(ciKey1)
                .isSameAs(ciKey1);

        final CIKey ciKey4 = CIKey.of(str.toLowerCase());
        assertThat(ciKey4)
                .isEqualTo(ciKey1)
                .isNotSameAs(ciKey1);

        final CIKey ciKey5 = CIKey.ofIgnoringCase(str.toLowerCase());
        assertThat(ciKey5)
                .isEqualTo(ciKey1)
                .isSameAs(ciKey1);

        final CIKey ciKey6 = CIKey.ofIgnoringCase(str.toUpperCase());
        assertThat(ciKey6)
                .isEqualTo(ciKey1)
                .isSameAs(ciKey1);
    }

    @Test
    void testHashcode() {
        final CIKey ciKey1 = new CIKey("FOO", "foo");
        final CIKey ciKey2 = new CIKey("Foo", "foo");
        // Single arg ctor is private so get at it via json de-ser
        final CIKey ciKey3 = JsonUtil.readValue("""
                {
                    "key": "foO"
                }""", CIKey.class);

        final CIKey ciKey4 = new CIKey("BAR", "bar");

        assertThat(ciKey1.get())
                .isEqualTo("FOO");
        assertThat(ciKey1.getAsLowerCase())
                .isEqualTo("foo");

        assertThat(ciKey2.get())
                .isEqualTo("Foo");
        assertThat(ciKey2.getAsLowerCase())
                .isEqualTo("foo");
        assertThat(ciKey2)
                .isEqualTo(ciKey1);
        assertThat(ciKey2.hashCode())
                .isEqualTo(ciKey1.hashCode());

        assertThat(ciKey3.get())
                .isEqualTo("foO");
        assertThat(ciKey3.getAsLowerCase())
                .isEqualTo("foo");
        assertThat(ciKey3)
                .isEqualTo(ciKey1);
        assertThat(ciKey3.hashCode())
                .isEqualTo(ciKey1.hashCode());

        assertThat(ciKey4.get())
                .isEqualTo("BAR");
        assertThat(ciKey4.getAsLowerCase())
                .isEqualTo("bar");
        assertThat(ciKey4)
                .isNotEqualTo(ciKey1);
        assertThat(ciKey4.hashCode())
                .isNotEqualTo(ciKey1.hashCode());
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

    @Test
    void testWithSet() {
        final Set<CIKey> set = Stream.of(
                        "foo", "Foo", "FOO",
                        "bar", "Bar", "BAR",
                        "feed", "Feed", "FEED")
                .map(CIKey::ofDynamicKey)
                .collect(Collectors.toSet());
        assertThat(set)
                .hasSize(3);

        assertThat(set.contains(CIKey.ofDynamicKey("foo")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("Foo")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("FOO")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("bar")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("Bar")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("BAR")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("feed")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("Feed")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("FEED")))
                .isTrue();
        assertThat(set.contains(CIKey.ofDynamicKey("xxx")))
                .isFalse();
    }

    @Test
    void testEqualsHash() {
        final Map<String, CIKey> knownKeys = Map.of(
                "Feed", CIKey.ofDynamicKey("Feed"));

        // Feed
        CIKeys.addCommonKey(CIKeys.FEED);
        final CIKey feed1a = CIKeys.FEED;
        final CIKey feed1b = CIKey.of("Feed");
        final CIKey feed1c = CIKey.of("Feed", "feed");
        final CIKey feed1d = CIKey.ofDynamicKey("Feed");
        final CIKey feed1e = CIKey.ofIgnoringCase("Feed");
        final CIKey feed1f = CIKey.ofIgnoringCase("feed");
        final CIKey feed1g = CIKey.ofIgnoringCase("FEED");
        final CIKey feed1h = CIKey.of("Feed", knownKeys);

        // feed
        final CIKey feed2a = CIKey.of("feed");
        final CIKey feed2b = CIKey.ofLowerCase("feed");

        // FEED
        final CIKey feed3 = CIKey.of("FEED");

        final List<CIKey> feed1Keys = List.of(
                feed1a,
                feed1b,
                feed1c,
                feed1d,
                feed1e,
                feed1f,
                feed1g,
                feed1h);

        final List<CIKey> feed2Keys = List.of(
                feed2a,
                feed2b);
        final List<CIKey> feed3Keys = List.of(feed3);

        final List<CIKey> allKeys = Stream.of(feed1Keys, feed2Keys, feed3Keys)
                .flatMap(List::stream)
                .toList();

        dumpCiKeys(allKeys);
        LOGGER.debug("Hash of 'feed': {}, {}", "feed".hashCode(), Objects.hash("feed"));

        // All keys should be equal except for a case-sense match on get()
        allKeys.forEach(aCiKey -> {
            assertThat(feed1a)
                    .isEqualTo(aCiKey);
            assertThat(feed1a.getAsLowerCase())
                    .isEqualTo(aCiKey.getAsLowerCase());
            assertThat(feed1a.get())
                    .isEqualToIgnoringCase(aCiKey.get());
            assertThat(feed1a.hashCode())
                    .isEqualTo(aCiKey.hashCode());

            assertThat(feed1a)
                    .isNotEqualTo(CIKey.ofDynamicKey("Foo"));
            assertThat(feed1a.get())
                    .isNotEqualTo("Foo");
            assertThat(feed1a.hashCode())
                    .isNotEqualTo("Foo".hashCode());
        });

        // Each set of keys with the same case-sense 'key' should be equal on get()
        feed1Keys.forEach(aCiKey -> {
            assertThat(feed1a.get())
                    .isEqualTo(aCiKey.get());
        });
        feed2Keys.forEach(aCiKey -> {
            assertThat(feed2a.get())
                    .isEqualTo(aCiKey.get());
        });
        feed3Keys.forEach(aCiKey -> {
            assertThat(feed3.get())
                    .isEqualTo(aCiKey.get());
        });
    }

    @TestFactory
    Stream<DynamicTest> testEqualsIgnoreCase() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final CIKey ciKey = CIKey.of(testCase.getInput()._2);
                    final boolean isEqual = CIKey.equalsIgnoreCase(str, ciKey);
                    // Test the other overloaded equalsIgnoreCase methods too
                    assertThat(CIKey.equalsIgnoreCase(ciKey, str))
                            .isEqualTo(isEqual);
                    assertThat(CIKey.equalsIgnoreCase(str, NullSafe.get(ciKey, CIKey::get)))
                            .isEqualTo(isEqual);
                    return isEqual;
                })
                .withSimpleEqualityAssertion()
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
                .build();
    }

    @Test
    void testListOf() {
        assertThat(CIKey.listOf("a", "B", "c"))
                .extracting(CIKey::getAsLowerCase)
                .containsExactly("a", "b", "c");

        assertThat(CIKey.listOf((String[]) null))
                .extracting(CIKey::getAsLowerCase)
                .isEmpty();

        assertThat(CIKey.listOf())
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
                .extracting(ciKey -> NullSafe.get(ciKey, CIKey::get))
                .containsExactly(null, "", "0", "1", "A", "aa", "b", "C", "d");

        assertThat(map.keySet()
                .stream()
                .sorted(Comparator.nullsFirst(CIKey.COMPARATOR))
                .toList())
                .extracting(ciKey -> NullSafe.get(ciKey, CIKey::getAsLowerCase))
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
        CIKeys.addCommonKey(CIKeys.UUID);
        final CIKey ciKey = CIKey.of(CIKeys.UUID.get(), knownCIKeys);
        assertThat(ciKey)
                .isSameAs(CIKeys.UUID);
    }

    @Test
    void testWithCommonKey() {
        // Not in known keys, so uses one from built-in common keys
        CIKeys.addCommonKey(CIKeys.UUID);
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
                        {
                          "key" : "foo"
                        }""");

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
    void testSerialisation2() throws JsonProcessingException {
        final SerdeTestClass serdeTestClass = new SerdeTestClass(CIKey.of("foo"), "bar");

        final String json = JsonUtil.getMapper()
                .writeValueAsString(serdeTestClass);

        LOGGER.info("json\n{}", json);

        assertThat(json)
                .isEqualTo("""
                        {
                          "ciKey" : {
                            "key" : "foo"
                          },
                          "string" : "bar"
                        }""");

        final SerdeTestClass serdeTestClass2 = JsonUtil.getMapper()
                .readerFor(SerdeTestClass.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);

        assertThat(serdeTestClass2)
                .isEqualTo(serdeTestClass);
    }

    @Test
    void trimmed() {
        final CIKey ciKey1 = CIKey.trimmed("  Foo   ");
        final CIKey ciKey2 = CIKey.of("Foo");

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
        final CIKey ciKey = CIKey.ofLowerCase(key);
        assertThat(ciKey.get())
                .isSameAs(key);
        assertThat(ciKey.getAsLowerCase())
                .isSameAs(key);
    }

    @Test
    void testOfLowerCase_throws() {
        final String key = "foO";
        Assertions.assertThatThrownBy(
                        () -> {
                            final CIKey ciKey = CIKey.ofLowerCase(key);
                        })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownUpperCase() {
        final String key = "XXX";
        final CIKey ciKey1 = CIKey.of(key);
        final CIKey ciKey2 = CIKey.ofDynamicKey(key);
        Assertions.assertThatThrownBy(
                        () -> CIKey.of(key, key))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(
                        () -> CIKey.ofLowerCase(key))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(ciKey1)
                .isNotSameAs(ciKey2);
        assertThat(ciKey1)
                .isEqualTo(ciKey2);
        assertThat(ciKey1.get())
                .isEqualToIgnoringCase(ciKey2.get());
        assertThat(ciKey1.getAsLowerCase())
                .isEqualTo(ciKey2.getAsLowerCase());
        assertThat(ciKey1.hashCode())
                .isEqualTo(ciKey2.hashCode());

        // Make sure not a common key
        assertThat(CIKeys.commonKeys())
                .doesNotContain(ciKey1);
    }

    @Test
    void testUnknownLowerCase() {
        final String key = "xxx";
        final CIKey ciKey1 = CIKey.of(key);
        final CIKey ciKey2 = CIKey.ofDynamicKey(key);
        final CIKey ciKey3 = CIKey.ofLowerCase(key);
        final CIKey ciKey4 = CIKey.of(key, key);

        assertThat(ciKey1)
                .isNotSameAs(ciKey2)
                .isNotSameAs(ciKey3)
                .isNotSameAs(ciKey4);
        assertThat(ciKey1)
                .isEqualTo(ciKey2)
                .isEqualTo(ciKey3)
                .isEqualTo(ciKey4);
        assertThat(ciKey1.get())
                .isEqualToIgnoringCase(ciKey2.get())
                .isEqualToIgnoringCase(ciKey3.get())
                .isEqualToIgnoringCase(ciKey4.get());
        assertThat(ciKey1.getAsLowerCase())
                .isEqualTo(ciKey2.getAsLowerCase())
                .isEqualTo(ciKey3.getAsLowerCase())
                .isEqualTo(ciKey4.getAsLowerCase());
        assertThat(ciKey1.hashCode())
                .isEqualTo(ciKey2.hashCode())
                .isEqualTo(ciKey3.hashCode())
                .isEqualTo(ciKey4.hashCode());

        // Make sure not a common key
        assertThat(CIKeys.commonKeys())
                .doesNotContain(ciKey1);
    }

    @Test
    void testKnownLowerCase() {
        CIKeys.addCommonKey(CIKeys.ACCEPT);
        final String key = CIKeys.ACCEPT.get();

        final CIKey ciKey1 = CIKey.of(key);
        final CIKey ciKey2 = CIKey.ofDynamicKey(key);
        final CIKey ciKey3 = CIKey.ofLowerCase(key);
        final CIKey ciKey4 = CIKey.of(key, key);
        final CIKey ciKey5 = CIKeys.ACCEPT;

        assertThat(ciKey1)
                .isNotSameAs(ciKey2)  // Dynamic one
                .isSameAs(ciKey3)
                .isSameAs(ciKey4)
                .isSameAs(ciKey5);
        assertThat(ciKey1)
                .isEqualTo(ciKey2)
                .isEqualTo(ciKey3)
                .isEqualTo(ciKey4)
                .isEqualTo(ciKey5);
        assertThat(ciKey1.get())
                .isEqualToIgnoringCase(ciKey2.get())
                .isEqualToIgnoringCase(ciKey3.get())
                .isEqualToIgnoringCase(ciKey4.get())
                .isEqualToIgnoringCase(ciKey5.get());
        assertThat(ciKey1.getAsLowerCase())
                .isEqualTo(ciKey2.getAsLowerCase())
                .isEqualTo(ciKey3.getAsLowerCase())
                .isEqualTo(ciKey4.getAsLowerCase())
                .isEqualTo(ciKey5.getAsLowerCase());
        assertThat(ciKey1.hashCode())
                .isEqualTo(ciKey2.hashCode())
                .isEqualTo(ciKey3.hashCode())
                .isEqualTo(ciKey4.hashCode())
                .isEqualTo(ciKey5.hashCode());

        // Make sure not a common key
        assertThat(CIKeys.commonKeys())
                .contains(ciKey1);
    }

    @Test
    void testKnownUpperCase() {
        CIKeys.addCommonKey(CIKeys.UUID);
        final String key = CIKeys.UUID.get();

        final CIKey ciKey1 = CIKey.of(key);
        final CIKey ciKey2 = CIKey.ofDynamicKey(key);
        // Key is known so this will work even though the case is wrong
        final CIKey ciKey3 = CIKey.ofLowerCase(key);
        // Key is known so this will work even though the case is wrong
        final CIKey ciKey4 = CIKey.of(key, key);
        final CIKey ciKey5 = CIKeys.UUID;

        assertThat(ciKey1)
                .isNotSameAs(ciKey2)  // Dynamic one
                .isSameAs(ciKey3)
                .isSameAs(ciKey4)
                .isSameAs(ciKey5);
        assertThat(ciKey1)
                .isEqualTo(ciKey2)
                .isEqualTo(ciKey3)
                .isEqualTo(ciKey4)
                .isEqualTo(ciKey5);
        assertThat(ciKey1.get())
                .isEqualToIgnoringCase(ciKey2.get())
                .isEqualToIgnoringCase(ciKey3.get())
                .isEqualToIgnoringCase(ciKey4.get())
                .isEqualToIgnoringCase(ciKey5.get());
        assertThat(ciKey1.getAsLowerCase())
                .isEqualTo(ciKey2.getAsLowerCase())
                .isEqualTo(ciKey3.getAsLowerCase())
                .isEqualTo(ciKey4.getAsLowerCase())
                .isEqualTo(ciKey5.getAsLowerCase());
        assertThat(ciKey1.hashCode())
                .isEqualTo(ciKey2.hashCode())
                .isEqualTo(ciKey3.hashCode())
                .isEqualTo(ciKey4.hashCode())
                .isEqualTo(ciKey5.hashCode());

        // Make sure not a common key
        assertThat(CIKeys.commonKeys())
                .contains(ciKey1);
    }

    @Test
    void testOfDynamicKey1() {
        final String key = "UUID";
        CIKeys.addCommonKey(CIKeys.UUID);
        final CIKey ciKey1 = CIKeys.UUID;
        final CIKey ciKey2 = CIKey.of(key);
        final CIKey ciKey3 = CIKey.ofDynamicKey(key);
        final CIKey ciKey4 = CIKey.ofDynamicKey(key);

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

    @Test
    void testOfDynamicKey2() {
        final String key = "accept";
        CIKeys.addCommonKey(CIKeys.ACCEPT);
        final CIKey ciKey1 = CIKeys.ACCEPT;
        final CIKey ciKey2 = CIKey.of(key);
        final CIKey ciKey3 = CIKey.ofDynamicKey(key);
        final CIKey ciKey4 = CIKey.ofDynamicKey(key);

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

    @Test
    void testIntern() {
        // Use a uuid so we know it won't be in the static map
        final String key = UUID.randomUUID().toString().toUpperCase();

        final CIKey ciKey1 = CIKey.internStaticKey(key);

        // Intern again
        final CIKey ciKey2 = CIKeys.internCommonKey(key);

        assertThat(ciKey2)
                .isEqualTo(ciKey1);
        assertThat(ciKey2)
                .isSameAs(ciKey1);

        final CIKey ciKey3 = CIKey.of(key);

        assertThat(ciKey3)
                .isEqualTo(ciKey1);
        assertThat(ciKey3)
                .isSameAs(ciKey1);
    }

    @Test
    void testMapOf_nullKey() {
        final Map<String, String> map = new HashMap<>();
        map.put(null, "bar");
        final Map<CIKey, String> ciMap = CIKey.mapOf(map);
        assertThat(ciMap.get(null))
                .isEqualTo("bar");
    }

    @Test
    void testMapOf_nullValue() {
        final Map<String, String> map = new HashMap<>();
        map.put("foo", null);
        Assertions.assertThatThrownBy(
                        () -> {
                            CIKey.mapOf(map);
                        })
                .isInstanceOf(NullPointerException.class);
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
                        assertThat(result2)
                                .isEqualTo(0);
                    } else {
                        assertThat(result2)
                                .isEqualTo(-1 * result);
                    }
                    return result;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, CIKey.ofDynamicKey("a")), -1)
                .addCase(Tuple.of(CIKey.EMPTY_STRING, CIKey.ofDynamicKey("a")), -1)
                .addCase(Tuple.of(CIKey.ofDynamicKey("aaa"), CIKey.ofDynamicKey("bbb")), -1)
                .addCase(Tuple.of(CIKey.ofDynamicKey("aaa"), CIKey.ofDynamicKey("BBB")), -1)
                .addCase(Tuple.of(CIKey.ofDynamicKey("aaa"), CIKey.ofDynamicKey("AAA")), 0)
                .addCase(Tuple.of(CIKey.ofDynamicKey("a"), CIKey.ofDynamicKey("aaa")), -1)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testOf2() {
        CIKeys.addCommonKey(CIKeys.FEED);
        CIKeys.addCommonKey(CIKeys.ACCEPT);
        CIKeys.addCommonKey(CIKeys.UUID);
        Assertions.assertThat(CIKeys.getCommonKey("unknown"))
                .isNull();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(CIKey.class)
                .withSingleArgTestFunction(CIKey::of)
                .withAssertions(testOutcome -> {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());
                    if (testOutcome.getInput().equals(testOutcome.getExpectedOutput().get())) {
                        // Make sure it is same instance as the common one
                        // Feed vs Feed, common instance
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .isSameAs(testOutcome.getExpectedOutput());
                    } else {
                        // FEED vs Feed, new instance
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .isNotSameAs(testOutcome.getExpectedOutput());
                    }
                })
                // Common key is 'Feed'
                .addCase("Feed", CIKeys.FEED)
                .addCase("feed", CIKeys.FEED)
                .addCase("FEED", CIKeys.FEED)
                // Common key is 'accept'
                .addCase("Accept", CIKeys.ACCEPT)
                .addCase("accept", CIKeys.ACCEPT)
                .addCase("ACCEPT", CIKeys.ACCEPT)
                // Common key is 'UUID'
                .addCase("Uuid", CIKeys.UUID)
                .addCase("uuid", CIKeys.UUID)
                .addCase("UUID", CIKeys.UUID)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testOfIgnoringCase2() {
        CIKeys.addCommonKey(CIKeys.FEED);
        CIKeys.addCommonKey(CIKeys.ACCEPT);
        CIKeys.addCommonKey(CIKeys.UUID);
        Assertions.assertThat(CIKeys.getCommonKey("unknown"))
                .isNull();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(CIKey.class)
                .withSingleArgTestFunction(CIKey::ofIgnoringCase)
                .withAssertions(testOutcome -> {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());
                    // Make sure it is same instance as the common one
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isSameAs(testOutcome.getExpectedOutput());
                })
                // Common key is 'Feed'
                .addCase("Feed", CIKeys.FEED)
                .addCase("feed", CIKeys.FEED)
                .addCase("FEED", CIKeys.FEED)
                // Common key is 'accept'
                .addCase("Accept", CIKeys.ACCEPT)
                .addCase("accept", CIKeys.ACCEPT)
                .addCase("ACCEPT", CIKeys.ACCEPT)
                // Common key is 'UUID'
                .addCase("Uuid", CIKeys.UUID)
                .addCase("uuid", CIKeys.UUID)
                .addCase("UUID", CIKeys.UUID)
                .build();
    }

    @Test
    @Disabled
        // manual run only
    void testOfLowerKeyPerf() {
        final List<String> lowerKeys = CIKeys.commonKeys()
                .stream()
                .map(CIKey::getAsLowerCase)
                .toList();

        final TimedCase caseCheckCase = TimedCase.of("Case check", (round, iterations) -> {
            long num = 0;
            for (long i = 0; i < iterations; i++) {
                for (final String key : lowerKeys) {
                    final String lower = key.toLowerCase();
                    // Hashcode should be cached after 1st round
                    num += key.hashCode();
                }
            }
            if (num == 0) {
                throw new RuntimeException("Shouldn't happen");
            }

        });

        final TimedCase toLowerCaseCase = TimedCase.of("To lowercase", (round, iterations) -> {
            long num = 0;
            for (long i = 0; i < iterations; i++) {
                for (final String key : lowerKeys) {
                    for (int j = 0; j < key.length(); j++) {
                        final char chr = key.charAt(j);
                        if (Character.isUpperCase(chr)) {
                            throw new RuntimeException(LogUtil.message("not lower case '{}'", key));
                        }
                    }
                    // Hashcode should be cached after 1st round
                    num += key.hashCode();
                }
            }
            if (num == 0) {
                throw new RuntimeException("Shouldn't happen");
            }
        });
        final int iterations = 1_000_000;

        TestUtil.comparePerformance(
                10,
                iterations,
                LOGGER::info,
                caseCheckCase,
                toLowerCaseCase);

        LOGGER.info("Check count = {}", ModelStringUtil.formatCsv(iterations * lowerKeys.size()));
    }

    // Last time I ran this it did:
    // Completed 'Local Known Key' (round 3) in PT0.734136101S
    // Completed 'Common Key' (round 3) in PT0.800335664S
    // Completed 'Dynamic Key' (round 3) in PT4.789287442S
    // Completed 'No CIKey' (round 3) in PT5.018792741S
    // HashMap in knownKeys very fractionally faster than ConcurrentHashMap in CIKeys
    // Map lookup is much faster than creating a new CIKey or doing it without CIKey
    @Test
    @Disabled
    // manual run only
    void testPerf() {
        final List<CIKey> ciKeys = new ArrayList<>(CIKeys.commonKeys());

        LOGGER.info("Key count: {}", ciKeys.size());

        final List<String> keys = new ArrayList<>(ciKeys
                .stream()
                .map(CIKey::get)
                .toList());

        final Set<CIKey> keysWithDups = ciKeys.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Entry::getKey)
                .collect(Collectors.toSet());
        assertThat(keysWithDups)
                .isEmpty();

        final Map<String, CIKey> localKnownKeys = ciKeys
                .stream()
                .collect(Collectors.toMap(CIKey::get, Function.identity()));

        final List<String> lowerKeys = keys.stream()
                .map(key -> NullSafe.get(key, String::toLowerCase))
                .toList();

        final int cpuCount = Runtime.getRuntime().availableProcessors();
        final ExecutorService executorService = Executors.newFixedThreadPool(cpuCount);

        // Will always get the CIKey from localKnownKeys
        final TimedCase localKnownKeyCase = TimedCase.of("Local Known Key", (round, iterations) -> {
            doWorkOnThreads(cpuCount, iterations, executorService, () -> {
                for (int j = 0; j < keys.size(); j++) {
                    final String key = keys.get(j);
                    final String lowerKey = lowerKeys.get(j);
                    final CIKey ciKey = CIKey.of(key, localKnownKeys);
                    if (!ciKey.equalsIgnoreCase(lowerKey)) {
                        throw new RuntimeException("Mismatch");
                    }
                }
            });
        });

        // Will always get the CIKey instance from a map.
        final TimedCase commonKeyCase = TimedCase.of("Common Key", (round, iterations) -> {
            doWorkOnThreads(cpuCount, iterations, executorService, () -> {
                for (int j = 0; j < keys.size(); j++) {
                    final String key = keys.get(j);
                    final String lowerKey = lowerKeys.get(j);
                    final CIKey ciKey = CIKey.of(key);
                    if (!ciKey.equalsIgnoreCase(lowerKey)) {
                        throw new RuntimeException("Mismatch");
                    }
                }
            });
        });

        // Will always create a new CIKey instance, except for "" and null.
        final TimedCase dynamicKeyCase = TimedCase.of("Dynamic Key", (round, iterations) -> {
            doWorkOnThreads(cpuCount, iterations, executorService, () -> {
                for (int j = 0; j < keys.size(); j++) {
                    final String key = keys.get(j);
                    final String lowerKey = lowerKeys.get(j);
                    final CIKey ciKey = CIKey.ofDynamicKey(key);
                    if (!ciKey.equalsIgnoreCase(lowerKey)) {
                        throw new RuntimeException("Mismatch");
                    }
                }
            });
        });

        // No CIKey, so have the added cost of lower-casing the key to do the equality check
        final TimedCase noCiKeyCase = TimedCase.of("No CIKey", (round, iterations) -> {
            doWorkOnThreads(cpuCount, iterations, executorService, () -> {
                for (int j = 0; j < keys.size(); j++) {
                    String key = keys.get(j);
                    final String lowerKey = lowerKeys.get(j);
                    if (key == null && lowerKey == null) {
                        // this is ok
                    } else if (key == null) {
                        throw new RuntimeException("Mismatch");
                    } else {
                        // In a cache key situation we would have to ensure both sides are lower-cased
                        key = key.toLowerCase();
                        if (!key.equals(lowerKey)) {
                            throw new RuntimeException("Mismatch");
                        }
                    }
                }
            });
        });

        TestUtil.comparePerformance(
                3,
                1_000_000,
                LOGGER::info,
                localKnownKeyCase,
                commonKeyCase,
                dynamicKeyCase,
                noCiKeyCase);
    }

    private void doWorkOnThreads(final int threadCount,
                                 final long iterations,
                                 final ExecutorService executorService,
                                 final Runnable work) {

        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = 0; j < iterations; j++) {
                    work.run();
                }
            }, executorService));
        }

        for (final CompletableFuture<Void> future : futures) {
            try {
                future.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void dumpCiKeys(final Collection<CIKey> ciKeys) {
        NullSafe.stream(ciKeys)
                .forEach(ciKey ->
                        LOGGER.debug("CiKey {}, lower: {}, hash: {}",
                                ciKey.get(), ciKey.getAsLowerCase(), ciKey.hashCode()));
    }


    // --------------------------------------------------------------------------------


    private static class SerdeTestClass {

        @JsonProperty
        private final CIKey ciKey;
        @JsonProperty
        private final String string;

        private SerdeTestClass(@JsonProperty("ciKey") final CIKey ciKey,
                               @JsonProperty("string") final String string) {
            this.ciKey = ciKey;
            this.string = string;
        }

        public CIKey getCiKey() {
            return ciKey;
        }

        public String getString() {
            return string;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SerdeTestClass that = (SerdeTestClass) o;
            return Objects.equals(ciKey, that.ciKey) && Objects.equals(string, that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ciKey, string);
        }
    }
}
