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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestElicitationSchema {

    private static final Map<String, Object> FORM = Map.of(
            "type", "object",
            "properties", Map.of(
                    "feed", Map.of("type", "string", "title", "Feed name", "maxLength", 20),
                    "copies", Map.of("type", "integer", "minimum", 1, "maximum", 3),
                    "confirm", Map.of("type", "boolean"),
                    "level", Map.of("type", "string", "enum", List.of("low", "high")),
                    "tags", Map.of("type", "array", "items", Map.of("type", "string", "enum", List.of("a", "b"))),
                    "start", Map.of("type", "string", "format", "date")),
            "required", List.of("feed", "confirm"));

    @Test
    void flatFormsCanBeShown() {
        assertThat(ElicitationSchema.check("Create the feed?", FORM)).isNull();
        assertThat(ElicitationSchema.check("x", Map.of("type", "object", "properties", Map.of(
                "choice", Map.of("type", "string", "oneOf", List.of(Map.of("const", "a", "title", "A")))))))
                .isNull();
    }

    @Test
    void otherFormsAreDeclined() {
        assertThat(ElicitationSchema.check("x", Map.of("type", "array"))).contains("not an object");
        assertThat(ElicitationSchema.check("x", Map.of("type", "object"))).contains("no fields");
        assertThat(ElicitationSchema.check("x", Map.of("type", "object", "properties", Map.of(
                "nested", Map.of("type", "object"))))).contains("'nested'").contains("unsupported type");
        assertThat(ElicitationSchema.check("x", Map.of("type", "object", "properties", Map.of(
                "list", Map.of("type", "array", "items", Map.of("type", "string"))))))
                .contains("not a choice of values");
        assertThat(ElicitationSchema.check("x", Map.of("type", "object", "properties", Map.of(
                "pw", Map.of("type", "string", "format", "password"))))).contains("unsupported format");
        assertThat(ElicitationSchema.check("x".repeat(ElicitationSchema.MAX_MESSAGE_CHARS + 1), FORM))
                .contains("too long");
        final Map<String, Object> many = new HashMap<>();
        for (int i = 0; i <= ElicitationSchema.MAX_FIELDS; i++) {
            many.put("f" + i, Map.of("type", "string"));
        }
        assertThat(ElicitationSchema.check("x", Map.of("type", "object", "properties", many))).contains("more than");
    }

    @Test
    void anAnswerIsConvertedToWhatTheServerExpects() {
        final Map<String, Object> content = ElicitationSchema.content(FORM, Map.of(
                "feed", " ACME-FW ",
                "copies", "2",
                "confirm", "true",
                "level", "high",
                "tags", "[\"a\",\"b\"]",
                "start", "2026-10-15"));

        assertThat(content).containsExactlyInAnyOrderEntriesOf(Map.of(
                "feed", "ACME-FW",
                "copies", 2L,
                "confirm", true,
                "level", "high",
                "tags", List.of("a", "b"),
                "start", "2026-10-15"));
    }

    @Test
    void blankOptionalFieldsAreLeftOut() {
        assertThat(ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "false", "copies", " ")))
                .containsOnlyKeys("feed", "confirm");
    }

    @Test
    void answersThatDoNotFitTheFormAreRefused() {
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("confirm", "true")))
                .hasMessageContaining("Fill in 'Feed name'");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "yes")))
                .hasMessageContaining("true or false");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "true",
                "copies", "9"))).hasMessageContaining("at most 3");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "true",
                "copies", "1.5"))).hasMessageContaining("whole number");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "true",
                "level", "medium"))).hasMessageContaining("not one of the choices");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "true",
                "tags", "[\"c\"]"))).hasMessageContaining("not one of the choices");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x".repeat(21), "confirm",
                "true"))).hasMessageContaining("at most 20 characters");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "true",
                "start", "15/10/2026"))).hasMessageContaining("must be a date");
        assertThatThrownBy(() -> ElicitationSchema.content(FORM, Map.of("feed", "x", "confirm", "true",
                "password", "secret"))).hasMessageContaining("no field 'password'");
    }
}
