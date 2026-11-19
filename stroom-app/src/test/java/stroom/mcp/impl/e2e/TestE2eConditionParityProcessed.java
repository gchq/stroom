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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogue's check and processing agree, through a real pipeline (stroom-ai/DESIGN.md, Event type catalogue,
/// Fields and values): the check reads each record as processing will, so
///
/// - a condition on a field inside a held JSON string past the jsonParser's `stringTruncateLength` (10,000 by
///   default, the [AwkwardValues] long value's cut) depends on the cut: refused, naming the setting;
/// - a held value that doesn't parse (a bad escape) holds no condition: its records are uncatalogued, counted with
///   why, and processing translates them by the catch-all;
/// - a NUL inside a type-telling value is removed before comparing, as the splitter and XSLT remove it;
/// - and the processed Events of each event type are as many as the check counted.
@SuppressWarnings("unchecked")
class TestE2eConditionParityProcessed extends AbstractE2eTest {

    private static final String JSON = "Event Data (JSON)";
    private static final String TELLING = "event.kind";

    @Test
    void aConditionInsideAHeldValuePastTheJsonParsersCutIsRefusedNamingTheSetting() {
        final String build = "parity-cut";
        final String feed = "PARITY-CUT";
        createFeed(build, feed);
        final StringBuilder sample = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            final Map<String, Object> event = new LinkedHashMap<>();
            // The long value first: the jsonParser's cut of the held string takes the kind with it.
            event.put("pad", i % 2 == 0
                    ? "L".repeat(AwkwardValues.LONG_LENGTH)
                    : "short");
            event.put("kind", i % 3 == 0
                    ? "login"
                    : "logout");
            sample.append(record(i, ToolJson.dumps(event)));
        }
        final List<Long> raw = List.of(upload(feed, sample.toString()));
        surveyed(build, feed, raw);
        json(CATALOGUE, args("build", build, "stream_ids", raw));
        final String reply = replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when(TELLING, "equals", "login")),
                eventType("Authenticate-Logoff", "Authenticate", "high", when(TELLING, "equals", "logout")))));
        dump("past the cut", reply);
        assertThat(reply).contains(TELLING, "stringTruncateLength");
    }

    @Test
    void heldValuesThatDontParseAndNulsAreCheckedAsProcessingTranslatesThem() {
        final String build = "parity-held";
        final String feed = "PARITY-HELD";
        createFeed(build, feed);
        final StringBuilder sample = new StringBuilder();
        int n = 0;
        for (int i = 0; i < 6; i++) {
            sample.append(record(n++, ToolJson.dumps(Map.of("kind", "login", "seq", i))));
            sample.append(record(n++, ToolJson.dumps(Map.of("kind", "logout", "seq", i))));
        }
        // A NUL inside the kind: removed before comparing, as Stroom's splitter and XSLT remove it.
        for (int i = 0; i < 2; i++) {
            sample.append(record(n++, ToolJson.dumps(Map.of("kind", "log\u0000in", "seq", 10 + i))));
        }
        // A held value with a bad escape: no condition inside it holds.
        for (int i = 0; i < 2; i++) {
            sample.append(record(n++, "{\"kind\": \"login\", \"note\": \"bad \\x escape " + i + "\"}"));
        }
        final List<Long> raw = List.of(upload(feed, sample.toString()));
        surveyed(build, feed, raw);
        json(CATALOGUE, args("build", build, "stream_ids", raw));

        final Map<String, Object> checked = proposed(build, raw, List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when(TELLING, "equals", "login")),
                        "action", "Logon", "actor", "user"),
                withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when(TELLING, "equals", "logout")),
                        "action", "Logoff", "actor", "user")));
        final Map<String, Long> counts = countsOf(checked);
        assertThat(counts).containsEntry("Authenticate-Logon", 8L).containsEntry("Authenticate-Logoff", 6L);
        final Map<String, Object> unparsed = map(checkOf(checked), "unparsed");
        assertThat(unparsed).containsEntry("records", 2);
        assertThat(strings(unparsed, "why")).isNotEmpty().allSatisfy(why -> assertThat(why).contains("event"));
        final long uncatalogued = number(map(checkOf(checked), "uncatalogued").get("records"));
        assertThat(uncatalogued).isEqualTo(2);
        confirmedCatalogue(build);

        // ### Processed through the draft's translation: as many of each event type as the check counted, the
        // records that don't parse translated by the catch-all.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Parity", "system_name", "Parity", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", timeChosen(map(draft,
                "mapping")), "schema_version", VERSION, "stream_ids", raw, "build", build, "name",
                feed + "-Events"));
        assertThat(built).as("built: %s", built.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", JSON))));
        final Map<String, Object> stepped = stepSample(pipeline, raw);
        assertThat(map(stepped, "groups_by_class")).as("stepped: %s", stepped.get("groups"))
                .containsEntry("blocking", 0);
        final Map<String, Object> processed = process(pipeline, raw);
        assertThat(processed).as("processed: %s", processed.get("streams")).containsEntry("gate", "pass");
        final List<Long> events = ids(list(processed, "streams").getFirst(), "events");
        final List<Element> written = children(parse(storedText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize(n);
        final Map<String, Long> byType = written.stream()
                .filter(event -> at(event, "EventDetail/Unknown").isEmpty())
                .collect(Collectors.groupingBy(event -> textAt(event, "EventDetail/TypeId"), Collectors.counting()));
        assertThat(byType).isEqualTo(counts);
        assertThat(written.stream().filter(event -> at(event, "EventDetail/Unknown").isPresent()).count())
                .as("the catch-all's").isEqualTo(uncatalogued);
    }

    // A JSON line: its time, user and the held `event` string.
    private static String record(final int i, final String held) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("ts", "2026-11-10T10:%02d:%02d.000Z".formatted(i / 60, i % 60));
        record.put("user", "user" + i % 4);
        record.put("event", held);
        return ToolJson.dumps(record) + "\n";
    }
}
