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
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.CompareActivityTool.Periods;
import stroom.ai.impl.tools.compare.CompareActivityTool.Planned;
import stroom.ai.impl.tools.compare.PopulationScreen.Ranked;
import stroom.ai.impl.tools.compare.RareValues.FieldValues;
import stroom.ai.impl.tools.compare.RareValues.Held;
import stroom.ai.impl.tools.compare.RareValues.Holder;
import stroom.ai.impl.tools.compare.RareValues.Picked;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What `compare_activity`'s population screen gives: the people ranked by their own findings (at most
/// [PopulationScreen#TOP], each with their largest findings as indicators with another explanation), how many others
/// were scored, the rare values found (or the fields that could be screened for them), the sources and their people,
/// what it cost, and the files: every person scored, every finding and every rare value, as CSV.
final class ScreenResult {

    /// What every screen's result says first.
    static final String INDICATORS = CompareResult.INDICATORS + " A rank says only whose activity departs most "
                                     + "from their own baseline in these aggregates.";
    static final String PEOPLE_FILE = "people.csv";
    static final String RARE_FILE = "rare.csv";
    /// The findings shown a person.
    static final int SHOWN_FINDINGS = 3;

    private ScreenResult() {
        // Static only.
    }

    /// Writes the files: every person scored (ranked first, then the rest), every finding by person (whether it
    /// counted in the ranking), every rare value with its holders.
    ///
    /// @param workspace The chat's workspace.
    /// @param run       What was screened.
    /// @return The files written.
    static List<WorkspacePath> write(final SessionWorkspace workspace, final Run run) {
        final List<List<String>> people = new ArrayList<>();
        final List<List<String>> findings = new ArrayList<>();
        final Map<String, Ranked> rankedOf = new LinkedHashMap<>();
        for (int i = 0; i < run.ranked().size(); i++) {
            final Ranked ranked = run.ranked().get(i);
            rankedOf.put(ranked.person(), ranked);
            people.add(List.of(String.valueOf(i + 1), ranked.person(), ActivityScoring.round(ranked.score()),
                    String.valueOf(ranked.findings().size()), String.join("; ", ranked.sources()),
                    ranked.findings().getFirst().evidence()));
        }
        run.unflagged().forEach(person -> people.add(List.of("", person, "0", "0", "", "")));
        // Every finding, the ranked people's first, each saying whether it counted in the ranking.
        final List<String> order = new ArrayList<>(rankedOf.keySet());
        run.all().keySet().stream().filter(person -> !rankedOf.containsKey(person)).sorted().forEach(order::add);
        for (final String person : order) {
            final Ranked ranked = rankedOf.get(person);
            final String rank = ranked == null
                    ? ""
                    : String.valueOf(run.ranked().indexOf(ranked) + 1);
            for (final Finding finding : run.all().getOrDefault(person, List.of())) {
                final boolean counted = ranked != null && ranked.findings().contains(finding);
                findings.add(List.of(person, rank, finding.source(), finding.kind().name().toLowerCase(),
                        finding.feature(), finding.window(), ActivityScoring.shown(finding.feature(),
                                finding.baseline()), ActivityScoring.shown(finding.feature(), finding.value()),
                        ActivityScoring.round(finding.score()), String.valueOf(counted), String.valueOf(
                                finding.sourceWide()), finding.evidence(), finding.alternative()));
            }
        }
        final WorkspacePath peoplePath = WorkspacePath.parse(run.folder() + PEOPLE_FILE);
        ToolTables.write(workspace, peoplePath, List.of("rank", "person", "score", "findings", "sources",
                "top_finding"), people, null);
        final WorkspacePath findingsPath = WorkspacePath.parse(run.folder() + CompareActivityTool.FINDINGS_FILE);
        ToolTables.write(workspace, findingsPath, List.of("person", "rank", "source", "kind", "feature", "window",
                "baseline", "value", "score", "ranked", "source_wide", "evidence", "alternative"), findings, null);
        final List<WorkspacePath> files = new ArrayList<>(List.of(peoplePath, findingsPath));
        if (!run.rare().isEmpty()) {
            final List<List<String>> rows = new ArrayList<>();
            for (final Rare rare : run.rare()) {
                for (final Held held : rare.held()) {
                    rows.add(List.of(rare.source(), rare.picked().field(), held.value().value(),
                            String.valueOf(held.value().people()), String.valueOf(held.value().events()),
                            held.value().first().toString(), held.value().last().toString(),
                            String.join("; ", held.holders().stream().map(Holder::person).toList()),
                            String.join("; ", held.holders().stream()
                                    .filter(Holder::isNew)
                                    .map(Holder::person)
                                    .toList())));
                }
            }
            final WorkspacePath rarePath = WorkspacePath.parse(run.folder() + RARE_FILE);
            ToolTables.write(workspace, rarePath, List.of("source", "field", "value", "people", "events", "first",
                    "last", "holders", "new_for"), rows, null);
            files.add(rarePath);
        }
        return files;
    }

    /// The result over MCP, as JSON.
    ///
    /// @param run   What was screened.
    /// @param files The files written.
    /// @return The structured content.
    static Map<String, Object> structured(final Run run, final List<WorkspacePath> files) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", CompareActivityTool.POPULATION);
        result.put("baseline", Map.of("from", run.periods().baselineFrom().toString(), "to",
                run.periods().baselineTo().minusDays(1).toString()));
        result.put("window", Map.of("from", run.periods().windowFrom().toString(), "to",
                run.periods().windowTo().minusDays(1).toString()));
        result.put("units", run.units().stream().map(Unit::word).toList());
        result.put("time_zone", run.zone().structured());
        result.put("note", INDICATORS);
        result.put("where", run.where());
        result.put("sources", sources(run));
        result.put("population", run.population());
        result.put("flagged", run.ranked().size());
        final List<Map<String, Object>> ranked = new ArrayList<>();
        for (int i = 0; i < Math.min(PopulationScreen.TOP, run.ranked().size()); i++) {
            final Ranked one = run.ranked().get(i);
            final Map<String, Object> person = new LinkedHashMap<>();
            person.put("rank", i + 1);
            person.put("person", one.person());
            person.put("score", Math.round(one.score() * 100) / 100.0);
            person.put("sources", one.sources());
            person.put("findings_total", one.findings().size());
            person.put("findings", one.findings().stream().limit(SHOWN_FINDINGS).map(ScreenResult::finding)
                    .toList());
            ranked.add(person);
        }
        result.put("ranked", ranked);
        result.put("others_scored", run.population() - ranked.size());
        if (!run.rare().isEmpty()) {
            result.put("rare", run.rare().stream().map(ScreenResult::rare).toList());
        }
        if (!run.rareFields().isEmpty()) {
            final List<Map<String, Object>> offered = new ArrayList<>();
            run.rareFields().forEach((source, fields) -> fields.forEach(field -> {
                final Map<String, Object> one = new LinkedHashMap<>();
                one.put("index", source);
                one.put("field", field.field());
                one.put("values", field.values());
                offered.add(one);
            }));
            result.put("rare_fields", offered);
        }
        result.put("source_wide", run.sourceWide());
        result.put("notes", run.notes());
        result.put("queries", run.queries());
        result.put("files", files.stream().map(WorkspacePath::path).toList());
        result.put("next_step", nextStep(run));
        return result;
    }

    /// The result in the chat, as Markdown.
    ///
    /// @param run   What was screened.
    /// @param files The files written.
    /// @return The text.
    static String markdown(final Run run, final List<WorkspacePath> files) {
        final StringBuilder sb = new StringBuilder();
        sb.append("**").append(CompareActivityTool.NAME).append("** (population screen): ").append(run.population())
                .append(" people, each against their own baseline, ").append(run.periods().describe())
                .append(", by ").append(String.join(", ", run.units().stream().map(Unit::word).toList()))
                .append(", in ").append(run.zone().label());
        if (!run.where().isEmpty()) {
            sb.append(", where ").append(String.join(" and ", run.where()));
        }
        sb.append(".\n\n").append(INDICATORS).append("\n\n**Sources**\n");
        for (final Map<String, Object> source : sources(run)) {
            sb.append("- ").append(source.get("index")).append(": ");
            if (source.containsKey("left_out")) {
                sb.append("left out: ").append(source.get("left_out")).append('\n');
            } else {
                sb.append(source.get("by")).append("; ").append(source.get("people")).append(" people\n");
            }
        }
        sb.append("\n**Ranked** (").append(run.ranked().size()).append(" of ").append(run.population())
                .append(" with any finding");
        if (run.ranked().size() > PopulationScreen.TOP) {
            sb.append("; the first ").append(PopulationScreen.TOP);
        }
        sb.append(")\n");
        if (run.ranked().isEmpty()) {
            sb.append("None: no one's activity departs from their own baseline past the thresholds.\n");
        }
        for (int i = 0; i < Math.min(PopulationScreen.TOP, run.ranked().size()); i++) {
            final Ranked one = run.ranked().get(i);
            sb.append(i + 1).append(". ").append(one.person()).append(" (score ")
                    .append(ActivityScoring.round(one.score())).append(", ").append(one.findings().size())
                    .append(" finding(s))\n");
            for (final Finding finding : one.findings().stream().limit(SHOWN_FINDINGS).toList()) {
                sb.append("   - [").append(finding.source()).append("] ").append(finding.evidence())
                        .append(". Alternatively: ").append(finding.alternative()).append('\n');
            }
        }
        sb.append('\n').append(run.population() - Math.min(PopulationScreen.TOP, run.ranked().size()))
                .append(" others scored.\n");
        for (final Rare rare : run.rare()) {
            sb.append("\n**Rare values** (").append(rare.source()).append(", ").append(rare.picked().field())
                    .append(": held by at most ").append(rare.picked().threshold()).append(" of ")
                    .append(rare.picked().population()).append(" people)\n");
            if (rare.held().isEmpty()) {
                sb.append("None.\n");
            }
            rare.held().stream().limit(RareValues.LISTED).forEach(held -> sb.append("- ").append(held.value()
                    .value()).append(": ").append(held.value().people()).append(" people (").append(String.join(
                    ", ", held.holders().stream()
                            .map(holder -> holder.person() + (holder.isNew()
                                    ? ", new from " + holder.first().atZone(run.zone().zone()).toLocalDate()
                                    : ""))
                            .toList())).append(")\n"));
            NullSafe.consume(rare.note(), note -> sb.append("- ").append(note).append('\n'));
        }
        if (!run.rareFields().isEmpty()) {
            sb.append("\n**Fields to screen for rare values** (rare: a field; many values each)\n");
            run.rareFields().forEach((source, fields) -> sb.append("- ").append(source).append(": ")
                    .append(String.join(", ", fields.stream()
                            .map(field -> field.field() + " (" + field.values() + ")")
                            .toList())).append('\n'));
        }
        if (!run.sourceWide().isEmpty()) {
            sb.append("\n**Explained by the source** (everyone else's activity moved as far)\n");
            run.sourceWide().forEach(line -> sb.append("- ").append(line).append('\n'));
        }
        if (!run.notes().isEmpty()) {
            sb.append("\n**Notes**\n");
            run.notes().forEach(note -> sb.append("- ").append(note).append('\n'));
        }
        sb.append("\nFiles: ").append(String.join(", ", files.stream().map(WorkspacePath::path).toList()))
                .append(". ").append(run.queries()).append(" aggregate queries run.\n\n").append(nextStep(run));
        return sb.toString();
    }

    // The next step: one person's comparison or timeline.
    private static String nextStep(final Run run) {
        if (run.ranked().isEmpty()) {
            return "Next: a longer period, other sources or where conditions, or compare_activity on one person "
                   + "named by the question.";
        }
        final String first = run.ranked().getFirst().person();
        return "Next: compare_activity with person " + first + " (and the window of their findings) for their "
               + "full comparison, or activity_timeline on their flagged days; one person at a time.";
    }

    private static List<Map<String, Object>> sources(final Run run) {
        final List<Map<String, Object>> sources = new ArrayList<>();
        for (final Planned planned : run.planned()) {
            final Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", planned.doc().getName());
            if (planned.usable()) {
                source.put("by", planned.label());
                source.put("people", run.people().getOrDefault(planned.source().name(), 0));
                source.put("breadth_fields", planned.fields().breadth());
                source.put("sum_fields", planned.fields().sums());
            } else {
                source.put("left_out", planned.leftOut());
            }
            sources.add(source);
        }
        return sources;
    }

    private static Map<String, Object> finding(final Finding finding) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", finding.source());
        map.put("kind", finding.kind().name().toLowerCase());
        map.put("feature", finding.feature());
        map.put("window", finding.window());
        map.put("score", Math.round(finding.score() * 100) / 100.0);
        map.put("evidence", finding.evidence());
        map.put("alternative", finding.alternative());
        return map;
    }

    private static Map<String, Object> rare(final Rare rare) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", rare.source());
        map.put("field", rare.picked().field());
        map.put("threshold", rare.picked().threshold());
        map.put("population", rare.picked().population());
        map.put("rare_values_total", rare.held().size());
        map.put("values", rare.held().stream().limit(RareValues.LISTED).map(held -> {
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("value", held.value().value());
            value.put("people", held.value().people());
            value.put("events", held.value().events());
            value.put("first", held.value().first().toString());
            value.put("holders", held.holders().stream().map(Holder::person).toList());
            value.put("new_for", held.holders().stream().filter(Holder::isNew).map(Holder::person).toList());
            return value;
        }).toList());
        if (rare.note() != null) {
            map.put("note", rare.note());
        }
        return map;
    }


    // --------------------------------------------------------------------------------


    /// A source's rare values.
    ///
    /// @param source The source's name.
    /// @param picked The rare values picked from its field.
    /// @param held   Each with its holders, in rank.
    /// @param note   The hostname (CDN noise) note, or null.
    record Rare(String source, Picked picked, List<Held> held, String note) {

        /// Copies the list.
        Rare {
            held = List.copyOf(held);
        }
    }


    // --------------------------------------------------------------------------------


    /// What was screened.
    ///
    /// @param periods    The baseline and window.
    /// @param units      The units windows are compared in.
    /// @param zone       The zone.
    /// @param where      The call's conditions, in words (`process.name = powershell.exe`).
    /// @param planned    The sources, those left out with why.
    /// @param people     Each source's people scored, by its name.
    /// @param population The people scored across the sources.
    /// @param ranked     The people with any finding, highest first.
    /// @param unflagged  The people scored with no finding.
    /// @param all        Every person's findings, ranked or not (shared, earlier, absences), largest first.
    /// @param rare       Each source's rare values, where `rare` was given.
    /// @param rareFields Each source's fields to offer for `rare`, where it wasn't.
    /// @param sourceWide The shifts everyone shared, in words, once each.
    /// @param notes      What the result should say.
    /// @param queries    The aggregate queries run.
    /// @param folder     The workspace folder of the files.
    record Run(Periods periods,
               List<Unit> units,
               ToolTimeZone zone,
               List<String> where,
               List<Planned> planned,
               Map<String, Integer> people,
               int population,
               List<Ranked> ranked,
               List<String> unflagged,
               Map<String, List<Finding>> all,
               List<Rare> rare,
               Map<String, List<FieldValues>> rareFields,
               List<String> sourceWide,
               List<String> notes,
               int queries,
               String folder) {

        /// Copies the collections.
        Run {
            units = List.copyOf(units);
            where = List.copyOf(where);
            planned = List.copyOf(planned);
            people = new LinkedHashMap<>(people);
            ranked = List.copyOf(ranked);
            unflagged = List.copyOf(unflagged);
            all = new LinkedHashMap<>(all);
            rare = List.copyOf(rare);
            rareFields = new LinkedHashMap<>(rareFields);
            sourceWide = List.copyOf(sourceWide);
            notes = List.copyOf(notes);
        }
    }
}
