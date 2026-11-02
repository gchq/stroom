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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A raw stream's longest record, read through whole from Stroom's store (asked for by the user: a converter whose
/// buffer was sized from the start of a 159 MB stream lost records of over 105,000 characters further in, and the
/// agent had no way to find the longest). measure_records gives it, and build_data_splitter sizes the buffer from
/// it, past the text it profiles. Driving the REAL tools on a real Stroom.
@SuppressWarnings("unchecked")
class TestE2eMeasureRecords extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    // Short records past the 1,000,000 characters build_data_splitter profiles, then one of 150,000.
    private static final String CSV = "time,user,url\n"
                                       + "2026-10-01T10:00:00Z,alice,https://example.org/a\n".repeat(25_000)
                                       + "2026-10-01T10:05:00Z,bob,https://example.org/" + "x".repeat(150_000) + "\n"
                                       + "2026-10-01T10:09:00Z,carol,https://example.org/c\n";
    // A quoted value spanning lines: one record of all of them.
    private static final String QUOTED = "id,note\n1,\"first line\n" + "a line of the note\n".repeat(2_000)
                                         + "last\"\n2,short\n";

    @Test
    void theLongestRecordOfTheWholeStreamSizesTheBuffer() {
        final String feed = "MEASURE-" + STAMP;
        createFeed("measure-" + STAMP, feed);
        final long raw = upload(feed, CSV);

        final Map<String, Object> measured = json("measure_records", args("stream_ids", List.of(raw)));

        dump("measure_records", measured);
        assertThat(measured).containsEntry("longest", 150_045).containsEntry("buffer_size", 610_000)
                .containsEntry("complete", true);
        assertThat(list(measured, "streams")).singleElement().satisfies(stream -> {
            assertThat(stream).containsEntry("lines", 25_003L);
            assertThat(map(stream, "longest_line")).containsEntry("chars", 150_045).containsEntry("line", 25_002L);
        });

        final Map<String, Object> splitter = json("build_data_splitter", args("stream_ids", List.of(raw)));
        dump("build_data_splitter", splitter);
        assertThat(splitter).as("sized from the whole stream, not the text profiled")
                .containsEntry("longest_record", 150_045).containsEntry("buffer_size", 610_000);
        assertThat((String) splitter.get("converter")).contains("bufferSize=\"610000\"");
    }

    @Test
    void aQuotedValueSpanningLinesIsMeasuredAsOneRecord() {
        final String feed = "MEASURE-QUOTED-" + STAMP;
        createFeed("measure-quoted-" + STAMP, feed);
        final long raw = upload(feed, QUOTED);

        final Map<String, Object> lines = json("measure_records", args("stream_ids", List.of(raw)));
        final Map<String, Object> records = json("measure_records", args("stream_ids", List.of(raw),
                "quote", "\""));

        dump("measure_records quoted", records);
        assertThat(lines).as("each line on its own, Stroom's default holds").doesNotContainKey("buffer_size");
        final Map<String, Object> stream = list(records, "streams").getFirst();
        assertThat(map(stream, "longest_record")).containsEntry("line", 2L)
                .containsEntry("chars", QUOTED.indexOf("2,short") - "id,note\n".length());
        assertThat(stream).containsEntry("records_over_lines", 1L);
        assertThat(records).containsEntry("buffer_size", 160_000);
    }
}
