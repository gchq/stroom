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

import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A large, awkward CSV built and processed end to end, as a real feed was (Netskope, 160 and 211 MB streams,
/// 2026-11-01): records far longer than Stroom's default read buffer, the longest well past the text a converter
/// is drafted from; quoted values holding the delimiter; and '-' for none. What each of those broke then, now
/// checked here: the buffer is sized from the whole stream (no record lost), the quote is in the spec (no value
/// shifted into the next field), '-' is counted as a placeholder, not a value, the sample streams are processed by
/// a filter allowing a task each, and processing with the large buffer takes seconds, not minutes (the blank-line
/// check once scanned the whole buffer for every record). Driving the REAL tools on a real Stroom.
@SuppressWarnings("unchecked")
class TestE2eLargeAwkwardCsv extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final int RECORDS = 30_000;
    // Further in than the first 1,000,000 characters a converter is drafted from.
    private static final int LONG_RECORD_AT = 25_000;
    private static final int LONG_URL = 750_000;
    // Far more than the processing takes with the anchored blank-line check; far less than it took without it.
    private static final long MAX_PROCESSING_MS = 180_000;

    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aLargeCsvOfLongRecordsQuotedCommasAndPlaceholdersIsBuiltAndProcessedWhole() {
        final String build = "large-csv-" + STAMP;
        final String feed = "LARGE-CSV-" + STAMP;
        createFeed(build, feed);
        final long first = upload(feed, csv("alice"));
        final long second = upload(feed, csv("bob"));
        final List<Long> streams = List.of(first, second);

        // The converter: the buffer from the whole streams, the quote inferred, '-' a placeholder.
        final Map<String, Object> splitter = agreed("build_data_splitter", args("stream_ids", streams,
                "save_as", feed + "-CSV", "build", build));
        dump("build_data_splitter", summary(splitter));
        final int longest = csv("alice").lines().mapToInt(String::length).max().orElseThrow();
        // Four times the longest record, rounded up to 10,000 characters.
        final int buffer = (int) ((4L * longest + 9_999) / 10_000 * 10_000);
        assertThat(splitter).as("the longest record of the whole stream, past the text profiled")
                .containsEntry("longest_record", longest)
                .containsEntry("buffer_size", buffer)
                .doesNotContainKey("wider_than_header")
                .doesNotContainKey("not_saved");
        assertThat(String.valueOf(((Map<String, Object>) splitter.get("spec")).get("quote"))).isEqualTo("\"");
        assertThat(list(splitter, "fields")).filteredOn(field -> "zone".equals(field.get("field"))).singleElement()
                .as("'-' is a placeholder, not a value")
                .satisfies(field -> assertThat(((Number) field.get("placeholder_rate")).doubleValue())
                        .isGreaterThan(0));
        final String converter = (String) map(splitter, "saved").get("uuid");

        // The translation: the group with its comma read whole.
        final String xslt = translation(build, feed, streams);
        final String pipeline = (String) createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", converter, "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, streams);
        assertThat(stepped.get("verdict")).as("stepped: %s", stepped.get("groups")).isEqualTo("clean");
        assertThat(translationOutput(pipeline, first, 0)).contains("Value=\"OU=Staff,DC=example\"");

        // Processed: a task allowed per stream, quickly, and nothing lost.
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", streams));
        final ProcessorFilter filter = securityContext.asProcessingUserResult(() -> processorFilterService
                .fetch((int) number(started.get("filter_id"))).orElseThrow());
        assertThat(filter.getMaxProcessingTasks()).isEqualTo(4);
        final long start = System.currentTimeMillis();
        processAll();
        final long took = System.currentTimeMillis() - start;
        dump("processing took ms", took);
        assertThat(took).as("the blank-line check doesn't scan the buffer for each record")
                .isLessThan(MAX_PROCESSING_MS);
        final Map<String, Object> done = json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", streams, "filter_id", started.get("filter_id"), "timeout_seconds", 30));
        dump("wait_for_processing", done);
        // A record lost to the buffer, or a value shifted into the wrong field, would leave an Error stream.
        assertThat(done).as("no record lost, no error: %s", done.get("problems"))
                .containsEntry("gate", "pass");
        assertThat(list(done, "streams")).hasSize(2).allSatisfy(stream -> {
            assertThat((List<?>) stream.get("events")).hasSize(1);
            assertThat((List<?>) stream.get("errors")).isEmpty();
        });
    }

    // RECORDS lines of CSV after its header: a group in quotes holding a comma, a zone of '-' every third record,
    // and one record of a 750,000-character URL further in than the text a converter is drafted from.
    private static String csv(final String user) {
        final StringBuilder csv = new StringBuilder("time,user,group,action,zone,url\n");
        for (int n = 0; n < RECORDS; n++) {
            csv.append("2026-10-01T10:00:00Z,").append(user).append(n).append(",\"OU=Staff,DC=example\",login,")
                    .append(n % 3 == 0
                            ? "-"
                            : "internal")
                    .append(",https://example.org/")
                    .append(n == LONG_RECORD_AT
                            ? "x".repeat(LONG_URL)
                            : "page" + n)
                    .append('\n');
        }
        return csv.toString();
    }

    // A translation of each record to a logon, the group carried as Data.
    private String translation(final String build, final String feed, final List<Long> streams) {
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", streams,
                "build", build, "schema_version", VERSION));
        final Map<String, Object> mapping = new LinkedHashMap<>((Map<String, Object>) draft.get("mapping"));
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : (List<Map<String, Object>>) mapping.get("common")) {
            if (!"EventSource/User/Id".equals(entry.get("path"))) {
                common.add(entry);
            }
        }
        common.add(Map.of("path", "EventSource/User/Id", "field", "user"));
        if (common.stream().noneMatch(entry -> String.valueOf(entry.get("path")).startsWith("EventSource/Device"))) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "proxy01"));
        }
        mapping.put("common", common);
        mapping.put("events", List.of(Map.of("name", "logon",
                "when", List.of(Map.of("field", "action", "equals", "login")),
                "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user"),
                        Map.of("path", "EventDetail/Authenticate/Data", "data_name", "group", "field", "group")))));
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping,
                "stream_ids", streams, "build", build, "name", feed + "-Events", "schema_version", VERSION));
        assertThat(built.get("ok")).as("built: %s", built.get("problems")).isEqualTo(true);
        return (String) map(built, "saved").get("uuid");
    }

    // A result without its records, to log.
    private static Map<String, Object> summary(final Map<String, Object> result) {
        final Map<String, Object> summary = new LinkedHashMap<>(result);
        summary.remove("first_records");
        summary.remove("converter");
        return summary;
    }
}
