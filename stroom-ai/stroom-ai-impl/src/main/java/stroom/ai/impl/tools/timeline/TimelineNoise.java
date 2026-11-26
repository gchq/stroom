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
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Very noisy event types (or a type at one object) of a timeline, found in code before anything else is worked
/// out (decided with the user 2026-11-26), so the timeline shows what the person did, not the background, and no
/// model call is spent on it. Only statistical and structural signals, never a list of names:
///
/// - **rate**: more than [#RATE_PER_HOUR] an hour, over the hours it occurs in;
/// - **periodic**: regular intervals (the spread of its gaps under [#PERIODIC_SPREAD] of their mean);
/// - **repeats**: most of its events the same as the one before (object, title and text);
/// - **unattended**: no window focus on their host within the idle cap before them, and no other type's activity
///   within [#ATTENTION_MS], for most of them;
/// - **service account**: a field of its events saying the account is a service's or the system's;
/// - **many hosts**: on [#HOSTS_AT_ONCE] or more hosts in the same minute, most minutes it occurs;
/// - **background**: requests to the same object with no title, or no title change, one after another.
///
/// A pattern of at least [#MIN_EVENTS] events and [#MIN_SHARE] of the timeline's is noise where it shows two or more
/// kinds of signal (repeats and background requests are one kind), one of them saying its events are alike or not
/// the person's: repeats, a service account, or many hosts at once. Rate and regularity alone never are: a script
/// downloading many different documents at a steady pace is just what an analyst needs to see. Noise is proposed
/// to the user, never dropped unasked; an exclusion the user agreed to is kept ([Exclusion], [#FILE]) and applied in
/// both views on every run before the sessions, the spans and any model call; the result says what was left out and
/// how many.
final class TimelineNoise {

    /// The fewest events a pattern needs to be noise.
    static final int MIN_EVENTS = 30;
    /// The smallest share of the timeline's events a pattern needs to be noise.
    static final double MIN_SHARE = 0.05;
    /// An hour's events past which a pattern's rate is high.
    static final int RATE_PER_HOUR = 60;
    /// The spread (standard deviation over mean) of a pattern's gaps under which it is periodic.
    static final double PERIODIC_SPREAD = 0.1;
    /// The share of a pattern's events that must show a signal for it to count.
    static final double MOST = 0.8;
    /// How near other activity must be for an event to have been attended to.
    static final long ATTENTION_MS = 2 * 60_000L;
    /// The hosts at once, in one minute, that say a pattern runs on many.
    static final int HOSTS_AT_ONCE = 3;
    /// The most patterns proposed in one run.
    static final int MAX_PROPOSED = 8;
    /// The examples given of each.
    static final int EXAMPLES = 3;
    /// The file the exclusions are kept in, in the user's own workspace where there is one.
    static final String FILE = "/outputs/timelines/exclusions.json";

    private static final long HOUR_MS = 3_600_000L;
    private static final long MINUTE_MS = 60_000L;

    private TimelineNoise() {
        // Static only.
    }

    /// Finds the noisy patterns.
    ///
    /// @param events     The timeline's events, classified.
    /// @param classified Their classifications, by event id: their types and keys.
    /// @param names      Each source's name, by its place.
    /// @param known      The exclusions already decided (either way): not proposed again.
    /// @param capMs      The idle cap: window focus this long before an event says the person was there.
    /// @return The noise found, the most events first, at most [#MAX_PROPOSED].
    static List<Noise> find(final List<Event> events,
                            final Map<String, Classified> classified,
                            final List<String> names,
                            final List<Exclusion> known,
                            final long capMs) {
        final List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(Event::time).thenComparing(Event::id));
        final Attention attention = new Attention(sorted, capMs);
        final Map<String, List<Event>> byPattern = new LinkedHashMap<>();
        final Map<String, String[]> patterns = new HashMap<>();
        for (final Event event : sorted) {
            if (event.role() != Role.ACTION && event.role() != Role.OPEN && event.role() != Role.CLOSE) {
                continue;
            }
            final String source = name(names, event.source());
            final String type = typeOf(event, classified.get(event.id()));
            final String key = source + "\u0000" + type;
            byPattern.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
            patterns.putIfAbsent(key, new String[]{source, type});
        }
        final List<Noise> found = new ArrayList<>();
        for (final Map.Entry<String, List<Event>> entry : byPattern.entrySet()) {
            final String[] pattern = patterns.get(entry.getKey());
            final List<Event> group = entry.getValue();
            // Where much of a type is at one object (a heartbeat URL), the type there first, so the person's own
            // events of the type are kept; else the type as a whole.
            Noise noise = null;
            final String object = mostCommonObject(group);
            if (object != null) {
                final List<Event> atObject = group.stream()
                        .filter(event -> object.equals(event.object()))
                        .toList();
                // Only where the type is at other objects too: all of it at one is the type itself.
                if (atObject.size() < group.size()) {
                    noise = judge(pattern[0], pattern[1], object, atObject, sorted, attention, classified);
                }
            }
            if (noise == null) {
                noise = judge(pattern[0], pattern[1], null, group, sorted, attention, classified);
            }
            if (noise != null && known.stream().noneMatch(noise::sameAs)) {
                found.add(noise);
            }
        }
        found.sort(Comparator.comparingInt(Noise::count).reversed().thenComparing(Noise::type));
        final List<Noise> out = new ArrayList<>();
        for (final Noise noise : found.stream().limit(MAX_PROPOSED).toList()) {
            out.add(noise.numbered(out.size() + 1));
        }
        return out;
    }

    /// Leaves out the events of the exclusions the user agreed to.
    ///
    /// @param events     The timeline's events.
    /// @param classified Their classifications, by event id.
    /// @param names      Each source's name, by its place.
    /// @param exclusions The exclusions; those not agreed to are skipped.
    /// @return The events kept, and how many each exclusion left out.
    static Applied apply(final List<Event> events,
                         final Map<String, Classified> classified,
                         final List<String> names,
                         final List<Exclusion> exclusions) {
        final List<Exclusion> agreed = exclusions.stream().filter(Exclusion::excluded).toList();
        if (agreed.isEmpty()) {
            return new Applied(events, Map.of());
        }
        final List<Event> kept = new ArrayList<>();
        final Map<Exclusion, Integer> left = new LinkedHashMap<>();
        for (final Event event : events) {
            final String source = name(names, event.source());
            final String type = typeOf(event, classified.get(event.id()));
            final Exclusion match = agreed.stream()
                    .filter(exclusion -> exclusion.matches(source, type, event.object()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                kept.add(event);
            } else {
                left.merge(match, 1, Integer::sum);
            }
        }
        return new Applied(kept, left);
    }

    /// @param workspace The workspace they are kept in (the user's own where there is one), or null.
    /// @return The exclusions decided, in order; empty where there are none or they can't be read.
    static List<Exclusion> kept(final SessionWorkspace workspace) {
        final WorkspacePath path = WorkspacePath.parse(FILE);
        if (workspace == null || workspace.find(path).isEmpty()) {
            return List.of();
        }
        try {
            final List<?> json = JsonUtil.readValue(String.join("\n", workspace.readLines(path, 0,
                    Integer.MAX_VALUE)), List.class);
            final List<Exclusion> out = new ArrayList<>();
            for (final Object entry : json) {
                if (entry instanceof final Map<?, ?> map && map.get("source") != null && map.get("type") != null) {
                    out.add(new Exclusion(String.valueOf(map.get("source")), String.valueOf(map.get("type")),
                            map.get("object") == null
                                    ? null
                                    : String.valueOf(map.get("object")),
                            !Boolean.FALSE.equals(map.get("excluded")), NullSafe.string((String) map.get("why"))));
                }
            }
            return out;
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    /// Keeps the exclusions decided, so later runs apply them and don't ask again.
    ///
    /// @param workspace  The workspace they are kept in.
    /// @param exclusions Every exclusion decided, either way.
    static void keep(final SessionWorkspace workspace, final List<Exclusion> exclusions) {
        if (workspace == null) {
            return;
        }
        final List<Map<String, Object>> json = new ArrayList<>();
        for (final Exclusion exclusion : exclusions) {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("source", exclusion.source());
            map.put("type", exclusion.type());
            if (exclusion.object() != null) {
                map.put("object", exclusion.object());
            }
            map.put("excluded", exclusion.excluded());
            map.put("why", exclusion.why());
            json.add(map);
        }
        workspace.write(WorkspacePath.parse(FILE), JsonUtil.writeValueAsString(json, true), null);
    }

    // A pattern judged by its signals; null if it isn't noise.
    private static Noise judge(final String source,
                               final String type,
                               final String object,
                               final List<Event> group,
                               final List<Event> all,
                               final Attention attention,
                               final Map<String, Classified> classified) {
        final int count = group.size();
        final double share = all.isEmpty()
                ? 0
                : (double) count / all.size();
        if (count < MIN_EVENTS || share < MIN_SHARE) {
            return null;
        }
        final List<String> signals = new ArrayList<>();
        int kinds = 0;
        final double rate = perHour(group);
        if (rate > RATE_PER_HOUR) {
            signals.add("about " + Math.round(rate) + " an hour");
            kinds++;
        }
        final boolean periodic = periodic(group);
        if (periodic) {
            signals.add("at regular intervals");
            kinds++;
        }
        final boolean repeated = repeats(group) >= MOST;
        if (repeated) {
            signals.add("near-identical repeats");
        }
        final boolean quiet = background(group) >= MOST;
        if (quiet) {
            signals.add("background requests with no page or title change");
        }
        if (repeated || quiet) {
            kinds++;
        }
        if (attention.unattended(group) >= MOST) {
            signals.add("no other activity around them");
            kinds++;
        }
        if (serviceAccount(group, classified) >= MOST) {
            signals.add("a service or system account, as its field says");
            kinds++;
        }
        if (manyHosts(group) >= MOST) {
            signals.add("on " + HOSTS_AT_ONCE + " or more hosts at once");
            kinds++;
        }
        final boolean notThePersons = repeated || quiet || serviceAccount(group, classified) >= MOST
                                      || manyHosts(group) >= MOST;
        if (kinds < 2 || !notThePersons) {
            return null;
        }
        final List<String> examples = group.stream()
                .limit(EXAMPLES)
                .map(event -> NullSafe.nonBlankStringElse(event.object(), type) + " (" + event.ref() + ")")
                .toList();
        return new Noise(0, source, type, object, count, share, signals, examples);
    }

    // Its events an hour, over the hours it occurs in.
    private static double perHour(final List<Event> group) {
        final Set<Long> hours = new HashSet<>();
        group.forEach(event -> hours.add(event.time() / HOUR_MS));
        return (double) group.size() / Math.max(1, hours.size());
    }

    // Its gaps' spread is small against their mean (at least 10 gaps, none of them zero on average).
    private static boolean periodic(final List<Event> group) {
        if (group.size() < 11) {
            return false;
        }
        final long[] gaps = new long[group.size() - 1];
        for (int i = 1; i < group.size(); i++) {
            gaps[i - 1] = group.get(i).time() - group.get(i - 1).time();
        }
        // The middle 80% of the gaps, so an idle night or a missed beat doesn't hide a steady beat.
        Arrays.sort(gaps);
        final int cut = gaps.length / 10;
        final long[] middle = Arrays.copyOfRange(gaps, cut, gaps.length - cut);
        final double mean = Arrays.stream(middle).average().orElse(0);
        if (mean <= 0) {
            return false;
        }
        final double variance = Arrays.stream(middle).mapToDouble(gap -> (gap - mean) * (gap - mean)).average()
                .orElse(0);
        return Math.sqrt(variance) / mean < PERIODIC_SPREAD;
    }

    // The share of its events the same as the one before: object, title and text, with an object or text (events
    // with neither are featureless, not repeats).
    private static double repeats(final List<Event> group) {
        int same = 0;
        for (int i = 1; i < group.size(); i++) {
            final Event a = group.get(i - 1);
            final Event b = group.get(i);
            if ((b.object() != null || b.text() != null) && Objects.equals(a.object(), b.object())
                && Objects.equals(a.title(), b.title()) && Objects.equals(a.text(), b.text())) {
                same++;
            }
        }
        return group.size() < 2
                ? 0
                : (double) same / (group.size() - 1);
    }

    // The share of its events whose keys say their account is a service's or the system's: a key naming an
    // account's or a user's type (by its path, as a field holds it), with such a value.
    private static double serviceAccount(final List<Event> group, final Map<String, Classified> classified) {
        int service = 0;
        for (final Event event : group) {
            final Classified c = classified.get(event.id());
            if (c != null && c.keys().entrySet().stream().anyMatch(TimelineNoise::saysService)) {
                service++;
            }
        }
        return (double) service / group.size();
    }

    private static boolean saysService(final Map.Entry<String, String> key) {
        final String path = key.getKey().toLowerCase(Locale.ROOT);
        final String value = NullSafe.string(key.getValue()).toLowerCase(Locale.ROOT);
        return (path.contains("account") || path.contains("user")) && path.endsWith("type")
               && (value.contains("service") || value.contains("system"));
    }

    // The share of the minutes it occurs in when it is on many hosts at once.
    private static double manyHosts(final List<Event> group) {
        final Map<Long, Set<String>> hostsByMinute = new HashMap<>();
        group.forEach(event -> hostsByMinute.computeIfAbsent(event.time() / MINUTE_MS, m -> new HashSet<>())
                .add(Objects.requireNonNullElse(event.host(), "")));
        final long many = hostsByMinute.values().stream().filter(hosts -> hosts.size() >= HOSTS_AT_ONCE).count();
        return hostsByMinute.isEmpty()
                ? 0
                : (double) many / hostsByMinute.size();
    }

    // The share of its events that are requests to the object of the one before, with no title or the same title.
    private static double background(final List<Event> group) {
        int quiet = 0;
        for (int i = 1; i < group.size(); i++) {
            final Event a = group.get(i - 1);
            final Event b = group.get(i);
            if (b.object() != null && b.object().equals(a.object())
                && (b.title() == null || b.title().equals(a.title()))) {
                quiet++;
            }
        }
        return group.size() < 2
                ? 0
                : (double) quiet / (group.size() - 1);
    }

    private static String mostCommonObject(final List<Event> group) {
        final Map<String, Integer> counts = new HashMap<>();
        group.forEach(event -> NullSafe.consume(event.object(), object -> counts.merge(object, 1, Integer::sum)));
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= MOST * group.size() / 2)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /// @param event An event.
    /// @param c     Its classification, or null.
    /// @return Its type as a pattern names it: its TypeId or schema type, else its kind.
    static String typeOf(final Event event, final Classified c) {
        final String type = c == null
                ? null
                : c.type();
        return NullSafe.nonBlankStringElse(type, NullSafe.nonBlankStringElse(event.kind(), "event"));
    }

    private static String name(final List<String> names, final int source) {
        return source >= 0 && source < names.size()
                ? names.get(source)
                : "source " + (source + 1);
    }


    // --------------------------------------------------------------------------------


    // Where the person's attention was: window focus on a host (within the idle cap after it), and every event
    // implying activity.
    private static final class Attention {

        private final Map<String, long[]> focusByHost = new HashMap<>();
        private final List<Event> active;
        private final long capMs;

        Attention(final List<Event> sorted, final long capMs) {
            this.capMs = capMs;
            final Map<String, List<Long>> focus = new HashMap<>();
            sorted.stream()
                    .filter(event -> event.role() == Role.FOCUS)
                    .forEach(event -> focus.computeIfAbsent(Objects.requireNonNullElse(event.host(), ""),
                            host -> new ArrayList<>()).add(event.time()));
            focus.forEach((host, times) -> focusByHost.put(host, times.stream().mapToLong(Long::longValue)
                    .toArray()));
            active = sorted.stream().filter(event -> event.role().impliesActivity()).toList();
        }

        // The share of the events with no focus on their host within the cap before them (or just after), and no
        // other type's activity within the attention window.
        double unattended(final List<Event> group) {
            final Set<String> ids = new HashSet<>();
            group.forEach(event -> ids.add(event.id()));
            final long[] others = active.stream()
                    .filter(event -> !ids.contains(event.id()) && event.role() != Role.FOCUS)
                    .mapToLong(Event::time)
                    .toArray();
            int alone = 0;
            for (final Event event : group) {
                final long[] focus = focusByHost.getOrDefault(Objects.requireNonNullElse(event.host(), ""),
                        new long[0]);
                if (!within(focus, event.time() - capMs, event.time() + ATTENTION_MS)
                    && !within(others, event.time() - ATTENTION_MS, event.time() + ATTENTION_MS)) {
                    alone++;
                }
            }
            return (double) alone / group.size();
        }

        private static boolean within(final long[] sortedTimes, final long from, final long to) {
            int at = Arrays.binarySearch(sortedTimes, from);
            if (at < 0) {
                at = -at - 1;
            }
            return at < sortedTimes.length && sortedTimes[at] <= to;
        }
    }


    // --------------------------------------------------------------------------------


    /// A noisy pattern, as proposed.
    ///
    /// @param number   Its number in the proposal, from 1.
    /// @param source   Its source's name.
    /// @param type     Its event type.
    /// @param object   The one object it is at, or null for the whole type.
    /// @param count    Its events.
    /// @param share    Its share of the timeline's events.
    /// @param signals  What says it is noise.
    /// @param examples A few of its events, with their StreamId:EventId.
    record Noise(int number,
                 String source,
                 String type,
                 String object,
                 int count,
                 double share,
                 List<String> signals,
                 List<String> examples) {

        /// Copies the lists.
        Noise {
            signals = List.copyOf(signals);
            examples = List.copyOf(examples);
        }

        /// @return It in words for the form, e.g. `Proxy: View at https://x/ping (412 events, 38%; at regular
        /// intervals, near-identical repeats)`.
        String said() {
            return source + ": " + type + (object == null
                    ? ""
                    : " at " + object) + " (" + count + " events, " + Math.round(share * 100) + "%; "
                   + String.join(", ", signals) + ")";
        }

        /// @return Its id in the form and the result, e.g. `noise-2`.
        String id() {
            return "noise-" + number;
        }

        Noise numbered(final int newNumber) {
            return new Noise(newNumber, source, type, object, count, share, signals, examples);
        }

        /// @param excluded True if the user agreed to leave it out.
        /// @return It as an exclusion decided.
        Exclusion decided(final boolean excluded) {
            return new Exclusion(source, type, object, excluded, String.join(", ", signals));
        }

        boolean sameAs(final Exclusion exclusion) {
            return exclusion.source().equals(source) && exclusion.type().equals(type)
                   && Objects.equals(exclusion.object(), object);
        }
    }


    // --------------------------------------------------------------------------------


    /// A pattern the user decided on: left out of every timeline, or kept (so not proposed again).
    ///
    /// @param source   Its source's name.
    /// @param type     Its event type.
    /// @param object   The one object it is at, or null for the whole type.
    /// @param excluded True if its events are left out.
    /// @param why      The signals it was proposed for.
    record Exclusion(String source, String type, String object, boolean excluded, String why) {

        /// @param eventSource An event's source's name.
        /// @param eventType   Its type ([TimelineNoise#typeOf]).
        /// @param eventObject Its object, or null.
        /// @return True if the event is of the pattern.
        boolean matches(final String eventSource, final String eventType, final String eventObject) {
            return source.equals(eventSource) && type.equals(eventType) && (object == null
                                                                            || object.equals(eventObject));
        }

        /// @return It in words, e.g. `Proxy: View at https://x/ping`.
        String said() {
            return source + ": " + type + (object == null
                    ? ""
                    : " at " + object);
        }
    }


    // --------------------------------------------------------------------------------


    /// What a run found and left out.
    ///
    /// @param proposed The noise found and not decided (listed in the result, not left out).
    /// @param applied  The exclusions applied.
    /// @param file     Where the exclusions are kept.
    /// @param declined True if the user was asked in the chat and declined or closed the form: a call again asks
    ///                 again.
    record Outcome(List<Noise> proposed, Applied applied, String file, boolean declined) {

        /// Copies the list.
        Outcome {
            proposed = List.copyOf(proposed);
        }
    }


    // --------------------------------------------------------------------------------


    /// The exclusions applied.
    ///
    /// @param kept The events kept.
    /// @param left How many each exclusion left out.
    record Applied(List<Event> kept, Map<Exclusion, Integer> left) {

        /// @return How many events were left out in all.
        int leftOut() {
            return left.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
