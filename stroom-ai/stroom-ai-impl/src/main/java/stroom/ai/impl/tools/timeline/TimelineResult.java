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

import stroom.ai.impl.tools.sequence.KeyGroupSource;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Activity;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.tools.timeline.TimelineReader.Read;
import stroom.ai.impl.tools.timeline.TimelineRun.SourceNote;
import stroom.ai.impl.tools.timeline.TimelineRun.View;
import stroom.ai.impl.tools.timeline.TimelineRun.XmlRead;
import stroom.ai.impl.tools.timeline.TimelineSessions.Flag;
import stroom.ai.impl.tools.timeline.TimelineSessions.Session;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;
import stroom.ai.impl.tools.timeline.TimelineSpans.Kind;
import stroom.ai.impl.tools.timeline.TimelineSpans.PairUse;
import stroom.ai.impl.tools.timeline.TimelineTables.Files;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            final SourceNote note = note(run, run.sources().get(i).name());
            if (note != null) {
                NullSafe.consume(note.userFrom(), from -> source.put("user_field_from", from));
                NullSafe.consume(note.transform(), transform -> source.put("user_transform", transform));
            }
            final Map<String, Object> routes = new LinkedHashMap<>();
            run.classification().routes().getOrDefault(i, Map.of()).forEach((route, count) ->
                    routes.put(route.description(), count));
            source.put("classified_by", routes);
            sources.add(source);
        }
        map.put("sources", sources);
        final List<Map<String, Object>> leftOut = run.notes().stream()
                .filter(note -> note.leftOut() != null)
                .map(note -> Map.<String, Object>of("index", note.index(), "why", note.leftOut()))
                .toList();
        if (!leftOut.isEmpty()) {
            map.put("left_out", leftOut);
        }
        map.put("events_read", run.read().events().size());
        if (run.classification().ignored() > 0) {
            map.put("events_ignored", run.classification().ignored());
        }
        map.put("events_xml", xml(run.xml()));
        map.put("ended_early", endedEarly(run.read()));
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
        map.put("time_zone", run.zone().zone().getId());
        map.put("derived", ActivityTimelineTool.DERIVED);
        map.put("caveat", ActivityTimelineTool.CAVEAT);
        return map;
    }

    private static SourceNote note(final TimelineRun run, final String name) {
        return run.notes().stream()
                .filter(note -> note.leftOut() == null && note.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, Object> xml(final XmlRead xml) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("with_ids", xml.wanted());
        map.put("read", xml.read());
        map.put("gone_or_not_events", xml.missing());
        map.put("too_large", xml.unreadable());
        map.put("past_the_cap", xml.pastCap());
        if (xml.notAllowed() > 0) {
            map.put("not_read_no_view_data_permission", xml.notAllowed());
        }
        if (xml.pastCap() > 0) {
            map.put("narrow_the_period", "More than " + xml.cap() + " events (stroom.ai.workspace."
                                         + "timelineMaxXmlEvents) had Events XML to read: the rest were classified "
                                         + "by the index's fields. Narrow the period to classify them all by their "
                                         + "XML.");
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
            final boolean perActivity = "activity".equals(run.lanes());
            charts.add(chart(files.activities().toString(), "activity_label", perActivity
                    ? "activity_label"
                    : "day"));
            final int lanes = perActivity
                    ? files.activityNames()
                    : files.activityLanes();
            if (files.activityRows() > MAX_BARS || lanes > MAX_LANES) {
                dense.add("the activities view has " + files.activityRows() + " bars in " + lanes + " lanes: draw "
                          + "one day (timeFrom and timeTo) or one activity (table, filtering "
                          + files.activities() + " by activity, into a new file)");
            }
        }
        if (run.view() != View.ACTIVITIES) {
            final boolean bySource = "source".equals(run.lanes());
            charts.add(chart(files.spans().toString(), "label", bySource
                    ? "source"
                    : "lane"));
            final int lanes = bySource
                    ? files.sourceLanes()
                    : files.hostLanes();
            if (files.spanRows() > MAX_BARS || lanes > MAX_LANES) {
                dense.add("the detail view has " + files.spanRows() + " bars in " + lanes + " lanes, too many for "
                          + "one chart: draw one day (timeFrom and timeTo) or one activity (table, filtering "
                          + files.spans() + " by activity, into a new file)");
            }
        }
        map.put("gantt_charts", charts);
        if (!dense.isEmpty()) {
            map.put("too_dense", dense);
        }
        if (run.view() != View.DETAIL && run.characterised() == null) {
            map.put("note", "activities.csv is empty: no activities were found.");
        }
        map.put("dashboard", "The timeline chart kind (propose_visualisation) takes " + files.spans()
                             + "'s columns as they are: start, end, label, lane, and colour activity_label.");
        return map;
    }

    private static Map<String, Object> chart(final String path, final String label, final String lane) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", path);
        map.put("start", "start");
        map.put("end", "end");
        map.put("label", label);
        map.put("lane", lane);
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
        map.put("items_asked", characterised.asked());
        map.put("items_not_classified", characterised.skipped());
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
    /// @return The result as Markdown, for a chat.
    static String markdown(final TimelineRun run, final Files files) {
        final ZoneId zoneId = run.zone().zone();
        final Timeline timeline = run.timeline();
        final StringBuilder sb = new StringBuilder("Activity timeline of ").append(run.entity()).append(", ")
                .append(local(run.period().fromMs(), zoneId)).append(" to ").append(local(run.period().toMs(), zoneId))
                .append(": ").append(timeline.sessions().size()).append(" session(s) on ")
                .append(timeline.sessions().stream().map(Session::host).distinct().count()).append(" host(s), ")
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
        sb.append("\nSources: ").append(run.chosen()).append(".\n");
        for (int i = 0; i < run.sources().size(); i++) {
            final SourceNote note = note(run, run.sources().get(i).name());
            final List<String> routes = new ArrayList<>();
            run.classification().routes().getOrDefault(i, Map.of()).forEach((route, count) ->
                    routes.add(count + " by " + route.description()));
            sb.append("- ").append(run.sources().get(i).name()).append(": user field ")
                    .append(run.sources().get(i).userField()).append(note == null || note.userFrom() == null
                            ? ""
                            : " (from " + note.userFrom() + ")").append(routes.isEmpty()
                            ? ""
                            : "; events classified " + String.join(", ", routes)).append('\n');
        }
        run.notes().stream().filter(note -> note.leftOut() != null).forEach(note -> sb.append("- left out: ")
                .append(note.index()).append(" (").append(note.leftOut()).append(")\n"));
        if (run.xml().pastCap() > 0) {
            sb.append("More than ").append(run.xml().cap()).append(" events had Events XML to read: the rest were "
                                                                   + "classified by the index's fields. Narrow the "
                                                                   + "period to classify them all by their XML.\n");
        }
        sb.append("\nSpans: ").append(run.built().count(Kind.FOCUS)).append(" focus session(s), ")
                .append(run.built().count(Kind.PAIR)).append(" pair span(s), ")
                .append(run.built().count(Kind.ACTIVITY)).append(" activity period(s)");
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
            final long unclear = characterised.items().values().stream()
                    .filter(placement -> ActivityCharacterisation.UNCLEAR.equals(placement.activity()))
                    .count();
            sb.append(characterised.allItems().size()).append(" item(s) (messages, prompts, sessions the graph "
                                                              + "left out): ").append(characterised.asked())
                    .append(" classified by the model in ").append(characterised.calls())
                    .append(" call(s) outside this conversation, ").append(unclear).append(" unclear (")
                    .append(files.items()).append(" has each with its reason).\n");
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
        sb.append("View '").append(run.view().argName()).append("': draw it in the chat with gantt_chart:");
        for (final Object chart : (List<?>) views.get("gantt_charts")) {
            final Map<?, ?> args = (Map<?, ?>) chart;
            sb.append(" path ").append(args.get("path")).append(", start start, end end, label ")
                    .append(args.get("label")).append(", lane ").append(args.get("lane")).append(';');
        }
        sb.setLength(sb.length() - 1);
        sb.append(".\n");
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
        return sb.toString();
    }

    private static String local(final long ms, final ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ');
    }


    // --------------------------------------------------------------------------------


    // A lane's label over the period.
    record ByApplication(String host, String process, int sessions, long activeMs) {

    }
}
