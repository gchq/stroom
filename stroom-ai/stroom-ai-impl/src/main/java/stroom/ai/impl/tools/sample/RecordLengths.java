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

/// The longest line of text read through once, and, for delimited text whose quoted values may hold new lines, the
/// longest record with those lines joined, as [SplitterDryRun#longestRecord] measures a sample: what a Data
/// Splitter's read buffer must hold. Only a character at a time is held, so a stream of any size can be measured.
///
/// A record over several lines of another kind (one starting at a marked line, or a block ended by a blank line)
/// needs the converter's own pattern, so is not measured here.
public final class RecordLengths {

    private static final int CHUNK = 64 * 1024;
    private static final char BYTE_ORDER_MARK = '﻿';

    private RecordLengths() {
        // Static methods only.
    }

    /// Reads the text through, stopping early when time is up.
    ///
    /// @param reader    The text.
    /// @param quote     The quote character of delimited text whose quoted values may hold new lines, or null to
    ///                  measure lines only.
    /// @param timeIsUp  Says when to stop, with what is measured so far.
    /// @return The longest line, the longest record (null without a quote), and whether the whole text was read.
    /// @throws IOException if the text can't be read.
    public static Measured measure(final Reader reader,
                                   final Character quote,
                                   final BooleanSupplier timeIsUp) throws IOException {
        final Counter counter = new Counter(quote);
        final char[] chunk = new char[CHUNK];
        boolean first = true;
        int read;
        while ((read = reader.read(chunk)) >= 0) {
            int from = 0;
            if (first && read > 0) {
                first = false;
                // A byte order mark, which a pipeline doesn't read as text either.
                if (chunk[0] == BYTE_ORDER_MARK) {
                    from = 1;
                }
            }
            for (int i = from; i < read; i++) {
                counter.accept(chunk[i]);
            }
            if (timeIsUp.getAsBoolean()) {
                return counter.measured(false);
            }
        }
        return counter.measured(true);
    }


    // --------------------------------------------------------------------------------


    /// What was measured.
    ///
    /// @param lines            How many lines were read.
    /// @param chars            How many characters were read.
    /// @param longestLine      The longest line, without its new line.
    /// @param longestRecord    The longest record with the new lines its quoted values hold joined, each new line
    ///                         counted (and the one ending it); null when no quote was given.
    /// @param recordsOverLines How many records span more than one line (0 when no quote was given).
    /// @param complete         True if the whole text was read; false if time ran out first.
    public record Measured(long lines,
                           long chars,
                           Span longestLine,
                           Span longestRecord,
                           long recordsOverLines,
                           boolean complete) {

        /// @return The longest record as a Data Splitter reads it: the longest record when quoted values may hold
        /// new lines, else the longest line.
        public int longest() {
            return longestRecord != null
                    ? Math.max(longestRecord.chars(), longestLine.chars())
                    : longestLine.chars();
        }
    }

    /// A line or record.
    ///
    /// @param chars Its length, in characters.
    /// @param line  The line it starts on, from 1; 0 when there is none.
    public record Span(int chars, long line) {

    }

    // Counts as the characters go by.
    private static final class Counter {

        private final Character quote;
        private long chars;
        private long line = 1;
        private int lineLength;
        private int longestLine;
        private long longestLineAt;
        // The record open, while its quoted values hold new lines: its length so far, the line it began on, how
        // many lines it has, and whether a quote is open.
        private int recordLength;
        private long recordStart;
        private int recordLines;
        private boolean quoteOpen;
        private boolean recordHasText;
        private int longestRecord;
        private long longestRecordAt;
        private long recordsOverLines;

        private Counter(final Character quote) {
            this.quote = quote;
        }

        private void accept(final char c) {
            chars++;
            if (c == '\n') {
                endLine();
                return;
            }
            lineLength++;
            if (quote != null) {
                if (!recordHasText && !Character.isWhitespace(c)) {
                    // Blank lines between records are passed over, as the converter does.
                    recordHasText = true;
                    recordStart = line;
                }
                if (c == quote) {
                    quoteOpen = !quoteOpen;
                }
            }
        }

        private void endLine() {
            if (lineLength > longestLine) {
                longestLine = lineLength;
                longestLineAt = line;
            }
            if (quote != null && recordHasText) {
                recordLength += lineLength + 1;
                recordLines++;
                if (!quoteOpen) {
                    endRecord();
                }
            }
            lineLength = 0;
            line++;
        }

        private void endRecord() {
            if (recordLength > longestRecord) {
                longestRecord = recordLength;
                longestRecordAt = recordStart;
            }
            if (recordLines > 1) {
                recordsOverLines++;
            }
            recordLength = 0;
            recordLines = 0;
            recordHasText = false;
        }

        private Measured measured(final boolean complete) {
            final long lines = lineLength > 0
                    ? line
                    : line - 1;
            if (lineLength > 0 || recordHasText) {
                // The last line, without a new line at its end.
                if (lineLength > longestLine) {
                    longestLine = lineLength;
                    longestLineAt = line;
                }
                if (quote != null && recordHasText) {
                    recordLength += lineLength;
                    recordLines++;
                    endRecord();
                }
            }
            return new Measured(lines,
                    chars,
                    new Span(longestLine, longestLineAt),
                    quote == null
                            ? null
                            : new Span(longestRecord, longestRecordAt),
                    recordsOverLines,
                    complete);
        }
    }
}
