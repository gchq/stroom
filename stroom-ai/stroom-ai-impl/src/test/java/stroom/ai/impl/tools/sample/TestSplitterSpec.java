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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Data Splitter spec's further options: escape, spaced_values, catch_all, blank_line_records and the fields
/// kind.
class TestSplitterSpec {

    @Test
    void theAddedOptionsGoToAndFromJson() {
        final Map<String, Object> json = Map.of("kind", "fields", "fields", Map.of("user", "user=(\\S+)"),
                "catch_all", true, "blank_line_records", true);

        final SplitterSpec spec = SplitterSpec.fromMap(json);

        assertThat(spec.fields()).containsEntry("user", "user=(\\S+)");
        assertThat(spec.catchAll()).isTrue();
        assertThat(spec.blankLineRecords()).isTrue();
        assertThat(spec.toMap()).containsAllEntriesOf(json);
        assertThat(SplitterSpec.fromMap(Map.of("kind", "delimited", "header", true, "escape", "\\")).toMap())
                .containsEntry("escape", "\\");
        assertThat(SplitterSpec.fromMap(Map.of("kind", "key_value", "spaced_values", true)).spacedValues()).isTrue();
        // Defaults are left out of the JSON, so a spec without these options is written as it was before them.
        assertThat(SplitterSpec.builder("key_value").build().toMap()).containsOnlyKeys("kind", "delimiter");
    }

    @Test
    void eachOptionIsRefusedWhereItMeansNothing() {
        assertThatThrownBy(() -> SplitterSpec.builder("regex").pattern("(.*)").names(List.of("a")).escape("\\")
                .build()).hasMessageContaining("escape is for delimited text");
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").header(true).escape(",").build())
                .hasMessageContaining("escape must differ");
        assertThatThrownBy(() -> SplitterSpec.builder("key_value").quote("\"").spacedValues(true).build())
                .hasMessageContaining("spaced_values is for key_value without quote");
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").header(true).catchAll(true).build())
                .hasMessageContaining("catch_all is for the kinds that can fail");
        assertThatThrownBy(() -> SplitterSpec.builder("regex").pattern("(.*)").names(List.of("a"))
                .recordStart("\\d").blankLineRecords(true).build())
                .hasMessageContaining("blank_line_records is for");
        assertThatThrownBy(() -> SplitterSpec.builder("fields").build())
                .hasMessageContaining("fields (a name and a regex for each field)");
        assertThatThrownBy(() -> SplitterSpec.builder("regex").pattern("(.*)").names(List.of("a"))
                .fields(Map.of("a", "b")).build())
                .hasMessageContaining("is for the fields kind");
        assertThatThrownBy(() -> SplitterSpec.builder("fields").fields(Map.of("a", "")).build())
                .hasMessageContaining("each of fields needs a name and a regex");
    }
}
