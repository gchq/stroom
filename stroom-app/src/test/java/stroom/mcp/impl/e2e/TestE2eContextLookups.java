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

package stroom.mcp.impl.e2e;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Events enriched from the context data each stream part carries, as the user asks: synthetic Windows-style event XML
/// in two parts of one stream, each part with a Context child of event metadata (per provider, event id and version:
/// a description template with %1..%n insertion strings, a keyword mask -> name table, the opcode's name), made by
/// `context/context-lookups-generator.py` (seeded), which also computes what each event should say. The walk: the
/// upload with context, propose_context_lookup (the key found, the outputs previewed, then confirmed), the context
/// loader from build_reference_xslt, the translation with the lookups' key_fields, fill_template and mask_names, the
/// Context reference, a clean step and processing; then every Event holds the description, keyword names and opcode
/// name expected, each part's from its own context (one template is worded differently in each part's context, and
/// a provider's version changes between the parts).
///
/// The JSON variant walks the same records with the same entries as JSON context data (one object wrapping an array of
/// entries, the event id and version JSON numbers), read by a loader whose CombinedParser is of type JSON, and names
/// the keywords from both shapes of JSON table: an array of {mask, name} objects and an object keyed by mask.
@SuppressWarnings("unchecked")
class TestE2eContextLookups extends AbstractE2eTest {
    private static final String NS = "http://schemas.microsoft.com/win/2004/08/events/event";
    private static final List<Map<String, Object>> COMMON = List.of(
            Map.of("path", "EventTime/TimeCreated", "field", "System/TimeCreated/@SystemTime",
                    "time_format", "yyyy-MM-dd'T'HH:mm:ss.SSSX"),
            Map.of("path", "EventSource/System/Name", "value", "E2E"),
            Map.of("path", "EventSource/System/Environment", "value", "Dev"),
            Map.of("path", "EventSource/Generator", "value", "context-lookups"),
            Map.of("path", "EventSource/Device/HostName", "field", "System/Computer"));

    private static final Variant XML = new Variant("e2e-context", "E2E-CONTEXT", "context/context-parts.json", "XML",
            List.of("@Provider", "@EventID", "@Version"), List.of("Keywords/Keyword"), List.of(
            Map.of("path", "EventDetail/Description", "from", "Description", "render", "fill_template",
                    "values", "EventData/Data"),
            Map.of("path", "EventDetail/Alert/Data", "data_name", "KeywordName", "from", "Keywords/Keyword",
                    "render", "mask_names", "mask", "System/Keywords", "mask_field", "@Mask",
                    "name_field", "@Name", "repeat", true),
            Map.of("path", "EventDetail/Alert/Data", "data_name", "OpCodeName", "from", "Opcode",
                    "render", "text")));
    private static final Variant JSON = new Variant("e2e-context-json", "E2E-CONTEXT-JSON",
            "context/context-parts-json.json", "JSON", List.of("provider", "event_id", "version"),
            List.of("keywords", "keyword_names"), List.of(
            Map.of("path", "EventDetail/Description", "from", "description", "render", "fill_template",
                    "values", "EventData/Data"),
            Map.of("path", "EventDetail/Alert/Data", "data_name", "KeywordName", "from", "keywords",
                    "render", "mask_names", "mask", "System/Keywords", "mask_field", "mask",
                    "name_field", "name", "repeat", true),
            Map.of("path", "EventDetail/Alert/Data", "data_name", "OpCodeName", "from", "opcode",
                    "render", "text"),
            Map.of("path", "EventDetail/Alert/Data", "data_name", "KeywordNames", "from", "keyword_names",
                    "render", "mask_names", "mask", "System/Keywords")));

    @Test
    void eachPartsEventsAreDescribedFromItsOwnContext() {
        walk(XML);
    }

    @Test
    void eachPartsEventsAreDescribedFromItsOwnJsonContext() {
        walk(JSON);
    }

    // The walk, upload to processed Events, every Event checked against the generator's expectations.
    private void walk(final Variant variant) {
        final String build = variant.build();
        final String feed = variant.feed();
        final List<Map<String, Object>> parts = JsonUtil.readValue(textResource(variant.parts()), List.class);
        final List<List<Map<String, Object>>> expected = JsonUtil.readValue(
                textResource("context/context-expected.json"), List.class);
        createFeed(build, feed);
        final Map<String, Object> uploaded = json("upload_sample", args("feed", feed, "parts", parts));
        dump("upload_sample", uploaded);
        assertThat(uploaded).containsEntry("parts", 2).containsEntry("context", true);
        final long stream = number(uploaded.get("stream_id"));
        final Map<String, Object> context = json("fetch_events", args("streamId", stream, "childType", "Context"));
        assertThat(strings(context, "available_child_types")).contains("Context");

        // The key: provider, event id and version (the version-changing provider has both versions in each
        // context, and two providers share an event id), matching every record of both parts.
        final Map<String, Object> found = json("propose_context_lookup", args("stream_ids", List.of(stream)));
        dump("propose_context_lookup", found);
        final Map<String, Object> key = list(found, "keys").getFirst();
        assertThat((List<String>) key.get("key_fields")).containsExactlyInAnyOrderElementsOf(variant.keyFields());
        assertThat((List<String>) key.get("event_key_fields")).containsExactlyInAnyOrder("System/Provider/@Name",
                "System/EventID", "System/Version");
        assertThat(key).containsEntry("match_rate", "100%").containsEntry("records", 24);
        assertThat(map(found, "entry_tables")).containsKeys(variant.tables().toArray(String[]::new));

        final List<Map<String, Object>> outputs = variant.outputs();
        final Map<String, Object> shown = json("propose_context_lookup", args("stream_ids", List.of(stream),
                "outputs", outputs));
        dump("propose_context_lookup outputs", shown);
        assertThat(shown).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(shown.get("table"))).contains("24 sample records (100%)", "| Before | After |");
        final List<Map<String, Object>> previews = list(shown, "previews");
        assertThat(previews).hasSize(2);
        for (final Map<String, Object> preview : previews) {
            final Map<String, Object> wanted = expected.get((int) number(preview.get("part")))
                    .get((int) number(preview.get("record")));
            final List<Map<String, Object>> filled = (List<Map<String, Object>>) preview.get("outputs");
            assertThat(filled.get(0).get("after")).as("the preview's description").isEqualTo(
                    List.of(wanted.get("description")));
            assertThat(filled.get(1).get("after")).as("the preview's keyword names").isEqualTo(wanted.get("keywords"));
            if (variant == JSON) {
                assertThat(filled.get(3).get("after")).as("the preview's keyword names, keyed by mask")
                        .isEqualTo(List.of(String.join(", ", (List<String>) wanted.get("keywords"))));
            }
        }

        final Map<String, Object> confirmed = agreed("propose_context_lookup", args("stream_ids", List.of(stream),
                "outputs", outputs, "confirm", true));
        dump("propose_context_lookup confirmed", confirmed);
        assertThat(confirmed).containsEntry("confirmed", true);

        // The context loader: its XSLT from the confirmed reference mapping, a child of the context loader template.
        final Map<String, Object> code = json("build_reference_xslt", args("mapping",
                confirmed.get("reference_mapping"), "build", build));
        assertThat(code).as("reference XSLT: %s", code.get("problems")).containsEntry("ok", true);
        final String loaderXslt = saveXslt(build, feed + "-Context", (String) code.get("xslt"));
        final Map<String, Object> template = map(json("find_reference_data", args()), "context_loader_template");
        assertThat(template).as("Stroom's standard Context Data, found by what it is").isNotNull();
        assertThat(String.valueOf(confirmed.get("hint"))).as("the loader's parser type")
                .contains("combinedParser.type '" + variant.parserType() + "'");
        final String loader = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Context",
                "template_uuid", template.get("uuid"), "set_properties",
                List.of(docProperty("translationFilter", "xslt", loaderXslt, "XSLT"),
                        valueProperty("combinedParser", "type", variant.parserType())))));

        // The loader stepped on its own: each part's Context child stepped in place of its data, as Stroom loads it,
        // every entry a reference.
        final Map<String, Object> loaded = json("step_sample", args("pipeline_uuid", loader, "stream_ids",
                List.of(stream), "child_type", "Context"));
        dump("step_sample context", loaded);
        assertThat(loaded).as("the context stepped clean: %s", loaded.get("groups")).containsEntry("verdict",
                "clean");
        assertThat(number(loaded.get("records_stepped"))).isPositive();
        assertThat(String.valueOf(loaded.get("first_record_output"))).contains("<reference>", "<map>");
        final Map<String, Object> secondPart = json("step_records", args("pipeline_uuid", loader, "child_type",
                "Context", "locations", List.of(Map.of("stream", stream, "part", 1, "record", 0))));
        dump("step_records context", secondPart);
        assertThat(secondPart).as("the second part's context: %s", secondPart.get("groups"))
                .containsEntry("verdict", "clean");
        assertThat(list(secondPart, "records")).singleElement().satisfies(record -> assertThat(record)
                .containsEntry("record", stream + ":1:0").doesNotContainKey("found"));

        // The translation, its lookups keyed as the loader keys the map.
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", "event");
        final List<Object> fields = new ArrayList<>(List.of(
                Map.of("path", "EventDetail/TypeId", "field", "System/EventID"),
                Map.of("path", "EventDetail/Alert/Type", "value", "Other"),
                Map.of("path", "EventDetail/Alert/Data", "data_name", "EventRecordID",
                        "field", "System/EventRecordID")));
        fields.addAll(list(confirmed, "mapping_entries"));
        rule.put("fields", fields);
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "xml");
        mapping.put("xml_namespace", NS);
        mapping.put("root", "Events");
        mapping.put("record", "Event");
        mapping.put("common", COMMON);
        mapping.put("events", List.of(rule));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", mapping,
                "stream_ids", List.of(stream), "build", build, "name", feed + "-Events", "schema_version", VERSION));
        dump("build_translation_xslt", saved);
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        assertThat(String.valueOf(saved.get("warnings"))).as("the lookup's key and the loader's agree")
                .doesNotContain("makes its key of");

        final Map<String, Object> reference = new LinkedHashMap<>(map(confirmed, "reference"));
        reference.put("loader_pipeline", loader);
        final String events = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (XML)"), "references", List.of(reference))));
        final Map<String, Object> stepped = stepSample(events, List.of(stream));
        dump("step_sample", stepped);
        assertThat(stepped).as("stepped clean: %s", stepped.get("groups")).containsEntry("verdict", "clean");

        final Map<String, Object> done = process(events, List.of(stream));
        dump("processed", done);
        assertThat(done).containsEntry("gate", "pass");
        final long eventsStream = number(((List<?>) list(done, "streams").getFirst().get("events")).getFirst());
        final Map<String, Map<String, Object>> byRecord = new LinkedHashMap<>();
        expected.forEach(part -> part.forEach(record -> byRecord.put((String) record.get("record_id"), record)));
        final List<String> checked = new ArrayList<>();
        int first = 0;
        while (first < byRecord.size()) {
            final Map<String, Object> read = json("fetch_events", args("streamId", eventsStream, "firstRecord", first,
                    "recordCount", 10));
            final List<?> records = (List<?>) read.get("records");
            if (records.isEmpty()) {
                break;
            }
            for (final Object record : records) {
                final String text = record instanceof final Map<?, ?> fieldsOf
                        ? String.valueOf(fieldsOf.get("data"))
                        : String.valueOf(record);
                for (final Element event : children(parse(text).getDocumentElement(), "Event")) {
                    checked.add(check(event, byRecord, variant == JSON));
                }
            }
            first += records.size();
        }
        assertThat(checked).as("every event checked").containsExactlyInAnyOrderElementsOf(byRecord.keySet());
    }

    // An Event against what the generator expects of its record: the description rendered, the keyword names, the
    // opcode's name; and, from the JSON table keyed by mask, the keyword names joined.
    private static String check(final Element event,
                                final Map<String, Map<String, Object>> byRecord,
                                final boolean keyedNames) {
        final Map<String, List<String>> data = new LinkedHashMap<>();
        for (final Element found : descendants(event, "Data")) {
            data.computeIfAbsent(found.getAttribute("Name"), ignored -> new ArrayList<>())
                    .add(found.getAttribute("Value"));
        }
        final String recordId = data.get("EventRecordID").getFirst();
        final Map<String, Object> wanted = byRecord.get(recordId);
        assertThat(wanted).as("record %s expected", recordId).isNotNull();
        assertThat(descendants(event, "Description").getFirst().getTextContent()).as("record %s's description",
                recordId).isEqualTo(wanted.get("description"));
        assertThat(data.get("KeywordName")).as("record %s's keyword names", recordId)
                .isEqualTo(wanted.get("keywords"));
        assertThat(data.get("OpCodeName")).as("record %s's opcode name", recordId)
                .containsExactly((String) wanted.get("opcode"));
        if (keyedNames) {
            assertThat(data.get("KeywordNames")).as("record %s's keyword names, keyed by mask", recordId)
                    .containsExactly(String.join(", ", (List<String>) wanted.get("keywords")));
        }
        return recordId;
    }


    // --------------------------------------------------------------------------------


    // One variant of the walk: its build and feed, its parts, the loader's parser type, the key's context fields, the
    // entry tables and the outputs proposed.
    private record Variant(String build,
                           String feed,
                           String parts,
                           String parserType,
                           List<String> keyFields,
                           List<String> tables,
                           List<Map<String, Object>> outputs) {

    }
}
