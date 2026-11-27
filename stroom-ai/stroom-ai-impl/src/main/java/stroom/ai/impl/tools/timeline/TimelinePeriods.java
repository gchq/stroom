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

import stroom.ai.impl.tools.catalogue.TimelinePeriod;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Options;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A timeline's periods (decided with the user 2026-11-26), worked out in code beside the sessions: a state the
/// person was in (a remote session, on site, elevated, a device attached, on a call) from a `period_start` event to a
/// `period_end` event of the same kind and key ([TimelinePeriod]), across hosts and sources (a door entered by A-1
/// and left by A-4 pair at the site). A period implies no activity.
///
/// - An end closes the open start of its kind and key.
/// - A start with no end closes at the next start of the same kind and key, else at the timeline's period end,
///   marked [#END_NOT_SEEN].
/// - An end with no start opens at the timeline's period start, or at the end of the last period of its kind and key
///   where there was one (so a repeated end doesn't overlap it), marked [#START_NOT_SEEN].
///
/// Each period's facts are the code's: its kind, key, times, how each end was seen and its evidence events. Its
/// description is the model's ([PeriodWording]), its label the plain fallback.
final class TimelinePeriods {

    /// What a period is said to show.
    static final String MEANS = "a state the person was in, from its start event to its end; no activity by itself";
    /// An end seen: its event is the period's.
    static final String SEEN = "seen";
    /// No start seen: it opens at the period read's start (or the last of its kind and key's end).
    static final String START_NOT_SEEN = "start not seen";
    /// No end seen: it closes at the next start of its kind and key, or the period read's end.
    static final String END_NOT_SEEN = "end not seen";

    private TimelinePeriods() {
        // Static only.
    }

    /// Works out the periods.
    ///
    /// @param events     Every event of the timeline; only the periods' starts and ends are read.
    /// @param classified The automatic sources' events as classified, by event id: their periods and keys.
    /// @param fromMs     The start of the period read.
    /// @param toMs       Its end.
    /// @param options    The sources' names.
    /// @return The periods, in time order, numbered from 1.
    static List<Period> build(final List<Event> events,
                              final Map<String, Classified> classified,
                              final long fromMs,
                              final long toMs,
                              final Options options) {
        final List<Event> sorted = new ArrayList<>(events.stream()
                .filter(event -> event.role().isPeriod())
                .toList());
        sorted.sort(Comparator.comparingLong(Event::time).thenComparingInt(Event::source).thenComparing(Event::id));
        final Map<String, Draft> open = new LinkedHashMap<>();
        final Map<String, Long> lastEnd = new HashMap<>();
        final List<Draft> drafts = new ArrayList<>();
        for (final Event event : sorted) {
            final Classified c = classified.get(event.id());
            final TimelinePeriod period = periodOf(event, c);
            if (period == null) {
                continue;
            }
            final List<String> key = TimelinePeriod.values(period.effectiveKey(), event.host(), c == null
                    ? Map.of()
                    : c.keys());
            final String group = period.kind() + "\u0000" + String.join("\u0000", key);
            if (event.role() == Role.PERIOD_START) {
                final Draft running = open.remove(group);
                if (running != null) {
                    running.close(event.time(), null, END_NOT_SEEN);
                    lastEnd.put(group, event.time());
                }
                final Draft draft = new Draft(period.kind(), key, event.time(), SEEN);
                draft.add(event, c, options, true);
                open.put(group, draft);
                drafts.add(draft);
            } else {
                Draft draft = open.remove(group);
                if (draft == null) {
                    draft = new Draft(period.kind(), key, Math.min(event.time(), lastEnd.getOrDefault(group,
                            fromMs)), START_NOT_SEEN);
                    drafts.add(draft);
                }
                draft.add(event, c, options, false);
                draft.close(event.time(), event, SEEN);
                lastEnd.put(group, event.time());
            }
        }
        open.values().forEach(draft -> draft.close(Math.max(draft.start, toMs), null, END_NOT_SEEN));
        drafts.sort(Comparator.comparingLong((Draft draft) -> draft.start).thenComparing(draft -> draft.kind));
        final List<Period> periods = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            periods.add(drafts.get(i).period(i + 1));
        }
        return periods;
    }

    /// @param periods The periods.
    /// @param from    A span's start.
    /// @param to      Its end.
    /// @return The periods overlapping it, in time order.
    static List<Period> during(final List<Period> periods, final long from, final long to) {
        return NullSafe.list(periods).stream()
                .filter(period -> period.start() <= to && period.end() >= from)
                .toList();
    }

    // An event's period: its classification's, else its kind's with the kind's own key.
    private static TimelinePeriod periodOf(final Event event, final Classified c) {
        if (c != null && c.period() != null) {
            return c.period();
        }
        final String kind = TimelinePeriod.kindOf(event.kind());
        return kind == null
                ? null
                : new TimelinePeriod(kind, null);
    }


    // --------------------------------------------------------------------------------


    /// A period: the facts are the code's, the description the model's.
    ///
    /// @param number      Its number, from 1 in time order.
    /// @param kind        Its kind, e.g. `remote_session`.
    /// @param key         The values of its key, in the key's order; empty for the kind alone.
    /// @param start       When it started.
    /// @param end         When it ended.
    /// @param startSeen   [#SEEN] or [#START_NOT_SEEN].
    /// @param endSeen     [#SEEN] or [#END_NOT_SEEN].
    /// @param hosts       The hosts of its events.
    /// @param sources     The sources of its events, by name.
    /// @param eventIds    Its evidence: the ids of its start and end events.
    /// @param refs        Its evidence by StreamId:EventId where its events have them, else their ids.
    /// @param startEvent  The type of the event it starts at, or null where its start wasn't seen.
    /// @param endEvent    The type of the event it ends at, or null where its end wasn't seen.
    /// @param keys        Its events' other values (their keys, the first of each path), for its wording.
    /// @param description The model's description ([PeriodWording]), or null for none.
    record Period(int number,
                  String kind,
                  List<String> key,
                  long start,
                  long end,
                  String startSeen,
                  String endSeen,
                  List<String> hosts,
                  List<String> sources,
                  List<String> eventIds,
                  List<String> refs,
                  String startEvent,
                  String endEvent,
                  Map<String, String> keys,
                  String description) {

        /// Copies the collections.
        Period {
            key = List.copyOf(key);
            hosts = List.copyOf(hosts);
            sources = List.copyOf(sources);
            eventIds = List.copyOf(eventIds);
            refs = List.copyOf(refs);
            keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
        }

        /// @return Its id, e.g. `pr3`.
        String id() {
            return "pr" + number;
        }

        /// @return Its key's values as text, e.g. `host01`; empty for the kind alone.
        String keyText() {
            return String.join(", ", key);
        }

        /// @return Its kind in words, e.g. `remote session`.
        String words() {
            return new TimelinePeriod(kind, null).words();
        }

        /// @return Its plain label, where no model words it, e.g. `remote session, host01`.
        String label() {
            return key.isEmpty()
                    ? words()
                    : words() + ", " + keyText();
        }

        /// @return What it is shown as: the model's description, else its label.
        String shown() {
            return description != null
                    ? description
                    : label();
        }

        /// @return True if either end wasn't seen.
        boolean uncertain() {
            return !SEEN.equals(startSeen) || !SEEN.equals(endSeen);
        }

        /// @param newDescription The model's description, checked, or null.
        /// @return It so described.
        Period described(final String newDescription) {
            return new Period(number, kind, key, start, end, startSeen, endSeen, hosts, sources, eventIds, refs,
                    startEvent, endEvent, keys, newDescription);
        }
    }


    // --------------------------------------------------------------------------------


    // A period while it is worked out.
    private static final class Draft {

        private final String kind;
        private final List<String> key;
        private final long start;
        private final String startSeen;
        private final Set<String> hosts = new LinkedHashSet<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private final List<String> eventIds = new ArrayList<>();
        private final List<String> refs = new ArrayList<>();
        private final Map<String, String> keys = new LinkedHashMap<>();
        private String startEvent;
        private String endEvent;
        private long end;
        private String endSeen;

        Draft(final String kind, final List<String> key, final long start, final String startSeen) {
            this.kind = kind;
            this.key = key;
            this.start = start;
            this.startSeen = startSeen;
        }

        void add(final Event event, final Classified c, final Options options, final boolean isStart) {
            NullSafe.consume(event.host(), hosts::add);
            sources.add(options.sourceName(event.source()));
            eventIds.add(event.id());
            refs.add(event.ref());
            if (c != null) {
                c.keys().forEach(keys::putIfAbsent);
            }
            final String type = TimelineSpans.typeName(event, c);
            if (isStart) {
                startEvent = type;
            } else {
                endEvent = type;
            }
        }

        void close(final long time, final Event endEventSeen, final String seen) {
            end = Math.max(start, time);
            endSeen = seen;
            if (endEventSeen == null) {
                endEvent = null;
            }
        }

        Period period(final int number) {
            return new Period(number, kind, key, start, end, startSeen, endSeen, List.copyOf(hosts),
                    List.copyOf(sources), eventIds, refs, startEvent, endEvent, keys, null);
        }
    }
}
