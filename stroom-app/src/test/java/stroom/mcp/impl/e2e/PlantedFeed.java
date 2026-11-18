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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/// A mail gateway's key=value log, large enough that the tools read only part of it, with features planted where one
/// way of sampling or another passes them by. Seen in testing (a Secure Email Gateway, then a web proxy): a quoted
/// list of 168 recipients overflowed the stack of the splitter's regex, and a byte count the Events held early in their
/// stream was documented as "not in the sample" once the tools read events spread over the stream rather than its
/// head.
///
/// The same text every run (seeded). With [#RECORDS] records and 200 read, events spread over a stream are read 100
/// apart, so the planted records are placed off that grid.
final class PlantedFeed {

    /// Records in the first stream.
    static final int RECORDS = 20_000;
    /// Records in the second stream.
    static final int SECOND_RECORDS = 5_000;
    /// Records in the stream sent after onboarding.
    static final int LATER_RECORDS = 2_000;

    /// A field only near the head of the first stream, between the records read spread over it.
    static final String HEAD_FIELD = "inbound_size";
    /// Where the head-only field is.
    static final Set<Integer> HEAD_RECORDS = Set.of(7, 23, 51);
    /// A field only mid-way through the first stream, outside its head and between the records read spread over it.
    static final String MID_FIELD = "dlp_policy";
    /// Where the mid-stream field is.
    static final Set<Integer> MID_RECORDS = Set.of(10_037, 10_063);
    /// A kind of record (its action) only near the tail of the first stream.
    static final String TAIL_ACTION = "quarantine";
    /// Where the tail-only kind starts.
    static final int TAIL_FROM = 19_000;
    /// The records whose recipients are a long quoted list.
    static final Set<Integer> LONG_RECORDS = Set.of(5, 9_001, 18_503);
    /// How many recipients a long list has.
    static final int LONG_RECIPIENTS = 168;
    /// A field only in the stream sent after onboarding.
    static final String LATER_FIELD = "tls_version";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final List<String> TLDS = List.of("com", "org", "net", "co.uk", "gov.au", "io", "de", "fr",
            "info", "edu");
    private static final List<String> USERS = List.of("jbloggs", "asmith", "bjones", "cwong", "dpatel");

    private PlantedFeed() {
        // Static only.
    }

    /// @return The first stream: every planted feature but the later one.
    static String first() {
        return records(RECORDS, 1, false);
    }

    /// @return The second stream: the common kinds only, one long list.
    static String second() {
        return records(SECOND_RECORDS, 2, false);
    }

    /// @return A stream sent after onboarding, every record with [#LATER_FIELD].
    static String later() {
        return records(LATER_RECORDS, 3, true);
    }

    /// @param count The records.
    /// @return A long quoted list of recipients, across many domains.
    static String recipients(final int count) {
        final List<String> list = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            list.add("user." + r + "@domain" + (r % 37) + ".example." + TLDS.get(r % TLDS.size()));
        }
        return String.join(",", list);
    }

    private static String records(final int count, final int stream, final boolean later) {
        final Random random = new Random(stream * 31L);
        final long start = 1_790_000_000_000L + stream * 100_000_000L;
        final StringBuilder sb = new StringBuilder(count * 220);
        for (int n = 0; n < count; n++) {
            final boolean first = stream == 1;
            final String action = first && n >= TAIL_FROM
                    ? TAIL_ACTION
                    : random.nextInt(10) < 7
                            ? "deliver"
                            : "reject";
            final String user = USERS.get(random.nextInt(USERS.size()));
            sb.append(TIME.format(Instant.ofEpochMilli(start + n * 1_000L)))
                    .append(" seg01 id=\"q").append(String.format("%09d", stream * 1_000_000 + n)).append('"')
                    .append(" action=").append(action)
                    .append(" sender=").append(user).append("@example.com")
                    .append(" recipients=\"")
                    .append(first && LONG_RECORDS.contains(n) || stream == 2 && n == 2_111
                            ? recipients(LONG_RECIPIENTS)
                            : recipients(1 + random.nextInt(3)).replace("user.", user + "."))
                    .append('"')
                    .append(" user=EXAMPLE\\").append(user)
                    .append(" areaname=\"%AREANAME%\"")
                    .append(" size=").append(1_000 + random.nextInt(90_000));
            if ("reject".equals(action)) {
                sb.append(" reason=\"spam score ").append(5 + random.nextInt(5)).append('"');
            } else if (TAIL_ACTION.equals(action)) {
                sb.append(" folder=\"Q").append(1 + random.nextInt(3)).append('"');
            }
            sb.append(" status=").append(random.nextInt(20) == 0
                    ? "-"
                    : "ok");
            if (first && HEAD_RECORDS.contains(n)) {
                sb.append(' ').append(HEAD_FIELD).append('=').append(500 + n);
            }
            if (first && MID_RECORDS.contains(n)) {
                sb.append(' ').append(MID_FIELD).append("=\"PCI\"");
            }
            if (later) {
                sb.append(' ').append(LATER_FIELD).append("=1.3");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
