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
import stroom.ai.impl.tools.catalogue.TypeIds;
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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

    private CatalogueTranslation(final Map<String, Object> mapping,
                                 final List<String> names,
                                 final Map<String, List<String>> values,
                                 final Catalogue catalogue,
                                 final List<Map<String, Object>> detailFields,
                                 final Function<String, String> detailCheck,
                                 final List<Map<String, Object>> records) {
        this.mapping = mapping;
        this.names = new ArrayList<>(names);
        this.values = new LinkedHashMap<>(values);
        this.catalogue = catalogue;
        this.detailFields = detailFields;
        this.detailCheck = detailCheck;
        this.records = NullSafe.list(records);
        this.check = WayCheck.of(mapping);
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
                                     final List<Map<String, Object>> records) {
        return new CatalogueTranslation(mapping, names, values, catalogue, detailFields, detailCheck, records)
                .apply(catchAllReason);
    }

    /// @param fieldName A field's name, as conditions name it.
    /// @param jsonKeys  Whether it is a key of JSON (the JSON input's, or JSON a field holds): its dots separate keys.
    /// @return The Data name the vendor's code in it is written under: its last segment (`System/EventID` is
    /// `EventID`, `event.logger_name` is `logger_name`, `@id` is `id`; a key in brackets as written). A Data
    /// Splitter's field is named as it is.
    static String vendorCodeName(final String fieldName, final boolean jsonKeys) {
        String last = fieldName;
        final int slash = last.lastIndexOf('/');
        if (slash >= 0 && !last.contains("[")) {
            last = last.substring(slash + 1);
        } else if (jsonKeys) {
            final List<String> keys = JsonFieldNames.keys(last);
            if (!keys.isEmpty()) {
                last = keys.getLast();
            }
        }
        return last.startsWith("@")
                ? last.substring(1)
                : last;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apply(final String catchAllReason) {
        final List<Map<String, Object>> common = (List<Map<String, Object>>) mapping.computeIfAbsent("common",
                key -> new ArrayList<>());
        // Each rule writes its own TypeId: not one read from a field for every record.
        common.removeIf(entry -> TYPE_ID.equals(entry.get(PATH)));
        captured();
        final List<String> rest = rest(common);
        final List<Map<String, Object>> rules = new ArrayList<>();
        final List<Map<String, Object>> drops = new ArrayList<>(JsonValues.maps(mapping.get("drop_when")));
        final List<String> said = new ArrayList<>();
        final List<String> notInSample = new ArrayList<>();
        final List<String> dropped = new ArrayList<>();
        final Set<String> ruleNames = new LinkedHashSet<>();
        final Set<Map<String, Object>> recognised = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean sole = false;
        final Set<String> seenTypes = new LinkedHashSet<>();
        for (final CatalogueOrder.WayOf wayOf : ways()) {
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
            rule.put(NAME, unique(ruleName(typeId, wayOf.index()), ruleNames));
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
        applied.put("uncatalogued", sole
                ? 0
                : (int) records.stream().filter(record -> !recognised.contains(record)).count());
        applied.put("dropped", dropped);
        applied.put("unread_fields", unread);
        return applied;
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
        final String affected = field(type.affected());
        if (affected != null && NullSafe.isNonBlankString(type.affectedAs())) {
            put(fields, entry(PATH, prefix + type.affectedAs(), FIELD, affected));
        } else if (NullSafe.isNonBlankString(type.affected()) && affected == null) {
            unread.add(typeId + ": affected '" + type.affected() + "' isn't a field of the records");
        }
        final String user = actor != null
                ? actor
                : common.stream()
                        .filter(entry -> "EventSource/User/Id".equals(entry.get(PATH)) && JsonValues.truthy(
                                entry.get(FIELD)))
                        .map(entry -> ToolText.str(entry.get(FIELD)))
                        .findFirst()
                        .orElse(null);
        if ("Authenticate".equals(detail) && user != null && !hasPath(fields, "EventDetail/Authenticate/User/Id")) {
            // The schema wants the user authenticating: the one the event is about.
            fields.add(entry(PATH, "EventDetail/Authenticate/User/Id", FIELD, user));
        }
        fields.addAll(detailFields.stream()
                .filter(field -> ToolText.str(field.get(PATH)).startsWith(prefix)
                                 && !hasPath(fields, ToolText.str(field.get(PATH))))
                .toList());
        // The vendor's code, as Data named after its field, so searching by it still works.
        final Set<String> read = readBy(fields);
        for (final String code : vendorCodeFields(way)) {
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
            if (!read.contains(name)) {
                fields.add(entry(PATH, prefix + "Data", DATA_NAME, name, FIELD, name));
                read.add(name);
            }
        }
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put(NAME, typeId);
        rule.put(WHEN, whenJson(way));
        rule.put(FIELDS, fields);
        // Unknown is not agreed here: build_translation_xslt puts it to the user with the records it catches.
        return rule;
    }

    // The catch-all's rule: Unknown, every field of the records not read in common kept as Data.
    private Map<String, Object> catchAll(final List<Map<String, Object>> common, final String reason) {
        final Set<String> inCommon = readBy(common);
        final List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(entry(PATH, TYPE_ID, VALUE, CATCH_ALL_TYPE_ID));
        for (final String name : names) {
            if (!inCommon.contains(name) && !NullSafe.isEmptyCollection(values.get(name))) {
                fields.add(entry(PATH, "EventDetail/Unknown/Data", DATA_NAME, name, FIELD, name));
            }
        }
        if (fields.size() == 1 && !names.isEmpty()) {
            // Every field read in common: an Unknown with no Data has no action element's content.
            fields.add(entry(PATH, "EventDetail/Unknown/Data", DATA_NAME, names.getFirst(), FIELD, names.getFirst()));
        }
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put(NAME, CATCH_ALL_NAME);
        rule.put(FIELDS, fields);
        if (reason != null) {
            rule.put("allow_unknown", reason);
        }
        rule.put("catch_all", true);
        return rule;
    }

    // The Description: the records' own message where they carry one (in common, the covering rule, or a field of
    // free text a person wrote), alone; else the event type's description as fixed text.
    private void description(final EventType type,
                             final List<Map<String, Object>> own,
                             final List<Map<String, Object>> common,
                             final List<Map<String, Object>> fields) {
        final boolean ownMessage = Stream.concat(common.stream(), fields.stream())
                .anyMatch(entry -> DESCRIPTION.equals(entry.get(PATH))
                                   && (JsonValues.truthy(entry.get(FIELD)) || JsonValues.truthy(entry.get("xpath"))
                                       || JsonValues.truthy(entry.get("any_of"))));
        if (ownMessage) {
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

    // The fields no common entry nor the notes' action element entries read, with values, but those the ways test
    // for a code (written as the vendor's code).
    private List<String> rest(final List<Map<String, Object>> common) {
        final Set<String> taken = readBy(common);
        detailFields.forEach(entry -> taken.add(ToolText.str(entry.get(FIELD))));
        final Set<String> codes = new LinkedHashSet<>();
        catalogue.eventTypes().forEach(type -> type.recognisedBy().forEach(way -> codes.addAll(vendorCodeFields(way))));
        return names.stream()
                .filter(name -> !taken.contains(name) && !codes.contains(name)
                                && !NullSafe.isEmptyCollection(values.get(name)))
                .toList();
    }

    // The fields a way tests for a code (equals, one_of; not a not), but the source type field.
    private List<String> vendorCodeFields(final Way way) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Condition condition : way.when()) {
            if (condition.field() != null && !condition.not() && condition.xpath() == null
                && (condition.equalTo() != null || condition.oneOf() != null) && !isSourceTypeField(condition.field())) {
                out.add(condition.field());
            }
        }
        return new ArrayList<>(out);
    }

    // The build's source type field, as its reading names it (`event>log_type`) or as a field (`event.log_type`).
    private boolean isSourceTypeField(final String fieldName) {
        final String reading = catalogue.scope().sourceTypeField();
        if (reading == null || reading.isBlank()) {
            return false;
        }
        return fieldName.equals(reading) || fieldName.equals(reading.replace('>', '.'))
               || fieldName.equals(reading.replace('>', '/'));
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
                : TypeIds.defaultTypeId(type);
    }

    private static String ruleName(final String typeId, final int way) {
        final String name = ToolText.strip(NOT_NAME.matcher(typeId).replaceAll("_"), "_").toLowerCase(Locale.ROOT);
        final String base = name.isEmpty()
                ? "event"
                : name;
        return way == 0
                ? base
                : base + "_" + (way + 1);
    }

    private static String unique(final String name, final Set<String> taken) {
        String out = name;
        while (taken.contains(out) || CATCH_ALL_NAME.equals(out)) {
            out = out + "_";
        }
        taken.add(out);
        return out;
    }

    private static List<Object> whenJson(final Way way) {
        return new ArrayList<>(way.when().stream().map(condition -> (Object) condition.toJson(DumpMode.COMPACT))
                .toList());
    }

    // The fields entries read: as a field, or as Data under that name.
    private static Set<String> readBy(final List<Map<String, Object>> entries) {
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
