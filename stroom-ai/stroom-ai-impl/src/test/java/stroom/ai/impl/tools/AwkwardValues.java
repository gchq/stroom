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

package stroom.ai.impl.tools;

import java.util.List;

/// Awkward values for the unit tests' samples, each with why it is there: characters real sources send that the
/// tools must read as Stroom does. Seen in chat 181: a form feed in a Teams message, which Stroom writes as `&#xc;` in
/// its XML 1.1 Events, made check_events refuse a stream Stroom had passed. The e2e suite's `AwkwardValues` holds the
/// same values with how each source format writes them.
public final class AwkwardValues {

    /// The characters Stroom's jsonParser keeps of a string by default (its `stringTruncateLength`): it cuts a
    /// longer one, logging a Warning.
    public static final int JSON_PARSER_CUT = 10_000;

    /// Every value.
    public static final List<Value> ALL = List.of(
            new Value("form_feed", "page one\fpage two",
                    "a form feed, as in chat 181's Teams message: &#xc; in Stroom's XML 1.1"),
            new Value("backspace", "typo\b fixed", "a backspace, a C0 control XML 1.0 can't hold"),
            new Value("bell", "ding\u0007 dong", "a bell, a C0 control XML 1.0 can't hold"),
            new Value("ansi_colour", "\u001b[31mred\u001b[0m text", "ESC of an ANSI colour code"),
            new Value("vertical_tab", "col1\u000bcol2", "a vertical tab: white space to some readers, not to XML"),
            new Value("del", "rub\u007fout", "DEL: raw in XML 1.0, a character reference in XML 1.1"),
            new Value("c1_control", "x\u0080y", "U+0080, a C1 control: raw in XML 1.0, a reference in XML 1.1"),
            new Value("nel", "line\u0085next", "NEL (U+0085): a line end to XML 1.1"),
            new Value("line_separator", "one\u2028two", "U+2028 LINE SEPARATOR: a line end to XML 1.1"),
            new Value("paragraph_separator", "one\u2029two", "U+2029 PARAGRAPH SEPARATOR"),
            new Value("bom_mid", "mid\ufefftext", "a byte order mark inside a value"),
            new Value("astral", "launch \ud83d\ude80 done \ud834\udd1e", "astral characters: surrogate pairs"),
            new Value("combining", "cafe\u0301 n\u0303o", "combining marks: one character seen, two code points"),
            new Value("right_to_left", "\u05e9\u05dc\u05d5\u05dd \u0645\u0631\u062d\u0628\u0627",
                    "right-to-left Hebrew and Arabic"),
            new Value("zero_width", "zero\u200bwidth\u200cnon\u200djoin\u2060word", "zero-width characters"),
            new Value("tabs", "a\tb\t\tc", "tabs inside a value"),
            new Value("crlf", "first\r\nsecond", "CRLF inside a value"),
            new Value("lone_cr", "first\rsecond", "a lone CR inside a value"),
            new Value("quotes_backslashes", "say \"hi\", it's C:\\temp\\new", "quotes and backslashes"),
            new Value("xml_special", "a < b && c > d", "XML's special characters as text"),
            new Value("cdata_end", "x ]]> y", "]]>, which ends a CDATA section"),
            new Value("entity_like", "&amp; and &#xc; and &lt;", "text that looks like XML entities, kept as written"),
            new Value("key_value_specials", "x=1 y=\"two words\" z", "=, quotes and spaces, as a key=value value"),
            new Value("padded", "  padded  ", "leading and trailing spaces"));

    private AwkwardValues() {
    }

    /// @param name A value's name.
    /// @return The value.
    public static Value named(final String name) {
        return ALL.stream().filter(value -> value.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No awkward value " + name));
    }

    /// @param text A text.
    /// @return It as XML 1.1 text, as Stroom's XML writer writes it: the specials escaped, the C0 controls (but tab
    /// and new line), CR, DEL, the C1 controls and U+2028 as character references, NUL dropped.
    public static String xml11Text(final String text) {
        final StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case 0 -> {
                    // Stroom's XML writer drops NUL: no XML version can hold it.
                }
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if ((cp < 0x20 && cp != '\t' && cp != '\n') || (cp >= 0x7f && cp <= 0x9f) || cp == 0x2028) {
                        out.append("&#x").append(Integer.toHexString(cp)).append(';');
                    } else {
                        out.appendCodePoint(cp);
                    }
                }
            }
        });
        return out.toString();
    }


    // --------------------------------------------------------------------------------


    /// An awkward value.
    ///
    /// @param name Its name.
    /// @param text The text as the source meant it.
    /// @param why  Why it's here.
    public record Value(String name, String text, String why) {

    }
}
