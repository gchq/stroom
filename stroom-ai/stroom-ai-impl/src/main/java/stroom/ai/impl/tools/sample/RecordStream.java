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

/// Records of one-record-a-line text read through from its start, as [RecordLengths] reads it: a record ends at a
/// new line, unless a quote is open (delimited text whose quoted values hold new lines), and blank lines between
/// records are passed over. Records before the first wanted are passed over without being kept, so a record any way
/// into a stream of any size can be read. Seen: an agent asked for record 9,000 of a 100 MB CSV part, which a
/// read of the stream's first million characters never reached, and was given the part's start instead.
public final class RecordStream {

    private static final int CHUNK = 64 * 1024;
    private static final char BYTE_ORDER_MARK = '﻿';

    private RecordStream() {
        // Static methods only.
    }

    /// Reads the text through to the records wanted, stopping early when time is up.
    ///
    /// @param reader   The text, from its start.
    /// @param quote    The quote character of delimited text whose quoted values may hold new lines, or null for
    ///                 a record a line.
    /// @param header   True if the first record is a header line naming the columns: given apart, not counted.
    /// @param first    The zero-based index of the first record wanted (the header not counted).
    /// @param count    How many records are wanted.
    /// @param budget   The most characters of record text to keep: a first record is cut to it, and a later one
    ///                 that doesn't fit whole is left out.
    /// @param timeIsUp Says when to stop, with what is read so far.
    /// @return The records, and how far the text was read.
    /// @throws IOException if the text can't be read.
    public static Read read(final Reader reader,
                            final Character quote,
                            final boolean header,
                            final long first,
                            final int count,
                            final int budget,
                            final BooleanSupplier timeIsUp) throws IOException {
        final Collector collector = new Collector(quote, header, first, count, budget);
        final char[] chunk = new char[CHUNK];
        boolean start = true;
        int read;
        while ((read = reader.read(chunk)) >= 0) {
            int from = 0;
            if (start && read > 0) {
                start = false;
                // A byte order mark, which a pipeline doesn't read as text either.
                if (chunk[0] == BYTE_ORDER_MARK) {
                    from = 1;
                }
            }
            for (int i = from; i < read; i++) {
                if (collector.accept(chunk[i])) {
                    return collector.read(false, false);
                }
            }
            if (timeIsUp.getAsBoolean()) {
                return collector.read(false, true);
            }
        }
        collector.endRecord();
        return collector.read(true, false);
    }


    // --------------------------------------------------------------------------------


    /// What was read.
    ///
    /// @param header   The header line, or null (none expected, or none read).
    /// @param records  The records wanted that were read, in order.
    /// @param seen     How many records were read through (the header not counted).
    /// @param ended    True if the text was read to its end: `seen` is then every record it has.
    /// @param timedOut True if time ran out before the records wanted were read.
    /// @param cut      True if a record kept was cut to the budget, or one was left out for it.
    public record Read(String header, List<String> records, long seen, boolean ended, boolean timedOut, boolean cut) {

    }

    // Splits the text into records as the characters go by, keeping only those wanted.
    private static final class Collector {

        private final Character quote;
        private final boolean header;
        private final long first;
        private final int count;
        private final int budget;
        private final List<String> records = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();
        private String headerLine;
        private long seen;
        private int used;
        private boolean quoteOpen;
        private boolean hasText;
        private boolean cut;
        private boolean full;

        private Collector(final Character quote,
                          final boolean header,
                          final long first,
                          final int count,
                          final int budget) {
            this.quote = quote;
            this.header = header;
            this.first = first;
            this.count = count;
            this.budget = budget;
        }

        // True once the records wanted are read.
        private boolean accept(final char c) {
            if (c == '\n' && !quoteOpen) {
                endRecord();
                return full;
            }
            if (!hasText && Character.isWhitespace(c)) {
                // Blank lines between records are passed over, as the converter does; a record's own leading
                // space is kept.
                if (kept() && current.length() <= budget) {
                    current.append(c);
                }
                return false;
            }
            hasText = true;
            if (quote != null && c == quote) {
                quoteOpen = !quoteOpen;
            }
            if (kept() && current.length() <= budget) {
                current.append(c);
            }
            return false;
        }

        // Whether the record being read is kept: the header, or one of those wanted.
        private boolean kept() {
            return (header && headerLine == null) || seen >= first;
        }

        private void endRecord() {
            if (!hasText) {
                current.setLength(0);
                return;
            }
            final String text = stripReturn(current);
            if (header && headerLine == null) {
                headerLine = text;
            } else {
                if (seen >= first && !full) {
                    keep(text);
                }
                seen++;
            }
            current.setLength(0);
            hasText = false;
            quoteOpen = false;
        }

        private void keep(final String text) {
            if (!records.isEmpty() && used + text.length() > budget) {
                // Left for a later read rather than returned cut: only a first record is ever cut.
                cut = true;
                full = true;
                return;
            }
            final String kept = text.substring(0, Math.min(text.length(), budget - used));
            cut = cut || kept.length() < text.length();
            records.add(kept);
            used += kept.length();
            full = records.size() >= count || used >= budget;
        }

        private Read read(final boolean ended, final boolean timedOut) {
            return new Read(headerLine, List.copyOf(records), seen, ended, timedOut && !full, cut);
        }

        // A record's text without the carriage return of a Windows line end.
        private static String stripReturn(final StringBuilder text) {
            final int end = text.length() > 0 && text.charAt(text.length() - 1) == '\r'
                    ? text.length() - 1
                    : text.length();
            return text.substring(0, end);
        }
    }
}
