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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.StyledXslt;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltComments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A field plan's draft indexing XSLT, reading Events (xpath-default-namespace event-logging:3), written in the
/// Events translation's style: each part of the record (or document) a template of its own, with a comment saying
/// what it writes, and an input a template reads often read once into a variable ([StyledXslt]). Ported from the
/// Stroom MCP server's `FieldPlan.xslt`; it writes the same text.
final class IndexingXslt {

    /// A plain path: steps of names, a predicate kept with its step.
    static final Pattern PLAIN = Pattern.compile("^[\\w@*.:-]+(\\[[^\\]]*\\])?(/[\\w@*.:-]+(\\[[^\\]]*\\])?)*$",
            Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNIX_LINES);

    private static final String XSL = MappingFacts.XSL;
    private static final Pattern BRACED = Pattern.compile("\\{([^{}]+)\\}");
    private static final Pattern OR = Pattern.compile("\\s+or\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private IndexingXslt() {
        // Static only.
    }

    /// @param path A path.
    /// @return True if it is a plain path (`re.match` of [#PLAIN]).
    static boolean plain(final String path) {
        return PLAIN.matcher(path).lookingAt();
    }

    /// @param plan    An Events plan (not a discovery one).
    /// @param version The XSLT version.
    /// @return The indexing XSLT.
    static String xslt(final FieldPlan plan, final String version) {
        final Set<String> taken = new HashSet<>();
        final List<Part> parts;
        final List<String> shapes;
        final String head;
        final String record;
        final String close;
        final String open;
        final String shut;
        if (FieldPlan.LUCENE.equals(plan.backend())) {
            parts = luceneParts(plan);
            shapes = List.of();
            head = "xmlns=\"records:2\"";
            record = "<records xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" version=\"2.0\">";
            close = "</records>";
            open = "<record>";
            shut = "</record>";
        } else {
            final ElasticDocument document = new ElasticDocument(plan, taken);
            parts = document.parts();
            shapes = document.templates();
            head = "xmlns=\"http://www.w3.org/2005/xpath-functions\"";
            record = "<array xsi:schemaLocation=\"http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd\">";
            close = "</array>";
            open = "<map>";
            shut = "</map>";
        }
        final String layout = plan.style().layout();
        final List<String> event = new ArrayList<>();
        final List<String> own = new ArrayList<>();
        for (final Part part : parts) {
            if (!part.separate() || "inline".equals(layout)) {
                event.addAll(part.lines());
                continue;
            }
            final String name = StyledXslt.partName(part.key(), plan.style(), taken);
            event.add("modes".equals(layout)
                    ? "      <xsl:apply-templates select=\".\" mode=\"" + name + "\" />"
                    : "      <xsl:call-template name=\"" + name + "\" />");
            final List<PlannedField> fields = plan.isElasticsearch()
                    ? plan.fields().stream()
                            .filter(f -> f.name().equals(part.key()) || f.name().startsWith(part.key() + "."))
                            .toList()
                    : plan.fields().stream()
                            .filter(f -> FieldPlans.steps(f.source()).getFirst().equals(part.key()))
                            .toList();
            final String said = String.join("; ", fields.stream().map(f -> f.name() + " from " + f.source()).toList());
            own.add("  <!--" + XsltComments.comment(name + ": " + said) + "-->\n"
                    + ("modes".equals(layout)
                    ? "  <xsl:template match=\"Event\" mode=\"" + name + "\">\n"
                    : "  <xsl:template name=\"" + name + "\">\n")
                    + String.join("\n", part.lines()) + "\n  </xsl:template>");
        }
        final List<String> after = new ArrayList<>(own);
        after.addAll(shapes);
        final String text = "<xsl:stylesheet xpath-default-namespace=\"event-logging:3\" " + head + "\n"
                            + "    xmlns:stroom=\"stroom\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"" + version + "\">\n"
                            + plan.importLines() + "  <xsl:template match=\"/Events\">\n"
                            + "    " + record + "\n"
                            + "      <xsl:apply-templates select=\"" + plan.events() + "\" />\n"
                            + "    " + close + "\n"
                            + "  </xsl:template>\n"
                            + "  <!--" + XsltComments.comment("Each Event: one " + (plan.isElasticsearch()
                                    ? "document"
                                    : "record") + " for the index " + plan.indexName()) + "-->\n"
                            + "  <xsl:template match=\"Event\">\n"
                            + "    " + open + "\n"
                            + String.join("\n", event) + "\n"
                            + "    " + shut + "\n"
                            + "  </xsl:template>\n"
                            + String.join("\n", after) + "\n"
                            + "</xsl:stylesheet>";
        final XmlElement sheet = StyledXslt.parse(text);
        // Read once into a variable where a template reads an input often (its guard, its value, its object's
        // guard).
        final Map<String, String> names = new LinkedHashMap<>();
        plan.fields().forEach(field -> names.put(field.source(), field.name()));
        for (final XmlElement template : sheet.childElements(XSL, "template")) {
            StyledXslt.variables(template, reads(template, names), plan.style(), new HashSet<>());
        }
        return StyledXslt.serialise(sheet);
    }

    // Grouped as the Events are: what each part of the Event (EventTime, EventSource, EventDetail) gives.
    private static List<Part> luceneParts(final FieldPlan plan) {
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        for (final PlannedField field : plan.fields()) {
            if (plan.writtenBy(field.name()) != null) {
                continue;
            }
            final String first = FieldPlans.steps(field.source()).getFirst();
            // One data element per value: a field of several (repeated Data elements, say) is multi-valued in
            // Lucene, not one value of them joined by spaces (seen: two ports indexed as "80 443").
            final String line = field.multiple()
                    ? "      <xsl:for-each select=\"" + FieldPlans.quoted(field.source()) + "\"><data name=\""
                      + field.name() + "\" value=\"{" + FieldPlans.quoted(field.value(".")) + "}\" /></xsl:for-each>"
                    : "      <data name=\"" + field.name() + "\" value=\"{" + FieldPlans.quoted(field.value(null))
                      + "}\" />";
            groups.computeIfAbsent(first.startsWith("@")
                    ? ""
                    : first, key -> new ArrayList<>()).add(line);
        }
        final List<Part> parts = new ArrayList<>();
        groups.forEach((key, lines) -> parts.add(new Part(key, !key.isEmpty(), lines)));
        parts.add(new Part("", false, plan.shared().stream().map(use -> call(use, "      ")).toList()));
        return parts;
    }

    /// @param use    A shared XSLT's named template.
    /// @param indent The line's indent.
    /// @return A call to it, with the parameters the environment passes it.
    static String call(final SharedTemplate use, final String indent) {
        if (use.withParams().isEmpty()) {
            return indent + "<xsl:call-template name=\"" + use.template() + "\" />";
        }
        final StringBuilder params = new StringBuilder();
        use.withParams().forEach((name, select) -> params.append("<xsl:with-param name=\"").append(name)
                .append("\" select=\"").append(FieldPlans.quoted(select)).append("\" />"));
        return indent + "<xsl:call-template name=\"" + use.template() + "\">" + params + "</xsl:call-template>";
    }

    // The inputs a template reads (a test's or select's paths, an attribute's {...}), each with the name a
    // variable holding it takes: the field reading it (a path relative to a template's element, the field whose
    // source ends with it), else its last step.
    private static Map<String, String> reads(final XmlElement template, final Map<String, String> names) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final XmlElement element : template.iterElements()) {
            final List<String> values = new ArrayList<>();
            for (final XmlElement.Attribute attribute : element.attributes()) {
                if (XSL.equals(element.namespace())) {
                    if ("select".equals(attribute.key()) || "test".equals(attribute.key())) {
                        values.add(attribute.value());
                    }
                } else {
                    final Matcher matcher = BRACED.matcher(attribute.value());
                    while (matcher.find()) {
                        values.add(matcher.group(1));
                    }
                }
            }
            for (final String value : values) {
                for (final String given : OR.split(value.split(" ! ", -1)[0], -1)) {
                    final String piece = PyValues.strip(given);
                    if (".".equals(piece) || piece.isEmpty() || !plain(piece)) {
                        continue;
                    }
                    String name = names.get(piece);
                    if (!PyValues.truthy(name)) {
                        name = names.entrySet().stream()
                                .filter(entry -> entry.getKey().endsWith("/" + piece))
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .orElse(null);
                    }
                    if (!out.containsKey(piece)) {
                        out.put(piece, PyValues.truthy(name)
                                ? name
                                : FieldPlans.steps(piece).getLast());
                    }
                }
            }
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// One part of the record or document: what it is named after, whether it is a part of its own (a template),
    /// and its lines.
    ///
    /// @param key      The top-level field or object it writes (Lucene: the Event element its fields read).
    /// @param separate True if it is written by a template of its own (unless the layout is inline).
    /// @param lines    Its lines.
    record Part(String key, boolean separate, List<String> lines) {

    }
}
