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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueKept;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.processing.ErrorTriage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Which of the build's catalogued event types the records step_sample stepped wrote, told by each record's output:
/// the `EventDetail/TypeId` of its events, which the translation writes from the catalogue (its TypeId, else the
/// default, [CatalogueKept#typeIdOf]). An event type whose records are dropped writes no event, so it is not looked
/// for; nor is the catch-all, which isn't an event type.
///
/// Seen in user testing (chat 182): the first 50 records of each of two streams were stepped, 57 of the 100 passed
/// over as other source types' records, and nothing said whether any of the build's 7 event types was among the 43
/// left: a clean verdict proved little, the rest met only at processing.
final class SteppedEventTypes {

    /// The result's key for the records stepped of each event type that was stepped, by TypeId.
    static final String STEPPED_BY_EVENT_TYPE = "stepped_by_event_type";
    /// The result's key for the event types not stepped, each with why.
    static final String NOT_STEPPED = "not_stepped";
    /// A group's key, for the fields no rule reads: the event types of the records holding each.
    static final String UNREAD_IN = "unread_in";
    // The records of no catalogued event type: the catch-all's.
    private static final String UNCATALOGUED = "uncatalogued records";

    private static final Pattern TYPE_ID = Pattern.compile(
            "<(?:[\\w.-]+:)?TypeId(?:\\s[^>]*)?>\\s*([^<]*?)\\s*</(?:[\\w.-]+:)?TypeId>");

    // The catalogue's event types looked for, by TypeId, in its order: their titles.
    private final Map<String, String> titles;
    // The records stepped of each, by TypeId.
    private final Map<String, Integer> stepped = new LinkedHashMap<>();
    // The catalogued event types each record stepped wrote, by its key.
    private final Map<String, Set<String>> byRecord = new HashMap<>();

    private SteppedEventTypes(final Map<String, String> titles) {
        this.titles = titles;
    }

    /// @param catalogue The build's catalogue of event types, or null when it has none.
    /// @return The event types to look for, none stepped yet; null when the build has no catalogue, or no event type
    /// in it writes an event.
    static SteppedEventTypes of(final Catalogue catalogue) {
        if (catalogue == null) {
            return null;
        }
        final Map<String, String> titles = new LinkedHashMap<>();
        for (final EventType type : catalogue.eventTypes()) {
            if (!type.drop()) {
                titles.putIfAbsent(CatalogueKept.typeIdOf(type), type.title());
            }
        }
        return titles.isEmpty()
                ? null
                : new SteppedEventTypes(titles);
    }

    /// Counts one record stepped against each catalogued event type its output has an event of (once each, however
    /// many of its events are of the type).
    ///
    /// @param record The record's key, e.g. `7:3`.
    /// @param output What the record's step wrote at the pipeline's output element.
    void count(final String record, final String output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        final Set<String> found = new LinkedHashSet<>();
        final Matcher matcher = TYPE_ID.matcher(output);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        found.removeIf(typeId -> !titles.containsKey(typeId));
        found.forEach(typeId -> stepped.merge(typeId, 1, Integer::sum));
        byRecord.put(record, found);
    }

    /// Adds [#UNREAD_IN] to each group of fields no rule reads: for each field, how many records held it and the
    /// event types they are of. Seen in chat 194: step_sample said 'app' was in 59 records that no rule reads, and the
    /// build that the records of nine rules never hold it; with nothing saying which records were meant, the agent
    /// took the two for a contradiction and added 'app' to all 12 rules, when only the focus events' rules lacked it.
    ///
    /// @param summary The step's triage, its groups listing their records.
    /// @param markers The markers met, each of its record.
    void addUnreadIn(final Map<String, Object> summary, final List<ErrorTriage.Marker> markers) {
        for (final Map<String, Object> group : SteppingVerdicts.groups(summary)) {
            final Map<String, Set<String>> recordsByField = new LinkedHashMap<>();
            for (final ErrorTriage.Marker marker : markers) {
                final String field = unreadField(marker.message());
                if (field != null && marker.record() != null
                    && Objects.equals(marker.severity(), group.get("severity"))
                    && Objects.equals(marker.element(), group.get("element"))) {
                    recordsByField.computeIfAbsent(field, ignored -> new LinkedHashSet<>()).add(marker.record());
                }
            }
            if (recordsByField.isEmpty()) {
                continue;
            }
            final List<String> said = new ArrayList<>();
            recordsByField.forEach((field, records) -> said.add(unreadIn(field, records)));
            group.put(UNREAD_IN, said);
        }
    }

    // A field no rule reads, with the records holding it by event type: which rules to map it in.
    private String unreadIn(final String field, final Set<String> records) {
        final Map<String, Integer> byType = new LinkedHashMap<>();
        for (final String record : records) {
            final Set<String> types = byRecord.getOrDefault(record, Set.of());
            if (types.isEmpty()) {
                byType.merge(UNCATALOGUED, 1, Integer::sum);
            }
            types.forEach(typeId -> byType.merge(typeId, 1, Integer::sum));
        }
        final List<String> of = new ArrayList<>();
        byType.forEach((type, count) -> of.add(type + " (" + count + ")"));
        return "'" + field + "' is in " + records.size() + " record(s) stepped, of " + String.join(", ", of)
               + ": no rule taking them reads it. Map it in those rules (or list it in ignored_fields), not in "
               + "every rule.";
    }

    // The field a "No rule reads field" line names, or null for any other message.
    private static String unreadField(final String message) {
        if (message == null) {
            return null;
        }
        final int at = message.indexOf(XsltGenerator.UNREAD_FIELD);
        if (at < 0) {
            return null;
        }
        final int from = at + XsltGenerator.UNREAD_FIELD.length();
        final int to = message.indexOf(XsltGenerator.UNREAD_FIELD_END, from);
        return to < 0
                ? message.substring(from).strip()
                : message.substring(from, to).strip();
    }

    /// @return True once every catalogued event type looked for has been stepped at least once.
    boolean allStepped() {
        return stepped.size() == titles.size();
    }

    /// @return How many event types are looked for.
    int size() {
        return titles.size();
    }

    /// @return The TypeIds of the event types not stepped yet, in the catalogue's order.
    List<String> notStepped() {
        final List<String> missing = new ArrayList<>();
        titles.keySet().stream()
                .filter(typeId -> !stepped.containsKey(typeId))
                .forEach(missing::add);
        return missing;
    }

    /// Adds [#STEPPED_BY_EVENT_TYPE] (the event types stepped, in the catalogue's order) and, when any wasn't,
    /// [#NOT_STEPPED] (each with its `type_id`, `title` and `why`) to a result.
    ///
    /// @param result A step_sample result.
    /// @param why    Why an event type not stepped wasn't: not in the sample, or not found within the limit.
    void addTo(final Map<String, Object> result, final String why) {
        final Map<String, Integer> byType = new LinkedHashMap<>();
        titles.keySet().stream()
                .filter(stepped::containsKey)
                .forEach(typeId -> byType.put(typeId, stepped.get(typeId)));
        result.put(STEPPED_BY_EVENT_TYPE, byType);
        final List<Map<String, Object>> missing = new ArrayList<>();
        for (final String typeId : notStepped()) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("type_id", typeId);
            one.put("title", titles.get(typeId));
            one.put("why", why);
            missing.add(one);
        }
        if (!missing.isEmpty()) {
            result.put(NOT_STEPPED, missing);
        }
    }

    /// @return What the hint says of the event types stepped: that every one was, or which weren't.
    String said() {
        if (allStepped()) {
            return "Every one of the build's " + size() + " catalogued event types was stepped at least once "
                   + "(stepped_by_event_type).";
        }
        final List<String> missing = notStepped();
        return missing.size() + " of the build's " + size() + " catalogued event types were not stepped ("
               + String.join(", ", missing) + "; not_stepped says why): this verdict doesn't cover them. "
               + "Processing reads every record: wait_for_processing reports any that fail, and check_events reads "
               + "their Events.";
    }
}
