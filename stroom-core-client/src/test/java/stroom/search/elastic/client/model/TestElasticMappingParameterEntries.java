/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.client.model;


import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticMappingParameterEntries {

    @Test
    void testPlainValue_isOneLine() {
        final List<ElasticMappingEntry> entries = ElasticMappingParameterEntries.create(Map.of("analyzer", "english"));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getName()).isEqualTo("analyzer");
            assertThat(entry.getValue()).isEqualTo("english");
            assertThat(entry.getValues()).isNull();
        });
    }

    @Test
    void testObject_isListedOneOptionPerLine() {
        final List<ElasticMappingEntry> entries = ElasticMappingParameterEntries.create(Map.of(
                "index_options.type", "int8_hnsw",
                "index_options.m", "16",
                "index_options.rescore_vector.oversample", "3.0"));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getName()).isEqualTo("index_options");
            assertThat(entry.getValue()).isNull();
            assertThat(entry.getValues()).containsExactly(
                    "m: 16",
                    "rescore_vector.oversample: 3.0",
                    "type: int8_hnsw");
        });
    }

    @Test
    void testList_isListedOneValuePerLineInIndexOrder() {
        final Map<String, String> parameters = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            parameters.put("copy_to[" + i + "]", "field" + i);
        }

        final List<ElasticMappingEntry> entries = ElasticMappingParameterEntries.create(parameters);

        // [10] and [11] come after [9], not after [1]
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getName()).isEqualTo("copy_to");
            assertThat(entry.getValues()).startsWith("field0", "field1", "field2").endsWith("field9", "field10",
                    "field11");
        });
    }

    @Test
    void testListOfObjects_showsEachItemsPath() {
        final List<ElasticMappingEntry> entries = ElasticMappingParameterEntries.create(Map.of(
                "a[0].x", "y",
                "a[1][0]", "z"));

        assertThat(entries).singleElement()
                .extracting(ElasticMappingEntry::getValues)
                .isEqualTo(List.of("[0].x: y", "z"));
    }

    @Test
    void testParameters_areOrderedByName() {
        final List<ElasticMappingEntry> entries = ElasticMappingParameterEntries.create(Map.of(
                "similarity", "cosine",
                "index_options.type", "hnsw",
                "dims", "3",
                "meta", "{}"));

        assertThat(entries).extracting(ElasticMappingEntry::getName)
                .containsExactly("dims", "index_options", "meta", "similarity");
        assertThat(entries.get(2).getValue()).isEqualTo("{}");
    }

    @Test
    void testNoParameters() {
        assertThat(ElasticMappingParameterEntries.create(null)).isEmpty();
        assertThat(ElasticMappingParameterEntries.create(Map.of())).isEmpty();
    }
}
