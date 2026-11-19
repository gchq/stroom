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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/// The events (top-level items) of a text that is one JSON array, read through from its start a character at a
/// time, tracking only nesting and string and escape state (nothing is parsed), so an array of any size, on one line
/// or many, can be sampled. An event longer than the most kept is read through to its end without being held, and
/// left out: no event kept is ever cut. Seen in user testing: an OpenWebUI audit stream whose every part was one
/// newline-free JSON array, a read of a part's first 100,000 characters cut inside a `\\u` escape, and the sampling
/// tools refused the cut text as "isn't valid JSON".
public final class JsonArrayStream {

    private static final int CHUNK = 64 * 1024;
    private static final char BYTE_ORDER_MARK = '﻿';

    private JsonArrayStream() {
        // Static methods only.
    }

    /// Reads the array's events, stopping when the budget is used, the array ends, or time is up.
    ///
    /// @param reader        The text, from its start (white space and a byte order mark before the `[` allowed).
    /// @param maxEventChars The longest event kept: a longer one is left out.
    /// @param budget        The most characters of events kept: an event that doesn't fit is left for a later read,
    ///                      and reading stops there.
    /// @param timeIsUp      Says when to stop, with what is read so far.
    /// @return The events kept, and what was left out.
    /// @throws IOException if the text can't be read.
    public static Read read(final Reader reader,
                            final int maxEventChars,
                            final int budget,
                            final BooleanSupplier timeIsUp) throws IOException {
        final Scanner scanner = new Scanner(maxEventChars, budget);
        final char[] chunk = new char[CHUNK];
        int read;
        while ((read = reader.read(chunk)) >= 0) {
            for (int i = 0; i < read; i++) {
                if (scanner.accept(chunk[i])) {
                    return scanner.read(false, false);
                }
            }
            if (timeIsUp.getAsBoolean()) {
                return scanner.read(false, true);
            }
        }
        scanner.end();
        return scanner.read(true, false);
    }


    // --------------------------------------------------------------------------------


    /// What was read of the array.
    ///
    /// @param events             The events kept, each whole, in order.
    /// @param seen               How many events were read through, kept or not.
    /// @param skipped            How many were left out as longer than the most kept.
    /// @param largestSkipped     The length of the longest left out; 0 if none.
    /// @param longestSkippedText The decoded length of the longest string value in those left out; 0 if none.
    /// @param longestSkippedPath The field holding it (as [JsonStringValues] names fields), or null if unknown.
    /// @param unterminated       True if the text ended inside an event, which is left out.
    /// @param unterminatedChars  How long that cut-off event was, as far as it went.
    /// @param closed             True if the array's closing `]` was read.
    /// @param trailing           True if more than white space follows the array's end: not one array (JSON lines
    ///                           of arrays, say), so it is better read a line at a time.
    /// @param notArray           True if the text doesn't start with `[`; nothing was read.
    /// @param ended              True if the text was read to its end (or to the array's end).
    /// @param timedOut           True if time ran out first.
    /// @param full               True if reading stopped at the most characters kept.
    public record Read(List<String> events,
                       long seen,
                       int skipped,
                       long largestSkipped,
                       long longestSkippedText,
                       String longestSkippedPath,
                       boolean unterminated,
                       long unterminatedChars,
                       boolean closed,
                       boolean trailing,
                       boolean notArray,
                       boolean ended,
                       boolean timedOut,
                       boolean full) {

        /// @return The events kept as one JSON array, `[e1,e2,...]`: a valid array of whole events.
        public String text() {
            return "[" + String.join(",", events) + "]";
        }

        /// @return The characters of the events kept.
        public long keptChars() {
            return events.stream().mapToLong(String::length).sum();
        }
    }

    // Splits the array into its events as the characters go by.
    private static final class Scanner {

        private final int maxEventChars;
        private final int budget;
        private final List<String> events = new ArrayList<>();
        private final StringBuilder event = new StringBuilder();
        private final LongestJsonString strings = new LongestJsonString();
        private State state = State.START;
        private boolean first = true;
        private long eventChars;
        private boolean skipping;
        private boolean scalar;
        private int depth;
        private boolean inString;
        private boolean escape;
        private long used;
        private long seen;
        private int skipped;
        private long largestSkipped;
        private long longestSkippedText;
        private String longestSkippedPath;
        private boolean unterminated;
        private long unterminatedChars;
        private boolean closed;
        private boolean trailing;
        private boolean notArray;
        private boolean full;

        private Scanner(final int maxEventChars, final int budget) {
            this.maxEventChars = maxEventChars;
            this.budget = budget;
        }

        // True once reading is to stop.
        private boolean accept(final char c) {
            final boolean atStart = first;
            first = false;
            return switch (state) {
                case START -> start(c, atStart);
                case BETWEEN -> between(c);
                case EVENT -> inEvent(c);
                case AFTER -> after(c);
            };
        }

        private boolean start(final char c, final boolean atStart) {
            if ((atStart && c == BYTE_ORDER_MARK) || Character.isWhitespace(c)) {
                return false;
            }
            if (c != '[') {
                notArray = true;
                return true;
            }
            state = State.BETWEEN;
            return false;
        }

        private boolean between(final char c) {
            if (Character.isWhitespace(c) || c == ',') {
                return false;
            }
            if (c == ']') {
                closed = true;
                state = State.AFTER;
                return false;
            }
            state = State.EVENT;
            event.setLength(0);
            eventChars = 0;
            skipping = false;
            depth = 0;
            inString = false;
            escape = false;
            scalar = c != '{' && c != '[' && c != '"';
            return inEvent(c);
        }

        private boolean after(final char c) {
            if (Character.isWhitespace(c)) {
                return false;
            }
            trailing = true;
            return true;
        }

        private boolean inEvent(final char c) {
            if (scalar && (c == ',' || c == ']' || Character.isWhitespace(c))) {
                // A number or literal ends at what follows it, which belongs to the array.
                if (endEvent()) {
                    return true;
                }
                return between(c);
            }
            append(c);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                    if (depth == 0) {
                        return endEvent();
                    }
                }
                return false;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if ((c == '}' || c == ']') && depth > 0) {
                depth--;
                if (depth == 0) {
                    return endEvent();
                }
            }
            return false;
        }

        private void append(final char c) {
            eventChars++;
            if (skipping) {
                strings.accept(c);
                return;
            }
            event.append(c);
            if (event.length() > maxEventChars) {
                // Too long to keep: read through to its end, only its longest string measured.
                skipping = true;
                strings.reset();
                strings.accept(event);
                event.setLength(0);
                event.trimToSize();
            }
        }

        // True once reading is to stop.
        private boolean endEvent() {
            state = State.BETWEEN;
            seen++;
            if (skipping) {
                skipped++;
                largestSkipped = Math.max(largestSkipped, eventChars);
                if (strings.longest() > longestSkippedText) {
                    longestSkippedText = strings.longest();
                    longestSkippedPath = strings.field();
                }
                return false;
            }
            if (used + event.length() > budget) {
                // Left for a later read rather than cut.
                full = true;
                return true;
            }
            events.add(event.toString());
            used += event.length();
            full = used >= budget;
            return full;
        }

        // The text ended: an event it ended inside is left out.
        private void end() {
            if (state == State.EVENT) {
                if (scalar && !inString) {
                    endEvent();
                    return;
                }
                unterminated = true;
                unterminatedChars = eventChars;
            }
        }

        private Read read(final boolean ended, final boolean timedOut) {
            return new Read(List.copyOf(events), seen, skipped, largestSkipped, longestSkippedText,
                    longestSkippedPath, unterminated, unterminatedChars, closed, trailing, notArray,
                    ended || closed, timedOut && !full, full);
        }
    }


    private enum State {
        START,
        BETWEEN,
        EVENT,
        AFTER
    }
}
