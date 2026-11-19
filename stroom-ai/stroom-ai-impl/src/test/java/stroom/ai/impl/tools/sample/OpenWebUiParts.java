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

package stroom.ai.impl.tools.sample;

/// Parts of an OpenWebUI audit log stream as filebeat wrote them (seen in user testing): each part one JSON array on
/// one line, no newline anywhere, holding filebeat events whose `message` is a JSON string, its `request_object`
/// long escaped content with `\\uXXXX` escapes in it.
public final class OpenWebUiParts {

    /// The most characters a sample's part is read to when a stream has 20 parts or more.
    public static final int PART_READ = 100_000;
    /// Where a planted `\\u00e9` escape starts, so a part cut at [#PART_READ] ends inside it (its `u` at 99,996).
    public static final int ESCAPE_AT = PART_READ - 5;
    /// The planted escape.
    public static final String ESCAPE = "\\u00e9";

    private static final String PREFIX = "[{\"@timestamp\":\"2026-11-18T09:00:00.000Z\",\"event\":{\"kind\":\"event\"},"
                                         + "\"message\":\"{\\\"user\\\":\\\"u1\\\",\\\"request_object\\\":\\\"";
    private static final String SUFFIX = "\\\"}\"}]";
    // A run of the request's content between two of its escapes.
    private static final int RUN = 994;

    private OpenWebUiParts() {
        // Static only.
    }

    /// @param length   The part's length in characters.
    /// @param escapeAt Where the planted [#ESCAPE] starts, or -1 for none.
    /// @return A part: a JSON array of one event, newline-free, exactly `length` characters long.
    public static String part(final int length, final int escapeAt) {
        final StringBuilder text = new StringBuilder(PREFIX);
        final int escapesUntil = escapeAt < 0
                ? length - SUFFIX.length()
                : escapeAt;
        // The content's own escapes, as the source writes a '<'.
        while (text.length() + RUN + 6 + 10 < escapesUntil) {
            text.append("x".repeat(RUN)).append("\\u003c");
        }
        if (escapeAt >= 0) {
            text.append("x".repeat(escapeAt - text.length())).append(ESCAPE);
        }
        text.append("x".repeat(length - SUFFIX.length() - text.length())).append(SUFFIX);
        return text.toString();
    }
}
