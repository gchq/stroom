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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.ActivityScoring.Kind;
import stroom.ai.impl.tools.compare.ActivityScoring.MonthLine;
import stroom.ai.impl.tools.compare.ActivityScoring.Scored;
import stroom.ai.impl.tools.compare.ActivityScoring.Status;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.ActivityScoring.Values;
import stroom.ai.impl.tools.compare.CompareActivityTool.Periods;
import stroom.ai.impl.tools.compare.CompareActivityTool.Planned;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.timeline.PersonScope;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.shared.NullSafe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// What `compare_activity` gives: the ranked findings (at most [CompareActivityTool#MAX_FINDINGS]), each an
/// indicator with its evidence and an alternative explanation; a line a month per source; the sources read and left
/// out, and by what (person id or logon id); and the workspace files: the daily profile, the monthly rollup, every
/// finding and each field's values started and stopped, as CSV (charted by the CSV Data Connection route, or the
/// table tool).
final class CompareResult {

    /// What every result says first.
    static final String INDICATORS = "These are indicators with evidence for an analyst to weigh, each with "
                                     + "another explanation: they say nothing about intent, and are never a "
                                     + "conclusion about the person.";
    /// The most flagged days named for activity_timeline.
    static final int FLAGGED_DAYS = 5;
    /// The most source-wide findings shown.
    static final int SOURCE_WIDE_SHOWN = 5;

    private CompareResult() {
        // Static only.
    }

    /// Writes the files.
    ///
    /// @param workspace The chat's workspace.
    /// @param run       What was compared.
    /// @return The files written.
    static Files write(final SessionWorkspace workspace, final Run run) {
        final List<String> features = new ArrayList<>();
        for (final Scored scored : run.scored()) {
            scored.features().keySet().stream().filter(name -> !features.contains(name)).forEach(features::add);
        }
        final List<String> profileColumns = new ArrayList<>(List.of("date", "source", "status"));
        profileColumns.addAll(features);
        final List<List<String>> profileRows = new ArrayList<>();
        for (final Scored scored : run.scored()) {
            for (final Map.Entry<LocalDate, Status> day : scored.status().entrySet()) {
                final List<String> row = new ArrayList<>(List.of(day.getKey().toString(), scored.source(),
                        day.getValue().name().toLowerCase()));
                for (final String feature : features) {
                    final Double value = NullSafe.map(scored.features().get(feature)).get(day.getKey());
                    row.add(value == null
                            ? ""
                            : ActivityScoring.shown(feature, value));
                }
                profileRows.add(row);
            }
        }
        final WorkspacePath profile = path(run, CompareActivityTool.PROFILE_FILE);
        ToolTables.write(workspace, profile, profileColumns, profileRows, null);

        final List<String> monthColumns = new ArrayList<>(List.of("month", "source", "days", "covered_days",
                "active_days", "leave_days", "in_window"));
        monthColumns.addAll(features);
        monthColumns.add("peers_median_events");
        final List<List<String>> monthRows = new ArrayList<>();
        for (final Scored scored : run.scored()) {
            for (final MonthLine line : scored.months()) {
                final List<String> row = new ArrayList<>(List.of(line.month().toString(), line.source(),
                        String.valueOf(line.days()), String.valueOf(line.covered()), String.valueOf(line.active()),
                        String.valueOf(line.leave()), String.valueOf(line.inWindow())));
                for (final String feature : features) {
                    final Double value = line.values().get(feature);
                    row.add(value == null
                            ? ""
                            : ActivityScoring.shown(feature, value));
                }
                row.add(Double.isNaN(line.peerMedian())
                        ? ""
                        : ActivityScoring.round(line.peerMedian()));
                monthRows.add(row);
            }
        }
        final WorkspacePath monthly = path(run, CompareActivityTool.MONTHLY_FILE);
        ToolTables.write(workspace, monthly, monthColumns, monthRows, null);

        final List<Finding> all = new ArrayList<>(ranked(run));
        all.addAll(sourceWide(run));
        final List<List<String>> findingRows = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            final Finding finding = all.get(i);
            findingRows.add(List.of(String.valueOf(i + 1), finding.source(), finding.kind().name().toLowerCase(),
                    finding.feature(), finding.window(), ActivityScoring.shown(finding.feature(), finding.baseline()),
                    ActivityScoring.shown(finding.feature(), finding.value()),
                    ActivityScoring.round(finding.score()), finding.evidence(), finding.alternative(),
                    String.valueOf(finding.sourceWide())));
        }
        final WorkspacePath findings = path(run, CompareActivityTool.FINDINGS_FILE);
        ToolTables.write(workspace, findings, List.of("rank", "source", "kind", "feature", "window", "baseline",
                "value", "score", "evidence", "alternative", "source_wide"), findingRows, null);

        final List<List<String>> valueRows = new ArrayList<>();
        for (final Scored scored : run.scored()) {
            for (final Values values : scored.values()) {
                valueRows.add(List.of(scored.source(), values.field(), String.valueOf(values.total()),
                        String.valueOf(values.started()), String.valueOf(values.stopped()),
                        String.join("; ", values.startedNames()), String.join("; ", values.stoppedNames()),
                        String.valueOf(values.cutShort())));
            }
        }
        final WorkspacePath valuesPath = path(run, CompareActivityTool.VALUES_FILE);
        ToolTables.write(workspace, valuesPath, List.of("source", "field", "values", "started", "stopped",
                "started_values", "stopped_values", "approximate"), valueRows, null);
        return new Files(profile, monthly, findings, valuesPath, all.size());
    }

    /// @param run What was compared.
    /// @return Every finding, across the sources, largest first.
    static List<Finding> ranked(final Run run) {
        final List<Finding> all = new ArrayList<>();
        run.scored().forEach(scored -> scored.findings().stream()
                .filter(finding -> !finding.sourceWide())
                .forEach(all::add));
        all.sort(Comparator.comparingDouble(Finding::score).reversed());
        return all;
    }

    /// The result in the chat, as Markdown.
    ///
    /// @param run   What was compared.
    /// @param files The files written.
    /// @return The text.
    static String markdown(final Run run, final Files files) {
        final StringBuilder sb = new StringBuilder();
        sb.append("**").append(CompareActivityTool.NAME).append("**: ").append(who(run)).append(", ")
                .append(run.periods().describe()).append(", by ").append(run.unit().word()).append(", in ")
                .append(run.zone().label()).append(".\n\n").append(INDICATORS).append("\n\n**Sources**\n");
        for (final Planned planned : run.planned()) {
            sb.append("- ").append(planned.doc().getName()).append(": ");
            if (!planned.usable()) {
                sb.append("left out: ").append(planned.leftOut()).append('\n');
                continue;
            }
            sb.append(planned.label());
            if (!planned.fields().breadth().isEmpty()) {
                sb.append("; breadth and new values of ").append(String.join(", ", planned.fields().breadth()));
            }
            if (!planned.fields().sums().isEmpty()) {
                sb.append("; sums of ").append(String.join(", ", planned.fields().sums()));
            }
            sb.append('\n');
        }
        final List<Finding> ranked = ranked(run);
        sb.append("\n**Findings** (largest first");
        if (ranked.size() > CompareActivityTool.MAX_FINDINGS) {
            sb.append("; ").append(CompareActivityTool.MAX_FINDINGS).append(" of ").append(ranked.size());
        }
        sb.append(")\n");
        if (ranked.isEmpty()) {
            sb.append("None: nothing in the window departs from the person's baseline or peers past the "
                      + "thresholds.\n");
        }
        for (int i = 0; i < Math.min(ranked.size(), CompareActivityTool.MAX_FINDINGS); i++) {
            final Finding finding = ranked.get(i);
            sb.append(i + 1).append(". [").append(finding.source()).append("] ").append(finding.evidence())
                    .append(". Alternatively: ").append(finding.alternative()).append('\n');
        }
        final List<Finding> shared = sourceWide(run);
        if (!shared.isEmpty()) {
            sb.append("\n**Explained by the source** (everyone else's activity moved as far: not the person's)\n");
            shared.stream().limit(SOURCE_WIDE_SHOWN).forEach(finding -> sb.append("- [").append(finding.source())
                    .append("] ").append(finding.evidence()).append('\n'));
        }
        for (final Scored scored : run.scored()) {
            final List<Values> changed = scored.values().stream()
                    .filter(values -> values.started() > 0 || values.stopped() > 0)
                    .toList();
            if (!changed.isEmpty()) {
                sb.append("\n**Values started and stopped** (").append(scored.source()).append(")\n");
                for (final Values values : changed) {
                    sb.append("- ").append(values.field()).append(": ").append(values.started())
                            .append(" started in the window");
                    if (!values.startedNames().isEmpty()) {
                        sb.append(" (").append(String.join(", ", values.startedNames())).append(')');
                    }
                    sb.append(", ").append(values.stopped()).append(" stopped");
                    if (!values.stoppedNames().isEmpty()) {
                        sb.append(" (").append(String.join(", ", values.stoppedNames())).append(')');
                    }
                    if (values.cutShort()) {
                        sb.append("; approximate, its values cut short");
                    }
                    sb.append('\n');
                }
            }
        }
        sb.append("\n**By month**\n");
        for (final Scored scored : run.scored()) {
            for (final MonthLine line : scored.months()) {
                sb.append("- ").append(monthLine(line)).append('\n');
            }
        }
        final Set<String> notes = new LinkedHashSet<>(run.notes());
        run.scored().forEach(scored -> scored.notes().forEach(note -> notes.add(scored.source() + ": " + note)));
        if (run.logonIds() != null && !run.logonIds().isEmpty() && run.planned().stream()
                .anyMatch(p -> p.usable() && p.label().startsWith("by logon id"))) {
            notes.add(PersonScope.OTHER_ACCOUNTS);
        }
        if (!notes.isEmpty()) {
            sb.append("\n**Notes**\n");
            notes.forEach(note -> sb.append("- ").append(note).append('\n'));
        }
        sb.append("\nFiles: ").append(files.profile().path()).append(" (daily), ").append(files.monthly().path())
                .append(" (monthly), ").append(files.findings().path()).append(" (all ").append(files.total())
                .append(" findings), ").append(files.values().path()).append(" (values). ").append(run.queries())
                .append(" aggregate queries run; a re-run asks only for the days since.");
        final List<String> flagged = flaggedDays(ranked);
        if (!flagged.isEmpty()) {
            sb.append("\n\nTo see what happened on the flagged days: activity_timeline on ")
                    .append(String.join(", ", flagged)).append('.');
        }
        return sb.toString();
    }

    /// The result over MCP, as JSON.
    ///
    /// @param run   What was compared.
    /// @param files The files written.
    /// @return The structured content.
    static Map<String, Object> structured(final Run run, final Files files) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("person", run.person());
        result.put("logon_ids", run.logonIds());
        result.put("baseline", Map.of("from", run.periods().baselineFrom().toString(), "to",
                run.periods().baselineTo().minusDays(1).toString()));
        result.put("window", Map.of("from", run.periods().windowFrom().toString(), "to",
                run.periods().windowTo().minusDays(1).toString()));
        result.put("unit", run.unit().word());
        result.put("time_zone", run.zone().structured());
        result.put("note", INDICATORS);
        final List<Map<String, Object>> sources = new ArrayList<>();
        for (final Planned planned : run.planned()) {
            final Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", planned.doc().getName());
            if (planned.usable()) {
                source.put("by", planned.label());
                source.put("values", planned.scope().values());
                source.put("breadth_fields", planned.fields().breadth());
                source.put("sum_fields", planned.fields().sums());
            } else {
                source.put("left_out", planned.leftOut());
            }
            sources.add(source);
        }
        result.put("sources", sources);
        final List<Finding> ranked = ranked(run);
        final List<Map<String, Object>> findings = new ArrayList<>();
        for (final Finding finding : ranked.stream().limit(CompareActivityTool.MAX_FINDINGS).toList()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("source", finding.source());
            map.put("kind", finding.kind().name().toLowerCase());
            map.put("feature", finding.feature());
            map.put("window", finding.window());
            map.put("baseline", ActivityScoring.shown(finding.feature(), finding.baseline()));
            map.put("value", ActivityScoring.shown(finding.feature(), finding.value()));
            map.put("score", Math.round(finding.score() * 100) / 100.0);
            map.put("evidence", finding.evidence());
            map.put("alternative", finding.alternative());
            findings.add(map);
        }
        result.put("findings", findings);
        result.put("source_wide", sourceWide(run).stream()
                .limit(SOURCE_WIDE_SHOWN)
                .map(finding -> "[" + finding.source() + "] " + finding.evidence())
                .toList());
        result.put("findings_total", ranked.size());
        result.put("months", run.scored().stream()
                .flatMap(scored -> scored.months().stream())
                .map(CompareResult::monthLine)
                .toList());
        final List<String> notes = new ArrayList<>(run.notes());
        run.scored().forEach(scored -> scored.notes().forEach(note -> notes.add(scored.source() + ": " + note)));
        result.put("notes", notes);
        result.put("files", List.of(files.profile().path(), files.monthly().path(), files.findings().path(),
                files.values().path()));
        result.put("flagged_days", flaggedDays(ranked));
        result.put("queries", run.queries());
        return result;
    }

    /// @param run What was compared.
    /// @return The findings everyone else's activity shared (a change in the source), largest first.
    static List<Finding> sourceWide(final Run run) {
        final List<Finding> all = new ArrayList<>();
        run.scored().forEach(scored -> scored.findings().stream()
                .filter(Finding::sourceWide)
                .forEach(all::add));
        all.sort(Comparator.comparingDouble(Finding::score).reversed());
        return all;
    }

    // The days of the largest day and change findings, for activity_timeline.
    private static List<String> flaggedDays(final List<Finding> ranked) {
        final Set<String> days = new TreeSet<>();
        for (final Finding finding : ranked) {
            if (days.size() >= FLAGGED_DAYS) {
                break;
            }
            if (finding.kind() == Kind.DAY || finding.kind() == Kind.CHANGE) {
                days.add(finding.window());
            }
        }
        return new ArrayList<>(days);
    }

    /// @param line A month's line.
    /// @return It in words.
    static String monthLine(final MonthLine line) {
        final StringBuilder sb = new StringBuilder(line.month().toString()).append(' ').append(line.source())
                .append(line.inWindow()
                        ? " (window)"
                        : "")
                .append(": ");
        if (line.covered() == 0) {
            return sb.append("no data in the source (not scored)").toString();
        }
        sb.append(line.active()).append(" active day(s)");
        appendValue(sb, line, ActivityScoring.EVENTS, " events a day");
        appendValue(sb, line, ActivityScoring.ACTIVE_HOURS, " active hours");
        final Double first = line.values().get(ActivityScoring.FIRST);
        final Double last = line.values().get(ActivityScoring.LAST);
        if (first != null && last != null) {
            sb.append(", ").append(ActivityScoring.shown(ActivityScoring.FIRST, first)).append(" to ")
                    .append(ActivityScoring.shown(ActivityScoring.LAST, last));
        }
        for (final Map.Entry<String, Double> value : line.values().entrySet()) {
            if (value.getKey().startsWith(ActivityScoring.SUM) || value.getKey().startsWith(ActivityScoring.DISTINCT)
                || value.getKey().startsWith(ActivityScoring.NOVEL)
                || value.getKey().startsWith(ActivityScoring.CATEGORY)) {
                sb.append(", ").append(ActivityScoring.label(value.getKey()).toLowerCase()).append(' ')
                        .append(ActivityScoring.round(value.getValue()));
            }
        }
        if (!Double.isNaN(line.peerMedian())) {
            sb.append("; peers' median ").append(ActivityScoring.round(line.peerMedian())).append(" events a day");
        }
        if (line.covered() < line.days()) {
            sb.append("; the source has data on ").append(line.covered()).append(" of ").append(line.days())
                    .append(" days");
        }
        if (line.leave() > 0) {
            sb.append("; ").append(line.leave()).append(" day(s) of leave");
        }
        return sb.toString();
    }

    private static void appendValue(final StringBuilder sb,
                                    final MonthLine line,
                                    final String feature,
                                    final String suffix) {
        final Double value = line.values().get(feature);
        if (value != null) {
            sb.append(", ").append(ActivityScoring.round(value)).append(suffix);
        }
    }

    private static String who(final Run run) {
        if (NullSafe.isNonBlankString(run.person())) {
            return "person " + run.person() + (run.logonIds().isEmpty()
                    ? ""
                    : " (logon ids " + String.join(", ", run.logonIds()) + " where a source has no person field)");
        }
        return "logon id(s) " + String.join(", ", run.logonIds());
    }

    private static WorkspacePath path(final Run run, final String file) {
        return WorkspacePath.parse(run.folder() + file);
    }


    // --------------------------------------------------------------------------------


    /// What was compared, and found.
    ///
    /// @param person   The person id, or null.
    /// @param logonIds The logon ids.
    /// @param periods  The periods.
    /// @param unit     The unit.
    /// @param zone     The person's zone.
    /// @param planned  The sources, read and left out.
    /// @param scored   Each source read, scored.
    /// @param notes    What the result says beside the sources' own notes.
    /// @param queries  The aggregate queries run.
    /// @param folder   The workspace folder written to.
    /// @param element  The UserDetails element peers share.
    record Run(String person,
               List<String> logonIds,
               Periods periods,
               Unit unit,
               ToolTimeZone zone,
               List<Planned> planned,
               List<Scored> scored,
               List<String> notes,
               int queries,
               String folder,
               String element) {

    }


    // --------------------------------------------------------------------------------


    /// The files written.
    ///
    /// @param profile  The daily profile.
    /// @param monthly  The monthly rollup.
    /// @param findings Every finding.
    /// @param values   Each breadth field's values started and stopped.
    /// @param total    How many findings there are in all.
    record Files(WorkspacePath profile, WorkspacePath monthly, WorkspacePath findings, WorkspacePath values,
                 int total) {

    }
}
