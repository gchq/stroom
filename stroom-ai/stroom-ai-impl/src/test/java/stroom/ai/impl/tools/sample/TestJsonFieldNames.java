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

import stroom.ai.impl.tools.generation.XPathText;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// JSON fields' one spelling: nested keys dotted, a key holding a dot or slash in brackets.
class TestJsonFieldNames {

    @Test
    void aFlatKeyHoldingADotIsNeverTheNestedOne() {
        final Map<String, Object> flat = SampleProfiler.flatten(ToolJson.loads(
                "{\"log.level\": \"flat\", \"log\": {\"level\": \"nested\"}, \"labels\": "
                + "{\"app.kubernetes.io/name\": \"web\", \"plain\": 1}, \"o'k.x\": 2, \"[odd\": 3}"));

        assertThat(flat).containsEntry("['log.level']", "flat")
                .containsEntry("log.level", "nested")
                .containsEntry("labels['app.kubernetes.io/name']", "web")
                .containsKeys("labels.plain", "['o''k.x']", "['[odd']");
    }

    @Test
    void aNameIsReadBackIntoItsKeys() {
        assertThat(JsonFieldNames.keys("event.app")).containsExactly("event", "app");
        assertThat(JsonFieldNames.keys("['log.level']")).containsExactly("log.level");
        assertThat(JsonFieldNames.keys("labels['app.kubernetes.io/name'].x")).containsExactly("labels",
                "app.kubernetes.io/name", "x");
        assertThat(JsonFieldNames.keys("['o''k.x']")).containsExactly("o'k.x");
        assertThat(JsonFieldNames.keys("a['b']['c.d']")).containsExactly("a", "b", "c.d");
        // Not a bracketed key: read as it is.
        assertThat(JsonFieldNames.keys("['open")).containsExactly("['open");
        assertThat(JsonFieldNames.keys("plain")).containsExactly("plain");
        for (final List<String> keys : List.of(List.of("a", "b.c", "d"), List.of("x/y"), List.of("it's.here"),
                List.of("[a", "b"))) {
            assertThat(JsonFieldNames.keys(JsonFieldNames.of(keys))).isEqualTo(keys);
        }
    }

    @Test
    void aNameBelowAFieldIsItsRest() {
        assertThat(JsonFieldNames.below("event", "event.app")).isEqualTo("app");
        assertThat(JsonFieldNames.below("event", "event['a.b']")).isEqualTo("['a.b']");
        assertThat(JsonFieldNames.below("event", "events.app")).isNull();
        assertThat(JsonFieldNames.below("event", "event")).isNull();
        assertThat(JsonFieldNames.join("event", "['a.b']")).isEqualTo("event['a.b']");
        assertThat(JsonFieldNames.join("event", "a")).isEqualTo("event.a");
        assertThat(JsonFieldNames.join("", "a")).isEqualTo("a");
    }

    @Test
    void theGeneratorReadsEachSpellingAsItsKeys() {
        assertThat(XPathText.fieldText("json", "log.level")).isEqualTo("*[@key='log']/*[@key='level']");
        assertThat(XPathText.fieldText("json", "['log.level']")).isEqualTo("*[@key='log.level']");
        assertThat(XPathText.fieldText("json", "labels['a/b']")).isEqualTo("*[@key='labels']/*[@key='a/b']");
    }
}
