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

package stroom.dashboard.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestListInputComponentSettings {

    @Test
    void testSerialisation() {
        final ListInputComponentSettings settings = ListInputComponentSettings.builder()
                .key("myKey")
                .values(List.of("a", "b", "c"))
                .multiSelect(true)
                .selectedValues(List.of("a", "c"))
                .build();

        final String json = JsonUtil.writeValueAsString(settings);
        final ListInputComponentSettings deserialised = JsonUtil.readValue(json, ListInputComponentSettings.class);

        assertThat(deserialised).isEqualTo(settings);
        assertThat(deserialised.isMultiSelect()).isTrue();
        assertThat(deserialised.getSelectedValues()).containsExactly("a", "c");
    }

    /// Settings saved before multi-select existed are single select.
    @Test
    void testDeserialisation_noMultiSelect() {
        final String json = """
                {"type":"list-input","key":"myKey","value":"b","values":["a","b"]}""";

        final ListInputComponentSettings deserialised = JsonUtil.readValue(json, ListInputComponentSettings.class);

        assertThat(deserialised.getValue()).isEqualTo("b");
        assertThat(deserialised.isMultiSelect()).isFalse();
        assertThat(deserialised.getSelectedValues()).isNull();
    }

    /// The copy must not share the selected values list with the original.
    @Test
    void testCopy_selectedValuesNotShared() {
        final List<String> selectedValues = new ArrayList<>(List.of("a"));
        final ListInputComponentSettings settings = ListInputComponentSettings.builder()
                .multiSelect(true)
                .selectedValues(selectedValues)
                .build();

        final ListInputComponentSettings copy = settings.copy().build();
        selectedValues.add("b");

        assertThat(copy.isMultiSelect()).isTrue();
        assertThat(copy.getSelectedValues()).containsExactly("a");
    }

    @Test
    void testEquals_multiSelectAndSelectedValuesMatter() {
        final ListInputComponentSettings settings = ListInputComponentSettings.builder()
                .multiSelect(true)
                .selectedValues(List.of("a"))
                .build();

        assertThat(settings.copy().multiSelect(false).build()).isNotEqualTo(settings);
        assertThat(settings.copy().selectedValues(List.of("b")).build()).isNotEqualTo(settings);
        assertThat(settings.copy().build()).isEqualTo(settings).hasSameHashCodeAs(settings);
    }

    @Test
    void testJoinSelectedValues() {
        assertThat(ListInputComponentSettings.joinSelectedValues(List.of("a", "b", "c")))
                .isEqualTo("a,b,c");
    }

    /// Values are trimmed, and blanks and duplicates are dropped, keeping the first occurrence's order.
    @Test
    void testJoinSelectedValues_trimsAndDropsBlanksAndDuplicates() {
        assertThat(ListInputComponentSettings.joinSelectedValues(Arrays.asList(" b ", "", "a", null, "b", "  ")))
                .isEqualTo("b,a");
    }

    @Test
    void testJoinSelectedValues_empty() {
        assertThat(ListInputComponentSettings.joinSelectedValues(null)).isEmpty();
        assertThat(ListInputComponentSettings.joinSelectedValues(List.of())).isEmpty();
        assertThat(ListInputComponentSettings.joinSelectedValues(List.of(" "))).isEmpty();
    }
}
