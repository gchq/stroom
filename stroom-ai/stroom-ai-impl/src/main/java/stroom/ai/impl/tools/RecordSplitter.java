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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/// Splits the start of one stream part into records, as a survey of a feed reads them: the items of a JSON array
/// (or of the array one JSON object wraps them in), XML fragments (several root elements, one `<Event>` a line),
/// the children of an XML document's root, or lines, with the header line of delimited text given apart.
///
/// A text cut off at the end (only the start of the part was read) loses only its cut-off last record: JSON arrays
/// and XML are read incrementally, so the complete records before the cut still count.
public final class RecordSplitter {

    public static final String JSON_ARRAY = "json array";
    public static final String JSON_OBJECT = "json object";
    public static final String XML_FRAGMENTS = "xml fragments";
    public static final String XML = "xml";
    public static final String JSON_LINES = "json lines";
    public static final String DELIMITED = "delimited";
    public static final String UNKNOWN_TEXT = "unknown text";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SYSLOG_5424 = Pattern.compile("^<\\d{1,3}>1 \\S+ \\S+ \\S+ \\S+ \\S+");
    private static final Pattern SYSLOG_3164 = Pattern.compile(
            "^(<\\d{1,3}>)?[A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2} \\S+ ");
    private static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");
    private static final Pattern COLUMN_NAME = Pattern.compile("[A-Za-z_][\\w .:/()\\[\\]-]*");
    private static final List<String> DELIMITERS = List.of(",", "\t", "|", ";");
    private static final int LINES_CHECKED = 20;

    private RecordSplitter() {
        // Static methods only.
    }

    /// @param text       The start of one stream part.
    /// @param maxRecords The most records to return.
    /// @param truncated  True if only the start of the part was read, so a cut-off last record is dropped.
    /// @return Its records.
    public static Chunk split(final String text, final int maxRecords, final boolean truncated) {
        final String trimmed = stripLeading(text);
        final char head = trimmed.isEmpty()
                ? ' '
                : trimmed.charAt(0);
        if (head == '[') {
            return jsonArray(trimmed, maxRecords);
        }
        if (head == '{') {
            final List<String> wrapped = wrappedRecords(trimmed.stripTrailing());
            if (wrapped != null) {
                // One object wrapping the records (CloudTrail's Records): its items are the records.
                return new Chunk(JSON_OBJECT, wrapped.subList(0, Math.min(maxRecords, wrapped.size())), null);
            }
        }
        if (head == '<' && !SYSLOG_5424.matcher(trimmed).find() && !SYSLOG_3164.matcher(trimmed).find()) {
            final Chunk chunk = xmlRecords(trimmed, maxRecords);
            if (chunk != null) {
                return chunk;
            }
        }
        List<String> lines = lines(trimmed);
        if (truncated && !lines.isEmpty() && !trimmed.endsWith("\n") && !trimmed.endsWith("\r")) {
            lines = lines.subList(0, lines.size() - 1);
        }
        if (!lines.isEmpty() && lines.stream().limit(LINES_CHECKED).allMatch(RecordSplitter::isJsonObject)) {
            return new Chunk(JSON_LINES, lines.subList(0, Math.min(maxRecords, lines.size())), null);
        }
        final String header = delimitedHeader(lines);
        if (header != null) {
            final List<String> body = lines.subList(1, lines.size());
            return new Chunk(DELIMITED, body.subList(0, Math.min(maxRecords, body.size())), header);
        }
        return new Chunk(UNKNOWN_TEXT, lines.subList(0, Math.min(maxRecords, lines.size())), null);
    }

    /// @return The non-blank lines of a text.
    public static List<String> lines(final String text) {
        return text.lines().filter(line -> !line.isBlank()).toList();
    }

    private static String stripLeading(final String text) {
        int i = 0;
        while (i < text.length() && "﻿\r\n ".indexOf(text.charAt(i)) >= 0) {
            i++;
        }
        return text.substring(i);
    }

    // Elements of a JSON array one at a time, stopping at the first that does not parse (a cut-off end).
    private static Chunk jsonArray(final String text, final int maxRecords) {
        final List<String> records = new ArrayList<>();
        try (final JsonParser parser = MAPPER.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                return new Chunk(JSON_ARRAY, records, null);
            }
            while (records.size() < maxRecords) {
                final JsonToken token = parser.nextToken();
                if (token == null || token == JsonToken.END_ARRAY) {
                    break;
                }
                final int start = (int) parser.currentTokenLocation().getCharOffset();
                if (token == JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    final int end = (int) parser.currentLocation().getCharOffset();
                    records.add(text.substring(start, end));
                } else {
                    parser.skipChildren();
                }
            }
        } catch (final IOException | RuntimeException e) {
            // A cut-off end: the records before it stand.
        }
        return new Chunk(JSON_ARRAY, records, null);
    }

    // The items of the array of objects one JSON object holds the records in ({"Records": [...]}, "data", "events",
    // "items"...): the key whose array of objects is the longest. Null if it isn't such an object.
    private static List<String> wrappedRecords(final String text) {
        final JsonNode value;
        try {
            value = MAPPER.readTree(text);
        } catch (final IOException | RuntimeException e) {
            return null;
        }
        if (value == null || !value.isObject()) {
            return null;
        }
        List<String> best = null;
        for (final Map.Entry<String, JsonNode> field : value.properties()) {
            final JsonNode array = field.getValue();
            if (!array.isArray() || array.isEmpty()) {
                continue;
            }
            final List<String> items = new ArrayList<>();
            boolean objects = true;
            for (final JsonNode item : array) {
                if (!item.isObject()) {
                    objects = false;
                    break;
                }
                items.add(item.toString());
            }
            if (objects && (best == null || items.size() > best.size())) {
                best = items;
            }
        }
        return best;
    }

    private static boolean isJsonObject(final String line) {
        final String trimmed = line.strip();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        try {
            return MAPPER.readTree(trimmed).isObject();
        } catch (final IOException | RuntimeException e) {
            return false;
        }
    }

    // Children of the root element that are complete, even if the document is cut off; or, for XML fragments
    // (several root elements, one <Event> per line), the complete fragments themselves. Null if it isn't XML.
    private static Chunk xmlRecords(final String text, final int maxRecords) {
        final String body = XML_DECL.matcher(text).replaceFirst("");
        final List<String> fragments = completeChildren("<fragments>" + body + "</fragments>", maxRecords);
        if (fragments != null && fragments.size() > 1) {
            // A fragment's own namespace is kept in its text: seen in Ask Stroom AI, Windows events shown without
            // theirs sent the agent to read the stream again to learn what its XSLT must match.
            return new Chunk(XML_FRAGMENTS, fragments, null);
        }
        final List<String> children = completeChildren(body, maxRecords);
        return children == null
                ? null
                : new Chunk(XML, children, null);
    }

    // The complete children of the root element, as text, up to the first error (a cut-off end); null if no root
    // element could be read.
    private static List<String> completeChildren(final String xml, final int maxRecords) {
        final XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        final List<String> records = new ArrayList<>();
        boolean rootSeen = false;
        int depth = 0;
        StringWriter current = null;
        XMLEventWriter writer = null;
        try {
            final XMLEventReader reader = inputFactory.createXMLEventReader(new StringReader(xml));
            while (reader.hasNext() && records.size() < maxRecords) {
                final XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    depth++;
                    rootSeen = true;
                    if (depth == 2) {
                        current = new StringWriter();
                        writer = outputFactory.createXMLEventWriter(current);
                    }
                }
                if (writer != null && depth >= 2) {
                    writer.add(event);
                }
                if (event.isEndElement()) {
                    if (depth == 2 && writer != null) {
                        writer.flush();
                        writer.close();
                        records.add(current.toString());
                        writer = null;
                        current = null;
                    }
                    depth--;
                }
            }
        } catch (final XMLStreamException | RuntimeException e) {
            // A cut-off end, or not XML: the complete records before it stand.
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (final XMLStreamException e) {
                // The cut-off record is dropped.
            }
        }
        return rootSeen
                ? records
                : null;
    }

    // The header line of delimited text: the first line, if every line checked has the same number of one
    // delimiter, and the first line's fields are all names (not numbers or empty), while the others are not.
    private static String delimitedHeader(final List<String> lines) {
        if (lines.size() < 2) {
            return null;
        }
        final List<String> checked = lines.subList(0, Math.min(LINES_CHECKED, lines.size()));
        for (final String delimiter : DELIMITERS) {
            final long count = count(checked.getFirst(), delimiter);
            if (count == 0 || !checked.stream().allMatch(line -> count(line, delimiter) == count)) {
                continue;
            }
            final List<String> first = fields(checked.getFirst(), delimiter);
            final boolean allNames = first.stream().allMatch(field -> COLUMN_NAME.matcher(field).matches());
            final boolean dataBelow = checked.subList(1, checked.size()).stream()
                    .anyMatch(line -> !fields(line, delimiter).stream()
                            .allMatch(field -> COLUMN_NAME.matcher(field).matches()));
            if (allNames && dataBelow) {
                return checked.getFirst();
            }
            return null;
        }
        return null;
    }

    private static long count(final String line, final String delimiter) {
        return line.chars().filter(c -> c == delimiter.charAt(0)).count();
    }

    private static List<String> fields(final String line, final String delimiter) {
        return List.of(line.split(Pattern.quote(delimiter), -1)).stream()
                .map(field -> field.strip().replaceAll("^\"|\"$", ""))
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// One stream part split into records.
    ///
    /// @param format  How it was read: json array, json object, xml fragments, xml, json lines, delimited or unknown
    ///                text.
    /// @param records Each record's text.
    /// @param header  For delimited text, the header line naming the columns; else null.
    public record Chunk(String format, List<String> records, String header) {

    }
}
