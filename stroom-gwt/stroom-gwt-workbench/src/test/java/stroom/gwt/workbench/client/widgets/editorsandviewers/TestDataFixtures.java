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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataFixtures {

    @Test
    void testQuote() {
        assertThat(DataFixtures.quote("a")).isEqualTo("\"a\"");
        assertThat(DataFixtures.quote("<a b=\"c\">\\\n\t</a>"))
                .isEqualTo("\"<a b=\\\"c\\\">\\\\\\n\\t</a>\"");
        assertThat(DataFixtures.quote("\u0001")).isEqualTo("\"\\u0001\"");
    }

    @Test
    void testQuote_null() {
        assertThat(DataFixtures.quote(null)).isEqualTo("null");
    }

    @Test
    void testQuote_roundTrip() {
        final String text = "line 1\nline \"2\"\r\n\u001f end";
        assertThat(JsonValues.parse(DataFixtures.quote(text))).isEqualTo(text);
    }

    @Test
    void testLocations() {
        assertThat(JsonValues.jsonEquals(DataFixtures.location(5, 1),
                "{\"type\": \"default\", \"lineNo\": 5, \"colNo\": 1}")).isTrue();
        assertThat(JsonValues.jsonEquals(DataFixtures.streamLocation(0, 4, 12),
                "{\"type\": \"stream\", \"partIndex\": 0, \"lineNo\": 4, \"colNo\": 12}")).isTrue();
    }

    @Test
    void testGetLong() {
        final String body = "{\"sourceLocation\": {\"metaId\": 1001, \"dataRange\": {\"charOffsetFrom\": 600}}}";
        assertThat(DataFixtures.getLong(body, 0, "sourceLocation", "dataRange", "charOffsetFrom"))
                .isEqualTo(600);
        assertThat(DataFixtures.getLong(body, 0, "sourceLocation", "metaId")).isEqualTo(1001);
    }

    @Test
    void testGetLong_missingOrNotANumber() {
        final String body = "{\"sourceLocation\": {\"childType\": \"Context\", \"dataRange\": null}}";
        assertThat(DataFixtures.getLong(body, 7, "sourceLocation", "dataRange", "charOffsetFrom"))
                .isEqualTo(7);
        assertThat(DataFixtures.getLong(body, 7, "sourceLocation", "childType")).isEqualTo(7);
        assertThat(DataFixtures.getLong(body, 7, "nothing")).isEqualTo(7);
        assertThat(DataFixtures.getLong(null, 7, "sourceLocation")).isEqualTo(7);
        assertThat(DataFixtures.getLong("", 7, "sourceLocation")).isEqualTo(7);
    }

    @Test
    void testGet() {
        final String body = "{\"expandedSeverities\": [\"ERROR\", \"WARNING\"], \"displayMode\": \"HEX\"}";
        assertThat(DataFixtures.get(body, "expandedSeverities")).isEqualTo(List.of("ERROR", "WARNING"));
        assertThat(DataFixtures.get(body, "displayMode")).isEqualTo("HEX");
        assertThat(DataFixtures.get(body, "displayMode", "deeper")).isNull();
    }
}
