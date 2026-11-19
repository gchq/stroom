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

import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/// Pega's loggers as the kinds of event, through the REAL tools on a real Stroom (seen in testing, 2026-11-19): Pega
/// Platform logs in a shipper's JSON lines, each record's Pega log (`log_type`: App, Integration, Security, Alert,
/// Cluster) its source type, read inside the JSON string `event`. Within the Integration log, the kind of event is
/// the logger, `event.logger_name`: Java class names, three of the four longer than 80 characters (SOAP service
/// records hold the SOAP envelope in their message). The logger is the kind field, chosen by its name as its values
/// now pass, and never the node, host or level (the testing run offered only `event.level`, `event.alertType`,
/// `event.source_host` and `event.nodeId`, so the user could only say "all one kind" and the mapping had one real
/// event); the group's pipeline translates each logger's records by its own rule.
///
/// The sample is `aggregated/pega-loggers/pega-loggers.log`, made by `pega-loggers-generator.py` beside it; the same
/// file serves for testing by hand.
@SuppressWarnings("unchecked")
class TestE2ePegaLoggerKinds extends AbstractE2eTest {

    private static final String FEED = "E2E-PEGA-LOGGERS";
    private static final String BUILD = "pega-loggers-1";
    private static final String GROUP = "Pega Integration";
    private static final String READING = "event>log_type";
    private static final String LOG_TYPE = "Integration";
    private static final String KIND_FIELD = "event.logger_name";
    // The kind field form's question.
    private static final String KIND_QUESTION = "Which field tells the kinds of event apart";
    private static final String SAMPLE = "aggregated/pega-loggers/pega-loggers.log";
    private static final String PACKAGE = "com.pega.pegarules.integration.engine.internal.";
    // Each Integration logger's rule, as the user confirms it: its event detail and description. Action elements
    // whose children the records can't fill (Process's Type and Command, Receive's Source and Destination) aren't
    // proposed.
    private static final Map<String, String> DETAILS = Map.of(
            PACKAGE + "services.soap.SOAPService", "Import",
            PACKAGE + "services.rest.RESTServiceRequestProcessorImpl", "Search",
            PACKAGE + "client.http.HTTPClientConnectorRequestProcessor", "Export",
            PACKAGE + "services.email.EmailListenerServiceImpl", "Import");
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            PACKAGE + "services.soap.SOAPService", "A SOAP service request or response",
            PACKAGE + "services.rest.RESTServiceRequestProcessorImpl", "A REST service request",
            PACKAGE + "client.http.HTTPClientConnectorRequestProcessor", "A call to an external HTTP service",
            PACKAGE + "services.email.EmailListenerServiceImpl", "An email listener poll");

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    // The kind field form's answers, as it put them to the user.
    private final List<String> kindOptions = new ArrayList<>();

    @Test
    void pegasLoggerTellsTheKindsOfEventApartAndEachIsTranslatedByItsOwnRule() {
        createFeed("pega-loggers-src", FEED);
        final String text = textResource(SAMPLE);
        final long stream = upload(FEED, text);
        final List<Long> raw = List.of(stream);
        final String template = templateUuid("translation", "Event Data (JSON)");
        final long integrationRecords = text.lines().filter(line -> line.contains("\\\"log_type\\\": \\\""
                                                                                   + LOG_TYPE + "\\\"")).count();
        assertThat(integrationRecords).as("the sample's Integration records").isGreaterThan(100);
        client.answeringWith(user());

        // ### The survey, given the held source type field.
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", BUILD,
                "source_type_field", READING));
        dump("survey_feed", surveyed);
        assertThat(map(surveyed, "source_types")).containsEntry("field", READING);

        // ### The scope: the Integration log alone.
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact"));
        final Map<String, Object> scoped = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact", "reviewed", shown.get("review_id"), "group", GROUP));
        dump("record_source_types", scoped);
        assertThat(scoped).containsEntry("group", GROUP);
        assertThat(strings(scoped, "scope")).containsExactly(LOG_TYPE);

        // ### classify_kinds: the logger, named like a kind field (`logger_name`) and on every record, its long class
        // names no bar, chosen without a form (2026-11-19); a form, if shown, offers it and never the node, host or
        // level. The Integration log's four loggers listed.
        final Map<String, Object> listed = json("classify_kinds", args("build", BUILD, "source", "Pega Platform",
                "stream_ids", raw));
        dump("classify_kinds listed", listed);
        if (!kindOptions.isEmpty()) {
            assertThat(kindOptions).as("the kind field form's answers")
                    .anyMatch(option -> option.startsWith(KIND_FIELD + " ("));
        }
        assertThat(kindOptions).as("the kind field form's answers")
                .noneMatch(option -> option.startsWith("event.source_host") || option.startsWith("event.nodeId")
                                     || option.startsWith("event.level"));
        final List<Map<String, Object>> kinds = list(listed, "to_classify");
        assertThat(kinds).extracting(kind -> kind.get("value"))
                .containsExactlyInAnyOrderElementsOf(DETAILS.keySet());
        assertThat(kinds).allSatisfy(kind -> assertThat(kind).containsEntry("field", KIND_FIELD));

        // ### Proposed and confirmed; the draft follows them, a rule a logger.
        json("classify_kinds", args("build", BUILD, "proposals", proposals(kinds)));
        final Map<String, Object> confirmed = json("classify_kinds", args("build", BUILD, "confirm", true));
        dump("classify_kinds confirmed", confirmed);
        assertThat((List<String>) confirmed.get("recorded")).hasSize(DETAILS.size());
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build", BUILD,
                "source_name", "Pega Platform", "system_name", "Pega", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        // Each event's description is its own message (`event.message`, mapped in common), so the rules are told by
        // the logger they are for and the action element the user confirmed.
        final String rules = JsonUtil.writeValueAsString(map(draft, "mapping").get("events"), false);
        assertThat(rules).contains(KIND_FIELD).contains(DETAILS.keySet());

        // ### The group saved from the draft, its pipeline stepped clean and processed.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "schema_version", VERSION, "stream_ids", raw, "mapping",
                timeChosen(map(draft, "mapping"))));
        dump("build_translation_xslt", saved);
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "group", GROUP,
                "template_uuid", template)));
        final Map<String, Object> stepped = stepSample(pipeline, raw);
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = process(pipeline, raw);
        dump("processed", done);
        final List<Long> events = ids(list(done, "streams").getFirst(), "events");
        assertThat(events).hasSize(1);
        final Map<String, Object> checked = json("check_events", args("stream_ids", events));
        assertThat(checked).as("check_events: %s", checked).containsEntry("ok", true);

        // ### Each logger's records translated by its own rule, none by a catch-all; the other logs passed over.
        final List<Element> written = children(parse(streamText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize((int) integrationRecords);
        for (final Element event : written) {
            final String logger = textAt(event, "EventDetail/TypeId");
            assertThat(DETAILS).as("a logger's rule").containsKey(logger);
            assertThat(at(event, "EventDetail/" + DETAILS.get(logger))).as("the event of '%s'", logger).isPresent();
            assertThat(at(event, "EventDetail/Unknown")).as("the event of '%s'", logger).isEmpty();
        }
        assertThat(written).extracting(event -> textAt(event, "EventDetail/TypeId"))
                .containsAll(DETAILS.keySet());
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(text.lines().count() - integrationRecords);
    }

    // The user: the Integration log alone in the group, the logger chosen as the kind field (the form's answers kept
    // to check), each set of classifications accepted with its button, and agreeing to everything else.
    private Function<ElicitFormRequest, ElicitResult> user() {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(List.of(LOG_TYPE),
                GROUP);
        return form -> {
            final Map<String, Object> question = (Map<String, Object>) ((Map<String, Object>) form.requestedSchema()
                    .get("properties")).get("answer");
            if (question == null || !String.valueOf(question.get("title")).contains(KIND_QUESTION)) {
                return scoping.apply(form);
            }
            final List<String> options = ((List<?>) question.get("examples")).stream().map(String::valueOf).toList();
            kindOptions.addAll(options);
            return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("answer", options.stream()
                    .filter(option -> option.startsWith(KIND_FIELD + " ("))
                    .findFirst()
                    .orElse(KIND_FIELD)), null);
        };
    }

    // The agent's proposals, high confidence, a rule a logger: the Pega operator acting in each.
    private static List<Map<String, Object>> proposals(final List<Map<String, Object>> kinds) {
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map<String, Object> kind : kinds) {
            final String value = String.valueOf(kind.get("value"));
            final Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("field", kind.get("field"));
            proposal.put("value", value);
            proposal.put("description", DESCRIPTIONS.get(value));
            proposal.put("event_detail", DETAILS.get(value));
            proposal.put("actor", "event.userid");
            proposal.put("confidence", "high");
            proposals.add(proposal);
        }
        return proposals;
    }

    // The draft's mapping, a time it couldn't choose chosen as the agent would (the first candidate).
    private static Map<String, Object> timeChosen(final Map<String, Object> drafted) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : list(mapping, "common")) {
            final Map<String, Object> kept = new LinkedHashMap<>(entry);
            if (kept.remove("choose_from") instanceof final List<?> candidates) {
                kept.put("field", candidates.getFirst());
            }
            common.add(kept);
        }
        mapping.put("common", common);
        return mapping;
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
}
