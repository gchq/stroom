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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Splits the start of one stream part into records, using the same detection as the profile of a sample
/// ([SampleProfiler]). Each record comes with its structured view where the format has one (a JSON record's flattened
/// fields, an XML record's leaf texts and attributes, a delimited row's columns, a key=value line's pairs), which
/// [Shapes] signs it by.
///
/// The records are the items of a JSON array (or of the array one JSON object wraps them in), XML fragments
/// (several root elements, one `<Event>` a line, each keeping its namespace), the children of an XML document's
/// root, records of several lines (a log entry and its stack trace), Kubernetes CRI records (partial lines to their
/// full one), the data lines of a log whose columns a directive line names (W3C, Zeek), records of delimited text
/// whose quoted values hold new lines, or lines, with the header line of delimited text given apart.
///
/// A text cut off at the end (only the start of the part was read) loses only its cut-off last record: JSON arrays
/// and XML are read incrementally, so the complete records before the cut still count.
public final class RecordSplitter {

    /// The most records split by default.
    public static final int MAX_RECORDS = 5000;
    /// A JSON array's items.
    public static final String JSON_ARRAY = SampleProfiler.JSON_ARRAY;
    /// The items of the array one JSON object wraps them in.
    public static final String JSON_OBJECT = SampleProfiler.JSON_OBJECT;
    /// XML fragments.
    public static final String XML_FRAGMENTS = SampleProfiler.XML_FRAGMENTS;
    /// The children of an XML document's root.
    public static final String XML = SampleProfiler.XML;
    /// A JSON object a line.
    public static final String JSON_LINES = SampleProfiler.JSON_LINES;
    /// Delimited text.
    public static final String DELIMITED = SampleProfiler.DELIMITED;
    /// Text of no format known, a line a record.
    public static final String UNKNOWN_TEXT = SampleProfiler.UNKNOWN_TEXT;

    private static final Pattern NAMESPACE_DECLARATION = ToolText.re("\\s+xmlns(:\\w+)?=\"[^\"]*\"");

    private RecordSplitter() {
        // Static methods only.
    }

    /// Splits text into at most [#MAX_RECORDS] records.
    ///
    /// @param text The start of one stream part.
    /// @return Its records.
    public static Chunk split(final String text) {
        return split(text, MAX_RECORDS, false);
    }

    /// Splits text into records.
    ///
    /// @param text       The start of one stream part.
    /// @param maxRecords The most records to return.
    /// @param truncated  True if only the start of the part was read: a cut-off last record is dropped.
    /// @return Its records.
    /// @throws RuntimeException if the part can't be split (a JSON line that doesn't parse among JSON lines, say):
    /// the caller says it couldn't split the part.
    public static Chunk split(final String text, final int maxRecords, final boolean truncated) {
        final String trimmed = ToolText.lstrip(text, "﻿\r\n ");
        final String head = trimmed.isEmpty()
                ? ""
                : trimmed.substring(0, 1);
        if ("[".equals(head)) {
            return jsonArray(trimmed, maxRecords);
        }
        if ("{".equals(head)) {
            final Map.Entry<String, List<Object>> wrapped = SampleProfiler.wrappedRecords(ToolText.rstrip(trimmed));
            if (wrapped != null) {
                // One object wrapping the records (CloudTrail's Records): its items are the records.
                final List<Object> items = wrapped.getValue().subList(0, Math.min(maxRecords,
                        wrapped.getValue().size()));
                final List<String> records = new ArrayList<>();
                final List<Map<String, Object>> parsed = new ArrayList<>();
                items.forEach(item -> {
                    records.add(ToolJson.dumps(item));
                    parsed.add(SampleProfiler.flatten(item));
                });
                return new Chunk(JSON_OBJECT, records, parsed, null, null, List.of());
            }
        }
        if ("<".equals(head) && !ToolText.match(SampleProfiler.SYSLOG_5424, trimmed)
            && !ToolText.match(SampleProfiler.SYSLOG_3164, trimmed)) {
            final Chunk chunk = xmlRecords(trimmed, maxRecords);
            if (chunk != null) {
                return chunk;
            }
        }
        List<String> lines = ToolText.nonBlankLines(trimmed);
        if (truncated && !lines.isEmpty() && !trimmed.endsWith("\n") && !trimmed.endsWith("\r")) {
            lines = lines.subList(0, lines.size() - 1);
        }
        final Map<String, Object> info = SampleProfiler.profile(String.join("\n",
                lines.subList(0, Math.min(maxRecords + 1, lines.size()))), SampleProfiler.MAX_RECORDS);
        return splitLines(lines, info, maxRecords, truncated);
    }

    private static Chunk splitLines(final List<String> lines,
                                    final Map<String, Object> info,
                                    final int maxRecords,
                                    final boolean truncated) {
        final String format = (String) info.get("format");
        final List<String> first = lines.subList(0, Math.min(maxRecords, lines.size()));
        if (SampleProfiler.MULTI_LINE.equals(format)) {
            // Records of several lines (a stack trace under its entry): grouped from where each starts.
            return grouped(format, SplitterDryRun.groupLines(lines, (String) info.get("record_start")), maxRecords,
                    truncated);
        }
        if (SampleProfiler.CRI.equals(format)) {
            // Kubernetes CRI: a record ends at its full (F) line, its partial (P) lines before it.
            return grouped(format, SplitterDryRun.groupLinesToEnd(lines, (String) info.get("record_end")),
                    maxRecords, truncated);
        }
        if (DELIMITED.equals(format) && info.get("header_directive") != null) {
            // Columns named by a directive line (W3C, Zeek): records are the data lines, read under the columns in
            // effect.
            final SplitterSpec spec = SplitterDryRun.inferSpec(String.join("\n",
                    lines.subList(0, Math.min(maxRecords + 1, lines.size())))).spec();
            final String prefix = (String) info.get("comment_prefix");
            final List<String> data = lines.stream().filter(line -> !line.startsWith(prefix)).limit(maxRecords)
                    .toList();
            final List<Map<String, String>> records = SplitterDryRun.dryRunDirectives(spec, lines, lines.size())
                    .records();
            final List<Map<String, Object>> parsed = objects(records.subList(0,
                    Math.min(maxRecords, records.size())));
            return new Chunk(format, data, parsed, null, null, namingColumns(JsonValues.strings(info.get("columns")),
                    parsed));
        }
        if (JSON_LINES.equals(format)) {
            final List<Map<String, Object>> parsed = new ArrayList<>();
            first.forEach(line -> parsed.add(SampleProfiler.flatten(ToolJson.loads(line))));
            return new Chunk(format, first, parsed, null, null, List.of());
        }
        if (SampleProfiler.SYSLOG_3164_FORMAT.equals(format) || SampleProfiler.SYSLOG_5424_FORMAT.equals(format)) {
            final Chunk delimitedBody = delimitedSyslog(format, first);
            if (delimitedBody != null) {
                return delimitedBody;
            }
        }
        if (SampleProfiler.KEY_VALUE_FORMAT.equals(format)) {
            final List<Map<String, Object>> parsed = new ArrayList<>();
            first.forEach(line -> parsed.add(SampleProfiler.keyValues(line)));
            return new Chunk(format, first, parsed, null, null, List.of());
        }
        if (DELIMITED.equals(format)) {
            return delimited(format, lines, info, maxRecords);
        }
        return new Chunk(format, first, nulls(first.size()), null, null, List.of());
    }

    private static Chunk grouped(final String format, final List<String> grouped, final int maxRecords,
                                 final boolean truncated) {
        List<String> records = grouped;
        if (truncated && records.size() > 1) {
            records = records.subList(0, records.size() - 1);
        }
        final List<String> kept = records.subList(0, Math.min(maxRecords, records.size()));
        return new Chunk(format, kept, nulls(kept.size()), null, null, List.of());
    }

    // A delimited body after the syslog header (Palo Alto's CSV): read by its columns, the kinds of event by their
    // low-variety ones, rather than as masked text, which made every line its own shape.
    private static Chunk delimitedSyslog(final String format, final List<String> lines) {
        final String text = String.join("\n", lines);
        final SplitterSpec spec = SplitterDryRun.inferSpec(text).spec();
        if (spec == null || spec.body() == null || !"delimited".equals(spec.body().kind())) {
            return null;
        }
        final List<Map<String, String>> records = SplitterDryRun.dryRun(spec, text).records();
        if (records.size() != lines.size()) {
            return null;
        }
        final List<Map<String, Object>> parsed = objects(records);
        return new Chunk(format, lines, parsed, null, null, namingColumns(spec.body().columns(), parsed));
    }

    private static Chunk delimited(final String format,
                                   final List<String> allLines,
                                   final Map<String, Object> info,
                                   final int maxRecords) {
        List<String> lines = allLines;
        if (Boolean.TRUE.equals(info.get("quoted_newlines"))) {
            // Quoted values holding new lines: records end at a new line outside quotes.
            lines = SplitterDryRun.quotedLines(String.join("\n", lines), "\"");
        }
        final String header = Boolean.TRUE.equals(info.get("has_header")) && !lines.isEmpty()
                ? lines.getFirst()
                : null;
        final List<String> body = header != null
                ? lines.subList(1, lines.size())
                : lines;
        final List<String> records = body.subList(0, Math.min(maxRecords, body.size()));
        final String delimiter = (String) info.getOrDefault("delimiter", ",");
        final List<List<String>> rows = new ArrayList<>();
        if (delimiter.length() > 1) {
            records.forEach(line -> rows.add(ToolText.split(line, delimiter)));
        } else {
            rows.addAll(DelimitedText.cells(records, delimiter.charAt(0)));
        }
        final List<String> columns = JsonValues.strings(info.get("columns"));
        final List<Map<String, Object>> parsed = new ArrayList<>();
        rows.forEach(row -> parsed.add(SampleProfiler.zip(columns, row)));
        return new Chunk(format, records, parsed, header, null, namingColumns(columns, parsed));
    }

    private static List<Map<String, Object>> objects(final List<Map<String, String>> records) {
        final List<Map<String, Object>> out = new ArrayList<>();
        records.forEach(record -> out.add(new LinkedHashMap<>(record)));
        return out;
    }

    private static List<Map<String, Object>> nulls(final int size) {
        return new ArrayList<>(Collections.nCopies(size, null));
    }

    // Elements of a JSON array one at a time, stopping at the first that does not parse (a cut-off end).
    private static Chunk jsonArray(final String text, final int maxRecords) {
        final List<String> records = new ArrayList<>();
        final List<Map<String, Object>> parsed = new ArrayList<>();
        int i = 1;
        while (records.size() < maxRecords) {
            while (i < text.length() && " \t\r\n,".indexOf(text.charAt(i)) >= 0) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) == ']') {
                break;
            }
            final ToolJson.Decoded decoded;
            try {
                decoded = ToolJson.rawDecode(text, i);
            } catch (final ToolJson.JsonDecodeException e) {
                break;
            }
            i = decoded.end();
            if (decoded.value() instanceof Map) {
                records.add(ToolJson.dumps(decoded.value()));
                parsed.add(SampleProfiler.flatten(decoded.value()));
            }
        }
        return new Chunk(JSON_ARRAY, records, parsed, null, null, List.of());
    }

    // Children of the root element that are complete, even if the document is cut off; or, for XML fragments
    // (several root elements, one <Event> per line), the complete fragments themselves.
    private static Chunk xmlRecords(final String text, final int maxRecords) {
        final List<XmlTree.Element> fragments = XmlTree.pull("<fragments>"
                                                             + ToolText.subFirst(SampleProfiler.XML_DECL, "", text)
                                                             + "</fragments>", maxRecords).records();
        if (fragments.size() > 1) {
            // A fragment's own namespace is kept in its text: seen in Ask Stroom AI, Windows events shown without
            // theirs sent the agent to read_stream to learn what its XSLT must match.
            final List<String> records = new ArrayList<>();
            final List<Map<String, Object>> parsed = new ArrayList<>();
            fragments.forEach(fragment -> {
                records.add(XmlTree.toText(fragment));
                parsed.add(xmlFields(fragment));
            });
            return new Chunk(XML_FRAGMENTS, records, parsed, null, null, List.of());
        }
        final XmlTree.Pull pull = XmlTree.pull(text, maxRecords);
        if (pull.root() == null) {
            return null;
        }
        final XmlTree.Element root = pull.root();
        final String opening = "<" + root.localName + (root.namespace != null
                ? " xmlns=\"" + root.namespace + "\""
                : "") + ">";
        final List<String> records = new ArrayList<>();
        final List<Map<String, Object>> parsed = new ArrayList<>();
        pull.records().forEach(record -> {
            records.add(xmlText(record));
            parsed.add(xmlFields(record));
        });
        return new Chunk(XML, records, parsed, null, opening + "\n{records}\n</" + root.localName + ">", List.of());
    }

    private static String xmlText(final XmlTree.Element node) {
        final String text = XmlTree.toText(node);
        return node.hasNamespaces()
                ? ToolText.subFirst(NAMESPACE_DECLARATION, "", text)
                : text;
    }

    private static Map<String, Object> xmlFields(final XmlTree.Element node) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final XmlTree.Element element : node.iter()) {
            for (final XmlTree.Attribute attribute : element.attributes) {
                out.put(element.localName + "@" + attribute.clarkName(), attribute.value());
            }
            if (element.isLeaf() && !ToolText.isBlank(element.text())) {
                out.put(element.localName, ToolText.strip(element.text()));
            }
        }
        return out;
    }

    /// @param columns The records' columns.
    /// @param rows    The records, by column.
    /// @return The columns that name the kind of event: those named so (action, event_type...), or else those of few
    /// values that each come up often and hold no digits.
    static List<String> namingColumns(final List<String> columns, final List<Map<String, Object>> rows) {
        final List<String> named = columns.stream().filter(column -> ToolText.search(Shapes.NAMING, column)).toList();
        if (!named.isEmpty() || rows.isEmpty()) {
            return named;
        }
        final List<String> out = new ArrayList<>();
        for (final String column : columns) {
            final List<Object> values = new ArrayList<>();
            rows.forEach(row -> {
                final Object value = row.get(column);
                if (value != null && !"".equals(value)) {
                    values.add(value);
                }
            });
            final List<Object> distinct = values.stream().distinct().toList();
            if (distinct.size() >= 2 && distinct.size() <= 8 && values.size() >= 4 * distinct.size()
                && distinct.stream().noneMatch(v -> ToolText.fullmatch(Shapes.TOKEN_WITH_DIGIT, ToolText.str(v)))) {
                out.add(column);
            }
        }
        return out;
    }

    /// @param text A text.
    /// @return The non-blank lines of a text.
    public static List<String> lines(final String text) {
        return ToolText.nonBlankLines(text);
    }


    // --------------------------------------------------------------------------------


    /// One stream part split into records, with what is needed to write a sample back in the same form.
    ///
    /// @param format        How it was read: a [SampleProfiler] format (`json array`, `json object`,
    ///                      `xml fragments`, `xml`, `json lines`, `delimited`, `key=value`, `cef`, `syslog rfc3164`,
    ///                      `syslog rfc5424`, `cri`, `multi-line text` or `unknown text`).
    /// @param records       Each record's text, as it would appear in a sample.
    /// @param parsed        Each record's structured view where the format has one (else null): field name to
    ///                      value.
    /// @param header        For delimited text, the header line naming the columns; else null.
    /// @param root          For an XML document, its root element's opening and closing tags round `{records}`;
    ///                      else null.
    /// @param namingColumns For delimited records, the columns that name the kind of event.
    public record Chunk(String format,
                        List<String> records,
                        List<Map<String, Object>> parsed,
                        String header,
                        String root,
                        List<String> namingColumns) {

        /// @param index A record's index.
        /// @return Its structured view, or null if it has none.
        public Map<String, Object> parsedAt(final int index) {
            return parsed != null && index < parsed.size()
                    ? parsed.get(index)
                    : null;
        }
    }
}
