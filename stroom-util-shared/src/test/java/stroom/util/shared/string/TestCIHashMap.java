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

import org.junit.jupiter.api.Test;

import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;

class TestCIHashMap {

    @Test
    void putAndGet() {
        final CIHashMap<String> map = new CIHashMap<>();
        // Same effective key
        map.put("foo", "A");
        map.put(CIKey.of("FOO"), "B");

        // Same effective key
        map.put("bar", "C");
        map.put("BAR", "D");

        assertThat(map.size())
                .isEqualTo(2);

        assertThat(map.get("foo"))
                .isEqualTo("B");
        assertThat(map.get("foo"))
                .isEqualTo(map.get(CIKey.of("Foo")));

        assertThat(map.get("bar"))
                .isEqualTo("D");
        assertThat(map.get("bar"))
                .isEqualTo(map.get(CIKey.of("Bar")));

        assertThat(map.containsKey("foo"))
                .isTrue();
        assertThat(map.containsKey(CIKey.of("foo")))
                .isTrue();

        assertThat(map.containsKey("bar"))
                .isTrue();
        assertThat(map.containsKey(CIKey.of("bar")))
                .isTrue();
    }

    @Test
    void sorting() {
        final CIHashMap<String> map = new CIHashMap<>();
        map.put("D", "1");
        map.put("e", "1");
        map.put("B", "1");
        map.put("c", "1");
        map.put("a", "1");

        assertThat(map.entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .toList())
                .extracting(entry -> entry.getKey().getAsLowerCase())
                .containsExactly(
                        "a",
                        "b",
                        "c",
                        "d",
                        "e");
    }

    @Test
    void testContainsKey() {
        final CIHashMap<Integer> map = new CIHashMap<>(CIKey.mapOf(
                "a", 100,
                "b", 200));

        assertThat(map.containsKey("a"))
                .isTrue();
        assertThat(map.containsKey("A"))
                .isTrue();
        assertThat(map.containsKey("B"))
                .isTrue();
        assertThat(map.containsKey("z"))
                .isFalse();

        assertThat(map.containsKey(CIKey.of("a")))
                .isTrue();
        assertThat(map.containsKey(CIKey.of("A")))
                .isTrue();
        assertThat(map.containsKey(CIKey.of("B")))
                .isTrue();
        assertThat(map.containsKey(CIKey.of("z")))
                .isFalse();
    }
}
