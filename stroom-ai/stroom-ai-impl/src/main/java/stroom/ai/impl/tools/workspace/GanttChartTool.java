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

import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.time.UserTimeZones;
import stroom.ai.impl.tools.workspace.GanttCharts.Chart;
import stroom.ai.impl.tools.workspace.GanttCharts.Options;
import stroom.ai.impl.tools.workspace.GanttCharts.Row;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.TableJoin;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/// `gantt_chart`: draws a workspace table of times as a timeline in the chat, as a ```` ```mermaid ```` `gantt`
/// block ([GanttCharts]) for the agent to place in its reply, which the chat draws with its own renderer. The
/// chat draws only this kind of chart: for any other the agent offers a dashboard.
public class GanttChartTool extends AbstractWorkspaceTool {

    public static final String NAME = "gantt_chart";
    static final String START_ARG = "start";
    static final String END_ARG = "end";
    static final String LABEL_ARG = "label";
    static final String LANE_ARG = "lane";
    static final String START_EVENT_ARG = "startEvent";
    static final String END_EVENT_ARG = "endEvent";
    static final String TITLE_ARG = "title";
    static final String TOP_ARG = "top";
    static final String MIN_SECONDS_ARG = "minSeconds";
    static final String MERGE_SECONDS_ARG = "mergeSeconds";
    /// Bars shorter than this are left out unless the call says otherwise.
    static final int DEFAULT_MIN_SECONDS = 60;
    /// Bars of a label this close are merged unless the call says otherwise.
    static final int DEFAULT_MERGE_SECONDS = 300;
    // A date as a lane holds it, e.g. a table's day column.
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /// What the result says the chat draws, and what it doesn't.
    public static final String CHAT_DRAWS = "The chat draws only timelines (Mermaid gantt). For any other kind of chart "
                                     + "(a heat map, network, pie, bars or lines, a map) offer only a dashboard "
                                     + "(propose_dashboard, or propose_visualisation for the attached one), never "
                                     + "a chat chart that approximates it.";

    /// @param workspace The chats' workspaces, which the tool reads and writes.
    @Inject
    public GanttChartTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public boolean isChatOnly() {
        // Its block is drawn by the chat; a client over /mcp is offered the dashboard tools for charts.
        return true;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Draws a table of times as a timeline in the chat: gives a ```mermaid gantt "
                                 + "block to put in your reply as it is, which the chat draws (a bar from each "
                                 + "row's start to its end, a marker where it has no end, a section per lane or "
                                 + "per day, a colour per label). Large tables are folded to stay readable (bars "
                                 + "of a label merged, short ones left out, the top labels kept and the rest as "
                                 + "Other), the result saying how. Never write chart code yourself.")
                        .chatOnly(" Where a timeline is asked for and the user hasn't said where, ask them "
                                  + "(ask_user): in the chat, or a dashboard (propose_visualisation, kind "
                                  + "timeline, from the same columns). " + CHAT_DRAWS)
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The table")
                                .chatOnly(", e.g. /outputs/sessions.csv")
                                .general(".")
                                .build())
                        .addStringProperty(START_ARG, "The column of each row's start time (ISO 8601, UTC if no "
                                                      + "zone, or epoch ms).")
                        .addStringProperty(END_ARG, "The column of each row's end time; a row without one is a "
                                                    + "marker.")
                        .addStringProperty(LABEL_ARG, "The column naming what each row is (e.g. the "
                                                      + "application).")
                        .addStringProperty(LANE_ARG, "The column of each row's lane (e.g. the host); default a "
                                                     + "lane per day.")
                        .addStringProperty(START_EVENT_ARG, "The column naming the event each row starts at "
                                                            + "(e.g. Logon-User), shown on its start marker.")
                        .addStringProperty(END_EVENT_ARG, "The column naming the event each row ends at.")
                        .addStringProperty(TITLE_ARG, "What the chart shows.")
                        .addIntegerProperty(TOP_ARG, "The most labels kept, 1 to " + GanttCharts.MAX_TOP
                                                     + " (default); the rest are Other.")
                        .addIntegerProperty(MIN_SECONDS_ARG, "Bars shorter than this are left out (default "
                                                             + DEFAULT_MIN_SECONDS + ").")
                        .addIntegerProperty(MERGE_SECONDS_ARG, "Bars of a label in a lane this close are merged "
                                                               + "(default " + DEFAULT_MERGE_SECONDS + ").")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .required(PATH_ARG, START_ARG, LABEL_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final ToolTimeZone zone = timeZone(request, context);
        final Spec spec = new Spec(required(request, START_ARG), stringArg(request, END_ARG),
                required(request, LABEL_ARG), stringArg(request, LANE_ARG), stringArg(request, START_EVENT_ARG),
                stringArg(request, END_EVENT_ARG), stringArg(request, TITLE_ARG), intArg(request, TOP_ARG),
                intArg(request, MIN_SECONDS_ARG), intArg(request, MERGE_SECONDS_ARG));
        return ToolResult.text(draw(sessionWorkspace(context), path, spec, zone));
    }

    /// Draws a workspace table as gantt_chart does: its result, the block to put in the reply, and what was
    /// folded.
    ///
    /// @param workspace The chat's workspace.
    /// @param path      The table.
    /// @param spec      Its columns and how to draw them.
    /// @param zone      The time zone to draw its times in.
    /// @return The result, as gantt_chart gives it.
    /// @throws WorkspaceException if the table, or a column, isn't there, or it has no row to draw.
    public static String draw(final SessionWorkspace workspace,
                              final WorkspacePath path,
                              final Spec spec,
                              final ToolTimeZone zone) {
        TableTool.refuseUnlessATable(path);
        final Result table = TableEngine.run(workspace.localFile(path), Format.forPath(path),
                new Query(List.of(), List.of(), List.of(), List.of(), false, false, List.of(), null));
        final List<String> columns = table.columns();
        final int start = TableEngine.index(spec.start(), columns);
        final int label = TableEngine.index(spec.label(), columns);
        final int end = column(spec.end(), columns);
        final int givenLane = column(spec.lane(), columns);
        final int startEvent = column(spec.startEvent(), columns);
        final int endEvent = column(spec.endEvent(), columns);

        if (table.rows().isEmpty()) {
            throw new WorkspaceException(path + " has no rows, so there is nothing to draw.");
        }
        final List<String> folded = new ArrayList<>();
        // A lane of dates (a table's days, cut in whatever zone it was written in) is drawn as the days of the
        // chart's zone, so a section holds the bars its times say are of that day.
        final int lane;
        if (givenLane >= 0 && isDates(table.rows(), givenLane)) {
            lane = -1;
            folded.add("The lane " + columns.get(givenLane) + " holds dates, so its sections are the days in "
                       + zone.id() + ", as the bars' times are.");
        } else {
            lane = givenLane;
        }
        final List<Row> rows = rows(table.rows(), start, end, label, lane, startEvent, endEvent, columns, folded);
        if (rows.isEmpty()) {
            throw new WorkspaceException("No row of " + path + " has a start time that could be read in "
                                         + columns.get(start) + ", so there is nothing to draw.");
        }
        final Options options = new Options(spec.title(), zone, lane >= 0,
                orDefault(spec.top(), GanttCharts.MAX_TOP),
                Math.max(0, orDefault(spec.minSeconds(), DEFAULT_MIN_SECONDS)),
                Math.max(0, orDefault(spec.mergeSeconds(), DEFAULT_MERGE_SECONDS)));
        final Chart chart = GanttCharts.write(rows, options);
        folded.addAll(chart.folded());
        return result(chart, folded, path, table.rows().size(), zone);
    }

    // The rows with a start time, each row without one, or ending before it starts, counted as left out.
    private static List<Row> rows(final List<List<String>> cells,
                                  final int start,
                                  final int end,
                                  final int label,
                                  final int lane,
                                  final int startEvent,
                                  final int endEvent,
                                  final List<String> columns,
                                  final List<String> folded) {
        final List<Row> rows = new ArrayList<>();
        int noStart = 0;
        int backwards = 0;
        for (final List<String> row : cells) {
            final Long from = TableJoin.millis(cell(row, start));
            if (from == null) {
                noStart++;
                continue;
            }
            final Long to = end < 0
                    ? null
                    : TableJoin.millis(cell(row, end));
            if (to != null && to < from) {
                backwards++;
                continue;
            }
            rows.add(new Row(from, to, cell(row, label), lane < 0
                    ? null
                    : cell(row, lane), startEvent < 0
                    ? null
                    : cell(row, startEvent), endEvent < 0
                    ? null
                    : cell(row, endEvent)));
        }
        if (noStart > 0) {
            folded.add("Left out " + noStart + " row(s) with no time in " + columns.get(start)
                       + " that could be read.");
        }
        if (backwards > 0) {
            folded.add("Left out " + backwards + " row(s) ending before they start.");
        }
        return rows;
    }

    private static String result(final Chart chart,
                                 final List<String> folded,
                                 final WorkspacePath path,
                                 final int rowCount,
                                 final ToolTimeZone zone) {
        final StringBuilder sb = new StringBuilder("Put this block in your reply exactly as it is: the chat draws "
                                                   + "it as a chart (other apps that draw Mermaid do too).\n\n");
        sb.append("```mermaid\n").append(chart.mermaid()).append("```\n\n");
        sb.append(chart.bars()).append(" bar(s) and ").append(chart.markers()).append(" marker(s) in ")
                .append(chart.sections()).append(" section(s), from ").append(rowCount).append(" row(s) of ")
                .append(path).append(", the whole table.");
        if (folded.isEmpty()) {
            sb.append(" Nothing was folded.");
        } else {
            sb.append(" Folded to stay readable (say so with the chart):");
            folded.forEach(line -> sb.append("\n- ").append(line));
        }
        sb.append("\n\n").append(zone.stated()).append("\n\n").append(CHAT_DRAWS);
        return sb.toString();
    }

    // The index of a column, or -1 if none is named.
    private static int column(final String name, final List<String> columns) {
        return name == null
                ? -1
                : TableEngine.index(name, columns);
    }

    // True if every row's value in a column is a date (yyyy-MM-dd), and some row has one.
    private static boolean isDates(final List<List<String>> cells, final int column) {
        boolean any = false;
        for (final List<String> row : cells) {
            final String value = cell(row, column);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!DATE.matcher(value.strip()).matches()) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private String required(final ToolCall request, final String name) {
        final String value = stringArg(request, name);
        if (value == null) {
            throw new WorkspaceException("Give " + name + ": the column of the table to use.");
        }
        return value;
    }

    private static String cell(final List<String> row, final int index) {
        return index < row.size()
                ? row.get(index)
                : null;
    }

    private static int orDefault(final Integer value, final int other) {
        return value == null
                ? other
                : value;
    }


    // --------------------------------------------------------------------------------


    /// The columns of a table to draw, and how to draw them.
    ///
    /// @param start        The column of each row's start time.
    /// @param end          The column of each row's end time, or null for markers.
    /// @param label        The column naming what each row is.
    /// @param lane         The column of each row's lane, or null for a lane per day.
    /// @param startEvent   The column naming the event each row starts at, or null.
    /// @param endEvent     The column naming the event each row ends at, or null.
    /// @param title        What the chart shows, or null.
    /// @param top          The most labels kept, or null for the default.
    /// @param minSeconds   Bars shorter than this are left out, or null for the default.
    /// @param mergeSeconds Bars of a label in a lane this close are merged, or null for the default.
    public record Spec(String start,
                       String end,
                       String label,
                       String lane,
                       String startEvent,
                       String endEvent,
                       String title,
                       Integer top,
                       Integer minSeconds,
                       Integer mergeSeconds) {

    }
}
