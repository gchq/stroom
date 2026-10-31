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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.pipeline.xml.converter.ds3.RootFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The Data Splitter XML a [SplitterSpec] is written as (the Stroom MCP server's `generate_splitter` in
/// `utils/dsgen.py`): the converter Stroom runs, dividing the text as [SplitterDryRun] does on a sample. It writes
/// the subset of Data Splitter the dry run follows, not the whole language.
public final class SplitterXml {

    /// The start of every Data Splitter written.
    public static final String DS_HEAD = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n"
            + "<dataSplitter xmlns=\"data-splitter:3\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "              xsi:schemaLocation=\"data-splitter:3 file://data-splitter-v3.0.xsd\" version=\"3.0\">\n"
            // A byte order mark before the first record, taken first: Stroom doesn't strip it, and it became part
            // of the first key (seen in eval case 51: '\uFEFFtime', so the first event had no time).
            + "  <regex pattern=\"^\\uFEFF\" maxMatch=\"1\" />\n";
    /// The end of every Data Splitter written.
    public static final String DS_TAIL = "</dataSplitter>\n";
    /// Example specs by the format they suit (`delimited`, `key=value`, `syslog rfc3164`, `syslog rfc5424`,
    /// `unknown text`), as the server offers them.
    public static final Map<String, Map<String, Object>> EXAMPLES = examples();

    /// What the Data Splitter schema (data-splitter-v3.0.xsd, `IdSimpleType`) allows as an element's id. Seen: an
    /// id with an underscore (`blank_line`) made Stroom refuse every converter built, and no record was stepped,
    /// though Stroom's parser alone (which [stroom.ai.impl.tools.sample.StroomSplitterRun] runs, without the
    /// schema) read them.
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-zA-Z0-9]*");
    /// A blank line at the place the records' split would read the next: white space and its new line, or white
    /// space to the end.
    public static final String BLANK_LINE = "^[^\\S\\n]*\\n|^[^\\S\\n]+$";
    /// The catch_all regex over a record: all of it, without the new line at its end; never the new line alone
    /// that a record the spec matched leaves.
    public static final String UNPARSED_RECORD = "^(?=.*\\S)(.*?)\\n?$";
    /// The fields kind's regex over what no field took: that text without the white space round it.
    public static final String UNPARSED_REST = "^\\s*(.+?)\\s*$";

    private static final String DELIMITED = "delimited";
    private static final String FIELDS = "fields";
    private static final String NEW_LINE = "\n";
    // The buffer sizes written are rounded up to this.
    private static final int BUFFER_STEP = 10_000;
    private static final Pattern DATA_SPLITTER_TAG = Pattern.compile("<dataSplitter\\b[^>]*>");
    private static final Pattern BUFFER_SIZE_ATTRIBUTE = Pattern.compile(
            "\\s+bufferSize\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private SplitterXml() {
        // Static only.
    }

    private static Map<String, Map<String, Object>> examples() {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        out.put(DELIMITED, JsonValues.dict("kind", DELIMITED, "delimiter", ",", "header", true, "quote", "\""));
        out.put("key=value", JsonValues.dict("kind", "key_value", "delimiter", " ", "pair_separator", "=", "quote",
                "\""));
        out.put("syslog rfc3164", JsonValues.dict("kind", "syslog", "rfc", "rfc3164", "body",
                JsonValues.dict("kind", "key_value")));
        out.put("syslog rfc5424", JsonValues.dict("kind", "syslog", "rfc", "rfc5424", "body",
                JsonValues.dict("kind", "key_value")));
        out.put("unknown text", JsonValues.dict("kind", "regex", "pattern", "^(\\S+ \\S+) (\\S+) (.*)$", "names",
                List.of("time", "host", "message")));
        return Collections.unmodifiableMap(out);
    }

    /// The Data Splitter XML: one record per line, divided as the spec says (or per record start or end, for
    /// records of several lines), as the Stroom MCP server wrote it.
    ///
    /// @param spec The spec.
    /// @return The converter's XML.
    public static String generateSplitter(final SplitterSpec spec) {
        return generate(spec, false);
    }

    /// The Data Splitter build_data_splitter gives Stroom: [#generateSplitter]'s, with an `id` on each of its parts
    /// (`header`, `record`, `fields`, `blankLine`, `body`, `unparsed`), which Stroom's errors name, so an error in
    /// processing says which part of the spec failed; and blank lines passed over, which Stroom otherwise writes as
    /// records of empty fields, with no error (seen in Stroom: `a,b` then a blank line gave a record `a=""`).
    ///
    /// @param spec The spec.
    /// @return The converter's XML.
    public static String converter(final SplitterSpec spec) {
        return generate(spec, true);
    }

    private static String generate(final SplitterSpec spec, final boolean ids) {
        final boolean delimited = DELIMITED.equals(spec.kind());
        if (delimited && spec.commentPrefix() != null && !spec.commentPrefix().isEmpty()) {
            return DS_HEAD + directivesXml(spec, ids) + DS_TAIL;
        }
        if (spec.blankLineRecords()) {
            return DS_HEAD + "  <regex" + id(ids, "record") + " pattern=\"" + attr(SplitterDryRun.BLANK_LINE_RECORD)
                   + "\">\n"
                   + "    <group" + id(ids, "fields") + " value=\"$1\">\n" + recordXml(spec, "      ", ids)
                   + catchAllXml(spec, "      ", ids) + "    </group>\n  </regex>\n" + DS_TAIL;
        }
        if ((hasText(spec.recordStart()) || hasText(spec.recordEnd())) && !delimited) {
            final String pattern = hasText(spec.recordStart())
                    ? recordRegex(spec.recordStart())
                    : recordEndRegex(spec.recordEnd());
            return DS_HEAD + "  <regex" + id(ids, "record") + " pattern=\"" + attr(pattern) + "\">\n"
                   + "    <group" + id(ids, "fields") + " value=\"$1\">\n" + recordXml(spec, "      ", ids)
                   + catchAllXml(spec, "      ", ids) + "    </group>\n  </regex>\n" + DS_TAIL;
        }
        // A new line inside quotes is part of the value when quoted_newlines says so: the records' own split
        // ignores it.
        final String linesContainer = spec.quotedNewlines()
                ? container(spec)
                : "";
        String body = "";
        if (delimited && spec.headerLine()) {
            body += "  <split" + id(ids, "header") + " delimiter=\"\\n\" maxMatch=\"1\"" + linesContainer
                    + ">\n    <group>\n"
                    + "      <split delimiter=\"" + attr(spec.delimiter()) + "\"" + container(spec) + escape(spec)
                    + ">\n"
                    + "        <var id=\"heading\" />\n"
                    + "      </split>\n    </group>\n  </split>\n";
        }
        body += blankLineXml("  ", ids)
                + "  <split" + id(ids, "record") + " delimiter=\"\\n\"" + linesContainer + ">\n"
                + "    <group" + id(ids, "fields") + ">\n" + recordXml(spec, "      ", ids)
                + catchAllXml(spec, "      ", ids) + "    </group>\n  </split>\n";
        return DS_HEAD + body + DS_TAIL;
    }

    // An id attribute, when ids are written. Each must match ID_PATTERN.
    private static String id(final boolean ids, final String id) {
        return ids
                ? " id=\"" + id + "\""
                : "";
    }

    // A line holding nothing but white space, taken with no data written, so it makes no record. Tried at the top
    // level, before the records' split, so only ever at a line's start: inside a record's group it would take the
    // new line after an empty last cell, and that cell with it.
    private static String blankLineXml(final String indent, final boolean ids) {
        return ids
                ? indent + "<regex id=\"blankLine\" pattern=\"" + attr(BLANK_LINE) + "\" />\n"
                : "";
    }

    // With catch_all, a record the spec didn't match kept whole as `unparsed` (without its new line), instead of
    // an error and the record lost. The fields kind keeps its own (what no field took).
    private static String catchAllXml(final SplitterSpec spec, final String indent, final boolean ids) {
        if (!spec.catchAll() || FIELDS.equals(spec.kind())) {
            return "";
        }
        return indent + "<regex" + id(ids, "unparsed") + " pattern=\"" + attr(UNPARSED_RECORD)
               + "\" dotAll=\"true\">\n" + indent + "  <data name=\"" + SplitterSpec.UNPARSED
               + "\" value=\"$1\" />\n" + indent + "</regex>\n";
    }

    private static String escape(final SplitterSpec spec) {
        return hasText(spec.escape())
                ? " escape=\"" + attr(spec.escape()) + "\""
                : "";
    }

    /// @param start A regex the first line of a record matches at its start.
    /// @return The regex taking one record of several lines: from a line that starts one to the next such line, or
    /// the end.
    public static String recordRegex(final String start) {
        return "(?sm)^((?:" + start + ").*?)\\n?(?=^(?:" + start + ")|\\z)";
    }

    /// @param end A regex the last line of a record matches at its start.
    /// @return The regex taking one record whose last line is marked: lines that aren't an end, then the end line.
    public static String recordEndRegex(final String end) {
        return "(?m)^((?:(?!(?:" + end + "))[^\\n]*\\n)*(?:" + end + ")[^\\n]*)\\n?";
    }

    /// The read buffer a Data Splitter needs for records as long as the longest seen. Stroom reads a Data Splitter's
    /// input through a buffer of `bufferSize` characters (20,000 by default), refilled only once less than half of
    /// it is unread, so only a record up to half of it is sure to fit; a longer one may match nothing, so be lost.
    /// Seen in testing: CSV lines of about 20,000 characters dropped, and a 751,000-character one with a buffer of
    /// 1,000,000. A sample need not hold the feed's longest record, so the size allows four times the longest seen.
    ///
    /// @param longestRecord The length of the longest record seen, in characters.
    /// @return The `bufferSize` to write, or empty when Stroom's default leaves enough room (twice the longest).
    public static OptionalInt bufferSizeFor(final int longestRecord) {
        if ((long) longestRecord * 2 <= RootFactory.DEFAULT_BUFFER_SIZE) {
            return OptionalInt.empty();
        }
        final long wanted = (long) longestRecord * 4;
        final long rounded = ((wanted + BUFFER_STEP - 1) / BUFFER_STEP) * BUFFER_STEP;
        return OptionalInt.of((int) Math.min(rounded, RootFactory.MAX_BUFFER_SIZE));
    }

    /// @param text Sample text.
    /// @return The length of its longest line, in characters.
    public static int longestLine(final String text) {
        int longest = 0;
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            longest = Math.max(longest, end - start);
            start = end + 1;
        }
        return longest;
    }

    /// The Data Splitter with its read buffer set: `bufferSize` on its `<dataSplitter>` element, replacing any
    /// there.
    ///
    /// @param xml        A Data Splitter.
    /// @param bufferSize The read buffer, in characters, between Stroom's default (20,000) and its most
    ///                   (1,000,000,000).
    /// @return The Data Splitter with that buffer; unchanged when it has no `<dataSplitter>` element.
    /// @throws IllegalArgumentException if the size is outside Stroom's range.
    public static String withBufferSize(final String xml, final int bufferSize) {
        if (bufferSize < RootFactory.MIN_BUFFER_SIZE || bufferSize > RootFactory.MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException("A Data Splitter's bufferSize is from " + RootFactory.MIN_BUFFER_SIZE
                                               + " to " + RootFactory.MAX_BUFFER_SIZE + ", not " + bufferSize);
        }
        final Matcher tag = DATA_SPLITTER_TAG.matcher(xml);
        if (!tag.find()) {
            return xml;
        }
        final String without = BUFFER_SIZE_ATTRIBUTE.matcher(tag.group()).replaceAll("");
        final int close = without.endsWith("/>")
                ? without.length() - 2
                : without.length() - 1;
        final String with = without.substring(0, close) + " bufferSize=\"" + bufferSize + "\""
                            + without.substring(close);
        return xml.substring(0, tag.start()) + with + xml.substring(tag.end());
    }

    private static boolean hasText(final String text) {
        return text != null && !text.isEmpty();
    }

    // A value as an attribute's text, with Data Splitter's own escapes for new lines and tabs.
    private static String attr(final String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "\\n").replace("\t", "\\t");
    }

    private static String container(final SplitterSpec spec) {
        return hasText(spec.quote())
                ? " containerStart=\"" + attr(spec.quote()) + "\" containerEnd=\"" + attr(spec.quote()) + "\""
                : "";
    }

    // Elements that parse the text in `group` ($n of the enclosing regex) with spec.body; the top body's group
    // given its id (ids are unique in a converter, and a body may have a body of its own).
    private static String bodyXml(final SplitterSpec spec, final String group, final String indent,
                                  final boolean ids) {
        if (spec.body() == null) {
            return "";
        }
        return indent + "<group" + id(ids, "body") + " value=\"" + group + "\">\n"
               + recordXml(spec.body(), indent + "  ", false) + indent + "</group>\n";
    }

    // The elements that divide one record (the text in scope) into data elements.
    private static String recordXml(final SplitterSpec spec, final String indent, final boolean ids) {
        switch (spec.kind()) {
            case "regex" -> {
                return regexXml(spec, indent, ids);
            }
            case FIELDS -> {
                return fieldsXml(spec, indent);
            }
            case "syslog" -> {
                final boolean rfc5424 = "rfc5424".equals(spec.rfc());
                final SplitterSpec inner = SplitterSpec.builder("regex")
                        .pattern(rfc5424
                                ? SplitterDryRun.SYSLOG_5424
                                : SplitterDryRun.SYSLOG_3164)
                        .names(rfc5424
                                ? SplitterDryRun.SYSLOG_5424_NAMES
                                : SplitterDryRun.SYSLOG_3164_NAMES)
                        .body(spec.body())
                        .bodyField(spec.bodyField())
                        .build();
                return recordXml(inner, indent, ids);
            }
            case "cef" -> {
                return cefXml(spec, indent, ids);
            }
            case "key_value" -> {
                // One regex, matched again and again along the text: a pair each time, a quoted value without its
                // quotes. Seen: a split on '=' writing $2, which a split doesn't have ("Group number 2 not found"),
                // so every key=value source failed in Stroom while the dry run, parsing on its own, said it was
                // fine.
                if (spec.spacedValues()) {
                    return spacedPairsXml(spec, indent);
                }
                final StringBuilder out = new StringBuilder();
                for (final String pattern : SplitterDryRun.kvPatterns(spec)) {
                    out.append(indent).append("<regex pattern=\"").append(attr(pattern)).append("\">\n")
                            .append(indent).append("  <data name=\"$1\" value=\"$2\" />\n")
                            .append(indent).append("</regex>\n");
                }
                return out.toString();
            }
            default -> {
                return delimitedXml(spec, indent);
            }
        }
    }

    // key=value pairs whose values hold the delimiter unquoted: read from the end, where each value comes before
    // its key and a key holds no delimiter (the Stroom docs' reverse example: `zones=Zone 1, Zone 2 user=An end
    // user`).
    private static String spacedPairsXml(final SplitterSpec spec, final String indent) {
        // A group is only ever an expression's child: <all> hands it the whole record.
        return indent + "<all>\n"
               + indent + "  <group reverse=\"true\">\n"
               + indent + "    <regex pattern=\"" + attr(SplitterDryRun.spacedPairPattern(spec)) + "\">\n"
               + indent + "      <data name=\"$2\" value=\"$1\" />\n"
               + indent + "    </regex>\n"
               + indent + "  </group>\n"
               + indent + "</all>\n";
    }

    // Each field found by its own regex, anywhere in the record and in any order (matchOrder any takes each
    // match out of the text); what no field took is passed over, or kept as `unparsed` with catch_all.
    private static String fieldsXml(final SplitterSpec spec, final String indent) {
        // A group is only ever an expression's child: <all> hands it the whole record.
        final String inner = indent + "    ";
        final StringBuilder out = new StringBuilder(indent + "<all>\n" + indent + "  <group matchOrder=\"any\">\n");
        spec.fields().forEach((name, regex) -> out.append(inner).append("<regex pattern=\"").append(attr(regex))
                .append("\">\n").append(inner).append("  <data name=\"").append(attr(name)).append("\" value=\"")
                .append(Pattern.compile(regex).matcher("").groupCount() > 0
                        ? "$1"
                        : "$")
                .append("\" />\n").append(inner).append("</regex>\n"));
        if (spec.catchAll()) {
            // What no field took, without the white space round it; nothing when that is only white space.
            out.append(inner).append("<regex pattern=\"^\\s*$\" />\n")
                    .append(inner).append("<regex pattern=\"").append(attr(UNPARSED_REST))
                    .append("\" dotAll=\"true\">\n")
                    .append(inner).append("  <data name=\"").append(SplitterSpec.UNPARSED)
                    .append("\" value=\"$1\" />\n")
                    .append(inner).append("</regex>\n");
        } else {
            out.append(inner).append("<all />\n");
        }
        return out.append(indent).append("  </group>\n").append(indent).append("</all>\n").toString();
    }

    private static String regexXml(final SplitterSpec spec, final String indent, final boolean ids) {
        final List<String> lines = new ArrayList<>();
        lines.add(indent + "<regex pattern=\"" + attr(spec.pattern()) + "\">");
        String bodyGroup = null;
        for (int n = 1; n <= spec.names().size(); n++) {
            final String name = spec.names().get(n - 1);
            if (!name.isEmpty()) {
                lines.add(indent + "  <data name=\"" + attr(name) + "\" value=\"$" + n + "\" />");
            }
            if (name.equals(spec.bodyField())) {
                bodyGroup = "$" + n;
            }
        }
        if (spec.body() != null && bodyGroup != null) {
            lines.add(PyText.rstrip(bodyXml(spec, bodyGroup, indent + "  ", ids), NEW_LINE));
        }
        lines.add(indent + "</regex>");
        return String.join(NEW_LINE, lines) + NEW_LINE;
    }

    private static String cefXml(final SplitterSpec spec, final String indent, final boolean ids) {
        final List<String> lines = new ArrayList<>();
        lines.add(indent + "<regex pattern=\"" + attr(SplitterDryRun.CEF_PATTERN) + "\">");
        if (spec.body() != null) {
            lines.add(PyText.rstrip(bodyXml(spec, "$1", indent + "  ", ids), NEW_LINE));
        }
        for (int n = 0; n < SplitterDryRun.CEF_NAMES.size(); n++) {
            lines.add(indent + "  <data name=\"" + SplitterDryRun.CEF_NAMES.get(n) + "\" value=\"$" + (n + 2)
                      + "\" />");
        }
        lines.add(indent + "  <group value=\"$9\">");
        lines.add(indent + "    <regex pattern=\"" + attr(SplitterDryRun.CEF_PAIR) + "\">");
        lines.add(indent + "      <data name=\"$1\" value=\"$2\" />");
        lines.add(indent + "    </regex>");
        lines.add(indent + "  </group>");
        lines.add(indent + "</regex>");
        return String.join(NEW_LINE, lines) + NEW_LINE;
    }

    private static String delimitedXml(final SplitterSpec spec, final String indent) {
        // With an escape character, $2: the cell with its escapes taken out ($1 keeps them).
        final String value = hasText(spec.escape())
                ? "$2"
                : "$1";
        if (spec.headerLine()) {
            return indent + "<split delimiter=\"" + attr(spec.delimiter()) + "\"" + container(spec) + escape(spec)
                   + ">\n" + indent + "  <data name=\"$heading$1\" value=\"" + value + "\" />\n" + indent
                   + "</split>\n";
        }
        if (hasText(spec.escape())) {
            // Named columns with escapes: a split per column, each taking one cell in turn (a regex can't take
            // escapes out).
            final StringBuilder out = new StringBuilder();
            for (final String column : spec.columns()) {
                out.append(indent).append("<split delimiter=\"").append(attr(spec.delimiter())).append("\"")
                        .append(container(spec)).append(escape(spec)).append(" maxMatch=\"1\">\n")
                        .append(indent).append("  <data name=\"").append(attr(column)).append("\" value=\"$2\" />\n")
                        .append(indent).append("</split>\n");
            }
            return out.toString();
        }
        // Named columns without a header line: one regex over the record, one group per column.
        final List<String> lines = new ArrayList<>();
        lines.add(indent + "<regex pattern=\"" + attr(SplitterDryRun.namedColumnsPattern(spec)) + "\">");
        for (int n = 1; n <= spec.columns().size(); n++) {
            lines.add(indent + "  <data name=\"" + attr(spec.columns().get(n - 1)) + "\" value=\"$" + n + "\" />");
        }
        lines.add(indent + "</regex>");
        return String.join(NEW_LINE, lines) + NEW_LINE;
    }

    // One record per line of a log with directive lines (W3C, Zeek): the header directive sets the column names
    // for the lines after it, each other directive kept is held for the records after it, other comment lines are
    // passed over, and each data line is split under the latest column names.
    private static String directivesXml(final SplitterSpec spec, final boolean ids) {
        final List<String> lines = new ArrayList<>();
        if (ids) {
            lines.add(PyText.rstrip(blankLineXml("  ", true), NEW_LINE));
        }
        lines.add("  <split" + id(ids, "record") + " delimiter=\"\\n\">");
        lines.add("    <group" + id(ids, "fields") + ">");
        if (hasText(spec.headerDirective())) {
            lines.add("      <regex pattern=\"^" + attr(PyText.escape(spec.headerDirective())) + "\\s*(.*)$\">");
            lines.add("        <group value=\"$1\">");
            lines.add("          <split delimiter=\"" + attr(spec.delimiter()) + "\"" + container(spec) + ">");
            lines.add("            <var id=\"heading\" />");
            lines.add("          </split>");
            lines.add("        </group>");
            lines.add("      </regex>");
        }
        for (int n = 0; n < spec.directives().size(); n++) {
            lines.add("      <regex pattern=\"^" + attr(PyText.escape(spec.directives().get(n))) + "\\s*(.*)$\">");
            lines.add("        <var id=\"directive" + n + "\" />");
            lines.add("      </regex>");
        }
        lines.add("      <regex pattern=\"^" + attr(PyText.escape(spec.commentPrefix())) + ".*$\" />");
        lines.add("      <regex pattern=\"^(.+)$\">");
        for (int n = 0; n < spec.directives().size(); n++) {
            lines.add("        <data name=\"" + attr(SplitterDryRun.directiveField(spec.directives().get(n)))
                      + "\" value=\"$directive" + n + "$1\" />");
        }
        lines.add("        <group value=\"$1\">");
        lines.add(PyText.rstrip(recordXml(spec, "          ", false), NEW_LINE));
        lines.add("        </group>");
        lines.add("      </regex>");
        lines.add("    </group>");
        lines.add("  </split>");
        return String.join(NEW_LINE, lines) + NEW_LINE;
    }
}
