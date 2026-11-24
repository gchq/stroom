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

import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Placement;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.TimelineSessions.Action;
import stroom.ai.impl.tools.timeline.TimelineSessions.Daily;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Flag;
import stroom.ai.impl.tools.timeline.TimelineSessions.Session;
import stroom.ai.impl.tools.timeline.TimelineSessions.Window;
import stroom.ai.impl.tools.timeline.TimelineSpans.Kind;
import stroom.ai.impl.tools.timeline.TimelineSpans.Span;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The tables a timeline writes to the chat's workspace under `/outputs/timelines/<entity>/`, every run: the focus
/// sessions, the actions with where each was attached, the events with their StreamId and EventId and how each was
/// classified, each day's active time, the window lifetimes, the data-quality flags; the two levels drawn as views,
/// the detail spans (`spans.csv`: focus sessions, pairs and activity periods) and the activities rolled up from them
/// (`activities.csv`, filled when the run characterises); and the characterisation's items. Times are UTC. The spans
/// and actions say each row's role: `actor` for the person's own, `target` for what was done to them
/// (`includeTargets`: an instant each, in a lane of its own per source, labelled by its type and who did it).
final class TimelineTables {

    /// The columns of `spans.csv`.
    static final List<String> SPAN_COLUMNS = List.of("span", "start", "end", "label", "action", "lane", "kind",
            "activity", "activity_label", "host", "source", "events", "how", "uncertain", "role");
    /// The columns of `activities.csv`.
    static final List<String> ACTIVITY_COLUMNS = List.of("activity", "activity_label", "day", "start", "end",
            "active_minutes", "spans", "actions", "lanes", "evidence");
    // The columns of spans.csv counted for how dense a view is: its lane and its source.
    private static final int SPAN_LANE = SPAN_COLUMNS.indexOf("lane");
    private static final int SPAN_SOURCE = SPAN_COLUMNS.indexOf("source");

    private TimelineTables() {
        // Static only.
    }

    /// Writes the tables.
    ///
    /// @param workspace The chat's workspace.
    /// @param folder    The timeline's folder.
    /// @param run       The run.
    /// @return The tables written.
    static Files write(final SessionWorkspace workspace, final String folder, final TimelineRun run) {
        final String sourceDoc = run.sources().size() == 1
                ? run.sources().getFirst().doc().getUuid()
                : null;
        final Characterised characterised = run.characterised();
        final Map<String, String> labels = new LinkedHashMap<>();
        if (characterised != null) {
            characterised.activities().forEach(activity -> labels.put(activity.id(), activity.label()));
        }

        final List<List<String>> sessionRows = new ArrayList<>();
        for (final Session session : run.timeline().sessions()) {
            final String activity = activityOf(characterised, session.id());
            sessionRows.add(List.of(session.id(), NullSafe.string(session.host()), NullSafe.string(session.process()),
                    iso(session.start()), iso(session.end()), minutes(session.activeMs()),
                    String.join("; ", session.titles().stream().limit(3).toList()), kinds(session.actionsByKind()),
                    NullSafe.string(session.startMark()), NullSafe.string(session.endMark()), session.endReason(),
                    activity, activity.isEmpty()
                            ? ""
                            : labels.getOrDefault(activity, activity)));
        }
        final WorkspacePath sessionsPath = path(folder, ActivityTimelineTool.SESSIONS_FILE);
        ToolTables.write(workspace, sessionsPath, List.of("session", "host", "process", "start", "end",
                "active_minutes", "titles", "actions", "start_mark", "end_mark", "ended_by", "activity",
                "activity_label"), sessionRows, sourceDoc);

        final List<List<String>> actionRows = new ArrayList<>();
        for (final Action action : run.built().actions()) {
            final Event event = action.event();
            actionRows.add(List.of(iso(event.time()), NullSafe.string(event.host()), NullSafe.string(event.process()),
                    NullSafe.string(event.kind()), NullSafe.string(event.object()), action.session() == 0
                            ? ""
                            : "s" + action.session(), action.by() == null
                            ? ""
                            : action.by().toString(), run.sourceName(event.source()),
                    NullSafe.string(event.streamId()), NullSafe.string(event.eventId()), TimelineTargets.ACTOR));
        }
        final List<TimelineTargets.Done> done = run.targets() == null
                ? List.of()
                : run.targets().read().events();
        for (final TimelineTargets.Done event : done) {
            actionRows.add(List.of(iso(event.time()), NullSafe.string(event.host()), "", event.label(),
                    NullSafe.string(event.object()), "", "", event.name(), NullSafe.string(event.streamId()),
                    NullSafe.string(event.eventId()), TimelineTargets.TARGET));
        }
        final WorkspacePath actionsPath = path(folder, ActivityTimelineTool.ACTIONS_FILE);
        ToolTables.write(workspace, actionsPath, List.of("time", "host", "process", "kind", "object", "session", "by",
                "source", "StreamId", "EventId", "role"), actionRows, sourceDoc);

        final List<List<String>> eventRows = new ArrayList<>();
        for (final Event event : run.byId().values().stream()
                .sorted(Comparator.comparingLong(Event::time))
                .toList()) {
            final Classified c = run.classification().byId().get(event.id());
            eventRows.add(List.of(iso(event.time()), run.sourceName(event.source()), event.role().argName(),
                    NullSafe.string(event.kind()), NullSafe.string(event.host()), NullSafe.string(event.process()),
                    NullSafe.string(event.title()), NullSafe.string(event.object()),
                    NullSafe.string(event.streamId()), NullSafe.string(event.eventId()), c == null
                            ? ""
                            : NullSafe.string(c.type()), c == null
                            ? EventXmlClassifier.Route.ROLES.description()
                            : c.route().description()));
        }
        final WorkspacePath eventsPath = path(folder, ActivityTimelineTool.EVENTS_FILE);
        ToolTables.write(workspace, eventsPath, List.of("time", "source", "role", "kind", "host", "process", "title",
                "object", "StreamId", "EventId", "type", "classified_by"), eventRows, sourceDoc);

        final List<List<String>> dailyRows = new ArrayList<>();
        for (final Daily daily : run.built().combined().daily()) {
            dailyRows.add(List.of(daily.day().toString(), daily.host(), NullSafe.string(daily.process()),
                    minutes(daily.activeMs()), String.valueOf(daily.sessions())));
        }
        final WorkspacePath dailyPath = path(folder, ActivityTimelineTool.DAILY_FILE);
        ToolTables.write(workspace, dailyPath, List.of("day", "host", "process", "active_minutes", "sessions"),
                dailyRows, sourceDoc);

        final List<List<String>> windowRows = new ArrayList<>();
        for (final Window window : run.timeline().windows()) {
            windowRows.add(List.of(window.host(), NullSafe.string(window.process()), NullSafe.string(window.window()),
                    window.opened() == null
                            ? ""
                            : iso(window.opened()), window.closed() == null
                            ? ""
                            : iso(window.closed())));
        }
        final WorkspacePath windowsPath = path(folder, ActivityTimelineTool.WINDOWS_FILE);
        ToolTables.write(workspace, windowsPath, List.of("host", "process", "window", "opened", "closed"), windowRows,
                sourceDoc);

        final List<List<String>> qualityRows = new ArrayList<>();
        for (final Flag flag : run.built().combined().flags()) {
            qualityRows.add(List.of(flag.name().key(), String.valueOf(flag.count()), flag.name().description(),
                    String.join(" | ", flag.examples())));
        }
        final WorkspacePath qualityPath = path(folder, ActivityTimelineTool.QUALITY_FILE);
        ToolTables.write(workspace, qualityPath, List.of("flag", "count", "what", "examples"), qualityRows,
                sourceDoc);

        final List<List<String>> spanRows = new ArrayList<>();
        for (final Span span : run.built().spans()) {
            final String activity = activityOf(characterised, span);
            spanRows.add(List.of(span.id(), iso(span.start()), iso(span.end()), span.shownLabel(), span.action(),
                    span.lane(), span.kind().key(), activity, activity.isEmpty()
                            ? ""
                            : labels.getOrDefault(activity, activity), NullSafe.string(span.host()), span.sources(),
                    String.valueOf(span.eventIds().size()), span.how(), span.mark(), TimelineTargets.ACTOR));
        }
        for (int i = 0; i < done.size(); i++) {
            final TimelineTargets.Done event = done.get(i);
            spanRows.add(List.of("t" + (i + 1), iso(event.time()), iso(event.time()), event.label(), event.label(),
                    event.lane(), TimelineTargets.TARGET, "", "", NullSafe.string(event.host()), event.lane(), "1",
                    TimelineTargets.LANE, "", TimelineTargets.TARGET));
        }
        final WorkspacePath spansPath = path(folder, ActivityTimelineTool.SPANS_FILE);
        ToolTables.write(workspace, spansPath, SPAN_COLUMNS, spanRows, sourceDoc);

        final List<ActivityRow> rolledUp = characterised == null
                ? List.of()
                : rollUp(run.built().spans(), characterised, labels, run.capMs(), run.zone().zone());
        final List<List<String>> activityRows = new ArrayList<>();
        for (final ActivityRow row : rolledUp) {
            activityRows.add(List.of(row.activity(), row.label(), row.day().toString(), iso(row.start()),
                    iso(row.end()), minutes(row.activeMs()), String.join(" ", row.spans()),
                    TimelineSpans.typesText(row.actions()), String.join("; ", row.lanes()),
                    String.join("; ", row.evidence())));
        }
        final WorkspacePath activitiesPath = path(folder, ActivityTimelineTool.ACTIVITIES_FILE);
        ToolTables.write(workspace, activitiesPath, ACTIVITY_COLUMNS, activityRows, sourceDoc);

        WorkspacePath itemsPath = null;
        if (characterised != null) {
            final List<List<String>> itemRows = new ArrayList<>();
            for (final ActivityCharacterisation.Item item : characterised.allItems()) {
                final Placement placement = item.id().startsWith("s") && characterised.sessions()
                        .containsKey(item.id())
                        ? characterised.sessions().get(item.id())
                        : characterised.items().get(item.id());
                itemRows.add(List.of(item.id(), iso(item.time()), item.source(), NullSafe.string(item.host()),
                        item.session() == null
                                ? ""
                                : item.session().id(), placement == null
                                ? ""
                                : placement.activity(), placement == null
                                ? "not classified"
                                : NullSafe.string(placement.how()), placement == null
                                ? ""
                                : NullSafe.string(placement.confidence()), placement == null
                                ? ""
                                : NullSafe.string(placement.reason())));
            }
            itemsPath = path(folder, ActivityTimelineTool.ITEMS_FILE);
            ToolTables.write(workspace, itemsPath, List.of("item", "time", "source", "host", "session", "activity",
                    "how", "confidence", "reason"), itemRows, sourceDoc);
        }
        return new Files(sessionsPath, actionsPath, eventsPath, dailyPath, windowsPath, qualityPath, spansPath,
                activitiesPath, itemsPath, spanRows.size(), activityRows.size(), distinct(spanRows, SPAN_LANE),
                distinct(activityRows, 2), distinct(activityRows, 1), distinct(spanRows, SPAN_SOURCE));
    }

    /// The activities rolled up from their spans: an activity's spans following each other within the idle cap,
    /// across sources and hosts, merge into one interval, keeping its spans and evidence; an interval over midnight
    /// (in the user's zone) is split at it, so a day's row holds that day's.
    ///
    /// @param spans         The spans.
    /// @param characterised The characterisation.
    /// @param labels        Each activity's label.
    /// @param capMs         The idle cap.
    /// @param zone          The user's zone.
    /// @return The intervals, in time order.
    static List<ActivityRow> rollUp(final List<Span> spans,
                                    final Characterised characterised,
                                    final Map<String, String> labels,
                                    final long capMs,
                                    final ZoneId zone) {
        final Map<String, List<Span>> byActivity = new LinkedHashMap<>();
        for (final Span span : spans) {
            final String activity = activityOf(characterised, span);
            // A session's pair (a logon to its logoff) holds the activities, so it isn't one's.
            if (activity.startsWith("a") && (span.kind() != Kind.PAIR || span.isItem())) {
                byActivity.computeIfAbsent(activity, a -> new ArrayList<>()).add(span);
            }
        }
        final Map<String, List<String>> evidence = new LinkedHashMap<>();
        characterised.activities().forEach(activity -> {
            final List<String> items = new ArrayList<>(activity.artifacts().stream().limit(3).toList());
            activity.applications().stream().limit(2).forEach(items::add);
            evidence.put(activity.id(), items);
        });
        final List<ActivityRow> rows = new ArrayList<>();
        byActivity.forEach((activity, list) -> {
            list.sort(Comparator.comparingLong(Span::start));
            Interval current = null;
            for (final Span span : list) {
                if (current != null && span.start() - current.end <= capMs) {
                    current.add(span);
                } else {
                    if (current != null) {
                        rows.addAll(current.days(activity, labels, evidence, zone));
                    }
                    current = new Interval(span);
                }
            }
            if (current != null) {
                rows.addAll(current.days(activity, labels, evidence, zone));
            }
        });
        rows.sort(Comparator.comparingLong(ActivityRow::start).thenComparing(ActivityRow::activity));
        return rows;
    }

    // A span's activity: an item's placement for a span placed as an item (by its item id, never a session's), else
    // its session's.
    private static String activityOf(final Characterised characterised, final Span span) {
        if (!span.isItem()) {
            return activityOf(characterised, span.id());
        }
        if (characterised == null) {
            return "";
        }
        final Placement placement = characterised.items().get(span.itemId());
        return placement == null
                ? ""
                : placement.activity();
    }

    private static String activityOf(final Characterised characterised, final String id) {
        if (characterised == null) {
            return "";
        }
        final Placement placement = characterised.sessions().get(id);
        return placement == null
                ? ""
                : placement.activity();
    }

    private static int distinct(final List<List<String>> rows, final int column) {
        final Set<String> values = new LinkedHashSet<>();
        rows.forEach(row -> values.add(row.get(column)));
        return values.size();
    }

    private static WorkspacePath path(final String folder, final String file) {
        return WorkspacePath.parse(folder + file);
    }

    /// @param ms Epoch milliseconds.
    /// @return It as an ISO 8601 instant, in UTC, as gantt_chart reads it.
    static String iso(final long ms) {
        return Instant.ofEpochMilli(ms).toString();
    }

    /// @param ms A length of time.
    /// @return It in minutes, to a tenth.
    static String minutes(final long ms) {
        return String.valueOf(Math.round(ms / 6_000.0) / 10.0);
    }

    private static String kinds(final Map<String, Integer> byKind) {
        final List<String> parts = new ArrayList<>();
        byKind.forEach((kind, count) -> parts.add(kind + ": " + count));
        return String.join("; ", parts);
    }


    // --------------------------------------------------------------------------------


    /// One interval of an activity on one day.
    ///
    /// @param activity The activity's id.
    /// @param label    Its label.
    /// @param day      The day, in the user's zone.
    /// @param start    The interval's start.
    /// @param end      Its end.
    /// @param activeMs The time its spans cover in it.
    /// @param spans    Its spans' ids.
    /// @param actions  Its spans' event types (a focus session's actions by kind), and how many of each.
    /// @param lanes    Its spans' lanes.
    /// @param evidence The activity's evidence: its top artifacts and applications.
    record ActivityRow(String activity,
                       String label,
                       LocalDate day,
                       long start,
                       long end,
                       long activeMs,
                       List<String> spans,
                       Map<String, Integer> actions,
                       List<String> lanes,
                       List<String> evidence) {

    }


    // --------------------------------------------------------------------------------


    /// The tables written, and how dense each view's is.
    ///
    /// @param sessions       The focus sessions.
    /// @param actions        The actions.
    /// @param events         The events.
    /// @param daily          The daily totals.
    /// @param windows        The window lifetimes.
    /// @param quality        The data-quality flags.
    /// @param spans          The detail spans.
    /// @param activities     The activities rolled up.
    /// @param items          The characterisation's items, or null.
    /// @param spanRows       The detail spans' rows.
    /// @param activityRows   The activities' rows.
    /// @param hostLanes      The detail spans' lanes (hosts, else sources).
    /// @param activityLanes  The activities' days.
    /// @param activityNames  The activities.
    /// @param sourceLanes    The detail spans' sources.
    record Files(WorkspacePath sessions,
                 WorkspacePath actions,
                 WorkspacePath events,
                 WorkspacePath daily,
                 WorkspacePath windows,
                 WorkspacePath quality,
                 WorkspacePath spans,
                 WorkspacePath activities,
                 WorkspacePath items,
                 int spanRows,
                 int activityRows,
                 int hostLanes,
                 int activityLanes,
                 int activityNames,
                 int sourceLanes) {

    }


    // --------------------------------------------------------------------------------


    // An activity's interval as its spans are merged.
    private static final class Interval {

        private final long start;
        private final List<Span> spans = new ArrayList<>();
        private long end;

        Interval(final Span first) {
            this.start = first.start();
            this.end = first.end();
            spans.add(first);
        }

        void add(final Span span) {
            spans.add(span);
            end = Math.max(end, span.end());
        }

        List<ActivityRow> days(final String activity,
                               final Map<String, String> labels,
                               final Map<String, List<String>> evidence,
                               final ZoneId zone) {
            final List<ActivityRow> rows = new ArrayList<>();
            long from = start;
            do {
                final LocalDate day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
                final long midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                final long to = Math.min(end, midnight);
                final long dayFrom = from;
                final List<Span> inDay = spans.stream()
                        .filter(span -> span.end() >= dayFrom && span.start() <= to)
                        .toList();
                final Map<String, Integer> actions = new LinkedHashMap<>();
                inDay.forEach(span -> span.types().forEach((type, count) -> actions.merge(type, count,
                        Integer::sum)));
                rows.add(new ActivityRow(activity, labels.getOrDefault(activity, activity), day, from, to,
                        covered(inDay, from, to), inDay.stream().map(Span::id).toList(), actions, inDay.stream()
                        .map(Span::lane).distinct().toList(), evidence.getOrDefault(activity, List.of())));
                from = to;
            } while (from < end);
            return rows;
        }

        // The time the spans cover within the interval, overlaps counted once.
        private static long covered(final List<Span> spans, final long from, final long to) {
            final List<long[]> cut = spans.stream()
                    .map(span -> new long[]{Math.max(from, span.start()), Math.min(to, span.end())})
                    .filter(range -> range[1] > range[0])
                    .sorted(Comparator.comparingLong(range -> range[0]))
                    .toList();
            long total = 0;
            long reached = Long.MIN_VALUE;
            for (final long[] range : cut) {
                final long begin = Math.max(range[0], reached);
                if (range[1] > begin) {
                    total += range[1] - begin;
                }
                reached = Math.max(reached, range[1]);
            }
            return total;
        }
    }
}
