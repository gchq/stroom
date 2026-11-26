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

import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.mcp.impl.McpTestClient;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// A key of JSON held in a key of JSON held in a field, placed in its element, through the REAL tools on a real
/// Stroom (seen in external testing, 2026-11-26): a single-source feed whose `event` is JSON text, its `original`
/// JSON text again, whose `details` is JSON text holding the search's `query_text`. Placed in Query/Raw by the
/// catalogue, the query text was written there and as Data too. Placed by the field's name, and by an xpath.
@SuppressWarnings("unchecked")
class TestE2eNestedHeldJsonPlacement extends AbstractE2eTest {

    private static final String QUERY_TEXT = "event.original.details.query_text";
    // As an agent writes it: the held JSON read three deep.
    private static final String XPATH = "json-to-xml(json-to-xml(json-to-xml(*[@key='event'])/*/*[@key='original'])"
                                        + "/*/*[@key='details'])/*/*[@key='query_text']";
    private static final int RECORDS = 24;
    // The JSON texts holding the query, each kept whole as Data.
    private static final Set<String> HELD_JSON_TEXTS = Set.of("event", "event.original", "event.original.details");

    // H2: the key of JSON held in held JSON is a field of its own, so the catalogue takes a placement naming it (at
    // efef7c74fd: "no record has the field", the JSON only read one level down).
    @Test
    void aQueryTextHeldThreeDeepIsAFieldThePlacementNames() {
        final String build = "nested-held-1";
        final List<Long> raw = surveyed(build, "E2E-NESTED-HELD");
        final String byField = McpTestClient.text(client.call(CATALOGUE, args("build", build, "stream_ids", raw,
                "proposals", List.of(search(args("path", "Query/Raw", "field", QUERY_TEXT))))));
        assertThat(byField).contains("\"recorded\":true").doesNotContain("no record has the field");

        assertPlacedNotData(build, raw, "E2E-NESTED-HELD");
    }

    // H1: placed by an xpath, as the agent did where the field wasn't one: the field it reads isn't kept as Data
    // too, nor the held JSON text holding it.
    @Test
    void aQueryTextPlacedByAnXpathIsNotAlsoData() {
        final String build = "nested-held-2";
        final List<Long> raw = surveyed(build, "E2E-NESTED-XPATH");
        final String byXpath = McpTestClient.text(client.call(CATALOGUE, args("build", build, "stream_ids", raw,
                "proposals", List.of(search(args("path", "Query/Raw", "xpath", XPATH))))));
        assertThat(byXpath).contains("\"recorded\":true");

        assertPlacedNotData(build, raw, "E2E-NESTED-XPATH");
    }

    // The feed made and surveyed (one source), its catalogue listed. Returns its stream.
    private List<Long> surveyed(final String build, final String feed) {
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, searches()));
        json("survey_feed", args("feed", feed, "build", build, "stream_ids", raw, "source_type_field",
                SourceTypeFields.NOT_AGGREGATED));
        json(CATALOGUE, args("build", build, "stream_ids", raw));
        return raw;
    }

    // The catalogue confirmed, drafted, built, stepped and processed: every Event's Query/Raw holds the query text,
    // and no Data repeats it, but the held JSON texts kept whole.
    private void assertPlacedNotData(final String build, final List<Long> raw, final String feed) {
        confirmedCatalogue(build);
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Search portal", "system_name", "Search portal", "environment", "Test"));
        final Map<String, Object> drafted = draftedMapping(draft);
        final Map<String, Object> built = agreed("build_translation_xslt", args("build", build, "schema_version",
                VERSION, "stream_ids", raw, "name", feed + "-Events", "changes", timeChosenChanges(drafted)));
        assertThat(built).as("built: %s", built.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));
        assertThat(stepSample(pipeline, raw)).containsEntry("verdict", "clean");
        final List<Long> events = ids(list(process(pipeline, raw), "streams").getFirst(), "events");
        final List<Element> written = children(parse(storedText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize(RECORDS);
        for (final Element event : written) {
            final String query = textAt(event, "EventDetail/Search/Query/Raw");
            assertThat(query).as("the query text placed").isNotBlank();
            // The held JSON texts are kept whole as Data (the record as given); no other Data repeats the query.
            final List<Element> data = descendants(event, "Data").stream()
                    .filter(element -> !HELD_JSON_TEXTS.contains(element.getAttribute("Name")))
                    .toList();
            assertThat(data).as("no Data repeating the query text in %s", canonical(event))
                    .noneSatisfy(element -> assertThat(element.getAttribute("Name")).endsWith("query_text"))
                    .noneSatisfy(element -> assertThat(element.getAttribute("Value")).contains(query));
        }
    }

    // The search event type, recognised by its type, both ways, the query placed as given.
    private static Map<String, Object> search(final Map<String, Object> placement) {
        return withKeys(eventType("Search-Query", "Search", "high", when("type", "equals", "search"),
                        when("type", "equals", "QA search")), "actor", "user", "placements", List.of(placement),
                "one_type_because", "E2E: one search, from two consoles");
    }

    // JSON lines: event JSON text, its original JSON text, whose details are JSON text holding the query.
    private static String searches() {
        final StringBuilder out = new StringBuilder();
        final List<String> queries = List.of("cats and dogs", "invoice 2026", "fish", "failed logins",
                "quarterly report", "vpn users");
        for (int n = 0; n < RECORDS; n++) {
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("query_text", queries.get(n % queries.size()) + " " + n);
            details.put("scope", n % 2 == 0
                    ? "web"
                    : "archive");
            details.put("results", 3 + n);
            final Map<String, Object> original = new LinkedHashMap<>();
            original.put("client", "console-" + n % 3);
            original.put("session", "s" + (1000 + n));
            original.put("details", ToolJson.dumps(details));
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("@timestamp", "2026-11-25T10:%02d:%02d.000Z".formatted(n / 60, n % 60));
            record.put("host", "search0" + n % 2);
            record.put("type", n % 4 == 3
                    ? "QA search"
                    : "search");
            record.put("user", "user" + n % 5);
            record.put("event", ToolJson.dumps(Map.of("original", ToolJson.dumps(original))));
            out.append(ToolJson.dumps(record)).append('\n');
        }
        return out.toString();
    }
}
