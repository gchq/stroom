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

package stroom.util.string;

import stroom.util.string.MultiCaseMap.MultipleMatchException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.shared.string.CIKey.of;

class TestMultiCaseMap {

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
                .isFalse();
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
    void getCaseSensitive() {
        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("a", "a1");
        map.put("A", "a2");

        map.put("b", "b1");

        assertThat(map.get(of("a")).keySet())
                .containsExactlyInAnyOrder("a", "A");
        assertThat(map.get(of("A")).keySet())
                .containsExactlyInAnyOrder("a", "A");
        assertThat(map.getCaseSensitive(of("a")))
                .isEqualTo("a1");
        assertThat(map.getCaseSensitive(of("A")))
                .isEqualTo("a2");

        assertThat(map.get(of("b")).keySet())
                .containsExactlyInAnyOrder("b");
        assertThat(map.get(of("B")).keySet())
                .containsExactlyInAnyOrder("b");
        assertThat(map.getCaseSensitive(of("b")))
                .isEqualTo("b1");
        assertThat(map.getCaseSensitive(of("B")))
                .isEqualTo("b1");
    }

    @Test
    void getCaseSensitive_throws() {

        final MultiCaseMap<String> map = new MultiCaseMap<>();
        map.put("foo", "f1");
        map.put("FOO", "f2");
        map.put("Foo", "f3");

        assertThat(map.get(of("foO")).keySet())
                .containsExactlyInAnyOrder("foo", "FOO", "Foo");

        Assertions.assertThatThrownBy(
                        () -> {
                            map.getCaseSensitive(of("fOO"));
                        })
                .isInstanceOf(MultipleMatchException.class);
    }
}
