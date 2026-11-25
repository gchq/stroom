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

import stroom.ai.impl.tools.catalogue.TimelineRole;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.instructions.InstructionRules.TypeRoleRule;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelineClassification.RoleOverride;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.util.shared.NullSafe;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/// An event type's timeline role, decided once per type (never per event; decided with the user 2026-11-25), the
/// first of: the call's or the skill's (`typeRoles`, Event chains); the catalogue in the source notes of the
/// translation that produced the event's stream ([stroom.ai.impl.tools.streams.StreamProvenance#timelineRoles]), by
/// its TypeId; an AGENTS doc's `## Event types` line; else the type's own, as classified (the schema's table by its
/// element and Action, from the index's fields or the Events XML; else its event code, as classified once). Each
/// type's origin is counted, per source.
final class TypeRoleLookup {

    /// From the call.
    static final String FROM_CALL = "call";
    /// From the skill's Event chains.
    static final String FROM_SKILL = "skill";
    /// From the catalogue of the stream's translation.
    static final String FROM_CATALOGUE = "catalogue";
    /// From an AGENTS doc's `## Event types`.
    static final String FROM_AGENTS = "AGENTS doc";
    /// From the schema's table, by the element and Action.
    static final String FROM_SCHEMA = "schema";
    /// From the event code, as classified.
    static final String FROM_CODE = "event code";
    /// Not classified: an action of the source.
    static final String FROM_NONE = "not classified";

    private final Map<Integer, Map<String, RoleOverride>> overrides;
    private final Function<String, Map<String, TimelineRoles.Role>> catalogue;
    private final Map<Integer, Map<String, TypeRoleRule>> agents;
    private final Map<String, Decision> decided = new HashMap<>();
    private final Map<Integer, Map<String, Set<String>>> origins = new LinkedHashMap<>();

    /// @param overrides Each source's types given a role by the call or the skill, by its place.
    /// @param catalogue The catalogue's roles of the translation that produced a stream, by the stream's id (as
    ///                  text); empty where there are none. The same map for every stream of one translation: a type
    ///                  is decided once per source and translation.
    /// @param agents    Each source's types given a role by an AGENTS doc, by its place.
    TypeRoleLookup(final Map<Integer, Map<String, RoleOverride>> overrides,
                   final Function<String, Map<String, TimelineRoles.Role>> catalogue,
                   final Map<Integer, Map<String, TypeRoleRule>> agents) {
        this.overrides = NullSafe.map(overrides);
        this.catalogue = catalogue == null
                ? streamId -> Map.of()
                : catalogue;
        this.agents = NullSafe.map(agents);
    }

    /// @param overrides Each source's types given a role by the call or the skill, by its place.
    /// @return A lookup of those alone, no catalogue or AGENTS doc.
    static TypeRoleLookup of(final Map<Integer, Map<String, RoleOverride>> overrides) {
        return new TypeRoleLookup(overrides, null, Map.of());
    }

    /// @param source   The event's source, by its place.
    /// @param streamId The event's stream, or null.
    /// @param c        The event as classified.
    /// @return It with its type's role, decided once per type; empty if the type is ignored.
    Optional<Classified> decide(final int source, final String streamId, final Classified c) {
        final Map<String, TimelineRoles.Role> fromCatalogue = streamId == null
                ? Map.of()
                : NullSafe.map(catalogue.apply(streamId));
        // The catalogue's roles are a translation's: types are decided again for another translation.
        final String key = source + "\n" + System.identityHashCode(fromCatalogue) + "\n" + c.typeId() + "\n"
                           + c.element() + "\n" + c.action() + "\n" + c.route();
        final Decision decision = decided.computeIfAbsent(key, k -> decision(source, c, fromCatalogue));
        origins.computeIfAbsent(source, k -> new LinkedHashMap<>())
                .computeIfAbsent(decision.from(), k -> new LinkedHashSet<>())
                .add(NullSafe.nonBlankStringElse(c.type(), c.kind()));
        if (decision.ignored()) {
            return Optional.empty();
        }
        if (decision.role() == null) {
            return Optional.of(c);
        }
        return Optional.of(c.withRole(decision.role(), decision.kind()));
    }

    /// @param source   A source, by its place.
    /// @param streamId An event's stream, or null.
    /// @param type     A type, e.g. an event code.
    /// @return True if the call, the skill, the catalogue of the stream's translation or an AGENTS doc gives the
    /// type a role: its events need no other classification (no side call for their code).
    boolean settles(final int source, final String streamId, final String type) {
        if (type == null) {
            return false;
        }
        if (overrides.getOrDefault(source, Map.of()).keySet().stream().anyMatch(type::equalsIgnoreCase)
            || agents.getOrDefault(source, Map.of()).keySet().stream().anyMatch(type::equalsIgnoreCase)) {
            return true;
        }
        return streamId != null && NullSafe.map(catalogue.apply(streamId)).keySet().stream()
                .anyMatch(type::equalsIgnoreCase);
    }

    /// @return For each source, by its place, each origin's types decided by it, in order.
    Map<Integer, Map<String, Set<String>>> origins() {
        return Collections.unmodifiableMap(origins);
    }

    private Decision decision(final int source,
                              final Classified c,
                              final Map<String, TimelineRoles.Role> fromCatalogue) {
        for (final Map.Entry<String, RoleOverride> entry : overrides.getOrDefault(source, Map.of()).entrySet()) {
            if (c.isType(entry.getKey())) {
                return entry.getValue().role() == null
                        ? Decision.ignore(entry.getValue().from())
                        : new Decision(entry.getValue().role(), null, entry.getValue().from(), false);
            }
        }
        if (c.typeId() != null) {
            for (final Map.Entry<String, TimelineRoles.Role> entry : fromCatalogue.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(c.typeId())) {
                    return of(entry.getValue().role(), entry.getValue().role() == TimelineRole.ACTION
                            ? entry.getValue().kind()
                            : null, FROM_CATALOGUE);
                }
            }
        }
        for (final Map.Entry<String, TypeRoleRule> entry : agents.getOrDefault(source, Map.of()).entrySet()) {
            if (c.isType(entry.getKey())) {
                return of(entry.getValue().role(), null, FROM_AGENTS);
            }
        }
        final String own = switch (c.route()) {
            case EVENT_CODE -> FROM_CODE;
            case NONE, TYPE_FIELD -> FROM_NONE;
            default -> c.role() == null
                    ? FROM_NONE
                    : FROM_SCHEMA;
        };
        return c.role() == null && c.route() != Route.NONE && c.route() != Route.TYPE_FIELD
                ? Decision.ignore(own)
                : new Decision(null, null, own, false);
    }

    private static Decision of(final TimelineRole role, final String kind, final String from) {
        if (role == TimelineRole.IGNORE) {
            return Decision.ignore(from);
        }
        return new Decision(EventCodes.role(role.label()), kind, from, false);
    }


    // --------------------------------------------------------------------------------


    // A type's role: null to keep its own; ignored to leave its events out.
    private record Decision(Role role, String kind, String from, boolean ignored) {

        static Decision ignore(final String from) {
            return new Decision(null, null, from, true);
        }
    }
}
