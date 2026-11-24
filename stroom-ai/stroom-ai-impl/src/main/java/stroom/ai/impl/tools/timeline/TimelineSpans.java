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

import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.TimelineSessions.Action;
import stroom.ai.impl.tools.timeline.TimelineSessions.By;
import stroom.ai.impl.tools.timeline.TimelineSessions.Daily;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Flag;
import stroom.ai.impl.tools.timeline.TimelineSessions.FlagName;
import stroom.ai.impl.tools.timeline.TimelineSessions.Options;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.tools.timeline.TimelineSessions.Session;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/// The spans of a timeline beyond the focus sessions ([TimelineSessions]), worked out in code: so a timeline is
/// made from any source, not only one that records window focus (seen in chat 190: a user seen only in web proxy
/// logs got no session at all).
///
/// - **Pairs** ([PairRule]): an end closes the latest open start of the same key (the lane, and the value at the
///   pair's key path); a start with no end runs until the lane goes idle (the cap, or an idle start) or the period
///   ends, marked uncertain; either unpaired is flagged. Pairs are applied in order (a skill's, the call's, the
///   schema's, those inferred), each event used by the first that takes it.
/// - **Activity periods**: the actions in no focus session or pair, close in time (gaps under the idle cap), in a
///   lane (the host, else the source's name), labelled by the application, else the object (a URL's domain, a
///   path's last part), else the action's kind, split where the label changes. They show when the user was active
///   in a source, not what had focus. An action is in a pair when it falls in its span on its lane and has its label.
///
/// Focus sessions are unchanged, and win: only what they leave becomes a pair's or an activity period.
final class TimelineSpans {

    /// What an activity period is said to show.
    static final String ACTIVITY_MEANS = "when the user was active in that source, not what had focus";
    /// The longest label of an activity period.
    static final int MAX_LABEL_CHARS = 80;

    private TimelineSpans() {
        // Static only.
    }

    /// Works out the spans.
    ///
    /// @param timeline   The focus sessions and the actions' attachment, from [TimelineSessions#build].
    /// @param events     Every event of the timeline.
    /// @param classified The automatic sources' events as classified, by event id.
    /// @param pairs      The pairs, in the order they are applied.
    /// @param options    The cap, the period's end, the zone and the sources' names.
    /// @return The spans of every kind, the timeline with the activity periods as sessions (for the
    ///         characterisation, the daily totals and the active time), every action's attachment, the pairs'
    ///         uses and the flags they raise.
    static Built build(final Timeline timeline,
                       final List<Event> events,
                       final Map<String, Classified> classified,
                       final List<PairRule> pairs,
                       final Options options) {
        final List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(Event::time).thenComparingInt(Event::source).thenComparing(Event::id));
        final int focusCount = timeline.sessions().size();
        final Map<FlagName, List<String>> flags = new LinkedHashMap<>();
        final Set<String> consumed = new HashSet<>();
        final List<Draft> pairDrafts = new ArrayList<>();
        final List<PairUse> uses = new ArrayList<>();
        for (final PairRule rule : pairs) {
            uses.add(pair(rule, sorted, classified, consumed, pairDrafts, flags, options));
        }

        // The actions in no focus session, as candidates for pairs (by label) and activity periods.
        final Map<String, Action> loose = new LinkedHashMap<>();
        for (final Action action : timeline.actions()) {
            if (action.session() == 0 && !consumed.contains(action.event().id())) {
                loose.put(action.event().id(), action);
            }
        }
        final Map<String, Draft> inPair = new HashMap<>();
        for (final Action action : loose.values()) {
            final Event event = action.event();
            final String lane = lane(event, options);
            final String label = label(event, classified.get(event.id()));
            pairDrafts.stream()
                    .filter(draft -> draft.lane.equals(lane) && draft.start <= event.time()
                                     && event.time() <= draft.end && draft.label.equalsIgnoreCase(label))
                    .findFirst()
                    .ifPresent(draft -> {
                        inPair.put(event.id(), draft);
                        draft.actions.add(event.id());
                    });
        }
        final List<Draft> activityDrafts = activities(loose.values().stream()
                .filter(action -> !inPair.containsKey(action.event().id()))
                .map(Action::event)
                .toList(), sorted, classified, options);

        // Numbered after the focus sessions: the activity periods, then the pairs.
        final List<Span> spans = new ArrayList<>();
        for (final Session session : timeline.sessions()) {
            spans.add(new Span(session.number(), Kind.FOCUS, session.start(), session.end(), NullSafe.string(
                    session.process()), session.host(), session.host(), sourcesOf(session, events, options),
                    session.eventIds(), "focus", session.startMark() != null
                            ? session.startMark()
                            : NullSafe.string(session.endMark())));
        }
        int number = focusCount;
        final List<Session> activitySessions = new ArrayList<>();
        final Map<String, Integer> attachedTo = new HashMap<>();
        activityDrafts.sort(Comparator.comparingLong((Draft draft) -> draft.start).thenComparing(draft -> draft.lane));
        for (final Draft draft : activityDrafts) {
            number++;
            draft.number = number;
            spans.add(draft.span(Kind.ACTIVITY));
            activitySessions.add(draft.session());
            final int at = number;
            draft.actions.forEach(id -> attachedTo.put(id, at));
        }
        pairDrafts.sort(Comparator.comparingLong((Draft draft) -> draft.start).thenComparing(draft -> draft.lane));
        final Map<String, Integer> inPairNumber = new HashMap<>();
        for (final Draft draft : pairDrafts) {
            number++;
            draft.number = number;
            spans.add(draft.span(Kind.PAIR));
            final int at = number;
            draft.actions.forEach(id -> inPairNumber.put(id, at));
            // Its own start and end are its too.
            draft.eventIds.forEach(id -> inPairNumber.putIfAbsent(id, at));
        }
        spans.sort(Comparator.comparingLong(Span::start).thenComparing(Span::lane).thenComparingInt(Span::number));

        final List<Action> attached = new ArrayList<>();
        final List<Action> forCharacterising = new ArrayList<>();
        for (final Action action : timeline.actions()) {
            final String id = action.event().id();
            if (attachedTo.containsKey(id)) {
                final Action inActivity = new Action(action.event(), attachedTo.get(id), By.ACTIVITY);
                attached.add(inActivity);
                forCharacterising.add(inActivity);
            } else if (inPairNumber.containsKey(id)) {
                attached.add(new Action(action.event(), inPairNumber.get(id), By.PAIR));
                forCharacterising.add(action);
            } else {
                attached.add(action);
                forCharacterising.add(action);
            }
        }
        final List<Session> sessions = new ArrayList<>(timeline.sessions());
        sessions.addAll(activitySessions);
        final List<Flag> allFlags = new ArrayList<>(timeline.flags());
        flags.forEach((name, examples) -> allFlags.add(new Flag(name, examples.size(),
                examples.subList(0, Math.min(TimelineSessions.EXAMPLES, examples.size())))));
        allFlags.sort(Comparator.comparingInt(flag -> flag.name().ordinal()));
        final Timeline combined = new Timeline(sessions, timeline.windows(), forCharacterising,
                daily(timeline.daily(), activitySessions, options.zone()), allFlags);
        return new Built(spans, combined, attached, uses);
    }

    // ------------------------------------------------------------------------------------------------
    // Pairs

    private static PairUse pair(final PairRule rule,
                                final List<Event> sorted,
                                final Map<String, Classified> classified,
                                final Set<String> consumed,
                                final List<Draft> drafts,
                                final Map<FlagName, List<String>> flags,
                                final Options options) {
        final Map<String, Deque<Event>> open = new LinkedHashMap<>();
        int made = 0;
        int unpairedStarts = 0;
        int unpairedEnds = 0;
        final List<Event> used = new ArrayList<>();
        for (final Event event : sorted) {
            final Classified c = classified.get(event.id());
            if (c == null || consumed.contains(event.id())
                || (rule.source() >= 0 && rule.source() != event.source())) {
                continue;
            }
            final boolean isStart = c.isType(rule.start());
            final boolean isEnd = !isStart && c.isType(rule.end());
            if (!isStart && !isEnd) {
                continue;
            }
            final String value = rule.keyOf(c.keys());
            if (value == null) {
                continue;
            }
            used.add(event);
            final String key = lane(event, options) + "\u0000" + value;
            if (isStart) {
                open.computeIfAbsent(key, k -> new ArrayDeque<>()).push(event);
            } else {
                final Deque<Event> starts = open.get(key);
                if (starts == null || starts.isEmpty()) {
                    unpairedEnds++;
                    flag(event, flags, FlagName.UNPAIRED_END, rule.end() + " on " + lane(event, options) + " at "
                                                      + options.at(event.time()) + " with no " + rule.start()
                                                      + " before it");
                } else {
                    final Event start = starts.pop();
                    drafts.add(pairDraft(rule, start, event.time(), null, classified, options, event));
                    made++;
                }
            }
        }
        for (final Deque<Event> starts : open.values()) {
            for (final Event start : starts) {
                unpairedStarts++;
                flag(start, flags, FlagName.UNPAIRED_START, rule.start() + " on " + lane(start, options) + " at "
                                                    + options.at(start.time()) + " with no " + rule.end()
                                                    + " after it");
                drafts.add(pairDraft(rule, start, idleAfter(start, sorted, options), "no end seen: runs until the "
                                                                                     + "lane goes idle or the period "
                                                                                     + "ends", classified, options,
                        null));
                made++;
            }
        }
        used.forEach(event -> consumed.add(event.id()));
        return new PairUse(rule, made, unpairedStarts, unpairedEnds);
    }

    private static Draft pairDraft(final PairRule rule,
                                   final Event start,
                                   final long end,
                                   final String mark,
                                   final Map<String, Classified> classified,
                                   final Options options,
                                   final Event endEvent) {
        final Classified c = classified.get(start.id());
        final String label;
        if (rule.label() != null && rule.label().startsWith("=")) {
            label = rule.label().substring(1);
        } else if (rule.label() != null && c.keys().get(rule.label()) != null) {
            label = c.keys().get(rule.label());
        } else {
            label = label(start, c);
        }
        final Draft draft = new Draft(lane(start, options), start.host(), cut(label), start.time(), Math.max(
                start.time(), end), "pair " + rule.start() + " to " + rule.end() + " (" + rule.originName() + ")",
                mark);
        draft.sources.add(options.sourceName(start.source()));
        draft.eventIds.add(start.id());
        if (endEvent != null) {
            draft.eventIds.add(endEvent.id());
        }
        return draft;
    }

    // When a start with no end stops: the lane's first gap past the cap after it (its last event then, plus the
    // cap), an idle start on it, or the period's end.
    private static long idleAfter(final Event start, final List<Event> sorted, final Options options) {
        final String lane = lane(start, options);
        long last = start.time();
        for (final Event event : sorted) {
            if (event.time() <= start.time() || !lane(event, options).equals(lane)) {
                continue;
            }
            if (event.time() - last > options.capMs()) {
                return Math.min(options.periodEndMs(), last + options.capMs());
            }
            if (event.role() == Role.IDLE_START) {
                return event.time();
            }
            last = event.time();
        }
        return Math.min(options.periodEndMs(), last + options.capMs());
    }

    // Flagged unless its role is flagged by the sessions already: an open without a close, an idle start without
    // an end.
    private static void flag(final Event event,
                             final Map<FlagName, List<String>> flags,
                             final FlagName name,
                             final String example) {
        if (event.role() == Role.ACTION) {
            flags.computeIfAbsent(name, k -> new ArrayList<>()).add(example);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Activity periods

    private static List<Draft> activities(final List<Event> actions,
                                          final List<Event> sorted,
                                          final Map<String, Classified> classified,
                                          final Options options) {
        final Map<String, List<Event>> byLane = new TreeMap<>();
        for (final Event event : actions) {
            byLane.computeIfAbsent(lane(event, options), lane -> new ArrayList<>()).add(event);
        }
        // An idle start (a lock, a logoff) on the lane's host ends its period.
        for (final Event event : sorted) {
            if (event.role() == Role.IDLE_START && event.host() != null && byLane.containsKey(event.host())) {
                byLane.get(event.host()).add(event);
            }
        }
        final List<Draft> drafts = new ArrayList<>();
        for (final Map.Entry<String, List<Event>> lane : byLane.entrySet()) {
            final List<Event> events = lane.getValue();
            events.sort(Comparator.comparingLong(Event::time).thenComparing(Event::id));
            Draft current = null;
            long last = 0;
            for (final Event event : events) {
                if (event.role() == Role.IDLE_START) {
                    if (current != null) {
                        current.end = event.time();
                        drafts.add(current);
                        current = null;
                    }
                    continue;
                }
                final String label = cut(label(event, classified.get(event.id())));
                if (current != null && event.time() - last > options.capMs()) {
                    current.end = last;
                    drafts.add(current);
                    current = null;
                }
                if (current != null && !current.label.equalsIgnoreCase(label)) {
                    // Active until this, in another application or place.
                    current.end = event.time();
                    drafts.add(current);
                    current = null;
                }
                if (current == null) {
                    current = new Draft(lane.getKey(), event.host(), label, event.time(), event.time(),
                            "by: activity", null);
                }
                current.sources.add(options.sourceName(event.source()));
                current.eventIds.add(event.id());
                current.actions.add(event.id());
                if (event.object() != null) {
                    current.titles.add(event.object());
                }
                last = event.time();
            }
            if (current != null) {
                current.end = last;
                drafts.add(current);
            }
        }
        return drafts;
    }

    /// @param event An event.
    /// @param c     It as classified, or null.
    /// @return Its label in an activity period: its application, else its object (a URL's domain, a path's last
    /// part), else its kind.
    static String label(final Event event, final Classified c) {
        final String application = event.process() != null
                ? event.process()
                : NullSafe.get(c, Classified::application);
        if (application != null && !application.isBlank()) {
            return application.strip();
        }
        final String object = event.object() != null
                ? event.object()
                : NullSafe.get(c, Classified::object);
        final String objectLabel = objectLabel(object);
        if (objectLabel != null) {
            return objectLabel;
        }
        return Objects.requireNonNullElse(event.kind(), "activity");
    }

    /// @param object What an event touched, or null.
    /// @return A URL's host, a path's last part, else the object; null for none.
    static String objectLabel(final String object) {
        if (object == null || object.isBlank()) {
            return null;
        }
        final String text = object.strip();
        final int scheme = text.indexOf("://");
        if (scheme > 0) {
            // The URL's host: after its scheme, up to its path, query or fragment, without a user or a port.
            String host = text.substring(scheme + 3);
            for (final char end : new char[]{'/', '?', '#'}) {
                final int at = host.indexOf(end);
                if (at >= 0) {
                    host = host.substring(0, at);
                }
            }
            host = host.substring(host.lastIndexOf('@') + 1);
            if (host.lastIndexOf(':') > host.lastIndexOf(']')) {
                host = host.substring(0, host.lastIndexOf(':'));
            }
            if (!host.isBlank()) {
                return host.toLowerCase(Locale.ROOT);
            }
        }
        final int slash = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
        if (slash >= 0 && slash < text.length() - 1) {
            return text.substring(slash + 1);
        }
        return text;
    }

    private static String cut(final String label) {
        return label.length() > MAX_LABEL_CHARS
                ? label.substring(0, MAX_LABEL_CHARS - 1) + "…"
                : label;
    }

    /// @param event   An event.
    /// @param options The sources' names.
    /// @return Its lane: its host, else its source's name.
    static String lane(final Event event, final Options options) {
        return event.host() != null && !event.host().isBlank()
                ? event.host()
                : options.sourceName(event.source());
    }

    private static String sourcesOf(final Session session, final List<Event> events, final Options options) {
        final Set<String> ids = new HashSet<>(session.eventIds());
        final Set<String> names = new LinkedHashSet<>();
        for (final Event event : events) {
            if (ids.contains(event.id())) {
                names.add(options.sourceName(event.source()));
            }
        }
        return String.join("; ", names);
    }

    // The focus sessions' daily totals with the activity periods': per day (in the zone), each lane's labels' active
    // time and spans, a period over midnight split at it.
    private static List<Daily> daily(final List<Daily> focus, final List<Session> sessions, final ZoneId zone) {
        final Map<String, long[]> totals = new TreeMap<>();
        for (final Daily daily : focus) {
            final long[] total = totals.computeIfAbsent(daily.day() + "\u0000" + daily.host() + "\u0000"
                                                        + daily.process(), key -> new long[2]);
            total[0] += daily.activeMs();
            total[1] += daily.sessions();
        }
        for (final Session session : sessions) {
            long from = session.start();
            boolean first = true;
            do {
                final LocalDate day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
                final long midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                final long to = Math.min(session.end(), midnight);
                final long[] total = totals.computeIfAbsent(day + "\u0000" + session.host() + "\u0000"
                                                            + session.process(), key -> new long[2]);
                total[0] += to - from;
                if (first) {
                    total[1]++;
                    first = false;
                }
                from = to;
            } while (from < session.end());
        }
        final List<Daily> daily = new ArrayList<>();
        totals.forEach((key, total) -> {
            final String[] parts = key.split("\u0000", -1);
            daily.add(new Daily(LocalDate.parse(parts[0]), parts[1], parts[2], total[0], (int) total[1]));
        });
        return daily;
    }


    // --------------------------------------------------------------------------------


    /// A span's kind.
    enum Kind {
        /// A focus session: an application in focus on a host.
        FOCUS,
        /// A pair's span: from a start to its end.
        PAIR,
        /// An activity period: actions close in time in a source.
        ACTIVITY;

        /// @return Its name in the tables.
        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }


    // --------------------------------------------------------------------------------


    /// A span of the detail level.
    ///
    /// @param number   Its number (focus sessions keep theirs).
    /// @param kind     Its kind.
    /// @param start    Its start.
    /// @param end      Its end.
    /// @param label    Its label.
    /// @param lane     Its lane: the host, else the source's name.
    /// @param host     Its host, or null.
    /// @param sources  The sources of its events, by name.
    /// @param eventIds Its events' ids.
    /// @param how      How it was made.
    /// @param mark     Why its start or end is uncertain, or empty.
    record Span(int number,
                Kind kind,
                long start,
                long end,
                String label,
                String lane,
                String host,
                String sources,
                List<String> eventIds,
                String how,
                String mark) {

        /// Copies the list.
        Span {
            eventIds = List.copyOf(eventIds);
        }

        /// @return Its id, as a session's is (`s12`).
        String id() {
            return "s" + number;
        }
    }


    // --------------------------------------------------------------------------------


    /// What a pair made.
    ///
    /// @param rule           The pair.
    /// @param spans          The spans it made.
    /// @param unpairedStarts Its starts with no end.
    /// @param unpairedEnds   Its ends with no start.
    record PairUse(PairRule rule, int spans, int unpairedStarts, int unpairedEnds) {

    }


    // --------------------------------------------------------------------------------


    /// The spans worked out.
    ///
    /// @param spans    Every span, in time order.
    /// @param combined The timeline with the activity periods as sessions (for the characterisation, the daily
    ///                 totals and the active time) and the pairs' flags.
    /// @param actions  Every action, with where it was attached (a focus session, an activity period or a pair).
    /// @param pairs    Each pair's use, in the order applied.
    record Built(List<Span> spans, Timeline combined, List<Action> actions, List<PairUse> pairs) {

        /// Copies the lists.
        Built {
            spans = List.copyOf(spans);
            actions = List.copyOf(actions);
            pairs = List.copyOf(pairs);
        }

        /// @param kind A kind.
        /// @return How many spans are of it.
        long count(final Kind kind) {
            return spans.stream().filter(span -> span.kind() == kind).count();
        }

        /// @return The actions in no session, period or pair.
        long unattached() {
            return actions.stream().filter(action -> action.session() == 0).count();
        }
    }


    // --------------------------------------------------------------------------------


    // A span while it is worked out.
    private static final class Draft {

        private final String lane;
        private final String host;
        private final String label;
        private final long start;
        private final String how;
        private final String mark;
        private final Set<String> sources = new LinkedHashSet<>();
        private final List<String> eventIds = new ArrayList<>();
        private final List<String> actions = new ArrayList<>();
        private final Set<String> titles = new LinkedHashSet<>();
        private long end;
        private int number;

        Draft(final String lane,
              final String host,
              final String label,
              final long start,
              final long end,
              final String how,
              final String mark) {
            this.lane = lane;
            this.host = host;
            this.label = label;
            this.start = start;
            this.end = end;
            this.how = how;
            this.mark = mark;
        }

        Span span(final Kind kind) {
            final List<String> ids = new ArrayList<>(eventIds);
            actions.stream().filter(id -> !ids.contains(id)).forEach(ids::add);
            return new Span(number, kind, start, Math.max(start, end), label, lane, host, String.join("; ",
                    sources), ids, how, mark == null
                    ? ""
                    : mark);
        }

        Session session() {
            return new Session(number, -number, lane, label, start, Math.max(start, end), List.copyOf(titles),
                    Map.of(), actions, eventIds, null, null, "activity");
        }
    }
}
