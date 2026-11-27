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

import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.time.UserTimeZones;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.MermaidGanttParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// gantt_chart on a workspace table: the block to place in the reply, what was folded, the zone its times are in,
/// and that the chat draws only timelines.
class TestGanttChartTool {

    private static final int CHAT = 1;
    private static final String TABLE = "/outputs/sessions.csv";

    @TempDir
    Path tempDir;

    private FakeTabWorkspace files;
    private GanttChartTool tool;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse(TABLE), """
                Start,End,Application,Host
                2026-11-23T09:00:00.000Z,2026-11-23T09:30:00.000Z,Chrome,host-1
                2026-11-23T09:31:00.000Z,2026-11-23T09:40:00.000Z,Chrome,host-1
                2026-11-23T09:45:00.000Z,,USB copy,host-1
                2026-11-23 10:00:00,1795429260000,Word,host-2
                not a time,2026-11-23T11:00:00.000Z,Excel,host-2
                2026-11-23T12:00:00.000Z,2026-11-23T11:00:00.000Z,Outlook,host-2
                """, null);
        tool = new GanttChartTool(files.workspace());
    }

    @Test
    void aTableIsDrawnAsABlockForTheReplySayingWhatWasFoldedAndTheZone() {
        final String result = run("{\"path\":\"" + TABLE + "\",\"start\":\"Start\",\"end\":\"End\","
                                  + "\"label\":\"Application\",\"lane\":\"host\",\"title\":\"alice's day\","
                                  + "\"timeZone\":\"Europe/London\"}");

        assertThat(result)
                .startsWith("Put this block in your reply exactly as it is: the chat draws it as a chart")
                .contains("""
                        ```mermaid
                        gantt
                            title alices day (times in Europe/London)
                            dateFormat YYYY-MM-DD HH:mm:ss
                            axisFormat %H:%M
                            section host-1
                            Chrome :2026-11-23 09:00:00, 2026-11-23 09:40:00
                            USB copy :milestone, 2026-11-23 09:45:00, 0d
                            section host-2
                            Word :2026-11-23 10:00:00, 2026-11-23 10:21:00
                        ```
                        """)
                .contains("2 bar(s) and 1 marker(s) in 2 section(s), from 6 row(s) of " + TABLE
                          + ", the whole table. Folded to stay readable (say so with the chart):")
                .contains("- Left out 1 row(s) with no time in Start that could be read.")
                .contains("- Left out 1 row(s) ending before they start.")
                .contains("- Merged 1 bar(s) into the bar before of the same label in the same lane")
                .contains("Times are in Europe/London (")
                .endsWith(GanttChartTool.CHAT_DRAWS);
        final String block = result.substring(result.indexOf("```mermaid\n") + 11, result.indexOf("```\n\n"));
        assertThat(MermaidGanttParser.parse(block).taskCount()).isEqualTo(3);
    }

    @Test
    void withoutAZoneTheUsersDeviceZoneIsUsedOrUtcSayingSo() {
        final String result = run("{\"path\":\"" + TABLE + "\",\"start\":\"Start\",\"label\":\"Application\"}");

        assertThat(result)
                .contains("title Timeline (times in UTC)")
                .contains("section 2026-11-23 Mon")
                // No end column: every row is a marker.
                .contains("Chrome :milestone, 2026-11-23 09:00:00, 0d")
                .contains(UserTimeZones.DEVICE_NOT_KNOWN);
    }

    @Test
    void aColumnThatIsntThereOrATableWithNoTimesIsRefusedSayingWhatThereIs() {
        assertThat(run("{\"path\":\"" + TABLE + "\",\"start\":\"Begin\",\"label\":\"Application\"}"))
                .isEqualTo("Error: There is no column 'Begin'. The columns are: Start, End, Application, Host.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"start\":\"Host\",\"label\":\"Application\"}"))
                .isEqualTo("Error: No row of " + TABLE + " has a start time that could be read in Host, so there "
                           + "is nothing to draw.");
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse("/outputs/empty.csv"), "Start,Application\n", null);
        assertThat(run("{\"path\":\"/outputs/empty.csv\",\"start\":\"Start\",\"label\":\"Application\"}"))
                .isEqualTo("Error: /outputs/empty.csv has no rows, so there is nothing to draw.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"label\":\"Application\"}"))
                .isEqualTo("Error: Give start: the column of the table to use.");
        assertThat(run("{\"path\":\"/outputs/events.json\",\"start\":\"a\",\"label\":\"b\"}"))
                .startsWith("Error: /outputs/events.json is JSON, not a table");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"start\":\"Start\",\"label\":\"Application\","
                       + "\"timeZone\":\"Mars/Olympus\"}"))
                .startsWith("Error: unknown time zone 'Mars/Olympus'");
    }

    @Test
    void theColumnsOfEachRowsStartAndEndEventsAreNamedAfterItsBar() {
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse("/outputs/spans.csv"), """
                start,end,label,lane,start_event,end_event
                2026-11-23T09:00:00Z,2026-11-23T17:00:00Z,logged on,host-1,Logon-User,Logoff-User
                2026-11-23T10:00:00Z,2026-11-23T10:30:00Z,Word,host-1,,
                """, null);

        final String result = run("{\"path\":\"/outputs/spans.csv\",\"start\":\"start\",\"end\":\"end\","
                                  + "\"label\":\"label\",\"lane\":\"lane\",\"startEvent\":\"start_event\","
                                  + "\"endEvent\":\"end_event\",\"timeZone\":\"UTC\"}");

        assertThat(result).contains("""
                    logged on :2026-11-23 09:00:00, 2026-11-23 17:00:00
                    %% events Logon-User | Logoff-User
                    Word :2026-11-23 10:00:00, 2026-11-23 10:30:00
                ```
                """);
        assertThat(run("{\"path\":\"/outputs/spans.csv\",\"start\":\"start\",\"label\":\"label\","
                       + "\"startEvent\":\"first\"}"))
                .startsWith("Error: There is no column 'first'.");
    }

    @Test
    void aLaneOfDatesIsDrawnAsTheDaysOfTheChartsZoneAsItsTimesAre() {
        // Days cut at UTC midnight, drawn in Sydney (11 hours ahead): the 11:00 cut is merged away.
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse("/outputs/activities.csv"), """
                activity_label,day,start,end
                Forecast,2026-10-26,2026-10-26T21:40:00Z,2026-10-27T00:00:00Z
                Forecast,2026-10-27,2026-10-27T00:00:00Z,2026-10-27T05:20:00Z
                Mail,2026-10-27,2026-10-27T21:50:00Z,2026-10-27T23:00:00Z
                """, null);

        final String result = run("{\"path\":\"/outputs/activities.csv\",\"start\":\"start\",\"end\":\"end\","
                                  + "\"label\":\"activity_label\",\"lane\":\"day\","
                                  + "\"timeZone\":\"Australia/Sydney\"}");

        assertThat(result)
                .contains("""
                            section 2026-10-27 Tue
                            Forecast :2026-10-27 08:40:00, 2026-10-27 16:20:00
                            section 2026-10-28 Wed
                            Mail :2026-10-28 08:50:00, 2026-10-28 10:00:00
                        """)
                .doesNotContain("section 2026-10-26")
                .contains("- The lane day holds dates, so its sections are the days in Australia/Sydney, as the "
                          + "bars' times are.");
        // A lane of anything else is kept as it is.
        assertThat(run("{\"path\":\"/outputs/activities.csv\",\"start\":\"start\",\"end\":\"end\","
                       + "\"label\":\"day\",\"lane\":\"activity_label\",\"timeZone\":\"Australia/Sydney\"}"))
                .contains("section Forecast")
                .doesNotContain("holds dates");
    }

    @Test
    void itIsOnlyOfferedInTheChatAndOnlyReads() {
        assertThat(tool.isChatOnly()).isTrue();
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.execute(ToolCalls.call(GanttChartTool.NAME, "{}"), ToolCalls.outsideChat()).text())
                .contains("only");
        // The rule for choosing between the chat and a dashboard is in what the chat's model is told.
        assertThat(tool.definition().description())
                .contains("ask them (ask_user): in the chat, or a dashboard")
                .contains("offer only a dashboard (propose_dashboard");
    }

    private String run(final String arguments) {
        return tool.execute(ToolCalls.call(GanttChartTool.NAME, arguments), ToolCalls.inChat(CHAT,
                files.workspace())).text();
    }
}
