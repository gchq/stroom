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

import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.Placeholders;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestRequiredSources {

    private static final EventSchema SCHEMA = EventSchema.parse(TranslationWorld.resource(
            "event-logging-v4.1.0.xsd"));

    @Test
    void aStandInInTheOnlyFieldsOfARequiredElementWouldEmptyIt() {
        final TranslationMapping mapping = mapping("""
                {"path": "EventSource/Device/HostName", "field": "host"}""");

        assertThat(RequiredSources.emptied(mapping, SCHEMA, List.of(found("-", "host"), found("N/A", "user"))))
                .containsExactly(Map.entry("-", List.of(RequiredSources.DEVICE)));
        // The time is required to its leaf.
        assertThat(RequiredSources.emptied(mapping, SCHEMA, List.of(found("-", "ts"))))
                .containsExactly(Map.entry("-", List.of("EventTime/TimeCreated")));
    }

    @Test
    void anotherSourceOfTheElementKeepsItFilled() {
        // Another field, a default, or the sender standing in.
        for (final String device : List.of(
                "{\"path\": \"EventSource/Device/HostName\", \"field\": \"host\"}, "
                + "{\"path\": \"EventSource/Device/IPAddress\", \"field\": \"ip\"}",
                "{\"path\": \"EventSource/Device/HostName\", \"any_of\": [\"host\", \"devname\"]}",
                "{\"path\": \"EventSource/Device/HostName\", \"field\": \"host\", \"default\": \"unknown\"}",
                "{\"path\": \"EventSource/Device/HostName\", \"field\": \"host\", "
                + "\"meta_fallback\": [\"RemoteAddress\"]}")) {
            assertThat(RequiredSources.emptied(mapping(device), SCHEMA, List.of(found("-", "host"))))
                    .as(device)
                    .isEmpty();
        }
    }

    @Test
    void theSenderStandsInForTheDeviceOfTheFieldsLeftOut() {
        final TranslationMapping mapping = RequiredSources.withDeviceFallback(mapping("""
                {"path": "EventSource/Device/HostName", "field": "host"},
                {"path": "EventSource/Device/IPAddress", "field": "host_ip"}"""), Set.of("host", "host_ip"));

        assertThat(String.valueOf(mapping.toJson(DumpMode.COMPACT).get("common")))
                .contains("field=host, meta_fallback=[RemoteHost, RemoteAddress]",
                        "field=host_ip, meta_fallback=[RemoteAddress]");
    }

    private static TranslationMapping mapping(final String device) {
        return TranslationMapping.fromJson(JsonUtil.readValue("""
                {"input": "json",
                 "common": [{"path": "EventTime/TimeCreated", "field": "ts"},
                            {"path": "EventSource/System/Name", "value": "Acme"},
                            {"path": "EventSource/System/Environment", "value": "Test"},
                            {"path": "EventSource/Generator", "value": "acme"},
                            %s,
                            {"path": "EventSource/User/Id", "field": "user"}],
                 "events": [{"name": "logon",
                             "fields": [{"path": "EventDetail/TypeId", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "user"}]}]}"""
                .formatted(device), Map.class));
    }

    private static Placeholders.Found found(final String value, final String field) {
        return new Placeholders.Found(value, List.of(field), 2, null);
    }
}
