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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.concurrent.atomic.AtomicLong;

/// An activity timeline's sessions, worked out in code from a user's events, never by a model: deterministic and
/// generic. It knows roles (a window gained focus, opened or closed; the user went idle or came back; an action),
/// a user's hosts, applications (processes) and times; which events have which role is the call's to say.
///
/// - **Active intervals, per host**: an interval on a process starts at a focus on it and ends at the next focus (any
///   process), an `idle_start` (lock, sleep, logoff), or the cap (no event of any source on the host for the cap's
///   length), the last marked `end: uncertain`. An `idle_end` (unlock, wake, logon) resumes the process focused
///   before. An action on a process with no interval running starts one, its start uncertain (no focus seen).
///   Hosts are lanes of their own, never merged.
/// - **Window lifetimes**: from `open` to `close` of a window (by its window field where the source has one, else its
///   host and process).
/// - **Actions attached**: an action on a host to the interval it falls in, `by: process` where it names the
///   interval's process, else `by: time`; an action naming no host to the interval of any host active then (the
///   latest started), `by: time`; any other is in no session.
/// - **Missing events flagged, never smoothed over**: each interval is still made, with why its start or end is
///   uncertain, and the data-quality flags are counted with examples ([Flag]).
final class TimelineSessions {

    /// The lane of events that name no host and aren't actions.
    static final String NO_HOST = "(no host)";
    /// Two focus events this close on one host, of different applications, are two focused at once.
    static final long FOCUS_TOGETHER_MS = 1_000;
    /// An event this far before another of the same stream with a lower event id is out of order.
    static final long OUT_OF_ORDER_MS = 60_000;
    /// An event this far after the timeline was made is future-dated.
    static final long FUTURE_MS = 5 * 60_000;
    /// Events of two sources naming the same object on a host this close are taken as the same thing seen twice.
    static final long SAME_OBJECT_MS = 10 * 60_000;
    /// Two sources' clocks on a host disagree when the middle of their differences is past this.
    static final long CLOCK_TOLERANCE_MS = 2 * 60_000;
    /// The fewest shared objects that say two sources' clocks disagree.
    static final int CLOCK_PAIRS = 3;
    /// The fewest events of other sources in a source's silence that say it may have dropped events.
    static final int SILENCE_EVENTS = 3;
    /// The examples kept of each flag.
    static final int EXAMPLES = 3;
    // The keys of the sessions as the hosts are walked, unique across timelines.
    private static final AtomicLong KEYS = new AtomicLong();

    private TimelineSessions() {
        // Static only.
    }

    /// Works out the timeline.
    ///
    /// @param events  The user's events, of every source.
    /// @param options The cap, the period's end, now, the user's zone and each source's roles.
    /// @return The sessions, the window lifetimes, the actions with where each was attached, the daily totals and
    ///         the data-quality flags.
    static Timeline build(final List<Event> events, final Options options) {
        final List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(Event::time).thenComparingInt(Event::source)
                .thenComparing(Event::id));
        final Flags flags = new Flags();
        final List<Session> sessions = new ArrayList<>();
        final List<Window> windows = new ArrayList<>();
        final Map<String, Attached> attached = new LinkedHashMap<>();
        final List<Event> hostless = new ArrayList<>();
        final Map<String, List<Event>> byHost = new TreeMap<>();
        for (final Event event : sorted) {
            if (event.host() == null && event.role() == Role.ACTION) {
                hostless.add(event);
            } else {
                byHost.computeIfAbsent(Objects.requireNonNullElse(event.host(), NO_HOST), host -> new ArrayList<>())
                        .add(event);
            }
        }
        for (final Map.Entry<String, List<Event>> host : byHost.entrySet()) {
            new HostWalk(host.getKey(), options, flags, sessions, windows, attached).walk(host.getValue());
        }
        sessions.sort(Comparator.comparingLong(Session::start).thenComparing(Session::host));
        final List<Session> numbered = new ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) {
            numbered.add(sessions.get(i).numbered(i + 1));
        }
        final List<Action> actions = actions(sorted, attached, hostless, numbered);
        final List<Session> withActions = withActions(numbered, actions);
        sourceSilence(byHost, withActions, options, flags);
        outOfOrder(sorted, options, flags);
        clocks(byHost, options, flags);
        return new Timeline(withActions, windows, actions, daily(withActions, options.zone()), flags.list());
    }

    // ------------------------------------------------------------------------------------------------
    // Actions

    // Every action, where it was attached: by host as the hosts were walked, then those naming no host by time.
    private static List<Action> actions(final List<Event> sorted,
                                        final Map<String, Attached> attached,
                                        final List<Event> hostless,
                                        final List<Session> sessions) {
        final Map<Long, Session> byKey = new HashMap<>();
        sessions.forEach(session -> byKey.put(session.key(), session));
        final Set<String> hostlessIds = new HashSet<>();
        hostless.forEach(event -> hostlessIds.add(event.id()));
        final List<Action> actions = new ArrayList<>();
        for (final Event event : sorted) {
            if (event.role() != Role.ACTION) {
                continue;
            }
            if (hostlessIds.contains(event.id())) {
                final Session active = sessions.stream()
                        .filter(session -> session.start() <= event.time() && event.time() < session.end())
                        .max(Comparator.comparingLong(Session::start))
                        .orElse(null);
                actions.add(new Action(event, active == null
                        ? 0
                        : active.number(), active == null
                        ? null
                        : By.TIME));
            } else {
                final Attached at = attached.get(event.id());
                final Session session = at == null
                        ? null
                        : byKey.get(at.sessionKey());
                actions.add(new Action(event, session == null
                        ? 0
                        : session.number(), session == null
                        ? null
                        : at.by()));
            }
        }
        return actions;
    }

    // Each session with the counts of its actions by kind, and the ids of its actions' events.
    private static List<Session> withActions(final List<Session> sessions, final List<Action> actions) {
        final Map<Integer, Map<String, Integer>> kinds = new HashMap<>();
        final Map<Integer, List<String>> ids = new HashMap<>();
        for (final Action action : actions) {
            if (action.session() > 0) {
                kinds.computeIfAbsent(action.session(), number -> new TreeMap<>())
                        .merge(Objects.requireNonNullElse(action.event().kind(), "action"), 1, Integer::sum);
                ids.computeIfAbsent(action.session(), number -> new ArrayList<>()).add(action.event().id());
            }
        }
        return sessions.stream()
                .map(session -> session.withActions(kinds.getOrDefault(session.number(), Map.of()),
                        ids.getOrDefault(session.number(), List.of())))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------
    // Data quality across sources

    // A source with no events on a host for longer than the cap while other sources show the user active there
    // (outside the host's idle periods): likely dropped events, not idleness.
    private static void sourceSilence(final Map<String, List<Event>> byHost,
                                      final List<Session> sessions,
                                      final Options options,
                                      final Flags flags) {
        for (final Map.Entry<String, List<Event>> host : byHost.entrySet()) {
            final List<Event> events = host.getValue();
            final List<long[]> idle = idlePeriods(events);
            final Set<Integer> sources = new LinkedHashSet<>();
            events.forEach(event -> sources.add(event.source()));
            if (sources.size() < 2) {
                continue;
            }
            for (final int source : sources) {
                // Only a source that reports the window in focus on the host is expected to report while the user is
                // active there, so only it can fall silent: others (idle starts and ends, web accesses, messages)
                // are sparse by nature.
                if (events.stream().noneMatch(event -> event.source() == source && event.role() == Role.FOCUS)) {
                    continue;
                }
                final List<Long> times = events.stream()
                        .filter(event -> event.source() == source && isActivity(event.role()))
                        .map(Event::time)
                        .toList();
                final List<Event> others = events.stream()
                        .filter(event -> event.source() != source && isActivity(event.role())
                                         && !inAny(idle, event.time()))
                        .toList();
                final List<Long> bounds = new ArrayList<>(times);
                if (!others.isEmpty()) {
                    bounds.add(others.getLast().time() + 1);
                }
                long from = times.getFirst();
                for (final long to : bounds) {
                    // The silence's time outside the host's idle periods: a lock isn't a source falling silent.
                    if (to - from - idleWithin(idle, from, to) > options.capMs()) {
                        final long start = from;
                        final List<Event> during = others.stream()
                                .filter(event -> event.time() > start && event.time() < to)
                                .toList();
                        if (during.size() >= SILENCE_EVENTS) {
                            final Map<Integer, Integer> bySource = new TreeMap<>();
                            during.forEach(event -> bySource.merge(event.source(), 1, Integer::sum));
                            final List<String> showing = bySource.entrySet().stream()
                                    .sorted((a, b) -> b.getValue().equals(a.getValue())
                                            ? Integer.compare(a.getKey(), b.getKey())
                                            : Integer.compare(b.getValue(), a.getValue()))
                                    .map(entry -> options.sourceName(entry.getKey()))
                                    .toList();
                            flags.add(FlagName.SOURCE_SILENCE, options.sourceName(source) + " has no events on "
                                                               + host.getKey() + " from " + options.at(start)
                                                               + " to " + options.at(during.getLast().time())
                                                               + ", while " + String.join(" and ", showing)
                                                               + (showing.size() == 1
                                    ? " shows"
                                    : " show") + " the user active there (" + during.size() + " events)");
                        }
                    }
                    from = Math.max(from, to);
                }
            }
        }
    }

    private static List<long[]> idlePeriods(final List<Event> events) {
        final List<long[]> periods = new ArrayList<>();
        Long since = null;
        for (final Event event : events) {
            if (event.role() == Role.IDLE_START && since == null) {
                since = event.time();
            } else if (event.role() == Role.IDLE_END && since != null) {
                periods.add(new long[]{since, event.time()});
                since = null;
            }
        }
        if (since != null) {
            periods.add(new long[]{since, Long.MAX_VALUE});
        }
        return periods;
    }

    private static long idleWithin(final List<long[]> periods, final long from, final long to) {
        long idle = 0;
        for (final long[] period : periods) {
            final long start = Math.max(from, period[0]);
            final long end = Math.min(to, period[1]);
            if (end > start) {
                idle += end - start;
            }
        }
        return idle;
    }

    private static boolean inAny(final List<long[]> periods, final long time) {
        return periods.stream().anyMatch(period -> time >= period[0] && time <= period[1]);
    }

    private static boolean isActivity(final Role role) {
        return role == Role.FOCUS || role == Role.ACTION;
    }

    // Events out of time order within their stream (by event id) beyond a tolerance, and events after now.
    private static void outOfOrder(final List<Event> sorted, final Options options, final Flags flags) {
        final Map<String, List<Event>> byStream = new LinkedHashMap<>();
        for (final Event event : sorted) {
            if (event.time() > options.nowMs() + FUTURE_MS) {
                flags.add(FlagName.FUTURE_DATED, options.sourceName(event.source()) + " event " + event.ref()
                                                 + " at " + options.at(event.time()));
            }
            if (number(event.streamId()) != null && number(event.eventId()) != null) {
                byStream.computeIfAbsent(event.source() + "/" + event.streamId(), key -> new ArrayList<>())
                        .add(event);
            }
        }
        for (final List<Event> stream : byStream.values()) {
            final List<Event> byId = new ArrayList<>(stream);
            byId.sort(Comparator.comparingLong(event -> number(event.eventId())));
            long latest = Long.MIN_VALUE;
            for (final Event event : byId) {
                if (latest != Long.MIN_VALUE && event.time() < latest - OUT_OF_ORDER_MS) {
                    flags.add(FlagName.OUT_OF_ORDER, options.sourceName(event.source()) + " event " + event.ref()
                                                     + " at " + options.at(event.time()) + ", after an event of "
                                                     + options.at(latest) + " in its stream");
                }
                latest = Math.max(latest, event.time());
            }
        }
    }

    // Two sources disagreeing on a host's clock: the events of each naming the same object, the middle of their
    // time differences past the tolerance.
    private static void clocks(final Map<String, List<Event>> byHost, final Options options, final Flags flags) {
        for (final Map.Entry<String, List<Event>> host : byHost.entrySet()) {
            final Map<String, List<Long>> differences = new TreeMap<>();
            final List<Event> named = host.getValue().stream()
                    .filter(event -> event.object() != null && !event.object().isBlank())
                    .toList();
            for (int i = 0; i < named.size(); i++) {
                final Event a = named.get(i);
                for (int j = i + 1; j < named.size(); j++) {
                    final Event b = named.get(j);
                    if (b.time() - a.time() > SAME_OBJECT_MS) {
                        break;
                    }
                    if (a.source() != b.source() && sameObject(a.object(), b.object())) {
                        final Event first = a.source() < b.source()
                                ? a
                                : b;
                        final Event second = first == a
                                ? b
                                : a;
                        differences.computeIfAbsent(first.source() + "," + second.source(), key -> new ArrayList<>())
                                .add(second.time() - first.time());
                    }
                }
            }
            for (final Map.Entry<String, List<Long>> pair : differences.entrySet()) {
                final List<Long> values = new ArrayList<>(pair.getValue());
                if (values.size() < CLOCK_PAIRS) {
                    continue;
                }
                Collections.sort(values);
                final long middle = values.get(values.size() / 2);
                if (Math.abs(middle) > CLOCK_TOLERANCE_MS) {
                    final String[] sources = pair.getKey().split(",");
                    flags.add(FlagName.CLOCK_DISAGREEMENT, options.sourceName(Integer.parseInt(sources[1]))
                                                           + " is about " + minutes(Math.abs(middle)) + " "
                                                           + (middle > 0
                            ? "ahead of "
                            : "behind ") + options.sourceName(Integer.parseInt(sources[0])) + " on "
                                                           + host.getKey() + " (" + values.size()
                                                           + " objects seen by both)");
                }
            }
        }
    }

    private static boolean sameObject(final String a, final String b) {
        return a.strip().toLowerCase(Locale.ROOT).equals(b.strip().toLowerCase(Locale.ROOT));
    }

    private static Long number(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static String minutes(final long ms) {
        final long minutes = Math.round(ms / 60_000.0);
        return minutes + (minutes == 1
                ? " minute"
                : " minutes");
    }

    // ------------------------------------------------------------------------------------------------
    // Daily totals

    // Per day (in the user's zone), each host's applications' active time and sessions, a session over midnight
    // split at it.
    private static List<Daily> daily(final List<Session> sessions, final ZoneId zone) {
        final Map<String, long[]> totals = new TreeMap<>();
        for (final Session session : sessions) {
            long from = session.start();
            while (from < session.end()) {
                final LocalDate day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
                final long midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                final long to = Math.min(session.end(), midnight);
                final long[] total = totals.computeIfAbsent(day + "\u0000" + session.host() + "\u0000"
                                                            + session.process(), key -> new long[2]);
                total[0] += to - from;
                if (from == session.start()) {
                    total[1]++;
                }
                from = to;
            }
        }
        final List<Daily> daily = new ArrayList<>();
        totals.forEach((key, total) -> {
            final String[] parts = key.split("\u0000", -1);
            daily.add(new Daily(LocalDate.parse(parts[0]), parts[1], parts[2], total[0], (int) total[1]));
        });
        return daily;
    }


    // --------------------------------------------------------------------------------


    /// What an event means to the timeline.
    enum Role {
        /// A window gained focus.
        FOCUS,
        /// A window opened.
        OPEN,
        /// A window closed.
        CLOSE,
        /// The user went idle: a lock, screensaver, sleep or logoff.
        IDLE_START,
        /// The user came back: an unlock, wake or logon.
        IDLE_END,
        /// Something the user did: a clipboard copy, a file copy, a web access, a process started.
        ACTION;

        /// @return Its name as a call gives it, e.g. `idle_start`.
        String argName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }


    // --------------------------------------------------------------------------------


    /// How an action was attached to its session.
    enum By {
        /// It names the session's process.
        PROCESS,
        /// It fell in the session's time.
        TIME,
        /// It is one of an activity period's actions (an action in no focus session).
        ACTIVITY,
        /// It fell in a pair's span and has its label.
        PAIR;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }


    // --------------------------------------------------------------------------------


    /// The data-quality flags, in the order the result lists them.
    enum FlagName {
        OPEN_WITHOUT_CLOSE("open_without_close", "windows opened and not closed in the period (or still open at "
                                                 + "its end)"),
        CLOSE_WITHOUT_OPEN("close_without_open", "windows closed with no open seen"),
        IDLE_START_WITHOUT_END("idle_start_without_end", "idle starts (lock, sleep, logoff) with activity after "
                                                         + "them and no idle end before it"),
        IDLE_END_WITHOUT_START("idle_end_without_start", "idle ends (unlock, wake, logon) with no idle start before "
                                                         + "them"),
        FOCUS_WITHOUT_OPEN("focus_without_open", "applications focused whose window never opened in the period"),
        ENDED_BY_CAP("ended_by_cap", "intervals ended only by the cap (no event for its length), so their end is "
                                     + "uncertain"),
        SOURCE_SILENCE("source_silence", "a source of window focus with no events on a host while another shows the "
                                         + "user active there: likely dropped events, not idleness"),
        OUT_OF_ORDER("out_of_order", "events out of time order in their stream beyond a minute"),
        FUTURE_DATED("future_dated", "events dated after the timeline was made"),
        CLOCK_DISAGREEMENT("clock_disagreement", "two sources disagreeing on a host's clock"),
        TWO_FOCUSED("two_focused", "two applications focused at once on one host (a missing focus-out)"),
        UNPAIRED_START("unpaired_start", "a pair's starts (e.g. a process started) with no end seen, so their "
                                         + "span runs until the lane goes idle or the period ends"),
        UNPAIRED_END("unpaired_end", "a pair's ends (e.g. a process ended) with no start seen");

        private final String key;
        private final String description;

        FlagName(final String key, final String description) {
            this.key = key;
            this.description = description;
        }

        /// @return Its name in the result, e.g. `open_without_close`.
        String key() {
            return key;
        }

        /// @return What it counts.
        String description() {
            return description;
        }
    }


    // --------------------------------------------------------------------------------


    /// One event of a source, as the timeline reads it.
    ///
    /// @param time       Its time, epoch milliseconds.
    /// @param source     Its source, by its place in the call's sources.
    /// @param id         Unique among the timeline's events: its index and document id.
    /// @param role       What it means.
    /// @param kind       An action's kind (e.g. `clipboard copy`), else null.
    /// @param host       Its host, or null.
    /// @param process    Its application, or null.
    /// @param title      Its window title, or null.
    /// @param window     Its window's id, or null.
    /// @param object     What it touched: a file, a URL, a subject; or null.
    /// @param text       Its free text (a message, an AI chat prompt), or null.
    /// @param streamId   Its stream's id, or null.
    /// @param eventId    Its event id in the stream, or null.
    record Event(long time,
                 int source,
                 String id,
                 Role role,
                 String kind,
                 String host,
                 String process,
                 String title,
                 String window,
                 String object,
                 String text,
                 String streamId,
                 String eventId) {

        /// @return Its stream and event ids where it has them, else its id.
        String ref() {
            return streamId != null && eventId != null
                    ? streamId + ":" + eventId
                    : id;
        }
    }


    // --------------------------------------------------------------------------------


    /// What the timeline is worked out with.
    ///
    /// @param capMs       How long with no event on a host ends its interval.
    /// @param periodEndMs The end of the period read (epoch ms), where an interval still running ends.
    /// @param nowMs       When the timeline is made, for future-dated events.
    /// @param zone        The user's zone, for the days.
    /// @param sourceNames Each source's name, by its place.
    /// @param withOpen    The sources that have an `open` role, so a focus with no open is flagged.
    record Options(long capMs,
                   long periodEndMs,
                   long nowMs,
                   ZoneId zone,
                   List<String> sourceNames,
                   Set<Integer> withOpen) {

        /// Copies the collections.
        Options {
            sourceNames = List.copyOf(sourceNames);
            withOpen = Set.copyOf(withOpen);
        }

        /// @param source A source, by its place.
        /// @return Its name.
        String sourceName(final int source) {
            return source >= 0 && source < sourceNames.size()
                    ? sourceNames.get(source)
                    : "source " + (source + 1);
        }

        /// @param time Epoch milliseconds.
        /// @return It in the user's zone, for an example.
        String at(final long time) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), zone).toLocalDateTime().toString();
        }
    }


    // --------------------------------------------------------------------------------


    /// An active interval on an application.
    ///
    /// @param number        Its number, from 1 in time order (0 until numbered).
    /// @param key           Its key while the hosts are walked.
    /// @param host          Its host.
    /// @param process       Its application.
    /// @param start         When it started.
    /// @param end           When it ended.
    /// @param titles        Its window titles, in the order seen.
    /// @param actionsByKind Its actions' counts by kind.
    /// @param actionIds     Its actions' event ids.
    /// @param eventIds      The ids of the host's events in it (focus, open, close and actions).
    /// @param startMark     Why its start is uncertain, or null.
    /// @param endMark       Why its end is uncertain, or null.
    /// @param endReason     What ended it: `focus`, `idle`, `cap` or `period end`.
    record Session(int number,
                   long key,
                   String host,
                   String process,
                   long start,
                   long end,
                   List<String> titles,
                   Map<String, Integer> actionsByKind,
                   List<String> actionIds,
                   List<String> eventIds,
                   String startMark,
                   String endMark,
                   String endReason) {

        /// Copies the collections.
        Session {
            titles = List.copyOf(titles);
            actionsByKind = Collections.unmodifiableMap(new TreeMap<>(actionsByKind));
            actionIds = List.copyOf(actionIds);
            eventIds = List.copyOf(eventIds);
        }

        /// @return How long it was active, in milliseconds.
        long activeMs() {
            return end - start;
        }

        /// @return Its id in the tables, e.g. `s12`.
        String id() {
            return "s" + number;
        }

        /// @param newNumber Its number in time order.
        /// @return It numbered.
        Session numbered(final int newNumber) {
            return new Session(newNumber, key, host, process, start, end, titles, actionsByKind, actionIds, eventIds,
                    startMark, endMark, endReason);
        }

        /// @param kinds Its actions' counts by kind.
        /// @param ids   Its actions' event ids.
        /// @return It with its actions.
        Session withActions(final Map<String, Integer> kinds, final List<String> ids) {
            return new Session(number, key, host, process, start, end, titles, kinds, ids, eventIds, startMark,
                    endMark, endReason);
        }

        /// @return True if its start or end is uncertain.
        boolean uncertain() {
            return startMark != null || endMark != null;
        }
    }


    // --------------------------------------------------------------------------------


    /// A window's lifetime, from its open to its close.
    ///
    /// @param host    Its host.
    /// @param process Its application.
    /// @param window  Its window, as the source names it (its id, else its title), or null.
    /// @param opened  When it opened, or null if no open was seen.
    /// @param closed  When it closed, or null if no close was seen.
    record Window(String host, String process, String window, Long opened, Long closed) {

    }


    // --------------------------------------------------------------------------------


    /// An action and where it was attached.
    ///
    /// @param event   The action.
    /// @param session The number of its session, or 0 for none.
    /// @param by      How it was attached, or null for none.
    record Action(Event event, int session, By by) {

    }


    // --------------------------------------------------------------------------------


    /// One day's active time on an application of a host.
    ///
    /// @param day      The day, in the user's zone.
    /// @param host     The host.
    /// @param process  The application.
    /// @param activeMs Its active time that day.
    /// @param sessions The sessions that started that day.
    record Daily(LocalDate day, String host, String process, long activeMs, int sessions) {

    }


    // --------------------------------------------------------------------------------


    /// A data-quality flag: what it counts, how many, a few examples.
    ///
    /// @param name     The flag.
    /// @param count    How many.
    /// @param examples Up to [#EXAMPLES] of them.
    record Flag(FlagName name, int count, List<String> examples) {

        /// Copies the list.
        Flag {
            examples = List.copyOf(examples);
        }
    }


    // --------------------------------------------------------------------------------


    /// A timeline.
    ///
    /// @param sessions The active intervals, in time order.
    /// @param windows  The window lifetimes.
    /// @param actions  Every action, in time order, with where it was attached.
    /// @param daily    Each day's active time by host and application.
    /// @param flags    The data-quality flags with a count, in [FlagName] order.
    record Timeline(List<Session> sessions,
                    List<Window> windows,
                    List<Action> actions,
                    List<Daily> daily,
                    List<Flag> flags) {

        /// Copies the lists.
        Timeline {
            sessions = List.copyOf(sessions);
            windows = List.copyOf(windows);
            actions = List.copyOf(actions);
            daily = List.copyOf(daily);
            flags = List.copyOf(flags);
        }

        /// @return The actions in no session.
        List<Action> unattached() {
            return actions.stream().filter(action -> action.session() == 0).toList();
        }

        /// @param name A flag.
        /// @return Its count; 0 if it wasn't raised.
        int count(final FlagName name) {
            return flags.stream().filter(flag -> flag.name() == name).mapToInt(Flag::count).sum();
        }
    }


    // --------------------------------------------------------------------------------


    // Where an action on a host was attached as the host was walked.
    private record Attached(long sessionKey, By by) {

    }


    // --------------------------------------------------------------------------------


    // The flags raised, with their examples.
    private static final class Flags {

        private final Map<FlagName, Integer> counts = new HashMap<>();
        private final Map<FlagName, List<String>> examples = new HashMap<>();

        void add(final FlagName name, final String example) {
            counts.merge(name, 1, Integer::sum);
            final List<String> kept = examples.computeIfAbsent(name, flag -> new ArrayList<>());
            if (kept.size() < EXAMPLES) {
                kept.add(example);
            }
        }

        List<Flag> list() {
            final List<Flag> flags = new ArrayList<>();
            for (final FlagName name : FlagName.values()) {
                if (counts.containsKey(name)) {
                    flags.add(new Flag(name, counts.get(name), examples.get(name)));
                }
            }
            return flags;
        }
    }


    // --------------------------------------------------------------------------------


    // One host's events walked in time order: its intervals and windows, the actions attached, the flags raised.
    private static final class HostWalk {

        private final String host;
        private final Options options;
        private final Flags flags;
        private final List<Session> sessions;
        private final List<Window> windows;
        private final Map<String, Attached> attached;
        private final Map<String, Long> open = new LinkedHashMap<>();
        private final Map<String, String[]> openNames = new HashMap<>();
        private final Set<String> everOpened = new HashSet<>();
        private final Set<String> focusWithoutOpen = new HashSet<>();

        private Running current;
        private String lastProcess;
        private boolean idle;
        private long idleSince;
        // True once the host has shown activity in the period: an idle end before any is the period's start, the
        // user idle since before it, not an idle end with no start.
        private boolean active;

        HostWalk(final String host,
                 final Options options,
                 final Flags flags,
                 final List<Session> sessions,
                 final List<Window> windows,
                 final Map<String, Attached> attached) {
            this.host = host;
            this.options = options;
            this.flags = flags;
            this.sessions = sessions;
            this.windows = windows;
            this.attached = attached;
        }

        void walk(final List<Event> events) {
            for (final Event event : events) {
                if (current != null && event.time() - current.lastActivity > options.capMs()) {
                    end(current.lastActivity + options.capMs(), "cap");
                }
                switch (event.role()) {
                    case FOCUS -> focus(event);
                    case IDLE_START -> idleStart(event);
                    case IDLE_END -> idleEnd(event);
                    case OPEN -> open(event);
                    case CLOSE -> close(event);
                    case ACTION -> action(event);
                    default -> throw new IllegalStateException("Unknown role " + event.role());
                }
                if (current != null) {
                    current.lastActivity = Math.max(current.lastActivity, event.time());
                    current.eventIds.add(event.id());
                    if (event.title() != null && !event.title().isBlank()) {
                        current.titles.add(event.title().strip());
                    }
                }
            }
            if (current != null) {
                final long capped = current.lastActivity + options.capMs();
                if (capped <= options.periodEndMs()) {
                    end(capped, "cap");
                } else {
                    end(Math.max(current.start, options.periodEndMs()), "period end");
                }
            }
            open.forEach((key, opened) -> {
                final String[] names = openNames.get(key);
                windows.add(new Window(host, names[0], names[1], opened, null));
                flags.add(FlagName.OPEN_WITHOUT_CLOSE, names[0] + " on " + host + " opened at " + options.at(opened)
                                                       + (names[1] == null
                        ? ""
                        : " (" + names[1] + ")"));
            });
        }

        private void focus(final Event event) {
            leaveIdle(event);
            active = true;
            if (current != null && event.time() - current.start <= FOCUS_TOGETHER_MS
                && !Objects.equals(current.process, event.process())) {
                flags.add(FlagName.TWO_FOCUSED, current.process + " and " + event.process() + " on " + host + " at "
                                                + options.at(event.time()));
            }
            if (current != null) {
                end(event.time(), "focus");
            }
            if (options.withOpen().contains(event.source()) && event.process() != null) {
                final String application = event.process().toLowerCase(Locale.ROOT);
                if (!everOpened.contains(application) && focusWithoutOpen.add(application)) {
                    flags.add(FlagName.FOCUS_WITHOUT_OPEN, event.process() + " on " + host + ", focused at "
                                                           + options.at(event.time()));
                }
            }
            start(event, null);
        }

        private void idleStart(final Event event) {
            if (idle) {
                flags.add(FlagName.IDLE_START_WITHOUT_END, "on " + host + " at " + options.at(idleSince)
                                                           + ", then again at " + options.at(event.time()));
            }
            if (current != null) {
                end(event.time(), "idle");
            }
            idle = true;
            idleSince = event.time();
        }

        private void idleEnd(final Event event) {
            if (!idle && active) {
                flags.add(FlagName.IDLE_END_WITHOUT_START, "on " + host + " at " + options.at(event.time()));
            }
            idle = false;
            if (current == null && lastProcess != null) {
                start(new Event(event.time(), event.source(), event.id(), Role.FOCUS, null, event.host(), lastProcess,
                        null, null, null, null, null, null), "resumed after idle");
            }
        }

        // Activity while idle: the idle start had no idle end before it.
        private void leaveIdle(final Event event) {
            if (idle) {
                flags.add(FlagName.IDLE_START_WITHOUT_END, "on " + host + " at " + options.at(idleSince)
                                                           + ", activity at " + options.at(event.time()));
                idle = false;
            }
        }

        private void open(final Event event) {
            final String key = windowKey(event);
            if (event.process() != null) {
                everOpened.add(event.process().toLowerCase(Locale.ROOT));
            }
            if (open.containsKey(key)) {
                final String[] names = openNames.get(key);
                windows.add(new Window(host, names[0], names[1], open.get(key), null));
                flags.add(FlagName.OPEN_WITHOUT_CLOSE, names[0] + " on " + host + " opened at "
                                                       + options.at(open.get(key)) + ", opened again at "
                                                       + options.at(event.time()));
            }
            open.put(key, event.time());
            openNames.put(key, new String[]{event.process(), windowName(event)});
        }

        private void close(final Event event) {
            final String key = windowKey(event);
            final Long opened = open.remove(key);
            if (opened == null) {
                flags.add(FlagName.CLOSE_WITHOUT_OPEN, event.process() + " on " + host + " closed at "
                                                       + options.at(event.time()));
            }
            windows.add(new Window(host, event.process(), windowName(event), opened, event.time()));
        }

        private void action(final Event event) {
            leaveIdle(event);
            active = true;
            if (current == null && event.process() != null) {
                start(event, "no focus event seen");
            }
            if (current != null) {
                final By by = event.process() != null && event.process().equalsIgnoreCase(current.process)
                        ? By.PROCESS
                        : By.TIME;
                attached.put(event.id(), new Attached(current.key, by));
            }
        }

        private void start(final Event event, final String startMark) {
            current = new Running(KEYS.incrementAndGet(), event.process(), event.time(), startMark);
            lastProcess = event.process();
        }

        private void end(final long time, final String reason) {
            final String endMark = switch (reason) {
                case "cap" -> "ended by the idle cap: no event for " + minutes(options.capMs());
                case "period end" -> "still running at the period's end";
                default -> null;
            };
            if ("cap".equals(reason)) {
                flags.add(FlagName.ENDED_BY_CAP, Objects.requireNonNullElse(current.process, "?") + " on " + host
                                                 + " from " + options.at(current.start));
            }
            sessions.add(new Session(0, current.key, host, current.process, current.start, Math.max(current.start,
                    time), List.copyOf(current.titles), Map.of(), List.of(), current.eventIds, current.startMark,
                    endMark, reason));
            current = null;
        }

        private static String windowKey(final Event event) {
            return (Objects.requireNonNullElse(event.process(), "") + "\u0000"
                    + Objects.requireNonNullElse(event.window(), "")).toLowerCase(Locale.ROOT);
        }

        private static String windowName(final Event event) {
            return event.window() != null
                    ? event.window()
                    : event.title();
        }
    }


    // --------------------------------------------------------------------------------


    // The interval running on a host as it is walked.
    private static final class Running {

        private final long key;
        private final String process;
        private final long start;
        private final String startMark;
        private final Set<String> titles = new LinkedHashSet<>();
        private final List<String> eventIds = new ArrayList<>();
        private long lastActivity;

        Running(final long key, final String process, final long start, final String startMark) {
            this.key = key;
            this.process = process;
            this.start = start;
            this.startMark = startMark;
            this.lastActivity = start;
        }
    }
}
