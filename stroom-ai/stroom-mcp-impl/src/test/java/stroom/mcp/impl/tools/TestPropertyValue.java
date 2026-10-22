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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A pipeline property as the tools read it (the Stroom MCP server's `tools/pipeline_writes.py` `PropertyValue`,
/// and its test in `tests/test_pipelines.py`).
class TestPropertyValue {

    private static final String UUID = "0443f53b-a694-4d3d-90a4-e3310716cf0c";

    @Test
    void aPropertyNamedAsTheDescriptionsNameItIsReadAsThatProperty() {
        // Seen ("schema guessing"): agents wrote properties as the descriptions named them, translationFilter.xslt.
        final PropertyValue expected = PropertyValue.document("translationFilter", "xslt", UUID, "XSLT");

        assertThat(PropertyValue.of("translationFilter.xslt=" + UUID)).isEqualTo(expected);
        assertThat(PropertyValue.of(Map.of("translationFilter.xslt", UUID))).isEqualTo(expected);
        assertThat(PropertyValue.of(Map.of("element", "translationFilter.xslt", "doc_uuid", UUID, "doc_type",
                "XSLT"))).isEqualTo(expected);
        assertThat(PropertyValue.of(Map.of("jsonParser.addRootObject", false)).value()).isEqualTo(false);
        assertThatThrownBy(() -> PropertyValue.of(Map.of("xslt", UUID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("e.g. " + PropertyValue.EXAMPLE);
    }

    @Test
    void aListOfPropertiesIsReadInTheShapesModelsSendIt() {
        final List<PropertyValue> read = PropertyValue.listOf("[{\"element\": \"splitFilter\", \"name\": "
                                                              + "\"splitCount\", \"value\": 100}]",
                "set_properties");

        assertThat(read).containsExactly(PropertyValue.plain("splitFilter", "splitCount", 100L));
        assertThat(PropertyValue.listOf(Map.of("element", "a", "name", "b", "value", "c"), "set_properties"))
                .containsExactly(PropertyValue.plain("a", "b", "c"));
        assertThat(PropertyValue.listOf(null, "set_properties")).isEmpty();
        assertThatThrownBy(() -> PropertyValue.listOf(List.of(Map.of("element", "a")), "set_properties"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("set_properties[0]: a property is {element, name} with doc_uuid and "
                                        + "doc_type (a document) or value (a plain property)")
                .hasMessageEndingWith("; given {\"element\": \"a\"}");
    }
}
