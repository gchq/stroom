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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The automatic sources' events classified, each by the first way that tells (counted per source): its Events XML
/// ([EventXmlClassifier], read by StreamId and EventId); else the element (and Action) fields the index holds; else
/// the source's own event code, as classified once and confirmed ([EventCodes]); else not classified (an action of
/// the source). Then the overrides (a skill's Event chains, the call's `typeRoles`): a type given a role takes it,
/// one ignored is left out. The user and the time are the index's, never the XML's.
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
    /// @param overrides Each source's types given a role (null: ignored), by its place.
    /// @return The events as the timeline reads them (classified, the ignored left out), how each was classified,
    ///         and the counts per source and route.
    static Result classify(final List<Event> events,
                           final List<Source> sources,
                           final Map<String, Map<String, String>> extras,
                           final Map<Integer, Fields> fields,
                           final Map<String, String> xml,
                           final Map<Integer, Map<String, CodeRole>> codes,
                           final Map<Integer, Map<String, RoleOverride>> overrides) {
        final List<Event> out = new ArrayList<>();
        final Map<String, Classified> byId = new LinkedHashMap<>();
        final Map<Integer, Map<Route, Integer>> routes = new LinkedHashMap<>();
        int ignored = 0;
        for (final Event event : events) {
            final Source source = sources.get(event.source());
            if (!source.automatic()) {
                routes.computeIfAbsent(event.source(), s -> new EnumMap<>(Route.class)).merge(Route.ROLES, 1,
                        Integer::sum);
                out.add(event);
                continue;
            }
            Classified classified = classifyOne(event, extras.getOrDefault(event.id(), Map.of()),
                    fields.getOrDefault(event.source(), Fields.NONE), xml, codes.getOrDefault(event.source(),
                            Map.of()));
            routes.computeIfAbsent(event.source(), s -> new EnumMap<>(Route.class)).merge(classified.route(), 1,
                    Integer::sum);
            final RoleOverride override = overrideOf(classified, overrides.getOrDefault(event.source(), Map.of()));
            if (override != null) {
                if (override.role() == null) {
                    ignored++;
                    continue;
                }
                classified = classified.withRole(override.role());
            }
            if (classified.role() == null) {
                ignored++;
                continue;
            }
            byId.put(event.id(), classified);
            out.add(asEvent(event, classified));
        }
        return new Result(out, byId, routes, ignored);
    }

    private static Classified classifyOne(final Event event,
                                          final Map<String, String> extra,
                                          final Fields fields,
                                          final Map<String, String> xml,
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
        if (code != null && codes.containsKey(code)) {
            final CodeRole role = codes.get(code);
            return new Classified(null, null, code, EventCodes.role(role.role()), role.kind(), null, event.object(),
                    null, Map.of(), Route.EVENT_CODE);
        }
        return new Classified(null, null, code, Role.ACTION, Objects.requireNonNullElse(code, "event"), null,
                event.object(), null, Map.of(), Route.NONE);
    }

    private static RoleOverride overrideOf(final Classified classified, final Map<String, RoleOverride> overrides) {
        for (final Map.Entry<String, RoleOverride> entry : overrides.entrySet()) {
            if (classified.isType(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
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
        return new Event(event.time(), event.source(), event.id(), c.role(), c.kind(), host, c.application(), null,
                window, event.object() != null
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
    record Fields(String element, String action, String eventType) {

        /// No fields.
        static final Fields NONE = new Fields(null, null, null);
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
    record Result(List<Event> events,
                  Map<String, Classified> byId,
                  Map<Integer, Map<Route, Integer>> routes,
                  int ignored) {

    }
}
