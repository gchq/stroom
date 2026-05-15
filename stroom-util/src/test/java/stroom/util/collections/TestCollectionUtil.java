/*
 * Copyright 2016-2026 Crown Copyright
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCollectionUtil {

    @Nested
    @DisplayName("mappingKey(Function) tests")
    class MappingKeyFunctionTests {

        @Test
        @DisplayName("Should return a function that correctly maps the key of an Entry")
        void shouldMapKey() {
            final Function<Entry<Integer, String>, Entry<String, String>> mapper =
                    CollectionUtil.createKeyMapper(String::valueOf);

            final Map.Entry<String, String> result = mapper.apply(Map.entry(123, "Value"));

            assertThat(result)
                    .isEqualTo(Map.entry("123", "Value"));
        }

        @Test
        @DisplayName("Should throw NullPointerException when key mapper is null")
        void nullKeyMapper_ShouldThrowNPE() {
            assertThatThrownBy(() -> CollectionUtil.createKeyMapper(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("mappingKey(Map, Function) tests")
    class MappingKeyMapTests {

        @Test
        @DisplayName("Should map all keys in a populated map")
        void shouldMapKeysInMap() {
            final Map<Integer, String> inputMap = Map.of(1, "One", 2, "Two");

            final Map<String, String> result = CollectionUtil.mappingKeys(inputMap, String::valueOf);

            assertThat(result)
                    .hasSize(2)
                    .containsEntry("1", "One")
                    .containsEntry("2", "Two");
        }

        @Test
        @DisplayName("Should return null when input map is null")
        void nullMap_ShouldReturnNull() {
            @SuppressWarnings("ConstantValue") final Map<String, String> result = CollectionUtil.mappingKeys(
                    (Map<String, String>) null, String::toUpperCase);

            //noinspection ConstantValue
            assertThat(result)
                    .isNull();
        }

        @Test
        @DisplayName("Should return empty map when input map is empty")
        void emptyMap_ShouldReturnEmptyMap() {
            final Map<String, String> result = CollectionUtil.mappingKeys(
                    Collections.emptyMap(),
                    (String str) -> str.toUpperCase());

            assertThat(result)
                    .isEmpty();
        }

        @Test
        @DisplayName("Should throw NullPointerException when mapper is null and map is not empty")
        void nullMapperWithPopulatedMap_ShouldThrowNPE() {
            final Map<Integer, String> inputMap = Map.of(1, "One");

            assertThatThrownBy(() -> CollectionUtil.mappingKeys(inputMap, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when mapped keys result in duplicates")
        void duplicateMappedKeys_ShouldThrowException() {
            // Mapping absolute values will cause both -1 and 1 to map to 1
            final Map<Integer, String> inputMap = Map.of(1, "A", -1, "B");

            assertThatThrownBy(() -> CollectionUtil.mappingKeys(inputMap, Math::abs))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("mappingValue(Function) tests")
    class MappingValueFunctionTests {

        @Test
        @DisplayName("Should return a function that correctly maps the value of an Entry")
        void shouldMapValue() {
            final Function<Map.Entry<String, Integer>, Map.Entry<String, String>> mapper =
                    CollectionUtil.createValueMapper(String::valueOf);

            final Map.Entry<String, String> result = mapper.apply(Map.entry("Key", 123));

            assertThat(result)
                    .isEqualTo(Map.entry("Key", "123"));
        }

        @Test
        @DisplayName("Should throw NullPointerException when value mapper is null")
        void nullValueMapper_ShouldThrowNPE() {
            assertThatThrownBy(() -> CollectionUtil.createValueMapper(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("mappingValue(Map, Function) tests")
    class MappingValueMapTests {

        @Test
        @DisplayName("Should map all values in a populated map")
        void shouldMapValuesInMap() {
            final Map<String, Integer> inputMap = Map.of("A", 1, "B", 2);

            final Map<String, String> result = CollectionUtil.mappingValues(inputMap, String::valueOf);

            assertThat(result)
                    .hasSize(2)
                    .containsEntry("A", "1")
                    .containsEntry("B", "2");
        }

        @Test
        @DisplayName("Should return null when input map is null")
        void nullMap_ShouldReturnNull() {
            //noinspection ConstantValue
            final Map<String, String> result = CollectionUtil.mappingValues(
                    null, (String str) -> str.toUpperCase());

            //noinspection ConstantValue
            assertThat(result)
                    .isNull();
        }

        @Test
        @DisplayName("Should return empty map when input map is empty")
        void emptyMap_ShouldReturnEmptyMap() {
            final Map<String, String> result = CollectionUtil.mappingValues(
                    Collections.emptyMap(),
                    (String str) -> str.toUpperCase());

            assertThat(result)
                    .isEmpty();
        }

        @Test
        @DisplayName("Should throw NullPointerException when mapper is null and map is not empty")
        void nullMapperWithPopulatedMap_ShouldThrowNPE() {
            final Map<String, Integer> inputMap = Map.of("A", 1);

            assertThatThrownBy(() -> CollectionUtil.mappingValues(inputMap, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
