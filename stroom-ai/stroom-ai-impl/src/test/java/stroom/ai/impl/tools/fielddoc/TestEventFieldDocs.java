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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The Field mapping section of an events pipeline's documentation, from its translation mapping and the sample's
/// events. Ported from the documentation cases of the Stroom MCP server's `tests/test_xsltgen.py`,
/// `test_fielddoc.py`, `test_extract.py`, `test_multi.py` and `test_draft_reply.py`, and checked against what its
/// `utils/fielddoc.py` writes for the same mappings and events (golden files).
class TestEventFieldDocs {

    private static final String NOT_IN_THE_MAPPING = "not in the mapping";
    private static final Pattern COMMON_DATA = Pattern.compile(
            "\\| `EventDetail/\\*/Data\\[@Name='([^']*)'\\]/@Value` \\|");

    private static final EventSchema SCHEMA = FieldDocFixtures.SCHEMA;

    @ParameterizedTest
    @CsvSource({
            "tables-mapping.json,,by_event_type,tables.md",
            "tables-mapping.json,,compact,tables-compact.md",
            "sampled-mapping.json,sampled-events.xml,by_event_type,sampled.md",
            "sampled-mapping.json,sampled-events.xml,compact,sampled-compact.md",
            "sampled-mapping.json,unmarked-events.xml,by_event_type,unmarked.md",
            "sampled-mapping.json,unmarked-events.xml,compact,unmarked-compact.md",
            "rich-mapping.json,,by_event_type,rich.md",
            "rich-mapping.json,,compact,rich-compact.md",
            "multi-mapping.json,,by_event_type,multi.md",
            "extract-mapping.json,,by_event_type,extract.md",
            "expressions-mapping.json,,by_event_type,expressions.md"})
    void theSectionIsThePythonText(final String mapping, final String events, final String layout,
                                   final String expected) {
        final List<String> outputs = new ArrayList<>();
        if (events != null) {
            outputs.add(FieldDocFixtures.golden("fielddoc/" + events));
            if (events.startsWith("sampled")) {
                // An event the mapping did not write, and an output that doesn't parse.
                outputs.add("<Events xmlns=\"event-logging:3\"><Event><EventDetail><TypeId>x</TypeId><View/>"
                            + "</EventDetail></Event></Events>");
                outputs.add("not xml");
            }
        }
        final String text = EventFieldDocs.fieldMappingMarkdown(golden(mapping), SCHEMA, events == null
                ? null
                : EventFieldDocs.sampledEvents(outputs), layout, null);
        assertThat(withoutCommonData(text)).isEqualTo(withoutCommonData(withoutExtractedFields(
                FieldDocFixtures.golden("fielddoc/" + expected)), text));
    }

    // The Python text without its Extracted fields section, which the port leaves to the XSLT (asked for by the
    // user).
    private static String withoutExtractedFields(final String python) {
        return python.replaceAll("(?s)### Extracted fields\n\n.*?\n\n(?=###|\\z)", "");
    }

    @Test
    void theFieldsAskedForAreThePythons() {
        final Descriptions asking = Descriptions.asking(true);
        EventFieldDocs.fieldMappingMarkdown(golden("sampled-mapping.json"), SCHEMA, EventFieldDocs.sampledEvents(
                List.of(FieldDocFixtures.golden("fielddoc/sampled-events.xml"), "<Events xmlns=\"event-logging:3\">"
                                                                               + "<Event><EventDetail><TypeId>x"
                                                                               + "</TypeId><View/></EventDetail>"
                                                                               + "</Event></Events>")),
                FieldDocs.BY_EVENT_TYPE, asking);
        assertThat(PyJson.dumps(asking.wanted()))
                .isEqualTo(PyJson.dumps(FieldDocFixtures.goldenJson("fielddoc/sampled-wanted.json")));
    }

    @Test
    void fieldMappingTablesShowTheMappingAndTheSample() {
        final TranslationMapping m = golden("tables-mapping.json");
        // From the mapping alone: From says where each value comes from, in schema order.
        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, null, FieldDocs.COMPACT, null);
        final String[] halves = text.split("### Event types");
        final String source = halves[0];
        final String events = halves[1];
        assertThat(source.lines().filter(l -> l.startsWith("| `")).map(l -> l.split(" \\| ")[0]).toList())
                .containsExactly("| `EventTime/TimeCreated`", "| `EventSource/System/Name`",
                        "| `EventSource/System/Environment`", "| `EventSource/Generator`",
                        "| `EventSource/Device/HostName`", "| `EventSource/Client/IPAddress`",
                        "| `EventSource/User/Id`");
        assertThat(source).contains("| `EventSource/System/Name` | The name of the system. | \"Acme VPN\" |",
                "| `host`, or \"unknown\" when empty |", "| `ip` (logon) |");
        assertThat(events).contains("| **logon**<br>`action` in login, logon | \"Logon\" |  | `Authenticate/Action` <- "
                                    + "\"Logon\"<br>`Authenticate/User/Id` <- `user`<br>"
                                    + "`Authenticate/Outcome/Success` <- `result`: ok -> true, fail -> false<br>"
                                    + "`Authenticate/Data[@Name='session']/@Value` <- `sid` |",
                "| **keepalive**<br>`action` = keepalive |  | Left untranslated on purpose |  |",
                "| **other**<br>any other record | `action` |");
    }

    @Test
    void anXsltEditedByHandIsDocumentedAsItRunsByTheRulesOfTheMappingsXslt() {
        // Seen: a hand edit documented from what the mapping generates. The XSLT as it runs gives the values; the
        // mapping's marked XSLT, stepped over the same records, says which rule wrote each event.
        final TranslationMapping m = golden("sampled-mapping.json");
        final String marked = XsltGenerator.generate(m, SCHEMA, "4.1.0", true).xslt();
        final String edited = XsltGenerator.generate(m, SCHEMA, "4.1.0").xslt()
                .replace("<TypeId>Logon</TypeId>", "<TypeId>Logon (edited)</TypeId>");
        assertThat(edited).contains("Logon (edited)");
        final Map<String, String> running = Map.of("7:1", FieldDocFixtures.transform(edited, FieldDocFixtures.RECORDS));
        final Map<String, String> markers = Map.of("7:1", FieldDocFixtures.transform(marked, FieldDocFixtures.RECORDS));

        assertThat(markers.get("7:1")).as("the marked XSLT's output").contains("<?stroom-mcp-rule logon?>");
        final List<XmlElement> events = EventFieldDocs.runningEvents(running, markers);

        assertThat(events).hasSize(2).extracting(EventFieldDocs::ruleOf).containsExactly("logon", "other");
        final String doc = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, events, FieldDocs.BY_EVENT_TYPE, null);
        assertThat(doc).contains("Logon (edited)").doesNotContain("not written by a rule of this mapping");
        // Read alone, the edited event fits no rule (its TypeId isn't the mapping's constant), so it isn't placed.
        assertThat(EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, EventFieldDocs.sampledEvents(List.copyOf(
                running.values())), FieldDocs.BY_EVENT_TYPE, null)).contains("not written by a rule of this mapping");
        // Records that give different numbers of events are left unmarked.
        assertThat(EventFieldDocs.runningEvents(running, Map.of("7:1", "<Events xmlns=\"event-logging:3\"/>")))
                .extracting(EventFieldDocs::ruleOf).containsOnlyNulls();
    }

    @Test
    void sampledFieldMappingMatchesEventsToRulesByTheMarker() {
        final TranslationMapping m = golden("sampled-mapping.json");
        final String marked = XsltGenerator.generate(m, SCHEMA, "4.1.0", true).xslt();
        assertThat(marked).contains("<xsl:processing-instruction name=\"stroom-mcp-rule\">logon"
                                    + "</xsl:processing-instruction>");
        assertThat(XsltGenerator.generate(m, SCHEMA, "4.1.0").xslt()).doesNotContain("stroom-mcp-rule");
        final List<XmlElement> events = EventFieldDocs.sampledEvents(List.of(FieldDocFixtures.transform(marked,
                FieldDocFixtures.RECORDS)));
        final String[] halves = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, events, FieldDocs.COMPACT, null)
                .split("### Event types");
        final String source = halves[0];
        final String text = halves[1];
        // Both halves: the mapping and the values the sample's events got, with which kinds had an element.
        assertThat(source).contains("| `EventSource/System/Name` | The name of the system. | \"Acme VPN\" | "
                                    + "\"Acme VPN\" |", "| `host`, or \"unknown\" when empty | \"unknown\"<br>"
                                                        + "\"ws01\" |",
                "| `user` | \"o'neil\"<br>(logon events only) |", "| `EventSource/Client/IPAddress` | ",
                "| `ip` (logoff) | (not in the sample) |");
        final List<List<String>> rows = text.lines()
                .filter(l -> l.startsWith("| **"))
                .map(l -> List.of(l.split(" \\| ", -1)).subList(0, 4))
                .toList();
        assertThat(rows).containsExactly(
                List.of("| **logon**<br>`action` = login", "\"Logon\"", "`result`<br>\"fail\"", "1 of 2"),
                // TypeId stays LOGON's.
                List.of("| **logoff**<br>`action` = logout", "\"Logon\"", "", "0 of 2<br>(not in the sample)"),
                List.of("| **other**<br>any other record", "`action`<br>\"keepalive\"", "", "1 of 2"));
        assertThat(text).contains("`Authenticate/Action` <- \"Logon\" = \"Logon\"<br>`Authenticate/User/Id` <- "
                                  + "`user` = \"o'neil\"<br>`Authenticate/Outcome/Success` <- `result`: ok -> "
                                  + "true, fail -> false = \"false\"");
        // An event the mapping did not write is reported, not silently dropped.
        final List<XmlElement> withForeign = new ArrayList<>(events);
        withForeign.add(XmlParser.parse("<Event xmlns=\"event-logging:3\"><EventDetail><TypeId>x</TypeId><View/>"
                                        + "</EventDetail></Event>"));
        assertThat(EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, withForeign, FieldDocs.BY_EVENT_TYPE, null))
                .contains("1 of the sample's 3 events were not written by a rule of this mapping");

        // By event type (the default, asked for by the user): the common fields in one table, then a section per
        // event type with a table of its own fields.
        final String[] byType = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, events, FieldDocs.BY_EVENT_TYPE, null)
                .split("### Event types");
        final String common = byType[0];
        final String types = byType[1];
        assertThat(common.strip()).startsWith("### Common fields");
        assertThat(common).contains("| `EventSource/System/Name` | The name of the system. | \"Acme VPN\" | "
                                    + "\"Acme VPN\" |", "| `EventSource/User/Id` | An identifier for the user.");
        // Written by one event type only (logoff's IP), or differently by each: the event type's own.
        assertThat(common).doesNotContain("EventSource/Client/IPAddress", "EventDetail");
        // logon and logoff write roughly the same fields: one section, both named (asked for by the user).
        final String both = types.split("#### logon, logoff")[1].split("####")[0];
        final String other = types.split("#### other")[1].split("####")[0];
        assertThat(both).contains("When `action` = login (logon): 1 of the sample's 2 events. When `action` = logout "
                                  + "(logoff): none in the sample.");
        assertThat(both.lines().filter(r -> r.startsWith("| `")).map(r -> r.split(" \\| ")[0]).limit(2).toList())
                .containsExactly("| `EventDetail/TypeId`", "| `EventDetail/Description`");
        // Each one's source, where they differ; written by one of them: named.
        assertThat(both).contains("| \"Logon\" (logon)<br>\"Logoff\" (logoff) |", "| `ip` (logoff) | (not in the "
                                                                                  + "sample) |",
                "| `EventDetail/Authenticate/Outcome/Success` |", "| \"false\" |");
        assertThat(other).contains("| `EventDetail/Unknown/Data[@Name='action']/@Value` | The `action` value recorded "
                                   + "in a Data element of Unknown. | `action` |");
        // Without a sample: no sample column.
        assertThat(EventFieldDocs.fieldMappingMarkdown(m, SCHEMA)).doesNotContain("Sample values");
    }

    @Test
    void dataInADataParentIsDocumentedUnderItWithItsValues() {
        // Seen: TWITTER's mention and link rows written as Data[@Name='mention'] under Document, not under the
        // mentions and links Data they're in, with no sample values, and the events holding them not counted as
        // their rule's.
        final TranslationMapping m = FieldDocFixtures.mapping(Map.of("events", FieldDocFixtures.json("""
                [{"name": "logon", "fields": [
                   {"path": "EventDetail/TypeId", "value": "Logon"},
                   {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                   {"path": "EventDetail/Authenticate/User/Id", "value": "u"},
                   {"path": "EventDetail/Authenticate/Data", "data_parent": "client", "data_name": "session",
                    "field": "sid"},
                   {"path": "EventDetail/Authenticate/Data", "data_parent": "client", "data_name": "tag",
                    "repeat": true, "xpath": "tokenize(data[@name='tags']/@value, ',')"},
                   {"path": "EventDetail/Authenticate/Data", "data_parent": "client/geo", "data_name": "result",
                    "field": "result"}]}]""")));
        final String records = FieldDocFixtures.RECORDS.replace("<data name=\"sid\" value=\"s1\"/>",
                "<data name=\"sid\" value=\"s1\"/><data name=\"tags\" value=\"vpn,eu\"/>");
        final XsltGenerator.Generated marked = XsltGenerator.generate(m, SCHEMA, "4.1.0", true);
        assertThat(marked.problems()).isEmpty();
        final List<XmlElement> events = EventFieldDocs.sampledEvents(List.of(FieldDocFixtures.transform(
                marked.xslt(), records)));
        assertThat(events).hasSize(2);

        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, events, FieldDocs.BY_EVENT_TYPE, null);

        assertThat(text).contains(
                "| `EventDetail/Authenticate/Data[@Name='client']/Data[@Name='session']/@Value` | ",
                "| `EventDetail/Authenticate/Data[@Name='client']/Data[@Name='tag']/@Value` | ",
                "| `EventDetail/Authenticate/Data[@Name='client']/Data[@Name='geo']/Data[@Name='result']/@Value` | ",
                "| \"s1\" |", "| \"vpn\"<br>\"eu\"<br>(several per event) |", "| \"fail\" |")
                .contains("Every record: 2 of the sample's 2 events.")
                .doesNotContain("not written by a rule", "(not in the sample)", "[@Name='client']/@Value`",
                        NOT_IN_THE_MAPPING);
        // Each Data a row's own key, as the events hold it.
        assertThat(EventFieldDocs.eventValues(events.getFirst(), List.of("EventDetail")))
                .containsEntry(new EventFieldDocs.FieldKey("EventDetail/Authenticate/Data[@Name='client']/Data",
                        "tag"), List.of("vpn", "eu"))
                .doesNotContainKey(new EventFieldDocs.FieldKey("EventDetail/Authenticate/Data", "client"));
    }

    @Test
    void expressionsInTheDocumentationNameFieldsNotSelectors() {
        final String text = EventFieldDocs.fieldMappingMarkdown(golden("expressions-mapping.json"), SCHEMA);
        assertThat(text).contains("| `normalize-space(user)` |", "`concat(action, '-', a/b)` = login-x");
        assertThat(FieldDocs.readable("*[@key='user']/*[@key='name']")).isEqualTo("user.name");
    }

    @Test
    void theExtractionsAreLeftToTheXslt() {
        // test_extract.py has the documentation list them; asked for by the user, the port doesn't: a long list of
        // regexes is more than documentation needs, and the XSLT holds them. The fields they give are still named
        // where they are read.
        final String text = EventFieldDocs.fieldMappingMarkdown(golden("extract-mapping.json"), SCHEMA);
        assertThat(text).startsWith("### Common fields")
                .doesNotContain("Extracted fields", "parsed from")
                .contains("| `EventSource/User/Id` | An identifier for the user. | `user` |");
    }

    @Test
    void eachItemIsAnEventWithRecordFieldsRepeatsAndDrops() {
        // From test_multi.py: the documentation's part.
        final String doc = EventFieldDocs.fieldMappingMarkdown(golden("multi-mapping.json"), SCHEMA);
        assertThat(doc).startsWith("Each record holds several events: one per `events` item.")
                .contains("- `host` (of the record) matches `^qa-`: QA traffic", "one per value");
    }

    @Test
    void allowUnknownOnARuleThatWritesAnActionElementMeansNothing() {
        // From test_draft_reply.py. Seen: a rule moved from Unknown to Alert kept its allow_unknown; the user was
        // asked to keep it Unknown ("none of the 20 records"), and the documentation listed it under Kept as
        // Unknown.
        final TranslationMapping mapping = TranslationMapping.fromJson(FieldDocFixtures.json("""
                {"input": "data_splitter", "common": [
                 {"path": "EventTime/TimeCreated", "field": "timestamp"},
                 {"path": "EventSource/System/Name", "value": "FW"},
                 {"path": "EventSource/System/Environment", "value": "Prod"},
                 {"path": "EventSource/Generator", "value": "FW"},
                 {"path": "EventSource/Device/HostName", "field": "device"},
                 {"path": "EventDetail/TypeId", "field": "action"}],
                 "events": [{"name": "system_alert", "when": [{"field": "event_type", "equals": "SYSTEM"}],
                             "allow_unknown": "System events",
                             "fields": [{"path": "EventDetail/Alert/Type", "value": "Other"}]},
                            {"name": "other", "allow_unknown": "anything else",
                             "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "action",
                                         "field": "action"}]}]}"""));
        assertThat(MappingFacts.keptUnknown(mapping).stream().map(EventRule::name)).containsExactly("other");
        String kept = EventFieldDocs.fieldMappingMarkdown(mapping, SCHEMA).split("### Kept as Unknown", 2)[1]
                .split("###", 2)[0];
        assertThat(kept).contains("`other`").doesNotContain("system_alert");
        // A catch-all the sample never reaches is said to be one, not a decision about the sample (seen: read
        // beside "0 of 1000" in the event types).
        final List<XmlElement> events = XmlParser.parse("<Events xmlns=\"event-logging:3\"><Event><EventDetail>"
                                                        + "<TypeId>START</TypeId><Alert><Type>Other</Type></Alert>"
                                                        + "</EventDetail></Event></Events>")
                .childElements(FieldDocs.EVT, "Event");
        kept = EventFieldDocs.fieldMappingMarkdown(mapping, SCHEMA, events, FieldDocs.BY_EVENT_TYPE, null)
                .split("### Kept as Unknown", 2)[1].split("###", 2)[0];
        assertThat(kept).contains("anything else None of the 1 sampled records reach it: it catches records the "
                                  + "sample doesn't have.");
    }

    @Test
    void aDataNameIsAskedOnceAndASharedRowByItsOwnPath() {
        // Seen (Delinea SecretServer): 224 fields asked, 18 Data names once per action element, and rows of event
        // types shown together asked under the first type's path (Process/Action's values as Authenticate/Action).
        final TranslationMapping m = TranslationMapping.fromJson(FieldDocFixtures.json("""
                {"input": "data_splitter", "unmatched": "skip", "common": [], "events": [
                 {"name": "login", "when": [{"field": "a", "equals": "login"}], "fields": [
                  {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                  {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                  {"path": "EventDetail/Authenticate/Data", "data_name": "Folder", "field": "folder"},
                  {"path": "EventDetail/Authenticate/Data", "data_name": "Result", "field": "result"}]},
                 {"name": "checkout", "when": [{"field": "a", "equals": "checkout"}], "fields": [
                  {"path": "EventDetail/Process/Action", "value": "Access"},
                  {"path": "EventDetail/Process/User/Id", "field": "user"},
                  {"path": "EventDetail/Process/Data", "data_name": "Folder", "field": "folder"},
                  {"path": "EventDetail/Process/Data", "data_name": "Result", "field": "result"}]},
                 {"name": "view", "fields": [{"path": "EventDetail/View/Object/Id", "field": "id"},
                  {"path": "EventDetail/View/Data", "data_name": "Folder", "field": "folder"},
                  {"path": "EventDetail/View/Data", "data_name": "Result", "field": "outcome"}]}]}"""));
        final Descriptions asking = Descriptions.asking(true);
        final String asked = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, null, FieldDocs.BY_EVENT_TYPE, asking);
        assertThat(withoutCommonData(asked)).isEqualTo(withoutCommonData(FieldDocFixtures.golden(
                "fielddoc/data-names.md"), asked));
        // Folder, written alike by every event type, is one common field (not the Python server's Folder per
        // type), asked of once all the same.
        final Map<String, Map<String, Object>> wanted = new LinkedHashMap<>(asking.wanted());
        final Map<String, Object> folder = wanted.remove("EventDetail/*/Data[@Name='Folder']/@Value");
        final Map<String, Object> python = new LinkedHashMap<>(FieldDocFixtures.goldenJson(
                "fielddoc/data-names-wanted.json"));
        python.remove("Data[@Name='Folder']");
        assertThat(PyJson.dumps(wanted)).isEqualTo(PyJson.dumps(python));
        assertThat(folder).containsEntry("from", "`folder`");
        // Result: read from two fields.
        assertThat(asking.wanted().keySet()).containsExactlyInAnyOrder("EventDetail/*/Data[@Name='Folder']/@Value",
                "EventDetail/(Authenticate|Process)/Action",
                "EventDetail/(Authenticate|Process)/Data[@Name='Result']/@Value",
                "EventDetail/(Authenticate|Process)/User/Id", "EventDetail/View/Data[@Name='Result']/@Value",
                "EventDetail/View/Object/Id");
        // One description covers the name everywhere; one by full path wins for its row; an older doc's, kept under
        // the first type's path, still covers the row it was given for.
        final Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("Data[@Name='Folder']", "Folder holding the secret.");
        descriptions.put("EventDetail/View/Data[@Name='Folder']/@Value", "Folder of the secret viewed.");
        descriptions.put("EventDetail/Authenticate/Action", "What the user did.");
        final Descriptions given = new Descriptions(descriptions, true);
        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, null, FieldDocs.BY_EVENT_TYPE, given);
        // Folder, one common field, takes the description given its name.
        assertThat(text).contains(
                "| `EventDetail/*/Data[@Name='Folder']/@Value` | Folder holding the secret. |",
                "| `EventDetail/(Authenticate\\|Process)/Action` | What the user did. |");
        assertThat(given.wanted()).doesNotContainKeys("EventDetail/*/Data[@Name='Folder']/@Value",
                "EventDetail/(Authenticate|Process)/Action");
    }

    @Test
    void dataEveryEventTypeWritesAlikeIsACommonField() {
        // Seen: a Windows event's System/EventID and the like in each of 23 SecretServer rules, repeated in every
        // event type's table, and, outweighing what told the kinds apart, putting them all in one section.
        final TranslationMapping m = TranslationMapping.fromJson(FieldDocFixtures.json("""
                {"input": "data_splitter", "unmatched": "skip", "common": [], "events": [
                 {"name": "login", "when": [{"field": "a", "equals": "login"}], "fields": [
                  {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                  {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                  {"path": "EventDetail/Authenticate/Data", "data_name": "EventID", "field": "id"},
                  {"path": "EventDetail/Authenticate/Data", "data_name": "Level", "field": "level"}]},
                 {"name": "role", "when": [{"field": "a", "equals": "role"}], "fields": [
                  {"path": "EventDetail/Alert/Type", "value": "Other"},
                  {"path": "EventDetail/Alert/Data", "data_name": "EventID", "field": "id"},
                  {"path": "EventDetail/Alert/Data", "data_name": "Level", "field": "level"}]},
                 {"name": "view", "fields": [{"path": "EventDetail/View/Object/Id", "field": "obj"},
                  {"path": "EventDetail/View/Data", "data_name": "EventID", "field": "id"},
                  {"path": "EventDetail/View/Data", "data_name": "Level", "field": "severity"}]}]}"""));

        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, null, FieldDocs.BY_EVENT_TYPE, null);
        final String[] halves = text.split("### Event types");

        assertThat(halves[0]).contains("| `EventDetail/*/Data[@Name='EventID']/@Value` | The `EventID` value "
                                       + "recorded in a Data element of the event's action element. | `id` |");
        // Not read alike by every type (Level from another field in view): each type's own.
        assertThat(halves[0]).doesNotContain("Level");
        assertThat(halves[1]).doesNotContain("EventID")
                .contains("`EventDetail/Authenticate/Data[@Name='Level']/@Value`");
        // Grouped by what tells them apart: each type in its own section.
        assertThat(halves[1].lines().filter(line -> line.startsWith("#### "))).containsExactly("#### login",
                "#### role", "#### view");
    }

    @Test
    void eventTypesAreGroupedByTheirElementsNotTheirData() {
        // Seen: SecretServer rules with the same Data entries and few elements of their own, an Alert's and Process
        // ones in one section. Rules writing only Data (Unknown) are judged on their Data.
        final TranslationMapping m = TranslationMapping.fromJson(FieldDocFixtures.json("""
                {"input": "data_splitter", "unmatched": "skip", "common": [], "events": [
                 {"name": "role", "when": [{"field": "a", "equals": "role"}], "fields": [
                  {"path": "EventDetail/Alert/Type", "value": "Other"},
                  {"path": "EventDetail/Alert/Data", "data_name": "ticket", "field": "ticket"},
                  {"path": "EventDetail/Alert/Data", "data_name": "node", "field": "node"},
                  {"path": "EventDetail/Alert/Data", "data_name": "item", "field": "item"}]},
                 {"name": "launch", "when": [{"field": "a", "equals": "launch"}], "fields": [
                  {"path": "EventDetail/Process/Action", "value": "Execute"},
                  {"path": "EventDetail/Process/Command", "field": "cmd"},
                  {"path": "EventDetail/Process/Data", "data_name": "ticket", "field": "ticket"},
                  {"path": "EventDetail/Process/Data", "data_name": "node", "field": "node"}]},
                 {"name": "start", "when": [{"field": "a", "equals": "start"}], "fields": [
                  {"path": "EventDetail/Process/Action", "value": "Execute"},
                  {"path": "EventDetail/Process/Command", "field": "cmd"}]},
                 {"name": "odd", "when": [{"field": "a", "equals": "odd"}], "fields": [
                  {"path": "EventDetail/Unknown/Data", "data_name": "ticket", "field": "ticket"}]},
                 {"name": "rest", "fields": [
                  {"path": "EventDetail/Unknown/Data", "data_name": "ticket", "field": "ticket"}]}]}"""));

        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, null, FieldDocs.BY_EVENT_TYPE, null);

        assertThat(text.lines().filter(line -> line.startsWith("#### "))).containsExactly("#### role",
                "#### launch, start", "#### odd, rest");
    }

    @Test
    void anObjectIsTheSameFieldWhicheverSideOfTheActionItIsOn() {
        // Seen: SecretServer's folder create (Create/Folder/...) and move (Move/Source/Folder/...) shared no path,
        // each in a section of its own.
        final TranslationMapping m = TranslationMapping.fromJson(FieldDocFixtures.json("""
                {"input": "data_splitter", "unmatched": "skip", "common": [], "events": [
                 {"name": "create", "when": [{"field": "a", "equals": "create"}], "fields": [
                  {"path": "EventDetail/Create/Folder/Id", "field": "id"},
                  {"path": "EventDetail/Create/Folder/Name", "field": "folder"}]},
                 {"name": "move", "when": [{"field": "a", "equals": "move"}], "fields": [
                  {"path": "EventDetail/Move/Source/Folder/Id", "field": "id"},
                  {"path": "EventDetail/Move/Source/Folder/Name", "field": "folder"},
                  {"path": "EventDetail/Move/Destination/Folder/Name", "field": "target"}]},
                 {"name": "grant", "when": [{"field": "a", "equals": "grant"}], "fields": [
                  {"path": "EventDetail/Authorise/Action", "value": "Modify"},
                  {"path": "EventDetail/Authorise/Object/Id", "field": "secret_id"},
                  {"path": "EventDetail/Authorise/Object/Name", "field": "secret"},
                  {"path": "EventDetail/Authorise/Object/Type", "value": "Secret"},
                  {"path": "EventDetail/Authorise/AddGroups/Group/Name", "field": "permission"}]},
                 {"name": "revoke", "when": [{"field": "a", "equals": "revoke"}], "fields": [
                  {"path": "EventDetail/Authorise/Action", "value": "Modify"},
                  {"path": "EventDetail/Authorise/Object/Id", "field": "secret_id"},
                  {"path": "EventDetail/Authorise/Object/Name", "field": "secret"},
                  {"path": "EventDetail/Authorise/Object/Type", "value": "Secret"},
                  {"path": "EventDetail/Authorise/RemoveGroups/Group/Name", "field": "permission"}]},
                 {"name": "start", "when": [{"field": "a", "equals": "start"}], "fields": [
                  {"path": "EventDetail/Network/Connect/Source/Device/IPAddress", "field": "ip"},
                  {"path": "EventDetail/Network/Connect/Destination/Device/HostName", "field": "host"}]},
                 {"name": "end", "when": [{"field": "a", "equals": "end"}], "fields": [
                  {"path": "EventDetail/Network/Close/Source/Device/IPAddress", "field": "ip"},
                  {"path": "EventDetail/Network/Close/Destination/Device/HostName", "field": "host"}]},
                 {"name": "alert", "when": [{"field": "a", "equals": "alert"}], "fields": [
                  {"path": "EventDetail/Alert/Type", "value": "Other"}]}]}"""));

        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, null, FieldDocs.BY_EVENT_TYPE, null);

        // And a permission granted and one revoked, the groups added and removed (seen: SecretServer's, 4 of their
        // 6 fields alike, in sections of their own).
        // And a session's start and end, a Network's Connect and Close (seen: SecretServer's, sharing no path).
        assertThat(text.lines().filter(line -> line.startsWith("#### "))).containsExactly("#### create, move",
                "#### grant, revoke", "#### start, end", "#### alert");
        // Each still shown at its own path.
        assertThat(text).contains("`EventDetail/Create/Folder/Id`", "`EventDetail/Move/Source/Folder/Id`",
                "`EventDetail/Move/Destination/Folder/Name`", "`EventDetail/Authorise/AddGroups/Group/Name`",
                "`EventDetail/Authorise/RemoveGroups/Group/Name`");
    }

    // The Python server's text without the Data rows the port shows once, as common fields (each Data name the
    // port's text has a common row for, in every table), and the port's without its common rows.
    private static String withoutCommonData(final String text) {
        return withoutCommonData(text, text);
    }

    private static String withoutCommonData(final String text, final String port) {
        final Matcher common = COMMON_DATA.matcher(port);
        final Set<String> names = new LinkedHashSet<>();
        while (common.find()) {
            names.add(common.group(1));
        }
        return text.lines()
                .filter(line -> names.stream().noneMatch(name -> line.startsWith("| `EventDetail/")
                                                                 && line.contains("/Data[@Name='" + name
                                                                                  + "']/@Value` |")))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void theVendorsDocumentationComesFirst() {
        // Asked by the user: the vendor's documentation (record_source_notes) still informs every field's
        // description: by the source field it is read from, else (an index or CEF field) by the element the notes
        // put the field in.
        final List<Map<String, Object>> notes = FieldDocFixtures.goldenJson("fielddoc/vendor-notes.json");
        final TranslationMapping m = FieldDocFixtures.mapping();
        final List<XmlElement> events = EventFieldDocs.sampledEvents(List.of(FieldDocFixtures.transform(
                XsltGenerator.generate(m, SCHEMA, "4.1.0", true).xslt(), FieldDocFixtures.RECORDS)));
        final Descriptions asking = new Descriptions(null, true, notes);
        final String text = EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, events, FieldDocs.BY_EVENT_TYPE, asking);
        @SuppressWarnings("unchecked") final Map<String, String> said =
                (Map<String, String>) asking.wanted().get("EventSource/User/Id").get("schemas_say");
        assertThat(said.keySet()).containsExactly("vendor documentation", "event-logging");
        assertThat(said.get("vendor documentation")).isEqualTo("Login of the account that signed in.");
        // The first fallback.
        assertThat(text).contains("| `EventSource/User/Id` | Login of the account that signed in. |");
        @SuppressWarnings("unchecked") final Map<String, String> session = (Map<String, String>) asking.wanted()
                .get("EventDetail/Authenticate/Data[@Name='session']/@Value").get("schemas_say");
        assertThat(session.get("vendor documentation")).isEqualTo("VPN session. Codes: s1: first session.");
        // As the server writes it, from the events it wrote.
        final Descriptions again = new Descriptions(null, true, notes);
        assertThat(EventFieldDocs.fieldMappingMarkdown(m, SCHEMA, EventFieldDocs.sampledEvents(List.of(
                FieldDocFixtures.golden("fielddoc/vendor-events.xml"))), FieldDocs.BY_EVENT_TYPE, again))
                .isEqualTo(FieldDocFixtures.golden("fielddoc/vendor.md"));
        assertThat(PyJson.dumps(again.wanted()))
                .isEqualTo(PyJson.dumps(FieldDocFixtures.goldenJson("fielddoc/vendor-wanted.json")));
    }

    @Test
    void anEventsValuesAreKeyedAsTheMappingKeysThem() {
        final XmlElement event = XmlParser.parse("<Event xmlns=\"event-logging:3\"><!--stroom-mcp rule: logon-->"
                                                 + "<EventSource><User><Id> alice </Id></User><Group><Name>a</Name>"
                                                 + "<Name>b</Name></Group></EventSource><EventDetail><View><Data "
                                                 + "Name=\"x\" Value=\"1\"/><Data Name=\"x\"/></View></EventDetail>"
                                                 + "</Event>");
        final Map<EventFieldDocs.FieldKey, List<String>> values = EventFieldDocs.eventValues(event,
                List.of("EventSource", "EventDetail"));
        assertThat(values).containsEntry(new EventFieldDocs.FieldKey("EventSource/User/Id", null), List.of("alice"))
                .containsEntry(new EventFieldDocs.FieldKey("EventSource/Group/Name", null), List.of("a", "b"))
                .containsEntry(new EventFieldDocs.FieldKey("EventDetail/View/Data", "x"), List.of("1", ""));
        assertThat(EventFieldDocs.ruleOf(event)).isEqualTo("logon");
        assertThat(EventFieldDocs.ruleOf(XmlParser.parse("<Event/>"))).isNull();
    }

    private static TranslationMapping golden(final String name) {
        return TranslationMapping.fromJson(FieldDocFixtures.goldenJson("fielddoc/" + name));
    }
}
