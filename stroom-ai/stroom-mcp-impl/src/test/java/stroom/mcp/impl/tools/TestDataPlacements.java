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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The fields a draft carries as Data, beside the schema elements that could hold them. Seen in testing: Netskope's
/// Network/Connect events carried a city, coordinates, a rule name, a URL, an HTTP method and a status code as Data
/// until the user asked for them to be mapped.
class TestDataPlacements {

    private static final EventSchema SCHEMA = schema();

    @Test
    void eachRulesDataIsListedBesideTheElementsOfEventSourceAndItsAction() {
        final Map<String, Object> mapping = Map.of(
                "common", List.of(Map.of("path", "EventSource/Device/Data", "data_name", "city", "field", "city")),
                "events", List.of(
                        Map.of("name", "connect", "fields", List.of(
                                // Every rule writes its TypeId first (found in review: taken as the action).
                                Map.of("path", "EventDetail/TypeId", "value", "Connect"),
                                Map.of("path", "EventDetail/Network/Network/Destination/HostName", "field", "host"),
                                Map.of("path", "EventDetail/Network/Network/Data", "data_name", "url", "field",
                                        "url"),
                                Map.of("path", "EventDetail/Network/Network/Data", "data_name", "status", "field",
                                        "status"))),
                        Map.of("name", "other", "fields", List.of(
                                Map.of("path", "EventDetail/Unknown/Data", "data_name", "x", "field", "x")))));

        final Map<String, Object> placement = DataPlacements.of(mapping, SCHEMA);

        assertThat(JsonValues.strings(placement.get("event_source"))).anySatisfy(path -> assertThat(path)
                .endsWith("Location/City")).noneSatisfy(path -> assertThat(path).endsWith("Data"));
        final List<Map<String, Object>> rules = JsonValues.maps(placement.get("rules"));
        assertThat(rules).extracting(rule -> rule.get("rule")).containsExactly("common", "connect", "other");
        assertThat(rules.get(0)).doesNotContainKey("action");
        assertThat(rules.get(1)).containsEntry("data", List.of("url", "status")).containsEntry("action", "Network");
        assertThat(JsonValues.strings(rules.get(1).get("action_elements"))).isNotEmpty()
                .noneSatisfy(path -> assertThat(path).endsWith("Data"));
    }

    @Test
    void aDraftCarryingNoDataNeedsNoPlacing() {
        final Map<String, Object> mapping = Map.of("common", List.of(), "events", List.of(Map.of("name", "logon",
                "fields", List.of(Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon")))));

        assertThat(DataPlacements.of(mapping, SCHEMA)).isEmpty();
    }

    private static EventSchema schema() {
        try (final InputStream in = TestDataPlacements.class.getResourceAsStream("event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
