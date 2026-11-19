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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A stream of many parts, each one JSON array of filebeat events on one line with no newline anywhere, profiled
/// through Stroom. Seen in user testing: an OpenWebUI audit log stream of 787 parts (about 67 MB), whose parts longer
/// than the 100,000 characters each was read to were cut inside a `\\u` escape, and the sampling tools refused the
/// sample: "This starts as JSON but isn't valid JSON: Invalid \\uXXXX escape at character 99996".
class TestE2eNewlineFreeJsonParts extends AbstractE2eTest {

    private static final String BUILD = "e2e-openwebui";
    private static final String FEED = "E2E-OPENWEBUI";
    // Enough parts that each is read to 100,000 characters (20 or more).
    private static final int PARTS = 25;
    // The characters each part is read to.
    private static final int CUT = 100_000;

    @Test
    void aSampleOfNewlineFreeJsonArraysIsProfiledAsAJsonArray() {
        createFeed(BUILD, FEED);
        final List<Map.Entry<Map<String, String>, String>> parts = new ArrayList<>();
        // The first part longer than its read, an escape where the read ends; the others shorter.
        parts.add(Map.entry(Map.of(), part(150_000, CUT - 5)));
        for (int part = 1; part < PARTS; part++) {
            parts.add(Map.entry(Map.of(), part(20_000, -1)));
        }
        final long raw = storeParts(FEED, parts);

        final String reply = replyText("profile_sample", args("stream_ids", List.of(raw)));

        assertThat(reply).doesNotContain("Invalid \\uXXXX escape").doesNotContain("isn't valid JSON")
                .contains("json array");
    }

    // A part of an OpenWebUI audit stream as filebeat wrote it: one JSON array of one event on one line, its message
    // a JSON string, exactly `length` characters, with a \\u00e9 escape starting at `escapeAt` (none if -1).
    private static String part(final int length, final int escapeAt) {
        final String prefix = "[{\"@timestamp\":\"2026-11-18T09:00:00.000Z\",\"event\":{\"kind\":\"event\"},"
                              + "\"message\":\"{\\\"user\\\":\\\"u1\\\",\\\"request_object\\\":\\\"";
        final String suffix = "\\\"}\"}]";
        final StringBuilder text = new StringBuilder(prefix);
        if (escapeAt >= 0) {
            text.append("x".repeat(escapeAt - text.length())).append("\\u00e9");
        }
        text.append("x".repeat(length - suffix.length() - text.length())).append(suffix);
        return text.toString();
    }
}
