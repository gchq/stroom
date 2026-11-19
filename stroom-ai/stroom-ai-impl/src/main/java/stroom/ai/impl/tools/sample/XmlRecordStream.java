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

/// The records of an XML part read through from its start a character at a time, tracking only markup (tags,
/// comments, CDATA, processing instructions and declarations) and nesting, nothing parsed, so a document of any size
/// can be sampled. The records are as [SampleProfiler] and [XmlTree] take them: the children of a document's root,
/// or, for XML fragments (several top-level elements), the top-level elements. A record longer than the most kept
/// is read through to its end without being held, and left out; no record kept is ever cut. The records kept are
/// written back as the same document: its prolog and root start tag (namespace declarations and attributes as they
/// are), the records, and the root's end tag.
public final class XmlRecordStream {

    private static final int CHUNK = 64 * 1024;

    private XmlRecordStream() {
        // Static methods only.
    }

    /// Reads the part's records, stopping when the budget is used, the part ends, or time is up.
    ///
    /// @param reader         The text, from its start (no byte order mark).
    /// @param fragments      True to read top-level elements as the records (XML fragments); false for a
    ///                       document's root's children.
    /// @param maxRecordChars The longest record kept: a longer one is left out.
    /// @param budget         The most characters of records kept: a record that doesn't fit is left for a later
    ///                       read, and reading stops there.
    /// @param timeIsUp       Says when to stop, with what is read so far.
    /// @return The records kept, and what was left out.
    /// @throws IOException if the text can't be read.
    public static Read read(final Reader reader,
                            final boolean fragments,
                            final int maxRecordChars,
                            final int budget,
                            final BooleanSupplier timeIsUp) throws IOException {
        final Scanner scanner = new Scanner(fragments, maxRecordChars, budget);
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


    /// What was read of the part.
    ///
    /// @param text               The records kept, as the part's document (or fragments), well-formed.
    /// @param records            The records kept, each whole, in order.
    /// @param skipped            How many were left out as longer than the most kept.
    /// @param largestSkipped     The length of the longest left out; 0 if none.
    /// @param longestSkippedText The length of the longest text in those left out; 0 if none.
    /// @param unterminated       True if the part ended inside a record, which is left out.
    /// @param unterminatedChars  How long that cut-off record was, as far as it went.
    /// @param moreRoots          True if, read as a document, another top-level element followed the root: XML
    ///                           fragments, to be read as such.
    /// @param unreadable         Why the part can't be read record by record (no root before the end, a prolog or
    ///                           root start tag too long, markup that isn't well-formed before any record, no
    ///                           record in the root), or null.
    /// @param ended              True if the part was read to its end (or the root's), nothing past it unread.
    /// @param timedOut           True if time ran out first.
    /// @param full               True if reading stopped at the budget.
    public record Read(String text,
                       List<String> records,
                       int skipped,
                       long largestSkipped,
                       long longestSkippedText,
                       boolean unterminated,
                       long unterminatedChars,
                       boolean moreRoots,
                       String unreadable,
                       boolean ended,
                       boolean timedOut,
                       boolean full) {

    }

    // Splits the part into records as the characters go by.
    private static final class Scanner {

        // The most characters of markup looked at to tell what it is ("![CDATA[" is the longest).
        private static final int MOST_HEAD = 8;

        private final boolean fragments;
        private final int maxRecordChars;
        private final int budget;
        private final int recordDepth;
        private final List<String> records = new ArrayList<>();
        private final StringBuilder record = new StringBuilder();
        private final StringBuilder prolog = new StringBuilder();
        private final StringBuilder rootTag = new StringBuilder();
        private final StringBuilder head = new StringBuilder();
        private Markup markup = Markup.NONE;
        private int depth;
        private boolean rootRead;
        private boolean rootClosed;
        private boolean inRootTag;
        private boolean inRecord;
        private boolean skipping;
        private long recordChars;
        private long textRun;
        private long longestText;
        private char quote;
        private char last;
        private char beforeLast;
        private int declarationBrackets;
        private long used;
        private int skipped;
        private long largestSkipped;
        private long longestSkippedText;
        private boolean unterminated;
        private long unterminatedChars;
        private boolean moreRoots;
        private String stopped;
        private boolean full;

        private Scanner(final boolean fragments, final int maxRecordChars, final int budget) {
            this.fragments = fragments;
            this.maxRecordChars = maxRecordChars;
            this.budget = budget;
            this.recordDepth = fragments
                    ? 0
                    : 1;
        }

        // True once reading is to stop.
        private boolean accept(final char c) {
            if (markup == Markup.NONE && c == '<') {
                endText();
                markup = Markup.OPENING;
                head.setLength(0);
                if (depth == recordDepth && !inRecord && (fragments || rootRead)) {
                    // Perhaps a record's start: kept from here, dropped if it turns out to be other markup.
                    startRecord();
                }
                keep(c);
                return stopped != null;
            }
            keep(c);
            if (stopped != null) {
                return true;
            }
            return switch (markup) {
                case NONE -> text(c);
                case OPENING -> opening(c);
                case START_TAG -> startTag(c);
                case END_TAG -> endTag(c);
                case COMMENT -> until(c, "-->");
                case CDATA -> cdata(c);
                case PROCESSING -> until(c, "?>");
                case DECLARATION -> declaration(c);
            };
        }

        private boolean text(final char c) {
            if (!Character.isWhitespace(c) && depth == 0 && !rootClosed) {
                return stop("it holds text outside any element");
            }
            textRun++;
            return false;
        }

        // The characters after '<', until it is known what markup it opens.
        private boolean opening(final char c) {
            head.append(c);
            final String said = head.toString();
            if (said.equals("?")) {
                return as(Markup.PROCESSING);
            }
            if (said.equals("/")) {
                return as(Markup.END_TAG);
            }
            if (said.startsWith("!")) {
                if (said.equals("!--")) {
                    return as(Markup.COMMENT);
                }
                if (said.equals("![CDATA[")) {
                    return as(Markup.CDATA);
                }
                if ("!--".startsWith(said) || "![CDATA[".startsWith(said)) {
                    return false;
                }
                declarationBrackets = 0;
                return as(Markup.DECLARATION);
            }
            if (head.length() > MOST_HEAD || !(Character.isLetter(c) || c == '_' || c == ':')) {
                return stop("it isn't well-formed XML (a '<' that opens no markup)");
            }
            markup = Markup.START_TAG;
            quote = 0;
            last = c;
            if (depth == 0 && !fragments) {
                if (rootClosed) {
                    moreRoots = true;
                    return true;
                }
                // The root's start tag: its '<' and first character were taken for the prolog.
                prolog.setLength(Math.max(0, prolog.length() - 2));
                inRootTag = true;
                rootTag.append('<').append(c);
            }
            return false;
        }

        // Markup other than a start tag: a record's tentative start is dropped where it is at a record's depth.
        private boolean as(final Markup kind) {
            markup = kind;
            last = 0;
            beforeLast = 0;
            if (inRecord && depth == recordDepth) {
                dropRecord();
            }
            return false;
        }

        private boolean startTag(final char c) {
            if (inRootTag) {
                rootTag.append(c);
                if (rootTag.length() > maxRecordChars) {
                    return stop("its root element's start tag is too long");
                }
            }
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                return false;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                return false;
            }
            if (c != '>') {
                if (!Character.isWhitespace(c)) {
                    last = c;
                }
                return false;
            }
            markup = Markup.NONE;
            final boolean selfClosing = last == '/';
            if (inRootTag) {
                inRootTag = false;
                rootRead = true;
                if (selfClosing) {
                    rootClosed = true;
                } else {
                    depth = 1;
                }
                return false;
            }
            if (!selfClosing) {
                depth++;
                return false;
            }
            return depth == recordDepth && inRecord && endRecord();
        }

        private boolean endTag(final char c) {
            if (c != '>') {
                return false;
            }
            markup = Markup.NONE;
            if (depth == 0) {
                return stop("it isn't well-formed XML (an end tag with no start tag)");
            }
            depth--;
            if (depth == recordDepth && inRecord) {
                return endRecord();
            }
            if (depth == 0 && !fragments) {
                // The root closed: read on only to see whether another top-level element follows.
                rootClosed = true;
            }
            return false;
        }

        private boolean cdata(final char c) {
            if (c == '>' && last == ']' && beforeLast == ']') {
                markup = Markup.NONE;
                return false;
            }
            textRun++;
            shift(c);
            return false;
        }

        private boolean declaration(final char c) {
            if (c == '[') {
                declarationBrackets++;
            } else if (c == ']') {
                declarationBrackets--;
            } else if (c == '>' && declarationBrackets <= 0) {
                markup = Markup.NONE;
            }
            return false;
        }

        private boolean until(final char c, final String end) {
            if (c == end.charAt(end.length() - 1) && last == end.charAt(end.length() - 2)
                && (end.length() < 3 || beforeLast == end.charAt(0))) {
                markup = Markup.NONE;
                return false;
            }
            shift(c);
            return false;
        }

        private void shift(final char c) {
            beforeLast = last;
            last = c;
        }

        private boolean stop(final String why) {
            stopped = why;
            return true;
        }

        // Where a character read goes: a document's prolog before its root, or the record being read.
        private void keep(final char c) {
            if (!fragments && !rootRead && !inRootTag) {
                prolog.append(c);
                if (prolog.length() > maxRecordChars && stopped == null) {
                    stopped = "its prolog, before the root element, is too long";
                }
                return;
            }
            if (!inRecord) {
                return;
            }
            recordChars++;
            if (skipping) {
                return;
            }
            record.append(c);
            if (record.length() > maxRecordChars) {
                // Too long to keep: read through to its end, only its length and longest text measured.
                skipping = true;
                record.setLength(0);
                record.trimToSize();
            }
        }

        private void startRecord() {
            inRecord = true;
            skipping = false;
            recordChars = 0;
            longestText = 0;
            textRun = 0;
            record.setLength(0);
        }

        private void dropRecord() {
            inRecord = false;
            skipping = false;
            record.setLength(0);
        }

        private void endText() {
            longestText = Math.max(longestText, textRun);
            textRun = 0;
        }

        // True once reading is to stop.
        private boolean endRecord() {
            inRecord = false;
            endText();
            if (skipping) {
                skipped++;
                largestSkipped = Math.max(largestSkipped, recordChars);
                longestSkippedText = Math.max(longestSkippedText, longestText);
                return false;
            }
            if (used + record.length() > budget) {
                // Left for a later read rather than cut.
                full = true;
                return true;
            }
            records.add(record.toString());
            used += record.length();
            full = used >= budget;
            return full;
        }

        // The part ended: a record it ended inside is left out.
        private void end() {
            if (inRecord && recordChars > 1) {
                unterminated = true;
                unterminatedChars = recordChars;
            }
            if (!fragments && !rootRead && stopped == null) {
                stopped = "it has no root element";
            }
        }

        private Read read(final boolean ended, final boolean timedOut) {
            final boolean nothing = records.isEmpty() && skipped == 0 && !unterminated;
            String unreadable = nothing
                    ? stopped
                    : null;
            if (unreadable == null && nothing && !moreRoots && (ended || rootClosed)) {
                unreadable = fragments
                        ? "it holds no element"
                        : "its root holds no record element";
            }
            // Stopped at markup it can't read after some records: those are kept, the part not read whole.
            final boolean whole = (ended || rootClosed) && stopped == null;
            return new Read(text(), List.copyOf(records), skipped, largestSkipped, longestSkippedText, unterminated,
                    unterminatedChars, moreRoots, unreadable, whole, timedOut && !full, full);
        }

        private String text() {
            final String body = String.join("\n", records) + (records.isEmpty()
                    ? ""
                    : "\n");
            if (fragments) {
                return body;
            }
            return prolog.toString() + rootTag + "\n" + body + "</" + rootName() + ">\n";
        }

        private String rootName() {
            int end = 1;
            while (end < rootTag.length() && !Character.isWhitespace(rootTag.charAt(end))
                   && rootTag.charAt(end) != '/' && rootTag.charAt(end) != '>') {
                end++;
            }
            return rootTag.substring(1, end);
        }
    }


    private enum Markup {
        NONE,
        OPENING,
        START_TAG,
        END_TAG,
        COMMENT,
        CDATA,
        PROCESSING,
        DECLARATION
    }
}
