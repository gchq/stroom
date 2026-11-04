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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.fielddoc.EventFieldDocs.FieldKey;
import stroom.ai.impl.tools.fielddoc.EventFieldDocs.Values;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// The Field mapping section of an events pipeline whose translation XSLT keeps no mapping (written by hand), from
/// the Events it writes over the sample: a table of the fields every event type has, then a section per event type
/// (types with roughly the same fields together) with a table of its own fields and the values the sample's events
/// got, as [EventFieldDocs] lays out a mapping's. Seen in testing: an XSLT written by hand documented by the agent
/// itself, its common fields there and every event type's own fields missing.
///
/// An event's type is its `EventDetail/TypeId`, else its action element (`Authenticate`, say). With no mapping, where
/// each value is read from is the XSLT's, so there is no From column.
public final class WrittenEventDocs {

    /// What a field's description is asked with in place of a mapping's source.
    static final String WRITTEN = "written by the XSLT";
    /// The event type of an event with no TypeId and no action element.
    static final String NO_TYPE = "(no event type)";

    private static final List<String> SOURCE_SECTIONS = List.of("EventTime", "EventSource");
    private static final List<String> DETAIL = List.of("EventDetail");
    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final Set<String> TYPE_AND_DESCRIPTION = Set.of(TYPE_ID, "EventDetail/Description");

    private WrittenEventDocs() {
        // Static only.
    }

    /// @param schema    The Events schema the XSLT writes.
    /// @param events    The sample's events ([EventFieldDocs#sampledEvents(List)]), at least one.
    /// @param layout    [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described The field descriptions, or null for none.
    /// @return The section's text, ending with a new line.
    public static String writtenEventsMarkdown(final EventSchema schema,
                                               final List<XmlElement> events,
                                               final String layout,
                                               final Descriptions described) {
        final Descriptions descriptions = Objects.requireNonNullElse(described, new Descriptions());
        final Map<String, List<XmlElement>> byType = byType(events);
        final List<FieldKey> common = commonSourceFields(byType);
        final List<String> lines = new ArrayList<>(List.of("The XSLT was written by hand, so these tables are of the "
                                                           + "Events it writes for the sample's " + events.size()
                                                           + " events, by event type (its TypeId, else its action "
                                                           + "element). Where each value is read from is the "
                                                           + "XSLT's.", ""));
        lines.addAll(commonTable(schema, common, events, descriptions));
        if (FieldDocs.COMPACT.equals(layout)) {
            lines.addAll(compactTypes(byType, events.size()));
        } else {
            lines.addAll(typeSections(schema, byType, common, events.size(), descriptions));
        }
        return String.join("\n", lines) + "\n";
    }

    /// @param event An Event element.
    /// @return Its event type: its TypeId, else its action element's name, else [#NO_TYPE].
    static String typeOf(final XmlElement event) {
        final List<String> typeId = EventFieldDocs.eventValues(event, DETAIL)
                .getOrDefault(new FieldKey(TYPE_ID, null), List.of());
        if (!typeId.isEmpty() && !ToolText.isBlank(typeId.getFirst())) {
            return ToolText.strip(typeId.getFirst());
        }
        for (final XmlElement section : event.childElements()) {
            if ("EventDetail".equals(section.localName())) {
                for (final XmlElement child : section.childElements()) {
                    if (!"TypeId".equals(child.localName()) && !"Description".equals(child.localName())) {
                        return child.localName();
                    }
                }
            }
        }
        return NO_TYPE;
    }

    // The events by type, in the order first seen.
    private static Map<String, List<XmlElement>> byType(final List<XmlElement> events) {
        final Map<String, List<XmlElement>> byType = new LinkedHashMap<>();
        events.forEach(event -> byType.computeIfAbsent(typeOf(event), type -> new ArrayList<>()).add(event));
        return byType;
    }

    // The EventTime and EventSource fields some event of every type has, in the order first seen.
    private static List<FieldKey> commonSourceFields(final Map<String, List<XmlElement>> byType) {
        final List<Set<FieldKey>> perType = byType.values().stream()
                .map(WrittenEventDocs::sourceFields)
                .toList();
        return perType.getFirst().stream()
                .filter(key -> perType.stream().allMatch(fields -> fields.contains(key)))
                .toList();
    }

    private static Set<FieldKey> sourceFields(final List<XmlElement> events) {
        final Set<FieldKey> keys = new LinkedHashSet<>();
        events.forEach(event -> keys.addAll(EventFieldDocs.eventValues(event, SOURCE_SECTIONS).keySet()));
        return keys;
    }

    private static List<String> commonTable(final EventSchema schema,
                                            final List<FieldKey> common,
                                            final List<XmlElement> events,
                                            final Descriptions descriptions) {
        final List<String> lines = new ArrayList<>(List.of("### Common fields", "", "Every event type has these. "
                                                                                    + "Sample values are what the "
                                                                                    + "sample's events were written, "
                                                                                    + events.size()
                                                                                    + " events in all.", ""));
        lines.addAll(FieldDocs.head(List.of("XPath", "Description", "Sample values")));
        common.forEach(key -> lines.add(row(schema, key, List.of(key.xpath()), events, descriptions)));
        return lines;
    }

    // A section per group of event types with roughly the same fields: how many events of each, and a row per field
    // of their own (their EventDetail, and any EventTime or EventSource field not every type has).
    private static List<String> typeSections(final EventSchema schema,
                                             final Map<String, List<XmlElement>> byType,
                                             final List<FieldKey> common,
                                             final int total,
                                             final Descriptions descriptions) {
        final Map<String, List<FieldKey>> own = new LinkedHashMap<>();
        byType.forEach((type, events) -> {
            final Set<FieldKey> keys = new LinkedHashSet<>();
            events.forEach(event -> keys.addAll(EventFieldDocs.eventValues(event, DETAIL).keySet()));
            sourceFields(events).stream().filter(key -> !common.contains(key)).forEach(keys::add);
            final List<FieldKey> ordered = new ArrayList<>(keys.stream()
                    .filter(key -> TYPE_AND_DESCRIPTION.contains(key.path()))
                    .toList());
            keys.stream().filter(key -> !TYPE_AND_DESCRIPTION.contains(key.path())).forEach(ordered::add);
            own.put(type, ordered);
        });
        // Judged as a mapping's rules are: on what their EventDetail holds besides TypeId, Description and its Data,
        // unless Data is all it holds; not on EventTime or EventSource fields only some of their events have.
        final Map<String, Set<String>> signatures = new LinkedHashMap<>();
        own.forEach((type, keys) -> {
            final List<FieldKey> judged = keys.stream()
                    .filter(key -> key.path().startsWith("EventDetail/") && !TYPE_AND_DESCRIPTION.contains(key.path()))
                    .toList();
            final List<FieldKey> typed = judged.stream().filter(key -> !JsonValues.truthy(key.name())).toList();
            signatures.put(type, (typed.isEmpty()
                    ? judged
                    : typed).stream()
                    .map(key -> FieldDocs.comparedPath(key.xpath(), schema))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        });
        final List<String> lines = new ArrayList<>(List.of("", "### Event types", ""));
        for (final List<String> types : FieldDocs.similarGroups(signatures)) {
            lines.add("#### " + String.join(", ", types));
            lines.add("");
            lines.add(types.stream()
                    .map(type -> (types.size() > 1
                            ? type + ": "
                            : "") + byType.get(type).size() + " of the sample's " + total + " events")
                    .collect(Collectors.joining("; ")) + ".");
            lines.add("");
            lines.addAll(FieldDocs.head(List.of("XPath", "Description", "Sample values")));
            // One row per field, the group's paths together under whichever action element each type has.
            final Map<String, List<FieldKey>> rows = new LinkedHashMap<>();
            final List<XmlElement> groupEvents = new ArrayList<>();
            for (final String type : types) {
                own.get(type).forEach(key -> {
                    final List<FieldKey> keys = rows.computeIfAbsent(FieldDocs.anyActionPath(key.xpath()),
                            path -> new ArrayList<>());
                    if (!keys.contains(key)) {
                        keys.add(key);
                    }
                });
                groupEvents.addAll(byType.get(type));
            }
            for (final List<FieldKey> keys : rows.values()) {
                final List<String> paths = keys.stream().map(FieldKey::xpath).toList();
                lines.add(row(schema, keys.getFirst(), paths, groupEvents, descriptions));
            }
            lines.add("");
        }
        return lines;
    }

    // The event types, compact: one row per type, with how many events and each EventDetail field's first value.
    private static List<String> compactTypes(final Map<String, List<XmlElement>> byType, final int total) {
        final List<String> lines = new ArrayList<>(List.of("", "### Event types", "", "One row per event type: how "
                                                                                       + "many of the sample's "
                                                                                       + total + " events, and each "
                                                                                       + "EventDetail element as path "
                                                                                       + "= a sampled value.", ""));
        lines.addAll(FieldDocs.head(List.of("Event type", "Events", "EventDetail")));
        byType.forEach((type, events) -> {
            final Map<FieldKey, List<String>> first = EventFieldDocs.eventValues(events.getFirst(), DETAIL);
            final String detail = first.entrySet().stream()
                    .filter(entry -> !TYPE_AND_DESCRIPTION.contains(entry.getKey().path()))
                    .map(entry -> "`" + entry.getKey().xpath().substring("EventDetail/".length()) + "` = "
                                  + (entry.getValue().isEmpty()
                            ? "(empty)"
                            : "\"" + entry.getValue().getFirst() + "\""))
                    .collect(Collectors.joining("\n"));
            lines.add(FieldDocs.row("**" + type + "**", String.valueOf(events.size()), detail));
        });
        return lines;
    }

    // A field's row: its path (the paths of the types shown together), description and the values written.
    private static String row(final EventSchema schema,
                              final FieldKey key,
                              final List<String> paths,
                              final List<XmlElement> events,
                              final Descriptions descriptions) {
        final List<String> found = new ArrayList<>();
        boolean several = false;
        for (final String path : new LinkedHashSet<>(paths)) {
            final FieldKey each = paths.size() == 1
                    ? key
                    : keyOf(path, key);
            final Values values = EventFieldDocs.valuesOf(events, each);
            found.addAll(values.values());
            several = several || values.several();
        }
        final List<String> values = new ArrayList<>(new LinkedHashSet<>(found));
        final List<String> distinct = new ArrayList<>(new LinkedHashSet<>(paths));
        final String shown = distinct.size() == 1
                ? distinct.getFirst()
                : FieldDocs.combinedPath(distinct);
        final String description = descriptions.of(shown.replace("\n", " | "), WRITTEN, values,
                Descriptions.eventLogging(EventFieldDocs.described(schema, key)), key.xpath(), List.of(),
                distinct.size() == 1
                        ? List.of()
                        : distinct, null);
        return FieldDocs.row(("`" + shown + "`").replace("\n", "`\n`"), description,
                EventFieldDocs.orNotSampled(FieldDocs.shown(values, true)) + (several
                        ? "\n(several per event)"
                        : ""));
    }

    // The key of a path shown with another: its path, and the same Data name.
    private static FieldKey keyOf(final String xpath, final FieldKey like) {
        if (JsonValues.truthy(like.name())) {
            final int at = xpath.lastIndexOf("[@Name=");
            return new FieldKey(at < 0
                    ? xpath
                    : xpath.substring(0, at), like.name());
        }
        return new FieldKey(xpath, null);
    }
}
