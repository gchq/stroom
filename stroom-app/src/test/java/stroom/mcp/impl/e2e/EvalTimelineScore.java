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

package stroom.mcp.impl.e2e;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The checks of what an activity timeline wrote (a case's `expected.timeline`, [EvalScore#scoreTimeline]), on its
/// structure and evidence, never on the model's wording: the periods' kinds, keys, times and ends seen
/// (`periods.csv`); the activities view's levels, parents and member events (`activity-levels.csv`, and
/// `events.csv`'s membership columns); and the noise form, what was left out and how much less the timeline holds
/// than its baseline. Each check is named and said through the [Checks] given, as the score's others are.
///
/// `expected.timeline`:
///
/// * `entity`: whose timeline: its files are in `/outputs/timelines/<entity>/`;
/// * `periods`: periods each a row of `periods.csv`, each row matched once: its `kind` and `key` exactly, its
///   `start` and `end` as the start of the row's (`2026-11-16T08:2` matches `2026-11-16T08:23:00Z`), its
///   `start_seen` and `end_seen` exactly, except that a period whose end wasn't seen may end at or after its `end`
///   (it runs to the end of the window the model asked for: both models asked for 16 to 22 November in case 119);
///   only the keys given are compared;
/// * `periods_by_kind`: how many periods of each kind, exactly, and of no other kind;
/// * `periods_imply_no_activity`: true when every event that starts or ends a period (`events.csv` role
///   `period_start` or `period_end`) is in no span, thematic span, run or activity, and in its period;
/// * `periods_within`: periods of a `kind` starting so (`start`, as above) that lie within a period of the kind
///   `within` (e.g. a remote session while on site);
/// * `no_activity_on`: days (`2026-11-18`) with no event in a span or activity, only periods (e.g. badged in but
///   never logged on);
/// * `theme`: a row of `activity-levels.csv` at `level` (default 2) whose member events (`events.csv` rows naming it
///   in `merged`, or in `activity` for level 1) are the person's events whose object or title `matches`, all of them
///   (with `exactly: true`, none other), with at least `children_at_least` rows naming it as their `parent`, each a
///   level finer and its members among those events; with `how`, a regular expression its `how` matches (e.g.
///   `^code: sharing category`, merged in code by a category);
/// * `members_answer`: true when an accepted `table` call on the timeline's `events.csv` returned exactly the theme's
///   member events (by `EventId`), as a follow-up asking which events make it up is answered from the membership
///   columns;
/// * `runs`: runs of point events each a level-2 row of `activity-levels.csv` (a run's id starts `r`) whose member
///   events (`events.csv` `merged`) are `events` many, and whose events' texts (the run's level-3 rows' labels)
///   all match `matches`; with `runs_total`, how many runs there are in all; `runs_described_with_evidence: true`
///   when each run matched was described by the model (`how` from `model`) citing only events of its own (the `e<n>`
///   ids of its `how`, the n-th event of the run code made, which a split shares among its parts);
///   `runs_under_activity: true` when each run's parent is the activity it is in (not a period, not none); and
///   `runs_parent_rows: true` when each run's parent is also a level-1 row of the table;
/// * `noise`: `form`, one form asking which patterns to leave out (its field `exclude`), accepted with every pattern
///   it proposed; `excluded_at_least`, each accepted `activity_timeline` call from it on saying it left out at least
///   so many events as noise; `remembered`, a later call of the turn (if any) asking nothing and leaving them out
///   again; `gone`, no event of `events.csv` whose object matches; `kept`, at least `kept_at_least` (default 1)
///   events whose object matches; `fewer_than_baseline`, files with fewer rows than they had in the baseline (the
///   chat's earlier turns' timeline);
/// * `sources`: the data sources chosen in the timeline's forms: `offered`, the data sources the forms' lists offer
///   (the confirming form's), exactly; `not_offered`, data sources named in no form's options; `left_out`, each a data
///   source named, with why (a regular expression, e.g. `holding none`), in a form's message or a timeline call's
///   result; `read`, the sources `events.csv`'s events came from, exactly, with at least one event.
final class EvalTimelineScore {

    /// The timeline's folder.
    static final String FOLDER = "/outputs/timelines/";
    /// The events' table.
    static final String EVENTS = "events.csv";
    /// The periods' table.
    static final String PERIODS = "periods.csv";
    /// The activities view's levels.
    static final String LEVELS = "activity-levels.csv";
    /// The noise form's field.
    static final String NOISE_FIELD = "exclude";
    /// The tool whose calls are scored.
    static final String TIMELINE_TOOL = "activity_timeline";
    /// The tool listing the data sources, which leaves out indexes holding no data.
    static final String LIST_TOOL = "list_data_sources";
    // A data source a list_data_sources result lists.
    private static final Pattern LISTED = Pattern.compile("(?m)^- [A-Za-z]+ '([^']+)' \\(uuid ");

    private static final Pattern CITED = Pattern.compile("\\be(\\d+)\\b");
    // The activity of a level's row in none.
    private static final String NO_ACTIVITY = "(no activity)";
    private static final Pattern LEFT_OUT = Pattern.compile("Left out as noise[^:]*:\\s*(\\d+) event");

    private EvalTimelineScore() {
        // Static only.
    }

    /// Scores a timeline's files and calls against the case's `expected.timeline`.
    ///
    /// @param expected The case's `expected.timeline`; nothing is scored where it is empty.
    /// @param run      What the run left.
    /// @param checks   Where each check is said.
    static void score(final Map<String, Object> expected, final Run run, final Checks checks) {
        if (expected.isEmpty()) {
            return;
        }
        final String folder = FOLDER + expected.get("entity") + "/";
        final Tables tables = new Tables(run.files(), folder);
        scorePeriods(expected, tables, checks);
        final Unit theme = scoreTheme(EvalCases.map(expected.get("theme")), tables, checks);
        if (Boolean.TRUE.equals(expected.get("members_answer"))) {
            scoreMembersAnswer(theme, run.calls(), folder, checks);
        }
        scoreRuns(expected, tables, checks);
        final Map<String, Object> noise = EvalCases.map(expected.get("noise"));
        if (!noise.isEmpty()) {
            scoreNoise(noise, run, tables, new Tables(run.baseline(), folder), checks);
        }
        final Map<String, Object> sources = EvalCases.map(expected.get("sources"));
        if (!sources.isEmpty()) {
            scoreSources(sources, run, tables, checks);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Periods

    private static void scorePeriods(final Map<String, Object> expected, final Tables tables, final Checks checks) {
        final List<Map<String, Object>> wanted = listOfMaps(expected.get("periods"));
        final Map<String, Object> byKind = EvalCases.map(expected.get("periods_by_kind"));
        final boolean noActivity = Boolean.TRUE.equals(expected.get("periods_imply_no_activity"));
        final List<Map<String, Object>> within = listOfMaps(expected.get("periods_within"));
        final List<String> quietDays = EvalCases.strings(expected.get("no_activity_on"));
        if (wanted.isEmpty() && byKind.isEmpty() && !noActivity && within.isEmpty() && quietDays.isEmpty()) {
            return;
        }
        final List<Map<String, String>> periods = tables.rows(PERIODS);
        if (periods == null) {
            checks.check("periods table", false, "no " + PERIODS + " was written");
            return;
        }
        final Set<Integer> used = new LinkedHashSet<>();
        for (final Map<String, Object> period : wanted) {
            int found = -1;
            for (int i = 0; i < periods.size() && found < 0; i++) {
                if (!used.contains(i) && periodMatches(period, periods.get(i))) {
                    found = i;
                }
            }
            if (found >= 0) {
                used.add(found);
            }
            checks.check("period " + period.get("kind") + " " + Objects.requireNonNullElse(period.get("key"), "")
                         + " " + Objects.requireNonNullElse(period.get("start"), ""), found >= 0,
                    "no period matched " + period + " (the periods: " + periodsText(periods) + ")");
        }
        if (!byKind.isEmpty()) {
            final Map<String, Integer> counted = new LinkedHashMap<>();
            periods.forEach(row -> counted.merge(row.get("kind"), 1, Integer::sum));
            final Map<String, Integer> wantedCounts = new LinkedHashMap<>();
            byKind.forEach((kind, count) -> wantedCounts.put(kind, ((Number) count).intValue()));
            checks.check("periods by kind", counted.equals(wantedCounts), "the periods by kind were " + counted
                                                                          + ", expected " + wantedCounts);
        }
        if (noActivity) {
            final List<Map<String, String>> events = tables.rows(EVENTS);
            final List<String> wrong = new ArrayList<>();
            if (events == null) {
                wrong.add("no " + EVENTS + " was written");
            } else {
                for (final Map<String, String> event : events) {
                    if (String.valueOf(event.get("role")).startsWith("period_")
                        && (!blank(event.get("span")) || !blank(event.get("merged"))
                            || !blank(event.get("activity")) || blank(event.get("periods")))) {
                        wrong.add(ref(event) + " (span '" + event.get("span") + "', merged '" + event.get("merged")
                                  + "', activity '" + event.get("activity") + "', periods '" + event.get("periods")
                                  + "')");
                    }
                }
            }
            checks.check("periods imply no activity", wrong.isEmpty(), "a period's events were taken as "
                                                                       + "activity or are in no period: "
                                                                       + first(wrong));
        }
        for (final Map<String, Object> one : within) {
            final String kind = String.valueOf(one.get("kind"));
            final String outer = String.valueOf(one.get("within"));
            final String start = String.valueOf(one.get("start"));
            final boolean found = periods.stream()
                    .filter(row -> kind.equals(row.get("kind")) && String.valueOf(row.get("start")).startsWith(start))
                    .anyMatch(row -> periods.stream().anyMatch(other -> outer.equals(other.get("kind"))
                                                                        && other.get("start").compareTo(
                            row.get("start")) <= 0 && other.get("end").compareTo(row.get("end")) >= 0));
            checks.check(kind + " within " + outer + " " + start, found, "no " + kind + " period starting " + start
                                                                         + " lies within a " + outer + " period: "
                                                                         + periodsText(periods));
        }
        if (!quietDays.isEmpty()) {
            final List<Map<String, String>> events = tables.rows(EVENTS);
            for (final String day : quietDays) {
                final List<String> active = events == null
                        ? List.of("no " + EVENTS + " was written")
                        : events.stream()
                                .filter(event -> String.valueOf(event.get("time")).startsWith(day)
                                                 && (!blank(event.get("span")) || !blank(event.get("activity"))))
                                .map(event -> ref(event) + " " + event.get("kind") + " in " + event.get("span"))
                                .toList();
                checks.check("no activity on " + day, active.isEmpty(), "events of " + day + " are activity: "
                                                                        + first(active));
            }
        }
    }

    static boolean periodMatches(final Map<String, Object> wanted, final Map<String, String> row) {
        final boolean openEnd = "end not seen".equals(String.valueOf(wanted.get("end_seen")));
        for (final Map.Entry<String, Object> entry : wanted.entrySet()) {
            final String value = String.valueOf(entry.getValue());
            final String actual = Objects.requireNonNullElse(row.get(entry.getKey()), "");
            final boolean time = "start".equals(entry.getKey()) || "end".equals(entry.getKey());
            final boolean matched;
            if ("end".equals(entry.getKey()) && openEnd) {
                // Its end the window's: at or after the one given.
                matched = actual.startsWith(value) || actual.compareTo(value) >= 0;
            } else {
                matched = time
                        ? actual.startsWith(value)
                        : actual.equals(value);
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    // The data sources a list_data_sources result lists, by name (`- ElasticIndex 'Badge events' (uuid ...)`).
    static List<String> listedSources(final String text) {
        final List<String> out = new ArrayList<>();
        final java.util.regex.Matcher matcher = LISTED.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    private static String periodsText(final List<Map<String, String>> periods) {
        return String.join("; ", periods.stream()
                .map(row -> row.get("kind") + " [" + row.get("key") + "] " + row.get("start") + " to "
                            + row.get("end") + " (" + row.get("start_seen") + ", " + row.get("end_seen") + ")")
                .toList());
    }

    // ------------------------------------------------------------------------------------------------
    // The theme and its members

    private static Unit scoreTheme(final Map<String, Object> theme, final Tables tables, final Checks checks) {
        if (theme.isEmpty()) {
            return null;
        }
        final List<Map<String, String>> levels = tables.rows(LEVELS);
        final List<Map<String, String>> events = tables.rows(EVENTS);
        if (levels == null || events == null) {
            checks.check("theme", false, "no " + (levels == null
                    ? LEVELS
                    : EVENTS) + " was written");
            return null;
        }
        final Pattern matches = Pattern.compile(String.valueOf(theme.get("matches")), Pattern.CASE_INSENSITIVE);
        final int level = theme.get("level") instanceof final Number number
                ? number.intValue()
                : 2;
        final boolean exactly = Boolean.TRUE.equals(theme.get("exactly"));
        final Set<String> target = new LinkedHashSet<>();
        events.stream().filter(event -> matches.matcher(eventText(event)).find()).forEach(event -> target.add(
                ref(event)));
        Unit best = null;
        String bestHow = "";
        String closest = "no row at level " + level;
        for (final Map<String, String> row : levels) {
            if (!String.valueOf(level).equals(row.get("level"))) {
                continue;
            }
            final Set<String> members = members(row.get("id"), level, events);
            final Set<String> outside = new LinkedHashSet<>(members);
            outside.removeAll(target);
            if (members.containsAll(target) && (!exactly || outside.isEmpty())) {
                best = new Unit(row.get("id"), level, members);
                bestHow = Objects.requireNonNullElse(row.get("how"), "");
                break;
            }
            final Set<String> held = new LinkedHashSet<>(members);
            held.retainAll(target);
            closest = "the closest, " + row.get("id") + " '" + row.get("bar_label") + "', held " + held.size()
                      + " of the " + target.size() + " events, with " + outside.size() + " other(s)";
        }
        checks.check("theme over " + theme.get("matches"), best != null && !target.isEmpty(), target.isEmpty()
                ? "no event matches " + theme.get("matches")
                : "no row at level " + level + " holds " + (exactly
                        ? "exactly "
                        : "all ") + "the " + target.size() + " events matching " + theme.get("matches") + ": "
                  + closest);
        if (best == null) {
            return null;
        }
        if (theme.get("children_at_least") instanceof final Number least) {
            final String id = best.id();
            final List<Map<String, String>> children = levels.stream()
                    .filter(row -> id.equals(row.get("parent")))
                    .toList();
            final List<String> wrong = new ArrayList<>();
            for (final Map<String, String> child : children) {
                final Set<String> outside = new LinkedHashSet<>(members(child.get("id"), level + 1, events));
                if (outside.isEmpty() && child.get("id").contains(":")) {
                    // A point event's own row: it is its one member.
                    outside.add(child.get("id"));
                }
                outside.removeAll(target);
                if (!String.valueOf(level + 1).equals(child.get("level")) || !outside.isEmpty()) {
                    wrong.add(child.get("id") + " (level " + child.get("level") + ", " + outside.size()
                              + " event(s) outside the theme)");
                }
            }
            checks.check("theme's children", children.size() >= least.intValue() && wrong.isEmpty(),
                    children.size() + " row(s) name " + id + " as their parent, expected at least " + least
                    + (wrong.isEmpty()
                            ? ""
                            : "; not a level finer within it: " + first(wrong)));
        }
        if (theme.get("how") != null) {
            final Pattern how = Pattern.compile(String.valueOf(theme.get("how")), Pattern.CASE_INSENSITIVE);
            checks.check("theme's how", how.matcher(bestHow).find(), best.id() + " was made '" + bestHow
                                                                    + "', expected " + theme.get("how"));
        }
        return best;
    }

    // A level's row's member events: the events naming it in their merged column (level 2), activity (level 1) or
    // span (level 3), by StreamId:EventId.
    private static Set<String> members(final String id, final int level, final List<Map<String, String>> events) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, String> event : events) {
            final boolean in = switch (level) {
                case 1 -> id.equals(event.get("activity"));
                case 2 -> id.equals(event.get("merged"));
                default -> List.of(Objects.requireNonNullElse(event.get("span"), "").split(" ")).contains(id);
            };
            if (in) {
                out.add(ref(event));
            }
        }
        return out;
    }

    private static void scoreMembersAnswer(final Unit theme,
                                           final List<EvalScore.ToolUse> calls,
                                           final String folder,
                                           final Checks checks) {
        if (theme == null) {
            checks.check("members answer", false, "no theme to answer of");
            return;
        }
        final Set<String> wanted = new LinkedHashSet<>();
        theme.members().forEach(member -> wanted.add(member.substring(member.indexOf(':') + 1)));
        String closest = "no accepted table call on " + folder + EVENTS;
        for (final EvalScore.ToolUse call : calls) {
            if (!"table".equals(call.name()) || call.refused()
                || !String.valueOf(call.result().get("path")).endsWith(folder + EVENTS)) {
                continue;
            }
            final List<String> columns = EvalCases.strings(call.result().get("columns"));
            final int column = columns.indexOf("EventId");
            if (column < 0) {
                closest = "a table call on " + EVENTS + " returned no EventId column (" + columns + ")";
                continue;
            }
            final Set<String> given = new LinkedHashSet<>();
            for (final Object row : call.result().get("rows") instanceof final List<?> rows
                    ? rows
                    : List.of()) {
                if (row instanceof final List<?> values && column < values.size()) {
                    given.add(String.valueOf(values.get(column)));
                }
            }
            if (given.equals(wanted)) {
                checks.check("members answer", true, null);
                return;
            }
            final Set<String> missing = new LinkedHashSet<>(wanted);
            missing.removeAll(given);
            final Set<String> extra = new LinkedHashSet<>(given);
            extra.removeAll(wanted);
            closest = "a table call returned " + given.size() + " event(s), " + missing.size() + " of "
                      + theme.id() + "'s missing and " + extra.size() + " not of it";
        }
        checks.check("members answer", false, "no answer listed exactly " + theme.id() + "'s " + wanted.size()
                                              + " events: " + closest);
    }

    // ------------------------------------------------------------------------------------------------
    // Runs

    private static void scoreRuns(final Map<String, Object> expected, final Tables tables, final Checks checks) {
        final List<Map<String, Object>> wanted = listOfMaps(expected.get("runs"));
        if (wanted.isEmpty() && expected.get("runs_total") == null) {
            return;
        }
        final List<Map<String, String>> levels = tables.rows(LEVELS);
        final List<Map<String, String>> events = tables.rows(EVENTS);
        if (levels == null || events == null) {
            checks.check("runs", false, "no " + (levels == null
                    ? LEVELS
                    : EVENTS) + " was written");
            return;
        }
        final List<Map<String, String>> runs = levels.stream()
                .filter(row -> "2".equals(row.get("level")) && String.valueOf(row.get("id")).startsWith("r"))
                .toList();
        if (expected.get("runs_total") instanceof final Number total) {
            checks.check("runs in all", runs.size() == total.intValue(), runs.size() + " run(s), expected " + total
                                                                         + ": " + runsText(runs));
        }
        final Map<String, String> texts = new LinkedHashMap<>();
        levels.stream().filter(row -> "3".equals(row.get("level"))).forEach(row -> texts.put(row.get("id"),
                row.get("bar_label")));
        final boolean evidence = Boolean.TRUE.equals(expected.get("runs_described_with_evidence"));
        final boolean underActivity = Boolean.TRUE.equals(expected.get("runs_under_activity"));
        final boolean parentRows = Boolean.TRUE.equals(expected.get("runs_parent_rows"));
        final Set<String> activities = new LinkedHashSet<>();
        levels.stream()
                .filter(row -> "1".equals(row.get("level")) && !String.valueOf(row.get("id")).startsWith("pr"))
                .forEach(row -> activities.add(row.get("id")));
        final Set<String> used = new LinkedHashSet<>();
        for (final Map<String, Object> run : wanted) {
            final Pattern matches = Pattern.compile(String.valueOf(run.get("matches")), Pattern.CASE_INSENSITIVE);
            final int count = ((Number) run.get("events")).intValue();
            Map<String, String> found = null;
            for (final Map<String, String> row : runs) {
                final Set<String> members = members(row.get("id"), 2, events);
                if (!used.contains(row.get("id")) && members.size() == count && members.stream()
                        .allMatch(member -> matches.matcher(Objects.requireNonNullElse(texts.get(member), ""))
                                .find())) {
                    found = row;
                    break;
                }
            }
            checks.check("run of " + run.get("matches"), found != null, "no run of exactly " + count
                                                                        + " events all matching "
                                                                        + run.get("matches") + ": "
                                                                        + runsText(runs));
            if (found == null) {
                continue;
            }
            used.add(found.get("id"));
            if (evidence) {
                final String problem = citesOwn(found, runs, events);
                checks.check("run of " + run.get("matches") + " cites its events", problem == null, problem);
            }
            if (underActivity) {
                final String parent = Objects.requireNonNullElse(found.get("parent"), "");
                checks.check("run of " + run.get("matches") + " under an activity", !parent.isEmpty()
                                                                                    && !parent.startsWith("pr")
                                                                                    && parent.equals(
                        found.get("activity")) && !NO_ACTIVITY.equals(found.get("activity_label")),
                        "run " + found.get("id") + "'s parent is '" + parent + "', not an activity");
            }
            if (parentRows) {
                checks.check("run of " + run.get("matches") + "'s parent a row", activities.contains(
                        found.get("parent")), "run " + found.get("id") + "'s parent '" + found.get("parent")
                                              + "' is no level-1 row (" + activities + ")");
            }
        }
    }

    // Null if a run was described by the model citing only events of its own, else what is wrong.
    private static String citesOwn(final Map<String, String> run,
                                   final List<Map<String, String>> runs,
                                   final List<Map<String, String>> events) {
        final String how = Objects.requireNonNullElse(run.get("how"), "");
        if (!how.startsWith("model")) {
            return "run " + run.get("id") + " wasn't described by the model (how: " + how + ")";
        }
        final String id = run.get("id");
        final String code = id.contains("-")
                ? id.substring(0, id.indexOf('-'))
                : id;
        // The run code made: its parts' events, in time order, as the model was given them.
        final List<Map<String, String>> whole = new ArrayList<>();
        for (final Map<String, String> part : runs) {
            if (part.get("id").equals(code) || part.get("id").startsWith(code + "-")) {
                events.stream().filter(event -> part.get("id").equals(event.get("merged"))).forEach(whole::add);
            }
        }
        whole.sort(Comparator.comparing((Map<String, String> event) -> event.get("time")).thenComparing(
                EvalTimelineScore::ref));
        final Set<String> own = members(id, 2, events);
        final Matcher matcher = CITED.matcher(how);
        final List<String> cited = new ArrayList<>();
        final List<String> wrong = new ArrayList<>();
        while (matcher.find()) {
            final int n = Integer.parseInt(matcher.group(1));
            cited.add("e" + n);
            if (n < 1 || n > whole.size() || !own.contains(ref(whole.get(n - 1)))) {
                wrong.add("e" + n);
            }
        }
        if (cited.isEmpty()) {
            return "run " + id + "'s description cites no event (how: " + how + ")";
        }
        return wrong.isEmpty()
                ? null
                : "run " + id + " cites " + wrong + ", not events of its own";
    }

    private static String runsText(final List<Map<String, String>> runs) {
        return runs.isEmpty()
                ? "none"
                : String.join("; ", runs.stream()
                        .map(row -> row.get("id") + " '" + row.get("bar_label") + "' (" + row.get("events")
                                    + " events, parent " + row.get("parent") + ")")
                        .toList());
    }

    // ------------------------------------------------------------------------------------------------
    // Noise

    private static void scoreNoise(final Map<String, Object> noise,
                                   final Run run,
                                   final Tables tables,
                                   final Tables baseline,
                                   final Checks checks) {
        final List<Form> asked = run.forms().stream()
                .filter(form -> properties(form.schema()).containsKey(NOISE_FIELD))
                .toList();
        if (Boolean.TRUE.equals(noise.get("form"))) {
            final Form form = asked.isEmpty()
                    ? null
                    : asked.getFirst();
            final List<String> offered = form == null
                    ? List.of()
                    : choices(form.schema());
            final List<String> given = form == null
                    ? List.of()
                    : EvalCases.strings(form.content() == null
                            ? null
                            : form.content().get(NOISE_FIELD));
            final boolean all = !offered.isEmpty() && new LinkedHashSet<>(given).equals(new LinkedHashSet<>(offered));
            checks.check("noise form accepted", asked.size() == 1 && "ACCEPTED".equals(form.status()) && all,
                    asked.isEmpty()
                            ? "no form asked which noise to leave out"
                            : asked.size() + " noise form(s); the first " + form.status() + " leaving out " + given
                              + " of " + offered);
        }
        final List<EvalScore.ToolUse> calls = run.calls().stream()
                .filter(call -> TIMELINE_TOOL.equals(call.name()) && !call.refused())
                .toList();
        if (noise.get("excluded_at_least") instanceof final Number least) {
            final List<Long> said = calls.stream().map(EvalTimelineScore::excluded).toList();
            checks.check("excluded reported", !said.isEmpty() && said.stream().allMatch(count -> count != null
                                                                                         && count >= least
                    .longValue()), "each timeline was to say it left out at least " + least + " events as noise; "
                                   + "they said " + said);
        }
        if (Boolean.TRUE.equals(noise.get("remembered"))) {
            if (calls.size() < 2) {
                checks.note("the turn built the timeline once: not re-run, so remembering wasn't checked");
            } else {
                final Long again = excluded(calls.getLast());
                checks.check("exclusion remembered", asked.size() <= 1 && again != null && again > 0,
                        "the later run " + (asked.size() > 1
                                ? "asked again (" + asked.size() + " noise forms)"
                                : "left out " + again + " events"));
            }
        }
        final List<Map<String, String>> events = tables.rows(EVENTS);
        if (noise.get("gone") != null) {
            final Pattern gone = Pattern.compile(String.valueOf(noise.get("gone")), Pattern.CASE_INSENSITIVE);
            final long left = events == null
                    ? -1
                    : events.stream().filter(event -> gone.matcher(eventText(event)).find()).count();
            checks.check("noise left out", left == 0, events == null
                    ? "no " + EVENTS + " was written"
                    : left + " event(s) matching " + noise.get("gone") + " are still in the timeline");
        }
        if (noise.get("kept") != null) {
            final Pattern kept = Pattern.compile(String.valueOf(noise.get("kept")), Pattern.CASE_INSENSITIVE);
            final int least = noise.get("kept_at_least") instanceof final Number number
                    ? number.intValue()
                    : 1;
            final long held = events == null
                    ? 0
                    : events.stream().filter(event -> kept.matcher(eventText(event)).find()).count();
            checks.check("own activity kept", held >= least, held + " event(s) matching " + noise.get("kept")
                                                             + " kept, expected at least " + least);
        }
        for (final String file : EvalCases.strings(noise.get("fewer_than_baseline"))) {
            final List<Map<String, String>> now = tables.rows(file);
            final List<Map<String, String>> before = baseline.rows(file);
            checks.check("fewer in " + file + " than the baseline", now != null && before != null
                                                                    && now.size() < before.size(),
                    file + " has " + (now == null
                            ? "no"
                            : now.size()) + " row(s), the baseline " + (before == null
                            ? "none (no baseline)"
                            : before.size()));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Sources

    private static void scoreSources(final Map<String, Object> sources,
                                     final Run run,
                                     final Tables tables,
                                     final Checks checks) {
        final List<Form> asked = run.forms().stream().filter(form -> TIMELINE_TOOL.equals(form.tool())).toList();
        final Set<String> offered = new LinkedHashSet<>();
        asked.forEach(form -> properties(form.schema()).values().forEach(field -> offered.addAll(listed(
                EvalCases.map(field)))));
        // With no form, the model chose from list_data_sources, which leaves out indexes holding no data: the case's
        // sources it lists are those offered (seen in case 117 on Haiku and Sonnet, 2026-11-27).
        final String list = asked.isEmpty()
                ? run.calls().stream()
                        .filter(call -> LIST_TOOL.equals(call.name()))
                        .map(call -> Objects.requireNonNullElse(call.text(), ""))
                        .reduce((first, second) -> second)
                        .orElse(null)
                : null;
        final Set<String> named = new LinkedHashSet<>(EvalCases.strings(sources.get("offered")));
        named.addAll(EvalCases.strings(sources.get("not_offered")));
        named.addAll(EvalCases.map(sources.get("left_out")).keySet());
        if (list != null) {
            listedSources(list).stream().filter(named::contains).forEach(offered::add);
        }
        if (sources.get("offered") != null) {
            final Set<String> wanted = new LinkedHashSet<>(EvalCases.strings(sources.get("offered")));
            checks.check("sources offered", offered.equals(wanted), asked.isEmpty() && list == null
                    ? "no form asked which data sources to read"
                    : "the forms (or the list) offered " + offered + ", expected " + wanted);
        }
        final List<String> optionsText = asked.stream()
                .map(form -> String.join("\n", allOptions(form.schema())))
                .toList();
        for (final String name : EvalCases.strings(sources.get("not_offered"))) {
            checks.check("not offered: " + name, optionsText.stream().noneMatch(text -> text.contains(name))
                                                 && (list == null || !listedSources(list).contains(name)),
                    name + " was offered in a form or listed");
        }
        final List<String> said = new ArrayList<>();
        asked.forEach(form -> said.add(Objects.requireNonNullElse(form.message(), "")));
        if (list != null) {
            said.add(list);
        }
        run.calls().stream()
                .filter(call -> TIMELINE_TOOL.equals(call.name()))
                .forEach(call -> said.add(Objects.requireNonNullElse(call.text(), "")));
        EvalCases.map(sources.get("left_out")).forEach((name, why) -> {
            final Pattern reason = Pattern.compile(String.valueOf(why), Pattern.CASE_INSENSITIVE);
            checks.check("left out, saying why: " + name, said.stream().anyMatch(text -> text.contains(name)
                                                                                        && reason.matcher(text)
                                                                                                .find()),
                    "no form or result names " + name + " with why (" + why + ")");
        });
        if (sources.get("read") != null) {
            final Set<String> wanted = new LinkedHashSet<>(EvalCases.strings(sources.get("read")));
            final List<Map<String, String>> events = tables.rows(EVENTS);
            final Set<String> read = new LinkedHashSet<>();
            if (events != null) {
                events.forEach(event -> read.add(event.get("source")));
            }
            checks.check("sources read", events != null && !events.isEmpty() && read.equals(wanted), events == null
                    ? "no " + EVENTS + " was written"
                    : events.size() + " event(s) from " + read + ", expected events from " + wanted);
        }
    }

    // The values a form's field offers as a list: its items' enum (or its own enum, of a field of one value).
    private static List<String> listed(final Map<String, Object> field) {
        if (!"array".equals(field.get("type"))) {
            return List.of();
        }
        return EvalCases.strings(EvalCases.map(field.get("items")).get("enum"));
    }

    // Every option a form's fields offer: their enums and their items' enums.
    private static List<String> allOptions(final Map<String, Object> schema) {
        final List<String> out = new ArrayList<>();
        properties(schema).values().forEach(value -> {
            final Map<String, Object> field = EvalCases.map(value);
            out.addAll(EvalCases.strings(field.get("enum")));
            out.addAll(EvalCases.strings(EvalCases.map(field.get("items")).get("enum")));
        });
        return out;
    }

    // The events a timeline call said it left out as noise: its JSON's, else its chat text's; null if it says none.
    private static Long excluded(final EvalScore.ToolUse call) {
        if (call.result().get("excluded") instanceof final Map<?, ?> excluded
            && excluded.get("events") instanceof final Number events) {
            return events.longValue();
        }
        final Matcher matcher = LEFT_OUT.matcher(Objects.requireNonNullElse(call.text(), ""));
        return matcher.find()
                ? Long.parseLong(matcher.group(1))
                : null;
    }

    private static Map<String, Object> properties(final Map<String, Object> schema) {
        return schema != null && schema.get("properties") instanceof final Map<?, ?> properties
                ? EvalCases.map(properties)
                : Map.of();
    }

    // The values a form's list field offers.
    private static List<String> choices(final Map<String, Object> schema) {
        final Map<String, Object> field = EvalCases.map(properties(schema).get(NOISE_FIELD));
        final Map<String, Object> items = EvalCases.map(field.get("items"));
        return listOfMaps(items.get("anyOf")).stream().map(choice -> String.valueOf(choice.get("const"))).toList();
    }

    // ------------------------------------------------------------------------------------------------

    // What an event touched and its title, for matching.
    private static String eventText(final Map<String, String> event) {
        return Objects.requireNonNullElse(event.get("object"), "") + " " + Objects.requireNonNullElse(
                event.get("title"), "");
    }

    private static String ref(final Map<String, String> event) {
        return event.get("StreamId") + ":" + event.get("EventId");
    }

    private static boolean blank(final String value) {
        return value == null || value.isBlank();
    }

    private static String first(final List<String> values) {
        return values.size() <= 3
                ? String.join("; ", values)
                : String.join("; ", values.subList(0, 3)) + " and " + (values.size() - 3) + " more";
    }

    private static List<Map<String, Object>> listOfMaps(final Object value) {
        return value instanceof final List<?> list
                ? list.stream().map(EvalCases::map).toList()
                : List.of();
    }

    /// A CSV table's rows, by its header's names; a quoted value (holding a comma or a quote) read whole.
    ///
    /// @param text The table.
    /// @return Its rows.
    static List<Map<String, String>> csv(final String text) {
        final List<String> lines = text.lines().filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        final List<String> header = cells(lines.getFirst());
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final String line : lines.subList(1, lines.size())) {
            final List<String> cells = cells(line);
            final Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < cells.size()
                        ? cells.get(i)
                        : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> cells(final String line) {
        final List<String> cells = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }


    // --------------------------------------------------------------------------------


    /// Where the checks are said.
    interface Checks {

        /// @param check   The check's name.
        /// @param passed  Whether it passed.
        /// @param problem What is wrong, if it didn't.
        void check(String check, boolean passed, String problem);

        /// @param note Something worth saying that isn't a check.
        void note(String note);
    }

    /// What a run left to score.
    ///
    /// @param calls    Its tool calls, in order (in a chat, a result's text only).
    /// @param files    A file of the timeline's workspace by its path, or null if there is none.
    /// @param baseline The same files as they were before the turn scored (after a chat's earlier turns); null for
    ///                 each where there was none.
    /// @param forms    The forms the turn asked the user, in order.
    record Run(List<EvalScore.ToolUse> calls,
               Function<String, String> files,
               Function<String, String> baseline,
               List<Form> forms) {

        /// Copies the lists; no baseline or forms where none are given.
        Run {
            calls = List.copyOf(calls);
            baseline = Objects.requireNonNullElse(baseline, path -> null);
            forms = List.copyOf(Objects.requireNonNullElse(forms, List.of()));
        }
    }

    /// A form the user was asked.
    ///
    /// @param tool    The tool that asked.
    /// @param message What it says.
    /// @param schema  Its fields, as a flat JSON schema.
    /// @param status  What became of it, e.g. `ACCEPTED`.
    /// @param content The user's answer, once accepted; else null.
    record Form(String tool, String message, Map<String, Object> schema, String status, Map<String, Object> content) {

    }

    // A level's row and its member events.
    private record Unit(String id, int level, Set<String> members) {

    }

    // The timeline's tables, each read once.
    private static final class Tables {

        private final Function<String, String> files;
        private final String folder;
        private final Map<String, List<Map<String, String>>> read = new LinkedHashMap<>();

        private Tables(final Function<String, String> files, final String folder) {
            this.files = files;
            this.folder = folder;
        }

        // A table's rows, or null where it wasn't written.
        List<Map<String, String>> rows(final String file) {
            return read.computeIfAbsent(file, name -> {
                final String text = files.apply(folder + name);
                return text == null
                        ? null
                        : csv(text);
            });
        }
    }
}
