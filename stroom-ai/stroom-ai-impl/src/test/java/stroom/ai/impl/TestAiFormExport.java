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

package stroom.ai.impl;

import stroom.ai.impl.ElicitationRecord.Status;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiFormExport {

    @Test
    void aFormNotAnsweredInTimeShowsItsQuestionsAndChoices() {
        final String json = form(Status.EXPIRED, null);

        assertThat(AiFormExport.body(json)).isEqualTo("""
                From Stroom AI (`ask_user`): not answered in time

                Onboarding Fortigate from its existing data.

                - Which kind of pipeline should the onboarding produce?
                  Choices: A new version (recommended); A new standalone pipeline; Only survey
                - Keep surveying until saturated?
                  Choices: Keep surveying (recommended); Stop now""");
    }

    @Test
    void anAnsweredFormShowsTheAnswers() {
        final String json = form(Status.ACCEPTED,
                Map.of("pipeline", "A new standalone pipeline", "survey", "Stop now"));

        assertThat(AiFormExport.body(json))
                .startsWith("From Stroom AI (`ask_user`): answered")
                .contains("""
                        - Which kind of pipeline should the onboarding produce?
                          Choices: A new version (recommended); A new standalone pipeline; Only survey
                          Answer: A new standalone pipeline
                        - Keep surveying until saturated?
                          Choices: Keep surveying (recommended); Stop now
                          Answer: Stop now""");
    }

    @Test
    void aFieldWithoutChoicesOrATitleIsShownByName() {
        final ElicitationRecord record = new ElicitationRecord(ElicitationRecord.KIND, Status.ACCEPTED, "acme-mcp",
                null, null, Map.of("type", "object", "properties", Map.of("feed", Map.of("type", "string"))),
                0L, Map.of("feed", "ACME-EVENTS"));

        assertThat(AiFormExport.body(JsonUtil.writeValueAsString(record, false))).isEqualTo("""
                From acme-mcp: answered

                - feed
                  Answer: ACME-EVENTS""");
    }

    @Test
    void textThatIsntAFormIsGivenAsItIs() {
        assertThat(AiFormExport.body("not json")).isEqualTo("not json");
        assertThat(AiFormExport.body(null)).isEmpty();
    }

    private static String form(final Status status, final Map<String, Object> answer) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pipeline", Map.of(
                "type", "string",
                "title", "Which kind of pipeline should the onboarding produce?",
                "enum", List.of("A new version", "A new standalone pipeline", "Only survey"),
                "default", "A new version"));
        properties.put("survey", Map.of(
                "type", "string",
                "title", "Keep surveying until saturated?",
                "enum", List.of("Keep surveying", "Stop now"),
                "default", "Keep surveying"));
        final ElicitationRecord record = new ElicitationRecord(ElicitationRecord.KIND, status, "Stroom AI",
                "ask_user", "Onboarding Fortigate from its existing data.",
                Map.of("type", "object", "properties", properties), 0L, answer);
        return JsonUtil.writeValueAsString(record, false);
    }
}
