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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// What an event-logging element takes, from the schema: Qwen in VS Code spent twelve minutes writing PowerShell to
/// read the action elements and their children out of the XSD.
class TestEventElements {

    private final HeldSchemas held = new HeldSchemasWorld()
            .hold("file://event-logging-v4.1.0.xsd", "event-logging:3", HeldSchemasWorld.XSD_4_1_0)
            .heldSchemas();

    @Test
    @SuppressWarnings("unchecked")
    void testDescribeEventElementSaysWhatAnElementTakes() {
        final Map<String, Object> detail = EventElements.describe(held, "EventDetail", null, null);
        assertThat(detail.get("path")).isEqualTo("Event/EventDetail");
        assertThat(detail.get("schema_version")).isEqualTo("4.1.0");
        final Map<String, Object> actions = ((List<Map<String, Object>>) detail.get("choices")).stream()
                .filter(choice -> ((List<String>) choice.get("one_of")).contains("Authenticate"))
                .findFirst()
                .orElseThrow();
        assertThat(actions.get("one_required")).isEqualTo(true);
        assertThat((List<String>) actions.get("one_of")).contains("Process", "Unknown", "Network");

        final Map<String, Map<String, Object>> process = children(EventElements.describe(held,
                "EventDetail/Process", null, null));
        assertThat(process.get("Action").get("required")).isEqualTo(true);
        assertThat((List<String>) process.get("Action").get("values")).contains("Execute");
        assertThat(process.get("Type").get("values")).isEqualTo(List.of("OS", "Service", "Application"));
        assertThat(process.get("Command").get("required")).isEqualTo(true);

        final Map<String, Object> leaf = EventElements.describe(held, "EventDetail/Authenticate/Action", null, null);
        assertThat(leaf.get("path")).isEqualTo("Event/EventDetail/Authenticate/Action");
        assertThat((List<String>) leaf.get("values")).contains("Logon");
        assertThat(leaf.get("type")).isEqualTo("string");

        assertThatThrownBy(() -> EventElements.describe(held, "EventDetail/Acess", null, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("has no child 'Acess'");
    }

    @Test
    void testTheVersionChosenForTheBuildIsUsedWhereNoneIsGiven() {
        assertThatThrownBy(() -> EventElements.describe(held, "EventDetail", null, "3.5.2"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("Stroom has no XML schema 'file://event-logging-v3.5.2.xsd'");
        assertThat(EventElements.describe(held, "EventDetail", "4.1.0", "3.5.2").get("schema_version"))
                .isEqualTo("4.1.0");
    }

    @Test
    void testAnElementsDescriptionIsTheFirstSentenceOfItsDocumentation() {
        final Map<String, Object> user = EventElements.describe(held, "EventSource/User", null, null);
        assertThat((String) user.get("description")).isNotEmpty().endsWith(".");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> children(final Map<String, Object> described) {
        final Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (final Map<String, Object> child : (List<Map<String, Object>>) described.get("children")) {
            byName.put((String) child.get("name"), child);
        }
        return byName;
    }
}
