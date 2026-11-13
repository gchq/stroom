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

import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.mcp.impl.McpTestClient;
import stroom.security.api.SecurityContext;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's source type groups through the REAL tools on a real Stroom: on the PEGA slice, a build scoped
/// to PegaSecurityEvent (a JSON message) and PegaIntegration (SOAP, some bodies truncated) builds a source type group
/// for each with build_translation_xslt; the first makes the feed's common module and helpers from its wrapper
/// entries, and each save sets the registry's rows translated. Each group is the XSLT of a pipeline of its own
/// (create_pipeline, its XSLT set to the group's), Stroom resolving the chain of imports (group, common module,
/// helpers) by UUID: each steps clean with valid Events for its own source types' records only, the rest passed over
/// and counted; an unsaved group is stepped as draft code for its pipeline's XSLT; processing writes each group's
/// Events, logs the counts passed over and the truncated SOAP bodies, and doesn't stop. The samples are the synthetic
/// test resources under `aggregated/` that TestE2eSourceTypes reads (made by aggregated-feeds-generator.py).
@SuppressWarnings("unchecked")
class TestE2eSourceTypeGroups extends AbstractE2eTest {

    // Where the aggregated feeds' synthetic samples are, beside this class.
    static final String SAMPLES = "aggregated/";
    static final String SCOPE_FORM = "source types does this build cover";
    private static final String FEED = "E2E-PEGA-GROUPS";
    private static final String BUILD = "pega-groups";
    private static final List<String> SCOPE = List.of("PegaSecurityEvent", "PegaIntegration");
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "timestamp"},
             {"path": "EventSource/System/Name", "value": "PEGA"},
             {"path": "EventSource/System/Environment", "value": "Production"},
             {"path": "EventSource/Generator", "value": "Pega"},
             {"path": "EventSource/Device/HostName", "field": "hostname"}]""";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void eachGroupIsItsOwnPipelinesXsltTranslatingItsSourceTypesAndPassingTheRestOver() {
        createFeed("pega-groups-src", FEED);
        final String text = lines("pega/pega-1.json", 1500);
        final long stream = upload(FEED, text);
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        client.answeringWith(scopeOf(SCOPE, "for later builds"));
        json("survey_feed", args("feed", FEED, "build", BUILD));
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED,
                "system_name", "PEGA", "environment", "Prod"));
        json("record_source_types", args("build", BUILD, "feed", FEED, "system_name", "PEGA", "environment",
                "Prod", "reviewed", shown.get("review_id")));

        // The first group: the wrapper's entries make the feed's common module; its helpers are made with it.
        final Map<String, Object> security = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "group", "PEGA security", "source_types", List.of("PegaSecurityEvent"),
                "schema_version", VERSION, "stream_ids", List.of(stream), "mapping", securityMapping()));
        dump("build_translation_xslt PEGA security", security);
        assertThat(security).as("the security group saved: %s", security.get("problems")).containsEntry("ok", true);
        final Map<String, Object> first = map(security, "group");
        assertThat(first).containsEntry("mode", "pega_security").doesNotContainKey("dispatcher");
        assertThat(String.valueOf(first.get("notes"))).contains("common module");
        // The sample check read the scope's records: the group's alone.
        assertThat(jsonOf(security.get("scope"))).contains("PegaSecurityEvent");
        final String commonUuid = uuidOf(map(first, "common"));
        final String helpersUuid = uuidOf(map(first, "helpers"));
        final String securityUuid = uuidOf(map(first, "xslt"));

        // The second group reuses the common module.
        final Map<String, Object> integration = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "group", "PEGA integration", "source_types", List.of("PegaIntegration"),
                "schema_version", VERSION, "stream_ids", List.of(stream), "mapping", integrationMapping()));
        dump("build_translation_xslt PEGA integration", integration);
        assertThat(integration).as("the integration group saved: %s", integration.get("problems"))
                .containsEntry("ok", true);
        final Map<String, Object> second = map(integration, "group");
        assertThat(uuidOf(map(second, "common"))).isEqualTo(commonUuid);
        final String integrationUuid = uuidOf(map(second, "xslt"));
        // A chain of imports: each group imports the common module alone, which imports the helpers.
        for (final String group : List.of(securityUuid, integrationUuid)) {
            final String code = readXslt(group).getData();
            assertThat(code).contains("uuid='" + commonUuid + "'", "<xsl:template match=\"/\">")
                    .doesNotContain("uuid='" + helpersUuid + "'");
        }
        assertThat(readXslt(commonUuid).getData()).contains("uuid='" + helpersUuid + "'");
        // The registry's rows for the two translated, by their groups; the rest still parked.
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(uuidOf(map(second,
                "registry"))));
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        list(registry, "rows").forEach(row -> rows.put((String) row.get("name"), row));
        assertThat(rows.get("PegaSecurityEvent")).containsEntry("status", "translated")
                .containsEntry("group", "PEGA security");
        assertThat(map(rows.get("PegaSecurityEvent"), "group_xslt")).containsEntry("uuid", securityUuid);
        assertThat(rows.get("PegaIntegration")).containsEntry("status", "translated")
                .containsEntry("group", "PEGA integration");
        assertThat(rows.get("PegaDB")).containsEntry("status", "parked");

        // Each group's pipeline, made by the tools with the group's XSLT as its XSLT, Stroom resolving the imports
        // by UUID.
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String securityPipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "name",
                FEED + "-Security-Events", "template_uuid", template, "set_properties", List.of(docProperty(
                        "translationFilter", "xslt", securityUuid, "XSLT")))));
        final String integrationPipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "name",
                FEED + "-Integration-Events", "template_uuid", template, "set_properties", List.of(docProperty(
                        "translationFilter", "xslt", integrationUuid, "XSLT")))));
        final List<String> records = text.lines().toList();
        final int securityAt = indexOf(records, "\"PegaSecurityEvent\"", null);
        final int soapAt = indexOf(records, "\"PegaIntegration\"", "Envelope>\"}");
        final int truncatedAt = indexOfTruncated(records);
        final int dbAt = indexOf(records, "\"PegaDB\"", null);
        assertOneValidEvent(securityPipeline, stream, securityAt);
        for (final int record : List.of(soapAt, truncatedAt)) {
            assertOneValidEvent(integrationPipeline, stream, record);
        }
        final Element login = children(parse(translationOutput(securityPipeline, stream, securityAt))
                .getDocumentElement(), "Event").getFirst();
        assertThat(textAt(login, "EventSource/System/Name")).isEqualTo("PEGA");
        assertThat(textAt(login, "EventSource/System/Environment")).isEqualTo("Prod");
        assertThat(textAt(login, "EventSource/Device/HostName")).startsWith("pega-app");
        assertThat(textAt(login, "EventTime/TimeCreated")).startsWith("2026-11-10T");
        // Not of its group's source types: no Event, the record passed over and counted.
        for (final int record : List.of(dbAt, soapAt)) {
            assertThat(children(parse(translationOutput(securityPipeline, stream, record)).getDocumentElement(),
                    "Event")).as("record %s in the security pipeline", record).isEmpty();
        }
        assertThat(children(parse(translationOutput(integrationPipeline, stream, securityAt)).getDocumentElement(),
                "Event")).isEmpty();

        // An unsaved change to a group, stepped as draft code for its pipeline's XSLT.
        final String draft = readXslt(securityUuid).getData().replace("<TypeId>Login</TypeId>",
                "<TypeId>LoginDraft</TypeId>");
        assertThat(draft).contains("LoginDraft");
        final int loginAt = indexOf(records, "\\\"eventType\\\": \\\"login\\\"", null);
        final Map<String, Object> stepped = json("step_pipeline", args("pipeline_uuid", securityPipeline,
                "stream_id", stream, "record", loginAt, "draft_code", Map.of("translationFilter", draft)));
        assertThat(String.valueOf(map(map(stepped, "elements"), "translationFilter").get("output")))
                .contains("LoginDraft");

        // Stepped clean with the saved code: the passed-over records and the truncated bodies are counted, not
        // faults, and no module is imported twice.
        for (final String pipeline : List.of(securityPipeline, integrationPipeline)) {
            final Map<String, Object> sampled = stepSample(pipeline, List.of(stream));
            dump("step_sample", sampled.get("groups"));
            assertThat(sampled).as("stepped clean: %s", sampled.get("groups")).containsEntry("verdict", "clean");
            assertThat(jsonOf(sampled)).doesNotContain("imported more than once", "cannot import itself");
        }

        // The pipeline's documentation: its group named, the registry named, the group's own Field mapping.
        final Map<String, Object> documented = agreed("write_documentation", args("build", BUILD, "pipeline_uuid",
                securityPipeline, "stream_ids", List.of(stream), "markdown", "# " + FEED + "-Security-Events\n\n"
                                                                              + "## Purpose and data\n\nPEGA's "
                                                                              + "security events.\n\n## Field "
                                                                              + "mapping\n\nGenerated.\n",
                "change", "Created"));
        dump("write_documentation", documented);
        final String docText = documentation(uuidOf(documented));
        assertThat(docText).contains("source type group PEGA security's (PegaSecurityEvent)", FEED + " source types",
                "`EventDetail/Authenticate/User/Id`", "`EventTime/TimeCreated`");

        // Processing: each group's pipeline writes valid Events for its own source types alone; the rest passed over
        // and counted (once a document: the lines add up to the stream's), the truncated SOAP bodies counted, and
        // nothing stops.
        final long total = records.size();
        final Processed securityDone = processed(securityPipeline, stream);
        final long securityRecords = recordsOf(records, "PegaSecurityEvent");
        assertThat(count(securityDone.events(), "<Event>")).isEqualTo(securityRecords);
        assertThat(passedOver(securityDone.logged())).isEqualTo(total - securityRecords);
        final Processed integrationDone = processed(integrationPipeline, stream);
        final long integrationRecords = recordsOf(records, "PegaIntegration");
        assertThat(count(integrationDone.events(), "<Event>")).isEqualTo(integrationRecords);
        assertThat(passedOver(integrationDone.logged())).isEqualTo(total - integrationRecords);
        final long truncated = records.stream().filter(TestE2eSourceTypeGroups::isTruncatedSoap).count();
        assertThat(truncated).isPositive();
        assertThat(count(integrationDone.logged(), "pega_integration: soap" + XsltGenerator.NOT_PARSED + "XML"))
                .isEqualTo(truncated);
        assertThat(securityDone.logged() + integrationDone.logged()).doesNotContain("WARN New source type");
    }

    // One record's output in a pipeline: valid, one Event.
    private void assertOneValidEvent(final String pipeline, final long stream, final int record) {
        final String output = translationOutput(pipeline, stream, record);
        final Map<String, Object> valid = json("check_events", args("events_xml", output));
        assertThat(map(valid, "schema")).as("record %s valid: %s", record, output).containsEntry("valid", true);
        assertThat(children(parse(output).getDocumentElement(), "Event")).as("record %s: one Event", record)
                .hasSize(1);
    }

    // A pipeline's processing of the stream: its one Events stream, valid, and what it logged beside it.
    private Processed processed(final String pipeline, final long stream) {
        final Map<String, Object> done = process(pipeline, List.of(stream));
        dump("wait_for_processing", done);
        final Map<String, Object> processed = list(done, "streams").getFirst();
        final List<Long> events = ids(processed, "events");
        assertThat(events).as("an Events stream: %s", processed).hasSize(1);
        final Map<String, Object> checked = json("check_events", args("stream_ids", events));
        assertThat(map(checked, "schema")).as("Events valid: %s", checked).containsEntry("valid", true);
        final List<Long> errors = ids(processed, "errors");
        assertThat(errors).as("the counts logged beside the Events").isNotEmpty();
        return new Processed(streamText(events.getFirst()), streamText(errors.getFirst()));
    }

    private static long recordsOf(final List<String> records, final String sourceType) {
        return records.stream().filter(line -> line.contains("\"sourcetype\": \"" + sourceType + "\"")).count();
    }

    // --- the mappings an agent would send for each group ---

    static Map<String, Object> securityMapping() {
        return jsonValue("""
                {"input": "json", "json_layout": "lines", "json_fields": ["message"],
                 "common": %s,
                 "ignored_fields": ["app", "environment"],
                 "events": [{"name": "login", "when": [{"field": "message.eventType", "equals": "login"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "Login"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "message.operatorId"},
                                        {"path": "EventDetail/Authenticate/Outcome/Success",
                                         "field": "message.outcome", "map": {"success": "true", "failure": "false"}}]},
                            {"name": "logout", "when": [{"field": "message.eventType", "equals": "logout"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "Logout"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logoff"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "message.operatorId"}]},
                            {"name": "other", "allow_unknown": "access, password and role events, for a later build",
                             "fields": [{"path": "EventDetail/TypeId", "field": "message.eventType"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "operator",
                                         "field": "message.operatorId"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "outcome",
                                         "field": "message.outcome"}]}]}""".formatted(WRAPPER));
    }

    static Map<String, Object> integrationMapping() {
        return jsonValue("""
                {"input": "json", "json_layout": "lines", "xml_fields": ["soap"],
                 "extract": [{"field": "message", "regex": "^(Request|Response): (.*)$", "names": ["direction", "soap"],
                              "flags": "s"}],
                 "common": %s,
                 "ignored_fields": ["app", "environment"],
                 "events": [{"name": "call", "allow_unknown": "integration calls name no action of the schema's",
                             "fields": [{"path": "EventDetail/TypeId", "field": "direction"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "correlation",
                                         "field": "soap/Envelope/Header/CorrelationId"}]}]}""".formatted(WRAPPER));
    }

    static Map<String, Object> jsonValue(final String text) {
        return (Map<String, Object>) ToolJson.loads(text);
    }

    // --- the sample ---

    static int indexOf(final List<String> records, final String part, final String alsoEndingWith) {
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).contains(part) && (alsoEndingWith == null || records.get(i).endsWith(alsoEndingWith))) {
                return i;
            }
        }
        throw new IllegalStateException("No record holds " + part);
    }

    private static int indexOfTruncated(final List<String> records) {
        for (int i = 0; i < records.size(); i++) {
            if (isTruncatedSoap(records.get(i))) {
                return i;
            }
        }
        throw new IllegalStateException("No truncated SOAP body");
    }

    static boolean isTruncatedSoap(final String line) {
        return line.contains("\"sourcetype\": \"PegaIntegration\"") && !line.endsWith("Envelope>\"}");
    }

    // The records a group's pipeline says it passed over, added up over its lines (one a document).
    static long passedOver(final String logged) {
        final Matcher matcher = Pattern.compile(Pattern.quote(XsltGenerator.PASSED_OVER) + "(\\d+)"
                                                + Pattern.quote(XsltGenerator.PASSED_OVER_END) + "$",
                Pattern.MULTILINE).matcher(logged);
        long total = 0;
        while (matcher.find()) {
            total += Long.parseLong(matcher.group(1));
        }
        return total;
    }

    static long count(final String text, final String part) {
        long n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            n++;
        }
        return n;
    }

    // A stream's text, as Stroom stores it.
    private String streamText(final long id) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Source source = streamStore.openSource(id)) {
                return SourceUtil.readString(source);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // A slice of a sample file, its first lines.
    static String lines(final String file, final int count) {
        return slice(file, 0, count);
    }

    // A slice of a sample file: so many lines from a line. The samples are the test resources under `aggregated/`,
    // made by its aggregated-feeds-generator.py.
    static String slice(final String file, final int from, final int count) {
        final String text = textResource(SAMPLES + file);
        return String.join("\n", text.lines().skip(from).limit(count).toList()) + "\n";
    }

    // A user who chooses these source types for the build's scope, giving their reason for the rest, and agrees to
    // everything else.
    static Function<ElicitFormRequest, ElicitResult> scopeOf(final List<String> chosen,
                                                                     final String reason) {
        return form -> {
            final ElicitResult agreed = McpTestClient.agree(form);
            if (!form.message().contains(SCOPE_FORM)) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            content.replaceAll((key, value) -> value instanceof Boolean
                    ? chosen.stream().anyMatch(name -> ("Build " + name + " now").equals(
                    ((Map<?, ?>) properties.get(key)).get("title")))
                    : value);
            content.put("text", reason);
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }


    // --------------------------------------------------------------------------------


    // A pipeline's Events and what it logged, as Stroom stored them.
    private record Processed(String events, String logged) {

    }
}
