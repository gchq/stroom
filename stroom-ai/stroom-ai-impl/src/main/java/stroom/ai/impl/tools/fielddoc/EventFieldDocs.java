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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.generation.DropRule;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.PythonRepr;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlComment;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlNode;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// The Field mapping section of an events pipeline's documentation, from its translation mapping: both halves of
/// each field mapping, where a value comes from (a field, a constant, a lookup, a transform) and what the sample's
/// events got. Events are matched to the rule that wrote them by a marker the generator puts in each Event for the
/// documentation run (never in saved XSLT), so the per-rule rows and counts are exact; events no rule of the
/// mapping wrote are reported rather than dropped. Ported from the Stroom MCP server's `field_mapping_markdown` in
/// `utils/fielddoc.py`.
public final class EventFieldDocs {

    private static final List<String> ALL_SECTIONS = List.of("EventTime", "EventSource", "EventDetail");
    private static final List<String> SOURCE_SECTIONS = List.of("EventTime", "EventSource");
    private static final List<String> DETAIL = List.of("EventDetail");
    private static final Set<String> TYPE_AND_DESCRIPTION = Set.of("EventDetail/TypeId", "EventDetail/Description");
    private static final String NOT_MAPPED = "not in the mapping (added by hand, or by a later step)";
    private static final Comparator<FieldKey> BY_PATH_AND_NAME = Comparator.comparing(FieldKey::path)
            .thenComparing(k -> Objects.requireNonNullElse(k.name(), ""));

    private EventFieldDocs() {
        // Static only.
    }

    /// `event_values`: an event's values in the given top-level sections, keyed as the mapping keys them (path
    /// and no name for an element, path to Data and its Name for a Data), every value of a repeated element in
    /// order.
    ///
    /// @param event    An Event element.
    /// @param sections The top-level sections, e.g. `EventSource`.
    /// @return The values by key.
    public static Map<FieldKey, List<String>> eventValues(final XmlElement event, final List<String> sections) {
        final Map<FieldKey, List<String>> found = new LinkedHashMap<>();
        for (final XmlElement section : event.childElements()) {
            if (sections.contains(section.localName())) {
                for (final XmlElement child : section.childElements()) {
                    walk(child, section.localName(), found);
                }
            }
        }
        return found;
    }

    /// `rule_of`: the rule the generator marked the event with for the documentation run.
    ///
    /// @param event An Event element.
    /// @return The rule's name, or null if the event has no marker.
    public static String ruleOf(final XmlElement event) {
        for (final XmlNode node : event.children()) {
            if (node instanceof final XmlComment comment) {
                final String text = PythonStrings.strip(comment.text());
                if (text.startsWith(XsltGenerator.RULE_MARK)) {
                    return PythonStrings.strip(text.substring(XsltGenerator.RULE_MARK.length()));
                }
            }
        }
        return null;
    }

    /// `sampled_events`: the Event elements in translation outputs (one per record when stepping).
    ///
    /// @param outputs The outputs; one that doesn't parse is passed over.
    /// @return Their events, in order.
    public static List<XmlElement> sampledEvents(final List<String> outputs) {
        final List<XmlElement> events = new ArrayList<>();
        for (final String xml : outputs) {
            final XmlElement root;
            try {
                root = XmlParser.parse(xml);
            } catch (final XmlParser.XmlParseException e) {
                continue;
            }
            root.iterElements().stream().filter(e -> "Event".equals(e.localName())).forEach(events::add);
        }
        return events;
    }

    /// [#fieldMappingMarkdown(TranslationMapping, EventSchema, List, String, Descriptions)] by event type, with no
    /// sample and no descriptions.
    ///
    /// @param mapping The translation mapping.
    /// @param schema  The event-logging schema.
    /// @return The section.
    public static String fieldMappingMarkdown(final TranslationMapping mapping, final EventSchema schema) {
        return fieldMappingMarkdown(mapping, schema, null, FieldDocs.BY_EVENT_TYPE, null);
    }

    /// `field_mapping_markdown`: the section: how the mapping reads records (items, drops, extractions), then, by
    /// event type (the default, asked for by the user): a table of the fields common to every event type, and a
    /// section per event type with a table of the fields particular to it (XPath, description, From, and with a
    /// sample the values written). Compact: the EventSource table and an event types table, one row per rule with
    /// every EventDetail element in a cell.
    ///
    /// @param mapping   The translation mapping.
    /// @param schema    The event-logging schema.
    /// @param observed  The sample's events ([#sampledEvents(List)]), or null for no sample.
    /// @param layout    [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described The field descriptions, or null for none.
    /// @return The section's text, ending with a new line.
    public static String fieldMappingMarkdown(final TranslationMapping mapping,
                                              final EventSchema schema,
                                              final List<XmlElement> observed,
                                              final String layout,
                                              final Descriptions described) {
        final Comparator<FieldMapping> order = order(schema);
        final List<EventRule> rules = mapping.events().stream().filter(r -> !r.drop()).toList();
        final Map<String, Map<FieldKey, FieldMapping>> effective = new LinkedHashMap<>();
        for (final EventRule rule : rules) {
            final Map<FieldKey, FieldMapping> fields = new LinkedHashMap<>();
            mapping.common().forEach(e -> fields.put(FieldKey.of(e), e));
            rule.fields().forEach(e -> fields.put(FieldKey.of(e), e));
            effective.put(rule.name(), fields);
        }

        // Attribution: the generator's marker, else the older guess; events no rule wrote are counted apart.
        final Map<String, List<XmlElement>> byRule = new LinkedHashMap<>();
        rules.forEach(r -> byRule.put(r.name(), new ArrayList<>()));
        final List<XmlElement> unexplained = new ArrayList<>();
        for (final XmlElement event : Objects.requireNonNullElse(observed, List.<XmlElement>of())) {
            String name = ruleOf(event);
            if (name == null) {
                name = attributeUnmarked(rules, effective, event);
            }
            if (name != null && byRule.containsKey(name)) {
                byRule.get(name).add(event);
            } else {
                unexplained.add(event);
            }
        }
        final Sample sample = new Sample(observed != null, observed == null
                ? 0
                : observed.size(), byRule, unexplained);

        final List<String> lines = new ArrayList<>(preamble(mapping, sample));
        final Descriptions descriptions = described == null
                ? new Descriptions()
                : described;
        final Context context = new Context(mapping, schema, rules, effective, sample, order, descriptions);
        if (!FieldDocs.COMPACT.equals(layout)) {
            lines.addAll(byEventType(context));
            return String.join("\n", lines) + "\n";
        }
        lines.addAll(compactSources(context));
        lines.addAll(compactEventTypes(context));
        return String.join("\n", lines) + "\n";
    }

    // How the mapping reads records: items, kept as Unknown, drops, shared XSLTs, extractions.
    private static List<String> preamble(final TranslationMapping mapping, final Sample sample) {
        final List<String> lines = new ArrayList<>();
        if (PythonStrings.truthy(mapping.forEach())) {
            lines.add("Each record holds several events: one per `" + mapping.forEach() + "` item. Sources marked "
                      + "\"of the record\" read the record round the items.");
            lines.add("");
        }
        final List<EventRule> kept = MappingFacts.keptUnknown(mapping);
        if (!kept.isEmpty()) {
            lines.add("### Kept as Unknown");
            lines.add("");
            for (final EventRule rule : kept) {
                final String conditions = FieldDocs.conditionsText(rule.when());
                // A catch-all the sample never reaches is a safety net, not events kept Unknown (seen: read as a
                // decision about the sample, beside "0 of 1000" in the event types).
                lines.add("- `" + rule.name() + "` (" + (conditions.isEmpty()
                        ? "records no other rule matches"
                        : conditions) + "): " + rule.allowUnknown()
                          + (sample.sampled() && sample.eventsOf(rule.name()).isEmpty()
                        ? " None of the " + sample.total() + " sampled records reach it: it catches records the "
                          + "sample doesn't have."
                        : ""));
            }
            lines.add("");
        }
        if (!mapping.dropWhen().isEmpty()) {
            lines.add("### Left untranslated");
            lines.add("");
            for (final DropRule drop : mapping.dropWhen()) {
                lines.add("- " + FieldDocs.conditionsText(drop.when()) + ": " + drop.reason());
            }
            lines.add("");
        }
        if (!mapping.shared().isEmpty()) {
            lines.add("### Written by shared XSLTs");
            lines.add("");
            for (final SharedTemplate use : mapping.shared()) {
                lines.add("- `" + use.at() + "`: the named template `" + use.template() + "` of `" + use.href()
                          + "`" + (use.withParams().isEmpty()
                        ? ""
                        : ", given " + use.withParams().entrySet().stream()
                                .map(e -> "`" + e.getKey() + "` = `" + FieldDocs.readable(e.getValue()) + "`")
                                .collect(Collectors.joining(", "))));
            }
            lines.add("");
        }
        if (!mapping.extract().isEmpty()) {
            lines.add("### Extracted fields");
            lines.add("");
            for (final Extraction ex : mapping.extract()) {
                final String names = ex.names().stream()
                        .filter(PythonStrings::truthy)
                        .map(n -> "`" + n + "`")
                        .collect(Collectors.joining(", "));
                lines.add("- " + names + ": parsed from `" + (PythonStrings.truthy(ex.field())
                        ? ex.field()
                        : FieldDocs.readable(ex.xpath())) + "`" + ("record".equals(ex.scope())
                        ? " (of the record)"
                        : "") + " with `" + ex.regex() + "`");
            }
            lines.add("");
        }
        return lines;
    }

    // The fields common to every event type in one table, then a section per event type with a table of its own
    // fields. Common: an EventTime or EventSource element every rule writes from the same source; EventDetail is
    // always the event type's own.
    private static List<String> byEventType(final Context c) {
        final Sample sample = c.sample();
        final List<String> head = new ArrayList<>(List.of("XPath", "Description", "From"));
        if (sample.sampled()) {
            head.add("Sample values");
        }
        final List<FieldKey> common = new ArrayList<>();
        if (!c.rules().isEmpty()) {
            final Map<FieldKey, FieldMapping> first = c.effective().get(c.rules().getFirst().name());
            first.forEach((k, entry) -> {
                if (!"EventDetail".equals(PythonStrings.split(k.path(), "/").getFirst()) && c.rules().stream()
                        .allMatch(r -> {
                            final FieldMapping theirs = c.effective().get(r.name()).get(k);
                            return theirs != null && FieldDocs.sourceText(theirs).equals(FieldDocs.sourceText(
                                    entry));
                        })) {
                    common.add(k);
                }
            });
            common.sort(Comparator.comparing(first::get, c.order()));
        }
        final List<XmlElement> every = sample.every();

        final List<String> lines = new ArrayList<>(List.of("### Common fields", "", sample.sampled()
                ? "Every event type writes these, from the same source. Sample values are what the sample's events "
                  + "were written, " + sample.total() + " events in all."
                : "Every event type writes these, from the same source.", ""));
        lines.addAll(FieldDocs.head(head));
        for (final FieldKey k : common) {
            lines.add(row(c, k, c.effective().get(c.rules().getFirst().name()).get(k), every));
        }
        final Set<FieldKey> mapped = new HashSet<>();
        c.effective().values().forEach(fields -> mapped.addAll(fields.keySet()));
        if (sample.sampled()) {
            // In the events, but no rule of the mapping writes it.
            final Set<FieldKey> extra = new TreeSet<>(BY_PATH_AND_NAME);
            every.forEach(e -> eventValues(e, SOURCE_SECTIONS).keySet().stream()
                    .filter(k -> !mapped.contains(k))
                    .forEach(extra::add));
            extra.forEach(k -> lines.add(row(c, k, null, every)));
        }
        lines.addAll(List.of("", "### Event types", ""));
        final List<EventRule> dropped = c.mapping().events().stream().filter(EventRule::drop).toList();
        if (!dropped.isEmpty()) {
            lines.add("Left untranslated on purpose: " + dropped.stream().map(r -> {
                final String conditions = FieldDocs.conditionsText(r.when());
                return "`" + r.name() + "` (" + (conditions.isEmpty()
                        ? "every record"
                        : conditions) + ")";
            }).collect(Collectors.joining("; ")) + ".");
            lines.add("");
        }
        final Map<String, List<FieldKey>> own = new LinkedHashMap<>();
        for (final EventRule rule : c.rules()) {
            final Map<FieldKey, FieldMapping> fields = c.effective().get(rule.name());
            final List<FieldKey> keys = new ArrayList<>(fields.keySet().stream()
                    .filter(k -> !common.contains(k))
                    .toList());
            keys.sort(Comparator.<FieldKey, Boolean>comparing(k -> !TYPE_AND_DESCRIPTION.contains(k.path()))
                    .thenComparing(fields::get, c.order()));
            own.put(rule.name(), keys);
        }
        // Judged on what they write besides TypeId and Description, which every event type has.
        final Map<String, Set<String>> signatures = new LinkedHashMap<>();
        own.forEach((name, keys) -> signatures.put(name, keys.stream()
                .filter(k -> !TYPE_AND_DESCRIPTION.contains(k.path()))
                .map(k -> FieldDocs.anyActionPath(k.xpath()))
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        for (final List<String> names : FieldDocs.similarGroups(signatures)) {
            lines.addAll(eventTypeSection(c, names, own, head));
        }
        if (!sample.unexplained().isEmpty()) {
            lines.add(sample.unexplained().size() + " of the sample's " + sample.total() + " events were not written "
                      + "by a rule of this mapping (the XSLT was edited by hand, or a later step rewrote them): "
                      + unknownKinds(sample.unexplained()) + ".");
            lines.add("");
        }
        return lines;
    }

    // One heading for event types with roughly the same fields: when each applies, and a row per field.
    private static List<String> eventTypeSection(final Context c,
                                                 final List<String> names,
                                                 final Map<String, List<FieldKey>> own,
                                                 final List<String> head) {
        final Sample sample = c.sample();
        final List<String> said = new ArrayList<>();
        for (final String name : names) {
            final EventRule rule = c.rules().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
            final String conditions = FieldDocs.conditionsText(rule.when());
            String line = (!conditions.isEmpty()
                    ? "When " + conditions
                    : c.rules().size() > 1
                            ? "Any other record"
                            : "Every record") + (names.size() > 1
                    ? " (" + name + ")"
                    : "");
            if (sample.sampled()) {
                final List<XmlElement> events = sample.eventsOf(name);
                line += events.isEmpty()
                        ? ": none in the sample"
                        : ": " + events.size() + " of the sample's " + sample.total() + " events";
            }
            said.add(line + ".");
        }
        final List<String> lines = new ArrayList<>(List.of("#### " + String.join(", ", names), "",
                String.join(" ", said), ""));
        lines.addAll(FieldDocs.head(head));
        // One row per field, the groups' own paths and sources together.
        final Map<String, List<Member>> rows = new LinkedHashMap<>();
        for (final String name : names) {
            final Map<FieldKey, FieldMapping> fields = c.effective().get(name);
            for (final FieldKey k : own.get(name)) {
                rows.computeIfAbsent(FieldDocs.anyActionPath(k.xpath()), x -> new ArrayList<>())
                        .add(new Member(name, k, fields.get(k)));
            }
            if (sample.sampled() && !sample.eventsOf(name).isEmpty()) {
                final Set<FieldKey> extra = new TreeSet<>(BY_PATH_AND_NAME);
                sample.eventsOf(name).forEach(e -> eventValues(e, DETAIL).keySet().stream()
                        .filter(k -> !fields.containsKey(k))
                        .forEach(extra::add));
                for (final FieldKey k : extra) {
                    rows.computeIfAbsent(FieldDocs.anyActionPath(k.xpath()), x -> new ArrayList<>())
                            .add(new Member(name, k, null));
                }
            }
        }
        for (final List<Member> members : rows.values()) {
            if (names.size() == 1) {
                final Member member = members.getFirst();
                lines.add(row(c, member.key(), member.entry(), sample.eventsOf(member.name())));
            } else {
                lines.add(mergedRow(c, members, names.size()));
            }
        }
        lines.add("");
        return lines;
    }

    // A field's row: its path, description, source, and with a sample the values written.
    private static String row(final Context c, final FieldKey key, final FieldMapping entry,
                              final List<XmlElement> events) {
        final String source = entry != null
                ? FieldDocs.sourceText(entry)
                : NOT_MAPPED;
        final Values values = valuesOf(events, key);
        final List<String> cells = new ArrayList<>(List.of("`" + key.xpath() + "`", c.described().of(key.xpath(),
                source, values.values(), Descriptions.eventLogging(described(c.schema(), key)), key.xpath(),
                readFrom(entry), List.of(), null), source));
        if (c.sample().sampled()) {
            cells.add(orNotSampled(FieldDocs.shown(values.values(), true)) + (values.several()
                    ? "\n(several per event)"
                    : ""));
        }
        return FieldDocs.row(cells);
    }

    // A field of event types shown together: its paths and sources together, each source named by its type where
    // they differ or not every type writes it, and the values all of them got.
    private static String mergedRow(final Context c, final List<Member> members, final int grouped) {
        final FieldKey first = members.getFirst().key();
        final List<String[]> sources = members.stream()
                .map(m -> new String[]{m.name(), m.entry() != null
                        ? FieldDocs.sourceText(m.entry())
                        : "not in the mapping"})
                .toList();
        final boolean same = sources.stream().map(s -> s[1]).distinct().count() == 1 && members.size() == grouped;
        final String source = same
                ? sources.getFirst()[1]
                : sources.stream().map(s -> s[1] + " (" + s[0] + ")").collect(Collectors.joining("\n"));
        final List<String> found = new ArrayList<>();
        boolean several = false;
        for (final Member member : members) {
            final Values got = valuesOf(c.sample().eventsOf(member.name()), member.key());
            found.addAll(got.values());
            several = several || got.several();
        }
        final List<String> values = new ArrayList<>(new LinkedHashSet<>(found));
        final List<String> paths = members.stream().map(m -> m.key().xpath()).toList();
        final String shown = FieldDocs.combinedPath(paths);
        final List<String> names = members.stream().flatMap(m -> readFrom(m.entry()).stream()).toList();
        // Asked by the row's own path, not its first type's: seen, Process/Action's values asked as Authenticate/
        // Action's, and Create's and Delete's Object as View/Object.
        final List<String> cells = new ArrayList<>(List.of(("`" + shown + "`").replace("\n", "`\n`"),
                c.described().of(shown.replace("\n", " | "), source, values,
                        Descriptions.eventLogging(described(c.schema(), first)), first.xpath(), names,
                        new ArrayList<>(new LinkedHashSet<>(paths)), sources.stream().map(s -> s[1]).toList()),
                source));
        if (c.sample().sampled()) {
            cells.add(orNotSampled(FieldDocs.shown(values, true)) + (several
                    ? "\n(several per event)"
                    : ""));
        }
        return FieldDocs.row(cells);
    }

    // The EventSource and EventTime table, compact.
    private static List<String> compactSources(final Context c) {
        final Sample sample = c.sample();
        // Element -> from text -> rules that write it so.
        final Map<FieldKey, Map<String, List<String>>> sources = new LinkedHashMap<>();
        final Map<FieldKey, FieldMapping> entries = new LinkedHashMap<>();
        for (final EventRule rule : c.rules()) {
            c.effective().get(rule.name()).forEach((k, entry) -> {
                final String section = PythonStrings.split(k.path(), "/").getFirst();
                if ("EventSource".equals(section) || "EventTime".equals(section)) {
                    sources.computeIfAbsent(k, x -> new LinkedHashMap<>())
                            .computeIfAbsent(FieldDocs.sourceText(entry), x -> new ArrayList<>())
                            .add(rule.name());
                    entries.putIfAbsent(k, entry);
                }
            });
        }
        // Element -> value -> rules whose events had it.
        final Map<FieldKey, Map<String, List<String>>> seen = new LinkedHashMap<>();
        final Set<FieldKey> multi = new HashSet<>();
        sample.byRule().forEach((rule, events) -> {
            for (final XmlElement event : events) {
                eventValues(event, SOURCE_SECTIONS).forEach((k, values) -> {
                    if (values.size() > 1) {
                        multi.add(k);
                    }
                    for (final String value : values) {
                        final List<String> kinds = seen.computeIfAbsent(k, x -> new LinkedHashMap<>())
                                .computeIfAbsent(value, x -> new ArrayList<>());
                        if (!kinds.contains(rule)) {
                            kinds.add(rule);
                        }
                    }
                });
            }
        });
        final List<String> rulesSampled = sample.byRule().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        final List<String> lines = new ArrayList<>(List.of("### EventSource", "", sample.sampled()
                ? "Every event carries these. From is the mapping; Sample values are what the sample's events were "
                  + "written, " + sample.total() + " events in all."
                : "Every event carries these; From is the mapping.", ""));
        lines.addAll(sample.sampled()
                ? List.of("| XPath | Description | From | Sample values |", "| --- | --- | --- | --- |")
                : List.of("| XPath | Description | From |", "| --- | --- | --- |"));
        final List<FieldKey> keys = new ArrayList<>(sources.keySet());
        keys.sort(Comparator.comparing(entries::get, c.order()));
        for (final FieldKey k : keys) {
            final Map<String, List<String>> froms = sources.get(k);
            final String fromText = froms.size() == 1 && froms.values().iterator().next().size() == c.rules().size()
                    ? froms.keySet().iterator().next()
                    : froms.entrySet().stream()
                            .map(e -> e.getKey() + " (" + String.join(", ", e.getValue()) + ")")
                            .collect(Collectors.joining("\n"));
            String description;
            try {
                description = c.schema().describe(c.schema().resolve(k.path()));
            } catch (final RuntimeException e) {
                description = "";
            }
            final String xpath = xpath(entries.get(k), "");
            final Map<String, List<String>> values = Objects.requireNonNullElse(seen.get(k), Map.of());
            description = c.described().of(xpath, fromText, new ArrayList<>(values.keySet()),
                    Descriptions.eventLogging(description), xpath, readFrom(entries.get(k)), List.of(), null);
            if (!sample.sampled()) {
                lines.add(FieldDocs.row("`" + xpath + "`", description, fromText));
                continue;
            }
            final List<String> kinds = new ArrayList<>(new LinkedHashSet<>(values.values().stream()
                    .flatMap(List::stream)
                    .toList()));
            String shown = orNotSampled(FieldDocs.shown(new ArrayList<>(values.keySet()), true));
            if (!values.isEmpty() && kinds.size() < rulesSampled.size()) {
                shown += "\n(" + String.join(", ", kinds) + " events only)";
            }
            if (multi.contains(k)) {
                shown += "\n(several per event)";
            }
            lines.add(FieldDocs.row("`" + xpath + "`", description, fromText, shown));
        }
        final Set<FieldKey> unmapped = new TreeSet<>(Comparator.comparing(FieldKey::path)
                .thenComparing(k -> k.name() == null
                        ? ""
                        : "\u0001" + k.name()));
        seen.keySet().stream().filter(k -> !sources.containsKey(k)).forEach(unmapped::add);
        for (final FieldKey k : unmapped) {
            lines.add(FieldDocs.row("`" + k.path() + (k.name() != null && !k.name().isEmpty()
                            ? "[@Name=" + PythonRepr.repr(k.name()) + "]/@Value"
                            : "") + "`", "", NOT_MAPPED,
                    FieldDocs.shown(new ArrayList<>(seen.get(k).keySet()), true)));
        }
        return lines;
    }

    // The event types table, compact: one row per rule.
    private static List<String> compactEventTypes(final Context c) {
        final Sample sample = c.sample();
        final List<String> lines = new ArrayList<>(List.of("", "### Event types", "", sample.sampled()
                ? "One row per rule: its conditions, what it writes, how many of the sample's events it wrote, and "
                  + "each EventDetail element as path <- from = a sampled value."
                : "One row per rule: its conditions and what it writes, each EventDetail element as path <- from.",
                ""));
        lines.addAll(sample.sampled()
                ? List.of("| Rule | TypeId | Description | Events | EventDetail |", "| --- | --- | --- | --- | --- |")
                : List.of("| Rule | TypeId | Description | EventDetail |", "| --- | --- | --- | --- |"));
        for (final EventRule rule : c.mapping().events()) {
            if (rule.drop()) {
                final String conditions = FieldDocs.conditionsText(rule.when());
                final List<String> cells = new ArrayList<>(List.of("**" + rule.name() + "**\n" + (conditions.isEmpty()
                        ? "every record"
                        : conditions), "", "Left untranslated on purpose"));
                if (sample.sampled()) {
                    cells.add("0");
                }
                cells.add("");
                lines.add(FieldDocs.row(cells));
                continue;
            }
            lines.add(compactRule(c, rule));
        }
        if (!sample.unexplained().isEmpty()) {
            lines.add("");
            lines.add(sample.unexplained().size() + " of the sample's " + sample.total() + " events were not written "
                      + "by a rule of this mapping (the XSLT was edited by hand, or a later step rewrote them): "
                      + unknownKinds(sample.unexplained()) + ".");
        }
        return lines;
    }

    // One rule's row of the compact event types table.
    private static String compactRule(final Context c, final EventRule rule) {
        final Sample sample = c.sample();
        final boolean others = c.rules().stream().anyMatch(r -> r != rule);
        final String conditions = FieldDocs.conditionsText(rule.when());
        final String when = !conditions.isEmpty()
                ? conditions
                : others
                        ? "any other record"
                        : "every record";
        final Map<FieldKey, FieldMapping> fields = c.effective().get(rule.name());
        final List<XmlElement> events = sample.eventsOf(rule.name());
        final Map<FieldKey, List<String>> first = events.isEmpty()
                ? Map.of()
                : eventValues(events.getFirst(), DETAIL);
        final List<FieldMapping> detail = new ArrayList<>(fields.entrySet().stream()
                .filter(e -> e.getKey().path().startsWith("EventDetail/")
                             && !TYPE_AND_DESCRIPTION.contains(e.getKey().path()))
                .map(Map.Entry::getValue)
                .toList());
        detail.sort(c.order());
        final List<String> detailLines = new ArrayList<>();
        for (final FieldMapping e : detail) {
            final FieldKey key = FieldKey.of(e);
            String line = "`" + xpath(e, "EventDetail/") + "` <- " + FieldDocs.sourceText(e);
            if (sample.sampled() && !events.isEmpty()) {
                final List<String> values = first.getOrDefault(key, List.of());
                line += values.isEmpty()
                        ? " = (empty)"
                        : " = " + values.subList(0, Math.min(values.size(), FieldDocs.VALUES_SHOWN)).stream()
                                .map(v -> "\"" + v + "\"")
                                .collect(Collectors.joining(", "));
            }
            detailLines.add(line);
        }
        final List<String> cells = new ArrayList<>(List.of("**" + rule.name() + "**\n" + when,
                fromAndValues(c, fields.get(new FieldKey("EventDetail/TypeId", null)),
                        new FieldKey("EventDetail/TypeId", null), events),
                fromAndValues(c, fields.get(new FieldKey("EventDetail/Description", null)),
                        new FieldKey("EventDetail/Description", null), events)));
        if (sample.sampled()) {
            cells.add(events.isEmpty()
                    ? "0 of " + sample.total() + "\n(not in the sample)"
                    : events.size() + " of " + sample.total());
        }
        cells.add(String.join("\n", detailLines));
        return FieldDocs.row(cells);
    }

    // An element's source, and the values its events got (a constant needs no sample).
    private static String fromAndValues(final Context c, final FieldMapping entry, final FieldKey key,
                                        final List<XmlElement> events) {
        String text = entry != null
                ? FieldDocs.sourceText(entry)
                : "";
        if (c.sample().sampled() && !events.isEmpty() && !(entry != null && entry.value() != null)) {
            final List<String> values = new ArrayList<>(new LinkedHashSet<>(events.stream()
                    .flatMap(e -> eventValues(e, DETAIL).getOrDefault(key, List.of()).stream())
                    .toList()));
            text += values.isEmpty()
                    ? ""
                    : (text.isEmpty()
                            ? ""
                            : "\n") + FieldDocs.shown(values, true);
        }
        return text;
    }

    // For an event without a marker (an older XSLT): the first rule whose EventDetail elements include all the
    // event's and whose constants agree with it.
    private static String attributeUnmarked(final List<EventRule> rules,
                                            final Map<String, Map<FieldKey, FieldMapping>> effective,
                                            final XmlElement event) {
        final Map<FieldKey, List<String>> leaves = eventValues(event, DETAIL);
        for (final EventRule rule : rules) {
            final Map<FieldKey, FieldMapping> fields = effective.get(rule.name());
            if (fields.keySet().containsAll(leaves.keySet()) && fields.entrySet().stream().noneMatch(e ->
                    e.getValue().value() != null && leaves.containsKey(e.getKey())
                    && !leaves.get(e.getKey()).equals(List.of(e.getValue().value())))) {
                return rule.name();
            }
        }
        return null;
    }

    // The events' distinct values of a key, and whether any event had several.
    private static Values valuesOf(final List<XmlElement> events, final FieldKey key) {
        final List<String> found = new ArrayList<>();
        boolean several = false;
        for (final XmlElement event : events) {
            final List<String> got = eventValues(event, ALL_SECTIONS).getOrDefault(key, List.of());
            several = several || got.size() > 1;
            found.addAll(got);
        }
        return new Values(new ArrayList<>(new LinkedHashSet<>(found)), several);
    }

    // The source fields a mapping entry reads (nulls kept, as Python's list has them).
    private static List<String> readFrom(final FieldMapping entry) {
        if (entry == null) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        names.add(entry.field());
        names.addAll(Objects.requireNonNullElse(entry.anyOf(), List.of()));
        if (entry.lookup() != null) {
            names.add(entry.lookup().field());
        }
        return names;
    }

    private static String described(final EventSchema schema, final FieldKey key) {
        return FieldDocs.schemaDescription(schema, key.xpath());
    }

    // An entry's xpath below a prefix, with its Data name.
    private static String xpath(final FieldMapping entry, final String below) {
        final String path = entry.strippedPath();
        final String rest = path.startsWith(below)
                ? path.substring(below.length())
                : path;
        return rest + (PythonStrings.truthy(entry.dataName())
                ? "[@Name='" + entry.dataName() + "']/@Value"
                : "");
    }

    private static String orNotSampled(final String shown) {
        return shown.isEmpty()
                ? "(not in the sample)"
                : shown;
    }

    // What the events no rule wrote are, by their action element: Python's dict of a Counter.
    private static String unknownKinds(final List<XmlElement> events) {
        final Map<String, Integer> kinds = new LinkedHashMap<>();
        for (final XmlElement event : events) {
            kinds.merge(unknownKind(event), 1, Integer::sum);
        }
        return PythonRepr.repr(kinds);
    }

    private static String unknownKind(final XmlElement event) {
        final List<XmlElement> details = event.childElements(FieldDocs.EVT, "EventDetail");
        if (details.isEmpty()) {
            return "?";
        }
        return details.getFirst().childElements().stream()
                .map(XmlElement::localName)
                .filter(name -> !"TypeId".equals(name) && !"Description".equals(name))
                .findFirst()
                .orElse("?");
    }

    // Schema order: the indices of a path's steps, then the Data name; a path the schema lacks last.
    private static Comparator<FieldMapping> order(final EventSchema schema) {
        return (a, b) -> {
            final int byPath = compareIndices(indices(schema, a), indices(schema, b));
            return byPath != 0
                    ? byPath
                    : Objects.requireNonNullElse(a.dataName(), "").compareTo(Objects.requireNonNullElse(b.dataName(),
                            ""));
        };
    }

    private static List<Integer> indices(final EventSchema schema, final FieldMapping entry) {
        try {
            return schema.resolve(entry.strippedPath()).stream().map(EventSchema.Child::index).toList();
        } catch (final RuntimeException e) {
            return List.of(999);
        }
    }

    private static int compareIndices(final List<Integer> a, final List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            final int compared = Integer.compare(a.get(i), b.get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static void walk(final XmlElement node, final String path, final Map<FieldKey, List<String>> found) {
        final String name = node.localName();
        final String here = path + "/" + name;
        final List<XmlElement> children = node.childElements();
        if ("Data".equals(name) && node.get("Name") != null) {
            found.computeIfAbsent(new FieldKey(here, node.get("Name")), k -> new ArrayList<>())
                    .add(Objects.requireNonNullElse(node.get("Value"), ""));
        } else if (children.isEmpty()) {
            found.computeIfAbsent(new FieldKey(here, null), k -> new ArrayList<>())
                    .add(PythonStrings.strip(node.text()));
        }
        for (final XmlElement child : children) {
            walk(child, here, found);
        }
    }


    // --------------------------------------------------------------------------------


    /// A field as the mapping keys it: an element's path (below Event, no slashes round it), and for a Data entry
    /// its Name.
    ///
    /// @param path The path, e.g. `EventSource/User/Id` or `EventDetail/Authenticate/Data`.
    /// @param name A Data entry's Name, or null.
    public record FieldKey(String path, String name) {

        /// @param entry A field mapping.
        /// @return Its key.
        public static FieldKey of(final FieldMapping entry) {
            return new FieldKey(entry.strippedPath(), entry.dataName());
        }

        /// @return The xpath it is documented by, e.g. `EventDetail/Authenticate/Data[@Name='sid']/@Value`.
        public String xpath() {
            return path + (PythonStrings.truthy(name)
                    ? "[@Name='" + name + "']/@Value"
                    : "");
        }
    }

    // A row of a field of event types shown together: the event type, the key and its entry (null when not
    // mapped).
    private record Member(String name, FieldKey key, FieldMapping entry) {

    }

    // A key's distinct values, and whether any event had several.
    private record Values(List<String> values, boolean several) {

    }

    // The sample: whether there is one, how many events, which rule wrote each, and those no rule wrote.
    private record Sample(boolean sampled, int total, Map<String, List<XmlElement>> byRule,
                          List<XmlElement> unexplained) {

        /// @param rule A rule's name.
        /// @return The events it wrote.
        List<XmlElement> eventsOf(final String rule) {
            return byRule.getOrDefault(rule, List.of());
        }

        /// @return Every event a rule wrote, rule by rule.
        List<XmlElement> every() {
            return byRule.values().stream().flatMap(List::stream).toList();
        }
    }

    // What the tables are drawn from.
    private record Context(TranslationMapping mapping, EventSchema schema, List<EventRule> rules,
                           Map<String, Map<FieldKey, FieldMapping>> effective, Sample sample,
                           Comparator<FieldMapping> order, Descriptions described) {

    }
}
