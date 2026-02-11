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

package stroom.util.collections;

import stroom.util.shared.string.CIKey;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMultiCaseMap {

    @Test
    void empty() {
        final MultiCaseMap<String> multiCaseMap = new MultiCaseMap<>();
        assertThat(multiCaseMap.isEmpty())
                .isTrue();
        multiCaseMap.put("foo", "bar");
        assertThat(multiCaseMap.isEmpty())
                .isFalse();
    }

    @Test
    void size() {
        final MultiCaseMap<String> multiCaseMap = new MultiCaseMap<>();
        assertThat(multiCaseMap.size())
                .isZero();
        multiCaseMap.put("foo", "bar");
        assertThat(multiCaseMap.size())
                .isEqualTo(1);
    }

    @Test
    void get() {
        final MultiCaseMap<String> multiCaseMap = new MultiCaseMap<>();
        multiCaseMap.put("foo", "bar");
        assertThat(multiCaseMap.get("foo"))
                .isEqualTo("bar");

        multiCaseMap.put("FOO", "bar");
        assertThat(multiCaseMap.get("FOO"))
                .isEqualTo("bar");

        assertThat(multiCaseMap.get("Foo"))
                .isEqualTo("bar");
    }

    @Test
    void testGet() {
    }

    @Test
    void put() {
    }

    @Test
    void testPut() {
    }

    @Test
    void containsKey() {
        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("a", "a1");
        map.put("A", "a2");
        map.put("b", "b1");

        assertThat(map.containsKey("a"))
                .isTrue();
        assertThat(map.containsKey("A"))
                .isTrue();
        assertThat(map.containsKey("b"))
                .isTrue();
        assertThat(map.containsKey("B"))
                .isTrue();
    }

    @Test
    void entrySet() {
        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("a", "a1");
        map.put("A", "a2");

        map.put("b", "b1");

        assertThat(map.entrySet())
                .containsExactlyInAnyOrder(
                        Map.entry("a", "a1"),
                        Map.entry("A", "a2"),
                        Map.entry("b", "b1"));
    }

    @Test
    void get2() {
        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("a", "a1");
        map.put("A", "a2");
        map.put("b", "b1");

        assertThat(map.keySet())
                .containsExactlyInAnyOrder("a", "A", "b");

        assertThat(map.get(CIKey.of("a")))
                .isEqualTo("a1");
        assertThat(map.get(CIKey.of("A")))
                .isEqualTo("a2");

        assertThat(map.get(CIKey.of("b")))
                .isEqualTo("b1");
        assertThat(map.get(CIKey.of("B")))
                .isEqualTo("b1");
    }

    @Test
    void getCaseSensitive() {
        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("a", "a1");
        map.put("A", "a2");
        map.put("b", "b1");

        assertThat(map.keySet())
                .containsExactlyInAnyOrder("a", "A", "b");

        assertThat(map.getCaseSensitive(CIKey.of("a")))
                .isEqualTo("a1");
        assertThat(map.getCaseSensitive(CIKey.of("A")))
                .isEqualTo("a2");

        assertThat(map.getCaseSensitive(CIKey.of("b")))
                .isEqualTo("b1");
        assertThat(map.getCaseSensitive(CIKey.of("B")))
                .isNull();
    }

    @Test
    void get_throws() {

        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("foo", "f1");
        map.put("FOO", "f2");
        map.put("Foo", "f3");

        assertThat(map.keySet())
                .containsExactlyInAnyOrder("foo", "FOO", "Foo");

        assertThat(map.getCaseSensitive(CIKey.of("fOO")))
                .isNull();

        Assertions.assertThatThrownBy(
                        () -> {
                            map.get(CIKey.of("fOO"));
                        })
                .isInstanceOf(MultipleMatchException.class);
    }
}
