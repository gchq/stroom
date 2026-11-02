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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.XmlElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// A CEF plan inferred from sample Events: ArcSight's standard keys first, then its custom slots (each with its
/// label), and keys outside the dictionary only where the user allows them (ArcSight usually indexes none of
/// those); the user's (or the standing instructions') overrides first of all. And what a plan lacks for more
/// Events, placed where the draft would place it.
public final class CefDraft {

    /// Where a value the draft leaves out on the user's word comes from.
    public static final String INSTRUCTED = "left out as instructed";
    /// The kind of event of the fields every event gets, in what is not sent.
    public static final String EVERY_EVENT = "every event";

    private static final Pattern DATA_NAME = Pattern.compile("Data\\[@Name='([^']+)'\\]");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");
    private static final Set<String> NOT_LABEL = Set.of("EventDetail", "EventSource", "*");

    private CefDraft() {
        // Static only.
    }

    /// `draft`: a plan inferred from sample Events, and notes on what was decided and why.
    ///
    /// @param events     The sample Events.
    /// @param customKeys Whether keys outside the dictionary may be sent.
    /// @param overrides  Where values go (or that they are not sent), as the user or the instructions say.
    /// @param header     Header fields given, by name; null for none.
    /// @param output     `kafka` or `text`.
    /// @param topic      The Kafka topic, or null.
    /// @param kafkaKey   An XPath for each Kafka record's key, or null.
    /// @return The plan and the notes.
    public static Drafted draft(final List<XmlElement> events,
                                final boolean customKeys,
                                final List<CefOverride> overrides,
                                final Map<String, CefValue> header,
                                final String output,
                                final String topic,
                                final String kafkaKey) {
        if (!CefPlan.OUTPUTS.contains(output)) {
            throw new IllegalArgumentException("output should be 'kafka' or 'text'");
        }
        final Draft draft = new Draft(customKeys, overrides);
        final Map<String, List<Map<String, String>>> byKind = new LinkedHashMap<>();
        for (final XmlElement event : events) {
            byKind.computeIfAbsent(Objects.requireNonNullElse(CefEvents.eventKind(event), "other"),
                    k -> new ArrayList<>()).add(CefEvents.eventValues(event));
        }
        final List<Map<String, String>> every = byKind.values().stream().flatMap(List::stream).toList();
        final Set<String> seenPaths = new HashSet<>();
        every.forEach(v -> seenPaths.addAll(v.keySet()));

        final Map<String, CefValue> head = header(every, draft.notes);
        if (header != null) {
            head.putAll(header);
        }
        // Values the header carries are not sent again in the extension.
        for (final CefValue value : head.values()) {
            if (JsonValues.truthy(value.source())) {
                draft.takenByHeader.add(value.source());
            }
        }
        CefKeys.HEADER_PATHS.forEach((name, path) -> {
            final String source = head.get(name).source();
            if (JsonValues.truthy(source) && source.contains(path)) {
                draft.takenByHeader.add(path);
            }
        });

        final List<CefField> common = new ArrayList<>();
        final Set<String> usedCommon = new HashSet<>();
        final List<String> commonPaths = byCount(seenPaths.stream()
                .filter(p -> !p.startsWith("EventDetail/") || "EventDetail/TypeId".equals(p)
                             || "EventDetail/Description".equals(p))
                .toList(), every);
        final Map<String, List<String>> free = new LinkedHashMap<>();
        CefKeys.SLOTS.forEach((kind, keys) -> free.put(kind, new ArrayList<>(keys)));
        for (final String path : commonPaths) {
            draft.place(path, every, usedCommon, free, null, common, EVERY_EVENT);
        }
        final Map<String, List<CefField>> eventsOut = new LinkedHashMap<>();
        for (final Map.Entry<String, List<Map<String, String>>> entry : new TreeMap<>(byKind).entrySet()) {
            final String kind = entry.getKey();
            final List<Map<String, String>> pool = entry.getValue();
            // A common key is taken for this kind only where its events hold the common value (Network events have
            // a Source address, not a Client one: src is theirs). Should an event hold both, its kind's value is
            // sent.
            final Set<String> held = new HashSet<>();
            pool.forEach(v -> held.addAll(v.keySet()));
            final Set<String> used = common.stream()
                    .filter(f -> held.stream().anyMatch(p -> matches(f.path(), p)))
                    .map(CefField::key)
                    .collect(Collectors.toCollection(HashSet::new));
            final Map<String, List<String>> slots = new LinkedHashMap<>();
            free.forEach((k, v) -> slots.put(k, new ArrayList<>(v)));
            final List<CefField> fields = new ArrayList<>();
            final List<String> paths = byCount(held.stream()
                    .filter(p -> p.startsWith("EventDetail/") && !commonPaths.contains(p))
                    .toList(), pool);
            for (final String path : paths) {
                draft.place(path, pool, used, slots, kind, fields, kind);
            }
            final CefOverride kindOverride = draft.overrideFor("EventDetail/" + kind, kind);
            if (!"other".equals(kind) && !used.contains("act") && !(kindOverride != null && kindOverride.drop())) {
                // No Action element (View, Create, Delete...): the action is the event's kind.
                fields.addFirst(CefField.of("Network".equals(kind)
                        ? "local-name(EventDetail/Network/*[1])"
                        : "'" + kind + "'", "act"));
            }
            if (!fields.isEmpty()) {
                eventsOut.put(kind, fields);
            }
        }
        final List<CefOverride> given = Objects.requireNonNullElse(overrides, List.of());
        if (given.stream().anyMatch(o -> o.eventType() == null && JsonValues.truthy(o.key())
                                         && seenPaths.stream().noneMatch(p -> matches(o.path(), p)))) {
            final List<String> missing = given.stream()
                    .filter(o -> JsonValues.truthy(o.key()) && seenPaths.stream().noneMatch(p -> matches(o.path(),
                            p)))
                    .map(CefOverride::path)
                    .toList();
            draft.notes.add("overrides for paths no sample Event holds, not used: " + ToolText.list(missing));
        }
        final CefPlan plan = new CefPlan(output, topic, kafkaKey, customKeys, head.get("vendor"), head.get("product"),
                head.get("version"), head.get("signature"), head.get("name"), head.get("severity"), common, eventsOut,
                draft.notSent, List.of(), null);
        return new Drafted(plan, draft.notes);
    }

    /// `extend`: what a plan lacks for these Events (event kinds it has no fields for, values a kind's fields don't
    /// take), as overrides placing each where the draft would: every key the plan gives stays where it is.
    ///
    /// @param plan   The plan.
    /// @param events More Events.
    /// @return The additions (`path`, `key`, `event_type` for a kind's, `label` for a slot's) and notes.
    public static Extended extend(final CefPlan plan, final List<XmlElement> events) {
        final List<CefOverride> kept = new ArrayList<>();
        plan.common().forEach(f -> kept.add(new CefOverride(f.path(), f.key(), f.label(), null, false)));
        plan.events().forEach((kind, fields) -> fields.forEach(f -> kept.add(new CefOverride(f.path(), f.key(),
                f.label(), kind, false))));
        for (final Map<String, String> n : plan.notSent()) {
            if (Objects.requireNonNullElse(n.get("why"), "").contains("instructed")) {
                kept.add(CefOverride.dropped(n.get("path"), EVERY_EVENT.equals(n.get("event_type"))
                        ? null
                        : n.get("event_type")));
            }
        }
        final CefPlan redrafted = draft(events, plan.customKeys(), kept, plan.headers(), plan.output(), plan.topic(),
                plan.kafkaKey()).plan();
        final Set<List<String>> had = new HashSet<>();
        plan.common().forEach(f -> had.add(pair(null, f.path())));
        plan.events().forEach((kind, fields) -> fields.forEach(f -> had.add(pair(kind, f.path()))));
        final List<Map<String, String>> added = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        for (final CefField f : redrafted.common()) {
            if (!had.contains(pair(null, f.path()))) {
                added.add(addition(f, null));
            }
        }
        redrafted.events().forEach((kind, fields) -> {
            if (!plan.events().containsKey(kind)) {
                notes.add(kind + " events: a kind the CEF plan has no fields for (only the common ones are sent)");
            }
            for (final CefField f : fields) {
                if (!had.contains(pair(kind, f.path())) && !had.contains(pair(null, f.path()))) {
                    added.add(addition(f, kind));
                }
            }
        });
        // The draft reserves only the keys of paths these Events hold: a key the plan gives a value they don't hold
        // is taken all the same, so an addition landing on one moves to a slot that is free in the plan.
        final Set<String> commonKeys = plan.common().stream().map(CefField::key).collect(Collectors.toSet());
        for (final Map<String, String> entry : new ArrayList<>(added)) {
            final String kind = entry.get("event_type");
            final Set<String> taken = new HashSet<>(commonKeys);
            plan.events().getOrDefault(kind, List.of()).forEach(f -> taken.add(f.key()));
            for (final Map<String, String> a : added) {
                if (a != entry && (Objects.equals(a.get("event_type"), kind) || a.get("event_type") == null)) {
                    taken.add(a.get("key"));
                }
            }
            if (!taken.contains(entry.get("key"))) {
                continue;
            }
            final String kindOf = CefKeys.SLOTS.entrySet().stream()
                    .filter(e -> e.getValue().contains(entry.get("key")))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("string");
            final List<String> candidates = new ArrayList<>(CefKeys.SLOTS.get(kindOf));
            candidates.addAll(CefKeys.SLOTS.get("string"));
            final String freeSlot = candidates.stream().filter(k -> !taken.contains(k)).findFirst().orElse(null);
            if (freeSlot != null) {
                entry.put("key", freeSlot);
                entry.putIfAbsent("label", labelFor(entry.get("path")));
            } else {
                added.remove(entry);
                notes.add(entry.get("path") + " (" + Objects.requireNonNullElse(kind, EVERY_EVENT) + "): no free slot "
                          + "left beside the plan's own");
            }
        }
        final List<Map<String, String>> unsent = redrafted.notSent().stream()
                .filter(n -> !plan.notSent().contains(n))
                .toList();
        if (!unsent.isEmpty()) {
            notes.add(unsent.size() + " new value(s) no key or free slot takes: " + ToolText.list(unsent.stream()
                    .map(n -> n.get("path"))
                    .limit(8)
                    .toList()));
        }
        return new Extended(added, notes);
    }

    /// A custom slot's label from its Event path.
    ///
    /// @param path An Event path.
    /// @return A Data's Name, else the path's last three steps (below EventSource and EventDetail).
    public static String labelFor(final String path) {
        final Matcher data = DATA_NAME.matcher(path);
        if (data.find()) {
            return data.group(1);
        }
        final List<String> steps = ToolText.split(path, "/").stream()
                .filter(s -> !NOT_LABEL.contains(s) && !s.startsWith("@"))
                .toList();
        return String.join(" ", steps.subList(Math.max(0, steps.size() - 3), steps.size()));
    }

    /// `_matches`: whether an Event path matches a pattern, `*` being any one step.
    ///
    /// @param pattern A pattern, e.g. `EventDetail/*/User/Id`.
    /// @param path    An Event path.
    /// @return True if it matches.
    public static boolean matches(final String pattern, final String path) {
        final List<String> want = ToolText.split(pattern, "/");
        final List<String> have = ToolText.split(path, "/");
        if (want.size() != have.size()) {
            return false;
        }
        for (int i = 0; i < want.size(); i++) {
            if (!"*".equals(want.get(i)) && !want.get(i).equals(have.get(i))) {
                return false;
            }
        }
        return true;
    }

    // The header: constants where the sample has no value for it.
    private static Map<String, CefValue> header(final List<Map<String, String>> every, final List<String> notes) {
        final Map<String, CefValue> head = new LinkedHashMap<>();
        for (final CefKeys.HeaderField field : CefKeys.HEADER) {
            final String name = field.name();
            if ("severity".equals(name)) {
                continue;
            }
            final String path = CefKeys.HEADER_PATHS.get(name);
            final double share = every.stream().filter(v -> v.containsKey(path)).count() / (double) Math.max(
                    every.size(), 1);
            if (share > 0 && ("name".equals(name) || "signature".equals(name))) {
                // Most events have it; the rest are named by their action rather than left empty.
                head.put(name, CefValue.from("(" + path + ", " + CefKeys.ACTION_NAME
                                             + ")[normalize-space(.) != ''][1]"));
            } else if (share >= 0.5 || "version".equals(name)) {
                // The version: from its path however few of the sample's events have it (asked for by the user:
                // an empty constant stayed empty for the events that do).
                head.put(name, CefValue.from(path));
            } else if ("name".equals(name)) {
                head.put(name, CefValue.from(CefKeys.ACTION_NAME));
                notes.add("Name: the action element and its Action (no EventDetail/Description in most events)");
            } else if ("signature".equals(name)) {
                head.put(name, CefValue.from("(EventDetail/TypeId, " + CefKeys.ACTION_NAME
                                             + ")[normalize-space(.) != ''][1]"));
            } else {
                head.put(name, CefValue.constant("TODO " + field.said() + ": the sample's Events have no " + path
                                                 + "; ask the user"));
            }
        }
        return head;
    }

    // The paths, the most often held first, then by name.
    private static List<String> byCount(final List<String> paths, final List<Map<String, String>> pool) {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final String path : new TreeSet<>(paths)) {
            counts.put(path, pool.stream().filter(v -> v.containsKey(path)).count());
        }
        return counts.keySet().stream()
                .sorted(Comparator.<String, Long>comparing(p -> -counts.get(p))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static Map<String, String> addition(final CefField f, final String kind) {
        final Map<String, String> entry = new LinkedHashMap<>();
        entry.put("path", f.path());
        entry.put("key", f.key());
        if (kind != null) {
            entry.put("event_type", kind);
        }
        if (JsonValues.truthy(f.label())) {
            entry.put("label", f.label());
        }
        return entry;
    }

    private static List<String> pair(final String kind, final String path) {
        return java.util.Arrays.asList(kind, path);
    }

    // A key outside the dictionary named after the path's label.
    private static String camel(final String path) {
        final List<String> words = new ArrayList<>();
        final Matcher matcher = WORD.matcher(labelFor(path));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        if (words.isEmpty()) {
            return "value";
        }
        final StringBuilder sb = new StringBuilder(words.getFirst().substring(0, 1).toLowerCase())
                .append(words.getFirst().substring(1));
        for (final String word : words.subList(1, words.size())) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A drafted plan.
    ///
    /// @param plan  The plan.
    /// @param notes What was decided and why.
    public record Drafted(CefPlan plan, List<String> notes) {

    }

    /// What a plan lacks for more Events.
    ///
    /// @param added Overrides placing each new value (`path`, `key`, `event_type` for a kind's, `label` for a
    ///              slot's).
    /// @param notes What the plan has no room or fields for.
    public record Extended(List<Map<String, String>> added, List<String> notes) {

    }

    // One draft's working state: the overrides, what the header takes, the notes, and what is not sent.
    private static final class Draft {

        private final boolean customKeys;
        private final List<CefOverride> overrides;
        private final Set<String> takenByHeader = new HashSet<>();
        private final List<String> notes = new ArrayList<>();
        private final List<Map<String, String>> notSent = new ArrayList<>();

        private Draft(final boolean customKeys, final List<CefOverride> overrides) {
            this.customKeys = customKeys;
            this.overrides = Objects.requireNonNullElse(overrides, List.of());
        }

        // Overrides: per kind first, then everywhere.
        private CefOverride overrideFor(final String path, final String kind) {
            return overrides.stream()
                    .filter(o -> Objects.equals(o.eventType(), kind) && matches(o.path(), path))
                    .findFirst()
                    .or(() -> overrides.stream()
                            .filter(o -> o.eventType() == null && matches(o.path(), path))
                            .findFirst())
                    .orElse(null);
        }

        private void place(final String path,
                           final List<Map<String, String>> pool,
                           final Set<String> used,
                           final Map<String, List<String>> slots,
                           final String kind,
                           final List<CefField> out,
                           final String where) {
            if (takenByHeader.contains(path)) {
                return;
            }
            final CefOverride given = overrideFor(path, kind);
            if (given != null && given.drop()) {
                notSent.add(notSent(path, where, INSTRUCTED));
                return;
            }
            if (given != null && JsonValues.truthy(given.key())) {
                final String key = CefKeys.keyOf(given.key());
                if (used.contains(key)) {
                    notes.add("[" + where + "] " + path + " -> " + key + " as instructed, but " + key + " was already "
                              + "taken: check the overrides");
                }
                out.add(new CefField(path, key, JsonValues.truthy(given.label())
                        ? given.label()
                        : CefKeys.SLOT_KEYS.contains(key)
                                ? labelFor(path)
                                : null, CefField.TEXT));
                used.add(key);
                slots.values().forEach(list -> list.remove(key));
                return;
            }
            final List<String> values = pool.stream()
                    .filter(v -> v.containsKey(path))
                    .map(v -> v.get(path))
                    .limit(200)
                    .toList();
            final CefKeys.Home home = CefKeys.HOMES.stream()
                    .filter(h -> matches(h.pattern(), path) && !used.contains(h.key()))
                    .findFirst()
                    .orElse(null);
            if (home != null && (!CefField.TEXT.equals(home.transform()) || CefEvents.fits(home.key(), values))) {
                out.add(new CefField(path, home.key(), null, home.transform()));
                used.add(home.key());
                return;
            }
            if (home != null) {
                notes.add("[" + where + "] " + path + ": values like " + ToolText.list(values.subList(0,
                        Math.min(2, values.size()))) + " don't fit " + home.key() + " (" + CefKeys.KEYS.get(home.key())
                                  .type() + "), so it goes to a custom slot");
            }
            final String kindOf = CefEvents.slotKind(values);
            String slot = slots.getOrDefault(kindOf, List.of()).stream()
                    .filter(s -> !used.contains(s))
                    .findFirst()
                    .orElse(null);
            if (slot == null) {
                slot = slots.get("string").stream().filter(s -> !used.contains(s)).findFirst().orElse(null);
            }
            if (slot != null) {
                out.add(new CefField(path, slot, labelFor(path), "time".equals(CefKeys.KEYS.get(slot).type())
                        ? CefField.EPOCH_MS
                        : CefField.TEXT));
                used.add(slot);
                final String taken = slot;
                slots.values().forEach(list -> list.remove(taken));
                return;
            }
            if (customKeys) {
                String key = camel(path);
                while (CefKeys.KEYS.containsKey(key) || used.contains(key)) {
                    key += "X";
                }
                out.add(CefField.of(path, key));
                used.add(key);
                notes.add("[" + where + "] " + path + " -> " + key + ": a key outside the CEF dictionary (allowed by "
                          + "the user)");
                return;
            }
            notSent.add(notSent(path, where, "no standard CEF key fits it and the custom slots are all used; keys "
                                             + "outside the dictionary are not allowed"));
        }

        private static Map<String, String> notSent(final String path, final String where, final String why) {
            final Map<String, String> entry = new LinkedHashMap<>();
            entry.put("path", path);
            entry.put("event_type", where);
            entry.put("why", why);
            return entry;
        }
    }
}
