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

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.Meta;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A mail gateway's feed onboarded with features planted where sampling passes them by ([PlantedFeed]): every one of
/// them has to be found, mapped, and not reported missing, however rare. Seen in testing: a long quoted list of
/// recipients overflowed the stack of the splitter's regex (in the tools' checks and in Stroom's Data Splitter), and a
/// field the Events held early in their stream was documented as "not in the sample".
@SuppressWarnings("unchecked")
class TestE2ePlantedFeatures extends AbstractE2eTest {

    private static final String BUILD = "e2e-planted";
    private static final String FEED = "E2E-PLANTED";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void plantedFeaturesAreFoundAndMappedHoweverRare() {
        // What sampling may miss is checked softly, so one run says everything it missed; a tool failing stops it.
        final SoftAssertions softly = new SoftAssertions();
        createFeed(BUILD, FEED);
        final long first = send(PlantedFeed.first());
        final long second = send(PlantedFeed.second());
        final List<Long> raw = List.of(first, second);

        // ### Surveyed: every kind of record, the rare ones too.
        Map<String, Object> survey = json("survey_feed", args("feed", FEED, "build", BUILD));
        dump("survey_feed", survey);
        String surveyed = JsonUtil.writeValueAsString(survey, false);
        if (String.valueOf(survey.get("coverage")).startsWith("NOT COVERED")) {
            // As the agent does: surveyed again as the coverage says, with what it already found.
            survey = json("survey_feed", args("feed", FEED, "build", BUILD, "stream_ids", List.of(first),
                    "max_chars_per_stream", 5_000_000, "known_signatures", signatures(survey)));
            dump("survey_feed again", survey);
            surveyed += JsonUtil.writeValueAsString(survey, false);
        }
        softly.assertThat(surveyed).as("the survey read the head-only field").contains(PlantedFeed.HEAD_FIELD);
        softly.assertThat(surveyed).as("the survey read the tail-only kind").contains(PlantedFeed.TAIL_ACTION);
        // The mid-stream field (2 records in 20,000) may be missed by sampling: processing reports it (below).
        dump("survey read the mid-stream field", surveyed.contains(PlantedFeed.MID_FIELD));

        // ### The splitter, built and saved, reads the long quoted lists (Stroom's Data Splitter too).
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", raw, "save_as", FEED,
                "build", BUILD));
        dump("build_data_splitter", ds);
        assertThat(ds.get("saved")).as("splitter saved: %s", ds.get("not_saved")).isNotNull();
        final Object splitter = ds.get("spec");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw,
                "source_name", "Mail gateway", "system_name", "Mail Gateway", "environment", "Test",
                "splitter", splitter));
        dump("draft_translation_mapping", draft);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping",
                decide(map(draft, "mapping")),
                "schema_version", draft.get("schema_version"), "stream_ids", raw, "splitter", splitter,
                "build", BUILD, "name", FEED + "-Events"));
        assertThat(saved).as("the mapping checked against the sample and saved: %s", saved.get("problems"))
                .containsEntry("ok", true);
        final String mapped = JsonUtil.writeValueAsString(keptMapping(uuidOf(saved)), false);
        softly.assertThat(mapped).as("the head-only field mapped").contains(PlantedFeed.HEAD_FIELD);
        final String pipelineUuid = (String) json("create_pipeline", args("build", BUILD, "name", FEED + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (Text)"))).get("uuid");
        final Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", raw));
        dump("step_sample", stepped);
        assertThat(stepped).as("stepped in Stroom: %s", stepped.get("groups")).containsEntry("verdict", "clean");

        // ### Processed: every record an Event, the long lists too.
        final Map<String, Object> gate = process(pipelineUuid, raw);
        dump("wait_for_processing", gate);
        assertThat(gate).as("processed: %s", gate.get("streams")).containsEntry("gate", "pass");
        final List<Long> events = list(gate, "streams").stream()
                .flatMap(stream -> strings(stream, "events").stream())
                .map(Long::parseLong)
                .toList();
        assertThat(events).hasSize(2);

        // ### The tail-only kind is in what the Events are said to hold.
        final Map<String, Object> summary = json("summarise_streams", args("stream_ids", events, "kind", "events"));
        dump("summarise_streams", summary);
        softly.assertThat(JsonUtil.writeValueAsString(summary, false)).as("the tail-only kind summarised")
                .contains(PlantedFeed.TAIL_ACTION);

        // ### Indexed: a field the translation writes is never called absent, however rare.
        final String headSource = dataSource(mapped, PlantedFeed.HEAD_FIELD);
        final Map<String, Object> plan = json("draft_index_mapping", args("backend", "lucene",
                "index_name", "E2E-PLANTED-INDEX", "convention", "stroom-flat", "events_stream_ids", events,
                "extra_fields", List.of(Map.of("name", "InboundSize", "type", "long", "source", headSource))));
        dump("draft_index_mapping", plan);
        softly.assertThat(strings(plan, "fields_not_in_sample"))
                .as("the head-only field, which the translation writes")
                .noneMatch(field -> field.startsWith("InboundSize"));

        // ### Sent later: a field the translation doesn't read is reported by processing, by name.
        final long later = send(PlantedFeed.later());
        final Map<String, Object> laterGate = process(pipelineUuid, List.of(later));
        dump("wait_for_processing later", laterGate);
        final Map<String, Object> errors = json("summarise_streams", args("stream_ids", List.of(later),
                "kind", "errors"));
        dump("summarise_streams errors", errors);
        softly.assertThat(JsonUtil.writeValueAsString(errors, false)).as("the later stream's unread field reported")
                .contains(PlantedFeed.LATER_FIELD);
        // The mid-stream field: mapped, or reported by processing the same way.
        final Map<String, Object> rawErrors = json("summarise_streams", args("stream_ids", raw, "kind", "errors"));
        dump("summarise_streams raw errors", rawErrors);
        final String reported = JsonUtil.writeValueAsString(rawErrors, false);
        softly.assertThat(mapped.contains(PlantedFeed.MID_FIELD) || reported.contains(PlantedFeed.MID_FIELD))
                .as("the mid-stream field mapped or reported").isTrue();
        softly.assertAll();
    }

    private static List<String> signatures(final Map<String, Object> survey) {
        return list(survey, "shapes").stream().map(shape -> String.valueOf(shape.get("signature"))).toList();
    }

    // The agent's decisions beyond the draft: the gateway as the device, and the mail kinds kept as Unknown (the test
    // is of what is read, not of the action elements chosen).
    private static Map<String, Object> decide(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Object> common = new ArrayList<>((List<Object>) mapping.get("common"));
        if (!String.valueOf(common).contains("EventSource/Device/")) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "seg01"));
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

    // Where the translation writes a Data entry the field became: its path and Name.
    private static String dataSource(final String mapping, final String field) {
        return "EventDetail//Data[@Name='" + field + "']/@Value";
    }

    // The text stored in the feed as Stroom's receiver stores data posted to its datafeed.
    private long send(final String data) {
        return securityContext.asProcessingUserResult(() -> {
            final MetaProperties properties = MetaProperties.builder()
                    .feedName(FEED)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(System.currentTimeMillis())
                    .build();
            try (final Target target = streamStore.openTarget(properties)) {
                TargetUtil.write(target, data);
                target.getAttributes().put(StandardHeaderArguments.FEED, FEED);
                final Meta meta = target.getMeta();
                return meta.getId();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
