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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Each event's time, from where its record keeps it ([EventTimeFeed]): drafted, saved, stepped in Stroom and every
/// record's TimeCreated checked against the time planted in it. The draft's choices are what is tested; the agent's
/// part is played only where the draft puts a choice to it (time_candidates).
@SuppressWarnings("unchecked")
class TestE2eEventTime extends AbstractE2eTest {

    private static final Pattern TIME_CREATED = Pattern.compile("<TimeCreated>([^<]+)</TimeCreated>");

    // T2.
    @Test
    void cefsOwnTimeIsTheEventsBeforeTheRelays() {
        timesAsPlanted("cef-own", EventTimeFeed.cefOwnTimeEvery(), UnaryOperator.identity());
    }

    // T3.
    @Test
    void recordsWithoutTheirOwnTimeHaveTheRelays() {
        timesAsPlanted("cef-some", EventTimeFeed.cefOwnTimeSome(), UnaryOperator.identity());
    }

    // T5.
    @Test
    void anObjectsTimesAreNotTheEvents() {
        final Map<String, Object> draft = timesAsPlanted("objects", EventTimeFeed.objectTimes(),
                UnaryOperator.identity());
        assertThat(JsonUtil.writeValueAsString(map(draft, "mapping"), false)).contains("created_at", "expires");
    }

    // T7.
    @Test
    void theAgentChoosesAKindsTimeFromTheCandidates() {
        final Map<String, Object> draft = timesAsPlanted("kinds", EventTimeFeed.twoKinds(), mapping -> {
            // The agent's choice: a session's end, when it was logged.
            for (final Map<String, Object> rule : (List<Map<String, Object>>) mapping.get("events")) {
                if (String.valueOf(rule.get("name")).endsWith("session")) {
                    for (final Map<String, Object> field : (List<Map<String, Object>>) rule.get("fields")) {
                        if ("EventTime/TimeCreated".equals(field.get("path"))) {
                            field.put("field", "end");
                            field.remove("choose_from");
                        }
                    }
                }
            }
            return mapping;
        });
        final List<Map<String, Object>> candidates = list(draft, "time_candidates");
        assertThat(candidates).as("the choice put to the agent").hasSize(1);
        assertThat(String.valueOf(candidates.getFirst().get("rule"))).endsWith("session");
    }

    // T11.
    @Test
    void eachFormatInAMixedStreamHasItsOwnTime() {
        timesAsPlanted("mixed", EventTimeFeed.mixed(), UnaryOperator.identity());
    }

    // The stream onboarded as the agent would, the user saying the source writes in UTC; every record's
    // TimeCreated is the time planted in it. Returns the draft.
    private Map<String, Object> timesAsPlanted(final String name,
                                               final EventTimeFeed.Feed feed,
                                               final UnaryOperator<Map<String, Object>> agent) {
        final String build = "e2e-time-" + name;
        final String feedName = "E2E-TIME-" + name.toUpperCase();
        createFeed(build, feedName);
        final long stream = upload(feedName, feed.text());
        final List<Long> raw = List.of(stream);
        // JSON lines need no Data Splitter: a template with a JSON parser reads them.
        final boolean jsonLines = feed.text().startsWith("{");
        Object splitter = null;
        if (!jsonLines) {
            final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", raw, "save_as", feedName,
                    "build", build));
            dump("build_data_splitter", ds);
            assertThat(ds.get("saved")).as("splitter saved: %s", ds.get("not_saved")).isNotNull();
            splitter = ds.get("spec");
        }
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw,
                "source_name", "Event times " + name, "system_name", "Event Times", "environment", "Test",
                "splitter", splitter, "timezone", "UTC"));
        dump("draft_translation_mapping", draft);
        if (draft.get("time_candidates") != null) {
            // Not saved until the agent chooses each time the draft put to it.
            assertThat(refusal("build_translation_xslt", args("mapping", keptUnknown(map(draft, "mapping")),
                    "schema_version", draft.get("schema_version"), "stream_ids", raw, "splitter", splitter, "build",
                    build, "name", feedName + "-Events"))).contains("still has choose_from");
        }
        final Map<String, Object> mapping = agent.apply(keptUnknown(map(draft, "mapping")));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", mapping,
                "schema_version", draft.get("schema_version"), "stream_ids", raw, "splitter", splitter,
                "build", build, "name", feedName + "-Events"));
        assertThat(saved).as("the mapping checked against the sample and saved: %s", saved.get("problems"))
                .containsEntry("ok", true);
        final String pipelineUuid = (String) json("create_pipeline", args("build", build, "name",
                feedName + "-Events", "template_uuid", templateUuid("translation", jsonLines
                        ? "Event Data (JSON)"
                        : "Event Data (Text)")))
                .get("uuid");
        final List<String> events = eventsOf(pipelineUuid, stream, IntStream.range(0, EventTimeFeed.RECORDS)
                .boxed()
                .toList());
        final List<Instant> written = new ArrayList<>();
        for (final String event : events) {
            final Matcher matcher = TIME_CREATED.matcher(event);
            written.add(matcher.find()
                    ? Instant.parse(matcher.group(1))
                    : null);
        }
        assertThat(written).as("each record's TimeCreated").isEqualTo(feed.times());
        return draft;
    }

    // Kinds the draft left Unknown kept so, and a device where the draft has none: the test is of times, not of the
    // action elements chosen.
    private static Map<String, Object> keptUnknown(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Object> common = new ArrayList<>((List<Object>) mapping.get("common"));
        if (!String.valueOf(common).contains("EventSource/Device/")) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "relay1"));
        }
        mapping.put("common", common);
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final Map<String, Object> rule : (List<Map<String, Object>>) mapping.get("events")) {
            final Map<String, Object> kept = new LinkedHashMap<>(rule);
            if (String.valueOf(rule.get("fields")).contains("/Unknown/") || !kept.containsKey("fields")) {
                kept.put("allow_unknown", "kept Unknown for the test");
            }
            events.add(kept);
        }
        mapping.put("events", events);
        return mapping;
    }
}
