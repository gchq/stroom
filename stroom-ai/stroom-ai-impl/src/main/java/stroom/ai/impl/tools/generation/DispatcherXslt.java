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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Writes an aggregated feed's dispatcher from its [DispatcherSpec]: the one XSLT filter of the feed's pipeline,
/// importing each source type group's XSLT (and the feed's helpers) and owning every stylesheet-level declaration. It
/// reads the source type field of each record once, tries the registry's rows in order (a value exactly with `=`, a
/// pattern with XPath's `matches()`, anchored), and hands the record to its group's mode; a record of any other source
/// type (parked, dropped, another pipeline's) is passed over: not written, counted. At the end of each document it
/// logs (INFO) how many records of each source type it passed over (one another pipeline translates said as handled
/// by that pipeline, never a warning), and (WARN, once a stream) each value no row matches, a new source type for the
/// registry.
///
/// With an unsaved group's code given, that group is inlined in place of its import (its own imports hoisted among
/// the dispatcher's): a temporary dispatcher, for stepping a group before it is saved.
public final class DispatcherXslt {

    /// What a dispatcher is named after: `<feed> dispatcher`.
    public static final String SUFFIX = " dispatcher";
    /// The block of a dispatcher's description its spec is kept in.
    public static final String SPEC_BLOCK = "dispatcher";
    /// Logged (INFO) at the end of each document for each source type passed over: this, the count, then
    /// [#PASSED_OVER_OF] and the source type (or [#PASSED_OVER_NONE]).
    public static final String PASSED_OVER = "Passed over ";
    /// What follows the count in a [#PASSED_OVER] line, before the source type's name.
    public static final String PASSED_OVER_OF = " record(s) of source type ";
    /// What follows the source type in a [#PASSED_OVER] line of a source type another pipeline translates, before
    /// the pipeline (`pipeline 'PEGA-Mail-Events'`): counted as handled there, not as parked or new.
    public static final String HANDLED_BY = ", handled by ";
    /// The source type said in a [#PASSED_OVER] line of records no row matches.
    public static final String PASSED_OVER_NONE = "(none: no registry row matches)";
    /// Logged (WARN) once a stream for each value of the source type field no row matches: this, the value, then
    /// [#NEW_SOURCE_TYPE_END].
    public static final String NEW_SOURCE_TYPE = "New source type: no registry row matches ";
    /// The end of a [#NEW_SOURCE_TYPE] line.
    public static final String NEW_SOURCE_TYPE_END = ": its records are passed over (review_coverage adds it to the "
                                                     + "registry as new)";

    private static final String XSL = XmlTrees.XSL;
    private static final Pattern NCNAME = Pattern.compile("[A-Za-z_][\\w.-]*", Pattern.UNICODE_CHARACTER_CLASS);

    private DispatcherXslt() {
        // Static only.
    }

    /// @param feed A feed's name.
    /// @return The name of its dispatcher.
    public static String docName(final String feed) {
        return feed + SUFFIX;
    }

    /// @param spec    The dispatcher's spec.
    /// @param schema  The event-logging schema the Events are written in, or null for event-logging's.
    /// @param version Its version.
    /// @return The dispatcher, or the problems that stopped it.
    public static Generated generate(final DispatcherSpec spec, final EventSchema schema, final String version) {
        return generate(spec, schema, version, Map.of());
    }

    /// @param spec    The dispatcher's spec.
    /// @param schema  The event-logging schema the Events are written in, or null for what the spec says.
    /// @param version Its version, or null for what the spec says.
    /// @param inlined Unsaved groups' code by their mode: each inlined in place of its import (a temporary
    ///                dispatcher, never saved).
    /// @return The dispatcher, or the problems that stopped it.
    public static Generated generate(final DispatcherSpec spec,
                                     final EventSchema schema,
                                     final String version,
                                     final Map<String, String> inlined) {
        final List<String> problems = new ArrayList<>(problems(spec, inlined));
        final Map<String, XmlElement> drafts = new LinkedHashMap<>();
        inlined.forEach((mode, code) -> {
            try {
                drafts.put(mode, XmlParser.parse(code));
            } catch (final RuntimeException e) {
                problems.add("The code of group " + mode + " is not well-formed XML: " + e.getMessage());
            }
        });
        if (!problems.isEmpty()) {
            return new Generated(false, problems, null);
        }
        final TranslationMapping input = spec.inputMapping();
        // A temporary dispatcher, given no schema, writes the Events the saved one does.
        final String ns = schema != null
                ? schema.namespace()
                : spec.events().getOrDefault("namespace", XsltGenerator.EVT);
        final String written = version != null
                ? version
                : spec.events().getOrDefault("version", "4.1.0");
        final String location = schema == null && spec.events().containsKey("location")
                ? spec.events().get("location")
                : XsltGenerator.schemaLocation(schema, written);
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put(null, ns);
        namespaces.put("xsl", XSL);
        namespaces.put("xsi", MappingFacts.XSI);
        namespaces.put("stroom", "stroom");
        namespaces.put("xs", MappingFacts.XS);
        namespaces.put("mcp", MappingFacts.MCP_NS);
        for (final Map.Entry<String, XmlElement> draft : drafts.entrySet()) {
            draft.getValue().namespaces().forEach((prefix, uri) -> {
                if (namespaces.containsKey(prefix) && !namespaces.get(prefix).equals(uri)) {
                    problems.add("Group " + draft.getKey() + " binds " + (prefix == null
                            ? "the default namespace"
                            : "prefix " + prefix) + " to " + uri + ", where the dispatcher binds it to "
                                 + namespaces.get(prefix) + ": it can't be inlined");
                } else {
                    namespaces.putIfAbsent(prefix, uri);
                }
            });
        }
        if (!problems.isEmpty()) {
            return new Generated(false, problems, null);
        }
        final XmlElement sheet = XmlElement.create(XSL, "stylesheet", namespaces);
        sheet.set("version", "3.0");
        sheet.set("xpath-default-namespace", MappingFacts.inputNamespace(input));
        sheet.set("exclude-result-prefixes", "stroom xs mcp");
        // Imports first: the helpers, then each group (an inlined one's own imports in its place).
        final Set<String> hrefs = new LinkedHashSet<>();
        if (spec.helpers() != null && !spec.helpers().isBlank()) {
            hrefs.add(spec.helpers());
        }
        for (final DispatcherSpec.Group group : spec.groups()) {
            if (drafts.containsKey(group.mode())) {
                drafts.get(group.mode()).childElements(XSL, "import").forEach(element -> hrefs.add(element.get(
                        "href")));
            } else if (group.href() != null) {
                hrefs.add(group.href());
            }
        }
        hrefs.forEach(href -> sheet.addElement(XSL, "import", "href", href));
        sheet.addElement(MappingFacts.MCP_NS, ModuleChecks.MARKER, "kind", ModuleChecks.DISPATCHER, "name",
                spec.feed(), "mode", spec.mode());
        sheet.append(new XmlComment(XsltComments.comment("The dispatcher of feed " + spec.feed() + ": each record "
                                                         + "handed to its source type group by the first registry "
                                                         + "row its " + spec.field() + " matches; the rest passed "
                                                         + "over, counted. Generated from the feed's source type "
                                                         + "registry: regenerated, not edited")));
        final String records = XsltGenerator.recordsSelect(input);
        final String function = "mcp:" + name(spec, "source-type");
        final XmlElement root = sheet.addElement(XSL, "template", "match", XsltGenerator.rootMatch(input));
        final XmlElement events = root.addElement(ns, "Events", "Version", written);
        events.set(MappingFacts.XSI, "schemaLocation", location);
        events.addElement(XSL, "apply-templates", "select", records, "mode", spec.mode());
        events.addElement(XSL, "call-template", "name", name(spec, "passed-over"))
                .addElement(XSL, "with-param", "name", "records", "select", records);
        routing(sheet, spec, function);
        sourceTypeFunction(sheet, spec, input, function);
        passedOver(sheet, spec, input, function);
        for (final Map.Entry<String, XmlElement> draft : drafts.entrySet()) {
            inline(sheet, draft.getKey(), draft.getValue());
        }
        return new Generated(true, List.of(), XmlSerialiser.toPrettyDocument(sheet));
    }

    // What stops the dispatcher being written: a pattern the two dialects don't both read, a group's mode that isn't
    // a name, a source type no row has, or two groups taking one; an inlined group the spec doesn't import.
    private static List<String> problems(final DispatcherSpec spec, final Map<String, String> inlined) {
        final List<String> out = new ArrayList<>();
        if (spec.mode() == null || !NCNAME.matcher(spec.mode()).matches()) {
            out.add("The dispatcher's mode " + ToolText.repr(spec.mode()) + " is not an XML name");
        }
        if (spec.field() == null || spec.field().isBlank()) {
            out.add("The registry has no source type field");
        }
        final Set<String> names = new HashSet<>();
        for (final DispatcherSpec.Row row : spec.rows()) {
            names.add(row.name());
        }
        final List<SourceTypeRows.Row> rows = spec.rows().stream()
                .map(row -> new SourceTypeRows.Row(row.name(), row.match(), row.value()))
                .toList();
        // Checked against the rows' own values: what both dialects must agree on.
        final List<String> values = spec.rows().stream()
                .filter(row -> SourceTypeRows.VALUE.equals(row.match()))
                .map(DispatcherSpec.Row::value)
                .toList();
        SourceTypeRows.problems(rows, values).forEach(problem -> out.add("Registry row " + problem));
        final Map<String, String> takenBy = new LinkedHashMap<>();
        for (final DispatcherSpec.Group group : spec.groups()) {
            if (group.mode() == null || !NCNAME.matcher(group.mode()).matches()) {
                out.add("Group '" + group.name() + "': its mode " + ToolText.repr(group.mode()) + " is not an XML "
                        + "name");
            }
            if (group.href() == null && !inlined.containsKey(group.mode())) {
                out.add("Group '" + group.name() + "' has no XSLT to import: save it first, or step it as draft "
                        + "code");
            }
            for (final String type : group.sourceTypes()) {
                if (!names.contains(type)) {
                    out.add("Group '" + group.name() + "' translates source type '" + type + "', which the registry "
                            + "has no row for");
                }
                final String before = takenBy.putIfAbsent(type, group.name());
                if (before != null && !before.equals(group.name())) {
                    out.add("Source type '" + type + "' is translated by two groups, '" + before + "' and '"
                            + group.name() + "': a source type belongs to one group");
                }
            }
        }
        inlined.keySet().stream()
                .filter(mode -> spec.groups().stream().noneMatch(group -> group.mode().equals(mode)))
                .forEach(mode -> out.add("Inlined group " + mode + " is not one the dispatcher imports"));
        return out;
    }

    // The record handed to its group's mode by the source type its value first matches; any other passed over.
    private static void routing(final XmlElement sheet, final DispatcherSpec spec, final String function) {
        final XmlElement template = sheet.addElement(XSL, "template", "match", "*", "mode", spec.mode());
        template.addElement(XSL, "variable", "name", "source_type", "select", function + "(.)");
        final XmlElement choose = template.addElement(XSL, "choose");
        for (final DispatcherSpec.Group group : spec.groups()) {
            if (group.sourceTypes().isEmpty()) {
                continue;
            }
            final XmlElement when = choose.addElement(XSL, "when", "test", "$source_type = ("
                                                                         + literals(group.sourceTypes()) + ")");
            when.append(new XmlComment(" " + group.name().replace("--", "-") + " "));
            when.addElement(XSL, "apply-templates", "select", ".", "mode", group.mode());
        }
        final XmlElement otherwise = choose.addElement(XSL, "otherwise");
        otherwise.append(new XmlComment(" Out of scope (parked, dropped, new, or another pipeline's): not written, "
                                        + "counted at the end of the document "));
    }

    // The source type a record's value first matches, by the registry's rows in order; '' for none.
    private static void sourceTypeFunction(final XmlElement sheet,
                                           final DispatcherSpec spec,
                                           final TranslationMapping input,
                                           final String function) {
        final XmlElement fn = sheet.addElement(XSL, "function", "name", function, "as", "xs:string");
        fn.append(new XmlComment(XsltComments.comment("The source type a record is of: the first registry row its "
                                                      + spec.field() + " matches (= a value, ~ an anchored "
                                                      + "pattern), '' for none")));
        fn.addElement(XSL, "param", "name", "record", "as", "element()");
        fn.addElement(XSL, "variable", "name", "value", "select", "string(($record/" + fieldText(spec, input)
                                                                  + ")[1])");
        final XmlElement choose = fn.addElement(XSL, "choose");
        for (final DispatcherSpec.Row row : spec.rows()) {
            final String test = SourceTypeRows.PATTERN.equals(row.match())
                    ? "matches($value, " + XPathText.literal(row.value()) + ")"
                    : "$value = " + XPathText.literal(row.value());
            final XmlElement when = choose.addElement(XSL, "when", "test", test);
            if (row.status() != null && !row.status().isBlank()) {
                final String status = row.handledBy() == null
                        ? row.status()
                        : row.status() + ", handled by " + row.handledBy();
                when.append(new XmlComment(" " + status.replace("--", "-") + " "));
            }
            when.addElement(XSL, "sequence", "select", XPathText.literal(row.name()));
        }
        choose.addElement(XSL, "otherwise").addElement(XSL, "sequence", "select", "''");
    }

    // The counts of records passed over, by source type, logged at the end of the document (INFO); each value no row
    // matches logged once a stream (WARN), naming it.
    private static void passedOver(final XmlElement sheet,
                                   final DispatcherSpec spec,
                                   final TranslationMapping input,
                                   final String function) {
        final XmlElement template = sheet.addElement(XSL, "template", "name", name(spec, "passed-over"));
        template.addElement(XSL, "param", "name", "records", "as", "element()*");
        final Set<String> routed = new LinkedHashSet<>();
        spec.groups().forEach(group -> routed.addAll(group.sourceTypes()));
        final XmlElement each = template.addElement(XSL, "for-each-group", "select", "$records", "group-by",
                function + "(.)");
        final XmlElement holder = routed.isEmpty()
                ? each
                : each.addElement(XSL, "if", "test", "not(current-grouping-key() = (" + literals(routed) + "))");
        // Another pipeline's source types, said as handled by it, so they read neither as parked nor as new.
        final Map<String, List<String>> handled = new LinkedHashMap<>();
        spec.rows().stream()
                .filter(row -> row.handledBy() != null && !row.handledBy().isBlank() && !routed.contains(row.name()))
                .forEach(row -> handled.computeIfAbsent(row.handledBy(), ignored -> new ArrayList<>())
                        .add(row.name()));
        final XmlElement said = handled.isEmpty()
                ? holder
                : holder.addElement(XSL, "choose");
        for (final Map.Entry<String, List<String>> by : handled.entrySet()) {
            said.addElement(XSL, "when", "test", "current-grouping-key() = (" + literals(by.getValue()) + ")")
                    .addElement(XSL, "sequence", "select", passedOverLog(XPathText.literal(HANDLED_BY
                                                                                           + by.getKey())));
        }
        (handled.isEmpty()
                ? said
                : said.addElement(XSL, "otherwise")).addElement(XSL, "sequence", "select", passedOverLog(null));
        final XmlElement unmatched = template.addElement(XSL, "for-each-group", "select", "$records[" + function
                                                                                          + "(.) = '']",
                "group-by", "string((" + fieldText(spec, input) + ")[1])");
        unmatched.addElement(XSL, "variable", "name", "key", "select", "concat('mcp-new-source-type:', "
                                                                       + "current-grouping-key())");
        final XmlElement first = unmatched.addElement(XSL, "if", "test", "not(string(stroom:get($key)))");
        first.addElement(XSL, "value-of", "select", "stroom:put($key, 'y')");
        first.addElement(XSL, "sequence", "select", "stroom:log('WARN', concat(" + XPathText.literal(NEW_SOURCE_TYPE
                + spec.field() + " ") + ", if (current-grouping-key() = '') then '(no value)' else concat('''', "
                + "current-grouping-key(), ''''), " + XPathText.literal(NEW_SOURCE_TYPE_END) + "))");
    }

    // The INFO line of a source type's count passed over, with what follows the source type (a literal), if any.
    private static String passedOverLog(final String after) {
        return "stroom:log('INFO', concat(" + XPathText.literal(PASSED_OVER) + ", count(current-group()), "
               + XPathText.literal(PASSED_OVER_OF) + ", if (current-grouping-key() = '') then "
               + XPathText.literal(PASSED_OVER_NONE) + " else current-grouping-key()" + (after == null
                ? ""
                : ", " + after) + "))";
    }

    // A group's templates, functions and variables put in the dispatcher in place of its import, each keeping the
    // group's own default XPath namespace and excluded prefixes; its mark left out.
    private static void inline(final XmlElement sheet, final String mode, final XmlElement draft) {
        sheet.append(new XmlComment(XsltComments.comment("Group " + mode + ", inlined unsaved in place of its "
                                                         + "import: a temporary dispatcher, for stepping")));
        final String xpathNamespace = draft.get("xpath-default-namespace");
        final String excluded = draft.get("exclude-result-prefixes");
        for (final XmlElement child : List.copyOf(draft.childElements())) {
            if (child.is(XSL, "import") || child.is(MappingFacts.MCP_NS, ModuleChecks.MARKER)) {
                continue;
            }
            if (XSL.equals(child.namespace())) {
                if (xpathNamespace != null && child.get("xpath-default-namespace") == null) {
                    child.set("xpath-default-namespace", xpathNamespace);
                }
                if (excluded != null && child.get("exclude-result-prefixes") == null) {
                    child.set("exclude-result-prefixes", excluded);
                }
            }
            sheet.append(child);
        }
    }

    // The source type field, read from a record.
    private static String fieldText(final DispatcherSpec spec, final TranslationMapping input) {
        return XPathText.fieldText(input.input(), spec.field());
    }

    // A name of the dispatcher's own, prefixed with its mode.
    private static String name(final DispatcherSpec spec, final String name) {
        return XsltNames.styleName(spec.mode() + "-" + name, "snake_case");
    }

    private static String literals(final Iterable<String> values) {
        final List<String> out = new ArrayList<>();
        values.forEach(value -> out.add(XPathText.literal(value)));
        return out.stream().collect(Collectors.joining(", "));
    }


    // --------------------------------------------------------------------------------


    /// A dispatcher written, or why not.
    ///
    /// @param ok       True if there are no problems.
    /// @param problems What stopped it.
    /// @param xslt     The XSLT, or null.
    public record Generated(boolean ok, List<String> problems, String xslt) {

    }
}
