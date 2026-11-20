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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DropRule;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A hand-edited XSLT's rules, read back into a mapping (rebuild_mapping), read back into the catalogue of event
/// types, so the catalogue stays the source of truth (DESIGN.md, Event type catalogue, After the first build): each
/// rule an event type's way of recognising it (its `when`), the rules of one fixed TypeId one event type, its meaning
/// from what the rule writes (action element, Action, outcome, who acted, Description); the rule marked catch_all (or
/// the last rule taking every remaining record) the catch-all, with its own translation when it writes a real event,
/// not Unknown; drop rules the dropped event types they name. What the catalogue can't express (a rule writing its
/// TypeId from a field, a drop rule of no event type) is named, the catalogue's entry left as it was.
public final class CatalogueFromMapping {

    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String UNKNOWN = "Unknown";
    // The reason the translation gives a dropped event type's drop rule, before its TypeId.
    private static final String DROPPED = "the user said to leave these untranslated: ";

    private CatalogueFromMapping() {
        // Static only.
    }

    /// The catalogue the mapping's rules say.
    ///
    /// @param mapping The mapping read back from the XSLT.
    /// @param before  The build's catalogue before, or null when it has none.
    /// @return The catalogue read back, what changed, and what couldn't be read.
    public static FromMapping read(final TranslationMapping mapping, final Catalogue before) {
        final Catalogue earlier = Objects.requireNonNullElse(before, new Catalogue(List.of(), null, null));
        final List<String> notRead = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        final Map<String, List<EventRule>> byTypeId = new LinkedHashMap<>();
        final EventRule catchAll = catchAll(mapping);
        final Set<List<Condition>> variants = variantConditions(mapping);
        for (final EventRule rule : mapping.events()) {
            if (rule == catchAll || rule.variantField() != null) {
                continue;
            }
            if (rule.drop()) {
                notRead.add("rule " + rule.name() + ": a drop rule among the rules (drop: true): the catalogue's "
                            + "dropped event types are drop_when rules; not read");
                continue;
            }
            final String typeId = fixed(mapping, rule, "EventDetail/TypeId");
            if (typeId == null) {
                notRead.add("rule " + rule.name() + ": its TypeId isn't fixed (read from the record), so it is no "
                            + "one event type of the catalogue; not read");
                continue;
            }
            byTypeId.computeIfAbsent(typeId, key -> new ArrayList<>()).add(rule);
        }
        final List<EventType> types = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        byTypeId.forEach((typeId, rules) -> {
            final EventType was = earlier.eventType(typeId);
            final EventType now = eventType(mapping, typeId, rules, was, variants);
            seen.add(typeId);
            types.add(now);
            if (was == null) {
                said.add("added: event type " + typeId + ", from rule(s) " + names(rules));
            } else if (!same(was, now)) {
                said.add("changed: event type " + typeId + " (" + changes(was, now) + "), from rule(s) "
                         + names(rules));
            }
        });
        dropped(mapping, earlier, types, seen, notRead);
        for (final EventType type : earlier.eventTypes()) {
            if (!seen.contains(type.typeId())) {
                said.add("removed: event type " + type.typeId() + " (no rule of the XSLT translates it)");
            }
        }
        final Catalogue.CatchAll catchAllNow = catchAllOf(mapping, catchAll, earlier.catchAll(), said);
        return new FromMapping(new Catalogue(types, catchAllNow, earlier.scope()), said, notRead);
    }

    // The rule for the records no other rule takes: the one marked catch_all, else a last rule with no conditions
    // (a hand-written otherwise); null when there is none.
    private static EventRule catchAll(final TranslationMapping mapping) {
        if (mapping.events().isEmpty()) {
            return null;
        }
        final EventRule last = mapping.events().getLast();
        final EventRule marked = mapping.events().stream()
                .filter(rule -> rule.catchAll() && rule.variantField() == null)
                .findFirst()
                .orElse(null);
        if (marked != null) {
            return marked;
        }
        return last.when().isEmpty() && !last.drop() && mapping.events().size() > 1
                ? last
                : null;
    }

    // The line formats' catch-alls' conditions: a way's leading condition on its format, not the event type's.
    private static Set<List<Condition>> variantConditions(final TranslationMapping mapping) {
        final Set<List<Condition>> out = new LinkedHashSet<>();
        mapping.events().stream()
                .filter(rule -> rule.variantField() != null)
                .forEach(rule -> out.add(rule.when()));
        return out;
    }

    // The event type the rules of one TypeId say, over what the catalogue said of it before.
    private static EventType eventType(final TranslationMapping mapping,
                                       final String typeId,
                                       final List<EventRule> rules,
                                       final EventType was,
                                       final Set<List<Condition>> variants) {
        final List<Way> ways = new ArrayList<>();
        for (final EventRule rule : rules) {
            final Way way = new Way(withoutVariant(rule.when(), variants), List.of());
            final Way kept = was == null
                    ? null
                    : was.recognisedBy().stream().filter(earlier -> earlier.when().equals(way.when()))
                            .findFirst().orElse(null);
            final Way chosen = kept != null
                    ? kept
                    : way;
            if (!ways.contains(chosen)) {
                ways.add(chosen);
            }
        }
        final EventRule first = rules.getFirst();
        final CatalogueKept.Written written = CatalogueKept.written(mapping, first);
        final String detail = written.details().stream()
                .filter(element -> !UNKNOWN.equals(element))
                .findFirst()
                .orElse(written.details().isEmpty()
                        ? null
                        : UNKNOWN);
        final String action = detail == null
                ? null
                : written.actions().get(detail);
        final String wasDetail = was == null
                ? null
                : CatalogueKept.detailOf(was);
        // A detail the catalogue gave more of (Authenticate/Logon) is kept while its element is the rule's.
        final String eventDetail = was != null && Objects.equals(wasDetail, detail)
                ? was.eventDetail()
                : detail;
        final String success = detail == null
                ? null
                : fixedOrField(mapping, first, EVENT_DETAIL + detail + "/Outcome/Success");
        final String actor = fieldOf(mapping, first, "EventSource/User/Id");
        final String description = fixed(mapping, first, "EventDetail/Description");
        if (was == null) {
            // Written by hand, saved as the user confirmed the rebuild: the user's.
            return new EventType(typeId, typeId, description, eventDetail, action, success, actor, null, null,
                    ways, false, EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);
        }
        return new EventType(typeId, was.title(), orElse(description, was.description()),
                eventDetail, orElse(action, was.action()),
                orElse(success, was.success()), orElse(actor, was.actor()),
                was.affected(), was.affectedAs(), ways, false, was.basis(), was.confidence(), was.oneTypeBecause(),
                was.status(), was.doubt());
    }

    // The way's conditions without a leading line format's condition (the translation prefixes it per variant), so
    // long as some condition is left: a sole event type of a line format is recognised by its format's alone.
    private static List<Condition> withoutVariant(final List<Condition> when, final Set<List<Condition>> variants) {
        for (final List<Condition> variant : variants) {
            if (when.size() > variant.size() && when.subList(0, variant.size()).equals(variant)) {
                return List.copyOf(when.subList(variant.size(), when.size()));
            }
        }
        return when;
    }

    // The drop rules naming the catalogue's event types: those types, dropped, recognised by the rules' conditions.
    private static void dropped(final TranslationMapping mapping,
                                final Catalogue earlier,
                                final List<EventType> types,
                                final Set<String> seen,
                                final List<String> notRead) {
        final Map<String, List<Way>> byTypeId = new LinkedHashMap<>();
        for (final DropRule drop : mapping.dropWhen()) {
            final String reason = NullSafe.string(drop.reason());
            if (!reason.startsWith(DROPPED)) {
                notRead.add("drop rule '" + reason + "': it leaves records untranslated that no dropped event "
                            + "type of the catalogue names; not read");
                continue;
            }
            byTypeId.computeIfAbsent(reason.substring(DROPPED.length()).strip(), key -> new ArrayList<>())
                    .add(new Way(drop.when(), List.of()));
        }
        byTypeId.forEach((typeId, ways) -> {
            final EventType was = earlier.eventType(typeId);
            seen.add(typeId);
            types.add(was == null
                    ? new EventType(typeId, typeId, null, null, null, null, null, null, null, ways, true,
                    EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED)
                    : new EventType(typeId, was.title(), was.description(), was.eventDetail(), was.action(),
                            was.success(), was.actor(), was.affected(), was.affectedAs(), ways, true, was.basis(),
                            was.confidence(), was.oneTypeBecause(), was.status(), was.doubt()));
        });
    }

    // The catch-all read back: Unknown as before, or its own translation when the rule writes a real event (named
    // in the review).
    private static Catalogue.CatchAll catchAllOf(final TranslationMapping mapping,
                                                 final EventRule rule,
                                                 final Catalogue.CatchAll before,
                                                 final List<String> said) {
        if (rule == null) {
            return before;
        }
        final CatalogueKept.Written written = CatalogueKept.written(mapping, rule);
        final String detail = written.details().stream()
                .filter(element -> !UNKNOWN.equals(element))
                .findFirst()
                .orElse(null);
        final String allowUnknown = orElse(rule.allowUnknown(), before.allowUnknown());
        if (detail == null || written.details().contains(UNKNOWN)) {
            if (before.translation() != null) {
                said.add("changed: the catch-all (uncatalogued records) writes Unknown again (rule " + rule.name()
                         + ")");
            }
            return new Catalogue.CatchAll(EventType.CONFIRMED, allowUnknown, null);
        }
        final Catalogue.CatchAll.Translation translation = new Catalogue.CatchAll.Translation(
                fixed(mapping, rule, "EventDetail/TypeId"), detail, written.actions().get(detail),
                fixed(mapping, rule, "EventDetail/Description"));
        if (!translation.equals(before.translation())) {
            said.add("the catch-all: rule " + rule.name() + " takes every record no other rule takes and writes a "
                     + "real event (" + detail + (translation.typeId() == null
                    ? ""
                    : ", TypeId " + translation.typeId()) + "), not Unknown: read back as the catch-all with its "
                     + "own translation");
        }
        return new Catalogue.CatchAll(EventType.CONFIRMED, null, translation);
    }

    private static boolean same(final EventType was, final EventType now) {
        return was.recognisedBy().equals(now.recognisedBy()) && Objects.equals(was.eventDetail(), now.eventDetail())
               && Objects.equals(was.action(), now.action());
    }

    private static String changes(final EventType was, final EventType now) {
        final List<String> out = new ArrayList<>();
        if (!was.recognisedBy().equals(now.recognisedBy())) {
            out.add("recognised by " + CatalogueKept.recognisedBy(now) + ", was " + CatalogueKept.recognisedBy(was));
        }
        if (!Objects.equals(was.eventDetail(), now.eventDetail())) {
            out.add("action element " + now.eventDetail() + ", was " + was.eventDetail());
        }
        if (!Objects.equals(was.action(), now.action())) {
            out.add("Action " + now.action() + ", was " + was.action());
        }
        return String.join("; ", out);
    }

    private static String names(final List<EventRule> rules) {
        return String.join(", ", rules.stream().map(EventRule::name).toList());
    }

    // The fixed value the rule (or the mapping's common entries) writes at the path, or null.
    private static String fixed(final TranslationMapping mapping, final EventRule rule, final String path) {
        final FieldMapping entry = entryAt(mapping, rule, path);
        return entry == null
                ? null
                : entry.value();
    }

    // The field the rule (or the common entries) writes at the path, or null.
    private static String fieldOf(final TranslationMapping mapping, final EventRule rule, final String path) {
        final FieldMapping entry = entryAt(mapping, rule, path);
        return entry == null
                ? null
                : entry.field();
    }

    private static String fixedOrField(final TranslationMapping mapping, final EventRule rule, final String path) {
        return orElse(fixed(mapping, rule, path), fieldOf(mapping, rule, path));
    }

    // The text, unless blank; else the other (null too).
    private static String orElse(final String text, final String other) {
        return NullSafe.isNonBlankString(text)
                ? text
                : other;
    }

    private static FieldMapping entryAt(final TranslationMapping mapping, final EventRule rule, final String path) {
        for (final List<FieldMapping> entries : List.of(rule.fields(), mapping.common())) {
            for (final FieldMapping entry : entries) {
                if (path.equals(entry.strippedPath())) {
                    return entry;
                }
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// The catalogue read back from a mapping.
    ///
    /// @param catalogue The catalogue the rules say.
    /// @param said      What changed, for the review: event types added, changed and removed, and the catch-all
    ///                  when it writes a real event.
    /// @param notRead   What the catalogue can't express, each naming the rule (`... not read`).
    public record FromMapping(Catalogue catalogue, List<String> said, List<String> notRead) {

    }
}
