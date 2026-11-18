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

import stroom.ai.impl.tools.sample.ToolJson;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// Awkward values for the e2e suites' samples, each with why it is there: characters and shapes real sources send
/// that no other sample held, so a tool reading them wrongly shows up in a test, not a chat. Seen in chat 181: a form
/// feed in a Teams message, which Stroom writes as `&#xc;` in its XML 1.1 Events, made check_events refuse a stream
/// Stroom had passed.
///
/// Each value is the text as the source meant it (decoded); [Value#json] is how a JSON source writes it, where that
/// is not simply its escaped text. [#lineSafe] holds those a line-per-record text format can carry unquoted, and
/// [#xmlSafe] those XML 1.1 can hold at all (NUL it cannot).
final class AwkwardValues {

    /// Just over the 10,000 characters Stroom's jsonParser keeps of a string by default (its
    /// `stringTruncateLength`, which logs a Warning when it cuts one).
    static final int LONG_LENGTH = 10_050;

    /// Every value.
    static final List<Value> ALL = List.of(
            // C0 controls: XML 1.0 can't hold them; Stroom writes XML 1.1, where they are character references.
            value("form_feed", "page one\fpage two",
                    "a form feed, as in chat 181's Teams message: &#xc; in Stroom's XML 1.1 Events"),
            value("backspace", "typo\b fixed", "a backspace, a C0 control XML 1.0 can't hold"),
            value("bell", "ding\u0007 dong", "a bell, a C0 control XML 1.0 can't hold"),
            value("ansi_colour", "\u001b[31mred\u001b[0m text",
                    "ESC of an ANSI colour code, as console logs are captured"),
            value("vertical_tab", "col1\u000bcol2", "a vertical tab: white space to some readers, not to XML"),
            // JSON can write NUL (\u0000); no XML, 1.0 or 1.1, can hold it.
            new Value("nul", "before\u0000after <b> & c", "{\"v\": \"before\\u0000after <b> & c\"}",
                    "NUL, as JSON writes it, then XML's specials: no XML version can hold a NUL, and Stroom's XML "
                    + "writer takes one for its switch of escaping"),
            // DEL and C1 controls: allowed in XML 1.0, must be character references in XML 1.1.
            value("del", "rub\u007fout", "DEL: allowed raw in XML 1.0, a reference in XML 1.1"),
            value("c1_control", "x\u0080y", "U+0080, a C1 control: raw in XML 1.0, a reference in XML 1.1"),
            value("nel", "line\u0085next", "NEL (U+0085): a line end to XML 1.1, and to some text readers"),
            // Line and paragraph separators: line ends to JavaScript and some regex engines, not to Java's lines().
            value("line_separator", "one\u2028two", "U+2028 LINE SEPARATOR: a line end to JavaScript"),
            value("paragraph_separator", "one\u2029two", "U+2029 PARAGRAPH SEPARATOR: a line end to JavaScript"),
            value("bom_mid", "mid\ufefftext", "a byte order mark inside a value, not at the start of the stream"),
            // Characters outside the BMP: two Java chars each, so length and substring cut them wrongly.
            value("astral", "launch \ud83d\ude80 done \ud834\udd1e",
                    "astral characters (an emoji, a musical symbol): surrogate pairs in Java and JSON"),
            value("combining", "cafe\u0301 n\u0303o", "combining marks: one character seen, two code points"),
            value("right_to_left", "\u05e9\u05dc\u05d5\u05dd \u0645\u0631\u062d\u0628\u0627 user=x",
                    "right-to-left Hebrew and Arabic beside left-to-right text"),
            value("zero_width", "zero\u200bwidth\u200cnon\u200djoin\u2060word",
                    "zero-width space, non-joiner, joiner and word joiner: invisible, but not equal"),
            value("tabs", "a\tb\t\tc", "tabs inside a value: a TSV's delimiter, white space to a trim"),
            value("crlf", "first\r\nsecond", "CRLF inside a value: a record end to a line reader"),
            value("lone_cr", "first\rsecond", "a lone CR inside a value: a line end to some readers, not to others"),
            value("lf", "first\nsecond", "a new line inside a value"),
            value("quotes_backslashes", "say \"hi\", it's C:\\temp\\new",
                    "double and single quotes and backslashes (a Windows path holding \\t and \\n as text)"),
            value("xml_special", "a < b && c > d", "XML's special characters as text"),
            value("cdata_end", "x ]]> y", "]]>, which ends a CDATA section"),
            value("entity_like", "&amp; and &#xc; and &lt;",
                    "text that looks like XML entities and a character reference, kept as written"),
            // As a JSON source writes it: an escaped solidus, an escaped quote, a unicode escape.
            new Value("json_escapes", "a/b \"q\" \u00e9", "{\"v\": \"a\\/b \\\"q\\\" \\u00e9\"}",
                    "JSON's own escapes: \\/, \\\" and a \\u escape, which must read as the characters"),
            value("key_value_specials", "x=1 y=\"two words\" z", "= , quotes and spaces, as a key=value value"),
            value("csv_specials", "one, two \"three\"", "a comma and quotes, as a CSV field"),
            value("padded", "  padded  ", "leading and trailing spaces, which a trim loses"),
            value("long", "L".repeat(LONG_LENGTH), "just over Stroom's jsonParser 10,000-character string cut"));

    // Values a line-per-record text format can't carry unquoted: they end the record or are refused as text.
    private static final List<String> LINE_ENDS = List.of("crlf", "lone_cr", "lf", "nel", "line_separator",
            "paragraph_separator", "nul", "form_feed", "vertical_tab");

    private AwkwardValues() {
    }

    /// @param name A value's name.
    /// @return The value.
    static Value named(final String name) {
        return ALL.stream().filter(value -> value.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No awkward value " + name));
    }

    /// @return The values a line-per-record text format (key=value, syslog, a CSV field unquoted) can carry: none
    /// holding a line end or a character a line reader may take for one.
    static List<Value> lineSafe() {
        return ALL.stream().filter(value -> !LINE_ENDS.contains(value.name())).toList();
    }

    /// @return The values XML 1.1 can hold (all but NUL).
    static List<Value> xmlSafe() {
        return ALL.stream().filter(Predicate.not(value -> value.text().indexOf('\0') >= 0)).toList();
    }

    /// @param values The values.
    /// @param from   The value names to leave out.
    /// @return The values without them.
    static List<Value> without(final List<Value> values, final String... from) {
        final List<String> out = List.of(from);
        return values.stream().filter(value -> !out.contains(value.name())).toList();
    }

    /// Bytes of a raw text stream that aren't UTF-8: an invalid UTF-8 sequence (a lone continuation byte, a lead
    /// byte with no continuation, 0xFF) and Windows-1252's smart quotes and é, as a Windows source sends them.
    ///
    /// @param user The record's user.
    /// @return The line's bytes, its new line ending it.
    static byte[] notUtf8Line(final String user) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("ts=2026-11-18T10:59:00.000Z user=" + user + " msg=\"").getBytes(StandardCharsets.US_ASCII));
        // 0x93 and 0x94: Windows-1252's curly quotes; 0xE9: its é; 0x80 alone: a continuation byte with no lead; 0xC3
        // then '(': a lead byte not followed by a continuation; 0xFF: never in UTF-8.
        out.writeBytes(new byte[]{(byte) 0x93, 'h', 'i', (byte) 0x94, ' ', 'c', 'a', 'f', (byte) 0xE9, ' ',
                (byte) 0x80, ' ', (byte) 0xC3, '(', ' ', (byte) 0xFF});
        out.writeBytes("\"\n".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    // A value whose JSON is simply its text escaped.
    private static Value value(final String name, final String text, final String why) {
        return new Value(name, text, null, why);
    }

    /// @param values The values.
    /// @return Their names.
    static List<String> names(final List<Value> values) {
        final List<String> out = new ArrayList<>();
        values.forEach(value -> out.add(value.name()));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// An awkward value.
    ///
    /// @param name   Its name, used for the record's user (`aw-<name>`) so each is found by it.
    /// @param text   The text as the source meant it.
    /// @param rawJson A JSON object `{"v": ...}` writing it as a source would, where not simply its escaped text; null
    ///                otherwise.
    /// @param why    Why it's here.
    record Value(String name, String text, String rawJson, String why) {

        /// @return The value as a JSON string literal, as a source writes it.
        String json() {
            if (rawJson == null) {
                return ToolJson.dumps(text);
            }
            return rawJson.substring("{\"v\": ".length(), rawJson.length() - 1);
        }

        /// @return The record's user: `aw-` and the value's name, with `_` as `-`.
        String user() {
            return "aw-" + name.replace('_', '-');
        }
    }
}
