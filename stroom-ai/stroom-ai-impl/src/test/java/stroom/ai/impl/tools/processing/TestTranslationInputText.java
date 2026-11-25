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

package stroom.ai.impl.tools.processing;

import stroom.ai.impl.tools.processing.TranslationInputText.Format;
import stroom.ai.impl.tools.processing.TranslationInputText.Text;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A translation's input, as stepping captures it a record at a time, made into one text a stream: XML of JSON back
/// into JSON, Stroom's records into JSON objects of their data, any other XML merged under one root.
class TestTranslationInputText {

    private static final String JSON = "xmlns=\"http://www.w3.org/2013/XSL/json\"";

    @Test
    void xmlOfJsonIsReadBackIntoTheJsonTheTranslationReads() {
        final Text text = TranslationInputText.of(List.of(
                "<array " + JSON + "><map><string key=\"action\">login</string><number key=\"n\">3</number>"
                + "<boolean key=\"ok\">true</boolean><null key=\"gone\"/><array key=\"tags\"><string>a</string>"
                + "</array><map key=\"inner\"><number key=\"x\">1.5</number></map></map></array>",
                "<map " + JSON + "><string key=\"action\">logout</string></map>")).orElseThrow();

        assertThat(text.format()).isEqualTo(Format.JSON);
        assertThat(text.documents()).isEqualTo(2);
        final List<?> records = (List<?>) ToolJson.loads(text.text());
        assertThat(records).hasSize(2);
        final Map<?, ?> first = (Map<?, ?>) records.getFirst();
        assertThat(first.get("action")).isEqualTo("login");
        assertThat(((Number) first.get("n")).longValue()).isEqualTo(3L);
        assertThat(first.get("ok")).isEqualTo(true);
        assertThat(first.containsKey("gone")).isTrue();
        assertThat(first.get("gone")).isNull();
        assertThat(first.get("tags")).isEqualTo(List.of("a"));
        assertThat(((Number) ((Map<?, ?>) first.get("inner")).get("x")).doubleValue()).isEqualTo(1.5);
        assertThat(((Map<?, ?>) records.get(1)).get("action")).isEqualTo("logout");
    }

    @Test
    void theSameDocumentSteppedForEveryRecordIsReadOnce() {
        final String whole = "<array " + JSON + "><map><string key=\"a\">1</string></map>"
                             + "<map><string key=\"a\">2</string></map></array>";

        final Text text = TranslationInputText.of(List.of(whole, whole, " " + whole + "\n")).orElseThrow();

        assertThat(text.documents()).isEqualTo(1);
        assertThat((List<?>) ToolJson.loads(text.text())).hasSize(2);
    }

    @Test
    void stroomsRecordsAreReadAsObjectsOfTheirData() {
        final Text text = TranslationInputText.of(List.of(
                "<records xmlns=\"records:2\"><record><data name=\"action\" value=\"login\"/>"
                + "<data name=\"user\"><data name=\"id\" value=\"jb\"/></data></record></records>")).orElseThrow();

        assertThat(text.format()).isEqualTo(Format.JSON);
        assertThat(ToolJson.loads(text.text())).isEqualTo(List.of(Map.of("action", "login", "user",
                Map.of("id", "jb"))));
    }

    @Test
    void otherXmlIsMergedUnderTheFirstDocumentsRoot() {
        final Text text = TranslationInputText.of(List.of("<log xmlns=\"acme:1\"><entry n=\"1\"/></log>",
                "<log xmlns=\"acme:1\"><entry n=\"2\"/></log>")).orElseThrow();

        assertThat(text.format()).isEqualTo(Format.XML);
        assertThat(text.text()).contains("<entry n=\"1\"/>", "<entry n=\"2\"/>").startsWith("<log");
    }

    @Test
    void nothingThatParsesGivesNoText() {
        assertThat(TranslationInputText.of(Arrays.asList(null, "", "not xml"))).isEmpty();
        assertThat(TranslationInputText.of(List.of())).isEmpty();
    }
}
