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
public final class EventsFile {

    public static final String ROOT_ELEMENT = "stroom-events";
    public static final String EVENT_ELEMENT = "stroom-event";
    public static final String STREAM_ID_ATTRIBUTE = "streamId";
    public static final String EVENT_ID_ATTRIBUTE = "eventId";
    public static final String FORMAT_ATTRIBUTE = "format";
    public static final String TEXT_FORMAT = "text";
    /// The columns a table's rows carry their stream and event ids in, so the events behind them can be fetched.
    public static final Set<String> SOURCE_ID_COLUMNS = Set.of("StreamId", "EventId");

    private static final Pattern XML_DECLARATION = Pattern.compile("^\\uFEFF?\\s*<\\?xml[^>]*\\?>");

    private EventsFile() {
        // Static methods only.
    }

    /// @return What the file starts with.
    public static String start() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<" + ROOT_ELEMENT + ">\n";
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
        final String start = "  <" + EVENT_ELEMENT + " " + STREAM_ID_ATTRIBUTE + "=\"" + streamId + "\" "
                             + EVENT_ID_ATTRIBUTE + "=\"" + eventId + "\"";
        if (isWellFormedXml(withoutDeclaration)) {
            return start + ">" + withoutDeclaration + "</" + EVENT_ELEMENT + ">\n";
        }
        return start + " " + FORMAT_ATTRIBUTE + "=\"" + TEXT_FORMAT + "\">" + escape(event.strip())
               + "</" + EVENT_ELEMENT + ">\n";
    }

    /// @return True if the text is one well-formed XML element, with no DOCTYPE.
    static boolean isWellFormedXml(final String text) {
        if (!text.startsWith("<")) {
            return false;
        }
        try {
            SafeXPath.secureReader().parse(new InputSource(new StringReader(text)));
            return true;
        } catch (final SAXException | IOException e) {
            return false;
        }
    }

    /// @return The text with the characters special to XML escaped, and those XML can't hold left out.
    static String escape(final String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(c -> {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                default -> {
                    if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF)) {
                        sb.appendCodePoint(c);
                    }
                }
            }
        });
        return sb.toString();
    }
}
