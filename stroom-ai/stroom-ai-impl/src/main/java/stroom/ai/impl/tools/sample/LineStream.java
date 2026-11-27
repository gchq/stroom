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
import java.util.function.BooleanSupplier;

/// The lines of a text read through from its start for a sample, as [RecordStream] reads one-record-a-line text but
/// keeping each line exactly as it is (its line end, blank lines between records): a line longer than the most kept
/// is read through to its end without being held, and left out, and reading goes on. No line kept is cut: one that
/// doesn't fit the budget is left for a later read, and reading stops there.
public final class LineStream {

    private static final int CHUNK = 64 * 1024;

    private LineStream() {
        // Static methods only.
    }

    /// Reads the text's lines until the budget is used, the text ends, or time is up.
    ///
    /// @param reader       The text.
    /// @param maxLineChars The longest line kept (its line end not counted): a longer one is left out.
    /// @param budget       The most characters of lines kept.
    /// @param timeIsUp     Says when to stop, with what is read so far.
    /// @return The lines kept, and what was left out.
    /// @throws IOException if the text can't be read.
    public static Read read(final Reader reader,
                            final int maxLineChars,
                            final int budget,
                            final BooleanSupplier timeIsUp) throws IOException {
        final Collector collector = new Collector(maxLineChars, budget);
        final char[] chunk = new char[CHUNK];
        int read;
        while ((read = reader.read(chunk)) >= 0) {
            for (int i = 0; i < read; i++) {
                if (collector.accept(chunk[i])) {
                    return collector.read(false, false);
                }
            }
            if (timeIsUp.getAsBoolean()) {
                return collector.read(false, true);
            }
        }
        collector.endLine();
        return collector.read(true, false);
    }


    // --------------------------------------------------------------------------------


    /// What was read.
    ///
    /// @param text               The lines kept, each with its line end as in the text (the last perhaps without).
    /// @param lines              How many non-blank lines were kept.
    /// @param seen               How many non-blank lines were read through, kept or not.
    /// @param skipped            How many were left out as longer than the most kept.
    /// @param largestSkipped     The length of the longest left out; 0 if none.
    /// @param longestSkippedText The decoded length of the longest JSON string value in those left out (should they
    ///                           be JSON); 0 if none.
    /// @param longestSkippedPath The field holding it, or null if unknown.
    /// @param ended              True if the text was read to its end.
    /// @param timedOut           True if time ran out first.
    /// @param full               True if reading stopped at the budget.
    public record Read(String text,
                       long lines,
                       long seen,
                       int skipped,
                       long largestSkipped,
                       long longestSkippedText,
                       String longestSkippedPath,
                       boolean ended,
                       boolean timedOut,
                       boolean full) {

    }

    // Splits the text into lines as the characters go by.
    private static final class Collector {

        private final int maxLineChars;
        private final int budget;
        private final StringBuilder kept = new StringBuilder();
        private final StringBuilder line = new StringBuilder();
        private final LongestJsonString strings = new LongestJsonString();
        private long length;
        private boolean blank = true;
        private boolean skipping;
        private long lines;
        private long seen;
        private int skipped;
        private long largestSkipped;
        private long longestSkippedText;
        private String longestSkippedPath;
        private boolean full;

        private Collector(final int maxLineChars, final int budget) {
            this.maxLineChars = maxLineChars;
            this.budget = budget;
        }

        // True once reading is to stop.
        private boolean accept(final char c) {
            if (c == '\n') {
                if (!skipping) {
                    line.append(c);
                }
                return endLine();
            }
            blank = blank && Character.isWhitespace(c);
            if (c != '\r') {
                length++;
            }
            if (skipping) {
                strings.accept(c);
                return false;
            }
            line.append(c);
            if (length > maxLineChars) {
                // Too long to keep: read through to its end, only its longest string measured.
                skipping = true;
                strings.reset();
                strings.accept(line);
                line.setLength(0);
                line.trimToSize();
            }
            return false;
        }

        // True once reading is to stop.
        private boolean endLine() {
            if (line.isEmpty() && !skipping) {
                return false;
            }
            if (!blank) {
                seen++;
            }
            if (skipping) {
                skipped++;
                largestSkipped = Math.max(largestSkipped, length);
                if (strings.longest() > longestSkippedText) {
                    longestSkippedText = strings.longest();
                    longestSkippedPath = strings.field();
                }
            } else if (kept.length() + line.length() > budget) {
                // Left for a later read rather than cut.
                full = true;
                return true;
            } else {
                kept.append(line);
                if (!blank) {
                    lines++;
                }
            }
            line.setLength(0);
            length = 0;
            blank = true;
            skipping = false;
            full = kept.length() >= budget;
            return full;
        }

        private Read read(final boolean ended, final boolean timedOut) {
            return new Read(kept.toString(), lines, seen, skipped, largestSkipped, longestSkippedText,
                    longestSkippedPath, ended, timedOut && !full, full);
        }
    }
}
