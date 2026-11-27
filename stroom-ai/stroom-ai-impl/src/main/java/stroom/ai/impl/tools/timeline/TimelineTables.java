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
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Activity;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Item;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Part;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
            "activity", "activity_label", "host", "source", "events", "how", "uncertain", "role", "start_event",
            "end_event");
    /// The columns of `periods.csv`.
    static final List<String> PERIOD_COLUMNS = List.of("period", "kind", "key", "start", "end", "start_seen",
            "end_seen", "label", "description", "lane", "host", "source", "events", "start_event", "end_event");
    /// A period's kind in `spans.csv`.
    static final String PERIOD_KIND = "period";
    /// The columns of `activity-levels.csv`: every level of the activities view, each row with its id, level and
    /// parent, how its members were chosen and how many events it holds.
    static final List<String> LEVEL_COLUMNS = List.of("id", "level", "parent", "activity", "activity_label", "day",
            "start", "end", "bar_label", "lane", "how", "events", "evidence", "start_event", "end_event");
    /// The membership columns of `events.csv`: each event's detail spans, its thematic span or run, its activity,
    /// and the periods it fell in.
    static final List<String> MEMBERSHIP_COLUMNS = List.of("span", "merged", "activity", "periods");
    /// The lane of a level's row in no activity.
    static final String NO_ACTIVITY = "(no activity)";
    /// The columns of `activities.csv`.
    static final List<String> ACTIVITY_COLUMNS = List.of("activity", "activity_label", "day", "start", "end",
            "active_minutes", "spans", "actions", "lanes", "evidence", "start_event", "end_event", "bar_label");
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

        final Membership membership = Membership.of(run, characterised);
        final List<List<String>> eventRows = new ArrayList<>();
        for (final Event event : run.byId().values().stream()
                .sorted(Comparator.comparingLong(Event::time))
                .toList()) {
            final Classified c = run.classification().byId().get(event.id());
            final List<String> eventRow = new ArrayList<>(List.of(iso(event.time()), run.sourceName(event.source()),
                    event.role().argName(),
                    NullSafe.string(event.kind()), NullSafe.string(event.host()), NullSafe.string(event.process()),
                    NullSafe.string(event.title()), NullSafe.string(event.object()),
                    NullSafe.string(event.streamId()), NullSafe.string(event.eventId()), c == null
                            ? ""
                            : NullSafe.string(c.type()), c == null
                            ? EventXmlClassifier.Route.ROLES.description()
                            : c.route().description()));
            eventRow.addAll(membership.of(event));
            eventRows.add(eventRow);
        }
        final WorkspacePath eventsPath = path(folder, ActivityTimelineTool.EVENTS_FILE);
        final List<String> eventColumns = new ArrayList<>(List.of("time", "source", "role", "kind", "host",
                "process", "title", "object", "StreamId", "EventId", "type", "classified_by"));
        eventColumns.addAll(MEMBERSHIP_COLUMNS);
        ToolTables.write(workspace, eventsPath, eventColumns, eventRows, sourceDoc);

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
                    String.valueOf(span.eventIds().size()), span.how(), span.mark(), TimelineTargets.ACTOR,
                    NullSafe.string(span.startEvent()), NullSafe.string(span.endEvent())));
        }
        for (int i = 0; i < done.size(); i++) {
            final TimelineTargets.Done event = done.get(i);
            spanRows.add(List.of("t" + (i + 1), iso(event.time()), iso(event.time()), event.label(), event.label(),
                    event.lane(), TimelineTargets.TARGET, "", "", NullSafe.string(event.host()), event.lane(), "1",
                    TimelineTargets.LANE, "", TimelineTargets.TARGET, "", ""));
        }
        // Each period a span of its own, on its kind's lane (e.g. `remote session`), labelled by its description.
        for (final TimelinePeriods.Period period : NullSafe.list(run.periods())) {
            spanRows.add(List.of(period.id(), iso(period.start()), iso(period.end()), period.shown(),
                    periodAction(period), period.words(), PERIOD_KIND, "", "", String.join("; ", period.hosts()),
                    String.join("; ", period.sources()), String.valueOf(period.eventIds().size()), periodHow(period),
                    periodMark(period), TimelineTargets.ACTOR, NullSafe.string(period.startEvent()),
                    NullSafe.string(period.endEvent())));
        }
        final WorkspacePath spansPath = path(folder, ActivityTimelineTool.SPANS_FILE);
        ToolTables.write(workspace, spansPath, SPAN_COLUMNS, spanRows, sourceDoc);

        final List<ActivityRow> rolledUp = new ArrayList<>(characterised == null
                ? List.of()
                : rollUp(run.built().spans(), characterised, labels, run.capMs(), run.zone().zone()));
        if (characterised != null) {
            // The periods as bands beside the activities, a lane and colour for each kind.
            rolledUp.addAll(periodRows(NullSafe.list(run.periods()), run.zone().zone()));
            rolledUp.sort(Comparator.comparingLong(ActivityRow::start).thenComparing(ActivityRow::activity));
        }
        final List<List<String>> activityRows = new ArrayList<>();
        for (final ActivityRow row : rolledUp) {
            // A minor part's marker ends as it starts (gantt_chart and the dashboard's timeline chart both draw such
            // a row as a marker; the dashboard's leaves out a row with no end), with its own bar label; the
            // activity's label still names its lane and colour.
            activityRows.add(List.of(row.activity(), row.label(), row.day().toString(), iso(row.start()),
                    row.marker()
                            ? iso(row.start())
                            : iso(row.end()), minutes(row.activeMs()), String.join(" ", row.spans()),
                    TimelineSpans.typesText(row.actions()), String.join("; ", row.lanes()),
                    String.join("; ", row.evidence()), NullSafe.string(row.startEvent()),
                    NullSafe.string(row.endEvent()), row.barLabel()));
        }
        final WorkspacePath activitiesPath = path(folder, ActivityTimelineTool.ACTIVITIES_FILE);
        ToolTables.write(workspace, activitiesPath, ACTIVITY_COLUMNS, activityRows, sourceDoc);

        final List<List<String>> levelRows = characterised == null
                ? List.of()
                : levelRows(run, rolledUp, membership, labels);
        final WorkspacePath levelsPath = path(folder, ActivityTimelineTool.LEVELS_FILE);
        ToolTables.write(workspace, levelsPath, LEVEL_COLUMNS, levelRows, sourceDoc);

        final List<List<String>> periodRows = new ArrayList<>();
        for (final TimelinePeriods.Period period : NullSafe.list(run.periods())) {
            periodRows.add(List.of(period.id(), period.kind(), period.keyText(), iso(period.start()),
                    iso(period.end()), period.startSeen(), period.endSeen(), period.label(),
                    NullSafe.string(period.description()), period.words(), String.join("; ", period.hosts()),
                    String.join("; ", period.sources()), String.join(" ", period.refs()),
                    NullSafe.string(period.startEvent()), NullSafe.string(period.endEvent())));
        }
        final WorkspacePath periodsPath = path(folder, ActivityTimelineTool.PERIODS_FILE);
        ToolTables.write(workspace, periodsPath, PERIOD_COLUMNS, periodRows, sourceDoc);

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
                activitiesPath, itemsPath, periodsPath, levelsPath, levelRows.size(), spanRows.size(),
                activityRows.size(),
                distinct(spanRows, SPAN_LANE), distinct(activityRows, 2), distinct(activityRows, 1),
                distinct(spanRows, SPAN_SOURCE));
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
        rows.addAll(minorMarkers(byActivity, characterised, labels, zone));
        rows.addAll(looseRows(byActivity.keySet(), characterised, labels, evidence, capMs, zone));
        rows.sort(Comparator.comparingLong(ActivityRow::start).thenComparing(ActivityRow::activity));
        return rows;
    }

    // The rows of each activity made of loose items alone (messages, commands: in no span of it), so it has a bar
    // and a level-1 row as one of spans has, its runs' parent (found building eval case 115, 2026-11-27): its items
    // following each other within the idle cap make one interval, from the first's time to the last's, split at
    // midnight; its evidence the activity's, else its first items' text.
    private static List<ActivityRow> looseRows(final Set<String> withSpans,
                                               final Characterised characterised,
                                               final Map<String, String> labels,
                                               final Map<String, List<String>> evidence,
                                               final long capMs,
                                               final ZoneId zone) {
        final Map<String, List<Item>> byActivity = new LinkedHashMap<>();
        for (final Item item : characterised.allItems()) {
            final Placement placement = characterised.items().get(item.id());
            if (placement != null && NullSafe.string(placement.activity()).startsWith("a")
                && !withSpans.contains(placement.activity())) {
                byActivity.computeIfAbsent(placement.activity(), a -> new ArrayList<>()).add(item);
            }
        }
        final List<ActivityRow> rows = new ArrayList<>();
        byActivity.forEach((activity, items) -> {
            items.sort(Comparator.comparingLong(Item::time).thenComparing(Item::id));
            final List<String> shown = NullSafe.list(evidence.get(activity)).isEmpty()
                    ? items.stream().map(item -> ActivityCharacterisation.excerpt(item.text(), 60))
                    .filter(text -> !text.isBlank()).distinct().limit(3).toList()
                    : evidence.get(activity);
            List<Item> run = new ArrayList<>();
            for (final Item item : items) {
                if (!run.isEmpty() && item.time() - run.getLast().time() > capMs) {
                    rows.addAll(looseDays(activity, labels, shown, run, zone));
                    run = new ArrayList<>();
                }
                run.add(item);
            }
            rows.addAll(looseDays(activity, labels, shown, run, zone));
        });
        return rows;
    }

    // One interval of loose items, a row a day it covers.
    private static List<ActivityRow> looseDays(final String activity,
                                               final Map<String, String> labels,
                                               final List<String> evidence,
                                               final List<Item> run,
                                               final ZoneId zone) {
        final long start = run.getFirst().time();
        final long end = run.getLast().time();
        final String label = labels.getOrDefault(activity, activity);
        final List<ActivityRow> rows = new ArrayList<>();
        long from = start;
        do {
            final LocalDate day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
            final long midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            final long to = Math.min(end, midnight);
            final long dayFrom = from;
            final List<String> lanes = run.stream()
                    .filter(item -> item.time() >= dayFrom && item.time() <= to)
                    .map(item -> NullSafe.nonBlankStringElse(item.host(), NullSafe.string(item.source())))
                    .distinct()
                    .toList();
            rows.add(new ActivityRow(activity, label, day, from, to, to - from, List.of(), Map.of(), lanes,
                    evidence, null, null, label, false));
            from = to;
        } while (from < end);
        return rows;
    }

    // A marker for each span of an activity's minor part ([Activity#minorParts()]), where it happened, named
    // "<artifact>, in <activity>", so what doesn't shape the activity's name stays in view on its chart (asked for by
    // the user, 2026-11-26; seen in chat 197: a 6-second archive inside seven hours of build work, its bar too short
    // to be drawn). A span is a part's where its label names the part's artifact.
    private static List<ActivityRow> minorMarkers(final Map<String, List<Span>> byActivity,
                                                  final Characterised characterised,
                                                  final Map<String, String> labels,
                                                  final ZoneId zone) {
        final List<ActivityRow> markers = new ArrayList<>();
        for (final Activity activity : characterised.activities()) {
            final List<Span> spans = byActivity.getOrDefault(activity.id(), List.of());
            // One marker a span, for its largest minor part (the parts come the largest first).
            final Set<String> marked = new HashSet<>();
            for (final Part part : activity.minorParts()) {
                final String artifact = part.artifact().toLowerCase(Locale.ROOT);
                for (final Span span : spans) {
                    if (!NullSafe.string(span.label()).toLowerCase(Locale.ROOT).contains(artifact)
                        || !marked.add(span.id())) {
                        continue;
                    }
                    final String label = labels.getOrDefault(activity.id(), activity.label());
                    markers.add(new ActivityRow(activity.id(), label,
                            Instant.ofEpochMilli(span.start()).atZone(zone).toLocalDate(), span.start(), span.end(),
                            span.end() - span.start(), List.of(span.id()), span.types(),
                            List.of(NullSafe.string(span.lane())), List.of(part.artifact()), span.startEvent(),
                            span.endEvent(), part.artifact() + ", in " + label, true));
                }
            }
        }
        return markers;
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

    // Every level's rows of the activities view: the activities and period bands (1), the thematic spans and runs
    // (2), the finest spans and the runs' events (3), each naming its parent and how many events it holds.
    private static List<List<String>> levelRows(final TimelineRun run,
                                                final List<ActivityRow> rolledUp,
                                                final Membership membership,
                                                final Map<String, String> labels) {
        final ZoneId zone = run.zone().zone();
        final Map<String, Span> spansById = new HashMap<>();
        run.built().spans().forEach(span -> spansById.put(span.id(), span));
        final List<List<String>> rows = new ArrayList<>();
        for (final ActivityRow row : rolledUp) {
            final int events = row.activity().startsWith("pr")
                    ? NullSafe.list(run.periods()).stream().filter(period -> period.id().equals(row.activity()))
                    .mapToInt(period -> period.eventIds().size()).sum()
                    : row.spans().isEmpty() && !row.marker()
                            ? looseItems(run.characterised(), row)
                            : row.spans().stream().map(spansById::get).filter(Objects::nonNull)
                            .mapToInt(span -> span.eventIds().size()).sum();
            rows.add(List.of(row.activity(), String.valueOf(ThematicSpans.THEMES), "", row.activity(), row.label(),
                    row.day().toString(), iso(row.start()), row.marker()
                            ? iso(row.start())
                            : iso(row.end()), row.barLabel(), String.join("; ", row.lanes()),
                    row.activity().startsWith("pr")
                            ? "period"
                            : "characterisation", String.valueOf(events), String.join("; ", row.evidence()),
                    NullSafe.string(row.startEvent()), NullSafe.string(row.endEvent())));
        }
        for (final ThematicSpans.Unit unit : membership.units()) {
            final String activity = NullSafe.string(unit.activity());
            final String label = activity.isEmpty()
                    ? NO_ACTIVITY
                    : labels.getOrDefault(activity, activity);
            final List<String> evidence = ThematicSpans.Unit.RUN.equals(unit.kind())
                    ? unit.eventIds().stream().limit(3).map(id -> run.byId().get(id)).filter(Objects::nonNull)
                    .map(Event::ref).toList()
                    : unit.spans().stream().limit(3).map(spansById::get).filter(Objects::nonNull)
                    .map(Span::shownLabel).toList();
            rows.add(List.of(unit.id(), String.valueOf(ThematicSpans.MERGED), activity, activity, label,
                    day(unit.start(), zone), iso(unit.start()), iso(unit.end()), unit.label(), unit.lane(),
                    unit.how(), String.valueOf(membership.eventsOf(unit).size()), String.join("; ", evidence), "",
                    ""));
        }
        for (final Span span : run.built().spans()) {
            if (!ThematicSpans.isFinest(span)) {
                continue;
            }
            final String activity = membership.activityOf(span);
            final String unit = membership.unitOf(span);
            rows.add(List.of(span.id(), String.valueOf(ThematicSpans.FINEST), unit.isEmpty()
                            ? activity
                            : unit, activity, activity.isEmpty()
                            ? NO_ACTIVITY
                            : labels.getOrDefault(activity, activity), day(span.start(), zone), iso(span.start()),
                    iso(span.end()), span.shownLabel(), span.lane(), span.how(),
                    String.valueOf(span.eventIds().size()), "", NullSafe.string(span.startEvent()),
                    NullSafe.string(span.endEvent())));
        }
        for (final ThematicSpans.Unit unit : membership.units()) {
            if (!ThematicSpans.Unit.RUN.equals(unit.kind())) {
                continue;
            }
            final String activity = NullSafe.string(unit.activity());
            for (final String id : unit.eventIds()) {
                final Event event = run.byId().get(id);
                if (event == null) {
                    continue;
                }
                rows.add(List.of(event.ref(), String.valueOf(ThematicSpans.FINEST), unit.id(), activity,
                        activity.isEmpty()
                                ? NO_ACTIVITY
                                : labels.getOrDefault(activity, activity), day(event.time(), zone),
                        iso(event.time()), iso(event.time()), eventLabel(event), NullSafe.nonBlankStringElse(
                                event.host(), run.sourceName(event.source())), "event of the run", "1", event.ref(),
                        "", ""));
            }
        }
        rows.sort(Comparator.comparing((List<String> row) -> row.get(1)).thenComparing(row -> row.get(6)));
        return rows;
    }

    // How many loose items of a row's activity (one of loose items alone) fall in its interval.
    private static int looseItems(final Characterised characterised, final ActivityRow row) {
        if (characterised == null) {
            return 0;
        }
        return (int) characterised.allItems().stream()
                .filter(item -> item.time() >= row.start() && item.time() <= row.end())
                .map(item -> characterised.items().get(item.id()))
                .filter(placement -> placement != null && row.activity().equals(placement.activity()))
                .count();
    }

    // A run's event as its marker is labelled: its text, else what it touched, else its kind, cut short.
    private static String eventLabel(final Event event) {
        return ActivityCharacterisation.excerpt(NullSafe.nonBlankStringElse(event.text(),
                NullSafe.nonBlankStringElse(event.object(), NullSafe.string(event.kind()))), 60);
    }

    private static String day(final long ms, final ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString();
    }

    /// The periods as rows of the activities' table: a band each, split at midnight in the user's zone as the
    /// activities are, its lane and colour its kind's words, its bar labelled by its description.
    ///
    /// @param periods The periods.
    /// @param zone    The user's zone.
    /// @return Their rows, in time order.
    static List<ActivityRow> periodRows(final List<TimelinePeriods.Period> periods, final ZoneId zone) {
        final List<ActivityRow> rows = new ArrayList<>();
        for (final TimelinePeriods.Period period : periods) {
            long from = period.start();
            do {
                final LocalDate day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
                final long midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                final long to = Math.min(period.end(), midnight);
                rows.add(new ActivityRow(period.id(), period.words(), day, from, to, to - from,
                        List.of(period.id()), Map.of(), period.hosts(), period.refs(), from == period.start()
                        ? period.startEvent()
                        : null, to == period.end()
                        ? period.endEvent()
                        : null, period.shown(), false));
                from = to;
            } while (from < period.end());
        }
        return rows;
    }

    // What happened in a period, e.g. `Authenticate/Connect to Authenticate/Disconnect`.
    private static String periodAction(final TimelinePeriods.Period period) {
        return NullSafe.nonBlankStringElse(period.startEvent(), "(start not seen)") + " to "
               + NullSafe.nonBlankStringElse(period.endEvent(), "(end not seen)");
    }

    private static String periodHow(final TimelinePeriods.Period period) {
        return "period " + period.kind() + (period.key().isEmpty()
                ? ""
                : " by " + period.keyText());
    }

    // Why a period's start or end is uncertain, or empty.
    private static String periodMark(final TimelinePeriods.Period period) {
        final List<String> marks = new ArrayList<>();
        if (!TimelinePeriods.SEEN.equals(period.startSeen())) {
            marks.add(period.startSeen());
        }
        if (!TimelinePeriods.SEEN.equals(period.endSeen())) {
            marks.add(period.endSeen());
        }
        return String.join("; ", marks);
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
    /// @param startEvent The type of the event it starts at: of the span starting it, or null where it starts at
    ///                 midnight (a day's cut) or the span doesn't say.
    /// @param endEvent The type of the event it ends at, the same way.
    /// @param barLabel What its bar or marker is labelled on the chart: the activity's label, or a minor part's.
    /// @param marker   True for a minor part's marker, drawn where its span starts.
    record ActivityRow(String activity,
                       String label,
                       LocalDate day,
                       long start,
                       long end,
                       long activeMs,
                       List<String> spans,
                       Map<String, Integer> actions,
                       List<String> lanes,
                       List<String> evidence,
                       String startEvent,
                       String endEvent,
                       String barLabel,
                       boolean marker) {

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
    /// @param periods        The periods.
    /// @param levels         Every level of the activities view.
    /// @param levelRows      Its rows.
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
                 WorkspacePath periods,
                 WorkspacePath levels,
                 int levelRows,
                 int spanRows,
                 int activityRows,
                 int hostLanes,
                 int activityLanes,
                 int activityNames,
                 int sourceLanes) {

    }


    // --------------------------------------------------------------------------------


    // Which spans, thematic spans, runs, activity and periods each event belongs to; and each unit placed in its
    // activity.
    private static final class Membership {

        private final Map<String, List<String>> spans = new HashMap<>();
        private final Map<String, String> merged = new HashMap<>();
        private final Map<String, String> activities = new HashMap<>();
        private final Map<String, String> spanActivities = new HashMap<>();
        private final Map<String, String> spanUnits = new HashMap<>();
        private final Map<String, List<String>> unitEvents = new HashMap<>();
        private final List<ThematicSpans.Unit> units = new ArrayList<>();
        private final List<TimelinePeriods.Period> periods;

        private Membership(final List<TimelinePeriods.Period> periods) {
            this.periods = periods;
        }

        static Membership of(final TimelineRun run, final Characterised characterised) {
            final Membership membership = new Membership(NullSafe.list(run.periods()));
            final Map<String, Span> byId = new HashMap<>();
            for (final Span span : run.built().spans()) {
                byId.put(span.id(), span);
                span.eventIds().forEach(id -> membership.spans.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(span.id()));
                final String activity = TimelineTables.activityOf(characterised, span);
                if (!activity.isEmpty() && activity.startsWith("a")) {
                    membership.spanActivities.put(span.id(), activity);
                    span.eventIds().forEach(id -> membership.activities.putIfAbsent(id, activity));
                }
            }
            if (characterised != null) {
                characterised.items().forEach((id, placement) -> {
                    if (placement.activity() != null && placement.activity().startsWith("a")) {
                        membership.activities.putIfAbsent(id, placement.activity());
                    }
                });
            }
            final ThematicSpans.Levels levels = run.levels() == null
                    ? ThematicSpans.Levels.NONE
                    : run.levels();
            for (final ThematicSpans.Unit unit : levels.units()) {
                final List<String> events = new ArrayList<>();
                final Map<String, Integer> votes = new LinkedHashMap<>();
                if (ThematicSpans.Unit.RUN.equals(unit.kind())) {
                    events.addAll(unit.eventIds());
                    events.forEach(id -> NullSafe.consume(membership.activities.get(id), activity ->
                            votes.merge(activity, 1, Integer::sum)));
                } else {
                    for (final String spanId : unit.spans()) {
                        membership.spanUnits.put(spanId, unit.id());
                        NullSafe.consume(byId.get(spanId), span -> events.addAll(span.eventIds()));
                        NullSafe.consume(membership.spanActivities.get(spanId), activity -> votes.merge(activity,
                                1, Integer::sum));
                    }
                }
                events.forEach(id -> membership.merged.putIfAbsent(id, unit.id()));
                membership.unitEvents.put(unit.id(), List.copyOf(new LinkedHashSet<>(events)));
                final String activity = votes.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                membership.units.add(unit.placed(activity, null));
            }
            return membership;
        }

        List<ThematicSpans.Unit> units() {
            return units;
        }

        List<String> eventsOf(final ThematicSpans.Unit unit) {
            return unitEvents.getOrDefault(unit.id(), List.of());
        }

        String activityOf(final Span span) {
            return spanActivities.getOrDefault(span.id(), "");
        }

        String unitOf(final Span span) {
            return spanUnits.getOrDefault(span.id(), "");
        }

        // An event's membership columns, as MEMBERSHIP_COLUMNS names them.
        List<String> of(final Event event) {
            final List<String> in = periods.stream()
                    .filter(period -> period.eventIds().contains(event.id())
                                      || (period.start() <= event.time() && event.time() <= period.end()))
                    .map(TimelinePeriods.Period::id)
                    .toList();
            return List.of(String.join(" ", spans.getOrDefault(event.id(), List.of())),
                    merged.getOrDefault(event.id(), ""), activities.getOrDefault(event.id(), ""),
                    String.join(" ", in));
        }
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
                        .map(Span::lane).distinct().toList(), evidence.getOrDefault(activity, List.of()),
                        eventAt(inDay, from, true), eventAt(inDay, to, false),
                        labels.getOrDefault(activity, activity), false));
                from = to;
            } while (from < end);
            return rows;
        }

        // The type of the event a span starts (or ends) at at a time, or null if none does.
        private static String eventAt(final List<Span> spans, final long time, final boolean start) {
            for (final Span span : spans) {
                if ((start
                        ? span.start()
                        : span.end()) == time) {
                    final String event = start
                            ? span.startEvent()
                            : span.endEvent();
                    if (event != null) {
                        return event;
                    }
                }
            }
            return null;
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
