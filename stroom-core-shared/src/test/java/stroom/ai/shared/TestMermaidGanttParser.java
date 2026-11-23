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

package stroom.ai.shared;

import stroom.ai.shared.MermaidGantt.Section;
import stroom.ai.shared.MermaidGantt.Task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Mermaid gantt subset Stroom AI writes and its chat draws: what reads, and what is outside it (shown as
/// code, never run).
class TestMermaidGanttParser {

    private static final String CHART = """
            gantt
                title Activity of alice (times in Europe/London)
                dateFormat YYYY-MM-DD HH:mm:ss
                axisFormat %H:%M
                section host-1
                Chrome :2026-11-23 09:00:00, 2026-11-23 09:30:00
                Word :done, w1, 2026-11-23 09:30:00, 45m
                Copy :milestone, 2026-11-23 09:40:00, 0d
                section host-2
                Outlook :crit, active, 2026-11-23 10:00:00, 2026-11-23 10:00:30
            """;

    @Test
    void aChartOfTheSubsetIsReadWithItsSectionsTasksAndTimes() {
        final MermaidGantt gantt = MermaidGanttParser.parse(CHART);

        assertThat(gantt.getTitle()).isEqualTo("Activity of alice (times in Europe/London)");
        assertThat(gantt.getDateFormat()).isEqualTo("YYYY-MM-DD HH:mm:ss");
        assertThat(gantt.getAxisFormat()).isEqualTo("%H:%M");
        assertThat(gantt.getSections()).extracting(Section::name).containsExactly("host-1", "host-2");
        assertThat(gantt.taskCount()).isEqualTo(4);

        final Task chrome = gantt.getSections().get(0).tasks().get(0);
        assertThat(chrome.name()).isEqualTo("Chrome");
        assertThat(chrome.start()).isEqualTo(millis("2026-11-23T09:00:00"));
        assertThat(chrome.end()).isEqualTo(millis("2026-11-23T09:30:00"));
        assertThat(chrome.isMarker()).isFalse();

        final Task word = gantt.getSections().get(0).tasks().get(1);
        assertThat(word.tags()).containsExactly("done");
        assertThat(word.id()).isEqualTo("w1");
        assertThat(word.end() - word.start()).isEqualTo(45 * 60_000L);

        final Task copy = gantt.getSections().get(0).tasks().get(2);
        assertThat(copy.isMarker()).isTrue();
        assertThat(copy.start()).isEqualTo(copy.end());

        final Task outlook = gantt.getSections().get(1).tasks().getFirst();
        assertThat(outlook.tags()).containsExactly("crit", "active");
        assertThat(outlook.end() - outlook.start()).isEqualTo(30_000L);
    }

    @Test
    void aChartWithNoDateFormatHasMermaidsDefaultAndTasksBeforeAnySectionAreInAnUnnamedOne() {
        final MermaidGantt gantt = MermaidGanttParser.parse("gantt\n  Build :2026-11-01, 3d\n");

        assertThat(gantt.getDateFormat()).isEqualTo(MermaidGanttParser.DEFAULT_DATE_FORMAT);
        assertThat(gantt.getTitle()).isNull();
        assertThat(gantt.getAxisFormat()).isNull();
        assertThat(gantt.getSections()).singleElement().extracting(Section::name).isEqualTo("");
        final Task build = gantt.getSections().getFirst().tasks().getFirst();
        assertThat(build.start()).isEqualTo(millis("2026-11-01T00:00:00"));
        assertThat(build.end()).isEqualTo(millis("2026-11-04T00:00:00"));
    }

    @Test
    void blankLinesWindowsLineEndingsAndKeywordCaseAreRead() {
        final MermaidGantt gantt = MermaidGanttParser.parse("\r\n\r\nGANTT\r\n  DATEFORMAT YYYY-MM-DDTHH:mm\r\n"
                                                            + "  Section A\r\n\r\n  t :2026-02-28T23:59, 1m\r\n");

        assertThat(gantt.getSections()).singleElement().extracting(Section::name).isEqualTo("A");
        assertThat(gantt.getSections().getFirst().tasks().getFirst().end())
                .isEqualTo(millis("2026-03-01T00:00:00"));
    }

    @Test
    void everyDurationUnitIsRead() {
        assertThat(MermaidGanttParser.duration("250ms")).isEqualTo(250);
        assertThat(MermaidGanttParser.duration("1.5s")).isEqualTo(1_500);
        assertThat(MermaidGanttParser.duration("2m")).isEqualTo(120_000);
        assertThat(MermaidGanttParser.duration("1h")).isEqualTo(3_600_000);
        assertThat(MermaidGanttParser.duration("0d")).isZero();
        assertThat(MermaidGanttParser.duration("1w")).isEqualTo(7 * MermaidGantt.MILLIS_PER_DAY);
        // Months and years have no one length; and the rest aren't durations at all.
        assertThat(MermaidGanttParser.duration("1M")).isEqualTo(-1);
        assertThat(MermaidGanttParser.duration("1y")).isEqualTo(-1);
        assertThat(MermaidGanttParser.duration("h")).isEqualTo(-1);
        assertThat(MermaidGanttParser.duration("1.2.3s")).isEqualTo(-1);
        assertThat(MermaidGanttParser.duration("2026-11-23")).isEqualTo(-1);
    }

    @Test
    void wallClockTimesMatchTheCalendarAcrossLeapYearsAndBefore1970() {
        for (final String time : new String[]{"1969-12-31T23:59:59", "2000-02-29T12:00:00", "2100-03-01T00:00:00",
                "2026-11-23T13:45:07"}) {
            final LocalDateTime local = LocalDateTime.parse(time);
            final long wallClock = MermaidGantt.wallClock(local.getYear(), local.getMonthValue(),
                    local.getDayOfMonth(), local.getHour(), local.getMinute(), local.getSecond(), 0);

            assertThat(wallClock).isEqualTo(millis(time));
            final int[] fields = MermaidGantt.fieldsOf(wallClock);
            assertThat(fields).startsWith(local.getYear(), local.getMonthValue(), local.getDayOfMonth(),
                    local.getHour(), local.getMinute(), local.getSecond(), 0);
            assertThat(fields[7]).isEqualTo(local.getDayOfWeek().getValue() % 7);
        }
    }

    /// Mermaid reads these as other statements, or as syntax, so a block holding one is outside the subset.
    @ParameterizedTest
    @ValueSource(strings = {
            "%%{init: {'theme': 'dark'}}%%",
            "%% a comment",
            "click t1 call alert()",
            "click t1 href \"javascript:alert(1)\"",
            "call t1",
            "excludes weekends",
            "todayMarker off",
            "tickInterval 1day",
            "inclusiveEndDates",
            "topAxis",
            "weekday monday",
            "accTitle: a title",
            "accDescr: a description",
            "Chrome #35; :2026-11-23, 1d",
            "Chrome :2026-11-23, 1d; Word :2026-11-24, 1d",
            "\"Chrome\" :2026-11-23, 1d",
            "<b>Chrome</b> :2026-11-23, 1d",
            "Chrome :after t1, 1d",
            "Chrome :2026-11-23, until t2",
            "Chrome :2026-11-23",
            "Chrome",
            " :2026-11-23, 1d",
            "Chrome :2026-11-23, 1M",
            "Chrome :2026-13-01, 1d",
            "Chrome :2026-02-30, 1d",
            "Chrome :2026-11-23, 2026-11-22",
            "Chrome :t 1, 2026-11-23, 1d",
            "Chrome :done, done, 2026-11-23, 1d",
            "2026-11-23 Chrome :2026-11-23, 1d",
            "Clicks :2026-11-23, 1d",
            "title",
            "axisFormat %Q",
            "axisFormat %%",
            "dateFormat X",
            "dateFormat HH:mm"})
    void aStatementOutsideTheSubsetIsRefused(final String statement) {
        assertThatThrownBy(() -> MermaidGanttParser.parse("gantt\n    " + statement + "\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Line 2: ");
        assertThat(MermaidGanttParser.isValid("gantt\n" + statement)).isFalse();
    }

    @Test
    void aBlockThatIsntAGanttChartIsRefused() {
        assertThat(MermaidGanttParser.isValid(null)).isFalse();
        assertThat(MermaidGanttParser.isValid("")).isFalse();
        assertThat(MermaidGanttParser.isValid("pie\n  \"a\" : 1")).isFalse();
        assertThat(MermaidGanttParser.isValid("---\ntitle: x\n---\ngantt")).isFalse();
        assertThat(MermaidGanttParser.isValid("graph TD\n  A --> B")).isFalse();
        assertThatThrownBy(() -> MermaidGanttParser.parse("flowchart LR"))
                .hasMessage("Line 1: not a gantt chart: it must start with 'gantt'");
    }

    @Test
    void aDateFormatMustComeBeforeTheTasksAndEachHeaderOnlyOnce() {
        assertThatThrownBy(() -> MermaidGanttParser.parse("gantt\nt :2026-11-23, 1d\ndateFormat YYYY-MM-DD"))
                .hasMessage("Line 3: the date format must come before the tasks");
        assertThatThrownBy(() -> MermaidGanttParser.parse("gantt\ntitle a\ntitle b"))
                .hasMessage("Line 3: a second title");
        assertThatThrownBy(() -> MermaidGanttParser.parse("gantt\ndateFormat YYYY-MM-DD\n"
                                                          + "dateFormat YYYY-MM-DD"))
                .hasMessage("Line 3: a second date format");
    }

    @Test
    void aTimeMustBeInTheChartsDateFormatExactly() {
        assertThat(MermaidGanttParser.isValid("gantt\ndateFormat YYYY-MM-DD HH:mm\nt :2026-11-23, 1d")).isFalse();
        assertThat(MermaidGanttParser.isValid("gantt\ndateFormat YYYY-MM-DD HH:mm\nt :2026-11-23 9:00, 1h"))
                .isFalse();
        assertThat(MermaidGanttParser.isValid("gantt\ndateFormat YYYY-MM-DD HH:mm\nt :2026-11-23 24:00, 1h"))
                .isFalse();
        assertThat(MermaidGanttParser.isValid("gantt\ndateFormat YYYY-MM-DD HH:mm\nt :2026-11-23 09:00, 1h"))
                .isTrue();
    }

    @Test
    void hugeBlocksAreRefusedBeforeTheyCanSlowTheChat() {
        final StringBuilder tasks = new StringBuilder("gantt\n");
        for (int i = 0; i <= MermaidGanttParser.MAX_TASKS; i++) {
            tasks.append("t :2026-11-23, 1d\n");
        }
        assertThatThrownBy(() -> MermaidGanttParser.parse(tasks.toString()))
                .hasMessageContaining("more than " + MermaidGanttParser.MAX_TASKS + " tasks");
        assertThatThrownBy(() -> MermaidGanttParser.parse("gantt\n" + "x".repeat(MermaidGanttParser.MAX_CHARS)))
                .hasMessage("Longer than " + MermaidGanttParser.MAX_CHARS + " characters");
    }

    @Test
    void namesMermaidWouldReadAsStatementsOrDatesAreTold() {
        assertThat(MermaidGanttParser.startsLikeAStatement("click here")).isTrue();
        assertThat(MermaidGanttParser.startsLikeAStatement("Section 3")).isTrue();
        assertThat(MermaidGanttParser.startsLikeAStatement("GANTT viewer")).isTrue();
        assertThat(MermaidGanttParser.startsLikeAStatement("2026-11-23 report")).isTrue();
        assertThat(MermaidGanttParser.startsLikeAStatement("Chrome")).isFalse();
        assertThat(MermaidGanttParser.startsLikeAStatement("Report 2026-11-23")).isFalse();
        assertThat(MermaidGanttParser.startsLikeAStatement("\u00b7 click here")).isFalse();
    }

    private static long millis(final String localTime) {
        return LocalDateTime.parse(localTime).toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
