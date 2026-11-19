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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueOrder;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.TypeIds;
import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.ModelReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// The agent's proposals to a catalogue of event types (`catalogue_event_types` `proposals=`), applied by TypeId:
/// `add` a new event type, `change` the keys given of one (the rest kept), `remove` one. The catalogue is never sent
/// whole (seen: 304 kinds in one build). Each proposal is read laxly ([EventType#read]), and one that can't be
/// applied (not read, a TypeId that isn't Verb-Object, an add of a TypeId already there, a change or remove of one
/// not there, the user's basis claimed) is refused with why. A drop is the user's word only, relayed by the agent:
/// an event type proposed with `drop: true` has the user's basis (`user_confirmed`), and is still put to the user in
/// its form. An event type proposed or changed is the agent's until
/// the user confirms it in a form: its status is `proposed` whatever the proposal says. The event types are kept in
/// the catalogue's order, more specific first ([CatalogueOrder]); a catalogue whose event types change has its
/// catch-all put to the user again, as what it takes has changed.
final class CatalogueProposals {

    /// A proposal's operation.
    static final String OP = "op";
    /// Adds an event type.
    static final String ADD = "add";
    /// Changes the keys given of an event type.
    static final String CHANGE = "change";
    /// Removes an event type.
    static final String REMOVE = "remove";
    /// A proposal's TypeId.
    static final String TYPE_ID = "type_id";

    private static final List<String> OPS = List.of(ADD, CHANGE, REMOVE);
    private static final String STATUS = "status";
    private static final String BASIS = "basis";

    private CatalogueProposals() {
        // Static only.
    }

    /// @param catalogue The catalogue so far.
    /// @param proposals The proposals, in the order given.
    /// @return The catalogue with every proposal that could be applied, and those refused with why.
    static Applied apply(final Catalogue catalogue, final List<Map<?, ?>> proposals) {
        final List<EventType> types = new ArrayList<>(catalogue.eventTypes());
        final List<Refused> refused = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < proposals.size(); i++) {
            final Map<?, ?> proposal = proposals.get(i);
            final String op = ToolText.str(proposal.get(OP)).strip().toLowerCase(Locale.ROOT);
            final String typeId = text(proposal.get(TYPE_ID));
            final String why = switch (op) {
                case ADD -> added(types, proposal);
                case CHANGE -> changedIn(types, typeId, proposal);
                case REMOVE -> removed(types, typeId);
                default -> "op is one of " + String.join(", ", OPS) + ", not '" + ToolText.str(proposal.get(OP))
                           + "'";
            };
            if (why == null) {
                changed = true;
            } else {
                refused.add(new Refused(i, typeId, why));
            }
        }
        if (!changed) {
            return new Applied(catalogue, refused);
        }
        final Catalogue.CatchAll catchAll = catalogue.catchAll().confirmed()
                ? new Catalogue.CatchAll(EventType.PROPOSED, catalogue.catchAll().allowUnknown())
                : catalogue.catchAll();
        return new Applied(new Catalogue(CatalogueOrder.ordered(types), catchAll, catalogue.scope()), refused);
    }

    // An event type added; why not, or null.
    private static String added(final List<EventType> types, final Map<?, ?> proposal) {
        final Map<String, Object> json = keys(proposal);
        final String claimed = claimedBasis(json);
        if (claimed != null) {
            return claimed;
        }
        json.put(STATUS, EventType.PROPOSED);
        dropsAsTheUsers(json);
        final ModelReader reader = new ModelReader();
        final EventType read = EventType.read(reader, json, List.of());
        if (read == null || !reader.errors().isEmpty()) {
            return said(reader.errors());
        }
        final EventType type = read.typeId() == null || read.typeId().isBlank()
                ? read.withTypeId(TypeIds.defaultTypeId(read))
                : read;
        final String problem = TypeIds.problem(type.typeId());
        if (problem != null) {
            return problem;
        }
        if (at(types, type.typeId()) >= 0) {
            return "type_id '" + type.typeId() + "' is already in the catalogue (each event type has its own): "
                   + "op: change changes it; another event type needs another TypeId";
        }
        types.add(type);
        return null;
    }

    // An event type's keys changed, those not given kept; why not, or null.
    private static String changedIn(final List<EventType> types, final String typeId, final Map<?, ?> proposal) {
        final int at = at(types, typeId);
        if (at < 0) {
            return missing(typeId);
        }
        final Map<String, Object> given = keys(proposal);
        final String claimed = claimedBasis(given);
        if (claimed != null) {
            return claimed;
        }
        given.remove(TYPE_ID);
        given.remove(STATUS);
        final EventType existing = types.get(at);
        final Map<String, Object> json = new LinkedHashMap<>(existing.toJson());
        json.putAll(given);
        dropsAsTheUsers(json);
        final ModelReader reader = new ModelReader();
        final EventType read = EventType.read(reader, json, List.of());
        if (read == null || !reader.errors().isEmpty()) {
            return said(reader.errors());
        }
        // Changed, it is the agent's again until the user confirms it; the same as it was, it stays as it was.
        final Map<String, Object> proposed = new LinkedHashMap<>(read.toJson());
        proposed.put(STATUS, existing.status());
        final boolean same = Objects.equals(proposed, existing.toJson());
        json.put(STATUS, same
                ? existing.status()
                : EventType.PROPOSED);
        types.set(at, EventType.read(new ModelReader(), json, List.of()));
        return null;
    }

    // An event type removed; why not, or null.
    private static String removed(final List<EventType> types, final String typeId) {
        final int at = at(types, typeId);
        if (at < 0) {
            return missing(typeId);
        }
        types.remove(at);
        return null;
    }

    private static String missing(final String typeId) {
        return typeId == null
                ? "give type_id: the event type the proposal is of"
                : "no event type '" + typeId + "' in the catalogue: op: add adds it";
    }

    // A drop is the user's word: its basis the user's.
    private static void dropsAsTheUsers(final Map<String, Object> json) {
        if (isDrop(json)) {
            json.put(BASIS, EventType.USER_CONFIRMED);
        }
    }

    private static boolean isDrop(final Map<String, Object> json) {
        return Boolean.TRUE.equals(json.get("drop")) || "true".equalsIgnoreCase(String.valueOf(json.get("drop")));
    }

    // The user's basis is given only by the user's form (or with the user's drop): never in another proposal.
    private static String claimedBasis(final Map<String, Object> json) {
        return EventType.USER_CONFIRMED.equals(json.get(BASIS)) && !isDrop(json)
                ? "basis user_confirmed is the user's, given when they confirm an event type in its form: propose it "
                  + "as agent_knowledge, documentation or draft"
                : null;
    }

    // The proposal's keys, as the catalogue holds them: all but op.
    private static Map<String, Object> keys(final Map<?, ?> proposal) {
        final Map<String, Object> out = new LinkedHashMap<>();
        proposal.forEach((key, value) -> {
            if (!OP.equals(String.valueOf(key))) {
                out.put(String.valueOf(key), value);
            }
        });
        return out;
    }

    // Where the event type of a TypeId is, compared ignoring case (as titles are); -1 when it isn't there.
    private static int at(final List<EventType> types, final String typeId) {
        if (typeId == null) {
            return -1;
        }
        for (int i = 0; i < types.size(); i++) {
            if (typeId.equalsIgnoreCase(types.get(i).typeId())) {
                return i;
            }
        }
        return -1;
    }

    private static String said(final List<MappingError> errors) {
        final List<String> out = new ArrayList<>();
        errors.forEach(error -> out.add((error.dotted().isEmpty()
                ? ""
                : error.dotted() + ": ") + error.message()));
        return out.isEmpty()
                ? "it can't be read as an event type"
                : String.join("; ", out);
    }

    private static String text(final Object value) {
        return value == null || ToolText.str(value).isBlank()
                ? null
                : ToolText.str(value).strip();
    }


    // --------------------------------------------------------------------------------


    /// The proposals applied.
    ///
    /// @param catalogue The catalogue with those that could be applied.
    /// @param refused   Those that couldn't, with why.
    record Applied(Catalogue catalogue, List<Refused> refused) {

    }


    // --------------------------------------------------------------------------------


    /// A proposal that couldn't be applied.
    ///
    /// @param index  Its place among the proposals, from 0.
    /// @param typeId Its TypeId, or null when it gave none.
    /// @param why    Why.
    record Refused(int index, String typeId, String why) {

        /// @return It as JSON.
        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("proposal", index);
            if (typeId != null) {
                out.put(TYPE_ID, typeId);
            }
            out.put("why", why);
            return out;
        }
    }
}
