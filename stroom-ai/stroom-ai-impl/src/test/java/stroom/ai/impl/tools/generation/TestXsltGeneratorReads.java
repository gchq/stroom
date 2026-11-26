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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.pipeline.xml.converter.json.JSONParserFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import static org.assertj.core.api.Assertions.assertThat;

/// Found in the audit of 2026-11-26: the draft's own view of a record's fields (the Data entries it drafts, each naming
/// a field the sample's records hold with a value) against what the generated XSLT, run by Saxon over the same records
/// as Stroom's parsers present them, writes. Each test fails where the two disagree: the draft writes a Data for a
/// field with values, and the Events hold none.
class TestXsltGeneratorReads extends XsltRuns {

    // An array of objects held in JSON text: the draft writes a Data group over its items (for_each array).
    @Test
    void anArrayOfObjectsInHeldJsonIsWrittenAsTheDraftsGroupSays() {
        final List<String> records = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Map<String, Object> held = new LinkedHashMap<>();
            held.put("user", "u" + i);
            held.put("files", List.of(Map.of("name", "a" + i + ".txt"), Map.of("name", "b" + i + ".txt")));
            records.add(record(i, "msg", ToolJson.dumps(held)));
        }
        final Map<String, Object> mapping = drafted(records);
        assertThat(String.valueOf(mapping.get("events"))).as("the draft's group over the held array")
                .contains("for_each={array=msg.files}");

        final XmlElement event = events(mapping, records).getFirst();

        // The draft's local view: two items, each a Data named by its key.
        assertThat(having(event, EVT, "Data", "Name", "msg.files")).as(XmlSerialiser.toXml(event)).hasSize(2);
    }

    // An object nested more than four levels deep in the record (not held as text): the profiler flattened only four
    // levels, so the fourth level's object was taken as JSON held in a field.
    @Test
    void aKeyNestedFiveLevelsDeepIsWrittenAsTheDraftsDataSays() {
        final List<String> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final Map<String, Object> l4 = Map.of("l5", "five-" + i);
            records.add(record(i, "deep", Map.of("l1", Map.of("l2", Map.of("l3", Map.of("l4", l4))))));
        }
        final Map<String, Object> mapping = drafted(records);
        assertThat(dataNames(mapping)).as("the draft's Data").contains("deep.l1.l2.l3.l4.l5");

        final XmlElement event = events(mapping, records).getFirst();

        assertThat(values(event, "deep.l1.l2.l3.l4.l5")).as(XmlSerialiser.toXml(event)).containsExactly("five-0");
    }

    // A top-level key that is empty (""): the profiler named it as a value that isn't an object, "(value)".
    @Test
    void anEmptyTopLevelKeyIsWrittenAsTheDraftsDataSays() {
        final List<String> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            records.add(record(i, "", "empty-key-" + i));
        }
        final Map<String, Object> mapping = drafted(records);
        final List<String> drafted = dataNames(mapping);
        assertThat(drafted).as("the draft writes a Data for the key").hasSize(1);

        final XmlElement event = events(mapping, records).getFirst();

        assertThat(values(event, drafted.getFirst())).as(XmlSerialiser.toXml(event)).containsExactly("empty-key-0");
    }

    // A key holding `['...']` but no dot, e.g. `headers['host']` as some exporters flatten maps.
    @Test
    void aKeyHoldingBracketsIsWrittenAsTheDraftsDataSays() {
        final List<String> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            records.add(record(i, "headers['host']", "h" + i));
        }
        final Map<String, Object> mapping = drafted(records);
        // The key spelt in brackets, as a key holding a dot is, so keys() reads it back whole.
        final String name = JsonFieldNames.of(List.of("headers['host']"));
        assertThat(dataNames(mapping)).as("the draft's Data").contains(name);

        final XmlElement event = events(mapping, records).getFirst();

        assertThat(values(event, name)).as(XmlSerialiser.toXml(event)).containsExactly("h0");
    }

    // A Data Splitter name holding a slash (a CSV heading `src/ip`): the draft reads it as one field, and the
    // generator's field text read it as a data element nested in another.
    @Test
    void aSplitterNameHoldingASlashIsWrittenAsTheDraftsDataSays() {
        final String csv = """
                ts,action,src/ip
                2026-10-01T08:00:00Z,login,10.0.0.1
                2026-10-01T08:01:00Z,login,10.0.0.2
                2026-10-01T08:02:00Z,login,10.0.0.3
                """;
        final Map<String, Object> draft = DraftMapping.draft(List.of(csv), "App", "App", "Test", null, null, null);
        final Map<String, Object> mapping = ready(mapOf(draft.get("mapping")));
        // The draft reads src/ip as one field, its address placed as the client's.
        assertThat(JsonValues.maps(mapping.get("common"))).as(ToolJson.dumps(mapping))
                .contains(Map.of("path", "EventSource/Client/IPAddress", "field", "src/ip"));
        final String records = "<records xmlns=\"records:2\"><record><data name=\"ts\" value=\"2026-10-01T08:00:00Z\"/>"
                               + "<data name=\"action\" value=\"login\"/><data name=\"src/ip\" value=\"10.0.0.1\"/>"
                               + "</record></records>";

        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(mapping),
                SCHEMA, "4.1.0");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        final XmlElement event = children(transform(generated.xslt(), records), "Event").getFirst();

        assertThat(descendant(event, "Client")).as(XmlSerialiser.toXml(event)).isNotNull();
        assertThat(text(descendant(event, "Client"), "IPAddress")).as(XmlSerialiser.toXml(event))
                .isEqualTo("10.0.0.1");
    }

    // An xpath stepping into a field holding JSON text as if it were the record's own object: XPathFieldReads named
    // the held key it means (msg.ref) as read, so its Data was dropped, but the XSLT, reading the record's tree
    // (where msg is text), finds nothing there: the value is in neither the element nor its own Data.
    @Test
    void aHeldKeyAnXpathReadsWithoutJsonToXmlIsNotTakenAsWritten() {
        final List<String> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            records.add(record(i, "msg", ToolJson.dumps(Map.of("ref", "rpt-" + i, "note", "n" + i))));
        }
        final String xpath = "*[@key='msg']/*[@key='ref']";
        assertThat(XPathFieldReads.fields(xpath)).as("XPathFieldReads' view").containsExactly("msg.ref");
        final Map<String, Object> type = new LinkedHashMap<>();
        type.put("type_id", "View-Report");
        type.put("title", "Report viewed");
        type.put("event_detail", "View");
        type.put("recognised_by", List.of(Map.of("when", List.of(Map.of("field", "action", "equals", "login")),
                "names", List.of())));
        type.put("basis", "documentation");
        type.put("confidence", "high");
        type.put("status", "confirmed");
        type.put("placements", List.of(Map.of("path", "Document/Id", "xpath", xpath)));
        final Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("source", "App");
        notes.put("fields", new ArrayList<>());
        notes.put("event_types", List.of(type));
        notes.put("catch_all", Map.of("status", "confirmed"));
        final Map<String, Object> mapping = ready(mapOf(DraftMapping.draft(List.of("[" + String.join(",", records)
                + "]"), "App", "App", "Test", notes, null, null).get("mapping")));
        assertThat(mapping.get("json_fields")).as(ToolJson.dumps(mapping)).isEqualTo(List.of("msg"));

        final XmlElement event = events(mapping, records).getFirst();

        final List<String> written = new ArrayList<>(values(event, "msg.ref"));
        final XmlElement document = descendant(event, "Document");
        if (document != null) {
            written.add(text(document, "Id"));
        }
        assertThat(written).as("msg.ref as Document/Id or its own Data: " + XmlSerialiser.toXml(event) + "\n"
                               + ToolJson.dumps(mapping)).contains("rpt-0");
    }

    // --------------------------------------------------------------------------------


    // A JSON record: its time, action and the given key.
    private static String record(final int n, final String key, final Object value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("time", "2026-10-01T08:0" + n + ":00Z");
        out.put("action", "login");
        out.put(key, value);
        return ToolJson.dumps(out);
    }

    // The draft of the records (a JSON array), made ready to build: a device and a user given, Unknown kept.
    private static Map<String, Object> drafted(final List<String> records) {
        final Map<String, Object> draft = DraftMapping.draft(List.of("[" + String.join(",", records) + "]"), "App",
                "App", "Test", null, null, null);
        return ready(mapOf(draft.get("mapping")));
    }

    private static Map<String, Object> ready(final Map<String, Object> drafted) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        final List<Object> common = new ArrayList<>(JsonValues.maps(mapping.get("common")));
        common.add(Map.of("path", "EventSource/Device/HostName", "value", "h1"));
        mapping.put("common", common);
        final List<Object> rules = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final Map<String, Object> kept = new LinkedHashMap<>(rule);
            final List<Object> fields = new ArrayList<>(JsonValues.maps(rule.get("fields")));
            if (String.valueOf(fields).contains("Authenticate")) {
                fields.add(Map.of("path", "EventDetail/Authenticate/User/Id", "value", "u"));
            } else {
                kept.put("allow_unknown", "kept for the audit");
            }
            kept.put("fields", fields);
            rules.add(kept);
        }
        mapping.put("events", rules);
        return mapping;
    }

    private static List<XmlElement> events(final Map<String, Object> mapping, final List<String> records) {
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(mapping),
                SCHEMA, "4.1.0");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        final List<XmlElement> out = children(transform(generated.xslt(),
                stroomXml("[" + String.join(",", records) + "]")), "Event");
        assertThat(out).hasSize(records.size());
        return out;
    }

    // The names of the Data the first rule's fields write.
    private static List<String> dataNames(final Map<String, Object> mapping) {
        return JsonValues.maps(JsonValues.maps(mapping.get("events")).getFirst().get("fields")).stream()
                .filter(field -> field.get("data_name") != null)
                .map(field -> String.valueOf(field.get("data_name")))
                .toList();
    }

    private static List<String> values(final XmlElement event, final String name) {
        return having(event, EVT, "Data", "Name", name).stream().map(found -> found.get("Value")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(final Object value) {
        return (Map<String, Object>) value;
    }

    // JSON as XML, as Stroom's JSON reader writes it.
    private static String stroomXml(final String json) {
        try {
            final StringWriter out = new StringWriter();
            TransformerFactory.newInstance().newTransformer().transform(new SAXSource(new JSONParserFactory()
                    .getParser(), new InputSource(new StringReader(json))), new StreamResult(out));
            return out.toString();
        } catch (final TransformerException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
