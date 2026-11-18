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

/// A kind field the user chooses inside a held value, through the REAL tools on a real Stroom (seen in testing,
/// 2026-11-18): an aggregated JSON-lines feed whose source type is read inside the JSON string `event`
/// (`event>logger_name`), and whose records of one source type name their kind of event inside it too
/// (`component`). The survey asks no feed-wide kind field (the outer fields, an epoch-ms `time` and a nanosecond
/// `fields.orig_rt`, were all it could offer); classify_kinds, within the build's source type group, offers the held
/// field, neither time, nor the source type field itself (one value in the scope), and lists the group's own
/// components alone once the user chooses it; the group's pipeline, processed, translates each component's records by
/// its own rule and passes the other source types over.
@SuppressWarnings("unchecked")
class TestE2eHeldKindFields extends AbstractE2eTest {

    private static final String FEED = "E2E-HELD-KIND";
    private static final String BUILD = "held-kind-1";
    private static final String GROUP = "Held app";
    private static final String READING = "event>logger_name";
    private static final String APP = "com.x.App";
    private static final String KIND_FIELD = "event.component";
    // The kind field form's question.
    private static final String KIND_QUESTION = "Which field tells the kinds of event apart";
    private static final List<String> COMPONENTS = List.of("auth", "billing", "search");
    private static final int RECORDS = 180;
    private static final long TIME = 1_763_452_800_000L;
    // Each component's rule, as the user confirms it: its event detail and description.
    private static final Map<String, String> DETAILS = Map.of("auth", "Authenticate", "billing", "Export",
            "search", "Search");
    private static final Map<String, String> DESCRIPTIONS = Map.of("auth", "A user signed in to the app",
            "billing", "A user exported an invoice", "search", "A user searched the catalogue");

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    // The kind field form's answers, as it put them to the user.
    private final List<String> kindOptions = new ArrayList<>();

    @Test
    void aKindFieldChosenInsideAHeldValueTellsTheGroupsRulesApartInProcessing() {
        createFeed("held-kind-src", FEED);
        final String text = lines();
        final long stream = upload(FEED, text);
        final List<Long> raw = List.of(stream);
        final String template = templateUuid("translation", "Event Data (JSON)");
        final long appRecords = text.lines().filter(line -> line.contains(APP)).count();
        client.answeringWith(user());

        // ### The survey, given the held source type field: no feed-wide kind field asked, the kinds told apart
        // within the source types.
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        final int formsBefore = client.forms().size();
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", BUILD,
                "source_type_field", READING));
        dump("survey_feed", surveyed);
        assertThat(map(surveyed, "source_types")).containsEntry("field", READING);
        assertThat(map(surveyed, "kind_fields")).containsEntry("fields", List.of())
                .containsEntry("by", "source_types").containsEntry("within", READING);
        assertThat(formsSince(formsBefore)).extracting(ElicitFormRequest::message)
                .noneMatch(message -> message.contains("could tell") || message.contains("kinds of event"));

        // ### The scope: one source type group of the app's records alone, chosen in one list.
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact"));
        final int formsBeforeScope = client.forms().size();
        final Map<String, Object> scoped = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact", "reviewed", shown.get("review_id"), "group", GROUP));
        dump("record_source_types", scoped);
        final ElicitFormRequest scopeForm = formsSince(formsBeforeScope).stream()
                .filter(form -> form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM))
                .findFirst()
                .orElseThrow();
        assertThat(((Map<String, Map<String, Object>>) scopeForm.requestedSchema().get("properties")).values())
                .filteredOn(field -> "array".equals(field.get("type")))
                .hasSize(1);
        assertThat(scoped).containsEntry("group", GROUP);
        assertThat(strings(scoped, "scope")).containsExactly(APP);

        // ### classify_kinds: the held field among the candidates, neither time, nor the source type field (one value
        // in the scope); the user's choice lists the group's components alone (not the web logger's cdn).
        final Map<String, Object> listed = json("classify_kinds", args("build", BUILD, "source", "Acme app",
                "stream_ids", raw));
        dump("classify_kinds listed", listed);
        assertThat(kindOptions).as("the kind field form's answers").isNotEmpty()
                .anyMatch(option -> option.startsWith(KIND_FIELD + " ("))
                .noneMatch(option -> option.startsWith("time") || option.contains("orig_rt")
                                     || option.contains("logger_name"));
        final List<Map<String, Object>> kinds = list(listed, "to_classify");
        assertThat(kinds).extracting(kind -> kind.get("value")).containsExactlyInAnyOrderElementsOf(COMPONENTS);
        assertThat(kinds).allSatisfy(kind -> assertThat(kind).containsEntry("field", KIND_FIELD));

        // ### Proposed and confirmed in one form; the draft follows them, a rule a component.
        json("classify_kinds", args("build", BUILD, "proposals", proposals(kinds)));
        final Map<String, Object> confirmed = json("classify_kinds", args("build", BUILD, "confirm", true));
        dump("classify_kinds confirmed", confirmed);
        assertThat((List<String>) confirmed.get("recorded")).containsExactlyInAnyOrder("event.component=auth",
                "event.component=billing", "event.component=search");
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build", BUILD,
                "source_name", "Acme app", "system_name", "Acme", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final String rules = JsonUtil.writeValueAsString(map(draft, "mapping").get("events"), false);
        assertThat(rules).contains(KIND_FIELD).contains(DESCRIPTIONS.values());

        // ### The group saved from the draft, its pipeline stepped clean and processed.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "schema_version", VERSION, "stream_ids", raw, "mapping",
                timeChosen(map(draft, "mapping"))));
        dump("build_translation_xslt", saved);
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        assertThat(strings(map(saved, "group"), "source_types")).containsExactly(APP);
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

        // ### Each component's records translated by its own rule (the user names the record, so its component),
        // none by a catch-all; the other source types passed over.
        final List<Element> written = children(parse(streamText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize((int) appRecords);
        for (final Element event : written) {
            final String user = textAt(event, "EventSource/User/Id");
            final String component = componentOf(Integer.parseInt(user.substring(1)));
            assertThat(textAt(event, "EventDetail/TypeId")).as("the event of %s", user).isEqualTo(component);
            assertThat(textAt(event, "EventDetail/Description")).as("the event of %s", user)
                    .isEqualTo(DESCRIPTIONS.get(component));
            assertThat(at(event, "EventDetail/" + DETAILS.get(component))).as("the event of %s", user).isPresent();
            assertThat(at(event, "EventDetail/Unknown")).as("the event of %s", user).isEmpty();
        }
        assertThat(written).extracting(event -> textAt(event, "EventDetail/Description"))
                .containsAll(DESCRIPTIONS.values());
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(RECORDS - appRecords);
    }

    // The user: the app's source type alone in the group, the held field chosen as the kind field (its answers kept
    // to check), each set of classifications accepted with its button, and agreeing to everything else.
    private Function<ElicitFormRequest, ElicitResult> user() {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(List.of(APP),
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

    // The agent's proposals, high confidence, a rule a component: the user acting in each.
    private static List<Map<String, Object>> proposals(final List<Map<String, Object>> kinds) {
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map<String, Object> kind : kinds) {
            final String value = String.valueOf(kind.get("value"));
            final Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("field", kind.get("field"));
            proposal.put("value", value);
            proposal.put("description", DESCRIPTIONS.get(value));
            proposal.put("event_detail", DETAILS.get(value));
            if ("auth".equals(value)) {
                proposal.put("action", "Logon");
            }
            proposal.put("actor", "event.user");
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

    // The component of the app's record i.
    private static String componentOf(final int i) {
        return COMPONENTS.get(i / 6 % COMPONENTS.size());
    }

    // The sample: half the records the app's (its component inside the held `event`), a third the web logger's
    // (a component of its own, cdn), the rest the database's (none); outside the held value an epoch-ms time and a
    // nanosecond orig_rt, each with few values, so a kind field by their values alone.
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
