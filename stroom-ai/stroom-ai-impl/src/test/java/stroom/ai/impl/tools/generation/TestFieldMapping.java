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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A mapping entry's max_length: the value cut to that many characters after any transform. Asked for in chat 182:
/// the user chose to cut message bodies over 10,000 characters, and the agent, told to cap them with an xpath, wrote
/// `substring(event.Body.Content, 1, 10000)`, which selects nothing for JSON input.
class TestFieldMapping extends XsltRuns {

    private static final String LONG = "abcdefghij".repeat(30);

    @Test
    void maxLengthIsReadAndWrittenBack() {
        final FieldMapping entry = FieldMapping.fromJson(json("""
                {"path": "EventDetail/Send/Data", "data_name": "body", "field": "event.Body.Content",
                 "transform": "strip_control", "max_length": 4000}"""));

        assertThat(entry.maxLength()).isEqualTo(4000);
        assertThat(entry.toJson(DumpMode.COMPACT)).containsEntry("max_length", 4000);
        assertThat(entry.withPath("EventDetail/Receive/Data").maxLength()).isEqualTo(4000);
        assertThat(entry.withDataName("text").withDataParent("message").withDefault("none").maxLength())
                .isEqualTo(4000);
        assertThat(FieldMapping.fromJson(json("""
                {"path": "EventDetail/Send/Data", "data_name": "body", "field": "body"}""")).toJson(DumpMode.COMPACT))
                .doesNotContainKey("max_length");
    }

    @Test
    void maxLengthIsAPositiveWholeNumber() {
        for (final String bad : List.of("0", "-5", "\"many\"", "2.5")) {
            assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                    {"path": "EventDetail/Send/Data", "data_name": "body", "field": "body", "max_length": %s}"""
                    .formatted(bad))))
                    .as(bad)
                    .isInstanceOf(MappingValidationException.class)
                    .hasMessageContaining("max_length");
        }
    }

    @Test
    void maxLengthGoesWithAnInputNotAConstantMapOrTime() {
        for (final String bad : List.of(
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"b\", \"value\": \"x\", \"max_length\": 3}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"b\", \"field\": \"b\", "
                + "\"map\": {\"a\": \"b\"}, \"max_length\": 3}",
                "{\"path\": \"EventTime/TimeCreated\", \"field\": \"t\", \"time_format\": \"epoch_ms\", "
                + "\"max_length\": 3}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"b\", \"field\": \"m\", \"max_length\": 3, "
                + "\"mask_names\": {\"names\": {\"0x1\": \"Read\"}}}")) {
            assertThatThrownBy(() -> FieldMapping.fromJson(json(bad)))
                    .as(bad)
                    .isInstanceOf(MappingValidationException.class)
                    .hasMessageContaining("max_length cuts the input's text");
        }
    }

    @Test
    void aGroupEntryRefusesMaxLengthAsItsOtherConversions() {
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventDetail/Send/Data", "data_name": "recipient", "max_length": 10,
                 "for_each": {"split": "to", "on": ","},
                 "fields": [{"path": "Data", "data_name": "id", "item": true}]}""")))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("it has no input of its own, so no max_length");
    }

    @Test
    void theGeneratedXsltCutsTheValueAfterItsTransform() {
        final Map<String, Object> mapping = baseJson();
        final List<Map<String, Object>> fields = new ArrayList<>(LOGON);
        fields.add(json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "note", "field": "note",
                 "transform": "upper", "max_length": 12}"""));
        fields.add(json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "tag", "field": "tag", "repeat": true,
                 "max_length": 2}"""));
        mapping.put("events", List.of(rule("logon", List.of(equalsCondition("action", "login")), fields),
                otherRule()));
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(mapping),
                SCHEMA, "4.1.0");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        assertThat(generated.xslt()).contains("substring(upper-case(", ", 1, 12)", ", 1, 2)");

        final String records = "<records xmlns=\"records:2\"><record><data name=\"time\" "
                               + "value=\"2026-09-28T10:00:00.000Z\"/><data name=\"action\" value=\"login\"/>"
                               + "<data name=\"user\" value=\"amy\"/><data name=\"note\" value=\"" + LONG + "\"/>"
                               + "<data name=\"tag\" value=\"red\"/><data name=\"tag\" value=\"blue\"/>"
                               + "</record></records>";
        final XmlElement events = transform(generated.xslt(), records);

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement event = children(events, "Event").getFirst();
        assertThat(having(find(event, "EventDetail/Authenticate"), EVT, "Data", "Name", "note"))
                .extracting(data -> data.get("Value")).containsExactly("ABCDEFGHIJAB");
        assertThat(having(find(event, "EventDetail/Authenticate"), EVT, "Data", "Name", "tag"))
                .extracting(data -> data.get("Value")).containsExactly("re", "bl");
    }

    @Test
    void aJsonBodyIsCutAsTheUserChose() {
        // The chat 182 shape: a message body below two objects, cut in the mapping, not by an xpath.
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                            {"path": "EventSource/System/Name", "value": "Teams"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Generator", "value": "Teams"},
                            {"path": "EventSource/Device/HostName", "value": "teams"},
                            {"path": "EventDetail/TypeId", "value": "Message"}],
                 "events": [{"name": "message", "allow_unknown": "a test",
                             "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "body",
                                         "field": "event.Body.Content", "max_length": 25},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "items",
                                         "for_each": {"array": "event.Items"},
                                         "fields": [{"path": "Data", "data_name": "item", "item": true,
                                                     "max_length": 3}]}]}]}"""));
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        final String records = "<map xmlns=\"http://www.w3.org/2013/XSL/json\"><map>"
                               + "<string key=\"time\">2026-11-20T00:00:00.000Z</string>"
                               + "<map key=\"event\"><map key=\"Body\"><string key=\"Content\">" + LONG
                               + "</string></map><array key=\"Items\"><string>first</string>"
                               + "<string>second</string></array></map></map></map>";

        final XmlElement events = transform(generated.xslt(), records);

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement unknown = find(children(events, "Event").getFirst(), "EventDetail/Unknown");
        assertThat(having(unknown, EVT, "Data", "Name", "body")).extracting(data -> data.get("Value"))
                .containsExactly(LONG.substring(0, 25));
        assertThat(having(unknown, EVT, "Data", "Name", "item"))
                .extracting(data -> data.get("Value")).containsExactly("fir", "sec");
    }

    @Test
    void anXmlInputsFieldIsCutTheSameWay() {
        final Map<String, Object> mapping = baseJson();
        mapping.put("input", "xml");
        mapping.put("root", "logons");
        mapping.put("record", "logon");
        final List<Map<String, Object>> fields = new ArrayList<>(LOGON);
        fields.add(json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "note", "field": "Body/Note",
                 "max_length": 40}"""));
        mapping.put("events", List.of(rule("logon", List.of(equalsCondition("action", "login")), fields),
                otherRule()));

        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(mapping),
                SCHEMA, "4.1.0");

        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        assertThat(generated.xslt()).containsPattern("substring\\([^)]*Note[^,]*\\[1\\], 1, 40\\)");
    }
}
