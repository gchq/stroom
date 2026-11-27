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

package stroom.ai.impl.workspace;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The file the agent's source events are written to by fetch_events, and read from by search_events: one XML
/// document holding each event in an element that says where it came from.
///
/// ```xml
/// <stroom-events>
///   <stroom-event streamId="12" eventId="3"><Events xmlns="event-logging:3"><Event>...</Event></Events></stroom-event>
///   <stroom-event streamId="12" eventId="4" format="text">a,line,of,text</stroom-event>
/// </stroom-events>
/// ```
///
/// An event that is well-formed XML (the stream's header, the event, and its footer) is kept as XML, without its XML
/// declaration. Anything else, e.g. text, or XML with a DOCTYPE, which the workspace's XML tools refuse, is kept as
/// escaped text.
///
/// The file is XML 1.1, as Stroom writes its Events streams: a control character in a source's value (seen in chat
/// 181: a form feed in a Teams message) is written by Stroom as a character reference, `&#xc;`, which XML 1.1
/// allows and XML 1.0 forbids. An XML 1.1 file keeps such an event as XML, the `&#xc;` as stored, where a 1.0 file
/// could keep it only as escaped text (`&amp;#xc;`), which no search for `&#xc;` finds. An event of another
/// version has its characters that XML 1.1 reads differently (C1 controls and the line separator U+2028, which
/// XML 1.0 holds as they are) written as character references, so it reads the same in the file.
public final class EventsFile {

    public static final String ROOT_ELEMENT = "stroom-events";
    public static final String EVENT_ELEMENT = "stroom-event";
    public static final String STREAM_ID_ATTRIBUTE = "streamId";
    public static final String EVENT_ID_ATTRIBUTE = "eventId";
    public static final String FORMAT_ATTRIBUTE = "format";
    public static final String TEXT_FORMAT = "text";
    /// The columns a table's rows carry their stream and event ids in, so the events behind them can be fetched.
    public static final Set<String> SOURCE_ID_COLUMNS = Set.of("StreamId", "EventId");

    /// The XML version of the file.
    public static final String XML_VERSION = "1.1";

    private static final Pattern XML_DECLARATION = Pattern.compile("^\\uFEFF?\\s*<\\?xml[^>]*\\?>");
    private static final Pattern DECLARED_VERSION = Pattern.compile(
            "^\\uFEFF?\\s*<\\?xml\\s+version\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final String FILE_DECLARATION = "<?xml version=\"" + XML_VERSION + "\" encoding=\"UTF-8\"?>";

    private EventsFile() {
        // Static methods only.
    }

    /// @return What the file starts with: an XML 1.1 declaration and the root element's start.
    public static String start() {
        return FILE_DECLARATION + "\n<" + ROOT_ELEMENT + ">\n";
    }

    /// @return What the file ends with.
    public static String end() {
        return "</" + ROOT_ELEMENT + ">\n";
    }

    /// @param streamId The stream the event is in.
    /// @param eventId  The event's number in the stream.
    /// @param event    The event as a document: the stream's header, the event, and its footer.
    /// @return The event's element.
    public static String element(final long streamId, final long eventId, final String event) {
        final String withoutDeclaration = XML_DECLARATION.matcher(event).replaceFirst("").strip();
        final String version = declaredVersion(event);
        final String asInFile = XML_VERSION.equals(version)
                ? withoutDeclaration
                : readTheSameIn11(withoutDeclaration);
        final String start = "  <" + EVENT_ELEMENT + " " + STREAM_ID_ATTRIBUTE + "=\"" + streamId + "\" "
                             + EVENT_ID_ATTRIBUTE + "=\"" + eventId + "\"";
        // Well-formed as the stream declares it, and as it is in the file.
        if (isWellFormedXml(asInFile, version) && (XML_VERSION.equals(version)
                                                   || isWellFormedXml(asInFile, XML_VERSION))) {
            return start + ">" + asInFile + "</" + EVENT_ELEMENT + ">\n";
        }
        return start + " " + FORMAT_ATTRIBUTE + "=\"" + TEXT_FORMAT + "\">" + escape(event.strip())
               + "</" + EVENT_ELEMENT + ">\n";
    }

    /// @param head The start of a file's text.
    /// @return True if it starts as an events file does: with the [#ROOT_ELEMENT] element, after any byte order
    /// mark, white space and XML declaration.
    public static boolean startsAsOne(final String head) {
        final String text = head == null
                ? ""
                : head.replace("\uFEFF", "").stripLeading();
        final String rest = XML_DECLARATION.matcher(text).replaceFirst("").stripLeading();
        return rest.startsWith("<" + ROOT_ELEMENT + ">") || rest.startsWith("<" + ROOT_ELEMENT + " ")
               || rest.startsWith("<" + ROOT_ELEMENT + "/");
    }

    /// @param text A document's text, or null.
    /// @return The XML version its declaration names, or `1.0` (XML's default) if it has none.
    static String declaredVersion(final String text) {
        final Matcher matcher = DECLARED_VERSION.matcher(text == null
                ? ""
                : text);
        return matcher.find()
                ? matcher.group(1).strip()
                : "1.0";
    }

    /// @param text    XML text, without a declaration.
    /// @param version The XML version to read it as, e.g. `1.0` or `1.1`.
    /// @return True if the text is one well-formed XML element of that version, with no DOCTYPE.
    static boolean isWellFormedXml(final String text, final String version) {
        if (!text.startsWith("<")) {
            return false;
        }
        try {
            SafeXPath.secureReader().parse(new InputSource(new StringReader("<?xml version=\"" + version
                                                                            + "\"?>" + text)));
            return true;
        } catch (final SAXException | IOException e) {
            return false;
        }
    }

    /// @param text XML text of a version other than 1.1, without its declaration.
    /// @return It with the characters XML 1.1 reads differently written as character references: C1 control
    /// characters and DEL (U+007F to U+009F), which XML 1.1 allows only so, and the line separator (U+2028), which
    /// it reads as a new line. In element content and attribute values, where alone XML allows them, the value is
    /// the same.
    static String readTheSameIn11(final String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(c -> {
            if (isC1OrDel(c) || c == 0x2028) {
                sb.append(reference(c));
            } else {
                sb.appendCodePoint(c);
            }
        });
        return sb.toString();
    }

    /// @return The text with the characters special to XML escaped, those XML 1.1 holds only as character
    /// references (control characters other than tab, line feed and carriage return; and the next line and line
    /// separator characters, which it would read as a new line) written so, and those XML can't hold (NUL,
    /// surrogates, U+FFFE and U+FFFF) left out.
    static String escape(final String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(c -> {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                default -> {
                    if (c == 0x9 || c == 0xA || c == 0xD) {
                        sb.appendCodePoint(c);
                    } else if ((c >= 0x1 && c < 0x20) || isC1OrDel(c) || c == 0x2028) {
                        sb.append(reference(c));
                    } else if (c >= 0x20 && c != 0xFFFE && c != 0xFFFF && (c < 0xD800 || c > 0xDFFF)) {
                        sb.appendCodePoint(c);
                    }
                }
            }
        });
        return sb.toString();
    }

    // DEL and the C1 control characters, NEL among them.
    private static boolean isC1OrDel(final int c) {
        return c >= 0x7F && c <= 0x9F;
    }

    private static String reference(final int c) {
        return "&#x" + Integer.toHexString(c) + ";";
    }
}
