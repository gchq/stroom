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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The generator's test fixtures: the event-logging schemas, the base mapping (`mapping(overrides)`) and its fields.
class GenerationFixtures {

    // The golden XSLTs' own function rounding a time to the millisecond, as the generator once wrote it.
    private static final Pattern TIME_MS_FUNCTION = Pattern.compile(
            "[ \\t]*<xsl:function name=\"mcp:time-ms\"[\\s\\S]*?</xsl:function>\\n");
    // A documentation run's rule marker, as the golden XSLTs have it.
    private static final Pattern RULE_MARKER = Pattern.compile("<!--" + Pattern.quote(XsltGenerator.RULE_MARK)
                                                               + "([^-]*)-->");
    // The record's number in a log line, as the golden XSLTs have it.
    private static final String LOGGED_RECORD_NO = "record ', stroom:record-no()";
    // The opening line of a template, with its indent.
    private static final Pattern EVENT_TEMPLATE = Pattern.compile("(?m)^([ \\t]*)<xsl:template match=\"\\*\" "
                                                                  + "mode=\"event\">\\n");
    private static final Pattern ITEM_TEMPLATE = Pattern.compile("(?m)^([ \\t]*)<xsl:template match=\"\\*\" "
                                                                 + "mode=\"item\">\\n");
    // The record handed to the items, with its indent.
    private static final Pattern RECORD_TO_ITEMS = Pattern.compile("(?m)^([ \\t]*)<xsl:with-param name=\"record\" "
                                                                   + "select=\"\\.\" tunnel=\"yes\"/>\\n");
    // The mcp prefix in use (not its namespace's own name, stroom-mcp:functions).
    private static final Pattern MCP_PREFIX = Pattern.compile("(?<![\\w-])mcp:[\\w-]");
    // A call of it: the value, the pattern and the zone ('' for none).
    private static final Pattern TIME_MS_CALL = Pattern.compile(
            "mcp:time-ms\\(string\\(([^()]*)\\), ('(?:[^']|'')*'), ('(?:[^']|'')*')\\)");

    /// The event-logging 4.1.0 XSD.
    static final String XSD = HeldSchemasWorld.XSD_4_1_0;
    /// The event-logging 4.1.0 schema.
    static final EventSchema SCHEMA = EventSchema.parse(XSD);
    /// The event-logging 3.5.2 XSD.
    static final String XSD_352 = resource("event-logging-v3.5.2.xsd");
    /// The event-logging 3.5.2 schema.
    static final EventSchema SCHEMA_352 = EventSchema.parse(XSD_352);

    /// The common fields of the base mapping.
    static final List<Map<String, Object>> BASE = json("""
            [{"path": "EventTime/TimeCreated", "field": "time"},
             {"path": "EventSource/System/Name", "value": "Acme VPN"},
             {"path": "EventSource/System/Environment", "value": "Dev"},
             {"path": "EventSource/Generator", "value": "vpnd"},
             {"path": "EventSource/Device/HostName", "field": "host", "default": "unknown"},
             {"path": "EventSource/User/Id", "field": "user"}]""");
    /// The logon rule's fields.
    static final List<Map<String, Object>> LOGON = json("""
            [{"path": "EventDetail/TypeId", "value": "Logon"},
             {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
             {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
             {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result",
              "map": {"ok": "true", "fail": "false"}},
             {"path": "EventDetail/Authenticate/Data", "data_name": "session", "field": "sid"}]""");
    /// Two records, a login and a keepalive.
    static final String RECORDS = """
            <records xmlns="records:2">
            <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="action" value="login"/>\
            <data name="user" value="o'neil"/>
            <data name="result" value="fail"/><data name="sid" value="s1"/></record>
            <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="action" value="keepalive"/>\
            <data name="host" value="ws01"/></record>
            </records>""";

    GenerationFixtures() {
        // Static only, and the base of the generator tests, which use its fixtures unqualified.
    }

    /// @return The base mapping's JSON: data_splitter input, unmatched skip, BASE in common, and the logon rule
    /// and the rule for the rest (Unknown).
    static Map<String, Object> baseJson() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "data_splitter");
        mapping.put("unmatched", "skip");
        mapping.put("common", BASE);
        mapping.put("events", List.of(rule("logon", List.of(equalsCondition("action", "login")), LOGON),
                otherRule()));
        return mapping;
    }

    /// @param overrides Keys replaced in the base mapping.
    /// @return The base mapping with them, validated (`mapping(**overrides)`).
    static TranslationMapping mapping(final Map<String, Object> overrides) {
        final Map<String, Object> mapping = baseJson();
        mapping.putAll(overrides);
        return TranslationMapping.fromJson(mapping);
    }

    /// @return The base mapping.
    static TranslationMapping mapping() {
        return mapping(Map.of());
    }

    /// @param keysAndValues Keys and their values, in turn.
    /// @return The overrides.
    static Map<String, Object> with(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    /// @return The rule for the rest of the base mapping.
    static Map<String, Object> otherRule() {
        return rule("other", List.of(), json("""
                [{"path": "EventDetail/TypeId", "field": "action"},
                 {"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]"""));
    }

    static Map<String, Object> rule(final String name, final List<?> when, final List<?> fields) {
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name);
        if (!when.isEmpty()) {
            rule.put("when", when);
        }
        rule.put("fields", fields);
        return rule;
    }

    static Map<String, Object> equalsCondition(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }

    /// @param text JSON text.
    /// @param <T>  What it is.
    /// @return Its value, read by [ToolJson].
    @SuppressWarnings("unchecked")
    static <T> T json(final String text) {
        return (T) ToolJson.loads(text);
    }

    /// @param entries Field mappings' JSON.
    /// @param path    A path.
    /// @return Them without that path.
    static List<Map<String, Object>> without(final List<Map<String, Object>> entries, final String path) {
        return entries.stream().filter(entry -> !path.equals(entry.get("path"))).toList();
    }

    /// @param entries Field mappings' JSON.
    /// @param more    One more.
    /// @return Them followed by it.
    static List<Map<String, Object>> plus(final List<Map<String, Object>> entries, final Map<String, Object> more) {
        final List<Map<String, Object>> out = new ArrayList<>(entries);
        out.add(more);
        return out;
    }

    /// @param entries Field mappings' JSON.
    /// @param more    One more.
    /// @param last    And another.
    /// @return Them followed by those.
    static List<Map<String, Object>> plus(final List<Map<String, Object>> entries,
                                          final Map<String, Object> more,
                                          final Map<String, Object> last) {
        return plus(plus(entries, more), last);
    }

    /// @param entries Field mappings' JSON.
    /// @param more    One more.
    /// @param next    Another.
    /// @param last    And a third.
    /// @return Them followed by those.
    static List<Map<String, Object>> plus(final List<Map<String, Object>> entries,
                                          final Map<String, Object> more,
                                          final Map<String, Object> next,
                                          final Map<String, Object> last) {
        return plus(plus(plus(entries, more), next), last);
    }

    /// A golden XSLT as the generator writes it now: a documentation run's rule marker written as a processing
    /// instruction, so the output carries it through Stroom's stepping (a comment in the stylesheet isn't copied to
    /// the output, and one written is dropped).
    ///
    /// @param xslt A golden XSLT.
    /// @return It as the generator writes it now.
    static String asThePortMarksRules(final String xslt) {
        return RULE_MARKER.matcher(xslt).replaceAll(match -> Matcher.quoteReplacement(
                "<xsl:processing-instruction name=\"" + XsltGenerator.RULE_INSTRUCTION + "\">" + match.group(1)
                + "</xsl:processing-instruction>"));
    }

    /// A golden XSLT as the generator writes it now: its log lines' record number read once, first, for every
    /// record ([XsltGenerator#READ_RECORD_NO]), and handed to the items' template when there are items; the golden
    /// XSLTs read it in the log line alone, which gives a later record's number in processing.
    ///
    /// @param xslt A golden XSLT.
    /// @return It as the generator writes it now.
    static String asThePortReadsRecordNumbers(final String xslt) {
        if (!xslt.contains(LOGGED_RECORD_NO)) {
            return xslt;
        }
        String out = xslt.replace(LOGGED_RECORD_NO, "record ', $" + XsltGenerator.RECORD_NO);
        out = EVENT_TEMPLATE.matcher(out).replaceFirst(match -> Matcher.quoteReplacement(match.group()
                + match.group(1) + "  <xsl:variable name=\"" + XsltGenerator.RECORD_NO
                + "\" select=\"stroom:record-no()\"/>\n"));
        out = afterLeadingVariables(out, "  <xsl:sequence select=\"" + XsltGenerator.READ_RECORD_NO + "\"/>");
        if (ITEM_TEMPLATE.matcher(out).find()) {
            out = RECORD_TO_ITEMS.matcher(out).replaceFirst(match -> Matcher.quoteReplacement(match.group()
                    + match.group(1) + "<xsl:with-param name=\"" + XsltGenerator.RECORD_NO + "\" select=\"$"
                    + XsltGenerator.RECORD_NO + "\" tunnel=\"yes\"/>\n"));
            out = ITEM_TEMPLATE.matcher(out).replaceFirst(match -> Matcher.quoteReplacement(match.group()
                    + match.group(1) + "  <xsl:param name=\"" + XsltGenerator.RECORD_NO + "\" tunnel=\"yes\"/>\n"));
        }
        return out;
    }

    // The XSLT with a line put in the event template after its leading variables (each of one line, or over several
    // lines indented deeper), at the template's indent plus the line's own.
    private static String afterLeadingVariables(final String xslt, final String line) {
        final Matcher template = EVENT_TEMPLATE.matcher(xslt);
        if (!template.find()) {
            return xslt;
        }
        final String indent = template.group(1) + "  ";
        int at = template.end();
        while (at < xslt.length()) {
            final int end = xslt.indexOf('\n', at) + 1;
            final String next = xslt.substring(at, end);
            final boolean variable = next.startsWith(indent + "<xsl:variable ")
                                     || next.startsWith(indent + "</xsl:variable>")
                                     || next.startsWith(indent + " ");
            if (!variable) {
                break;
            }
            at = end;
        }
        return xslt.substring(0, at) + template.group(1) + line + "\n" + xslt.substring(at);
    }

    /// A golden XSLT as the generator writes it now: times finer than a millisecond cut, not rounded (see
    /// [XPathText#timeExpression]): an epoch time with floor, and a pattern's digits by Stroom's format-date alone,
    /// with no mcp:time-ms function (nor the mcp prefix, if nothing else uses it).
    ///
    /// @param xslt A golden XSLT.
    /// @return It as the generator writes it now.
    static String asThePortWritesTimes(final String xslt) {
        String out = xslt.replace("xs:integer(round(", "xs:integer(floor(");
        out = TIME_MS_FUNCTION.matcher(out).replaceAll("");
        out = TIME_MS_CALL.matcher(out).replaceAll(match -> {
            final String zone = "''".equals(match.group(3))
                    ? ""
                    : ", " + match.group(3);
            return Matcher.quoteReplacement("stroom:format-date(" + match.group(1) + ", " + match.group(2) + zone
                                            + ")");
        });
        if (!MCP_PREFIX.matcher(out).find()) {
            out = out.replace(" xmlns:mcp=\"stroom-mcp:functions\"", "")
                    .replaceAll("(exclude-result-prefixes=\"[^\"]*?)\\s*\\bmcp\\b", "$1");
        }
        return out;
    }

    static String resource(final String name) {
        try (final InputStream in = GenerationFixtures.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
