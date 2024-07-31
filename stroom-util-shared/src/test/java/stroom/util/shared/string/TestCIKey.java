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

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.shared.string.CIKey.equalsIgnoreCase;

class TestCIKey {

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
                    assertThat(equalsIgnoreCase(ciKey, str))
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

    @Test
    void testSorting() {

        final Map<CIKey, String> map = new HashMap<>();
        Stream.of("0", "1", "A", "aa", "b", "C", "d")
                .forEach(str -> map.put(CIKey.of(str), str));

        assertThat(map.keySet()
                .stream()
                .sorted()
                .toList())
                .extracting(CIKey::get)
                .containsExactly("0", "1", "A", "aa", "b", "C", "d");

        assertThat(map.keySet()
                .stream()
                .sorted()
                .toList())
                .extracting(CIKey::getAsLowerCase)
                .containsExactly("0", "1", "a", "aa", "b", "c", "d");
    }
}
