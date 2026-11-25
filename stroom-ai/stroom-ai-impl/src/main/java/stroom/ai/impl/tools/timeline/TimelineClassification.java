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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.EventCodes.CodeRole;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The automatic sources' events classified, each by the first way that tells (counted per source): its Events XML
/// ([EventXmlClassifier], read by StreamId and EventId, only where the index doesn't hold the event's type, or for the
/// content of the events characterised); else the element (and Action) fields the index holds; else the source's own
/// event code the index holds, as classified once and confirmed ([EventCodes]); else not classified (an action of
/// the source). Each type's role is then decided once per type ([TypeRoleLookup]): the call's or skill's, the
/// catalogue of the stream's translation, an AGENTS doc's `## Event types`, else its own; a type ignored is left out.
/// The user and the time are the index's, never the XML's.
final class TimelineClassification {

    private TimelineClassification() {
        // Static only.
    }

    /// Classifies the events.
    ///
    /// @param events    Every event read.
    /// @param sources   The sources, by their place.
    /// @param extras    The automatic sources' events' more fields, by event id.
    /// @param fields    Each automatic source's classifying fields, by its place.
    /// @param xml       The events' XML read, by `streamId:eventId`.
    /// @param codes     Each automatic source's codes' roles, by its place.
    /// @param roles     Each type's role, decided once per type: the call's or skill's, the catalogue of the stream's
    ///                  translation, an AGENTS doc's, else its own as classified ([TypeRoleLookup]).
    /// @return The events as the timeline reads them (classified, the ignored left out), how each was classified,
    ///         and the counts per source and route.
    static Result classify(final List<Event> events,
                           final List<Source> sources,
                           final Map<String, Map<String, String>> extras,
                           final Map<Integer, Fields> fields,
                           final Map<String, String> xml,
                           final Map<Integer, Map<String, CodeRole>> codes,
                           final TypeRoleLookup roles) {
        final List<Event> out = new ArrayList<>();
        final Map<String, Classified> byId = new LinkedHashMap<>();
        final Map<Integer, Map<Route, Integer>> routes = new LinkedHashMap<>();
        final Map<Integer, Map<String, Classified>> byCode = codesFromXml(events, extras, fields, xml);
        int ignored = 0;
        for (final Event event : events) {
            final Source source = sources.get(event.source());
            if (!source.automatic()) {
                routes.computeIfAbsent(event.source(), s -> new EnumMap<>(Route.class)).merge(Route.ROLES, 1,
                        Integer::sum);
                out.add(event);
                continue;
            }
            final Fields sourceFields = fields.getOrDefault(event.source(), Fields.NONE);
            final Map<String, String> extra = extras.getOrDefault(event.id(), Map.of());
            final Classified found = classifyOne(event, extra, sourceFields, xml, byCode.getOrDefault(
                    event.source(), Map.of()), codes.getOrDefault(event.source(), Map.of()))
                    .withKeys(indexKeys(source, sourceFields, event, extra));
            routes.computeIfAbsent(event.source(), s -> new EnumMap<>(Route.class)).merge(found.route(), 1,
                    Integer::sum);
            final Classified classified = roles.decide(event.source(), event.streamId(), found).orElse(null);
            if (classified == null || classified.role() == null) {
                ignored++;
                continue;
            }
            byId.put(event.id(), classified);
            out.add(asEvent(event, classified));
        }
        return new Result(out, byId, routes, ignored, roles.origins());
    }

    /// Each automatic source's event codes classified by the Events XML of the few of their events read (decided
    /// with the user 2026-11-25): a code once, by its events' most common schema type (the earliest on a tie), to
    /// apply to all its events.
    ///
    /// @param events The events read.
    /// @param extras Their more fields, by event id.
    /// @param fields Each automatic source's classifying fields, by its place.
    /// @param xml    The events' XML read, by `streamId:eventId`.
    /// @return Each source's codes, by its place, with how one of their events' XML classified them.
    static Map<Integer, Map<String, Classified>> codesFromXml(final List<Event> events,
                                                              final Map<String, Map<String, String>> extras,
                                                              final Map<Integer, Fields> fields,
                                                              final Map<String, String> xml) {
        final Map<Integer, Map<String, Map<String, List<Classified>>>> found = new LinkedHashMap<>();
        if (xml.isEmpty()) {
            return Map.of();
        }
        for (final Event event : events) {
            final Fields sourceFields = fields.getOrDefault(event.source(), Fields.NONE);
            final String text = event.streamId() == null || event.eventId() == null
                    ? null
                    : xml.get(event.streamId() + ":" + event.eventId());
            final String code = sourceFields.eventType() == null
                    ? null
                    : extras.getOrDefault(event.id(), Map.of()).get(sourceFields.eventType());
            if (text == null || NullSafe.isBlankString(code)) {
                continue;
            }
            EventXmlClassifier.classify(text)
                    .filter(classified -> classified.element() != null)
                    .ifPresent(classified -> found.computeIfAbsent(event.source(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(code, k -> new LinkedHashMap<>())
                            .computeIfAbsent(classified.schemaType(), k -> new ArrayList<>())
                            .add(classified));
        }
        final Map<Integer, Map<String, Classified>> out = new LinkedHashMap<>();
        found.forEach((source, byCode) -> byCode.forEach((code, byType) -> {
            List<Classified> most = null;
            for (final List<Classified> same : byType.values()) {
                if (most == null || same.size() > most.size()) {
                    most = same;
                }
            }
            if (most != null) {
                out.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(code, most.getFirst());
            }
        }));
        return out;
    }

    private static Classified classifyOne(final Event event,
                                          final Map<String, String> extra,
                                          final Fields fields,
                                          final Map<String, String> xml,
                                          final Map<String, Classified> byCode,
                                          final Map<String, CodeRole> codes) {
        if (event.streamId() != null && event.eventId() != null) {
            final String text = xml.get(event.streamId() + ":" + event.eventId());
            if (text != null) {
                final Classified fromXml = EventXmlClassifier.classify(text).orElse(null);
                if (fromXml != null) {
                    return fromXml;
                }
            }
        }
        final String code = fields.eventType() == null
                ? null
                : extra.get(fields.eventType());
        final String element = fields.element() == null
                ? null
                : extra.get(fields.element());
        if (EventXmlClassifier.isElement(element)) {
            return EventXmlClassifier.of(element, fields.action() == null
                    ? null
                    : extra.get(fields.action()), code, null, event.object(), null, Map.of(), Route.INDEX_FIELDS);
        }
        if (code != null && byCode.containsKey(code)) {
            // The code as the XML of a few of its events classified it: its element and Action, not their content.
            final Classified sampled = byCode.get(code);
            return EventXmlClassifier.of(sampled.element(), sampled.action(), code, null, event.object(), null,
                    Map.of(), Route.XML_CODE);
        }
        if (code != null && codes.containsKey(code)) {
            final CodeRole role = codes.get(code);
            return new Classified(null, null, code, EventCodes.role(role.role()), role.kind(), null, event.object(),
                    null, Map.of(), Route.EVENT_CODE);
        }
        return new Classified(null, null, code, Role.ACTION, Objects.requireNonNullElse(code, "event"), null,
                event.object(), null, Map.of(), code == null
                ? Route.NONE
                : Route.TYPE_FIELD);
    }

    // The index's fields returned with an event, by their names, as correlation keys beside its XML's: what it
    // touched, its window, its application, its other fields; never its user, its host (the lane already) or the
    // fields that say its type.
    private static Map<String, String> indexKeys(final Source source,
                                                 final Fields fields,
                                                 final Event event,
                                                 final Map<String, String> extra) {
        final Map<String, String> keys = new LinkedHashMap<>();
        putKey(keys, source.objectField(), event.object());
        putKey(keys, source.windowField(), event.window());
        putKey(keys, source.processField(), event.process());
        putKey(keys, source.titleField(), event.title());
        // A process's id the index holds, as the schema's pair matches a process's start to its end.
        if (fields.processId() != null) {
            putKey(keys, "ProcessId", extra.get(fields.processId()));
        }
        final Set<String> typeFields = new HashSet<>();
        for (final String field : new String[]{fields.element(), fields.action(), fields.eventType(),
                fields.processId(), source.userField(), source.hostField()}) {
            if (field != null) {
                typeFields.add(field);
            }
        }
        extra.forEach((field, value) -> {
            if (!typeFields.contains(field)) {
                putKey(keys, field, value);
            }
        });
        return keys;
    }

    private static void putKey(final Map<String, String> keys, final String field, final String value) {
        if (field != null && value != null && !value.isBlank() && value.length() <= EventXmlClassifier.MAX_KEY_CHARS) {
            keys.putIfAbsent(field, value);
        }
    }

    // The event as the timeline reads it: its role, kind, application, object and window from its classification;
    // its host the index's, else the XML's; its user and time the index's, always.
    private static Event asEvent(final Event event, final Classified c) {
        final String host = event.host() != null
                ? event.host()
                : c.host();
        final String window = c.role() == Role.OPEN || c.role() == Role.CLOSE
                ? firstNonNull(c.keys().get("ProcessId"), c.application())
                : null;
        // The index's own application, title and window where the call names their fields: a code or an index
        // field says what an event was, not its application (eval case 77 with no roles: focus sessions with none).
        final String application = event.process() != null
                ? NullSafe.nonBlankStringElse(EventXmlClassifier.baseName(event.process()), event.process())
                : c.application();
        return new Event(event.time(), event.source(), event.id(), c.role(), c.kind(), host, application,
                event.title(), firstNonNull(window, event.window()), event.object() != null
                ? event.object()
                : c.object(), event.text(), event.streamId(), event.eventId());
    }


    private static String firstNonNull(final String a, final String b) {
        return a != null
                ? a
                : b;
    }


    // --------------------------------------------------------------------------------


    /// The index's fields an automatic source's events are classified by, where their XML is gone.
    ///
    /// @param element   The field holding the EventDetail element, or null.
    /// @param action    The field holding its Action, or null.
    /// @param eventType The field holding the source's own event code, or null.
    /// @param processId The field holding a process's id (the schema's `EventDetail/Process/ProcessId`), or null.
    record Fields(String element, String action, String eventType, String processId) {

        /// No fields.
        static final Fields NONE = new Fields(null, null, null, null);

        /// @param element   The field holding the EventDetail element, or null.
        /// @param action    The field holding its Action, or null.
        /// @param eventType The field holding the source's own event code, or null.
        Fields(final String element, final String action, final String eventType) {
            this(element, action, eventType, null);
        }

        /// @return True if the index holds what an event's type is (its element or event code), so its Events XML
        /// isn't read to classify it.
        boolean typed() {
            return element != null || eventType != null;
        }
    }


    // --------------------------------------------------------------------------------


    /// A type's role given by a skill or the call.
    ///
    /// @param role The role, or null to leave its events out.
    /// @param from Where it came from (`skill`, `call`).
    record RoleOverride(Role role, String from) {

    }


    // --------------------------------------------------------------------------------


    /// The events classified.
    ///
    /// @param events  The events as the timeline reads them, the ignored left out.
    /// @param byId    How each automatic source's event was classified, by its id.
    /// @param routes  Each source's events by route.
    /// @param ignored The events left out, as a skill or the call (or a code) says to ignore their type.
    /// @param roleFrom For each source, by its place, the types whose role each origin decided ([TypeRoleLookup]).
    record Result(List<Event> events,
                  Map<String, Classified> byId,
                  Map<Integer, Map<Route, Integer>> routes,
                  int ignored,
                  Map<Integer, Map<String, Set<String>>> roleFrom) {

    }
}
