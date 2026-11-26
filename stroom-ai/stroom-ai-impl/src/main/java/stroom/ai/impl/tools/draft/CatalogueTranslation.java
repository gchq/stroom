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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.IpAddresses;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueOrder;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.Placement;
import stroom.ai.impl.tools.catalogue.TypeIds;
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.ListChange;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.XPathFieldReads;
import stroom.ai.impl.tools.sample.NameValueLists;
import stroom.ai.impl.tools.sample.NameValueLists.ChangeList;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// The translation's rules from the catalogue of event types (the source notes' `event_types`): one rule per (event
/// type, way of recognising it), in the catalogue's order ([CatalogueOrder#ways]), its `when` the way's conditions;
/// then the catch-all's rule for uncatalogued records (Unknown, their fields kept as Data, `catch_all`), left out
/// after a sole event type, which takes every record; an event type the user dropped is a `drop_when` rule per way.
/// The catalogue comes first and the XSLT follows from it.
///
/// A rule's fields are built from the draft's own rule that took the way's records in the sample (what the draft
/// read stays: severity, names, addresses), then what the catalogue says over it:
///
/// * TypeId fixed: the event type's (Verb-Object), else the default ([TypeIds#defaultTypeId]).
/// * The vendor's code as Data: every `equals`/`one_of` field of the way but the source type field, named after
///   the field's last segment (`System/EventID` is `EventID`), so searching by the code still works.
/// * Description: the record's own message, alone, where the records carry one; else the event type's description
///   (the vendor documentation's text, or the agent's) as fixed text.
/// * The action element's Action and outcome, who acted (a field naming a person, not an address), what was acted
///   on (`affected_as`), the user authenticating for Authenticate.
/// * The fields left over that the way's own records hold, as Data of the action element.
final class CatalogueTranslation {

    /// The catch-all's rule's name.
    static final String CATCH_ALL_NAME = "uncatalogued";
    /// The catch-all's TypeId.
    static final String CATCH_ALL_TYPE_ID = "Unknown";
    /// The catch-all's agreement when the catch-all was confirmed with no reason of its own.
    static final String CATCH_ALL_AGREED = "records no event type recognises (uncatalogued records): the catch-all "
                                          + "confirmed with the catalogue";
    /// Why a field is left out of every rule.
    static final String EMPTY_EVERYWHERE = "empty in every sampled record, so left out of the rules";

    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final String DESCRIPTION = "EventDetail/Description";
    private static final String UNKNOWN = "Unknown";
    private static final String FIELD = "field";
    private static final String PATH = "path";
    private static final String VALUE = "value";
    private static final String DATA_NAME = "data_name";
    private static final String FIELDS = "fields";
    private static final String WHEN = "when";
    private static final String NAME = "name";
    private static final Pattern NOT_NAME = Pattern.compile("[^A-Za-z0-9]+");
    // Where a word starts inside a name written in camel case (SearchQuery, HTTPRequest, Win32Process).
    private static final Pattern WORD_START = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    // The longest a rule name's part telling one of a type's ways apart is.
    private static final int MAX_TOLD_APART = 24;

    private final Map<String, Object> mapping;
    private final List<String> names;
    private final Map<String, List<String>> values;
    private final Catalogue catalogue;
    private final List<Map<String, Object>> detailFields;
    private final Function<String, String> detailCheck;
    private final List<Map<String, Object>> records;
    private final WayCheck check;
    private final List<Map<String, Object>> sampled;
    private final List<String> unread = new ArrayList<>();
    // The user's answers of which key of each old/new list holds the value before the change, by the list.
    private final Map<String, String> changeSides;
    // The build's existing mapping's rules, whose names a rule it already has keeps.
    private final List<Map<String, Object>> existingRules;
    // The build's existing mapping's own drop_when entries (not the catalogue's, nor a format's), kept as they are.
    private final List<Map<String, Object>> existingDrops;
    // The names no rule new to the build is given: those kept, and the catch-all's.
    private final Set<String> reserved = new HashSet<>();
    // The catch-all's name: the existing mapping's, else the default.
    private String catchAllName = CATCH_ALL_NAME;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private CatalogueTranslation(final Map<String, Object> mapping,
                                 final List<String> names,
                                 final Map<String, List<String>> values,
                                 final Catalogue catalogue,
                                 final List<Map<String, Object>> detailFields,
                                 final Function<String, String> detailCheck,
                                 final List<Map<String, Object>> records,
                                 final PairMatches matches,
                                 final Map<String, String> changeSides,
                                 final List<Map<String, Object>> existingRules,
                                 final List<Map<String, Object>> existingDrops) {
        this.mapping = mapping;
        this.changeSides = Map.copyOf(changeSides);
        this.existingRules = NullSafe.list(existingRules);
        this.existingDrops = NullSafe.list(existingDrops);
        this.names = new ArrayList<>(names);
        this.values = new LinkedHashMap<>(values);
        this.catalogue = catalogue;
        this.detailFields = detailFields;
        this.detailCheck = detailCheck;
        this.records = NullSafe.list(records);
        this.check = WayCheck.of(mapping, matches);
        this.sampled = JsonValues.maps(mapping.get("events")).stream()
                .filter(rule -> JsonValues.truthy(rule.get(WHEN)))
                .toList();
    }

    /// Replaces the draft's rules with the catalogue's, in place.
    ///
    /// @param mapping        The draft's mapping: `common`, `events` (the draft's own rules, whose fields a way's
    ///                       rule starts from), `extract`, `drop_when`. Changed in place.
    /// @param names          The records' fields.
    /// @param values         Each field's values.
    /// @param catalogue      The catalogue.
    /// @param catchAllReason The catch-all's agreement (allow_unknown), or null when it isn't agreed yet.
    /// @param detailFields   The source notes' entries placing fields under an action element.
    /// @param detailCheck    Why an `event_detail` can't be an action element, or null.
    /// @param records        The sample's records, as the draft reads them.
    /// @param matches        The call's regex matches, each regex's first in a text found once.
    /// @param changeSides    The user's answers of which key of each old/new list holds the value before the change,
    ///                       by the list ([SourceNotes#CHANGE_SIDES]); none where none were given.
    /// @param existingRules  The build's existing mapping's `events` rules ([SourceNotes#EXISTING_RULES]): a rule
    ///                       the build already has keeps its name there ([KeptRuleNames]); none for a new build.
    /// @return What was done: `events` (each rule, said), `not_in_sample` (TypeIds no sampled record has),
    /// `uncatalogued` (how many sampled records no event type recognises), `dropped` and `unread_fields`.
    @SuppressWarnings("checkstyle:ParameterNumber")
    static Map<String, Object> apply(final Map<String, Object> mapping,
                                     final List<String> names,
                                     final Map<String, List<String>> values,
                                     final Catalogue catalogue,
                                     final String catchAllReason,
                                     final List<Map<String, Object>> detailFields,
                                     final Function<String, String> detailCheck,
                                     final List<Map<String, Object>> records,
                                     final PairMatches matches,
                                     final Map<String, String> changeSides,
                                     final List<Map<String, Object>> existingRules) {
        return apply(mapping, names, values, catalogue, catchAllReason, detailFields, detailCheck, records, matches,
                changeSides, existingRules, List.of());
    }

    /// Replaces the draft's rules with the catalogue's, in place, as
    /// [#apply(Map, List, Map, Catalogue, String, List, Function, List, PairMatches, Map, List)] does, keeping the
    /// build's existing mapping's own drops: its `drop_when` entries given, and its `events` rules with `drop: true`
    /// (among existingRules), each in its place and order. Found in review (2026-11-26): a fresh draft from the
    /// catalogue lost them, so processing changed between drafts with no one told.
    ///
    /// @param mapping        The draft's mapping, changed in place.
    /// @param names          The records' fields.
    /// @param values         Each field's values.
    /// @param catalogue      The catalogue.
    /// @param catchAllReason Why the catch-all writes Unknown, or null.
    /// @param detailFields   The notes' action element entries.
    /// @param detailCheck    The schema's check of an action element.
    /// @param records        The sample's records, as the draft reads them.
    /// @param matches        The call's regex matches.
    /// @param changeSides    The user's answers of which key of each old/new list holds the value before.
    /// @param existingRules  The build's existing mapping's `events` rules; none for a new build.
    /// @param existingDrops  The build's existing mapping's own `drop_when` entries ([SourceNotes#EXISTING_DROPS]);
    ///                       none for a new build.
    /// @return What was done, as the other form says, and `kept_drops`: the existing drops kept, said.
    @SuppressWarnings("checkstyle:ParameterNumber")
    static Map<String, Object> apply(final Map<String, Object> mapping,
                                     final List<String> names,
                                     final Map<String, List<String>> values,
                                     final Catalogue catalogue,
                                     final String catchAllReason,
                                     final List<Map<String, Object>> detailFields,
                                     final Function<String, String> detailCheck,
                                     final List<Map<String, Object>> records,
                                     final PairMatches matches,
                                     final Map<String, String> changeSides,
                                     final List<Map<String, Object>> existingRules,
                                     final List<Map<String, Object>> existingDrops) {
        return new CatalogueTranslation(mapping, names, values, catalogue, detailFields, detailCheck, records,
                matches, changeSides, existingRules, existingDrops)
                .apply(catchAllReason);
    }

    /// @param fieldName A field's name, as conditions name it.
    /// @param jsonKeys  Whether it is a key of JSON (the JSON input's, or JSON a field holds): its dots separate keys.
    /// @return The Data name the vendor's code in it is written under: its last segment (`System/EventID` is
    /// `EventID`, `event.logger_name` is `logger_name`, `@id` is `id`; a key in brackets as written). A Data
    /// Splitter's field is named as it is.
    static String vendorCodeName(final String fieldName, final boolean jsonKeys) {
        return VendorCode.dataName(fieldName, jsonKeys);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apply(final String catchAllReason) {
        final List<Map<String, Object>> common = (List<Map<String, Object>>) mapping.computeIfAbsent("common",
                key -> new ArrayList<>());
        // Each rule writes its own TypeId: not one read from a field for every record.
        common.removeIf(entry -> TYPE_ID.equals(entry.get(PATH)));
        captured();
        // The fields empty in every sampled record, each said once among those not read.
        List.copyOf(names).forEach(this::emptyEverywhere);
        final List<String> rest = rest(common);
        final List<Map<String, Object>> rules = new ArrayList<>();
        final List<String> keptDrops = new ArrayList<>();
        final List<Map<String, Object>> drops = withExistingDrops(JsonValues.maps(mapping.get("drop_when")),
                keptDrops);
        // The draft's events rules with drop: true, kept ahead of the catch-all (the events list is replaced): they
        // drop the records no event type recognises, as they did, so the catch-all's count of what the mapping's
        // drop rules drop is true (seen: a draft's drop: true rule was discarded, yet its records were said to be
        // dropped, and the applied draft wrote them as Unknown).
        final List<Map<String, Object>> keptDropRules = dropRules(JsonValues.maps(mapping.get("events")));
        // The existing mapping's own drop: true rules the draft doesn't have, each kept where it was.
        final List<Map.Entry<Integer, Map<String, Object>>> existingDropRules = existingDropRules(keptDropRules);
        // The mapping's own drop rules (the draft's), before the catalogue's are added: the uncatalogued records
        // they drop are counted apart from those the catch-all writes as Unknown.
        final List<List<Condition>> ownDrops = Stream.of(drops.stream(), keptDropRules.stream(),
                        existingDropRules.stream().map(Map.Entry::getValue))
                .flatMap(Function.identity())
                .map(drop -> WayCheck.conditions(drop.get(WHEN)))
                .filter(when -> when != null && !when.isEmpty())
                .toList();
        final List<String> said = new ArrayList<>();
        final List<String> notInSample = new ArrayList<>();
        final List<String> dropped = new ArrayList<>();
        final Set<String> ruleNames = new LinkedHashSet<>();
        final Set<Map<String, Object>> recognised = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean sole = false;
        final Set<String> seenTypes = new LinkedHashSet<>();
        final List<CatalogueOrder.WayOf> ways = ways();
        final List<CatalogueOrder.WayOf> written = ways.stream().filter(wayOf -> !wayOf.type().drop()).toList();
        final KeptRuleNames kept = keptNames(written);
        for (final CatalogueOrder.WayOf wayOf : ways) {
            final EventType type = wayOf.type();
            final String typeId = typeId(type);
            final List<Map<String, Object>> own = check.recognised(wayOf.way().when(), records);
            recognised.addAll(own);
            if (!own.isEmpty()) {
                seenTypes.add(typeId);
            }
            if (type.drop()) {
                if (!wayOf.way().when().isEmpty()) {
                    final Map<String, Object> drop = new LinkedHashMap<>();
                    drop.put("reason", SourceNotes.DROPPED_REASON + ": " + typeId);
                    drop.put(WHEN, whenJson(wayOf.way()));
                    if (!drops.contains(drop)) {
                        drops.add(drop);
                    }
                    dropped.add(typeId);
                }
                continue;
            }
            final Map<String, Object> rule = rule(type, typeId, wayOf.way(), own, common, rest);
            rule.put(NAME, name(kept.nameOf(written.indexOf(wayOf)), typeId, wayOf, ruleNames));
            if (wayOf.way().when().isEmpty()) {
                sole = true;
                rule.remove(WHEN);
            }
            rules.add(rule);
            said.add(typeId + " (" + (wayOf.way().when().isEmpty()
                    ? "every record"
                    : String.join(" and ", wayOf.way().when().stream().map(MappingFacts::conditionWords).toList()))
                     + ") -> " + detail(type));
        }
        for (final EventType type : catalogue.eventTypes()) {
            final String typeId = typeId(type);
            if (!type.drop() && !seenTypes.contains(typeId) && !records.isEmpty()) {
                notInSample.add(typeId);
            }
        }
        if (!sole) {
            // After every event type's rules: only what none recognises is dropped, as before the catch-all.
            keptDropRules.forEach(rule -> {
                rule.put(NAME, unique(ToolText.str(rule.get(NAME)), ruleNames));
                rules.add(rule);
            });
        }
        for (final Map.Entry<Integer, Map<String, Object>> existing : existingDropRules) {
            // In its place among the rules (before a sole rule taking every record, which would leave it unreached).
            final Map<String, Object> rule = new LinkedHashMap<>(existing.getValue());
            rule.put(NAME, unique(ToolText.str(rule.get(NAME)), ruleNames));
            rules.add(Math.min(existing.getKey(), sole
                    ? Math.max(0, rules.size() - 1)
                    : rules.size()), rule);
            keptDrops.add("rule '" + rule.get(NAME) + "' (drop: true)");
        }
        if (!sole) {
            rules.add(catchAll(common, catchAllReason));
        }
        mapping.put("events", rules);
        if (drops.isEmpty()) {
            mapping.remove("drop_when");
        } else {
            mapping.put("drop_when", drops);
        }
        final Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("events", said);
        applied.put("not_in_sample", notInSample);
        final List<Map<String, Object>> uncatalogued = sole
                ? List.of()
                : records.stream().filter(record -> !recognised.contains(record)).toList();
        final int droppedByMapping = (int) uncatalogued.stream()
                .filter(record -> ownDrops.stream().anyMatch(when -> check.recognises(when, record)))
                .count();
        applied.put("uncatalogued", uncatalogued.size() - droppedByMapping);
        if (droppedByMapping > 0) {
            applied.put("uncatalogued_dropped", droppedByMapping);
        }
        applied.put("dropped", dropped);
        applied.put("unread_fields", unread);
        if (!keptDrops.isEmpty()) {
            applied.put("kept_drops", keptDrops);
        }
        return applied;
    }

    // The draft's drop_when with the existing mapping's own drops: in the existing order, each of the draft's that
    // matches an existing one (by its reason or conditions) in that one's place, then the draft's others.
    private List<Map<String, Object>> withExistingDrops(final List<Map<String, Object>> fresh,
                                                        final List<String> kept) {
        if (existingDrops.isEmpty()) {
            return new ArrayList<>(fresh);
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> existing : existingDrops) {
            final Map<String, Object> same = fresh.stream()
                    .filter(drop -> sameDrop(drop, existing))
                    .findFirst()
                    .orElse(null);
            if (same == null) {
                out.add(new LinkedHashMap<>(existing));
                kept.add("drop_when '" + ToolText.str(existing.get("reason")) + "'");
            } else if (!out.contains(same)) {
                out.add(same);
            }
        }
        fresh.stream().filter(drop -> !out.contains(drop)).forEach(out::add);
        return out;
    }

    private static boolean sameDrop(final Map<String, Object> one, final Map<String, Object> other) {
        return Objects.equals(one.get("reason"), other.get("reason")) || Objects.equals(one.get(WHEN),
                other.get(WHEN));
    }

    // The existing mapping's drop: true rules (with conditions) that none of the draft's has the conditions of, each
    // with its position among the existing rules.
    private List<Map.Entry<Integer, Map<String, Object>>> existingDropRules(
            final List<Map<String, Object>> fresh) {
        final List<Map.Entry<Integer, Map<String, Object>>> out = new ArrayList<>();
        for (int n = 0; n < existingRules.size(); n++) {
            final Map<String, Object> rule = existingRules.get(n);
            if (JsonValues.truthy(rule.get("drop")) && JsonValues.truthy(rule.get(WHEN))
                && fresh.stream().noneMatch(own -> Objects.equals(own.get(WHEN), rule.get(WHEN)))) {
                out.add(Map.entry(n, rule));
            }
        }
        return out;
    }

    // The rules of an events list that drop their records (drop: true) by conditions that read, each a copy; one
    // with no conditions (it would take every record) is left out, as the catalogue's rules settle those records.
    private static List<Map<String, Object>> dropRules(final List<Map<String, Object>> events) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> rule : events) {
            final List<Condition> when = JsonValues.truthy(rule.get("drop"))
                    ? WayCheck.conditions(rule.get(WHEN))
                    : null;
            if (when != null && !when.isEmpty()) {
                final Map<String, Object> kept = new LinkedHashMap<>(rule);
                if (NullSafe.isBlankString(ToolText.str(kept.get(NAME)))) {
                    kept.put(NAME, "drop");
                }
                out.add(kept);
            }
        }
        return out;
    }

    // Every way of recognising the event types, in the catalogue's order, then a sole event type's (recognised by
    // every record: no way given), which takes every record, so last.
    private List<CatalogueOrder.WayOf> ways() {
        final List<CatalogueOrder.WayOf> ways = new ArrayList<>(CatalogueOrder.ways(catalogue));
        catalogue.eventTypes().stream()
                .filter(type -> type.recognisedBy().isEmpty())
                .forEach(type -> ways.add(new CatalogueOrder.WayOf(type, 0, new Way(List.of(), List.of()))));
        return ways;
    }

    // One rule for one way of recognising an event type: the draft's own rule that took the way's records first, so
    // what the draft read stays (asked for by the user; seen in the agent eval, a catalogue rule wrote one fixed
    // description over each record's message, and lost the severity, names and addresses the sample's rule had),
    // then what the catalogue says over it.
    private Map<String, Object> rule(final EventType type,
                                     final String typeId,
                                     final Way way,
                                     final List<Map<String, Object>> own,
                                     final List<Map<String, Object>> common,
                                     final List<String> rest) {
        final String detail = detail(type);
        final String prefix = EVENT_DETAIL + detail + "/";
        final List<Map<String, Object>> fields = new ArrayList<>();
        final Map<String, Object> like = covering(own);
        if (like != null) {
            for (final Map<String, Object> field : JsonValues.maps(like.get(FIELDS))) {
                final String path = ToolText.str(field.get(PATH));
                if (TYPE_ID.equals(path)) {
                    continue;
                }
                if (field.get(FIELD) instanceof final String read && emptyEverywhere(read)
                    && !placedByType(type, read, heldJson())) {
                    // Empty in every sampled record: nothing to write (seen in chat 182, a time field empty in all
                    // 1,547 records drafted into several rules, each then warned of and removed by hand).
                    continue;
                }
                if (path.startsWith(EVENT_DETAIL) && path.chars().filter(c -> c == '/').count() >= 2
                    && !path.startsWith(prefix)) {
                    // Under another action element (Unknown, or the draft's guess): its Data move to the event type's;
                    // its other children are that element's, not this one's.
                    if (path.endsWith("/Data") && JsonValues.truthy(field.get(DATA_NAME))) {
                        final Map<String, Object> moved = new LinkedHashMap<>(field);
                        moved.put(PATH, prefix + "Data");
                        fields.add(moved);
                    }
                    continue;
                }
                fields.add(new LinkedHashMap<>(field));
            }
        }
        fields.addFirst(entry(PATH, TYPE_ID, VALUE, typeId));
        description(type, own, common, fields);
        if (NullSafe.isNonBlankString(type.action())) {
            put(fields, entry(PATH, prefix + "Action", VALUE, type.action()));
        }
        success(type, prefix, fields);
        // Who acted, where the catalogue says: only a field of the records, and not one holding addresses (seen in
        // the eval: a firewall's source address given as the actor of records naming no user).
        final String actor = person(type.actor());
        if (actor != null) {
            put(fields, entry(PATH, "EventSource/User/Id", FIELD, actor));
        } else if (NullSafe.isNonBlankString(type.actor())) {
            unread.add(typeId + ": actor '" + type.actor() + "'" + (field(type.actor()) == null
                    ? " isn't a field of the records"
                    : " holds addresses, not a user"));
        }
        final Set<String> placed = placements(type, typeId, detail, fields, pathsOf(common));
        final String user = actor != null
                ? actor
                : common.stream()
                        .filter(entry -> "EventSource/User/Id".equals(entry.get(PATH)) && JsonValues.truthy(
                                entry.get(FIELD)))
                        .map(entry -> ToolText.str(entry.get(FIELD)))
                        .findFirst()
                        .orElse(null);
        if ("Authenticate".equals(detail) && user != null && !hasPath(fields, "EventDetail/Authenticate/User/Id")
            && fields.stream().map(field -> ToolText.str(field.get(PATH))).noneMatch(path -> Stream.of("User/",
                "Device/", "Group/").anyMatch(object -> path.startsWith(prefix + object)))) {
            // Unless a placement gives who (or what) authenticated: one of the schema's choice.
            // The schema wants the user authenticating: the one the event is about.
            fields.add(entry(PATH, "EventDetail/Authenticate/User/Id", FIELD, user));
        }
        fields.addAll(detailFields.stream()
                .filter(field -> ToolText.str(field.get(PATH)).startsWith(prefix)
                                 && !hasPath(fields, ToolText.str(field.get(PATH))))
                .toList());
        // The vendor's code, as Data named after its field, so searching by it still works.
        final Set<String> read = readBy(fields);
        read.addAll(placed);
        for (final String code : vendorCodeFields(way)) {
            if (placed.contains(code)) {
                // Placed (written to an element, not also_data): the element holds it, searched there.
                continue;
            }
            final String dataName = vendorCodeName(code, "json".equals(mapping.get("input"))
                                                          || MappingFacts.heldJsonField(
                    JsonValues.strings(mapping.get("json_fields")), code) != null);
            fields.removeIf(field -> code.equals(field.get(FIELD)) && JsonValues.truthy(field.get(DATA_NAME))
                                     && ToolText.str(field.get(PATH)).endsWith("/Data"));
            fields.add(entry(PATH, prefix + "Data", DATA_NAME, dataName, FIELD, code));
            read.add(code);
        }
        // The fields left over that the way's own records hold (all of them where the sample has none), and the
        // names its pattern's groups read, as Data: not one the rule already reads.
        final List<String> leftOver = new ArrayList<>(heldBy(own, rest));
        way.names().stream().filter(name -> !leftOver.contains(name)).forEach(leftOver::add);
        for (final String name : leftOver) {
            if (!read.contains(name) && !emptyEverywhere(name)) {
                fields.add(entry(PATH, prefix + "Data", DATA_NAME, name, FIELD, name));
                read.add(name);
            }
        }
        // A placed field the placement keeps as Data too (also_data), once.
        for (final Placement placement : type.allPlacements()) {
            final String kept = placement.field();
            if (placement.alsoData() && !UNKNOWN.equals(detail) && kept != null && field(kept) != null
                && fields.stream().noneMatch(field -> kept.equals(field.get(FIELD)) && field.get(DATA_NAME) != null)) {
                fields.add(entry(PATH, prefix + "Data", DATA_NAME, kept, FIELD, kept));
            }
        }
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put(NAME, typeId);
        rule.put(WHEN, whenJson(way));
        rule.put(FIELDS, fields);
        // Unknown is not agreed here: build_translation_xslt puts it to the user with the records it catches.
        return rule;
    }

    // The catch-all's rule: Unknown, every field of the records not read in common kept as Data; or, when the
    // catalogue's catch-all has its own translation (a hand-written last rule writing a real event, read back by
    // rebuild_mapping), that event, the fields kept as Data of its action element.
    private Map<String, Object> catchAll(final List<Map<String, Object>> common, final String reason) {
        final Catalogue.CatchAll.Translation own = catalogue.catchAll().translation();
        final String detail = own == null || NullSafe.isBlankString(own.eventDetail())
                ? UNKNOWN
                : ToolText.strip(own.eventDetail().strip(), "/").split("/")[0];
        final String data = EVENT_DETAIL + detail + "/Data";
        final Set<String> inCommon = readBy(common);
        final List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(entry(PATH, TYPE_ID, VALUE, own == null || NullSafe.isBlankString(own.typeId())
                ? CATCH_ALL_TYPE_ID
                : own.typeId()));
        if (own != null && NullSafe.isNonBlankString(own.action())) {
            fields.add(entry(PATH, EVENT_DETAIL + detail + "/Action", VALUE, own.action()));
        }
        if (own != null && NullSafe.isNonBlankString(own.description())) {
            fields.add(entry(PATH, DESCRIPTION, VALUE, own.description()));
        }
        final int fixed = fields.size();
        for (final String name : names) {
            if (!inCommon.contains(name) && !NullSafe.isEmptyCollection(values.get(name)) && !emptyEverywhere(name)) {
                fields.add(entry(PATH, data, DATA_NAME, name, FIELD, name));
            }
        }
        if (fields.size() == fixed && !names.isEmpty() && UNKNOWN.equals(detail)) {
            // Every field read in common: an Unknown with no Data has no action element's content.
            fields.add(entry(PATH, data, DATA_NAME, names.getFirst(), FIELD, names.getFirst()));
        }
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put(NAME, catchAllName);
        rule.put(FIELDS, fields);
        if (reason != null && UNKNOWN.equals(detail)) {
            rule.put("allow_unknown", reason);
        }
        rule.put("catch_all", true);
        return rule;
    }

    // The event type's typed placements ([Placement]), `affected`'s included, as entries of the rule in place of any
    // with their paths; the entries of other sources reading a placed field below the action element (the draft's
    // Data of it, say) taken out, as each field is written once (kept as Data too only where the placement says
    // also_data). A placement reading a field the records haven't is left out, said. The fields placed (not also
    // kept as Data) are returned.
    private Set<String> placements(final EventType type,
                                   final String typeId,
                                   final String detail,
                                   final List<Map<String, Object>> fields,
                                   final Set<String> commonPaths) {
        final Set<String> placed = new LinkedHashSet<>();
        if (UNKNOWN.equals(detail)) {
            return placed;
        }
        final String prefix = EVENT_DETAIL + detail + "/";
        final Set<Map<String, Object>> written = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Placement placement : type.allPlacements()) {
            if (!placement.inEventSource() && placement.pathBelow(detail) == null) {
                continue;
            }
            if (placement.inEventSource() && placement.writesFromRoot(detail).stream()
                    .anyMatch(commonPaths::contains)) {
                // The common entries write it for every event (the catalogue's check refuses such a placement).
                unread.add(typeId + ": placement '" + placement.path() + "' is written by the common entries for "
                           + "every event, so left out");
                continue;
            }
            if (placement.field() != null && field(placement.field()) == null) {
                unread.add(typeId + ": " + (type.placements().contains(placement)
                        ? "placement '" + placement.path() + "' reads '" + placement.field() + "', which"
                        : "affected '" + placement.field() + "'") + " isn't a field of the records");
                continue;
            }
            final Placement paired = withPairKeys(placement);
            if (paired == null) {
                unread.add(typeId + ": placement '" + placement.path() + "' reads a pair of '"
                           + placement.pair().get("list") + "', which isn't a name/value list of the records");
                continue;
            }
            final Placement keyed = withChangeKeys(paired);
            if (keyed == null) {
                unread.add(typeId + ": placement '" + placement.path() + "' reads a change of "
                           + String.join(", ", placement.changeLists()) + ", which isn't an old/new list of the "
                           + "records");
                continue;
            }
            // A change is two entries, its Before snapshot's and its After's; one only where the event type places
            // the other snapshot's object itself (each snapshot's object as its own placement says).
            final List<Map<String, Object>> entries = new ArrayList<>(keyed.mappingEntries(detail));
            if (entries.size() > 1 && otherSidePlaced(type, keyed, detail)) {
                entries.removeIf(entry -> !keyed.fullPath(detail).equals(entry.get(PATH)));
            }
            final Set<String> paths = new HashSet<>();
            entries.forEach(entry -> paths.add(ToolText.str(entry.get(PATH))));
            // What it reads, an xpath's fields too (seen in testing: a key of JSON held in a field, placed by an
            // xpath, kept as Data as well).
            final Set<String> inputs = placement.reads(heldJson());
            // Another placement of the same field is kept: a field may be typed in two places.
            fields.removeIf(existing -> {
                final String path = ToolText.str(existing.get(PATH));
                return paths.contains(path)
                       || !written.contains(existing) && path.startsWith(prefix)
                          && existing.get(FIELD) instanceof final String read && inputs.contains(read)
                          && !(placement.alsoData() && path.endsWith("/Data"));
            });
            fields.addAll(entries);
            written.addAll(entries);
            if (keyed.pair() != null && placement.alsoData()) {
                // The pair kept as Data too, named by its name: the list's Data per pair leaves it out.
                fields.add(entry(PATH, prefix + "Data", DATA_NAME, ToolText.str(keyed.pair().get(NAME)), "pair",
                        keyed.pair()));
            }
            if (!placement.alsoData()) {
                placed.addAll(inputs);
            }
        }
        return placed;
    }

    // Whether a placement of both snapshots names its own (`After/User`), and another of the event type's places the
    // same object over the same items in the other (`Before/User`): seen in eval case 76, a model placing each
    // snapshot's targets apart, each then written in both, the second's fields over the first's.
    private static boolean otherSidePlaced(final EventType type, final Placement placement, final String detail) {
        final String side = sideGiven(placement, detail);
        if (side == null) {
            return false;
        }
        final String object = placement.snapshotPath(detail);
        final Object array = JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH)).get("array");
        return type.allPlacements().stream()
                .filter(other -> other != placement)
                .anyMatch(other -> {
                    final String otherSide = sideGiven(other, detail);
                    return otherSide != null && !otherSide.equals(side)
                           && object.equals(other.snapshotPath(detail))
                           && Objects.equals(array, JsonValues.mapOrEmpty(other.entry().get(Placement.FOR_EACH))
                                   .get("array"));
                });
    }

    // The snapshot a placement's path names first below the action element (`Before`, `After`), or null.
    private static String sideGiven(final Placement placement, final String detail) {
        final String below = placement.pathBelow(detail);
        if (below == null) {
            return null;
        }
        final String first = below.split("/", 2)[0];
        return ListChange.BEFORE.equals(first) || ListChange.AFTER.equals(first)
                ? first
                : null;
    }

    // A placement with its pair's keys as the records' list has them (the mapping's entry names them); as it is
    // without a pair; null where the list isn't a name/value list of the records.
    private Placement withPairKeys(final Placement placement) {
        if (placement.pair() == null) {
            return placement;
        }
        final String valueKey = placement.pair().get("value_key") instanceof final String given
                ? given
                : null;
        return NameValueLists.forPair(values.getOrDefault(ToolText.str(placement.pair().get("list")), List.of()),
                        ToolText.str(placement.pair().get(NAME)), valueKey)
                .map(pairs -> placement.withPairKeys(pairs.nameKey(), pairs.valueKey()))
                .orElse(null);
    }

    // A placement with its changes' keys as the records' lists have them (the mapping's entries name them; a key
    // given kept); as it is without a change; null where a list isn't an old/new list of the records (a group's, a
    // key of its array's items).
    private Placement withChangeKeys(final Placement placement) {
        if (!placement.snapshots()) {
            return placement;
        }
        final String array = placement.group()
                ? ToolText.str(JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH)).get("array"))
                : null;
        final Map<String, List<String>> keys = new LinkedHashMap<>();
        for (final String list : placement.changeLists()) {
            final List<String> held = values.getOrDefault(array == null
                    ? list
                    : array, List.of());
            final Optional<ChangeList> found = NameValueLists.changes(array == null
                    ? held
                    : NameValueLists.heldIn(held, list));
            if (found.isEmpty()) {
                return null;
            }
            final ChangeList sided = found.get().oriented(changeSides.get(array == null
                    ? list
                    : NameValueLists.nestedName(array, list)));
            keys.put(list, List.of(sided.nameKey(), sided.oldKey(), sided.newKey()));
        }
        return placement.withChangeKeys(keys);
    }

    // The paths the entries write.
    private static Set<String> pathsOf(final List<Map<String, Object>> entries) {
        final Set<String> out = new HashSet<>();
        entries.forEach(entry -> out.add(ToolText.strip(ToolText.str(entry.get(PATH)), "/")));
        return out;
    }

    // Whether a placement of the event type names the field.
    private static boolean placedByType(final EventType type, final String field, final List<String> heldJson) {
        return type.allPlacements().stream().anyMatch(placement -> placement.reads(heldJson).contains(field));
    }

    // The mapping's json_fields: fields holding JSON as text, whose keys an xpath reads only through json-to-xml.
    private List<String> heldJson() {
        return JsonValues.strings(mapping.get("json_fields"));
    }

    // Whether the field is empty in every sampled record (no value, or only the mapping's nil_values): left out of the
    // rules, said once among the fields not read (asked for by the user 2026-11-21, chat 182).
    private boolean emptyEverywhere(final String name) {
        if (!values.containsKey(name)) {
            // A name an extraction gives: its values aren't known here.
            return false;
        }
        // A value the mapping reads as none is no value: its nil_values, and JSON's null (which the draft's records
        // hold as the text null, and processing reads as no value).
        final Set<String> nil = new LinkedHashSet<>(JsonValues.strings(mapping.get("nil_values")));
        if ("json".equals(mapping.get("input"))) {
            nil.add("null");
        }
        final Predicate<Object> held = value -> hasText(value) && !nil.contains(ToolText.strip(ToolText.str(value)));
        final boolean empty = values.get(name).stream().noneMatch(held)
                              && records.stream().noneMatch(record -> held.test(record.get(name)));
        if (empty) {
            final String said = name + ": " + EMPTY_EVERYWHERE;
            if (!unread.contains(said)) {
                unread.add(said);
            }
        }
        return empty;
    }

    // The Description: the records' own message where they carry one (in common, the covering rule, or a field of
    // free text a person wrote), alone; else the event type's description as fixed text.
    private void description(final EventType type,
                             final List<Map<String, Object>> own,
                             final List<Map<String, Object>> common,
                             final List<Map<String, Object>> fields) {
        final Optional<Map<String, Object>> ownMessage = Stream.concat(fields.stream(), common.stream())
                .filter(entry -> DESCRIPTION.equals(entry.get(PATH))
                                 && (JsonValues.truthy(entry.get(FIELD)) || JsonValues.truthy(entry.get("xpath"))
                                     || JsonValues.truthy(entry.get("any_of"))))
                .findFirst();
        if (ownMessage.isPresent()) {
            messageElseOwnText(type, own, ownMessage.get(), fields);
            return;
        }
        // A field of free text a person wrote about each event (a ticket's comment) is the event's message before
        // the catalogue's words for it (seen in the agent eval: every ticket event described as 'A ticket is
        // closed', its comment kept as Data).
        final String freeText = names.stream()
                .filter(name -> DraftMapping.FREE_TEXT.matcher(name).matches()
                                && !NullSafe.isEmptyCollection(values.get(name))
                                && (own.isEmpty() || own.stream().anyMatch(record -> hasText(record.get(name)))))
                .findFirst()
                .orElse(null);
        if (freeText != null) {
            final Map<String, Object> message = fields.stream()
                    .filter(field -> freeText.equals(field.get(FIELD)))
                    .findFirst()
                    .map(field -> (Map<String, Object>) new LinkedHashMap<>(field))
                    .orElseGet(() -> entry(FIELD, freeText));
            fields.removeIf(field -> freeText.equals(field.get(FIELD)));
            message.put(PATH, DESCRIPTION);
            message.remove(DATA_NAME);
            put(fields, message);
        } else if (NullSafe.isNonBlankString(type.description())) {
            put(fields, entry(PATH, DESCRIPTION, VALUE, type.description()));
        }
    }

    // The message read from a field that not every record of the way holds (seen in eval case 47: FortiGate's msg,
    // in common, held by none of the traffic records, which had no Description at all): the type's own text where
    // none of them holds it, else the message with the type's text as its default.
    private static void messageElseOwnText(final EventType type,
                                           final List<Map<String, Object>> own,
                                           final Map<String, Object> message,
                                           final List<Map<String, Object>> fields) {
        if (!(message.get(FIELD) instanceof final String field) || own.isEmpty()
            || NullSafe.isBlankString(type.description())
            || own.stream().allMatch(record -> hasText(record.get(field)))) {
            return;
        }
        if (own.stream().noneMatch(record -> hasText(record.get(field)))) {
            put(fields, entry(PATH, DESCRIPTION, VALUE, type.description()));
        } else {
            final Map<String, Object> withDefault = new LinkedHashMap<>(message);
            withDefault.put("default", type.description());
            put(fields, withDefault);
        }
    }

    // The outcome: true or false as given, else a field of the records holding it.
    private void success(final EventType type, final String prefix, final List<Map<String, Object>> fields) {
        final String success = type.success();
        if (success == null || success.isBlank()) {
            return;
        }
        final String path = prefix + "Outcome/Success";
        if ("true".equalsIgnoreCase(success) || "false".equalsIgnoreCase(success)) {
            put(fields, entry(PATH, path, VALUE, success.toLowerCase(Locale.ROOT)));
        } else if (field(success) != null) {
            fields.removeIf(field -> path.equals(field.get(PATH)));
            fields.add(entry(PATH, path, FIELD, success));
        }
    }

    // The draft's own rule that took most of the way's records; null when none did.
    private Map<String, Object> covering(final List<Map<String, Object>> own) {
        final Map<Map<String, Object>, Integer> taken = new LinkedHashMap<>();
        for (final Map<String, Object> record : own) {
            for (final Map<String, Object> rule : sampled) {
                final List<Condition> when = WayCheck.conditions(rule.get(WHEN));
                if (when != null && check.recognises(when, record)) {
                    taken.merge(rule, 1, Integer::sum);
                    break;
                }
            }
        }
        Map<String, Object> best = null;
        int most = 0;
        for (final Map.Entry<Map<String, Object>, Integer> entry : taken.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    // The fields of the rest the records hold: all of them when there are none.
    private static List<String> heldBy(final List<Map<String, Object>> own, final List<String> rest) {
        if (own.isEmpty()) {
            return rest;
        }
        return rest.stream().filter(name -> own.stream().anyMatch(record -> hasText(record.get(name)))).toList();
    }

    // The fields no common entry nor the notes' action element entries read, with values (a way's own code fields
    // are written as the vendor's code, and not again).
    private List<String> rest(final List<Map<String, Object>> common) {
        final Set<String> taken = readBy(common);
        detailFields.forEach(entry -> taken.add(ToolText.str(entry.get(FIELD))));
        return names.stream()
                .filter(name -> !taken.contains(name) && !NullSafe.isEmptyCollection(values.get(name)))
                .toList();
    }

    // The fields a way tests for a code (equals, one_of; not a not), but the source type field.
    private List<String> vendorCodeFields(final Way way) {
        return VendorCode.fields(way, catalogue);
    }

    // The fields pattern conditions' capture groups name: an extract entry each (once), so the rules can read them.
    @SuppressWarnings("unchecked")
    private void captured() {
        for (final EventType type : catalogue.eventTypes()) {
            for (final Way way : type.recognisedBy()) {
                final Condition pattern = way.when().stream()
                        .filter(condition -> condition.matches() != null && condition.field() != null)
                        .findFirst()
                        .orElse(null);
                if (pattern == null || way.names().isEmpty()) {
                    continue;
                }
                final Map<String, Object> extract = entry(FIELD, pattern.field(), "regex", pattern.matches(),
                        "names", new ArrayList<>(way.names()));
                final List<Object> extracts = (List<Object>) mapping.computeIfAbsent("extract",
                        key -> new ArrayList<>());
                if (!extracts.contains(extract)) {
                    extracts.add(extract);
                }
                way.names().stream().filter(name -> !names.contains(name)).forEach(names::add);
            }
        }
    }

    // The action element: the event type's, unless the schema has no such element (then Unknown, said).
    private String detail(final EventType type) {
        String detail = ToolText.strip(NullSafe.nonBlankStringElse(type.eventDetail(), UNKNOWN).strip(), "/");
        if (detail.isEmpty()) {
            detail = UNKNOWN;
        }
        if (!UNKNOWN.equals(detail) && detailCheck != null && NullSafe.isNonEmptyString(detailCheck.apply(detail))) {
            return UNKNOWN;
        }
        return detail;
    }

    private static String typeId(final EventType type) {
        return NullSafe.isNonBlankString(type.typeId())
                ? type.typeId()
                : TypeIds.defaultFor(type);
    }

    /// A rule's name: its TypeId's words in snake case (SearchQuery, Search-Query: search_query), and for a second
    /// or later way of the type, what tells the way apart from the type's other ways (the values its equals and
    /// one_of conditions test that no other way of the type tests: search_query_qa), else its number
    /// (search_query_2), as also where that would be another rule's name. Seen in testing: a rule searchquery_2,
    /// named nowhere else, read as an event type of its own in the documentation.
    ///
    /// @param typeId The event type's TypeId.
    /// @param type   The event type.
    /// @param way    Which of the type's ways the rule is for, 0 the first.
    /// @param taken  The names of the rules named so far.
    /// @return The rule's name (not yet made unique among the rules).
    static String ruleName(final String typeId, final EventType type, final int way, final Set<String> taken) {
        final String name = slug(typeId);
        final String base = name.isEmpty()
                ? "event"
                : name;
        if (way == 0) {
            return base;
        }
        final String told = toldApartBy(type, way);
        final String named = base + "_" + told;
        return told.isEmpty() || taken.contains(named) || CATCH_ALL_NAME.equals(named)
                ? base + "_" + (way + 1)
                : named;
    }

    // What tells one of an event type's ways apart, as a name's part: the values its equals and one_of conditions
    // test that none of the type's other ways tests, in snake case and cut short; "" when there are none.
    private static String toldApartBy(final EventType type, final int way) {
        final List<Way> ways = type.recognisedBy();
        if (way < 0 || way >= ways.size()) {
            return "";
        }
        final List<String> values = new ArrayList<>();
        for (final Condition condition : ways.get(way).when()) {
            final boolean sharedWithAnother = ways.stream()
                    .filter(other -> other != ways.get(way))
                    .anyMatch(other -> other.when().contains(condition));
            if (condition.not() || sharedWithAnother) {
                continue;
            }
            if (condition.equalTo() != null) {
                values.add(condition.equalTo());
            } else if (condition.oneOf() != null) {
                values.addAll(condition.oneOf());
            }
        }
        final String told = slug(String.join(" ", values));
        return told.length() <= MAX_TOLD_APART
                ? told
                : ToolText.strip(told.substring(0, MAX_TOLD_APART), "_");
    }

    /// A text as a rule name's part: its words lower case, joined by _ (a lower case letter or digit followed by a
    /// capital, and the last of a run of capitals followed by a lower case letter, start a word).
    ///
    /// @param text The text, e.g. a TypeId (`Search-Query`, `SearchQuery`).
    /// @return Its words in snake case (`search_query`), "" if it has none.
    static String slug(final String text) {
        final String words = WORD_START.matcher(text).replaceAll("_");
        return ToolText.strip(NOT_NAME.matcher(words).replaceAll("_"), "_").toLowerCase(Locale.ROOT);
    }

    // The names the build's existing mapping gives the rules written, matched by what each is for; every one kept,
    // and the catch-all's, kept from the rules new to the build.
    private KeptRuleNames keptNames(final List<CatalogueOrder.WayOf> written) {
        reserved.add(CATCH_ALL_NAME);
        if (existingRules.isEmpty()) {
            return KeptRuleNames.none();
        }
        final KeptRuleNames kept = KeptRuleNames.of(existingRules, written.stream()
                .map(wayOf -> new KeptRuleNames.Wanted(typeId(wayOf.type()), wayOf.way().when()))
                .toList());
        if (kept.catchAll() != null) {
            catchAllName = kept.catchAll();
        }
        reserved.addAll(kept.all());
        return kept;
    }

    // A rule's name: the one the build's existing mapping gives it, where it has one no rule named so far has; else
    // as new to the build ([#ruleName]), never a name kept for another rule.
    private String name(final String kept,
                        final String typeId,
                        final CatalogueOrder.WayOf wayOf,
                        final Set<String> ruleNames) {
        if (kept != null && !ruleNames.contains(kept) && !kept.equals(catchAllName)) {
            ruleNames.add(kept);
            return kept;
        }
        final Set<String> taken = new HashSet<>(ruleNames);
        taken.addAll(reserved);
        return unique(ruleName(typeId, wayOf.type(), wayOf.index(), taken), ruleNames);
    }

    private String unique(final String name, final Set<String> taken) {
        String out = name;
        while (taken.contains(out) || reserved.contains(out) || catchAllName.equals(out)) {
            out = out + "_";
        }
        taken.add(out);
        return out;
    }

    private static List<Object> whenJson(final Way way) {
        return new ArrayList<>(way.when().stream().map(condition -> (Object) condition.toJson(DumpMode.COMPACT))
                .toList());
    }

    // The fields entries read: as a field, or as Data under that name, the first of any_of's, or by an xpath.
    private Set<String> readBy(final List<Map<String, Object>> entries) {
        final Set<String> read = new LinkedHashSet<>();
        for (final Map<String, Object> entry : entries) {
            if (TYPE_ID.equals(entry.get(PATH))) {
                continue;
            }
            if (entry.get(FIELD) != null) {
                read.add(ToolText.str(entry.get(FIELD)));
            }
            if (entry.get(DATA_NAME) != null) {
                read.add(ToolText.str(entry.get(DATA_NAME)));
            }
            JsonValues.strings(entry.get("any_of")).forEach(read::add);
            if (entry.get("xpath") instanceof final String xpath) {
                read.addAll(XPathFieldReads.fields(xpath, heldJson()));
            }
        }
        return read;
    }

    // The field, when the records have it; else null.
    private String field(final String name) {
        return name != null && !name.isBlank() && names.contains(name)
                ? name
                : null;
    }

    // The field, when the records have it and it names a person or account: not one whose values are addresses.
    private String person(final String name) {
        final String known = field(name);
        if (known == null) {
            return null;
        }
        final List<String> held = NullSafe.list(values.get(known));
        return !held.isEmpty() && held.stream().allMatch(IpAddresses::isAddress)
                ? null
                : known;
    }

    // An entry in place of any with its path (and Data name), where it was; else last.
    private static void put(final List<Map<String, Object>> fields, final Map<String, Object> entry) {
        for (int i = 0; i < fields.size(); i++) {
            if (Objects.equals(fields.get(i).get(PATH), entry.get(PATH))
                && Objects.equals(fields.get(i).get(DATA_NAME), entry.get(DATA_NAME))) {
                fields.set(i, entry);
                return;
            }
        }
        fields.add(entry);
    }

    private static boolean hasPath(final List<Map<String, Object>> fields, final String path) {
        return fields.stream().anyMatch(existing -> Objects.equals(existing.get(PATH), path));
    }

    private static boolean hasText(final Object value) {
        return value != null && !ToolText.isBlank(ToolText.str(value));
    }

    private static Map<String, Object> entry(final Object... keysAndValues) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            entry.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return entry;
    }
}
