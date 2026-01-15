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

package stroom.util.shared.concurrent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestCopyOnWriteMap {

    @Test
    void testPutGet() {
        final Map<String, String> map = Set.of(
                        "cat",
                        "dog",
                        "pig",
                        "horse")
                .stream()
                .collect(Collectors.toMap(Function.identity(), String::toUpperCase));

        final Map<String, String> copyOnWriteMap = new CopyOnWriteMap<>(map);
        assertThat(copyOnWriteMap.size())
                .isEqualTo(map.size());

        assertThat(copyOnWriteMap.put("cat", "CAT2"))
                .isEqualTo("CAT");
        ;
        assertThat(copyOnWriteMap.put("duck", "DUCK"))
                .isEqualTo(null);

        assertThat(copyOnWriteMap.size())
                .isEqualTo(map.size() + 1);
        assertThat(copyOnWriteMap.get("duck"))
                .isEqualTo("DUCK");
        assertThat(copyOnWriteMap.get("cat"))
                .isEqualTo("CAT2");
        assertThat(map.get("cat"))
                .isEqualTo("CAT");
        assertThat(copyOnWriteMap.containsKey("pig"))
                .isEqualTo(map.containsKey("pig"));
        assertThat(copyOnWriteMap.containsValue("PIG"))
                .isEqualTo(map.containsValue("PIG"));

        assertThat(copyOnWriteMap.replace("dog", "NOT DOG", "DOG2"))
                .isFalse();
        assertThat(copyOnWriteMap.replace("dog", "DOG", "DOG2"))
                .isTrue();
        assertThat(copyOnWriteMap.replace("dog", "DOG3"))
                .isEqualTo("DOG2");
        assertThat(copyOnWriteMap.replace("unknown", "UNKNOWN2"))
                .isEqualTo(null);
    }

    @Test
    void testImmutable() {
        final Map<String, String> map = Set.of(
                        "cat",
                        "dog",
                        "pig",
                        "horse")
                .stream()
                .collect(Collectors.toMap(Function.identity(), String::toUpperCase));

        final Map<String, String> copyOnWriteMap = new CopyOnWriteMap<>(map);
        assertThat(copyOnWriteMap)
                .hasSize(map.size());

        Assertions.assertThatThrownBy(() -> copyOnWriteMap.entrySet().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> copyOnWriteMap.entrySet().add(Map.entry("foo", "FOO")))
                .isInstanceOf(UnsupportedOperationException.class);

        Assertions.assertThatThrownBy(() -> copyOnWriteMap.keySet().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> copyOnWriteMap.keySet().add("foo"))
                .isInstanceOf(UnsupportedOperationException.class);

        Assertions.assertThatThrownBy(() -> copyOnWriteMap.values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> copyOnWriteMap.values().add("FOO"))
                .isInstanceOf(UnsupportedOperationException.class);

        copyOnWriteMap.clear();
        assertThat(copyOnWriteMap)
                .hasSize(0);
        assertThat(map)
                .hasSize(4);
    }

    @Test
    void testSupplier() {
        final Map<String, String> map = Set.of(
                        "cat",
                        "dog",
                        "pig",
                        "horse",
                        "duck",
                        "owl",
                        "fox",
                        "tiger",
                        "skunk")
                .stream()
                .collect(Collectors.toMap(Function.identity(), String::toUpperCase));

        final AtomicInteger callCount = new AtomicInteger();
        final Map<String, String> copyOnWriteMap = new CopyOnWriteMap<>(
                ignored -> {
                    callCount.incrementAndGet();
                    return new TreeMap<>();
                },
                map);
        assertThat(callCount)
                .hasValue(1);

        assertThat(copyOnWriteMap.keySet().stream().toList())
                .containsExactly(
                        "cat",
                        "dog",
                        "duck",
                        "fox",
                        "horse",
                        "owl",
                        "pig",
                        "skunk",
                        "tiger");

        // This relies on the assumption that the hashcodes of the keys do not result in them
        // being in alphabetical order in map.
        assertThat(copyOnWriteMap.keySet().stream().toList())
                .isNotEqualTo(map.keySet().stream().toList());

        copyOnWriteMap.put("mouse", "MOUSE");
        assertThat(callCount)
                .hasValue(2);

        assertThat(copyOnWriteMap.keySet().stream().toList())
                .containsExactly(
                        "cat",
                        "dog",
                        "duck",
                        "fox",
                        "horse",
                        "mouse",
                        "owl",
                        "pig",
                        "skunk",
                        "tiger");

        copyOnWriteMap.putAll(Map.of("bear", "BEAR",
                "snake", "SNAKE"));

        // only one copy op
        assertThat(callCount)
                .hasValue(3);
    }
}
