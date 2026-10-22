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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.StyledXslt;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltComments;
import stroom.ai.impl.tools.generation.XsltStyle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// The CEF XSLT a plan generates: a Kafka record per Event whose value is the CEF line (or the lines as text),
/// written as an Events translation is (asked for by the user): a template a part (the line, each kind of event's
/// keys, the keys every event gets), in its own mode, or named, or inline (the style's layout), named in the style's
/// naming, each with a comment; an input read often, a variable. Ported from the Stroom MCP server's `render_xslt`
/// in `utils/cef.py`; the text is the same as the server's.
public final class CefXslt {

    /// The XSLT's functions: escaping a header field and a key=value pair (and a custom slot's label), a time as
    /// milliseconds since 1970, and Outcome/Success as CEF's outcome.
    public static final String FUNCTIONS = functions();

    private CefXslt() {
        // Static only.
    }

    /// `render_xslt`: the CEF XSLT.
    ///
    /// @param plan The plan.
    /// @return The XSLT's text.
    /// @throws IllegalArgumentException if an expression is unsafe: the XSLT isn't written.
    public static String render(final CefPlan plan) {
        final List<String> unsafe = plan.unsafe();
        if (!unsafe.isEmpty()) {
            throw new IllegalArgumentException("The CEF XSLT isn't written: " + String.join("; ", unsafe));
        }
        final XsltStyle style = plan.style();
        final Set<String> taken = new HashSet<>();
        final String header = CefKeys.HEADER.stream()
                .map(field -> "cef:h(" + headerExpression(plan.header(field.name())) + ", " + field.length() + ")")
                .collect(Collectors.joining(", "));
        final String lineMode = StyledXslt.partName("cef-line", style, taken);
        final String extensionMode = StyledXslt.partName("cef-extension", style, taken);
        final String commonMode = StyledXslt.partName("cef-common", style, taken);
        final boolean named = "named".equals(style.layout());

        final List<String> parts = new ArrayList<>();
        final String pairs;
        if ("inline".equals(style.layout())) {
            // Every kind of event in one choose, as the inline layout writes the Events translation's rules.
            final StringBuilder whens = new StringBuilder();
            plan.events().forEach((kind, fields) -> {
                if (!"other".equals(kind)) {
                    whens.append("<xsl:when test=\"EventDetail/").append(kind).append("\">").append(fieldLines(fields))
                            .append("</xsl:when>");
                }
            });
            final String otherwise = fieldLines(plan.events().getOrDefault("other", List.of()));
            pairs = (!whens.isEmpty()
                    ? "<xsl:choose>" + whens + "<xsl:otherwise>" + otherwise + "</xsl:otherwise></xsl:choose>"
                    : otherwise) + fieldLines(plan.common());
        } else {
            // Each kind of event's keys: a template rule matching it (a named template can't choose by kind).
            plan.events().forEach((kind, fields) -> {
                final String match = !"other".equals(kind)
                        ? "Event[EventDetail/" + kind + "]"
                        : "Event";
                parts.add("<!--" + XsltComments.comment(kind + " events: " + keysSaid(fields)) + "-->"
                          + "<xsl:template match=\"" + match + "\" mode=\"" + extensionMode
                          + "\" as=\"xs:string*\">" + fieldLines(fields) + "</xsl:template>");
            });
            if (!plan.events().containsKey("other")) {
                parts.add("<xsl:template match=\"Event\" mode=\"" + extensionMode + "\" as=\"xs:string*\" />");
            }
            if (!plan.common().isEmpty()) {
                parts.add("<!--" + XsltComments.comment(commonMode + ": every event: " + keysSaid(plan.common()))
                          + "-->" + own(named, commonMode, "xs:string*") + fieldLines(plan.common())
                          + "</xsl:template>");
            }
            pairs = "<xsl:apply-templates select=\".\" mode=\"" + extensionMode + "\" />" + (!plan.common().isEmpty()
                    ? apply(named, commonMode)
                    : "");
        }
        final String line = "  <!-- " + lineMode + ": header|...|extension, the event kind's own fields then the "
                            + "common ones. -->\n"
                            + "  " + own(named, lineMode, "xs:string") + "\n"
                            + "    <xsl:variable name=\"header\" as=\"xs:string*\" select=\"('CEF:0', " + header
                            + ")\" />\n"
                            + "    <xsl:variable name=\"pairs\" as=\"xs:string*\">" + pairs + "</xsl:variable>\n"
                            + "    <!-- One value a key: the event kind's own, where a common field gives the same "
                            + "key. -->\n"
                            + "    <xsl:variable name=\"extension\" select=\"for $i in 1 to count($pairs) return "
                            + "$pairs[$i][not(substring-before(., '=') = (for $j in 1 to $i - 1 return "
                            + "substring-before($pairs[$j], '=')))]\" />\n"
                            + "    <xsl:sequence select=\"concat(string-join($header, '|'), '|', "
                            + "string-join($extension, ' '))\" />\n"
                            + "  </xsl:template>\n";
        // Imports come first in a stylesheet.
        final String imports = new LinkedHashSet<>(plan.imports()).stream()
                .map(h -> "\n  <xsl:import href=\"" + attr(h) + "\" />")
                .collect(Collectors.joining());
        final String top = CefPlan.KAFKA.equals(plan.output())
                ? kafkaTop(plan, imports, apply(named, lineMode))
                : textTop(imports, apply(named, lineMode));
        final XmlElement sheet = StyledXslt.parse(top + line + String.join("", parts) + FUNCTIONS
                                                  + "</xsl:stylesheet>");
        // An input a template reads often, read once into a variable: a header field's source its map tests in turn.
        final Map<String, String> reads = new LinkedHashMap<>();
        for (final CefKeys.HeaderField field : CefKeys.HEADER) {
            final String source = plan.header(field.name()).source();
            if (source != null && !source.isEmpty()) {
                reads.put(source, field.name());
            }
        }
        final List<CefField> every = new ArrayList<>(plan.common());
        plan.events().values().forEach(every::addAll);
        for (final CefField f : every) {
            final CefKeys.KeyInfo info = CefKeys.KEYS.get(f.key());
            reads.put(f.path(), info != null
                    ? info.fullName()
                    : f.key());
        }
        reads.put("EventTime/TimeCreated", "event-time");
        for (final XmlElement template : sheet.childElements(MappingFacts.XSL, "template")) {
            StyledXslt.variables(template, reads, style, new HashSet<>());
        }
        return StyledXslt.serialise(sheet);
    }

    private static String kafkaTop(final CefPlan plan, final String imports, final String applyLine) {
        final String key = plan.kafkaKey() != null && !plan.kafkaKey().isEmpty()
                ? "\n      <key><xsl:value-of select=\"" + attr(plan.kafkaKey()) + "\" /></key>"
                : "";
        final String topic = attr((plan.topic() == null
                ? ""
                : plan.topic()).replace("{", "{{").replace("}", "}}"));
        return "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n"
               + "<xsl:stylesheet xpath-default-namespace=\"event-logging:3\" xmlns=\"kafka-records:1\" "
               + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
               + "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:cef=\"urn:stroom-mcp:cef\" "
               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
               + "    exclude-result-prefixes=\"xs cef\" version=\"3.0\">" + imports + "\n"
               + "  <!-- CEF for ArcSight: one Kafka record per Event (kafka-records:1 takes one a document: split one "
               + "Event a record),\n"
               + "       its value the flattened CEF line. -->\n"
               + "  <xsl:template match=\"/Events\">\n"
               + "    <kafkaRecords xsi:schemaLocation=\"kafka-records:1 file://kafka-records-v1.1.xsd\">\n"
               + "      <xsl:apply-templates select=\"Event\" />\n"
               + "    </kafkaRecords>\n"
               + "  </xsl:template>\n"
               + "  <xsl:template match=\"Event\">\n"
               + "    <kafkaRecord topic=\"" + topic + "\">\n"
               + "      <xsl:if test=\"string(EventTime/TimeCreated) castable as xs:dateTime\">\n"
               + "        <xsl:attribute name=\"timestamp\" select=\"format-dateTime(adjust-dateTime-to-timezone("
               + "xs:dateTime(string(EventTime/TimeCreated)), xs:dayTimeDuration('PT0H')), "
               + "'[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]Z')\" />\n"
               + "      </xsl:if>" + key + "\n"
               + "      <xsl:variable name=\"line\" as=\"xs:string\">" + applyLine + "</xsl:variable>\n"
               + "      <value><xsl:value-of select=\"$line\" /></value>\n"
               + "    </kafkaRecord>\n"
               + "  </xsl:template>\n";
    }

    private static String textTop(final String imports, final String applyLine) {
        return "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n"
               + "<xsl:stylesheet xpath-default-namespace=\"event-logging:3\" "
               + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
               + "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:cef=\"urn:stroom-mcp:cef\" "
               + "exclude-result-prefixes=\"xs cef\" version=\"3.0\">" + imports + "\n"
               + "  <!-- CEF for ArcSight: one flattened CEF line per Event, as text. -->\n"
               + "  <xsl:output method=\"text\" />\n"
               + "  <xsl:template match=\"/Events\">\n"
               + "    <xsl:for-each select=\"Event\">\n"
               + "      " + applyLine + "\n"
               + "      <xsl:text>&#10;</xsl:text>\n"
               + "    </xsl:for-each>\n"
               + "  </xsl:template>\n";
    }

    // The part applied: called by name, or applied in its mode.
    private static String apply(final boolean named, final String name) {
        return named
                ? "<xsl:call-template name=\"" + name + "\" />"
                : "<xsl:apply-templates select=\".\" mode=\"" + name + "\" />";
    }

    // A part's template: named, or matching an Event in its mode.
    private static String own(final boolean named, final String name, final String returns) {
        return named
                ? "<xsl:template name=\"" + name + "\" as=\"" + returns + "\">"
                : "<xsl:template match=\"Event\" mode=\"" + name + "\" as=\"" + returns + "\">";
    }

    private static String headerExpression(final CefValue value) {
        if (value.value() != null) {
            return literal(value.value());
        }
        final String source = attr(value.source() == null
                ? ""
                : value.source());
        if (value.map().isEmpty()) {
            return value.defaultValue() != null && !value.defaultValue().isEmpty()
                    ? "(" + source + ", " + literal(value.defaultValue()) + ")[normalize-space(string(.)) != ''][1]"
                    : source;
        }
        final String chain = value.map().entrySet().stream()
                .map(e -> "if (string((" + source + ")[1]) = " + literal(e.getKey()) + ") then "
                          + literal(e.getValue()))
                .collect(Collectors.joining(" else "));
        return chain + " else " + literal(value.defaultValue() == null
                ? ""
                : value.defaultValue());
    }

    private static String fieldLines(final List<CefField> fields) {
        return fields.stream().map(CefXslt::fieldLine).collect(Collectors.joining());
    }

    private static String fieldLine(final CefField f) {
        final CefKeys.KeyInfo info = CefKeys.KEYS.get(f.key());
        final int maxLength = info != null && info.maxLength() != null
                ? info.maxLength()
                : 1023;
        String expression = attr(f.path());
        if (CefField.EPOCH_MS.equals(f.transform())) {
            expression = "cef:ms(" + expression + ")";
        } else if (CefField.OUTCOME.equals(f.transform())) {
            expression = "cef:outcome(" + expression + ")";
        }
        final String label = CefKeys.SLOT_KEYS.contains(f.key()) && f.label() != null && !f.label().isEmpty()
                ? ", " + literal(f.label())
                : "";
        return "<xsl:sequence select=\"cef:kv(" + literal(f.key()) + ", " + expression + ", " + maxLength + label
               + ")\" />";
    }

    private static String keysSaid(final List<CefField> fields) {
        final String said = fields.stream().map(f -> f.key() + " from " + f.path()).collect(Collectors.joining(", "));
        return said.isEmpty()
                ? "none"
                : said;
    }

    // Text inside a double-quoted attribute.
    private static String attr(final String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // An XPath string literal, inside a double-quoted attribute.
    private static String literal(final String text) {
        return "'" + attr(text).replace("'", "''") + "'";
    }

    private static String functions() {
        try (final InputStream in = CefXslt.class.getResourceAsStream("cef-functions.xsl")) {
            if (in == null) {
                throw new IllegalStateException("cef-functions.xsl is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
