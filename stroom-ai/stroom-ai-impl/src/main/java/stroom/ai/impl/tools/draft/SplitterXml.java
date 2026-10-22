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

import stroom.ai.impl.tools.sample.PyText;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final String DELIMITED = "delimited";
    private static final String NEW_LINE = "\n";

    private SplitterXml() {
        // Static only.
    }

    private static Map<String, Map<String, Object>> examples() {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        out.put(DELIMITED, PyValues.dict("kind", DELIMITED, "delimiter", ",", "header", true, "quote", "\""));
        out.put("key=value", PyValues.dict("kind", "key_value", "delimiter", " ", "pair_separator", "=", "quote",
                "\""));
        out.put("syslog rfc3164", PyValues.dict("kind", "syslog", "rfc", "rfc3164", "body",
                PyValues.dict("kind", "key_value")));
        out.put("syslog rfc5424", PyValues.dict("kind", "syslog", "rfc", "rfc5424", "body",
                PyValues.dict("kind", "key_value")));
        out.put("unknown text", PyValues.dict("kind", "regex", "pattern", "^(\\S+ \\S+) (\\S+) (.*)$", "names",
                List.of("time", "host", "message")));
        return Collections.unmodifiableMap(out);
    }

    /// The Data Splitter XML: one record per line, divided as the spec says (or per record start or end, for
    /// records of several lines).
    ///
    /// @param spec The spec.
    /// @return The converter's XML.
    public static String generateSplitter(final SplitterSpec spec) {
        final boolean delimited = DELIMITED.equals(spec.kind());
        if (delimited && spec.commentPrefix() != null && !spec.commentPrefix().isEmpty()) {
            return DS_HEAD + directivesXml(spec) + DS_TAIL;
        }
        if ((hasText(spec.recordStart()) || hasText(spec.recordEnd())) && !delimited) {
            final String pattern = hasText(spec.recordStart())
                    ? recordRegex(spec.recordStart())
                    : recordEndRegex(spec.recordEnd());
            return DS_HEAD + "  <regex pattern=\"" + attr(pattern) + "\">\n    <group value=\"$1\">\n"
                   + recordXml(spec, "      ") + "    </group>\n  </regex>\n" + DS_TAIL;
        }
        String body = "";
        if (delimited && spec.headerLine()) {
            body += "  <split delimiter=\"\\n\" maxMatch=\"1\">\n    <group>\n"
                    + "      <split delimiter=\"" + attr(spec.delimiter()) + "\"" + container(spec) + ">\n"
                    + "        <var id=\"heading\" />\n"
                    + "      </split>\n    </group>\n  </split>\n";
        }
        // A new line inside quotes is part of the value when quoted_newlines says so: the records' own split
        // ignores it.
        final String linesContainer = spec.quotedNewlines()
                ? container(spec)
                : "";
        if (!linesContainer.isEmpty() && !body.isEmpty()) {
            body = body.replace("<split delimiter=\"\\n\" maxMatch=\"1\">",
                    "<split delimiter=\"\\n\" maxMatch=\"1\"" + linesContainer + ">");
        }
        body += "  <split delimiter=\"\\n\"" + linesContainer + ">\n    <group>\n" + recordXml(spec, "      ")
                + "    </group>\n  </split>\n";
        return DS_HEAD + body + DS_TAIL;
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

    // Elements that parse the text in `group` ($n of the enclosing regex) with spec.body.
    private static String bodyXml(final SplitterSpec spec, final String group, final String indent) {
        if (spec.body() == null) {
            return "";
        }
        return indent + "<group value=\"" + group + "\">\n" + recordXml(spec.body(), indent + "  ") + indent
               + "</group>\n";
    }

    // The elements that divide one record (the text in scope) into data elements.
    private static String recordXml(final SplitterSpec spec, final String indent) {
        switch (spec.kind()) {
            case "regex" -> {
                return regexXml(spec, indent);
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
                return recordXml(inner, indent);
            }
            case "cef" -> {
                return cefXml(spec, indent);
            }
            case "key_value" -> {
                // One regex, matched again and again along the text: a pair each time, a quoted value without its
                // quotes. Seen: a split on '=' writing $2, which a split doesn't have ("Group number 2 not found"),
                // so every key=value source failed in Stroom while the dry run, parsing on its own, said it was
                // fine.
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

    private static String regexXml(final SplitterSpec spec, final String indent) {
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
            lines.add(PyText.rstrip(bodyXml(spec, bodyGroup, indent + "  "), NEW_LINE));
        }
        lines.add(indent + "</regex>");
        return String.join(NEW_LINE, lines) + NEW_LINE;
    }

    private static String cefXml(final SplitterSpec spec, final String indent) {
        final List<String> lines = new ArrayList<>();
        lines.add(indent + "<regex pattern=\"" + attr(SplitterDryRun.CEF_PATTERN) + "\">");
        if (spec.body() != null) {
            lines.add(PyText.rstrip(bodyXml(spec, "$1", indent + "  "), NEW_LINE));
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
        if (spec.headerLine()) {
            return indent + "<split delimiter=\"" + attr(spec.delimiter()) + "\"" + container(spec) + ">\n"
                   + indent + "  <data name=\"$heading$1\" value=\"$1\" />\n" + indent + "</split>\n";
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
    private static String directivesXml(final SplitterSpec spec) {
        final List<String> lines = new ArrayList<>(List.of("  <split delimiter=\"\\n\">", "    <group>"));
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
        lines.add(PyText.rstrip(recordXml(spec, "          "), NEW_LINE));
        lines.add("        </group>");
        lines.add("      </regex>");
        lines.add("    </group>");
        lines.add("  </split>");
        return String.join(NEW_LINE, lines) + NEW_LINE;
    }
}
