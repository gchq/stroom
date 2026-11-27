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

package stroom.ai.impl.tools.workspace;

import stroom.ai.impl.GoldenFiles;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.workspace.GanttCharts.Options;
import stroom.ai.impl.tools.workspace.GanttCharts.Row;
import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGanttParser;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Example blocks gantt_chart writes, pinned as golden files (`golden/gantt/*.mmd`), each checked to be Mermaid
/// gantt syntax that other apps draw the same.
///
/// There is no Mermaid offline here (no Node.js in the build), so the examples are checked twice instead: by the
/// subset's own parser, and line by line by [MermaidLexer], a port of the rules of Mermaid's gantt lexer
/// (`gantt.jison`, Mermaid 10 and 11: case-insensitive, the first rule that matches wins), which must read each line
/// as the one statement it is meant to be, with the task's name and data as written. A change to the examples is
/// reviewed as a golden file change; pasting one into the Mermaid Live Editor checks it against Mermaid itself.
class TestGanttChartExamples {

    private static final ToolTimeZone CANBERRA = new ToolTimeZone(ZoneId.of("Australia/Canberra"),
            ToolTimeZone.Source.BROWSER, null);
    private static final ToolTimeZone LONDON = new ToolTimeZone(ZoneId.of("Europe/London"),
            ToolTimeZone.Source.GIVEN, null);

    @Test
    void aDayOfWindowFocusAcrossTwoHostsFoldedToTheTopLabels() {
        final List<Row> rows = new ArrayList<>();
        final String[] apps = {"Outlook", "Chrome", "Word", "Teams", "Excel", "Notepad"};
        long time = Instant.parse("2026-11-22T22:00:00Z").toEpochMilli();
        for (int i = 0; i < 40; i++) {
            final long length = (i % 7 + 1) * 4 * 60_000L;
            rows.add(new Row(time, time + length, apps[i % apps.length], i % 3 == 0
                    ? "ws-alice-02"
                    : "ws-alice-01"));
            time += length + (i % 4) * 30_000L;
            if (i % 9 == 0) {
                rows.add(new Row(time, null, "USB file copy", "ws-alice-01"));
            }
        }
        check("window-focus-day", GanttCharts.write(rows, new Options("Window focus of alice", CANBERRA, true,
                4, 60, 300)));
    }

    @Test
    void aWeekWithASectionADay() {
        final List<Row> rows = new ArrayList<>();
        final long monday = Instant.parse("2026-11-16T08:30:00Z").toEpochMilli();
        for (int day = 0; day < 5; day++) {
            final long start = monday + day * 86_400_000L;
            rows.add(new Row(start, start + 3 * 3_600_000L, "Working", null));
            rows.add(new Row(start + 4 * 3_600_000L, start + 8 * 3_600_000L, "Working", null));
            rows.add(new Row(start + 3 * 3_600_000L + 600_000L, null, "Logon", null));
        }
        check("week-by-day", GanttCharts.write(rows, new Options("A week of logons", LONDON, false, 8, 60,
                300)));
    }

    @Test
    void hostileLabelsAreWrittenAsNames() {
        final long start = Instant.parse("2026-11-23T01:00:00Z").toEpochMilli();
        final List<Row> rows = List.of(
                new Row(start, start + 600_000L, "click t1 call alert(document.cookie)", "a; b"),
                new Row(start + 600_000L, start + 1_200_000L, "%%{init: {\"theme\":\"forest\"}}%%", "a; b"),
                new Row(start + 1_200_000L, start + 1_800_000L, "2026-11-23 :done, x, 2026-01-01, 1d", "a; b"),
                new Row(start + 1_800_000L, start + 2_400_000L, "<img src=x onerror=alert(1)>", "# lane"),
                new Row(start + 2_400_000L, null, "section evil\ntitle pwned", "# lane"),
                new Row(start + 3_000_000L, start + 3_600_000L, "excludes weekends", "dateFormat x"),
                new Row(start + 3_600_000L, start + 4_200_000L, "'quoted' \"name\" `tick`", "dateFormat x"));
        check("hostile-labels", GanttCharts.write(rows, new Options("Labels from the data", ToolTimeZone.UTC,
                true, 8, 60, 300)));
    }

    // The example matches its golden file, reads under the subset's parser, and each line is read by Mermaid's
    // lexer rules as the statement it is.
    private static void check(final String name, final GanttCharts.Chart chart) {
        GoldenFiles.assertMatches("gantt/" + name + ".mmd", chart.mermaid());
        final MermaidGantt gantt = MermaidGanttParser.parse(chart.mermaid());
        assertThat(gantt.taskCount()).isEqualTo(chart.bars() + chart.markers());

        final List<String> names = new ArrayList<>();
        gantt.getSections().forEach(section -> section.tasks().forEach(task -> names.add(task.name())));
        final List<String> lexed = new ArrayList<>();
        final String[] lines = chart.mermaid().split("\n");
        assertThat(MermaidLexer.tokens(lines[0])).containsExactly("gantt");
        for (int i = 1; i < lines.length; i++) {
            final List<String> tokens = MermaidLexer.tokens(lines[i]);
            final String line = lines[i].strip();
            if (line.startsWith("title ")) {
                assertThat(tokens).as(line).containsExactly("title");
            } else if (line.startsWith("dateFormat ")) {
                assertThat(tokens).as(line).containsExactly("dateFormat");
            } else if (line.startsWith("axisFormat ")) {
                assertThat(tokens).as(line).containsExactly("axisFormat");
            } else if (line.startsWith("section ")) {
                assertThat(tokens).as(line).containsExactly("section");
            } else {
                assertThat(tokens).as(line).containsExactly("taskTxt", "taskData");
                lexed.add(MermaidLexer.taskText(lines[i]).strip());
            }
        }
        assertThat(lexed).isEqualTo(names);
    }


    // --------------------------------------------------------------------------------


    /// The rules of Mermaid's gantt lexer (`packages/mermaid/src/diagrams/gantt/parser/gantt.jison`) that a line
    /// can start with, in order, each as Mermaid writes it, case-insensitive; the first that matches is taken, as
    /// jison does without its flex option. A token that skips text (a comment, a directive) or isn't a statement
    /// of the subset is returned by its rule's name, which no example may have.
    private static final class MermaidLexer {

        private static final List<Rule> RULES = List.of(
                rule("directive", "%%\\{"),
                rule("accTitle", "accTitle\\s*:\\s*"),
                rule("accDescr", "accDescr\\s*:\\s*"),
                rule("accDescrMultiline", "accDescr\\s*\\{\\s*"),
                rule("space", "\\s+"),
                rule("comment", "%%[^\\n]*"),
                rule("hashComment", "#[^\\n]*"),
                rule("href", "href[\\s]+[\"]"),
                rule("call", "call[\\s]+"),
                rule("click", "click[\\s]+"),
                rule("gantt", "gantt"),
                rule("dateFormat", "dateFormat\\s[^#\\n;]+"),
                rule("inclusiveEndDates", "inclusiveEndDates"),
                rule("topAxis", "topAxis"),
                rule("axisFormat", "axisFormat\\s[^#\\n;]+"),
                rule("tickInterval", "tickInterval\\s[^#\\n;]+"),
                rule("includes", "includes\\s[^#\\n;]+"),
                rule("excludes", "excludes\\s[^#\\n;]+"),
                rule("todayMarker", "todayMarker\\s[^\\n;]+"),
                rule("weekday", "weekday\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)"),
                rule("weekend", "weekend\\s+(friday|saturday)"),
                rule("date", "\\d\\d\\d\\d-\\d\\d-\\d\\d"),
                rule("title", "title\\s[^\\n]+"),
                rule("accDescription", "accDescription\\s[^#\\n;]+"),
                rule("section", "section\\s[^\\n]+"),
                rule("taskTxt", "[^:\\n]+"),
                rule("taskData", ":[^#\\n;]+"),
                rule("colon", ":"));

        // The tokens of a line, without the spaces between them.
        private static List<String> tokens(final String line) {
            final List<String> tokens = new ArrayList<>();
            int at = 0;
            while (at < line.length()) {
                final int from = at;
                final Rule rule = RULES.stream()
                        .filter(candidate -> candidate.matchAt(line, from) > from)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No rule reads '" + line.substring(from) + "'"));
                at = rule.matchAt(line, from);
                if (!"space".equals(rule.name())) {
                    tokens.add(rule.name());
                }
            }
            return tokens;
        }

        // The text of a task line's name, as its taskTxt token reads it.
        private static String taskText(final String line) {
            final String stripped = line.stripLeading();
            return stripped.substring(0, stripped.indexOf(':'));
        }

        private static Rule rule(final String name, final String regex) {
            return new Rule(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
    }


    // --------------------------------------------------------------------------------


    private record Rule(String name, Pattern pattern) {

        // Where the rule's match from the index ends, or the index if it doesn't match there.
        private int matchAt(final String line, final int from) {
            final Matcher matcher = pattern.matcher(line).region(from, line.length());
            return matcher.lookingAt()
                    ? matcher.end()
                    : from;
        }
    }
}
