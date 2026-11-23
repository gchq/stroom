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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.catalogue.Placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// A case's reference catalogue of event types, as the source notes hold one (the event type catalogue's contract:
/// `event_types`, `catch_all`, `scope`), every event type and the catch-all confirmed by the user.
///
/// It is derived from the case's reference mapping, which fully determines it: one event type per TypeId (rules with
/// the same TypeId are one event type recognised several ways, each rule's `when` one way), in the mapping's order;
/// its title the first rule's name, its description the rule's fixed Description, else its title; its event detail
/// and fixed Action the rule's; its `placements` the rule's typed entries below its event detail, as the agent would
/// give them for the catalogue's check, which refuses to put a set to the user while an element the schema requires
/// is left empty; each `drop_when` rule a dropped event type.
///
/// The placements are the first rule's (the one the event type is otherwise derived from), then the mapping's
/// common entries under the same detail at paths the rule doesn't write (case 01's `EventDetail/Authenticate/User/Id`
/// is common, written for each of its events): each entry written to a path under `EventDetail/<detail>/`, its path
/// given below the detail, but the fixed Action (the event type's `action`), the outcome where the event type gives
/// its `success`, and the Data entries (a path through Data, or an entry with a `data_name`; a group's Data fields
/// dropped, a group left with none dropped too), as Data is what no placement takes. Only the keys a placement has
/// are kept (the mapping's field, value, any_of, join, xpath and lookup, its conversions and groups). The other rules
/// recognising the same event type write the same elements (one reference mapping, one event type), so the first
/// rule's placements are its. An old/new list's property (`change`, 2026-11-23) is one placement for both snapshots:
/// the After entry (or group) is kept, its Before twin left out, as the placement writes both.
///
/// The catch-all rule (`catch_all: true`, or one writing Unknown with `allow_unknown`) is the catalogue's catch-all,
/// not an event type. The scope is the case's source types where it has some.
///
/// Where the catalogue differs from what the mapping says, the case's `reference.catalogue` gives it: `event_types`,
/// each merged into the derived event type of its `type_id` (or added, if none has it), e.g. `basis: documentation`
/// for a type documented with no record in the sample; `basis`, every derived event type's (`documentation` for a
/// case whose every event type the vendor documents); and `drops`, the TypeIds of the `drop_when` rules, in order.
@SuppressWarnings("unchecked")
final class EvalCatalogues {

    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final String DESCRIPTION = "EventDetail/Description";
    private static final String EVENT_DETAIL = "EventDetail/";
    private static final Set<String> DETAIL_META = Set.of("TypeId", "Description", "Classification", "Purpose");
    private static final String CONFIRMED = "confirmed";
    private static final String PATH = "path";
    private static final String FIELDS = "fields";
    private static final String DATA = "Data";
    private static final String PLACEMENTS = "placements";
    // The keys of a mapping's fields entry a placement has ([stroom.ai.impl.tools.catalogue.Placement#KEYS]).
    private static final Set<String> PLACEMENT_KEYS = Set.copyOf(Placement.KEYS);

    private EvalCatalogues() {
        // Static only.
    }

    /// The case's reference catalogue.
    ///
    /// @param evalCase The case.
    /// @return The catalogue, as the source notes' JSON block holds it; null for a case with no reference mapping (a
    ///         question).
    static Map<String, Object> reference(final Map<String, Object> evalCase) {
        final Map<String, Object> reference = maps(evalCase.get("reference"));
        if (!(reference.get("mapping") instanceof final Map<?, ?> given)) {
            return null;
        }
        final Map<String, Object> mapping = (Map<String, Object>) given;
        final Map<String, Object> overlay = maps(reference.get("catalogue"));
        final Map<String, Map<String, Object>> types = new LinkedHashMap<>();
        for (final Map<String, Object> rule : list(mapping.get("events"))) {
            if (catchAll(rule)) {
                continue;
            }
            final String typeId = fixed(rule, TYPE_ID);
            if (typeId == null) {
                throw new IllegalStateException(evalCase.get("id") + ": rule " + rule.get("name")
                                                + " has no fixed TypeId");
            }
            types.computeIfAbsent(typeId, id -> eventType(id, rule, list(mapping.get("common"))));
            ((List<Object>) types.get(typeId).get("recognised_by")).add(way(rule.get("when")));
        }
        final List<String> drops = strings(overlay.get("drops"));
        final List<Map<String, Object>> dropRules = list(mapping.get("drop_when"));
        for (int i = 0; i < dropRules.size(); i++) {
            final String typeId = i < drops.size()
                    ? drops.get(i)
                    : "Drop-Rule" + (i + 1);
            final Map<String, Object> rule = dropRules.get(i);
            final Map<String, Object> type = types.computeIfAbsent(typeId, id -> dropped(id,
                    String.valueOf(rule.get("reason"))));
            ((List<Object>) type.get("recognised_by")).add(way(rule.get("when")));
        }
        if (overlay.get("basis") != null) {
            types.values().forEach(type -> type.put("basis", overlay.get("basis")));
        }
        for (final Map<String, Object> entry : list(overlay.get("event_types"))) {
            final String typeId = String.valueOf(entry.get("type_id"));
            final Map<String, Object> type = types.computeIfAbsent(typeId, id -> base(id, id, ""));
            type.putAll(entry);
        }
        types.values().forEach(EvalCatalogues::withoutGivenOutcome);
        final Map<String, Object> catalogue = new LinkedHashMap<>();
        catalogue.put("event_types", new ArrayList<>(types.values()));
        catalogue.put("catch_all", new LinkedHashMap<>(Map.of("status", CONFIRMED)));
        final Map<String, Object> sourceTypes = maps(reference.get("source_types"));
        if (!sourceTypes.isEmpty()) {
            final Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("source_type_field", sourceTypes.get("field"));
            scope.put("source_types", strings(sourceTypes.get("source_types")));
            catalogue.put("scope", scope);
        }
        return catalogue;
    }

    // An event type from its first rule: identity, meaning, a confirmed user's.
    private static Map<String, Object> eventType(final String typeId,
                                                 final Map<String, Object> rule,
                                                 final List<Map<String, Object>> common) {
        final String name = String.valueOf(rule.get("name"));
        final String title = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        final String description = fixed(rule, DESCRIPTION);
        final Map<String, Object> type = base(typeId, title, description != null
                ? description
                : title);
        final String detail = detail(rule);
        type.put("event_detail", detail);
        type.put("action", fixed(rule, EVENT_DETAIL + detail + "/Action"));
        final List<Map<String, Object>> placements = placements(list(rule.get(FIELDS)), detail,
                type.get("action") != null);
        // The common entries written below its event detail (Authenticate's user, say), for each of its events:
        // where its rule writes nothing of its own.
        for (final Map<String, Object> entry : placements(common, detail, type.get("action") != null)) {
            if (placements.stream().noneMatch(placement -> placement.get(PATH).equals(entry.get(PATH)))) {
                placements.add(entry);
            }
        }
        if (!placements.isEmpty()) {
            type.put(PLACEMENTS, placements);
        }
        return type;
    }

    // The typed entries (a rule's, or the common ones) below the event detail, as placements: the fixed Action (given
    // as the event type's action) and Data aside, each path below the detail, only a placement's keys kept.
    private static List<Map<String, Object>> placements(final List<Map<String, Object>> entries,
                                                        final String detail,
                                                        final boolean actionGiven) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (detail == null) {
            return out;
        }
        final String prefix = EVENT_DETAIL + detail + "/";
        for (final Map<String, Object> field : entries) {
            final String path = String.valueOf(field.get(PATH));
            if (!path.startsWith(prefix)) {
                continue;
            }
            final String below = path.substring(prefix.length());
            if (isData(below, field) || (actionGiven && "Action".equals(below) && field.get("value") != null)) {
                continue;
            }
            if (below.startsWith("Before/") && readsChange(field)) {
                // The After entry's placement writes both snapshots.
                continue;
            }
            final Map<String, Object> placement = placement(field);
            if (placement != null) {
                placement.put(PATH, below);
                out.add(placement);
            }
        }
        return out;
    }

    // An entry in a placement's form: a placement's keys only, a group's Data fields left out; null for a group
    // with only Data fields.
    private static Map<String, Object> placement(final Map<String, Object> field) {
        final Map<String, Object> out = new LinkedHashMap<>();
        field.forEach((key, value) -> {
            if (PLACEMENT_KEYS.contains(key)) {
                out.put(key, value);
            }
        });
        if (field.get("for_each") != null) {
            final List<Map<String, Object>> inner = new ArrayList<>();
            for (final Map<String, Object> one : list(field.get(FIELDS))) {
                if (!isData(String.valueOf(one.getOrDefault(PATH, "")), one)) {
                    inner.add(placement(one));
                }
            }
            if (inner.isEmpty()) {
                return null;
            }
            out.put(FIELDS, inner);
        }
        return out;
    }

    // An entry (or a group's field) reading an old/new list by the change form.
    private static boolean readsChange(final Map<String, Object> field) {
        return field.get("change") != null || list(field.get(FIELDS)).stream().anyMatch(EvalCatalogues::readsChange);
    }

    // An entry writing Data: a path through Data, or one naming its Data.
    private static boolean isData(final String path, final Map<String, Object> field) {
        return field.get("data_name") != null || List.of(path.split("/")).contains(DATA);
    }

    // The outcome placed and given as the event type's success (the case's catalogue gives it): the success only.
    private static void withoutGivenOutcome(final Map<String, Object> type) {
        if (type.get("success") != null && type.get(PLACEMENTS) instanceof final List<?> placements) {
            final List<Object> kept = new ArrayList<>(placements);
            kept.removeIf(placement -> "Outcome/Success".equals(((Map<String, Object>) placement).get(PATH)));
            type.put(PLACEMENTS, kept);
        }
    }

    private static Map<String, Object> dropped(final String typeId, final String reason) {
        final Map<String, Object> type = base(typeId, reason, reason);
        type.put("drop", true);
        return type;
    }

    private static Map<String, Object> base(final String typeId, final String title, final String description) {
        final Map<String, Object> type = new LinkedHashMap<>();
        type.put("type_id", typeId);
        type.put("title", title);
        type.put("description", description);
        type.put("recognised_by", new ArrayList<>());
        type.put("drop", false);
        type.put("basis", "user_confirmed");
        type.put("confidence", "high");
        type.put("one_type_because", null);
        type.put("status", CONFIRMED);
        return type;
    }

    // One way of recognising a type: the rule's conditions, none for a sole event type.
    private static Map<String, Object> way(final Object when) {
        final Map<String, Object> way = new LinkedHashMap<>();
        way.put("when", when instanceof final List<?> conditions
                ? new ArrayList<>(conditions)
                : new ArrayList<>());
        way.put("names", new ArrayList<>());
        return way;
    }

    // The rule for uncatalogued records: marked so, or writing Unknown as allowed.
    private static boolean catchAll(final Map<String, Object> rule) {
        return Boolean.TRUE.equals(rule.get("catch_all"))
               || (rule.get("allow_unknown") != null && "Unknown".equals(detail(rule)));
    }

    // The rule's event detail element (Authenticate, Network...), or null if it writes none.
    private static String detail(final Map<String, Object> rule) {
        for (final Map<String, Object> field : list(rule.get("fields"))) {
            final String path = String.valueOf(field.get("path"));
            if (path.startsWith(EVENT_DETAIL)) {
                final String[] parts = path.substring(EVENT_DETAIL.length()).split("/");
                if (!DETAIL_META.contains(parts[0])) {
                    // Network's action is its child: Network/Connect.
                    return "Network".equals(parts[0]) && parts.length > 2
                            ? parts[0] + "/" + parts[1]
                            : parts[0];
                }
            }
        }
        return null;
    }

    // The rule's fixed value for a path, or null if it has none (or reads it from the record).
    private static String fixed(final Map<String, Object> rule, final String path) {
        return list(rule.get("fields")).stream()
                .filter(field -> path.equals(field.get("path")) && field.get("value") != null)
                .map(field -> String.valueOf(field.get("value")))
                .findFirst()
                .orElse(null);
    }

    private static List<Map<String, Object>> list(final Object value) {
        return value instanceof final List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
    }

    private static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static Map<String, Object> maps(final Object value) {
        return value instanceof final Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }
}
