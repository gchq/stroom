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
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/// A stream part's sample: read as a stream of whole events up to the part's share of characters, not its first so
/// many characters, so no event in it is ever cut, and an event too long to sample is left out (said, with its
/// length and its longest string value) rather than kept cut. Characters decide how much is read, whatever the
/// format. How the part is read is told by its first character:
///
/// * `[`: one JSON array ([JsonArrayStream]), its items the events (an array followed by more text, JSON lines of
///   arrays say, is read a line at a time instead);
/// * `<`, not a syslog priority: XML ([XmlRecordStream]), its records the events: a document's root's children,
///   kept in the document's prolog and root, or the top-level elements of XML fragments. XML that can't be read so
///   (no root, no record in it, not well-formed before a record) is read from its start, cut back to a line end;
/// * anything else: lines ([LineStream]), each a record or a line of one. A part whose every line read is too long
///   to sample is read from its start instead (a one-line JSON object that wraps its records, say).
///
/// Also how a stream's parts are chosen for a sample: spread through it, from its first to its last.
public final class PartSampler {

    // The most leading white space kept before a part's first character.
    private static final int MOST_LEAD = 4096;
    private static final char BYTE_ORDER_MARK = '﻿';
    private static final int CHUNK = 64 * 1024;
    // The parts named in full when said; more are shortened.
    private static final int MOST_NAMED = 12;

    private PartSampler() {
        // Static methods only.
    }

    /// Which parts of a stream a sample reads: all of them when there are no more than `most`, else `most` spread
    /// evenly from the first to the last, in order. Always the same for the same stream.
    ///
    /// @param parts How many parts the stream has.
    /// @param most  The most parts read.
    /// @return The zero-based parts, ascending and distinct, the first and the last among them.
    public static List<Integer> spread(final int parts, final int most) {
        final int count = Math.max(1, Math.min(parts, most));
        final List<Integer> chosen = new ArrayList<>(count);
        if (count == 1) {
            chosen.add(0);
            return chosen;
        }
        for (int i = 0; i < count; i++) {
            chosen.add((int) ((long) i * (parts - 1) / (count - 1)));
        }
        return chosen;
    }

    /// A part's share of what is left of a stream's characters: what is left spread over the parts left to read,
    /// but never less than `least`, so every part chosen is read, with room for an event of the most length,
    /// however little of the stream's characters is left: they bound only the depth beyond that.
    ///
    /// @param left      The stream's characters left (none, or less than none, once used).
    /// @param partsLeft The parts left to read, this one included.
    /// @param least     The fewest characters a part is given.
    /// @return The part's characters.
    public static int share(final long left, final int partsLeft, final int least) {
        final long even = Math.max(0, left) / Math.max(1, partsLeft);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(least, even));
    }

    /// @param parts Parts, zero-based.
    /// @return Them said, e.g. `0, 41, 82 and 786`, a long list shortened to its first and last few.
    public static String said(final List<Integer> parts) {
        final List<String> named = parts.size() <= MOST_NAMED
                ? parts.stream().map(String::valueOf).toList()
                : shortened(parts);
        if (named.size() <= 1) {
            return String.join("", named);
        }
        return String.join(", ", named.subList(0, named.size() - 1)) + " and " + named.getLast();
    }

    /// Reads a part's sample.
    ///
    /// @param opener   Opens the part's text from its start; called again if the part is read a second way.
    /// @param limits   How much is kept.
    /// @param timeIsUp Says when to stop, with what is read so far.
    /// @return The sample.
    /// @throws IOException if the part can't be read.
    public static Sample sample(final Opener opener,
                                final Limits limits,
                                final BooleanSupplier timeIsUp) throws IOException {
        final PushbackReader reader = new PushbackReader(opener.open(), MOST_LEAD + 16);
        final StringBuilder lead = new StringBuilder();
        int c = reader.read();
        if (c == BYTE_ORDER_MARK) {
            // A byte order mark, which a pipeline doesn't read as text either.
            c = reader.read();
        }
        while (c >= 0 && Character.isWhitespace(c)) {
            if (lead.length() < MOST_LEAD) {
                lead.append((char) c);
            }
            c = reader.read();
        }
        if (c < 0) {
            return new Sample(lead.toString(), Mode.LINES, 0, true, false, SkippedEvents.NONE, false, 0, false,
                    null);
        }
        if (c == '[') {
            reader.unread(c);
            final JsonArrayStream.Read array = JsonArrayStream.read(reader, limits.maxEventChars(), limits.maxChars(),
                    timeIsUp);
            if (!array.trailing()) {
                return fromArray(array);
            }
            // More than one array (JSON lines of arrays, say): read a line at a time.
            return lines(opener, withoutByteOrderMark(opener.open()), limits, timeIsUp);
        }
        final boolean xml = c == '<' && !syslogPriority(reader);
        reader.unread(c);
        reader.unread(lead.toString().toCharArray());
        return xml
                ? xml(opener, reader, limits, timeIsUp)
                : lines(opener, reader, limits, timeIsUp);
    }

    // An XML part read record by record (XmlRecordStream): a document's root's children, or, where more top-level
    // elements follow the first, the top-level elements (XML fragments). Read from its start instead where it can't
    // be read so.
    private static Sample xml(final Opener opener,
                              final Reader reader,
                              final Limits limits,
                              final BooleanSupplier timeIsUp) throws IOException {
        XmlRecordStream.Read read = XmlRecordStream.read(reader, false, limits.maxEventChars(), limits.maxChars(),
                timeIsUp);
        if (read.moreRoots()) {
            read = XmlRecordStream.read(withoutByteOrderMark(opener.open()), true, limits.maxEventChars(),
                    limits.maxChars(), timeIsUp);
        }
        if (read.unreadable() != null) {
            final Sample head = head(withoutByteOrderMark(opener.open()), limits.maxChars(), timeIsUp, null);
            return new Sample(head.text(), Mode.HEAD, 0, head.whole(), head.cut(), SkippedEvents.NONE, false, 0,
                    head.timedOut(), head.whole()
                    ? null
                    : "it can't be read record by record (" + read.unreadable() + "), so its first "
                      + thousands(head.text().length()) + " characters are read instead");
        }
        final boolean whole = read.ended() && read.skipped() == 0 && !read.unterminated();
        return new Sample(read.text(), Mode.XML, read.records().size(), whole, false,
                new SkippedEvents(read.skipped(), read.largestSkipped(), read.longestSkippedText(), null),
                read.unterminated(), read.unterminatedChars(), read.timedOut(), null);
    }


    // A part read a line at a time; read from its start if every line read is too long to sample.
    private static Sample lines(final Opener opener,
                                final Reader reader,
                                final Limits limits,
                                final BooleanSupplier timeIsUp) throws IOException {
        final LineStream.Read read = LineStream.read(reader, limits.maxEventChars(), limits.maxChars(), timeIsUp);
        final SkippedEvents skipped = new SkippedEvents(read.skipped(), read.largestSkipped(),
                read.longestSkippedText(), read.longestSkippedPath());
        if (read.lines() == 0 && read.skipped() > 0 && !read.timedOut()) {
            final Sample head = head(withoutByteOrderMark(opener.open()), limits.maxChars(), timeIsUp, null);
            return new Sample(head.text(), Mode.HEAD, 0, false, head.cut(), skipped, false, 0, head.timedOut(),
                    "every line read is that long, so its first " + thousands(head.text().length())
                    + " characters are read instead");
        }
        return new Sample(read.text(), Mode.LINES, read.lines(), read.ended() && read.skipped() == 0, false,
                skipped, false, 0, read.timedOut(), null);
    }

    // A part read from its start up to the most characters, cut back to its last line end (as a part's head was
    // always read).
    private static Sample head(final Reader reader,
                               final int maxChars,
                               final BooleanSupplier timeIsUp,
                               final String fellBack) throws IOException {
        final StringBuilder text = new StringBuilder();
        final char[] chunk = new char[CHUNK];
        boolean more = false;
        boolean timedOut = false;
        int read;
        while ((read = reader.read(chunk, 0, (int) Math.min(CHUNK, maxChars + 1L - text.length()))) > 0) {
            text.append(chunk, 0, read);
            if (text.length() > maxChars) {
                more = true;
                break;
            }
            if (timeIsUp.getAsBoolean()) {
                timedOut = reader.read() >= 0;
                more = timedOut;
                break;
            }
        }
        String said = text.toString();
        if (more) {
            said = said.substring(0, Math.min(said.length(), maxChars));
            final int lineEnd = said.lastIndexOf('\n');
            if (lineEnd >= 0) {
                said = said.substring(0, lineEnd + 1);
            }
        }
        return new Sample(said, Mode.HEAD, 0, !more, more, SkippedEvents.NONE, false, 0, timedOut, fellBack);
    }

    private static Sample fromArray(final JsonArrayStream.Read array) {
        final boolean whole = array.ended() && array.skipped() == 0 && !array.unterminated();
        return new Sample(array.text(), Mode.JSON_ARRAY, array.events().size(), whole, false,
                new SkippedEvents(array.skipped(), array.largestSkipped(), array.longestSkippedText(),
                        array.longestSkippedPath()), array.unterminated(), array.unterminatedChars(),
                array.timedOut(), null);
    }

    // Whether a '<' just read starts a syslog priority, as <34>: its following characters read and put back.
    private static boolean syslogPriority(final PushbackReader reader) throws IOException {
        final StringBuilder next = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            final int c = reader.read();
            if (c < 0) {
                break;
            }
            next.append((char) c);
            if (!Character.isDigit(c)) {
                break;
            }
        }
        reader.unread(next.toString().toCharArray());
        final String said = next.toString();
        return said.length() >= 2 && said.endsWith(">") && said.chars().limit(said.length() - 1)
                .allMatch(Character::isDigit);
    }

    private static Reader withoutByteOrderMark(final Reader reader) throws IOException {
        final PushbackReader pushback = new PushbackReader(reader, 1);
        final int c = pushback.read();
        if (c >= 0 && c != BYTE_ORDER_MARK) {
            pushback.unread(c);
        }
        return pushback;
    }

    private static String thousands(final long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    private static List<String> shortened(final List<Integer> parts) {
        final List<String> named = new ArrayList<>();
        parts.subList(0, MOST_NAMED / 2).forEach(part -> named.add(String.valueOf(part)));
        named.add("…");
        parts.subList(parts.size() - MOST_NAMED / 2 + 1, parts.size()).forEach(part -> named.add(String.valueOf(part)));
        return named;
    }


    // --------------------------------------------------------------------------------


    /// Opens a part's text from its start.
    @FunctionalInterface
    public interface Opener {

        /// @return The part's text, from its start: a fresh reader each call.
        /// @throws IOException if it can't be read.
        Reader open() throws IOException;
    }


    /// How much of a part a sample keeps.
    ///
    /// @param maxEventChars The longest event kept: a longer one is left out.
    /// @param maxChars      The most characters of whole events kept: the part's share of its stream's.
    public record Limits(int maxEventChars, int maxChars) {

    }


    /// A part's sample.
    ///
    /// @param text              The sample: whole events (a JSON array's kept as one array, an XML document's
    ///                          records in its root), or, read from its start, the part's first characters.
    /// @param mode              How the part was read.
    /// @param events            How many events (items, records, or non-blank lines) were kept; 0 when read from
    ///                          its start.
    /// @param whole             True if the part was read to its end and nothing was left out.
    /// @param cut               True if, read from its start, the part was cut at the most characters.
    /// @param skipped           The events left out as too long to sample.
    /// @param unterminated      True if the part ended inside its last event, which is left out.
    /// @param unterminatedChars How long that cut-off event was, as far as it went.
    /// @param timedOut          True if time ran out before the sample was read.
    /// @param fellBack          Why the part was read from its start rather than as its events, or null.
    public record Sample(String text,
                         Mode mode,
                         long events,
                         boolean whole,
                         boolean cut,
                         SkippedEvents skipped,
                         boolean unterminated,
                         long unterminatedChars,
                         boolean timedOut,
                         String fellBack) {

        /// @param maxEventChars The longest event kept, as the sample was read.
        /// @return What was left out or cut of the part, for a tool's notes (without the part's name); empty when
        /// nothing was.
        public List<String> said(final int maxEventChars) {
            final List<String> said = new ArrayList<>();
            if (skipped.count() > 0) {
                said.add(skipped.said(maxEventChars) + (fellBack != null
                        ? "; " + fellBack
                        : "; " + thousands(events) + " " + (mode == Mode.LINES
                                ? "line"
                                : "event") + (events == 1
                                ? ""
                                : "s") + " sampled"));
            } else if (fellBack != null) {
                said.add(fellBack);
            }
            if (unterminated) {
                said.add("its last event is cut off at the part's end (" + thousands(unterminatedChars)
                         + " characters, never closed): left out");
            }
            return said;
        }
    }


    /// Events left out of a sample as too long to sample.
    ///
    /// @param count       How many.
    /// @param largest     The longest's length; 0 if none.
    /// @param longestText The decoded length of the longest string value in them; 0 if none.
    /// @param path        The field holding it, or null if unknown.
    public record SkippedEvents(int count, long largest, long longestText, String path) {

        /// None left out.
        public static final SkippedEvents NONE = new SkippedEvents(0, 0, 0, null);

        /// @param maxEventChars The longest event kept, as the sample was read.
        /// @return Them said, e.g. `4 events over 100,000 characters left out of the sample (largest 412,118
        /// characters; longest string value 398,002, in 'message')`.
        public String said(final int maxEventChars) {
            return count + " event" + (count == 1
                    ? ""
                    : "s") + " over " + thousands(maxEventChars) + " characters left out of the sample (largest "
                   + thousands(largest) + " characters" + (longestText > 0
                    ? "; longest string value " + thousands(longestText) + (path != null
                    ? ", in '" + path + "'"
                    : "")
                    : "") + ")";
        }

        /// @param other More left out.
        /// @return Both together.
        public SkippedEvents plus(final SkippedEvents other) {
            final boolean otherLonger = other.longestText > longestText;
            return new SkippedEvents(count + other.count, Math.max(largest, other.largest), otherLonger
                    ? other.longestText
                    : longestText, otherLonger
                    ? other.path
                    : path);
        }
    }


    /// How a part was read.
    public enum Mode {
        /// One JSON array, its items the events.
        JSON_ARRAY,
        /// XML, its records (a document's root's children, or fragments) the events.
        XML,
        /// A line at a time.
        LINES,
        /// From its start, up to the most characters (XML that can't be read record by record, or lines all too
        /// long to sample).
        HEAD;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }
}
