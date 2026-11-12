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

import stroom.ai.impl.tools.ToolText;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Syslog headers inside the text they carry, as relays and collectors wrap a source's own line: a relay's header
/// before the source's (syslog within syslog), a collector's words before a header (its time and host), and the
/// end of an RFC 5424 header a shipper has already read up to the host (the rest kept as one JSON value). Format
/// level only (RFC 3164 and RFC 5424), never one product's fields.
public final class SyslogHeaders {

    /// RFC 5424 structured data: `-`, or one or more bracketed elements whose quoted values may hold `]` and
    /// escapes (`\]`, `\"`). Written so a repetition is a run of characters, not one character: Java's regex
    /// engine recurses once a repetition of a group, and a long element read a character at a time overflowed
    /// the stack. Runs as Java's regex (Data Splitter) and XPath's (an extraction in the XSLT).
    public static final String STRUCTURED_DATA = "-|(?:\\[[^\\]\"\\\\]*(?:(?:\\\\.|\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")"
                                                 + "[^\\]\"\\\\]*)*\\])+";
    /// What follows the host in an RFC 5424 header: app, process id, message id, structured data, then the
    /// message, without the byte order mark RFC 5424 lets a UTF-8 message start with (seen in
    /// testing); XPath's dialect too.
    public static final String TAIL_5424 = "^(\\S+) (\\S+) (\\S+) (" + STRUCTURED_DATA + ")(?: \uFEFF?(.*))?$";
    /// The names of [#TAIL_5424]'s groups.
    public static final List<String> TAIL_5424_NAMES = List.of("app", "pid", "msgid", "structured", "message");
    /// The prefix a nested header's fields are written with in the header around it: the relay's are
    /// `relay_time`, `relay_host`..., the source's keep their own names.
    public static final String RELAY_PREFIX = "relay_";
    /// The most words a collector's prefix is read as.
    public static final int MOST_PREFIX_WORDS = 4;

    private static final Pattern HEADER_3164 = ToolText.re(SplitterDryRun.SYSLOG_3164);
    private static final Pattern HEADER_5424 = ToolText.re(SplitterDryRun.SYSLOG_5424);
    // The start of either header, as the profile tells them (RFC 5424's priority required, RFC 3164's not).
    private static final String START = "(?:<\\d{1,3}>1 \\S+ \\S+ \\S+ \\S+ \\S+ |(?:<\\d{1,3}>)?[A-Z][a-z]{2} "
                                        + "[ \\d]\\d \\d{2}:\\d{2}:\\d{2} \\S+ )";
    private static final Pattern HEADER_START = ToolText.re("^" + START);
    // A collector's words before a header: as few as reach one.
    private static final Pattern PREFIXED = ToolText.re("^((?:\\S+\\s+){1," + MOST_PREFIX_WORDS + "}?)(?=" + START
                                                        + ")");
    private static final Pattern TAIL = ToolText.re(TAIL_5424, Pattern.DOTALL);
    // One structured data element as RFC 5424 writes it: an id, then name="value" parameters.
    private static final Pattern SD_ELEMENT = ToolText.re("\\[[^\\s\\]\"=]+(?: +[^\\s\\]\"=]+=\"[^\"\\\\]*"
                                                          + "(?:\\\\.[^\"\\\\]*)*\")* *\\]");
    private static final Pattern PROCESS_ID = ToolText.re("^(?:-|\\d+)$");
    // Headers within headers read at most this deep.
    private static final int MOST_DEPTH = 4;

    private SyslogHeaders() {
        // Static only.
    }

    /// @param text A line, or a message.
    /// @return Whether it starts with a syslog header (RFC 5424 or RFC 3164).
    public static boolean startsWithHeader(final String text) {
        return text != null && ToolText.match(HEADER_START, text);
    }

    /// A relay's header that may or may not be there before a source's header, each of its fields a group, so lines
    /// relayed and lines sent directly are read by one regex. Java's dialect (a Data Splitter's regex).
    ///
    /// @param rfc The relay's header format, `rfc5424` or `rfc3164`.
    /// @return The regex, to go after the `^`, with a group per name of [#relayNames(String)].
    public static String optionalRelay(final String rfc) {
        final String outer = "rfc5424".equals(rfc)
                ? "(?:<(\\d+)>)?1 (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (" + STRUCTURED_DATA + ") ?"
                : "(?:<(\\d+)>)?([A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2}) (\\S+) "
                  + "(?:([^:\\[ ]+)(?:\\[(\\d+)\\])?: ?)?";
        return "(?:" + outer + "(?=" + START + "))?";
    }

    /// @param rfc The relay's header format, `rfc5424` or `rfc3164`.
    /// @return The names of [#optionalRelay(String)]'s groups: the header's, without its message, with the prefix.
    public static List<String> relayNames(final String rfc) {
        return ("rfc5424".equals(rfc)
                ? List.of("pri", "time", "host", "app", "pid", "msgid", "structured")
                : List.of("pri", "time", "host", "app", "pid")).stream()
                .map(name -> RELAY_PREFIX + name)
                .toList();
    }

    /// The regex a relayed line matches at its start: a header (RFC 3164 with any tag, or RFC 5424 with its
    /// structured data) whose message starts with another header. Java's dialect (an alternatives part's match).
    ///
    /// @param rfc The relay's header format, `rfc5424` or `rfc3164`.
    /// @return The regex.
    public static String relayedStart(final String rfc) {
        final String outer = "rfc5424".equals(rfc)
                ? "<\\d{1,3}>1 \\S+ \\S+ \\S+ \\S+ \\S+ (?:" + STRUCTURED_DATA + ") ?"
                : "(?:<\\d{1,3}>)?[A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2} \\S+ (?:[^:\\[ ]+(?:\\[\\d+\\])?: ?)?";
        return "^" + outer + START;
    }

    /// @param line A line.
    /// @return The format of the header it starts with ([SampleProfiler#SYSLOG_5424_FORMAT] or
    /// [SampleProfiler#SYSLOG_3164_FORMAT]), or null for none.
    public static String formatOf(final String line) {
        if (line == null) {
            return null;
        }
        if (ToolText.match(SampleProfiler.SYSLOG_5424, line)) {
            return SampleProfiler.SYSLOG_5424_FORMAT;
        }
        if (ToolText.match(SampleProfiler.SYSLOG_3164, line)) {
            return SampleProfiler.SYSLOG_3164_FORMAT;
        }
        return null;
    }

    /// The words a collector wrote before a syslog header (`2026-11-09T10:00:00Z relay01 <14>Oct ...`).
    ///
    /// @param line A line not starting with a header.
    /// @return How many words come before the header (1 to [#MOST_PREFIX_WORDS]), or 0 when none does.
    public static int prefixWords(final String line) {
        if (line == null || startsWithHeader(line)) {
            return 0;
        }
        final Matcher m = ToolText.matchAt(PREFIXED, line);
        if (m == null) {
            return 0;
        }
        return ToolText.strip(m.group(1)).split("\\s+").length;
    }

    /// @param line A line.
    /// @param words How many words to take off its start.
    /// @return The line after them.
    public static String withoutWords(final String line, final int words) {
        String rest = ToolText.lstrip(line);
        for (int n = 0; n < words; n++) {
            final int space = indexOfSpace(rest);
            if (space < 0) {
                return "";
            }
            rest = ToolText.lstrip(rest.substring(space));
        }
        return rest;
    }

    /// The message a header carries: after RFC 3164's tag, or RFC 5424's structured data.
    ///
    /// @param line A line.
    /// @return The message, or null when the line doesn't start with a header.
    public static String messageOf(final String line) {
        if (!startsWithHeader(line)) {
            return null;
        }
        // The message is each header's last group.
        Matcher m = ToolText.matchAt(HEADER_5424, line);
        if (m == null) {
            m = ToolText.matchAt(HEADER_3164, line);
        }
        return m == null
                ? null
                : NullSafe.string(m.group(m.groupCount()));
    }

    /// The source's own line inside its wrappers: a collector's words taken off, and each header whose message is
    /// another header taken off in turn.
    ///
    /// @param line A line.
    /// @return The innermost line (the line itself when nothing wraps it).
    public static String innermost(final String line) {
        if (line == null) {
            return null;
        }
        String current = line;
        final int words = prefixWords(current);
        if (words > 0) {
            current = withoutWords(current, words);
        }
        for (int depth = 0; depth < MOST_DEPTH; depth++) {
            final String message = messageOf(current);
            if (message == null || !startsWithHeader(message)) {
                break;
            }
            current = message;
        }
        return current;
    }

    /// Whether a text is what follows the host in an RFC 5424 header (`app pid msgid [structured data] message`):
    /// its structured data `-`, with a process id that is a number or `-`, or elements as RFC 5424 writes them.
    ///
    /// @param text A text.
    /// @return True if it reads as that.
    public static boolean isTail(final String text) {
        final Matcher m = tailOf(text);
        if (m == null) {
            return false;
        }
        final String structured = m.group(4);
        if ("-".equals(structured)) {
            return ToolText.match(PROCESS_ID, m.group(2));
        }
        return wellFormed(structured);
    }

    /// @param text A text.
    /// @return Whether it is an RFC 5424 tail holding structured data elements (not `-`).
    public static boolean hasStructuredData(final String text) {
        final Matcher m = tailOf(text);
        // With a parameter or an enterprise's id, as a source's structured data has: a bare bracketed word or number
        // is a log's own label (seen in testing: a mail gateway's `msw 2026-11-10 00:00:04.6 [1002294] [info] ...`
        // read as an app, a process id of 2026-11-10 and structured data [1002294]).
        return m != null && !"-".equals(m.group(4)) && wellFormed(m.group(4))
               && (m.group(4).contains("=\"") || m.group(4).contains("@"));
    }

    /// @param text A text.
    /// @return The [#TAIL_5424] match on it, or null.
    public static Matcher tailOf(final String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = TAIL.matcher(text);
        return m.matches()
                ? m
                : null;
    }

    // Structured data made of elements as RFC 5424 writes them, one after another.
    private static boolean wellFormed(final String structured) {
        int at = 0;
        final Matcher m = SD_ELEMENT.matcher(structured);
        while (at < structured.length()) {
            m.region(at, structured.length());
            if (!m.lookingAt()) {
                return false;
            }
            at = m.end();
        }
        return at > 0;
    }

    private static int indexOfSpace(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
