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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Where XML text is not well-formed, said so it can be found: the line and column, the event it is in, the text
/// around it (control characters made visible), and why, where the cause is a character reference XML 1.0 forbids.
///
/// Seen in testing (chat 181): check_events refused a stream with "Character reference "&#xc" is an invalid XML
/// character. (line 1)" of a one-line merged document, and the agent spent about 30 calls looking for it.
public final class WellFormedFaults {

    /// The characters of text shown before the fault.
    static final int BEFORE = 50;
    /// The characters of text shown after the fault.
    static final int AFTER = 30;

    // An Event element's start tag, with or without a prefix.
    private static final Pattern EVENT_START = Pattern.compile("<(?:[A-Za-z_][\\w.-]*:)?Event(?=[\\s/>])");
    // The first element's start tag, after any declaration, comments and processing instructions.
    private static final Pattern FIRST_ELEMENT = Pattern.compile(
            "^\\uFEFF?(?:\\s|<\\?[^>]*\\?>|<!--.*?-->)*<(?:[A-Za-z_][\\w.-]*:)?(Events?)(?=[\\s/>])",
            Pattern.DOTALL);
    private static final Pattern CHARACTER_REFERENCE = Pattern.compile(
            "Character reference \"&#(x[0-9A-Fa-f]+|[0-9]+)\"");

    private WellFormedFaults() {
        // Static only.
    }

    /// @param text The XML text that did not parse, e.g. an `<Events>` document.
    /// @param what What it is, for the message, e.g. `Events XML`.
    /// @param e    Why it did not parse.
    /// @return A refusal saying so: the parser's reason, where (the line and column, the Event it is in where the
    /// document is Events, and the text near it), and a hint where the cause is a character reference XML 1.0
    /// forbids.
    public static ToolRefusal refusal(final String text, final String what, final NotWellFormedException e) {
        return new ToolRefusal(message(text, what, e, isEvents(text)));
    }

    /// As [#refusal(String, String, NotWellFormedException)], for one record of a stream, which `what` names
    /// (e.g. `Event 219 of stream 73288`): the Events in the record are not counted.
    ///
    /// @param text The record's text.
    /// @param what What it is, for the message.
    /// @param e    Why it did not parse.
    /// @return A refusal saying so.
    public static ToolRefusal recordRefusal(final String text, final String what, final NotWellFormedException e) {
        return new ToolRefusal(message(text, what, e, false));
    }

    /// @param text        The XML text that did not parse.
    /// @param e           Why.
    /// @param countEvents True to say which Event element (counted from 1) the fault is in.
    /// @return E.g. ` (line 1, column 2345, in Event 219; near "...")`, or "" if the parser gave no line.
    static String where(final String text, final NotWellFormedException e, final boolean countEvents) {
        if (e.getLine() <= 0) {
            return "";
        }
        if (e.getColumn() <= 0) {
            return " (line " + e.getLine() + ")";
        }
        final String safeText = text == null
                ? ""
                : text;
        final int offset = offset(safeText, e.getLine(), e.getColumn());
        final StringBuilder sb = new StringBuilder(" (line ").append(e.getLine())
                .append(", column ").append(e.getColumn());
        if (countEvents) {
            final int event = eventNumber(safeText, offset);
            if (event > 0) {
                sb.append(", in Event ").append(event);
            }
        }
        return sb.append("; near \"").append(excerpt(safeText, offset)).append("\")").toString();
    }

    /// @param message The parser's reason.
    /// @return Why a character reference XML 1.0 forbids (a C0 control character other than NUL, tab, line feed and
    /// carriage return) is there, and what to do, or "" if that is not the cause.
    static String hint(final String message) {
        final Matcher matcher = CHARACTER_REFERENCE.matcher(message == null
                ? ""
                : message);
        if (!matcher.find()) {
            return "";
        }
        final String number = matcher.group(1);
        final int codePoint;
        try {
            codePoint = number.startsWith("x")
                    ? Integer.parseInt(number.substring(1), 16)
                    : Integer.parseInt(number);
        } catch (final NumberFormatException e) {
            return "";
        }
        if (codePoint <= 0 || codePoint >= 0x20) {
            return "";
        }
        return ". &#" + number + "; (U+" + String.format(Locale.ROOT, "%04X", codePoint)
               + ", a control character) is a character reference XML 1.0 forbids and XML 1.1 allows. Stroom "
               + "writes Events streams as XML 1.1 (their header declares <?xml version=\"1.1\"?>), and its schema "
               + "filter passes them: give the document that declaration to check it as Stroom reads it. The "
               + "character is in the source's value; transform strip_control in the mapping takes such "
               + "characters out, needed only if the output must be XML 1.0";
    }

    /// @param text   Text.
    /// @param line   A line (from 1), as a parser counts them (a new line is LF, CR LF or CR).
    /// @param column A column (from 1) on that line.
    /// @return The offset in the text of that position, within the text.
    static int offset(final String text, final int line, final int column) {
        int at = 0;
        int currentLine = 1;
        while (currentLine < line && at < text.length()) {
            final char c = text.charAt(at);
            at++;
            if (c == '\n' || (c == '\r' && (at >= text.length() || text.charAt(at) != '\n'))) {
                currentLine++;
            }
        }
        return Math.min(text.length(), at + Math.max(0, column - 1));
    }

    /// @param text   Text.
    /// @param offset An offset in it.
    /// @return The number (from 1) of the Event element whose start tag comes last before the offset, or 0 if
    /// none does.
    static int eventNumber(final String text, final int offset) {
        final Matcher matcher = EVENT_START.matcher(text);
        matcher.region(0, Math.min(offset, text.length()));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /// @param text   Text.
    /// @param offset An offset in it.
    /// @return Up to [#BEFORE] characters before it and [#AFTER] after, with `...` where cut, and each control
    /// character written as `\\uXXXX` so it can be seen (e.g. a form feed as `\\u000c`).
    static String excerpt(final String text, final int offset) {
        final int start = Math.max(0, offset - BEFORE);
        final int end = Math.min(text.length(), offset + AFTER);
        final StringBuilder sb = new StringBuilder();
        if (start > 0) {
            sb.append("...");
        }
        for (int i = start; i < end; i++) {
            final char c = text.charAt(i);
            if (Character.isISOControl(c)) {
                sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        if (end < text.length()) {
            sb.append("...");
        }
        return sb.toString();
    }

    /// @param text XML text, or null.
    /// @return True if its root element is `Events` or `Event` (in any namespace), so its Event elements are
    /// counted; an XSLT's literal `<Event>` elements are not.
    static boolean isEvents(final String text) {
        return text != null && FIRST_ELEMENT.matcher(text).find();
    }

    private static String message(final String text,
                                  final String what,
                                  final NotWellFormedException e,
                                  final boolean countEvents) {
        return what + " is not well-formed XML: " + e.getMessage() + where(text, e, countEvents)
               + hint(e.getMessage());
    }
}
