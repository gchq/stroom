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

/// Whether JSON that doesn't parse was only cut short (a read stopped at its limit) rather than being invalid: read
/// through tracking nesting, strings and escapes, it is cut if it ends with a string or container still open, no
/// escape before the end is wrong, and the parse failed in its last record. Seen in user testing: a one-line JSON
/// array read to 100,000 characters, cut inside a `\\u` escape, refused as "isn't valid JSON: Invalid \\uXXXX
/// escape".
final class JsonCut {

    private static final String SIMPLE_ESCAPES = "\"\\/bfnrt";

    private JsonCut() {
        // Static methods only.
    }

    /// @param text     JSON that doesn't parse, from its first character.
    /// @param errorPos Where the parse failed.
    /// @return True if it was only cut short: it ends open, with no wrong escape, the failure in its last record (a
    /// JSON array's last item, or anywhere in other JSON).
    static boolean cutShort(final String text, final int errorPos) {
        final Scan scan = scan(text);
        return scan.open() && scan.badEscapeAt() < 0 && errorPos >= scan.lastRecordStart();
    }

    /// @param text JSON, from its first character.
    /// @return Where it was left at its end.
    static Scan scan(final String text) {
        int at = 0;
        while (at < text.length() && (Character.isWhitespace(text.charAt(at)) || text.charAt(at) == '﻿')) {
            at++;
        }
        final boolean array = at < text.length() && text.charAt(at) == '[';
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int unicodeLeft = 0;
        boolean expectRecord = false;
        int lastRecordStart = 0;
        for (int i = at; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                if (unicodeLeft > 0) {
                    if (Character.digit(c, 16) < 0) {
                        return new Scan(depth > 0, i, lastRecordStart);
                    }
                    unicodeLeft--;
                } else if (escape) {
                    escape = false;
                    if (c == 'u') {
                        unicodeLeft = 4;
                    } else if (SIMPLE_ESCAPES.indexOf(c) < 0) {
                        return new Scan(depth > 0, i - 1, lastRecordStart);
                    }
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (array && depth == 1 && expectRecord && c != ']' && c != ',') {
                lastRecordStart = i;
                expectRecord = false;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    depth++;
                    expectRecord = array && depth == 1;
                }
                case '}', ']' -> depth = Math.max(0, depth - 1);
                case ',' -> expectRecord = array && depth == 1;
                default -> {
                    // Numbers and literals.
                }
            }
        }
        return new Scan(inString || depth > 0, -1, lastRecordStart);
    }


    // --------------------------------------------------------------------------------


    /// Where JSON was left at its end.
    ///
    /// @param open            True if it ended with a string or container still open.
    /// @param badEscapeAt     Where a wrong escape starts (scanning stops there), or -1 for none.
    /// @param lastRecordStart Where its last record starts: a JSON array's last item, else 0.
    record Scan(boolean open, int badEscapeAt, int lastRecordStart) {

    }
}
