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

import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.sequence.KeyGroupSource;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Activity;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.tools.timeline.TimelineReader.Read;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineRun.EventRange;
import stroom.ai.impl.tools.timeline.TimelineRun.EventsXml;
import stroom.ai.impl.tools.timeline.TimelineRun.SourceNote;
import stroom.ai.impl.tools.timeline.TimelineRun.View;
import stroom.ai.impl.tools.timeline.TimelineRun.XmlRead;
import stroom.ai.impl.tools.timeline.TimelineSessions.Flag;
import stroom.ai.impl.tools.timeline.TimelineSessions.Session;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;
import stroom.ai.impl.tools.timeline.TimelineSpans.Built;
import stroom.ai.impl.tools.timeline.TimelineSpans.Kind;
import stroom.ai.impl.tools.timeline.TimelineSpans.PairUse;
import stroom.ai.impl.tools.timeline.TimelineSpans.Span;
import stroom.ai.impl.tools.timeline.TimelineTables.Files;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/// What `activity_timeline` returns: over MCP JSON (also its structured content), in a chat Markdown, each with the
/// sources and how each index's user field was found, how the events were classified, the spans by kind, the pairs
/// used and borderline, the views and the gantt_chart arguments that draw each (and when one is too dense for one
/// chart), what was learned for the skill, and the data-quality flags.
final class TimelineResult {

    /// The sessions and activities shown in the result; the rest are in the tables.
    static final int SHOWN = 15;
    /// The most bars gantt_chart draws in one chart (as `GanttCharts` keeps).
    static final int MAX_BARS = 200;
    /// The most lanes gantt_chart draws in one chart (as `GanttCharts` keeps).
    static final int MAX_LANES = 20;

    // Why a source's events weren't deduplicated.
    private static final String NOT_DEDUPLICATED = "its index has no StreamId and EventId, so an event read twice "
                                                   + "can't be told";
    // The most types listed for each origin of a source's roles.
    private static final int MAX_TYPES_LISTED = 20;
    // The most days an overview's result lists (the busiest); every day is in its file.
    private static final int OVERVIEW_DAYS_SHOWN = 62;

    private TimelineResult() {
        // Static only.
    }

    /// @param run   The run.
    /// @param files The tables written.
    /// @return The result as JSON.
    static Map<String, Object> structured(final TimelineRun run, final Files files) {
        final Timeline timeline = run.timeline();
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("entity", run.entity());
        map.put("time", Map.of("from", TimelineTables.iso(run.period().fromMs()), "to",
                TimelineTables.iso(run.period().toMs())));
        map.put("idle_cap_minutes", run.capMs() / 60_000);
        map.put("sources_chosen", run.chosen());
        final List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < run.sources().size(); i++) {
            final Map<String, Object> source = new LinkedHashMap<>();
            source.put("name", run.sources().get(i).name());
            source.put("index", run.sources().get(i).doc().getName());
            source.put("events", i < run.read().counts().size()
                    ? run.read().counts().get(i)
                    : 0L);
            source.put("user_field", run.sources().get(i).userField());
            source.put("by", run.sources().get(i).by().label());
            final Map<String, Long> accounts = accounts(run, i);
            if (run.sources().get(i).by() == PersonScope.By.LOGON) {
                source.put("accounts", accounts);
            }
            final SourceNote note = note(run, run.sources().get(i).name());
            if (note != null) {
                NullSafe.consume(note.userFrom(), from -> source.put("user_field_from", from));
                NullSafe.consume(note.transform(), transform -> source.put("user_transform", transform));
                NullSafe.consume(note.hint(), hint -> source.put("hint", hint));
            }
            final Map<String, Object> routes = new LinkedHashMap<>();
            run.classification().routes().getOrDefault(i, Map.of()).forEach((route, count) ->
                    routes.put(route.description(), count));
            source.put("classified_by", routes);
            source.put("fields", fields(run.sources().get(i)));
            final Map<String, Map<String, Long>> byStream = reading(run).byStream().get(i);
            if (byStream != null && !byStream.isEmpty()) {
                source.put("fields_by_pipeline", byStream);
            }
            final Integer duplicates = reading(run).duplicates().get(i);
            if (duplicates != null && duplicates > 0) {
                source.put("duplicates_left_out", duplicates);
            }
            if (reading(run).notDeduplicated().contains(i)) {
                source.put("not_deduplicated", NOT_DEDUPLICATED);
            }
            final Map<String, Set<String>> origins = NullSafe.map(run.classification().roleFrom()).get(i);
            if (origins != null && !origins.isEmpty()) {
                final Map<String, Object> from = new LinkedHashMap<>();
                origins.forEach((origin, types) -> from.put(origin, types.stream().limit(MAX_TYPES_LISTED)
                        .toList()));
                source.put("roles_from", from);
            }
            sources.add(source);
        }
        map.put("sources", sources);
        if (byLogon(run)) {
            map.put("other_accounts", PersonScope.OTHER_ACCOUNTS);
        }
        final List<String> suggested = suggestions(run.notes());
        if (!suggested.isEmpty()) {
            map.put("fields_lines_to_offer", suggested);
        }
        final List<Map<String, Object>> leftOut = run.notes().stream()
                .filter(note -> note.leftOut() != null)
                .map(note -> Map.<String, Object>of("index", note.index(), "why", note.leftOut()))
                .toList();
        if (!leftOut.isEmpty()) {
            map.put("left_out", leftOut);
        }
        map.put("events_read", run.read().events().size());
        map.put("scans", run.read().scans());
        if (!NullSafe.list(run.ranges()).isEmpty()) {
            map.put("none_in_period", run.ranges().stream().map(TimelineResult::range).toList());
        }
        if (run.classification().ignored() > 0) {
            map.put("events_ignored", run.classification().ignored());
        }
        final Map<String, Object> xml = xml(run.xml());
        if (!reading(run).xmlPaths().isEmpty()) {
            xml.put("paths_read", reading(run).xmlPaths().stream().map(paths -> Map.<String, Object>of(
                    "source", paths.source(), "index", paths.index(), "paths", paths.paths(), "events",
                    paths.events(), "suggestion", paths.suggestion())).toList());
        }
        map.put("events_xml", xml);
        if (!reading(run).offers().isEmpty()) {
            map.put("event_types_lines_to_offer", Map.of("by_index", reading(run).offers(), "how",
                    "The event codes classified this run, as an AGENTS doc's ## Event types lines: offer them "
                    + "(propose_instructions, in the index's folder) so later runs take them from the doc."));
        }
        map.put("ended_early", endedEarly(run.read()));
        map.put("span_count", run.built().spans().size());
        map.put("span_hosts", spanHosts(run.built()));
        map.put("session_count", timeline.sessions().size());
        map.put("hosts", timeline.sessions().stream().map(Session::host).distinct().toList());
        map.put("uncertain_sessions", timeline.sessions().stream().filter(Session::uncertain).count());
        map.put("actions", run.built().actions().size());
        map.put("unattached_actions", (int) run.built().unattached());
        final Map<String, Object> spans = new LinkedHashMap<>();
        for (final Kind kind : Kind.values()) {
            spans.put(kind.key(), (int) run.built().count(kind));
        }
        map.put("spans", spans);
        if (run.built().count(Kind.ACTIVITY) > 0) {
            map.put("activity_periods_show", TimelineSpans.ACTIVITY_MEANS);
        }
        map.put("pairs", pairs(run));
        if (!run.borderline().isEmpty()) {
            map.put("borderline_pairs", run.borderline().stream().map(candidate -> borderline(run, candidate))
                    .toList());
        }
        // Active time by lane and label over the period (focus sessions and activity periods), the most first.
        map.put("columns", List.of("host", "process", "sessions", "active_minutes"));
        map.put("rows", byApplication(run.built().combined()).stream()
                .map(row -> List.<Object>of(row.host(), row.process(), row.sessions(),
                        Math.round(row.activeMs() / 6_000.0) / 10.0))
                .toList());
        final Map<String, Object> quality = new LinkedHashMap<>();
        for (final Flag flag : run.built().combined().flags()) {
            quality.put(flag.name().key(), Map.of("count", flag.count(), "what", flag.name().description(),
                    "examples", flag.examples()));
        }
        map.put("data_quality", quality);
        final Map<String, Object> written = new LinkedHashMap<>();
        written.put("sessions", files.sessions().toString());
        written.put("spans", files.spans().toString());
        written.put("activities", files.activities().toString());
        written.put("actions", files.actions().toString());
        written.put("events", files.events().toString());
        written.put("daily", files.daily().toString());
        written.put("windows", files.windows().toString());
        written.put("data_quality", files.quality().toString());
        if (files.items() != null) {
            written.put("items", files.items().toString());
        }
        map.put("files", written);
        map.put("gantt", Map.of("path", files.sessions().toString(), "start", "start", "end", "end", "label",
                "process", "lane", "host"));
        map.put("view", views(run, files));
        if (run.characterised() != null) {
            map.put("characterisation", characterisation(run.characterised(), files, timeline.sessions().size()
                                                                                     + (int) run.built()
                    .count(Kind.ACTIVITY)));
        }
        if (run.learned() != null && (!run.learned().lines().isEmpty() || !run.learned().borderline().isEmpty())) {
            final Map<String, Object> learned = new LinkedHashMap<>();
            learned.put("event_chains", run.learned().lines());
            if (!run.learned().borderline().isEmpty()) {
                learned.put("borderline_to_confirm", run.learned().borderline());
            }
            learned.put("can_record", run.learned().canRecord());
            NullSafe.consume(run.learned().file(), file -> learned.put("file", file.toString()));
            NullSafe.consume(run.learned().offer(), offer -> learned.put("offer", offer));
            map.put("learned", learned);
        }
        if (run.targets() != null) {
            map.put("done_to_them", TimelineTargets.structured(run.targets(), run.zone().zone()));
        }
        map.put("time_zone", run.zone().zone().getId());
        map.put("derived", ActivityTimelineTool.DERIVED);
        map.put("caveat", ActivityTimelineTool.CAVEAT);
        return map;
    }

    /// A long period's overview, in place of a timeline: its events counted per source, more than a timeline reads,
    /// and a day at a time (by source, and by type where the index holds it), for days to be picked.
    ///
    /// @param entity Whose events.
    /// @param period The period.
    /// @param cap    The events a timeline reads.
    /// @param known  The events counted in the period.
    /// @param counts Each source's count, first and last.
    /// @param days   Each source's days.
    /// @param file   The workspace file of every day, source and type.
    /// @param chat   True in a chat (the user picks the days), false over MCP (the calling model does).
    /// @return The result as JSON.
    static Map<String, Object> overview(final String entity,
                                        final TimelineRun.Period period,
                                        final int cap,
                                        final long known,
                                        final List<TimelineOverview.Counted> counts,
                                        final List<TimelineOverview.SourceDays> days,
                                        final String file,
                                        final boolean chat) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("entity", entity);
        map.put("time", Map.of("from", TimelineTables.iso(period.fromMs()), "to", TimelineTables.iso(period.toMs())));
        map.put("overview", true);
        map.put("why", known + " events in the period, more than the " + cap + " a timeline reads (stroom.ai."
                       + "workspace.timelineMaxEvents): none was read; here they are a day at a time.");
        map.put("events_in_period", counts.stream().map(counted -> {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("source", counted.source());
            if (counted.events() < 0) {
                one.put("events", "not counted (its user is matched by a script or by several fields)");
            } else {
                one.put("events", counted.events());
            }
            NullSafe.consume(counted.first(), first -> one.put("first", first));
            NullSafe.consume(counted.last(), last -> one.put("last", last));
            return one;
        }).toList());
        final Map<String, Map<String, Long>> byDay = new TreeMap<>();
        boolean cut = false;
        for (final TimelineOverview.SourceDays source : days) {
            source.byDay().forEach((day, events) -> byDay.computeIfAbsent(day, k -> new LinkedHashMap<>())
                    .put(source.source(), events));
            cut |= source.cut();
        }
        final List<Map<String, Object>> rows = new ArrayList<>();
        byDay.forEach((day, bySource) -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day);
            row.put("events", bySource.values().stream().mapToLong(Long::longValue).sum());
            row.put("by_source", bySource);
            rows.add(row);
        });
        map.put("active_days", byDay.size());
        map.put("days", rows.size() > OVERVIEW_DAYS_SHOWN
                ? rows.stream().sorted(Comparator.comparingLong((Map<String, Object> row) ->
                        (Long) row.get("events")).reversed()).limit(OVERVIEW_DAYS_SHOWN).sorted(
                        Comparator.comparing(row -> (String) row.get("day"))).toList()
                : rows);
        if (rows.size() > OVERVIEW_DAYS_SHOWN) {
            map.put("days_shown", "the " + OVERVIEW_DAYS_SHOWN + " busiest of " + rows.size() + " active days; "
                                  + "every day is in the file");
        }
        if (cut) {
            map.put("cut", "Some days or types were past the rows an overview gives: the file has those given.");
        }
        map.put("file", file);
        map.put("columns", List.of("day", "source", "type", "events"));
        map.put("next", (chat
                ? "Ask the user which days to read in detail"
                : "Pick the days to read in detail") + " (each day's events are above, by type in the file), then "
                        + "call again with timeFrom and timeTo around them, fewer than " + cap + " events in all.");
        return map;
    }

    /// @param overview A long period's overview ([#overview]).
    /// @return It for the chat.
    @SuppressWarnings("unchecked")
    static String overviewMarkdown(final Map<String, Object> overview) {
        final StringBuilder sb = new StringBuilder("**Too many events for one timeline.** ")
                .append(overview.get("why"));
        sb.append("\n\n| day | events | by source |\n| --- | --- | --- |");
        for (final Map<String, Object> row : (List<Map<String, Object>>) overview.get("days")) {
            sb.append("\n| ").append(row.get("day")).append(" | ").append(row.get("events")).append(" | ")
                    .append(((Map<String, Long>) row.get("by_source")).entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""))
                    .append(" |");
        }
        NullSafe.consume(overview.get("days_shown"), shown -> sb.append("\n\n(").append(shown).append(")"));
        sb.append("\n\nEvery day by source and type: `").append(overview.get("file")).append("`.\n\n")
                .append(overview.get("next"));
        return sb.toString();
    }

    /// @param ranges Each source's first and last events at any time, for a period with none.
    /// @return What a result says of them: empty for none.
    static String rangesText(final List<EventRange> ranges) {
        if (NullSafe.list(ranges).isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder("\n\nNone in the period. Their events at any time, per source:");
        for (final EventRange range : ranges) {
            sb.append("\n- ").append(range.source()).append(": ");
            if (range.events() < 0) {
                sb.append("not known (").append(NullSafe.nonBlankStringElse(range.why(), "it couldn't be asked"))
                        .append(')');
            } else if (range.events() == 0) {
                sb.append("none");
            } else {
                sb.append(range.events()).append(" event(s), first ").append(range.first()).append(", last ")
                        .append(range.last());
            }
        }
        return sb.toString();
    }

    private static Map<String, Object> range(final EventRange range) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", range.source());
        if (range.events() >= 0) {
            map.put("events", range.events());
        } else {
            map.put("not_known", NullSafe.nonBlankStringElse(range.why(), "it couldn't be asked"));
        }
        NullSafe.consume(range.first(), first -> map.put("first", first));
        NullSafe.consume(range.last(), last -> map.put("last", last));
        return map;
    }

    // The events of each value a source was filtered on (each logon id), the accounts covered.
    private static Map<String, Long> accounts(final TimelineRun run, final int source) {
        return source < NullSafe.list(run.read().accounts()).size()
                ? run.read().accounts().get(source)
                : Map.of();
    }

    // True if any source was read by logon id.
    private static boolean byLogon(final TimelineRun run) {
        return run.sources().stream().anyMatch(source -> source.by() == PersonScope.By.LOGON);
    }

    private static SourceNote note(final TimelineRun run, final String name) {
        return run.notes().stream()
                .filter(note -> note.leftOut() == null && note.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    // How the run read its sources; none known for a run without it.
    private static TimelineReading reading(final TimelineRun run) {
        return run.reading() == null
                ? TimelineReading.NONE
                : run.reading();
    }

    // The fields a source's events were read by, each thing's: the call's or those mapped for its index.
    private static Map<String, String> fields(final Source source) {
        final Map<String, String> out = new LinkedHashMap<>();
        out.put(InstructionRules.USER_FIELD, source.userField());
        NullSafe.consume(source.hostField(), field -> out.put(InstructionRules.HOST_FIELD, field));
        NullSafe.consume(source.processField(), field -> out.put(InstructionRules.APPLICATION_FIELD, field));
        NullSafe.consume(source.titleField(), field -> out.put(InstructionRules.TITLE_FIELD, field));
        NullSafe.consume(source.objectField(), field -> out.put(InstructionRules.OBJECT_FIELD, field));
        NullSafe.consume(source.textField(), field -> out.put(InstructionRules.TEXT_FIELD, field));
        return out;
    }

    // What the cap left unread, and what to do about it.
    private static String pastCapText(final XmlRead xml, final String capSetting) {
        if (xml.mode() == EventsXml.EACH) {
            return "More than " + xml.cap() + " events" + capSetting + " had Events XML to read ("
                   + ActivityTimelineTool.EVENTS_XML_ARG + " " + EventsXml.EACH.argName() + "): " + xml.pastCap()
                   + " weren't read: they were classified by their code (from its events read, else its role) or "
                   + "the index's fields. Narrow the period to classify them all by their own XML.";
        }
        return "More than " + xml.cap() + " events" + capSetting + " had Events XML to read: the rest were "
               + "classified by the index's fields. Narrow the period to classify them all by their XML.";
    }

    private static Map<String, Object> xml(final XmlRead xml) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", xml.mode().argName());
        map.put("with_ids", xml.wanted());
        map.put("read", xml.read());
        map.put("gone_or_not_events", xml.missing());
        map.put("too_large", xml.unreadable());
        map.put("past_the_cap", xml.pastCap());
        if (xml.notAllowed() > 0) {
            map.put("not_read_no_view_data_permission", xml.notAllowed());
        }
        if (xml.pastCap() > 0) {
            map.put("narrow_the_period", pastCapText(xml, " (stroom.ai.workspace.timelineMaxXmlEvents)"));
        }
        return map;
    }

    private static List<Map<String, Object>> pairs(final TimelineRun run) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final PairUse use : run.built().pairs()) {
            if (use.spans() == 0 && use.unpairedEnds() == 0) {
                continue;
            }
            final Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("source", use.rule().source() < 0
                    ? "any"
                    : run.sourceName(use.rule().source()));
            pair.put("start", use.rule().start());
            pair.put("end", use.rule().end());
            pair.put("by", Objects.requireNonNullElse(use.rule().by(), "host"));
            pair.put("origin", use.rule().originName());
            pair.put("alternating_percent", (int) Math.round(use.rule().share() * 100));
            pair.put("spans", use.spans());
            pair.put("unpaired_starts", use.unpairedStarts());
            pair.put("unpaired_ends", use.unpairedEnds());
            out.add(pair);
        }
        return out;
    }

    private static Map<String, Object> borderline(final TimelineRun run, final Candidate candidate) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", run.sourceName(candidate.source()));
        map.put("start", candidate.start());
        map.put("end", candidate.end());
        map.put("by", Objects.requireNonNullElse(candidate.by(), "host"));
        map.put("alternating_percent", candidate.percent());
        map.put("keys", candidate.keys());
        map.put("starts", candidate.startCount());
        map.put("ends", candidate.endCount());
        map.put("used", false);
        return map;
    }

    // The view asked for, the gantt_chart arguments that draw it, and whether it is too dense for one chart.
    private static Map<String, Object> views(final TimelineRun run, final Files files) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("view", run.view().argName());
        final List<Map<String, Object>> charts = new ArrayList<>();
        final List<String> dense = new ArrayList<>();
        if (run.view() != View.DETAIL) {
            charts.add(activitiesChart(run, files));
            if (isDense(run, files, true)) {
                dense.add("the activities view has " + files.activityRows() + " bars in " + activityLanes(run, files)
                          + " lanes: draw one day (timeFrom and timeTo) or one activity (table, filtering "
                          + files.activities() + " by activity, into a new file)");
            }
        }
        if (run.view() != View.ACTIVITIES) {
            charts.add(spansChart(run, files));
            if (isDense(run, files, false)) {
                dense.add("the detail view has " + files.spanRows() + " bars in " + spanLanes(run, files)
                          + " lanes, too many for one chart: draw one day (timeFrom and timeTo) or one activity "
                          + "(table, filtering " + files.spans() + " by activity, into a new file)");
            }
        }
        map.put("gantt_charts", charts);
        if (!dense.isEmpty()) {
            map.put("too_dense", dense);
        }
        if (run.view() != View.DETAIL && run.characterised() == null) {
            map.put("note", "activities.csv is empty: no activities were found.");
        }
        map.put("dashboard", "A dashboard can't read the workspace: " + ProposeTimelineDashboardTool.NAME
                             + " writes " + files.spans() + " (table spans) or " + files.activities()
                             + " (table activities) as a CSV Data Connection with its timeline chart, in a "
                             + "folder the user chooses, then a dashboard of them.");
        return map;
    }

    /// The chart a chat's result draws ready for the reply: the view's (the activities first, for both) that has
    /// rows and isn't too dense for one chart.
    ///
    /// @param run   The run.
    /// @param files Its tables.
    /// @return The gantt_chart arguments that draw it (as the result's `gantt_charts` give them), or null if no
    /// view's chart can be drawn as it is.
    static Map<String, Object> readyChart(final TimelineRun run, final Files files) {
        if (run.view() != View.DETAIL && files.activityRows() > 0 && !isDense(run, files, true)) {
            return activitiesChart(run, files);
        }
        if (run.view() != View.ACTIVITIES && files.spanRows() > 0 && !isDense(run, files, false)) {
            return spansChart(run, files);
        }
        return null;
    }

    private static Map<String, Object> activitiesChart(final TimelineRun run, final Files files) {
        return chart(files.activities().toString(), "activity_label", "activity".equals(run.lanes())
                ? "activity_label"
                : "day", run.entity() + ": activities by " + ("activity".equals(run.lanes())
                ? "activity"
                : "day"));
    }

    private static Map<String, Object> spansChart(final TimelineRun run, final Files files) {
        return chart(files.spans().toString(), "label", "source".equals(run.lanes())
                ? "source"
                : "lane", run.entity() + ": spans by " + ("source".equals(run.lanes())
                ? "source"
                : "host"));
    }

    private static boolean isDense(final TimelineRun run, final Files files, final boolean activities) {
        return activities
                ? files.activityRows() > MAX_BARS || activityLanes(run, files) > MAX_LANES
                : files.spanRows() > MAX_BARS || spanLanes(run, files) > MAX_LANES;
    }

    private static int activityLanes(final TimelineRun run, final Files files) {
        return "activity".equals(run.lanes())
                ? files.activityNames()
                : files.activityLanes();
    }

    private static int spanLanes(final TimelineRun run, final Files files) {
        return "source".equals(run.lanes())
                ? files.sourceLanes()
                : files.hostLanes();
    }

    // A gantt_chart call: its title says what the lanes (the chart's sections) are, as a gantt has no lane axis, in a
    // word (by day, host, source or activity): the lanes' fuller titles (a host field's description, the days' zone)
    // made titles cut short with the zone twice (chat 197), and the chart adds the zone itself.
    private static Map<String, Object> chart(final String path,
                                             final String label,
                                             final String lane,
                                             final String title) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", path);
        map.put("start", "start");
        map.put("end", "end");
        map.put("label", label);
        map.put("lane", lane);
        map.put("startEvent", "start_event");
        map.put("endEvent", "end_event");
        map.put("title", title);
        return map;
    }

    private static Map<String, Object> characterisation(final Characterised characterised,
                                                        final Files files,
                                                        final int sessions) {
        final Map<String, Object> map = new LinkedHashMap<>();
        final List<Map<String, Object>> activities = new ArrayList<>();
        for (final Activity activity : characterised.activities()) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", activity.id());
            one.put("label", activity.label());
            one.put("label_is", ActivityCharacterisation.INFERRED);
            one.put("found_by", activity.fromModel()
                    ? "the model (a theme several items share)"
                    : "the sessions' graph (Leiden clusters)");
            one.put("sessions", activity.sessionCount());
            one.put("items", activity.items());
            one.put("active_minutes", Math.round(activity.activeMs() / 6_000.0) / 10.0);
            one.put("applications", activity.applications());
            one.put("artifacts", activity.artifacts());
            one.put("terms", activity.terms());
            activities.add(one);
        }
        map.put("activities", activities);
        map.put("sessions_in_no_activity", sessions - (int) characterised.sessions().values().stream()
                .filter(placement -> placement.activity().startsWith("a"))
                .count());
        final Map<String, Long> placed = new TreeMap<>();
        characterised.items().values().forEach(placement -> placed.merge(placement.activity().startsWith("a")
                ? "in_an_activity"
                : placement.activity(), 1L, Long::sum));
        map.put("items", characterised.allItems().size());
        map.put("items_placed", placed);
        map.put("model_calls", characterised.calls());
        map.put("items_linked_in_code", characterised.counts().linked());
        map.put("items_reused", characterised.counts().reused());
        map.put("items_asked", characterised.asked());
        map.put("items_classified", characterised.asked() - characterised.counts().failed());
        map.put("items_failed", characterised.counts().failed());
        if (characterised.counts().failedWhy() != null) {
            map.put("failed_why", characterised.counts().failedWhy());
        }
        map.put("items_not_classified", characterised.skipped());
        map.put("items_unclear", unclear(characterised));
        final List<Map<String, Object>> unclear = new ArrayList<>();
        characterised.items().values().stream()
                .filter(placement -> ActivityCharacterisation.UNCLEAR.equals(placement.activity()))
                .limit(ActivityCharacterisation.UNCLEAR_SHOWN)
                .forEach(placement -> unclear.add(Map.of("item", placement.id(), "reason",
                        NullSafe.string(placement.reason()))));
        map.put("unclear", unclear);
        map.put("modularity", Math.round(characterised.modularity() * 100) / 100.0);
        map.put("notes", characterised.notes());
        map.put("gantt", Map.of("path", files.activities().toString(), "start", "start", "end", "end", "label",
                "activity_label", "lane", "day"));
        return map;
    }

    static String endedEarly(final Read read) {
        if (read.reached() == null) {
            return read.cut()
                    ? "A role of a source had more than " + KeyGroupSource.MAX_KEY_ROWS + " events: the rest were "
                      + "counted, not kept. Narrow the period."
                    : null;
        }
        return switch (read.reached()) {
            case ROWS -> "Reading stopped at the events cap (stroom.ai.workspace.timelineMaxEvents): narrow the "
                         + "period.";
            case TIME -> "Reading stopped at the time limit: narrow the period, or the sources' roles.";
            default -> "Reading stopped early.";
        };
    }

    // Each lane's labels (a host's applications; a source's places), the most active time first.
    static List<ByApplication> byApplication(final Timeline timeline) {
        final Map<String, long[]> totals = new LinkedHashMap<>();
        for (final Session session : timeline.sessions()) {
            final long[] total = totals.computeIfAbsent(session.host() + "\u0000" + NullSafe.string(
                    session.process()), key -> new long[2]);
            total[0] += session.activeMs();
            total[1]++;
        }
        final List<ByApplication> rows = new ArrayList<>();
        totals.forEach((key, total) -> {
            final String[] parts = key.split("\u0000", -1);
            rows.add(new ByApplication(parts[0], parts[1], (int) total[1], total[0]));
        });
        rows.sort((a, b) -> a.activeMs() == b.activeMs()
                ? (a.host() + a.process()).compareTo(b.host() + b.process())
                : Long.compare(b.activeMs(), a.activeMs()));
        return rows;
    }

    /// @param run   The run.
    /// @param files The tables written.
    // Each logon id's events, those with none included.
    /// @param notes The sources' notes.
    /// @return The `## Fields` lines that would make the fields found by a guess the site's, of the sources read,
    ///         each once.
    static List<String> suggestions(final List<SourceNote> notes) {
        return NullSafe.list(notes).stream()
                .filter(note -> note.leftOut() == null)
                .flatMap(note -> NullSafe.list(note.suggestions()).stream())
                .distinct()
                .toList();
    }

    private static String accountsText(final Map<String, Long> accounts, final List<String> values) {
        final Map<String, Long> all = new LinkedHashMap<>(accounts);
        values.forEach(value -> all.putIfAbsent(value, 0L));
        return String.join(", ", all.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .toList());
    }

    /// @return The result as Markdown, for a chat.
    // How a source's things were read where its feeding pipelines' plans put them in different fields.
    private static String fieldsByPipelineText(final TimelineRun run, final int i) {
        final Map<String, Map<String, Long>> byThing = reading(run).byStream().get(i);
        if (byThing == null || byThing.isEmpty()) {
            return "";
        }
        final List<String> things = new ArrayList<>();
        byThing.forEach((thing, ways) -> things.add(thing + " by " + String.join(", ", ways.entrySet().stream()
                .map(way -> way.getKey() + " " + way.getValue())
                .toList())));
        return "; its feeding pipelines' plans differ, so each event's own was read: " + String.join("; ", things);
    }

    // A source's documents of events read more than once, left out, or why it wasn't deduplicated.
    private static String dedupeText(final TimelineRun run, final int i) {
        final Integer duplicates = reading(run).duplicates().get(i);
        if (reading(run).notDeduplicated().contains(i)) {
            return "; not deduplicated (" + NOT_DEDUPLICATED + ")";
        }
        return duplicates == null || duplicates == 0
                ? ""
                : "; " + duplicates + " duplicate document(s) left out (an event counted once by its raw stream "
                  + "and record)";
    }

    /// @param run   The run.
    /// @param files Its tables.
    /// @return The chat's result, as [#markdown(TimelineRun, Files, Map, String)] gives it with no chart drawn.
    static String markdown(final TimelineRun run, final Files files) {
        return markdown(run, files, null, null);
    }

    /// @param run        The run.
    /// @param files      Its tables.
    /// @param ready      The gantt_chart arguments of the chart drawn ready for the reply ([#readyChart]), or null.
    /// @param readyChart That chart, as gantt_chart's result gives it, or null if none was drawn.
    /// @return The chat's result: what was found, how to draw each view, and the chart drawn ready for the reply.
    static String markdown(final TimelineRun run,
                           final Files files,
                           final Map<String, Object> ready,
                           final String readyChart) {
        final ZoneId zoneId = run.zone().zone();
        final Timeline timeline = run.timeline();
        final StringBuilder sb = new StringBuilder("Activity timeline of ").append(run.entity()).append(", ")
                .append(local(run.period().fromMs(), zoneId)).append(" to ").append(local(run.period().toMs(), zoneId))
                .append(": ").append(spansText(run.built())).append(", ")
                .append(run.built().actions().size()).append(" action(s) (").append(run.built().unattached())
                .append(" in no session or span), from ").append(run.read().events().size()).append(" event(s): ");
        final List<String> counts = new ArrayList<>();
        for (int i = 0; i < run.sources().size(); i++) {
            counts.add(run.sources().get(i).name() + " " + (i < run.read().counts().size()
                    ? run.read().counts().get(i)
                    : 0));
        }
        sb.append(String.join(", ", counts)).append(". Idle cap ").append(run.capMs() / 60_000).append(" minutes.\n");
        final String early = endedEarly(run.read());
        if (early != null) {
            sb.append("\n").append(early).append("\n");
        }
        if (!NullSafe.list(run.ranges()).isEmpty()) {
            sb.append(rangesText(run.ranges()).substring(1)).append('\n');
        }
        sb.append("\nSources: ").append(run.chosen()).append(".\n");
        for (int i = 0; i < run.sources().size(); i++) {
            final SourceNote note = note(run, run.sources().get(i).name());
            final List<String> routes = new ArrayList<>();
            run.classification().routes().getOrDefault(i, Map.of()).forEach((route, count) ->
                    routes.add(count + " by " + route.description()));
            final Source source = run.sources().get(i);
            sb.append("- ").append(source.name()).append(source.by() == PersonScope.By.PERSON
                    ? ": person field "
                    : ": user field ")
                    .append(source.userField()).append(note == null || note.userFrom() == null
                            ? ""
                            : " (from " + note.userFrom() + ")").append(note == null || note.transform() == null
                            ? ""
                            : ", " + note.transform()).append(", ").append(source.by().label())
                    .append(source.by() == PersonScope.By.LOGON
                            ? "; accounts " + accountsText(accounts(run, i), source.values())
                            : "").append(routes.isEmpty()
                            ? ""
                            : "; events classified " + String.join(", ", routes))
                    .append(dedupeText(run, i)).append(fieldsByPipelineText(run, i)).append('\n');
        }
        if (byLogon(run)) {
            sb.append(PersonScope.OTHER_ACCOUNTS).append('\n');
        }
        run.notes().stream().filter(note -> note.leftOut() == null && note.hint() != null)
                .forEach(note -> sb.append("- ").append(note.name()).append(": nothing as given. ")
                        .append(note.hint()).append(".\n"));
        final List<String> suggested = suggestions(run.notes());
        if (!suggested.isEmpty()) {
            sb.append("Fields found by a convention or the documentation (offer to make them the site's): ")
                    .append(String.join(" ", suggested)).append('\n');
        }
        run.notes().stream().filter(note -> note.leftOut() != null).forEach(note -> sb.append("- left out: ")
                .append(note.index()).append(" (").append(note.leftOut()).append(")\n"));
        if (run.xml().mode() == EventsXml.EACH) {
            sb.append("Events XML: every event's read (").append(ActivityTimelineTool.EVENTS_XML_ARG).append(' ')
                    .append(EventsXml.EACH.argName()).append("), each classified from its own.\n");
        }
        if (run.xml().pastCap() > 0) {
            sb.append(pastCapText(run.xml(), "")).append('\n');
        }
        reading(run).xmlPaths().forEach(paths -> sb.append(TimelineReadNotes.xmlLine(paths)).append('\n'));
        sb.append("\nSpans: ").append(kindsText(run.built()));
        if (run.built().count(Kind.ACTIVITY) > 0) {
            sb.append(" (an activity period shows ").append(TimelineSpans.ACTIVITY_MEANS).append(")");
        }
        sb.append(".\n");
        for (final Map<String, Object> pair : pairs(run)) {
            sb.append("- pair ").append(pair.get("start")).append(" -> ").append(pair.get("end")).append(" by ")
                    .append(pair.get("by")).append(" (").append(pair.get("origin")).append(", ")
                    .append(pair.get("alternating_percent")).append("% alternating, ").append(pair.get("source"))
                    .append("): ").append(pair.get("spans")).append(" span(s)\n");
        }
        if (!run.borderline().isEmpty()) {
            sb.append("Borderline pairs (not used; ask the user whether any is a real pair):\n");
            run.learned().borderline().forEach(line -> sb.append("- ").append(line).append('\n'));
        }
        if (run.targets() != null) {
            sb.append(TimelineTargets.markdown(run.targets(), zoneId));
        }
        sb.append("\nActive time by host (or source) and application (or place):\n\n| host | process | sessions | "
                  + "active minutes |\n| --- | --- | --- | --- |\n");
        byApplication(run.built().combined()).stream().limit(SHOWN).forEach(row -> sb.append("| ")
                .append(row.host()).append(" | ").append(row.process()).append(" | ").append(row.sessions())
                .append(" | ").append(Math.round(row.activeMs() / 6_000.0) / 10.0).append(" |\n"));
        sb.append("\nData quality (missing events are flagged, never smoothed over):");
        if (run.built().combined().flags().isEmpty()) {
            sb.append(" nothing flagged.\n");
        } else {
            sb.append('\n');
            for (final Flag flag : run.built().combined().flags()) {
                sb.append("- ").append(flag.name().key()).append(": ").append(flag.count()).append(" ")
                        .append(flag.name().description()).append(", e.g. ")
                        .append(String.join("; ", flag.examples())).append('\n');
            }
        }
        final Characterised characterised = run.characterised();
        if (characterised != null) {
            sb.append("\nWhat ").append(run.entity()).append(" was working on (labels are inferences from the "
                                                              + "evidence):\n");
            for (final Activity activity : characterised.activities().stream().limit(SHOWN).toList()) {
                sb.append("- ").append(activity.id()).append(" \"").append(activity.label()).append("\": ")
                        .append(activity.sessionCount()).append(" session(s), ")
                        .append(Math.round(activity.activeMs() / 6_000.0) / 10.0).append(" active minutes, ")
                        .append(activity.items()).append(" item(s); ").append(String.join(", ",
                                activity.applications())).append(activity.artifacts().isEmpty()
                                ? ""
                                : "; artifacts: " + String.join(", ", activity.artifacts()))
                        .append(activity.fromModel()
                                ? " (a theme the model found in several items)"
                                : "").append('\n');
            }
            sb.append(itemsText(characterised, files.items()));
            characterised.notes().forEach(note -> sb.append("- ").append(note).append('\n'));
        }
        sb.append("\nTables: ").append(files.spans()).append(" (detail spans: focus sessions, pairs and activity "
                                                             + "periods), ").append(files.activities())
                .append(" (activities rolled up from them), ").append(files.sessions()).append(" (focus sessions), ")
                .append(files.actions()).append(" (actions, with where each was attached), ").append(files.daily())
                .append(" (per day), ").append(files.windows()).append(" (window lifetimes), ").append(files.events())
                .append(" (the events, with their StreamId and EventId and how each was classified), ")
                .append(files.quality()).append(" (data quality).\n");
        final Map<String, Object> views = views(run, files);
        final List<?> charts = ((List<?>) views.get("gantt_charts")).stream()
                .filter(chart -> readyChart == null || !chart.equals(ready))
                .toList();
        sb.append("View '").append(run.view().argName()).append("': ");
        if (readyChart != null) {
            sb.append("the chart of ").append(ready.get("path")).append(" is drawn at the end of this result, ready "
                                                                        + "for your reply");
            sb.append(charts.isEmpty()
                    ? ""
                    : "; draw the other in the chat with gantt_chart:");
        } else {
            sb.append("draw it in the chat with gantt_chart:");
        }
        for (final Object chart : charts) {
            final Map<?, ?> args = (Map<?, ?>) chart;
            sb.append(" path ").append(args.get("path")).append(", start start, end end, label ")
                    .append(args.get("label")).append(", lane ").append(args.get("lane"))
                    .append(", startEvent start_event, endEvent end_event, title '")
                    .append(args.get("title")).append("';");
        }
        if (!charts.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        sb.append(". On a dashboard instead: ").append(ProposeTimelineDashboardTool.NAME)
                .append(" (entity, table spans or activities).\n");
        if (views.get("too_dense") instanceof final List<?> dense) {
            dense.forEach(line -> sb.append("Too dense for one chart: ").append(line).append(".\n"));
        }
        if (run.learned() != null && run.learned().offer() != null) {
            sb.append("\nLearned for the skill:");
            run.learned().lines().forEach(line -> sb.append("\n").append(line));
            sb.append("\n").append(run.learned().offer()).append('\n');
        }
        sb.append("\n").append(run.zone().stated()).append(" The tables' times are UTC.\n\n")
                .append(ActivityTimelineTool.DERIVED).append(' ').append(ActivityTimelineTool.CAVEAT);
        if (readyChart != null) {
            sb.append("\n\nThe chart of ").append(ready.get("path")).append(":\n").append(readyChart);
        }
        return sb.toString();
    }

    /// The spans of every kind and the hosts they are on, e.g. `313 span(s) on 1 host(s) (0 focus session(s), 246
    /// pair span(s), 67 activity period(s))`: focus sessions are only a part of them.
    ///
    /// @param built The spans.
    /// @return The text.
    static String spansText(final Built built) {
        return built.spans().size() + " span(s) on " + spanHosts(built).size() + " host(s) (" + kindsText(built)
               + ")";
    }

    // Each kind's spans: focus sessions, pairs and activity periods always, spans bounded by a key where there are.
    private static String kindsText(final Built built) {
        return built.count(Kind.FOCUS) + " focus session(s), " + built.count(Kind.PAIR) + " pair span(s), "
               + built.count(Kind.ACTIVITY) + " activity period(s)" + (built.count(Kind.KEY) > 0
                ? ", " + built.count(Kind.KEY) + " key span(s)"
                : "");
    }

    // The hosts the spans are on, in order; a span with no host (in a source's lane) is on none.
    private static List<String> spanHosts(final Built built) {
        return built.spans().stream()
                .map(Span::host)
                .filter(NullSafe::isNonBlankString)
                .distinct()
                .toList();
    }

    /// What became of the characterisation's items: placed in code, reused from an earlier run, sent to the model
    /// (classified, or not as their calls failed, and why), not sent; and how many are unclear in all.
    ///
    /// @param characterised The characterisation.
    /// @param itemsFile     The items' table.
    /// @return The line, ending with a new line.
    static String itemsText(final Characterised characterised, final Object itemsFile) {
        final ActivityCharacterisation.Counts counts = characterised.counts();
        final StringBuilder sb = new StringBuilder().append(characterised.allItems().size())
                .append(" item(s) (messages, prompts, spans and sessions the graph left out): ")
                .append(counts.linked()).append(" placed in code (a shared artifact or terms)");
        if (counts.reused() > 0) {
            sb.append(", ").append(counts.reused()).append(" reused from an earlier run");
        }
        sb.append(", ").append(characterised.asked()).append(" sent to the model in ").append(characterised.calls())
                .append(" call(s) outside this conversation: ").append(characterised.asked() - counts.failed())
                .append(" classified");
        if (counts.failed() > 0) {
            sb.append(", ").append(counts.failed()).append(" not classified as the calls failed (")
                    .append(NullSafe.nonBlankStringElse(counts.failedWhy(), "the model couldn't be asked"))
                    .append(')');
        }
        if (characterised.skipped() > 0) {
            sb.append("; ").append(characterised.skipped()).append(" not sent (see below)");
        }
        return sb.append("; ").append(unclear(characterised)).append(" unclear in all (").append(itemsFile)
                .append(" has each with its reason).\n").toString();
    }

    private static long unclear(final Characterised characterised) {
        return characterised.items().values().stream()
                .filter(placement -> ActivityCharacterisation.UNCLEAR.equals(placement.activity()))
                .count();
    }

    private static String local(final long ms, final ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ');
    }


    // --------------------------------------------------------------------------------


    // A lane's label over the period.
    record ByApplication(String host, String process, int sessions, long activeMs) {

    }
}
