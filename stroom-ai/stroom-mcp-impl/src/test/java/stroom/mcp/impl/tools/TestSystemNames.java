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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// System/Name for a stream of several products. Seen in testing (chat 144): one name for five products, the user
/// having asked for each product's from the vendor's documentation.
class TestSystemNames {

    @Test
    void severalVariantsWithNoNameGivenAreEachAskedFor() {
        final Map<String, Object> mapping = mapping();
        final List<Object> notes = new ArrayList<>();

        SystemNames.apply(mapping, "variant", null, notes);

        assertThat(String.valueOf(mapping.get("common"))).doesNotContain("System/Name");
        assertThat(systemNameOf(mapping, 0)).isEqualTo("TODO system name (cef)");
        assertThat(systemNameOf(mapping, 1)).isEqualTo("TODO system name (syslog)");
        assertThat(String.valueOf(notes)).contains("2 formats (cef, syslog)", "one per product",
                "system_name={\"<variant>\": \"<system>\"}");
    }

    @Test
    void eachVariantsNameMayBeGiven() {
        final Map<String, Object> mapping = mapping();
        final List<Object> notes = new ArrayList<>();

        SystemNames.apply(mapping, "variant", "{\"cef\": \"SentinelWorks SecureGate\", \"syslog\": \"Atlas BackupD\"}",
                notes);

        assertThat(systemNameOf(mapping, 0)).isEqualTo("SentinelWorks SecureGate");
        assertThat(systemNameOf(mapping, 1)).isEqualTo("Atlas BackupD");
        assertThat(notes).isEmpty();
        assertThatThrownBy(() -> SystemNames.apply(mapping(), "variant", "{not json", new ArrayList<>()))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("JSON object of each variant's name");
    }

    @Test
    void oneNameGivenOrOneFormatKeepsOneName() {
        final Map<String, Object> given = mapping();
        SystemNames.apply(given, "variant", "Server Telemetry", new ArrayList<>());
        assertThat(String.valueOf(given.get("common"))).contains("System/Name");

        final Map<String, Object> oneFormat = mapping();
        SystemNames.apply(oneFormat, null, null, new ArrayList<>());
        assertThat(String.valueOf(oneFormat.get("common"))).contains("System/Name");
        assertThat(SystemNames.single("Server Telemetry")).isEqualTo("Server Telemetry");
        assertThat(SystemNames.single("{\"cef\": \"A\"}")).isNull();
    }

    private static Map<String, Object> mapping() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", new ArrayList<>(List.of(Map.of("path", "EventSource/System/Name", "value", "Mixed"))));
        mapping.put("events", new ArrayList<>(List.of(rule("cef"), rule("syslog"))));
        return mapping;
    }

    private static Map<String, Object> rule(final String variant) {
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", variant);
        rule.put("when", List.of(Map.of("field", "variant", "equals", variant)));
        rule.put("fields", new ArrayList<>(List.of(Map.of("path", "EventDetail/TypeId", "value", variant))));
        return rule;
    }

    @SuppressWarnings("unchecked")
    private static String systemNameOf(final Map<String, Object> mapping, final int rule) {
        final Map<String, Object> first = ((List<Map<String, Object>>) ((List<Map<String, Object>>) mapping
                .get("events")).get(rule).get("fields")).getFirst();
        assertThat(first).containsEntry("path", "EventSource/System/Name");
        return String.valueOf(first.get("value"));
    }
}
