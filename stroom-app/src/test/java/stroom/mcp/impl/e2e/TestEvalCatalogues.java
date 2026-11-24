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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheck;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.CatalogueSample;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class TestEvalCatalogues {

    private static final Pattern VERB_OBJECT = Pattern.compile("[A-Z][A-Za-z]*-[A-Z][A-Za-z0-9]*");
    // TODO phase 1 (lane A): the cases whose reference rules use `not` or the `catch_all` flag, which the mapping
    //  reads once the event type catalogue's conditions land; then this set goes.
    private static final Set<String> AWAITING_GENERATOR = Set.of("19_jsonl_message_layouts",
            "35_xml_fragments_secretserver_messages", "36_xml_fragments_secretserver_whole_export",
            "58_jsonl_pega_wrapper");

    @Test
    void everyBuildCaseHasAReferenceCatalogueAsTheContractHasIt() {
        int built = 0;
        for (final String id : EvalCases.IDS) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            final Map<String, Object> catalogue = EvalCatalogues.reference(evalCase);
            if (EvalCases.isQuestion(evalCase)) {
                assertThat(catalogue).as(id).isNull();
                continue;
            }
            built++;
            assertThat(catalogue).as(id).containsKeys("event_types", "catch_all");
            assertThat((Map<String, Object>) catalogue.get("catch_all")).as(id).containsEntry("status", "confirmed");
            final List<Map<String, Object>> types = (List<Map<String, Object>>) catalogue.get("event_types");
            assertThat(types).as(id).isNotEmpty();
            final Set<String> typeIds = new HashSet<>();
            final long real = types.stream().filter(type -> !Boolean.TRUE.equals(type.get("drop"))).count();
            for (final Map<String, Object> type : types) {
                final String typeId = (String) type.get("type_id");
                assertThat(typeIds.add(typeId)).as("%s: %s is one event type", id, typeId).isTrue();
                assertThat(typeId).as(id).matches(VERB_OBJECT);
                assertThat((String) type.get("description")).as("%s %s", id, typeId).isNotBlank();
                assertThat(type).as("%s %s", id, typeId).containsEntry("status", "confirmed");
                // Network's action element is its child (Network/Connect), as the schema has it.
                assertThat(String.valueOf(type.get("event_detail"))).as("%s %s", id, typeId).isNotEqualTo("Network");
                final List<Map<String, Object>> ways = (List<Map<String, Object>>) type.get("recognised_by");
                assertThat(ways).as("%s %s", id, typeId).isNotEmpty();
                // A sole event type may be recognised by every record; any other is recognised by a condition.
                if (real > 1) {
                    assertThat(ways).as("%s %s is recognised by a condition", id, typeId)
                            .allSatisfy(way -> assertThat((List<?>) way.get("when")).isNotEmpty());
                }
                // The source type field is never in a condition.
                if (catalogue.get("scope") instanceof final Map<?, ?> scope) {
                    final String field = String.valueOf(scope.get("source_type_field"));
                    assertThat(ways).as("%s %s tests the source type field %s", id, typeId, field)
                            .allSatisfy(way -> assertThat((List<Map<String, Object>>) way.get("when"))
                                    .noneMatch(condition -> field.equals(condition.get("field"))));
                }
            }
        }
        // Every case but the ten question cases (62 to 64, 71 to 75, 77 and 78) builds a translation.
        assertThat(built).isEqualTo(EvalCases.IDS.size() - 10);
    }

    @Test
    void aTypeWrittenTwoWaysIsOneEventTypeRecognisedTwoWays() {
        final Map<String, Object> logon = type("30_jsonl_two_shapes_idp", "Authenticate-Logon");

        assertThat((List<Map<String, Object>>) logon.get("recognised_by")).extracting(way -> way.get("when"))
                .containsExactly(List.of(Map.of("field", "eventType", "equals", "user.session.start")),
                        List.of(Map.of("field", "type", "equals", "login")));
        assertThat(logon).containsEntry("event_detail", "Authenticate").containsEntry("action", "Logon")
                .containsEntry("title", "New session start");
        assertThat((List<?>) type("14_csv_two_files_variants", "Authenticate-Logoff").get("recognised_by"))
                .hasSize(2);
    }

    @Test
    void theCasesCatalogueSaysWhatTheMappingCannot() {
        // A documented type with no record in the sample; dropped records named as event types.
        assertThat(type("20_csv_coded_with_docs", "Authenticate-PasswordChange"))
                .containsEntry("basis", "documentation")
                .containsEntry("description", "Account password changed");
        assertThat(type("16_json_batches_items", "Process-Heartbeat"))
                .containsEntry("drop", true)
                .containsEntry("description", "heartbeats are not events");
        assertThat(type("16_json_batches_items", "Filter-QATraffic")).containsEntry("drop", true);
        assertThat((List<Map<String, Object>>) EvalCatalogues.reference(EvalCases.load(
                "68_csv_documented_codes_many")).get("event_types")).hasSize(300)
                .allSatisfy(type -> assertThat(type).containsEntry("basis", "documentation"));
        // The catch-all is not an event type.
        assertThat((List<Map<String, Object>>) EvalCatalogues.reference(EvalCases.load(
                "19_jsonl_message_layouts")).get("event_types")).extracting(type -> type.get("type_id"))
                .containsExactly("Authenticate-Logon", "Authenticate-Logoff", "Alert-Tunnel");
        assertThat((Map<String, Object>) EvalCatalogues.reference(EvalCases.load("56_jsonl_azure_audit_aggregated"))
                .get("scope")).containsEntry("source_type_field", "Workload");
    }

    @Test
    void anEventTypesPlacementsAreItsRulesTypedEntriesBelowItsDetailAndTheCommonOnes() {
        // The inbox rule's Name read by its pair; Data, the TypeId, the Description and the fixed Action aside.
        assertThat((List<Map<String, Object>>) type("56_jsonl_azure_audit_aggregated", "Create-InboxRule")
                .get("placements")).containsExactly(
                Map.of("path", "Configuration/Type", "value", "Inbox rule"),
                // One pair of a name/value list read by its name (2026-11-23).
                Map.of("path", "Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "Name",
                        "name_key", "Name", "value_key", "Value")),
                Map.of("path", "User/EmailAddress", "field", "event.MailboxOwnerUPN"));
        // A group kept with its fields.
        assertThat((List<Map<String, Object>>) type("56_jsonl_azure_audit_aggregated", "Send-Mail")
                .get("placements")).extracting(placement -> placement.get("path"))
                .containsExactly("Source/User/EmailAddress", "Source/Device/IPAddress", "Destination/User",
                        "Payload/Email/Type", "Payload/Email/Id", "Payload/Email/From/EmailAddress",
                        "Payload/Email/Subject");
        assertThat(((List<Map<String, Object>>) type("56_jsonl_azure_audit_aggregated", "Send-Mail")
                .get("placements")).get(2))
                .containsEntry("for_each", Map.of("array", "event.Item.Recipients"))
                .containsEntry("fields", List.of(Map.of("path", "EmailAddress", "item", true)));
        // The user authenticating and the outcome written in common, for each Authenticate event.
        assertThat((List<Map<String, Object>>) type("01_csv_vpn", "Authenticate-Logon").get("placements"))
                .containsExactly(Map.of("path", "User/Id", "field", "user"),
                        Map.of("path", "Outcome/Success", "field", "result", "map",
                                Map.of("success", "true", "failure", "false")));
    }

    @Test
    void everyReferenceCatalogueFillsWhatTheSchemaRequiresAndItsPlacementsPassTheCheck() {
        // catalogue_event_types's gate, as TestE2eEvalReference meets it, in seconds: each case's records read as
        // its reference reads them (its own Data Splitter where it has one), its reference catalogue checked against
        // event-logging 4.1.0 with nothing drafted. Every case: no element the schema requires left empty, no
        // placement refused; where the records are read here (a Data Splitter the server infers aside), nothing
        // refused at all.
        final EventSchema schema = EventSchema.parse(resource("event-logging-v4.1.0.xsd"));
        final List<String> refused = new ArrayList<>();
        int read = 0;
        for (final String id : EvalCases.IDS) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            if (EvalCases.isQuestion(evalCase)) {
                continue;
            }
            final Map<String, Object> reference = (Map<String, Object>) evalCase.get("reference");
            final CatalogueSample sample = CatalogueSample.read(reading((Map<String, Object>) reference.get("mapping")),
                    texts(evalCase), splitterOf(reference), LocalCheck.JSON_PARSER_CUT, null);
            final CatalogueCheckResult check = CatalogueCheck.check(Catalogue.fromJson(EvalCatalogues.reference(
                    evalCase)), sample, schema, CatalogueCheck.Drafted.NONE);
            if (!sample.records().isEmpty()) {
                read++;
            }
            check.unplaced().forEach(problem -> refused.add(id + " unplaced: " + problem.problem()));
            check.problems().stream()
                    .filter(problem -> !sample.records().isEmpty() || problem.problem().contains("placement '"))
                    .forEach(problem -> refused.add(id + ": " + problem.problem()));
        }
        assertThat(read).isGreaterThan(40);
        assertThat(refused).isEmpty();
    }

    @Test
    void noTwoEventTypesOfAReferenceCatalogueRecogniseOneSampleRecord() {
        // Each sample record read as the reference mapping reads it, its rule found with the rules in order and in
        // reverse (the catch-all last either way): a record two event types recognise falls into a different type's
        // rule. Cases whose records the mapping can't read here (a converter of its own, an inferred Data Splitter)
        // are left to the catalogue's own check.
        final List<String> overlaps = new ArrayList<>();
        int read = 0;
        for (final String id : EvalCases.IDS) {
            final Map<String, Object> evalCase = EvalCases.load(id);
            final Map<String, Object> reference = (Map<String, Object>) evalCase.get("reference");
            if (EvalCases.isQuestion(evalCase) || AWAITING_GENERATOR.contains(id)
                || (reference.get("splitter") != null && !(reference.get("splitter") instanceof Map<?, ?>))
                || reference.get("converter") != null) {
                continue;
            }
            final Map<String, Object> json = (Map<String, Object>) reference.get("mapping");
            final TranslationMapping forward = TranslationMapping.fromJson(json);
            final TranslationMapping reverse = TranslationMapping.fromJson(reversed(json));
            final SplitterSpec splitter = reference.get("splitter") instanceof final Map<?, ?> spec
                    ? SplitterSpec.fromMap((Map<String, ?>) spec)
                    : null;
            final Map<String, String> typeIds = typeIds(json);
            final List<Object> records = LocalCheck.sampleRecords(forward, texts(evalCase), splitter).records();
            if (!records.isEmpty()) {
                read++;
            }
            for (int n = 0; n < records.size(); n++) {
                final Object record = records.get(n);
                final String first = LocalCheck.ruleOf(forward, record, LocalCheck.extractedValues(forward, record));
                final String last = LocalCheck.ruleOf(reverse, record, LocalCheck.extractedValues(reverse, record));
                if (first != null && last != null && !Objects.equals(typeIds.get(first), typeIds.get(last))) {
                    overlaps.add(id + " record " + n + ": " + first + " and " + last);
                }
            }
        }
        assertThat(read).isGreaterThan(10);
        assertThat(overlaps).isEmpty();
    }

    // How the reference mapping reads a record, as the eval hands catalogue_event_types its reading: its reading keys,
    // with one rule.
    private static TranslationMapping reading(final Map<String, Object> mapping) {
        final Map<String, Object> reading = new LinkedHashMap<>();
        for (final String key : List.of("input", "root", "record", "xml_namespace", "json_layout", "json_fields",
                "xml_fields", "pairs", "extract", "nil_values", "for_each")) {
            if (mapping.get(key) != null) {
                reading.put(key, mapping.get(key));
            }
        }
        reading.putIfAbsent("input", "data_splitter");
        reading.put("events", List.of(Map.of("name", "reading", "fields", List.of(Map.of("path",
                "EventDetail/TypeId", "value", "Reading")))));
        return TranslationMapping.parse(reading);
    }

    // The Data Splitter the case's records are read with: its spec, its own converter (the CSV header splitter for
    // csv_header), or none (one the server infers or profiles, not read here).
    private static SplitterSpec splitterOf(final Map<String, Object> reference) {
        if (reference.get("splitter") instanceof final Map<?, ?> spec) {
            return SplitterSpec.fromMap((Map<String, ?>) spec);
        }
        if (!"DATA_SPLITTER".equals(reference.getOrDefault("converter_type", "DATA_SPLITTER"))
            || !(reference.get("converter") instanceof final String converter)) {
            return null;
        }
        if ("csv_header".equals(converter)) {
            return SplitterSpec.ofConverter(resource("csv_splitter.xml"));
        }
        return converter.strip().startsWith("<")
                ? SplitterSpec.ofConverter(converter)
                : null;
    }

    private static String resource(final String name) {
        try (final InputStream in = TestEvalCatalogues.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No test resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The mapping with its rules in reverse, the catch-all's kept last.
    private static Map<String, Object> reversed(final Map<String, Object> json) {
        final List<Map<String, Object>> rules = new ArrayList<>((List<Map<String, Object>>) json.get("events"));
        final List<Map<String, Object>> catchAll = rules.stream()
                .filter(rule -> rule.get("when") == null || ((List<?>) rule.get("when")).isEmpty())
                .toList();
        rules.removeAll(catchAll);
        Collections.reverse(rules);
        rules.addAll(catchAll);
        final Map<String, Object> copy = new LinkedHashMap<>(json);
        copy.put("events", rules);
        return copy;
    }

    // Each rule's TypeId (its fixed value), by the rule's name; a drop rule its reason.
    private static Map<String, String> typeIds(final Map<String, Object> json) {
        final Map<String, String> typeIds = new LinkedHashMap<>();
        for (final Map<String, Object> rule : (List<Map<String, Object>>) json.get("events")) {
            ((List<Map<String, Object>>) rule.get("fields")).stream()
                    .filter(field -> "EventDetail/TypeId".equals(field.get("path")))
                    .findFirst()
                    .ifPresent(field -> typeIds.put((String) rule.get("name"), String.valueOf(field.get("value"))));
        }
        return typeIds;
    }

    // The case's records as text: its later data, its files' text, or its samples.
    private static List<String> texts(final Map<String, Object> evalCase) {
        if (!EvalCases.later(evalCase).isEmpty()) {
            return EvalCases.later(evalCase);
        }
        final List<EvalCases.SampleFile> files = EvalCases.files(evalCase);
        return files.isEmpty()
                ? EvalCases.samples(evalCase)
                : files.stream().map(file -> new String(file.data(), file.charset())).toList();
    }

    private static Map<String, Object> type(final String id, final String typeId) {
        return ((List<Map<String, Object>>) EvalCatalogues.reference(EvalCases.load(id)).get("event_types"))
                .stream()
                .filter(type -> typeId.equals(type.get("type_id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " has no event type " + typeId));
    }
}
