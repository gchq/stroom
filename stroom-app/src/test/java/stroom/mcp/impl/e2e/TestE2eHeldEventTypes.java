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

import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Event types recognised by a field inside a held value, through the REAL tools on a real Stroom (seen in testing,
/// 2026-11-18): an aggregated JSON-lines feed whose source type is read inside the JSON string `event`
/// (`event>logger_name`), and whose records of one source type say what happened inside it too (`component`). The
/// catalogue's conditions name the held field as a nested object's would be spelt (`event.component`); a condition
/// on the source type field itself is refused (the group's module already passes other source types over); and the
/// group's pipeline, processed, translates each record by its event type's rule, as many of each as the check
/// counted, and passes the other source types over.
@SuppressWarnings("unchecked")
class TestE2eHeldEventTypes extends AbstractE2eTest {

    private static final String FEED = "E2E-HELD-TYPES";
    private static final String BUILD = "held-types-1";
    private static final String GROUP = "Held app";
    private static final String READING = "event>logger_name";
    private static final String APP = "com.x.App";
    private static final String TELLING = "event.component";
    private static final List<String> COMPONENTS = List.of("auth", "billing", "search");
    private static final int RECORDS = 180;
    private static final long TIME = 1_763_452_800_000L;
    // Each component's event type, as the agent proposes it: its TypeId and event detail.
    private static final Map<String, String> TYPE_IDS = Map.of("auth", "Authenticate-User", "billing",
            "Export-Invoice", "search", "Search-Catalogue");
    private static final Map<String, String> DETAILS = Map.of("auth", "Authenticate", "billing", "Export",
            "search", "Search");

    @Test
    void eventTypesRecognisedInsideAHeldValueAreTranslatedByTheirOwnRulesInProcessing() {
        createFeed("held-types-src", FEED);
        final String text = lines();
        final List<Long> raw = List.of(upload(FEED, text));
        final long appRecords = text.lines().filter(line -> line.contains(APP)).count();
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of(APP), GROUP));

        // ### The survey, given the held source type field; the scope: the app's records alone.
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", BUILD,
                "source_type_field", READING));
        assertThat(map(surveyed, "source_types")).containsEntry("field", READING);
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact"));
        final Map<String, Object> scoped = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact", "reviewed", shown.get("review_id"), "group", GROUP));
        assertThat(strings(scoped, "scope")).containsExactly(APP);

        // ### The evidence: the held field tells the scope's records apart, spelt as a nested object's would be;
        // neither time, nor the source type field (one value in the scope).
        final Map<String, Object> listed = json(CATALOGUE, args("build", BUILD, "stream_ids", raw));
        dump("listed", listed);
        assertThat(list(map(listed, "evidence"), "fields")).extracting(field -> field.get("field"))
                .contains(TELLING)
                .doesNotContain("time", "fields.orig_rt", "event.logger_name");
        assertThat(jsonOf(map(listed, "evidence"))).as("the other source types' records left out")
                .doesNotContain("cdn", "com.x.Web", "com.x.Db");

        // ### A condition on the source type field: refused, whatever else holds.
        assertThat(replyText(CATALOGUE, args("build", BUILD, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-User", "Authenticate", "high", when("event.logger_name", "equals", APP,
                        TELLING, "equals", "auth"))))))
                .contains("event.logger_name", "source type");

        // ### Proposed by the held field, each counted; confirmed with the catch-all.
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final String component : COMPONENTS) {
            final Map<String, Object> proposal = new LinkedHashMap<>(eventType(TYPE_IDS.get(component),
                    DETAILS.get(component), "high", when(TELLING, "equals", component)));
            proposal.put("actor", "event.user");
            if ("auth".equals(component)) {
                proposal.put("action", "Logon");
            }
            proposals.add(proposal);
        }
        final Map<String, Object> checked = proposed(BUILD, raw, proposals);
        final Map<String, Long> counts = countsOf(checked);
        assertThat(counts).containsOnlyKeys(TYPE_IDS.values());
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(appRecords);
        assertThat(map(checkOf(checked), "uncatalogued")).containsEntry("records", 0);
        confirmedCatalogue(BUILD);
        assertThat(strings(map(catalogueOf(BUILD), "scope"), "source_types")).containsExactly(APP);

        // ### The group saved from the draft, its pipeline stepped clean and processed.
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build", BUILD,
                "source_name", "Acme app", "system_name", "Acme", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        assertThat(jsonOf(map(draft, "mapping").get("events"))).contains(TELLING)
                .doesNotContain("\"field\":\"event.logger_name\",\"equals\"");
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "schema_version", VERSION, "stream_ids", raw, "mapping",
                timeChosen(map(draft, "mapping"))));
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "group", GROUP,
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));
        assertThat(stepSample(pipeline, raw)).containsEntry("verdict", "clean");
        final List<Long> events = ids(list(process(pipeline, raw), "streams").getFirst(), "events");
        assertThat(events).hasSize(1);
        assertThat(json("check_events", args("stream_ids", events))).containsEntry("ok", true);

        // ### Processing agrees with the check: each record by its component's rule, as many of each as counted.
        final List<Element> written = children(parse(storedText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize((int) appRecords);
        for (final Element event : written) {
            final String user = textAt(event, "EventSource/User/Id");
            final String component = componentOf(Integer.parseInt(user.substring(1)));
            assertThat(textAt(event, "EventDetail/TypeId")).as("the event of %s", user)
                    .isEqualTo(TYPE_IDS.get(component));
            assertThat(at(event, "EventDetail/" + DETAILS.get(component))).as("the event of %s", user).isPresent();
        }
        assertThat(written.stream().collect(Collectors.groupingBy(event -> textAt(event, "EventDetail/TypeId"),
                Collectors.counting()))).isEqualTo(counts);
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(RECORDS - appRecords);
    }

    // The component of the app's record i.
    private static String componentOf(final int i) {
        return COMPONENTS.get(i / 6 % COMPONENTS.size());
    }

    // The sample: half the records the app's (its component inside the held `event`), a third the web logger's
    // (a component of its own, cdn), the rest the database's (none); outside the held value an epoch-ms time and a
    // nanosecond orig_rt, each with few values.
    private static String lines() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < RECORDS; i++) {
            final Map<String, Object> event = new LinkedHashMap<>();
            switch (i % 6) {
                case 0, 1, 2 -> {
                    event.put("logger_name", APP);
                    event.put("component", componentOf(i));
                    event.put("user", "u" + i);
                }
                case 3, 4 -> {
                    event.put("logger_name", "com.x.Web");
                    event.put("component", "cdn");
                    event.put("path", "/p" + i);
                }
                default -> {
                    event.put("logger_name", "com.x.Db");
                    event.put("query", "select " + i);
                    event.put("rows", 100 + i);
                }
            }
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("time", TIME + i % 4 * 1000L);
            record.put("host", "app0" + i % 3);
            record.put("fields", Map.of("orig_rt", (TIME + i % 3) * 1_000_000L));
            record.put("event", ToolJson.dumps(event));
            out.append(ToolJson.dumps(record)).append('\n');
        }
        return out.toString();
    }
}
