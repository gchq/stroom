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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Pega's event types, through the REAL tools on a real Stroom (the motivating test of the event type catalogue,
/// stroom-ai/DESIGN.md; seen in testing, 2026-11-19): Pega Platform logs in a shipper's JSON lines, each record's Pega
/// log (`log_type`: App, Integration, Security, Alert, Cluster) its source type, read inside the JSON string `event`.
/// A logger says where a record came from, not what happened: one logger writes several event types.
/// `SecurityEventLogger` (under the Security log) writes operator, access group, password and ruleset changes, told
/// apart by `eventType`; `SOAPService` writes requests, responses and faults, told apart only by its message. The
/// draft's seed (one event type per logger) is warned as "may be several" on those; the agent's catalogue splits
/// them, Verb-Object TypeIds none of which is a logger's name; the user confirms it; the group's pipeline translates
/// each record by its event type's rule, the logger written as Data on every event and each event's Description its
/// own message (Pega's `message`).
///
/// The sample is `aggregated/pega-loggers/pega-loggers.log`, made by `pega-loggers-generator.py` beside it; the same
/// file serves for testing by hand.
@SuppressWarnings("unchecked")
class TestE2ePegaEventTypes extends AbstractE2eTest {

    private static final String FEED = "E2E-PEGA-TYPES";
    private static final String BUILD = "pega-types-1";
    private static final String GROUP = "Pega Integration and Security";
    private static final String READING = "event>log_type";
    private static final List<String> SCOPE = List.of("Integration", "Security");
    private static final String SAMPLE = "aggregated/pega-loggers/pega-loggers.log";
    private static final String LOGGER = "event.logger_name";
    private static final String MESSAGE = "event.message";
    private static final String EVENT_TYPE = "event.eventType";
    private static final String INTEGRATION = "com.pega.pegarules.integration.engine.internal.";
    private static final String SOAP = INTEGRATION + "services.soap.SOAPService";
    private static final String REST = INTEGRATION + "services.rest.RESTServiceRequestProcessorImpl";
    private static final String HTTP = INTEGRATION + "client.http.HTTPClientConnectorRequestProcessor";
    private static final String EMAIL = INTEGRATION + "services.email.EmailListenerServiceImpl";
    private static final String SECURITY = "com.pega.pegarules.web.security.SecurityEventLogger";
    private static final String AUTHN = "com.pega.pegarules.session.internal.authentication.service."
                                        + "AuthenticationServiceProviderImpl";
    private static final String AUTHZ = "com.pega.pegarules.session.internal.authorization.context."
                                        + "AuthorizationContextManagerImpl";
    // The records of each event type in the sample, as pega-loggers-generator.py wrote them.
    private static final Map<String, Long> EXPECTED = expected();
    private static final String VERB_OBJECT = "[A-Z][A-Za-z]*-[A-Z][A-Za-z]*";

    @Test
    void pegasLoggersEachWriteTheirOwnEventTypesToldApartByTheirRecords() {
        createFeed("pega-types-src", FEED);
        final String text = textResource(SAMPLE);
        final long stream = upload(FEED, text);
        final List<Long> raw = List.of(stream);
        final long inScope = text.lines().filter(line -> SCOPE.stream()
                .anyMatch(type -> line.contains("\\\"log_type\\\": \\\"" + type + "\\\""))).count();
        assertThat(inScope).as("the sample's Integration and Security records")
                .isEqualTo(EXPECTED.values().stream().mapToLong(Long::longValue).sum());
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(SCOPE, GROUP));

        // ### The survey, given the held source type field; the scope: the Integration and Security logs.
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", BUILD,
                "source_type_field", READING));
        assertThat(map(surveyed, "source_types")).containsEntry("field", READING);
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact"));
        final Map<String, Object> scoped = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact", "reviewed", shown.get("review_id"), "group", GROUP));
        dump("record_source_types", scoped);
        assertThat(strings(scoped, "scope")).containsExactlyInAnyOrderElementsOf(SCOPE);

        // ### The draft's seed, one event type per logger: those whose records are several are warned first, and
        // can't be confirmed so.
        final Map<String, Object> listed = json(CATALOGUE, args("build", BUILD, "stream_ids", raw));
        dump("listed", listed);
        final List<Map<String, Object>> seed = list(map(listed, "evidence"), "seed");
        assertThat(jsonOf(seed)).contains(SECURITY, SOAP);
        final List<Map<String, Object>> adding = new ArrayList<>();
        seed.forEach(seeded -> adding.add(withKeys(seeded, "op", "add")));
        final Map<String, Object> seeded = proposed(BUILD, raw, adding);
        final List<Map<String, Object>> several = severalOf(seeded);
        assertThat(several).as("the security logger's seeded type may be several, told by its eventType")
                .anySatisfy(type -> assertThat(map(type, "by_field")).containsEntry("field", EVENT_TYPE));

        // ### The agent's catalogue in place of the seed: the security logger's four, the SOAP service's three told
        // by its message, the rest one each; checked against every record.
        final List<Map<String, Object>> proposals = new ArrayList<>();
        seed.forEach(type -> proposals.add(args("op", "remove", "type_id", type.get("type_id"))));
        proposals.addAll(catalogue());
        final Map<String, Object> checked = proposed(BUILD, raw, proposals);
        final List<Map<String, Object>> because = new ArrayList<>();
        severalOf(checked).forEach(type -> because.add(args("op", "change", "type_id", type.get("type_id"),
                "one_type_because", "One event type of the logger: its fields are details, not other events")));
        final Map<String, Object> catalogued = because.isEmpty()
                ? checked
                : proposed(BUILD, raw, because);
        assertThat(severalOf(catalogued)).isEmpty();
        assertThat(countsOf(catalogued)).isEqualTo(EXPECTED);
        assertThat(list(checkOf(catalogued), "overlaps")).isEmpty();
        assertThat(map(checkOf(catalogued), "uncatalogued")).containsEntry("records", 0);
        assertThat(EXPECTED.keySet()).allSatisfy(typeId -> assertThat(typeId).matches(VERB_OBJECT)
                .doesNotContain("Logger", "Impl", "pega"));

        // ### Confirmed, the catch-all last; the draft follows the catalogue.
        confirmedCatalogue(BUILD);
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build", BUILD,
                "source_name", "Pega Platform", "system_name", "Pega", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        // Too large for a client outside Stroom to take whole (this test's client is one): outlined, and saved to
        // the workspace, read from there as the agent would.
        assertThat(draft).doesNotContainKey("mapping");
        assertThat(jsonOf(draft.get("mapping_outline"))).contains(EXPECTED.keySet().toArray(String[]::new))
                .contains("logger_name <- event.logger_name");
        final Map<String, Object> drafted = draftedMapping(draft);
        final String rules = JsonUtil.writeValueAsString(drafted.get("events"), false);
        assertThat(rules).contains(EXPECTED.keySet().toArray(String[]::new));

        // ### The group saved from the draft the server keeps, sending only what changes (the time chosen); its
        // pipeline stepped clean and processed.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "schema_version", VERSION, "stream_ids", raw, "changes", timeChosenChanges(drafted)));
        dump("build_translation_xslt", saved);
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "group", GROUP,
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));
        final Map<String, Object> stepped = stepSample(pipeline, raw);
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = process(pipeline, raw);
        final List<Long> events = ids(list(done, "streams").getFirst(), "events");
        assertThat(events).hasSize(1);
        assertThat(json("check_events", args("stream_ids", events))).containsEntry("ok", true);

        // ### Each event type's records translated by its own rule, as many as the check counted; none Unknown; the
        // logger written as Data on every event; each Description the record's own message.
        final List<Element> written = children(parse(storedText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize((int) inScope);
        assertThat(written.stream().collect(Collectors.groupingBy(event -> textAt(event, "EventDetail/TypeId"),
                Collectors.counting()))).isEqualTo(EXPECTED);
        for (final Element event : written) {
            final String typeId = textAt(event, "EventDetail/TypeId");
            assertThat(at(event, "EventDetail/Unknown")).as("the event of '%s'", typeId).isEmpty();
            assertThat(descendants(event, "Data")).as("the logger as Data, in '%s'", typeId)
                    .anySatisfy(data -> assertThat(data.getAttribute("Name")).isEqualTo("logger_name"));
            final String description = textAt(event, "EventDetail/Description");
            switch (typeId) {
                case "Receive-SOAPRequest" -> assertThat(description).startsWith("Request:");
                case "Send-SOAPFault" -> assertThat(description).contains("<soapenv:Fault>");
                case "Send-SOAPResponse" -> assertThat(description).startsWith("Response:")
                        .doesNotContain("<soapenv:Fault>");
                case "Update-Password" -> assertThat(description).startsWith("Password change by ");
                case "Unlock-Ruleset" -> assertThat(description).startsWith("Ruleset unlock by ");
                default -> assertThat(description).as("the Description of '%s'", typeId).isNotBlank();
            }
        }
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(text.lines().count() - inScope);
    }

    // The agent's catalogue: the security logger's four event types told by its eventType, the SOAP service's
    // three told by its message, every other logger's one.
    private static List<Map<String, Object>> catalogue() {
        final Function<String, Map<String, Object>> security = eventType -> args("field", EVENT_TYPE, "equals",
                eventType);
        return List.of(
                securityChange("Update-Operator", security.apply("Operator record change")),
                securityChange("Update-AccessGroup", security.apply("Access group change")),
                securityChange("Update-Password", security.apply("Password change")),
                securityChange("Unlock-Ruleset", security.apply("Ruleset unlock")),
                logged("Receive-SOAPRequest", "Import", SOAP, args("field", MESSAGE, "starts_with", "Request:")),
                logged("Send-SOAPFault", "Export", SOAP, args("field", MESSAGE, "contains", "<soapenv:Fault>")),
                logged("Send-SOAPResponse", "Export", SOAP, args("field", MESSAGE, "starts_with", "Response:"),
                        args("field", MESSAGE, "contains", "<soapenv:Fault>", "not", true)),
                logged("Search-RESTService", "Search", REST),
                logged("Call-HTTPService", "Export", HTTP),
                logged("Poll-Mailbox", "Import", EMAIL),
                withKeys(logged("Authenticate-Operator", "Authenticate", AUTHN), "action", "Logon"),
                withKeys(logged("Authorise-Privilege", "Authorise", AUTHZ), "affected", "event.privilege",
                        "affected_as", "Object"));
    }

    // A change the security logger writes, told by its eventType: the operator changed (or the ruleset) is what it
    // acts on (Update needs After).
    private static Map<String, Object> securityChange(final String typeId, final Map<String, Object> told) {
        return withKeys(logged(typeId, "Update", SECURITY, told), "affected", "event.target", "affected_as",
                "Unlock-Ruleset".equals(typeId)
                        ? "After/Object"
                        : "After/User");
    }

    // An event type a logger writes: recognised by the logger and any more conditions, the Pega operator acting.
    @SafeVarargs
    private static Map<String, Object> logged(final String typeId,
                                              final String eventDetail,
                                              final String logger,
                                              final Map<String, Object>... more) {
        final List<Map<String, Object>> conditions = new ArrayList<>(when(LOGGER, "equals", logger));
        conditions.addAll(List.of(more));
        return withKeys(eventType(typeId, eventDetail, "high", conditions), "actor", "event.userid");
    }


    private static Map<String, Long> expected() {
        final Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("Update-Operator", 5L);
        expected.put("Update-AccessGroup", 7L);
        expected.put("Update-Password", 4L);
        expected.put("Unlock-Ruleset", 9L);
        expected.put("Receive-SOAPRequest", 31L);
        expected.put("Send-SOAPFault", 5L);
        expected.put("Send-SOAPResponse", 27L);
        expected.put("Search-RESTService", 57L);
        expected.put("Call-HTTPService", 42L);
        expected.put("Poll-Mailbox", 19L);
        expected.put("Authenticate-Operator", 48L);
        expected.put("Authorise-Privilege", 31L);
        return expected;
    }
}
