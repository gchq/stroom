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
import stroom.meta.impl.MetaValueDao;
import stroom.meta.shared.Meta;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/// Onboarding a sample far larger than any model's context (200,000 CSV records, about 15 MB) through the REAL tools on
/// a real Stroom. The file reaches Stroom as the source would send it, never through the model, and the agent works
/// from its stream id. Every reply is measured: none may exceed a budget of 64,000 characters of JSON, whatever the
/// file's size. The tools read a bounded part of the stream to profile, draft and check; stepping takes a bounded
/// number of records; Stroom processes the whole stream itself, and every record has to come out as an event.
///
/// The file is written to the stream store as Stroom's receiver stores data posted to its datafeed (an in-process
/// Stroom has no HTTP listener for it).
@SuppressWarnings("unchecked")
class TestE2eLargeSample extends AbstractE2eTest {

    private static final int RECORDS = 200_000;
    private static final int REPLY_BUDGET = 64_000;
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "frank", "grace",
            "heidi");
    private static final String BUILD = "e2e-large";
    private static final String FEED = "E2E-LARGE";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private MetaValueDao metaValueDao;

    private final Map<String, Integer> sizes = new LinkedHashMap<>();

    @Test
    void aSampleLargerThanAnyContextIsOnboardedWithBoundedReplies() {
        // The file: sent to Stroom as the source would, never to the model.
        createFeed(BUILD, FEED);
        final String data = bigCsv();
        final long raw = send(data);

        // The agent works from the stream id; every reply measured.
        final Map<String, Object> profile = metered("profile_sample", args("stream_ids", List.of(raw)));
        assertThat(profile.get("format")).as("profiled from a bounded read").isIn("delimited", "csv");
        final Map<String, Object> draft = metered("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Acme VPN gateway", "system_name", "Acme VPN", "environment", "Eval"));
        // As the agent does with the draft's notes: the gateway column is the device (the draft could not tell).
        assertThat(strings(draft, "notes")).anySatisfy(note ->
                assertThat(note).contains("No host field was recognised"));
        final Map<String, Object> mapping = new LinkedHashMap<>(map(draft, "mapping"));
        final List<Object> common = new ArrayList<>(list(mapping, "common"));
        common.add(Map.of("path", "EventSource/Device/HostName", "field", "gateway"));
        mapping.put("common", common);
        json("save_text_converter", args("build", BUILD, "name", FEED, "converter_type", "DATA_SPLITTER",
                "code", CSV_SPLITTER));
        Map<String, Object> saved = metered("build_translation_xslt", args("mapping", mapping,
                "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw), "build", BUILD,
                "name", FEED + "-Events"));
        while ("needs_review".equals(saved.get("status"))) {
            saved = metered("build_translation_xslt", args("mapping", mapping,
                    "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw), "build", BUILD,
                    "name", FEED + "-Events", "reviewed", saved.get("review_id")));
        }
        assertThat(saved).as("translation saved: %s", saved.get("problems")).containsEntry("ok", true);
        assertThat(saved.get("saved")).isNotNull();
        final Map<String, Object> pipeline = metered("create_pipeline", args("build", BUILD,
                "name", FEED + "-Events", "template_uuid", templateUuid("translation", "Event Data (Text)")));
        final String pipelineUuid = (String) pipeline.get("uuid");
        final Map<String, Object> stepped = metered("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(stepped).as("stepped %s records (a bounded number)", stepped.get("records_stepped"))
                .containsEntry("verdict", "clean");
        final Map<String, Object> started = metered("create_processor_filter", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> done = metered("wait_for_processing", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw), "timeout_seconds", 900, "filter_id", started.get("filter_id")));
        final List<Object> events = (List<Object>) ((Map<String, Object>) list(done, "streams").getFirst())
                .get("events");
        assertThat(done).as("the whole stream processed by Stroom: %s", done.get("streams"))
                .containsEntry("gate", "pass");
        assertThat(events).isNotEmpty();
        // Stroom records a stream's counts a moment after the stream completes (its meta values are flushed by a
        // job, run here as the test stands in for Stroom's jobs).
        Map<String, Object> described = metered("stream_meta", args("streamId",
                String.valueOf(number(events.getFirst()))));
        for (int attempt = 0; attempt < 20 && !described.containsKey("processing"); attempt++) {
            metaValueDao.flush();
            described = metered("stream_meta", args("streamId", String.valueOf(number(events.getFirst()))));
        }
        // Every record became an event, read from the stream's counts, not its text.
        final Map<String, Object> counts = map(described, "processing");
        assertThat(number(counts.get("records_read"))).isEqualTo(RECORDS);
        assertThat(number(counts.get("records_written"))).isEqualTo(RECORDS);
        assertThat(counts.get("errors") == null || number(counts.get("errors")) == 0).isTrue();
        assertThat(counts.get("fatal_errors") == null || number(counts.get("fatal_errors")) == 0).isTrue();

        // No reply grew with the file.
        dump("reply sizes", sizes);
        assertThat(sizes).allSatisfy((tool, size) -> assertThat(size)
                .as("%s's reply, for a file of %s bytes", tool, data.length())
                .isLessThanOrEqualTo(REPLY_BUDGET));
    }

    // A tool reply, its size as the model would receive it recorded.
    private Map<String, Object> metered(final String tool, final Map<String, Object> arguments) {
        final Map<String, Object> result = json(tool, arguments);
        sizes.merge(tool, JsonUtil.writeValueAsString(result, false).length(), Math::max);
        return result;
    }

    // The file stored in the feed as Stroom's receiver stores data posted to its datafeed.
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

    // A VPN gateway's log: one logon or logoff per line, success or failure.
    private static String bigCsv() {
        final Random random = new Random(7);
        final long start = 1_790_000_000L;
        final StringBuilder sb = new StringBuilder("time,user,src_ip,gateway,action,result\n");
        final List<String> actions = List.of("login", "login", "logout");
        final List<String> results = List.of("success", "success", "success", "failure");
        for (int n = 0; n < RECORDS; n++) {
            sb.append(TIME.format(Instant.ofEpochSecond(start + n * 3L))).append(',')
                    .append(USERS.get(random.nextInt(USERS.size()))).append(",10.")
                    .append(random.nextInt(256)).append('.').append(random.nextInt(256)).append('.')
                    .append(1 + random.nextInt(254)).append(",vpn-gw").append(1 + random.nextInt(3)).append(',')
                    .append(actions.get(random.nextInt(actions.size()))).append(',')
                    .append(results.get(random.nextInt(results.size()))).append('\n');
        }
        return sb.toString();
    }
}
