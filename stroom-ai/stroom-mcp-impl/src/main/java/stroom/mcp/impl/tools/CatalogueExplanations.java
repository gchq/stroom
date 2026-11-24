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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheck;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// The parts of [CatalogueEventTypesTool]'s answer to `explain=`: an event type or a group of the summary explained,
/// the names asked for found, and why a name isn't in the catalogue.
final class CatalogueExplanations {

    private static final String EXPLAIN_ARG = CatalogueEventTypesTool.EXPLAIN_ARG;

    private CatalogueExplanations() {
        // Static methods only.
    }

    /// An event type explained: what it means, how it is recognised and translated, its records and a few of them.
    ///
    /// @param type           The event type.
    /// @param check          The catalogue's check.
    /// @param examples       A few of its records.
    /// @param beingConfirmed True if it is in the set put to the user.
    /// @return The event type explained.
    static Map<String, Object> explainedType(final EventType type,
                                             final CatalogueCheckResult check,
                                             final List<CatalogueCheck.Example> examples,
                                             final boolean beingConfirmed) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("type_id", type.typeId());
        out.put("title", type.title());
        out.put("description", NullSafe.isBlankString(type.description())
                ? "none of its own: its records' Description comes only from a message they hold"
                : type.description());
        out.put("recognised_by", CatalogueTables.recognisedBy(type));
        out.put("translated_as", CatalogueTables.translatedAs(type) + CatalogueTables.placedAs(type));
        final CatalogueCheckResult.Count count = check.counts().get(type.typeId());
        out.put("records", count == null
                ? 0
                : count.records());
        out.put("confidence", type.confidence());
        if (type.doubt() != null) {
            out.put("doubt", type.doubt());
        }
        if (NullSafe.isNonBlankString(type.oneTypeBecause())) {
            out.put("one_type_because", type.oneTypeBecause());
        }
        out.put(CatalogueEventTypesTool.STATUS, type.status() + (beingConfirmed
                ? ", in the set put to the user"
                : ""));
        out.put("examples", examples.isEmpty()
                ? "no record of it in the sample"
                : examples.stream().map(CatalogueCheck.Example::toJson).toList());
        return out;
    }

    /// A group of the summary explained: its event types, each with how it is recognised and its records.
    ///
    /// @param group The group.
    /// @param check The catalogue's check.
    /// @return The group explained.
    static Map<String, Object> explainedGroup(final CatalogueSummary.Group group,
                                              final CatalogueCheckResult check) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("group", group.whatHappened());
        out.put("event_types", group.types().size());
        out.put("records", group.records());
        final List<Map<String, Object>> members = new ArrayList<>();
        for (final EventType type : group.types()) {
            final Map<String, Object> member = new LinkedHashMap<>();
            member.put("type_id", type.typeId());
            member.put("title", type.title());
            member.put("recognised_by", CatalogueTables.recognisedBy(type));
            final CatalogueCheckResult.Count count = check.counts().get(type.typeId());
            member.put("records", count == null
                    ? 0
                    : count.records());
            members.add(member);
        }
        out.put("members", members);
        out.put("more", "explain=<a TypeId> gives one's meaning and example records");
        return out;
    }

    /// The event type a name is the TypeId of, ignoring case; null when none is.
    ///
    /// @param catalogue The catalogue.
    /// @param name      The name.
    /// @return The event type, or null.
    static EventType eventType(final Catalogue catalogue, final String name) {
        return catalogue.eventTypes().stream()
                .filter(type -> type.typeId() != null && type.typeId().equalsIgnoreCase(name.strip()))
                .findFirst()
                .orElse(null);
    }

    /// The group of the summary a name is the action element of, ignoring case: the set put to the user's, else the
    /// whole catalogue's.
    ///
    /// @param pending   The set put to the user.
    /// @param catalogue The catalogue.
    /// @param check     The catalogue's check.
    /// @param name      The name.
    /// @return The group, if any.
    static Optional<CatalogueSummary.Group> group(final List<EventType> pending,
                                                  final Catalogue catalogue,
                                                  final CatalogueCheckResult check,
                                                  final String name) {
        for (final List<EventType> types : List.of(pending, catalogue.eventTypes())) {
            final Optional<CatalogueSummary.Group> found = CatalogueSummary.groups(types, check).stream()
                    .filter(group -> group.whatHappened().equalsIgnoreCase(name.strip()))
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /// Why names explain= was given aren't in the catalogue, with the nearest TypeIds and groups to each.
    ///
    /// @param catalogue The catalogue.
    /// @param check     The catalogue's check.
    /// @param unknown   The names not in it.
    /// @return Why, for the agent.
    static String unknownNames(final Catalogue catalogue,
                               final CatalogueCheckResult check,
                               final List<String> unknown) {
        final Map<String, String> byLower = new LinkedHashMap<>();
        catalogue.eventTypes().forEach(type -> {
            if (type.typeId() != null) {
                byLower.putIfAbsent(type.typeId().toLowerCase(Locale.ROOT), type.typeId());
            }
        });
        CatalogueSummary.groups(catalogue.eventTypes(), check).forEach(group -> byLower.putIfAbsent(
                group.whatHappened().toLowerCase(Locale.ROOT), group.whatHappened()));
        final List<String> said = new ArrayList<>();
        for (final String name : unknown) {
            final List<String> nearest = CloseMatches.closeMatches(name.strip().toLowerCase(Locale.ROOT),
                    byLower.keySet(), CloseMatches.DEFAULT_COUNT, 0).stream().map(byLower::get).toList();
            said.add("'" + name + "'" + (nearest.isEmpty()
                    ? ""
                    : " (nearest: " + String.join(", ", nearest) + ")"));
        }
        return "No event type or group " + String.join("; ", said) + " in the catalogue. " + EXPLAIN_ARG + " takes "
               + "TypeIds, action elements (a group of the summary) or Unknown (the uncatalogued records). Nothing "
               + "was changed.";
    }

    /// The names explain= gives: a list, or one text (several split at commas); empty when not given.
    ///
    /// @param call The call.
    /// @return The names.
    static List<String> explainArg(final ToolCall call) {
        final Object given = AbstractBuiltInTool.argsMap(call).get(EXPLAIN_ARG);
        final List<String> names = given instanceof final List<?> list
                ? list.stream().map(ToolText::str).toList()
                : given == null
                        ? List.of()
                        : List.of(ToolText.str(given).split(","));
        return names.stream().map(String::strip).filter(name -> !name.isEmpty()).distinct().toList();
    }
}
