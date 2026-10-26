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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.DashboardComponents;
import stroom.ai.impl.DashboardComponents.Component;
import stroom.ai.impl.DashboardComponents.EmbeddedQuery;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.vis.ChartCheck;
import stroom.ai.impl.vis.ChartCheck.Sample;
import stroom.ai.impl.vis.ChartCheck.TableColumn;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibrary;
import stroom.ai.impl.vis.ChartRole;
import stroom.ai.impl.vis.D3Visualisation;
import stroom.ai.impl.vis.StyleSetting;
import stroom.ai.impl.vis.VisualisationActions;
import stroom.ai.impl.vis.VisualisationPlan;
import stroom.ai.impl.vis.VisualisationPlan.Replace;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AskStroomAiContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.query.shared.QueryDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// `propose_visualisation` - plans a chart of a table on the dashboard (or the StroomQL query) the user attached,
/// checks the table's data can make it, and offers a way to put it there:
///
/// - With ECharts, a "Create visualisation" button: it opens Stroom's New Visualisation dialog with a suggested
///   name in the dashboard's folder, and once the user creates it, the Visualisation is given the chart (built by
///   Stroom from a fixed template, never code from the model) and added to the dashboard (or the query's `show`
///   clause), unsaved.
/// - With the visualisations content pack's D3 Visualisations, nothing is generated: the agent is given the
///   add_vis change (or `show` clause) that wires the existing Visualisation to the table, to propose.
///
/// When the data can't make the chart (e.g. no numeric column for a bar's value), nothing is offered, and the
/// result says why and what would fix it, for the agent to tell the user.
///
/// When the table already has a chart Stroom AI generated, nothing is offered until the agent says, having asked
/// the user, whether to replace it (`replace`: its Visualisation is rewritten for the new chart and renamed, saved
/// at once, and stays in its pane) or to add the new chart beside it (`addNew`).
class ProposeVisualisationTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_visualisation";
    static final String LIBRARY_ARG = "library";
    static final String KIND_ARG = "kind";
    static final String COLUMNS_ARG = "columns";
    static final String TABLE_ARG = "table";
    static final String STYLE_ARG = "style";
    static final String OPTIONS_ARG = "options";
    static final String NAME_ARG = "name";
    static final String COMPONENT_NAME_ARG = "componentName";
    static final String PLACEMENT_ARG = "placement";
    static final String SUMMARY_ARG = "summary";
    static final String REPLACE_ARG = "replace";
    static final String ADD_NEW_ARG = "addNew";
    static final String ECHARTS = "echarts";
    static final String D3 = "d3";
    /// Wires one of the visualisations content pack's own D3 Visualisations to the table; nothing is generated.
    static final String CONTENT_PACK = "content-pack";
    static final int MAX_SAMPLE_ROWS = 5_000;
    static final int MAX_NAME_CHARS = 100;
    static final int MAX_SUMMARY_CHARS = 500;
    static final String NO_CHART = "no chart can be proposed";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    static final String CHARTS_ARG = "charts";
    static final String LAYOUT_ARG = "layout";
    /// Several charts side by side.
    static final String ROW = "row";
    /// Several charts one below another.
    static final String COLUMN = "column";
    /// Several charts in rows of up to [#GRID_COLUMNS].
    static final String GRID = "grid";
    /// Several charts stacked as tabs in one pane.
    static final String TABS = "tabs";
    static final List<String> LAYOUTS = List.of(ROW, COLUMN, GRID, TABS);
    static final int GRID_COLUMNS = 3;
    /// The most charts one proposal may create.
    static final int MAX_CHARTS = 12;
    // A column whose expression reads a data source field as it is, e.g. ${user.name}.
    private static final Pattern PLAIN_FIELD = Pattern.compile("\\$\\{([^}]+)}");

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;
    private final CurrentResultsTool currentResultsTool;
    private final ChartLibraries chartLibraries;
    private final AiAuditLog auditLog;

    @Inject
    ProposeVisualisationTool(final ChatWorkspace workspace,
                             final TabSnapshots tabSnapshots,
                             final DocumentAccess documentAccess,
                             final CurrentResultsTool currentResultsTool,
                             final ChartLibraries chartLibraries,
                             final AiAuditLog auditLog) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
        this.currentResultsTool = currentResultsTool;
        this.chartLibraries = chartLibraries;
        this.auditLog = auditLog;
    }

    @Override
    public boolean isChatOnly() {
        // It proposes a chart for the tab the user attached to the chat.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema.Builder columns = ObjectSchema.builder()
                .description(ToolDescription.builder()
                        .general("The table column for each of the chart kind's inputs, by input.")
                        .chatOnly(" list_visualisations gives each kind's inputs.")
                        .general(" Give the column names exactly as the table has them.")
                        .build());
        allRoleIds().forEach(roleId -> columns.addStringProperty(roleId, "The column for " + roleId + "."));
        final ObjectSchema.Builder style = ObjectSchema.builder()
                .description(ToolDescription.builder()
                        .general("Optional style settings, by setting.")
                        .chatOnly(" list_visualisations gives each kind's settings and their values.")
                        .general(" Leave out settings to keep their defaults.")
                        .build());
        allStyleIds().forEach(id -> style.addStringProperty(id, "The value for " + id + ", as text."));

        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .chatOnly("Plans a chart of a table on the dashboard, or of the StroomQL query, the user "
                                  + "attached, checks the table's rows can make it, and offers it. With library "
                                  + "echarts or d3 it offers a 'Create visualisation' button: the user names it and "
                                  + "picks its folder (the dashboard's, by default) in Stroom's own dialog, and it "
                                  + "is then added to the dashboard below the table (or to the query's show "
                                  + "clause), unsaved, updating as the table does. With library content-pack it "
                                  + "gives the change that wires the content pack's existing D3 Visualisation to "
                                  + "the table, for you to propose. Call list_visualisations first to see which "
                                  + "libraries are installed and each kind's inputs, and ask the user which "
                                  + "library to use (ask_user) when more than one is. If the data can't make the "
                                  + "chart, nothing is offered: tell the user why and suggest the fix the result "
                                  + "gives. When the table already has a chart Stroom AI generated (e.g. the user "
                                  + "asks for it as another kind of chart), ask the user (ask_user) whether to "
                                  + "replace it or add a new one, unless they have said, and give replace or "
                                  + "addNew. You never write the chart's code or settings JSON: Stroom builds them.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addEnumProperty(LIBRARY_ARG, List.of(ECHARTS, D3, CONTENT_PACK), "The charting library "
                                + "to draw it with: echarts or d3 to generate the chart, or content-pack to wire "
                                + "one of the content pack's existing D3 Visualisations to the table.")
                        .addEnumProperty(KIND_ARG, Arrays.stream(ChartKind.values()).map(ChartKind::id).toList(),
                                "The chart kind: " + Arrays.stream(ChartKind.values())
                                        .map(kind -> kind.id() + " (" + kind.summary() + ")")
                                        .collect(Collectors.joining("; ")) + ".")
                        .addProperty(COLUMNS_ARG, columns.build())
                        .addStringProperty(TABLE_ARG, "Optional. The dashboard table (or embedded query) to "
                                                      + "chart, by the name the user sees, when the dashboard has "
                                                      + "several.")
                        .addProperty(STYLE_ARG, style.build())
                        .addStringProperty(OPTIONS_ARG, "Optional, echarts or d3. Option overrides as a JSON object, "
                                                        + "for what the style settings can't do, e.g. "
                                                        + "{\"color\": [\"#5470c6\", \"#91cc75\"]}: ECharts "
                                                        + "option keys, or for d3 only color. Only plain JSON: no "
                                                        + "functions, HTML or URLs.")
                        .addStringProperty(NAME_ARG, "Echarts or d3. The name to suggest for the new "
                                                     + "Visualisation, saying what it shows, e.g. 'Chat user "
                                                     + "connections'. The user confirms or changes it.")
                        .addStringProperty(COMPONENT_NAME_ARG, "Optional. The name of the chart's pane on the "
                                                               + "dashboard; the Visualisation's name by default.")
                        .addEnumProperty(PLACEMENT_ARG, List.of(VisualisationPlan.BELOW, VisualisationPlan.TAB),
                                "Optional. Where the chart goes on the dashboard: in a new pane below the table "
                                + "(the default), or as a tab beside it.")
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what the chart shows, shown with the "
                                                        + "button.")
                        .addStringProperty(REPLACE_ARG, "Optional, echarts or d3 on a dashboard. The name of the "
                                                        + "Visualisation pane whose chart (one Stroom AI generated, "
                                                        + "of the same table) the new one replaces: its "
                                                        + "Visualisation is rewritten for the new chart and renamed "
                                                        + "to name, saved at once, and it stays in its pane. Give "
                                                        + "it, or addNew, once the user has chosen.")
                        .addBooleanProperty(ADD_NEW_ARG, "Optional. True to add the chart as a new one when the "
                                                         + "table already has a chart Stroom AI generated, once "
                                                         + "the user has chosen that over replacing it.")
                        .addProperty(CHARTS_ARG, ArraySchema.builder()
                                .items(ObjectSchema.builder()
                                        .addEnumProperty(KIND_ARG,
                                                Arrays.stream(ChartKind.values()).map(ChartKind::id).toList(),
                                                "The chart kind.")
                                        .addProperty(COLUMNS_ARG, ObjectSchema.builder()
                                                .description("The table column for each input, as the "
                                                             + "top-level columns takes them.")
                                                .build())
                                        .addStringProperty(TABLE_ARG, "Optional. The table it draws, when the "
                                                                      + "dashboard has several.")
                                        .addProperty(STYLE_ARG, ObjectSchema.builder()
                                                .description("Optional style settings, as the top-level "
                                                             + "style takes them.")
                                                .build())
                                        .addStringProperty(OPTIONS_ARG, "Optional. Option overrides as a JSON "
                                                                        + "object.")
                                        .addStringProperty(NAME_ARG, "The new Visualisation's name, saying what "
                                                                     + "it shows.")
                                        .addStringProperty(COMPONENT_NAME_ARG, "Optional. Its pane's name.")
                                        .required(KIND_ARG, COLUMNS_ARG, NAME_ARG)
                                        .build())
                                .description("Echarts or d3, on a dashboard. Several charts at once, of any kinds "
                                             + "and tables (e.g. a gauge per column, or a line per metric), up to "
                                             + MAX_CHARTS + ", offered with one 'Create visualisations' button: "
                                             + "the user picks one folder and each is created, named as given. "
                                             + "Give these instead of kind and columns (and give layout).")
                                .build())
                        .addEnumProperty(LAYOUT_ARG, LAYOUTS, "With charts: how they are arranged on the "
                                                              + "dashboard: row (side by side), column (one below "
                                                              + "another), grid (rows of up to " + GRID_COLUMNS
                                                              + ") or tabs (stacked as tabs in one pane). Ask the "
                                                              + "user unless they have said.")
                        .required(LIBRARY_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String library = NullSafe.string(stringArg(request, LIBRARY_ARG)).toLowerCase(Locale.ROOT);
        if (!ECHARTS.equals(library) && !D3.equals(library) && !CONTENT_PACK.equals(library)) {
            throw new WorkspaceException("Give library as " + ECHARTS + ", " + D3 + " or " + CONTENT_PACK + ".");
        }
        // The library a generated chart is drawn with; a content pack chart's settings are checked as ECharts'.
        final ChartLibrary chartLibrary = ChartLibrary.find(library).orElse(ChartLibrary.ECHARTS);
        if (argsMap(request).get(CHARTS_ARG) != null) {
            return runCharts(request, context, library);
        }
        final ChartKind kind = ChartKind.find(stringArg(request, KIND_ARG))
                .orElseThrow(() -> new WorkspaceException("Give kind as one of " + ChartKind.ids() + "."));
        final Map<String, String> mappings = stringMap(argsMap(request).get(COLUMNS_ARG), COLUMNS_ARG);
        final Map<String, String> style;
        final String options;
        try {
            style = VisualisationPlan.normaliseStyle(kind, stringMap(argsMap(request).get(STYLE_ARG), STYLE_ARG),
                    chartLibrary);
            options = VisualisationPlan.normaliseOptions(optionsText(argsMap(request).get(OPTIONS_ARG)),
                    chartLibrary);
        } catch (final IllegalArgumentException e) {
            throw new WorkspaceException(e.getMessage());
        }
        final String name = stringArg(request, NAME_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        final String placement = NullSafe.nonBlankStringElse(stringArg(request, PLACEMENT_ARG),
                VisualisationPlan.BELOW);
        if (!VisualisationPlan.BELOW.equals(placement) && !VisualisationPlan.TAB.equals(placement)) {
            throw new WorkspaceException("Give placement as " + VisualisationPlan.BELOW + " or "
                                         + VisualisationPlan.TAB + ".");
        }
        if (!CONTENT_PACK.equals(library) && (name == null || name.isBlank() || name.length() > MAX_NAME_CHARS)) {
            throw new WorkspaceException("Give the new Visualisation a name of up to " + MAX_NAME_CHARS
                                         + " characters, saying what it shows.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a chart can only be offered while answering a question in a chat.");
        }

        // The tab the user attached to the chat.
        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef target = NullSafe.get(tab, StoredTabSnapshot::docRef);
        final boolean query = target != null && QueryDoc.TYPE.equals(target.getType());
        if (target == null || (!query && !DashboardDoc.TYPE.equals(target.getType()))) {
            return ToolResult.text(TabSnapshots.notAttached(tab,
                    tabSnapshots.latestOfType(chatId, Set.of(DashboardDoc.TYPE, QueryDoc.TYPE)).orElse(null),
                    "dashboard or StroomQL query", NO_CHART));
        }
        if (!documentAccess.canView(target)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final AiTabSearch table = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()),
                stringArg(request, TABLE_ARG));
        if (table == null) {
            return ToolResult.text(NullSafe.isEmptyCollection(tab.searches())
                    ? "The tab shows no table of results to chart. Run its query first."
                    : "Say which table to chart: " + NullSafe.list(tab.searches()).stream()
                            .map(search -> "'" + tableName(search) + "'")
                            .collect(Collectors.joining(", ")) + ".");
        }
        final UnsavedPart part = tabSnapshots.part(chatId, tab, query
                ? AiTabSnapshotPart.QUERY
                : AiTabSnapshotPart.DASHBOARD).orElse(null);
        if (part == null || part.cut()) {
            return ToolResult.text("The tab's " + (query
                    ? "query"
                    : "settings") + " aren't in its snapshot in full (it may be too large, or still loading), so "
                                   + "a chart can't be proposed for it.");
        }

        final String tableName = tableName(table);
        // An embedded query on a dashboard is charted in its StroomQL's show clause, as a Query is.
        final EmbeddedQuery embedded;
        if (!query && ProposeDashboardChangeTool.isEmbeddedQuery(table)) {
            embedded = DashboardComponents.embeddedQuery(part.text(), table.getComponentId()).orElse(null);
            final String problem = embeddedQueryProblem(embedded, tableName, library);
            if (problem != null) {
                return ToolResult.text(problem);
            }
        } else {
            embedded = null;
        }

        // Check the table's data can make the chart.
        final AskStroomAiContext results = table.getLive() != null
                ? table.getLive()
                : table.getResults();
        final Map<String, String> formats = ResultsRequests.columnFormats(results);
        final Map<String, String> expressions = ResultsRequests.columnExpressions(results);
        final Map<String, Integer> groups = ResultsRequests.columnGroups(results);
        final List<TableColumn> columns = NullSafe.list(table.getColumns()).stream()
                .map(column -> new TableColumn(column, formats.get(column), expressions.get(column),
                        groups.get(column)))
                .toList();
        final SampleOutcome sample = sample(request, context, table);
        final ChartCheck.Result check = ChartCheck.check(kind, mappings, style, columns, sample.sample());
        if (!check.ok()) {
            return ToolResult.text("No chart was offered: the table '" + tableName + "' can't make this "
                                   + kind.displayName().toLowerCase(Locale.ROOT) + ":\n- "
                                   + String.join("\n- ", check.problems())
                                   + "\nTell the user why, and suggest the way forward above (offer the change, "
                                   + "e.g. with propose_dashboard_change, if they want it); or try another chart "
                                   + "kind or columns.");
        }
        // A chart of a table that already has one Stroom AI generated replaces it, or is added beside it, as the
        // user chooses.
        final String replaceName = NullSafe.get(stringArg(request, REPLACE_ARG), String::strip);
        final boolean addNew = booleanArg(request, ADD_NEW_ARG, false);
        Replace replace = null;
        if (NullSafe.isNonBlankString(replaceName)) {
            if (addNew) {
                throw new WorkspaceException("Give " + REPLACE_ARG + " or " + ADD_NEW_ARG + ", not both.");
            }
            replace = replacement(DashboardComponents.parse(part.text()), replaceName, tableName, library,
                    query || embedded != null);
        } else if (!query && embedded == null && !addNew) {
            final List<Component> generated = generatedCharts(DashboardComponents.parse(part.text()), tableName);
            if (!generated.isEmpty()) {
                return ToolResult.text(askReplaceOrAdd(tableName, generated));
            }
        }

        final StringBuilder notes = new StringBuilder();
        if (sample.note() != null) {
            notes.append("\nSample: ").append(sample.note());
        }
        if (!check.stats().isEmpty()) {
            notes.append("\nFrom the sample: ").append(check.stats().entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .collect(Collectors.joining(", "))).append('.');
        }
        if (!check.warnings().isEmpty()) {
            notes.append("\nWarnings (tell the user the ones that matter):\n- ")
                    .append(String.join("\n- ", check.warnings()));
        }

        if (CONTENT_PACK.equals(library)) {
            return contentPack(kind, mappings, target, query, tableName, placement, name, notes);
        }
        notes.append(clickFilterNote(kind, mappings, expressions, formats, query, tableName));
        return generate(chartLibrary, kind, mappings, style, options, target, query, embedded, table, tableName,
                part, name, summary, placement, replace, request, context, notes);
    }

    // Several charts proposed together, behind one button: each checked against its table's rows as a single chart
    // is. Those that can be drawn are offered; why the others can't is said. Each is a new chart (none replaces one),
    // on a dashboard table (an embedded query's chart is its show clause, proposed alone).
    private ToolResult runCharts(final ToolCall request, final ToolCallContext context, final String library) {
        if (CONTENT_PACK.equals(library)) {
            return ToolResult.text("No charts were offered: several charts at once are generated (library "
                                   + ECHARTS + " or " + D3 + "). Propose the content pack's own D3 charts one at a "
                                   + "time.");
        }
        final ChartLibrary chartLibrary = ChartLibrary.find(library).orElse(ChartLibrary.ECHARTS);
        if (!(argsMap(request).get(CHARTS_ARG) instanceof final List<?> charts) || charts.isEmpty()
            || charts.size() > MAX_CHARTS) {
            throw new WorkspaceException("Give charts as a list of 1 to " + MAX_CHARTS + " charts.");
        }
        final String summary = stringArg(request, SUMMARY_ARG);
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        final String layout = NullSafe.get(stringArg(request, LAYOUT_ARG),
                text -> text.strip().toLowerCase(Locale.ROOT));
        if (layout != null && !LAYOUTS.contains(layout)) {
            throw new WorkspaceException("Give layout as one of " + String.join(", ", LAYOUTS) + ".");
        }
        if (layout == null && charts.size() > 1) {
            return ToolResult.text("No charts were offered yet: ask the user (ask_user) how to arrange the "
                                   + charts.size() + " charts on the dashboard, unless they have said: row (side "
                                   + "by side), column (one below another), grid (rows of up to " + GRID_COLUMNS
                                   + ") or tabs (stacked as tabs in one pane). Recommend "
                                   + recommendedLayout(charts) + ". Then propose them again with layout.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a chart can only be offered while answering a question in a chat.");
        }

        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef target = NullSafe.get(tab, StoredTabSnapshot::docRef);
        if (target == null || !DashboardDoc.TYPE.equals(target.getType())) {
            return ToolResult.text(TabSnapshots.notAttached(tab,
                    tabSnapshots.latestOfType(chatId, Set.of(DashboardDoc.TYPE)).orElse(null), "dashboard",
                    "no charts can be offered"));
        }
        if (!documentAccess.canView(target)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final UnsavedPart part = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.DASHBOARD).orElse(null);
        if (part == null || part.cut()) {
            return ToolResult.text("The tab's settings aren't in its snapshot in full (it may be too large, or "
                                   + "still loading), so charts can't be proposed for it.");
        }
        final String snapshotHash = AiTabSnapshotPart.hashText(DashboardComponents.withoutRenderReports(part.text()));

        final List<String> problems = new ArrayList<>();
        final List<Map<String, Object>> planned = new ArrayList<>();
        final List<String> described = new ArrayList<>();
        final StringBuilder notes = new StringBuilder();
        final Map<String, SampleOutcome> samples = new LinkedHashMap<>();
        // The charts planned so far on each table: a table's first goes below it, the rest as the layout says.
        final Map<String, Integer> perTable = new LinkedHashMap<>();
        for (int i = 0; i < charts.size(); i++) {
            final String prefix = "Chart " + (i + 1);
            if (!(charts.get(i) instanceof final Map<?, ?> spec)) {
                problems.add(prefix + ": isn't an object with a kind, columns and name.");
                continue;
            }
            try {
                final ChartKind kind = ChartKind.find(NullSafe.get(spec.get(KIND_ARG), String::valueOf))
                        .orElseThrow(() -> new WorkspaceException("give kind as one of " + ChartKind.ids() + "."));
                final String name = NullSafe.get(spec.get(NAME_ARG), value -> String.valueOf(value).strip());
                if (name == null || name.isEmpty() || name.length() > MAX_NAME_CHARS) {
                    throw new WorkspaceException("give it a name of up to " + MAX_NAME_CHARS
                                                 + " characters, saying what it shows.");
                }
                final String label = prefix + " ('" + name + "')";
                final Map<String, String> mappings = stringMap(spec.get(COLUMNS_ARG), COLUMNS_ARG);
                final Map<String, String> style;
                final String options;
                try {
                    style = VisualisationPlan.normaliseStyle(kind, stringMap(spec.get(STYLE_ARG), STYLE_ARG),
                            chartLibrary);
                    options = VisualisationPlan.normaliseOptions(optionsText(spec.get(OPTIONS_ARG)),
                            chartLibrary);
                } catch (final IllegalArgumentException e) {
                    throw new WorkspaceException(e.getMessage());
                }
                final String specTable = NullSafe.get(spec.get(TABLE_ARG), String::valueOf);
                final String tableArg = NullSafe.isNonBlankString(specTable)
                        ? specTable
                        : stringArg(request, TABLE_ARG);
                final AiTabSearch table = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), tableArg);
                if (table == null) {
                    problems.add(label + ": say which table it draws: " + NullSafe.list(tab.searches()).stream()
                            .map(search -> "'" + tableName(search) + "'")
                            .collect(Collectors.joining(", ")) + ".");
                    continue;
                }
                final String tableName = tableName(table);
                if (ProposeDashboardChangeTool.isEmbeddedQuery(table)) {
                    problems.add(label + ": '" + tableName + "' is an embedded query, whose chart goes in its "
                                 + "StroomQL's show clause: propose it on its own.");
                    continue;
                }
                final AskStroomAiContext results = table.getLive() != null
                        ? table.getLive()
                        : table.getResults();
                final Map<String, String> formats = ResultsRequests.columnFormats(results);
                final Map<String, String> expressions = ResultsRequests.columnExpressions(results);
                final Map<String, Integer> groups = ResultsRequests.columnGroups(results);
                final List<TableColumn> columns = NullSafe.list(table.getColumns()).stream()
                        .map(column -> new TableColumn(column, formats.get(column), expressions.get(column),
                                groups.get(column)))
                        .toList();
                final SampleOutcome sample = samples.computeIfAbsent(tableName,
                        key -> sample(request, context, table));
                final ChartCheck.Result check = ChartCheck.check(kind, mappings, style, columns, sample.sample());
                if (!check.ok()) {
                    problems.add(label + ": the table '" + tableName + "' can't make this "
                                 + kind.displayName().toLowerCase(Locale.ROOT) + ": "
                                 + String.join("; ", check.problems()));
                    continue;
                }
                final int onTable = perTable.merge(tableName, 1, Integer::sum) - 1;
                final String componentName = NullSafe.nonBlankStringElse(
                        NullSafe.get(spec.get(COMPONENT_NAME_ARG), value -> String.valueOf(value).strip()), name);
                final VisualisationPlan plan = new VisualisationPlan(kind.id(), mappings, style, options, tableName,
                        table.getComponentId(), componentName, placement(layout, onTable), snapshotHash, null, null,
                        null, chartLibrary.id());
                try {
                    plan.validate();
                } catch (final IllegalArgumentException e) {
                    throw new WorkspaceException(e.getMessage());
                }
                final Map<String, Object> chart = new LinkedHashMap<>();
                chart.put("name", name);
                chart.put("plan", plan.toJson());
                planned.add(chart);
                described.add("'" + name + "': a " + kind.displayName().toLowerCase(Locale.ROOT) + " of '"
                              + tableName + "' (" + describeMappings(mappings) + ")");
                if (!check.warnings().isEmpty()) {
                    notes.append("\n").append(label).append(" warnings (tell the user the ones that matter): ")
                            .append(String.join(" ", check.warnings()));
                }
            } catch (final WorkspaceException e) {
                problems.add(prefix + ": " + e.getMessage());
            }
        }
        final String leftOut = problems.isEmpty()
                ? ""
                : "\nLeft out (tell the user why, and the fix):\n- " + String.join("\n- ", problems);
        if (planned.isEmpty()) {
            return ToolResult.text("No charts were offered." + leftOut);
        }
        final Installed installed = installed(chartLibrary).orElse(null);
        if (installed == null) {
            return ToolResult.text(notInstalled(chartLibrary, "No charts were offered"));
        }
        if (!chartLibraries.currentUserCanUse(installed.script())) {
            return ToolResult.text("No charts were offered: the user doesn't have Use permission on Script '"
                                   + installed.script().getName() + "' (" + chartLibrary.displayName() + "). Tell "
                                   + "them to ask an administrator for it.");
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", Map.of("type", target.getType(), "uuid", target.getUuid()));
        arguments.put(CHARTS_ARG, planned);
        NullSafe.consume(summary, text -> arguments.put("summary", text));
        final ToolUserAction action = new ToolUserAction("create-visualisations-" + SEQUENCE.incrementAndGet(),
                UserActions.CREATE_VISUALISATIONS, arguments);
        auditLog.documentViewed(context.session(), target, "Proposed " + planned.size() + " charts with the "
                                                           + NAME + " tool");
        final String arranged = NullSafe.nonBlankStringElse(layout, ROW);
        return ToolResult.text("The user has been shown a 'Create visualisations' button for " + planned.size()
                               + " chart(s), drawn with " + chartLibrary.displayName() + " " + installed.version()
                               + ":\n- " + String.join("\n- ", described)
                               + "\nWhen they click it, Stroom asks for one folder (the dashboard's, by default), "
                               + "creates a Visualisation for each there, named as above, and adds them to dashboard '"
                               + target.getName() + "' " + (planned.size() == 1
                ? "below its table"
                : describeLayout(arranged)) + ", unsaved. Nothing changes "
                               + "until they click it. Tell them about the button rather than saying the charts "
                               + "were made." + leftOut + notes)
                .withUserAction(action);
    }

    // The layout to recommend for several charts: a row of a few gauges or pies, a grid of four or more, else tabs.
    private static String recommendedLayout(final List<?> charts) {
        final boolean small = charts.stream().allMatch(chart -> chart instanceof final Map<?, ?> spec
                && ChartKind.find(NullSafe.get(spec.get(KIND_ARG), String::valueOf))
                        .filter(kind -> kind == ChartKind.GAUGE || kind == ChartKind.PIE)
                        .isPresent());
        if (small && charts.size() <= GRID_COLUMNS + 1) {
            return ROW + " (side by side suits small charts)";
        }
        return charts.size() >= 4
                ? GRID + " (rows of up to " + GRID_COLUMNS + ")"
                : TABS + " (one shown at a time, each at full size)";
    }

    /// @param layout  How several charts are arranged, or null for a single chart.
    /// @param onTable How many charts of the same table come before this one.
    /// @return Where the chart goes: a table's first chart below the table, the rest by the layout.
    static String placement(final String layout, final int onTable) {
        if (onTable == 0 || layout == null) {
            return VisualisationPlan.BELOW;
        }
        return switch (layout) {
            case ROW -> VisualisationPlan.RIGHT;
            case COLUMN -> VisualisationPlan.UNDER;
            case GRID -> onTable % GRID_COLUMNS == 0
                    ? VisualisationPlan.UNDER
                    : VisualisationPlan.RIGHT;
            default -> VisualisationPlan.WITH;
        };
    }

    private static String describeLayout(final String layout) {
        return switch (layout) {
            case COLUMN -> "below their tables, one below another";
            case GRID -> "below their tables, in rows of up to " + GRID_COLUMNS;
            case TABS -> "below their tables, stacked as tabs in one pane";
            default -> "below their tables, side by side";
        };
    }

    // The library's Script doc and version, if it is installed.
    private Optional<Installed> installed(final ChartLibrary library) {
        return library == ChartLibrary.D3
                ? chartLibraries.findD3Library().map(d3 -> new Installed(d3.script(), d3.version()))
                : chartLibraries.findECharts().map(echarts -> new Installed(echarts.script(), echarts.version()));
    }

    // Why nothing was offered when a library isn't installed, and what else is.
    private String notInstalled(final ChartLibrary library, final String nothing) {
        final boolean contentPack = !chartLibraries.findContentPackD3().isEmpty();
        if (library == ChartLibrary.D3) {
            return nothing + ": D3 isn't installed in Stroom. An administrator installs it as a Script doc holding a "
                   + "D3 build, version 3 or later (e.g. d3.min.js from D3 7); the visualisations content pack "
                   + "includes D3 3. " + (chartLibraries.findECharts().isPresent()
                    ? "ECharts is installed: offer an ECharts chart instead (library " + ECHARTS + ")."
                    : "No other charting library is installed either.") + " Tell the user.";
        }
        final String unrecognised = unrecognisedNote(chartLibraries.findUnrecognisedECharts());
        return nothing + ": ECharts isn't installed in Stroom. " + unrecognised + "An administrator installs it by "
               + "creating a Script doc (e.g. Visualisations/ECharts/ECharts) holding echarts.min.js from an Apache "
               + "ECharts release, version " + ChartLibraries.MIN_ECHARTS_MAJOR + " or later. "
               + (chartLibraries.findD3Library().isPresent()
                ? "D3 is installed: offer a D3 chart instead (library " + D3 + "). "
                : contentPack
                        ? "The content pack's D3 Visualisations are installed: offer one instead (library "
                          + CONTENT_PACK + "). "
                        : "No other charting library is installed either. ")
               + "Tell the user.";
    }

    // What to offer instead of a content pack chart: a chart generated with whichever library is installed.
    private String generatedInstead() {
        final List<String> libraries = new ArrayList<>();
        if (chartLibraries.findECharts().isPresent()) {
            libraries.add(ECHARTS);
        }
        if (chartLibraries.findD3Library().isPresent()) {
            libraries.add(D3);
        }
        return libraries.isEmpty()
                ? "Neither ECharts nor D3 is installed to generate one instead: tell the user an administrator "
                  + "would need to install one."
                : "Generate it instead (library " + String.join(" or ", libraries) + ").";
    }

    // How a click on the chart could filter the dashboard, for the agent to offer once the chart is created: what a
    // click selects, and the query fields (columns that read a field as it is) and table columns it can be matched
    // with. Empty if it can't filter anything: a StroomQL Query has no other components, a histogram's bars (ranges)
    // select nothing, and a chart with no column a click picks out selects nothing either.
    static String clickFilterNote(final ChartKind kind,
                                  final Map<String, String> mappings,
                                  final Map<String, String> expressions,
                                  final Map<String, String> formats,
                                  final boolean query,
                                  final String tableName) {
        if (query || kind == ChartKind.HISTOGRAM) {
            return "";
        }
        final List<String> selects = new ArrayList<>();
        final List<String> fields = new ArrayList<>();
        final List<String> columns = new ArrayList<>();
        NullSafe.map(mappings).forEach((role, column) -> {
            if (ProposeDashboardChangeTool.SELECTED_ROLES.contains(role) && NullSafe.isNonBlankString(column)) {
                selects.add(role + " (column '" + column + "')");
                columns.add(column);
                final Matcher matcher = PLAIN_FIELD.matcher(NullSafe.string(NullSafe.get(expressions,
                        map -> map.get(column))).strip());
                if (matcher.matches()) {
                    fields.add(matcher.group(1) + " (by " + role + ")");
                }
            }
        });
        if (selects.isEmpty()) {
            return "";
        }
        if (kind == ChartKind.GRAPH || kind == ChartKind.SANKEY || kind == ChartKind.CHORD) {
            selects.add("node (a node's name, from either end)");
        }
        return "\nClick filtering: once the chart is created, a click on it selects " + String.join(", ", selects)
               + ". In one line, offer to make clicks filter the dashboard (add_selection_filter, once its pane is "
               + "on the dashboard): "
               + (fields.isEmpty()
                ? "the rows of table '" + tableName + "' or another table by column(s) " + String.join(", ", columns)
                  + " (the search can't be filtered by them, as they aren't plain fields of the data source)"
                : "the search of the query feeding '" + tableName + "' by field(s) " + String.join(", ", fields)
                  + " (every table it feeds narrows, this chart too), or a table's rows by column(s) "
                  + String.join(", ", columns) + " (this chart stays whole)")
               + ". Ask which, unless the user has said; propose it only once they've created the chart."
               + (kind == ChartKind.GRAPH || kind == ChartKind.SANKEY || kind == ChartKind.CHORD
                ? " For a network, give match any with a term per end by node, and the link's ends (the source's "
                  + "field or column with selection source, the target's with selection target), so a clicked "
                  + "node or link both filter; a link matches either direction unless the chart is directed."
                : "")
               + " Ctrl-click or Shift-click selects several items; the filter then matches any of them."
               + rangeNote(kind, mappings, expressions, formats);
    }

    // For a chart with a time axis (a line chart's or a scatter plot's x, a Date Time column), how its range tool
    // can filter by a time range; empty otherwise.
    private static String rangeNote(final ChartKind kind,
                                    final Map<String, String> mappings,
                                    final Map<String, String> expressions,
                                    final Map<String, String> formats) {
        final String x = NullSafe.get(mappings, map -> map.get("x"));
        if ((kind != ChartKind.LINE && kind != ChartKind.SCATTER) || NullSafe.isBlankString(x)
            || !"DATE_TIME".equals(NullSafe.get(formats, map -> map.get(x)))) {
            return "";
        }
        final Matcher matcher = PLAIN_FIELD.matcher(NullSafe.string(NullSafe.get(expressions,
                map -> map.get(x))).strip());
        return " Its time axis has a range tool: dragging along it selects a time range, which a term with "
               + "selection " + ProposeDashboardChangeTool.RANGE_SELECTION + " keeps what is within ("
               + (matcher.matches()
                ? "the query's field " + matcher.group(1) + ", or "
                : "")
               + "a table's date column, e.g. '" + x + "'). Offer that too: e.g. to narrow the dashboard to a "
               + "period.";
    }

    // Why an embedded query's chart can't be offered, or null if it can.
    static String embeddedQueryProblem(final EmbeddedQuery embedded, final String name, final String library) {
        if (embedded == null) {
            return "Embedded query '" + name + "''s StroomQL isn't in the tab's snapshot in full (it may be too "
                   + "large, or still loading), so a chart can't be proposed for it.";
        }
        if (embedded.isReference()) {
            return "No chart was offered: embedded query '" + name + "' shows Query '"
                   + embedded.referenced().getName() + "' by reference, so its chart goes in that Query's show "
                   + "clause. Ask the user to open Query '" + embedded.referenced().getName() + "', attach its tab, "
                   + "and ask again.";
        }
        if (CONTENT_PACK.equals(library)) {
            return "No chart was offered: an embedded query is charted by a show clause in its StroomQL, which "
                   + "Stroom puts there for a chart it generates (library " + ECHARTS + " or " + D3 + "). Use one of "
                   + "those.";
        }
        return null;
    }

    // The Visualisation panes linked to the table that show a chart Stroom AI generated.
    private List<Component> generatedCharts(final List<Component> components, final String tableName) {
        return components.stream()
                .filter(component -> DashboardComponents.VISUALISATION.equals(component.type()))
                .filter(component -> tableName.equalsIgnoreCase(component.table()))
                .filter(component -> chartLibraries.isGenerated(component.visualisation()))
                .toList();
    }

    static String askReplaceOrAdd(final String tableName, final List<Component> generated) {
        final String charts = generated.stream()
                .map(component -> "pane '" + component.name() + "' (Visualisation '"
                                  + component.visualisation().getName() + "')")
                .collect(Collectors.joining(", "));
        return "No chart was offered yet: table '" + tableName + "' already has a chart Stroom AI generated: "
               + charts + ". Unless the user has already said, ask them (ask_user) whether to replace it, which "
               + "rewrites its Visualisation for the new chart and renames it (saved at once), keeping its place "
               + "on the dashboard, or to add the new chart beside it. Then call " + NAME + " again with "
               + REPLACE_ARG + " (the pane's name) or " + ADD_NEW_ARG + " true.";
    }

    // The chart a new one replaces, checked: a Visualisation pane of the same table, showing a chart Stroom AI
    // generated that the user may edit.
    private Replace replacement(final List<Component> components,
                                final String paneName,
                                final String tableName,
                                final String library,
                                final boolean query) {
        if (query) {
            throw new WorkspaceException("Only a chart on a dashboard replaces another with " + REPLACE_ARG
                                         + ": a StroomQL query's chart is its show clause, which a new chart "
                                         + "takes the place of.");
        }
        if (CONTENT_PACK.equals(library)) {
            throw new WorkspaceException("Only a generated chart (library " + ECHARTS + " or " + D3 + ") can "
                                         + "replace one Stroom AI generated, as its Visualisation is rewritten for "
                                         + "it. Give " + ADD_NEW_ARG + " true to add the content pack chart beside "
                                         + "it instead.");
        }
        final List<Component> panes = components.stream()
                .filter(component -> DashboardComponents.VISUALISATION.equals(component.type()))
                .toList();
        final Component pane = DashboardComponents.find(panes, paneName)
                .orElseThrow(() -> new WorkspaceException("The dashboard has no Visualisation pane '" + paneName
                                                          + "'. " + (panes.isEmpty()
                        ? "It has none."
                        : "Its Visualisation panes are: " + panes.stream()
                                .map(component -> "'" + component.name() + "'")
                                .collect(Collectors.joining(", ")) + ".")));
        if (!tableName.equalsIgnoreCase(pane.table())) {
            throw new WorkspaceException("Pane '" + pane.name() + "' draws table '" + pane.table() + "', not '"
                                         + tableName + "'. Chart table '" + pane.table() + "' to replace it, or "
                                         + "give " + ADD_NEW_ARG + " true.");
        }
        final DocRef visualisation = pane.visualisation();
        if (!chartLibraries.isGenerated(visualisation)) {
            throw new WorkspaceException("Pane '" + pane.name() + "' shows Visualisation '"
                                         + NullSafe.get(visualisation, DocRef::getName) + "', which Stroom AI "
                                         + "didn't generate, so it isn't rewritten. Give " + ADD_NEW_ARG + " true "
                                         + "to add the new chart beside it.");
        }
        if (!documentAccess.canEdit(visualisation)) {
            throw new WorkspaceException("The user doesn't have Edit permission on Visualisation '"
                                         + visualisation.getName() + "', so it can't be replaced. Give "
                                         + ADD_NEW_ARG + " true to add the new chart beside it instead.");
        }
        return new Replace(pane.id(), pane.name(), visualisation);
    }

    private ToolResult generate(final ChartLibrary library,
                                final ChartKind kind,
                                final Map<String, String> mappings,
                                final Map<String, String> style,
                                final String options,
                                final DocRef target,
                                final boolean query,
                                final EmbeddedQuery embedded,
                                final AiTabSearch table,
                                final String tableName,
                                final UnsavedPart part,
                                final String name,
                                final String summary,
                                final String placement,
                                final Replace replace,
                                final ToolCall request,
                                final ToolCallContext context,
                                final StringBuilder notes) {
        final Installed installed = installed(library).orElse(null);
        if (installed == null) {
            return ToolResult.text(notInstalled(library, "No chart was offered"));
        }
        if (!chartLibraries.currentUserCanUse(installed.script())) {
            return ToolResult.text("No chart was offered: the user doesn't have Use permission on Script '"
                                   + installed.script().getName() + "' (" + library.displayName() + "), so the "
                                   + "chart wouldn't load for them. Tell them to ask an administrator for it.");
        }
        // A replaced chart keeps its pane's name unless given another.
        final String componentName = NullSafe.nonBlankStringElse(stringArg(request, COMPONENT_NAME_ARG),
                replace != null
                        ? replace.componentName()
                        : name);
        // A Query's chart, or an embedded query's, is a show clause; an embedded query's is planned for its
        // component on the dashboard, against its StroomQL as it stands.
        final boolean showClause = query || embedded != null;
        final VisualisationPlan plan = new VisualisationPlan(kind.id(), mappings, style, options,
                query
                        ? null
                        : tableName,
                query
                        ? null
                        : table.getComponentId(),
                showClause
                        ? null
                        : componentName,
                showClause
                        ? null
                        : placement,
                query
                        ? AiTabSnapshotPart.hashText(part.text())
                        : AiTabSnapshotPart.hashText(DashboardComponents.withoutRenderReports(part.text())),
                showClause
                        ? Boolean.TRUE
                        : null,
                query
                        ? part.text()
                        : NullSafe.get(embedded, EmbeddedQuery::query),
                replace,
                library.id());
        try {
            plan.validate();
        } catch (final IllegalArgumentException e) {
            throw new WorkspaceException(e.getMessage());
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", Map.of("type", target.getType(), "uuid", target.getUuid()));
        arguments.put("name", name.strip());
        arguments.put("plan", plan.toJson());
        NullSafe.consume(summary, text -> arguments.put("summary", text));
        final ToolUserAction action = new ToolUserAction("create-visualisation-" + SEQUENCE.incrementAndGet(),
                UserActions.CREATE_VISUALISATION, arguments);
        auditLog.documentViewed(context.session(), target, "Proposed a " + kind.displayName().toLowerCase(Locale.ROOT)
                                                           + " of table '" + tableName + "' with the " + NAME
                                                           + " tool");

        if (replace != null) {
            return ToolResult.text(
                    "The user has been shown a 'Replace visualisation' button for a "
                    + kind.displayName().toLowerCase(Locale.ROOT) + " of table '" + tableName + "' ("
                    + describeMappings(mappings) + "), drawn with " + library.displayName() + " "
                    + installed.version() + ". When they click it, Visualisation '"
                    + replace.visualisation().getName() + "' is rewritten for it and renamed '" + name.strip()
                    + "', saved at once (they need Edit permission on it), and pane '"
                    + replace.componentName() + "' shows it" + (componentName.equals(replace.componentName())
                            ? ""
                            : ", renamed '" + componentName + "'")
                    + ", unsaved on the dashboard. Nothing changes until they click it. Tell them about the button, "
                    + "and that the old chart is replaced, rather than saying it was done. Once it draws, its render "
                    + "report shows in the chat and in the dashboard's description: if it reports an error or drew "
                    + "nothing, explain why and offer a fix." + notes)
                    .withUserAction(action);
        }
        final String where = embedded != null
                ? "put in the show clause of embedded query '" + tableName + "' on dashboard '" + target.getName()
                  + "', naming it by its UUID (show \"<its uuid>\" (" + showArguments(kind, mappings) + ")), which "
                  + "then runs, unsaved"
                : query
                ? "put in the query's show clause, naming it by its UUID (show \"<its uuid>\" ("
                  + showArguments(kind, mappings) + ")), unsaved"
                : "added to dashboard '" + target.getName() + "' " + ("tab".equals(placement)
                        ? "as a tab beside"
                        : "below") + " table '" + tableName + "', unsaved";
        return ToolResult.text(
                "The user has been shown a 'Create visualisation' button for a "
                + kind.displayName().toLowerCase(Locale.ROOT) + " of table '" + tableName + "' ("
                + describeMappings(mappings) + "), drawn with " + library.displayName() + " "
                + installed.version() + ". When they click it, Stroom's New Visualisation dialog opens with the "
                + "name '" + name.strip() + "' in the "
                + (query
                        ? "query's"
                        : "dashboard's") + " folder, which they can change; once they create it, it is " + where
                + ", and redraws as the table updates. Nothing changes until they click it. Tell them about the "
                + "button rather than saying the chart was made. Once it draws, its render report (what it drew, "
                + "and any rows it left out or error) shows in the chat and in the dashboard's description: if "
                + "it reports an error or drew nothing, explain why and offer a fix." + notes)
                .withUserAction(action);
    }

    private ToolResult contentPack(final ChartKind kind,
                               final Map<String, String> mappings,
                               final DocRef target,
                               final boolean query,
                               final String tableName,
                               final String placement,
                               final String name,
                               final StringBuilder notes) {
        if (D3Visualisation.find(kind).isEmpty()) {
            return ToolResult.text("No chart was offered: the visualisations content pack has no D3 "
                                   + kind.displayName().toLowerCase(Locale.ROOT) + ". " + generatedInstead());
        }
        final DocRef visualisation = chartLibraries.findContentPackD3().get(kind);
        if (visualisation == null) {
            return ToolResult.text("No chart was offered: the visualisations content pack's D3 "
                                   + kind.displayName().toLowerCase(Locale.ROOT) + " isn't installed. "
                                   + generatedInstead());
        }
        final D3Visualisation d3 = D3Visualisation.forKind(kind);
        final Map<String, String> settings = new LinkedHashMap<>();
        final List<String> unsupported = new ArrayList<>();
        for (final Entry<String, String> entry : mappings.entrySet()) {
            d3.control(entry.getKey()).ifPresentOrElse(
                    control -> settings.put(control, entry.getValue()),
                    () -> unsupported.add(entry.getKey()));
        }
        final String unsupportedNote = unsupported.isEmpty()
                ? ""
                : "\nThe D3 " + kind.displayName().toLowerCase(Locale.ROOT) + " has no input for "
                  + String.join(", ", unsupported) + ", so it is left out: tell the user, or use ECharts.";
        if (query) {
            final String clause = "show " + VisualisationActions.quote(visualisation.getUuid()) + " ("
                                  + settings.entrySet().stream()
                                          .map(entry -> entry.getKey() + " = "
                                                        + VisualisationActions.quote(entry.getValue()))
                                          .collect(Collectors.joining(", ")) + ")";
            return ToolResult.text("The data can make the chart. Nothing has been offered yet: propose the query "
                                   + "with this show clause in place of any it has, as its last clause, with "
                                   + "propose_query_change (it names '" + visualisation.getName() + "' by its "
                                   + "UUID, so a name shared with another Visualisation can't make it "
                                   + "ambiguous):\n" + clause + unsupportedNote + notes);
        }
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("op", "add_vis");
        change.put("table", tableName);
        change.put("name", NullSafe.nonBlankStringElse(name, visualisation.getName()));
        change.put("visualisation", visualisation.getUuid());
        change.put("settings", settings);
        change.put("placement", placement);
        return ToolResult.text("The data can make the chart with the content pack's D3 Visualisation '"
                               + visualisation.getName() + "' (" + visualisation.getUuid() + "). Nothing has been "
                               + "offered yet: propose this change with propose_dashboard_change (component '"
                               + tableName + "'):\n" + JsonUtil.writeValueAsString(change, false)
                               + unsupportedNote + notes);
    }

    // A sample of the table's rows, from its current_results snapshot (taken if there isn't one, never re-run).
    private SampleOutcome sample(final ToolCall request, final ToolCallContext context, final AiTabSearch table) {
        final Map<String, Object> args = new LinkedHashMap<>();
        NullSafe.consume(table.getName(), tableName -> args.put(CurrentResultsTool.TABLE_ARG, tableName));
        final CurrentResultsTool.Outcome outcome;
        try {
            outcome = currentResultsTool.outcome(new ToolCall(request.id(), CurrentResultsTool.NAME,
                    JsonUtil.writeValueAsString(args, false)), context);
        } catch (final RuntimeException e) {
            return new SampleOutcome(null, "the table's rows couldn't be read (" + e.getMessage() + ")");
        }
        if (outcome.path() == null) {
            return new SampleOutcome(null, outcome.text().lines().findFirst().orElse("none"));
        }
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = WorkspacePath.parse(outcome.path());
        final TableEngine.Result result = TableEngine.run(workspace.localFile(path),
                Format.forPath(path),
                new Query(List.of(), List.of(), List.of(), List.of(), false, false, List.of(), MAX_SAMPLE_ROWS));
        return new SampleOutcome(new Sample(result.columns(), result.rows()),
                result.rows().size() + " rows from snapshot " + outcome.path());
    }

    /// Says which Script docs are named like ECharts but don't hold an ECharts build Stroom can use, if any.
    ///
    /// @param scripts Those Script docs.
    /// @return A sentence naming them, or empty text.
    static String unrecognisedNote(final List<DocRef> scripts) {
        return scripts.isEmpty()
                ? ""
                : "Script " + scripts.stream()
                          .map(ref -> "'" + ref.getName() + "'")
                          .collect(Collectors.joining(", "))
                  + " doesn't hold an ECharts " + ChartLibraries.MIN_ECHARTS_MAJOR + " or later build (it may be "
                  + "an older release, or not ECharts), so it isn't used. ";
    }

    /// The options argument as JSON text: models give it as a JSON string, as asked, or as the object itself.
    ///
    /// @param value The argument's value.
    /// @return Its JSON text, or null if not given.
    static String optionsText(final Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Map || value instanceof List
                ? JsonUtil.writeValueAsString(value, false)
                : String.valueOf(value);
    }

    private static Map<String, String> stringMap(final Object value, final String argument) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof final Map<?, ?> map)) {
            throw new WorkspaceException("Give " + argument + " as an object of names to text values.");
        }
        final Map<String, String> strings = new LinkedHashMap<>();
        map.forEach((key, entry) -> {
            if (entry != null && !String.valueOf(entry).isBlank()) {
                // Stripped here, once, so the chart is given the names the check found.
                strings.put(String.valueOf(key).strip(), String.valueOf(entry).strip());
            }
        });
        return strings;
    }

    private static String tableName(final AiTabSearch search) {
        return NullSafe.nonBlankStringElse(search.getName(), search.getComponentId());
    }

    private static String describeMappings(final Map<String, String> mappings) {
        return mappings.entrySet().stream()
                .map(entry -> entry.getKey() + " = '" + entry.getValue() + "'")
                .collect(Collectors.joining(", "));
    }

    private static String showArguments(final ChartKind kind, final Map<String, String> mappings) {
        return kind.roles().stream()
                .filter(role -> mappings.containsKey(role.id()))
                .map(role -> role.id() + " = " + VisualisationActions.quote(mappings.get(role.id())))
                .collect(Collectors.joining(", "));
    }

    private static Set<String> allRoleIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (final ChartKind kind : ChartKind.values()) {
            kind.roles().stream().map(ChartRole::id).forEach(ids::add);
        }
        return ids;
    }

    private static Set<String> allStyleIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (final StyleSetting setting : StyleSetting.values()) {
            ids.add(setting.id());
        }
        return ids;
    }

    // A sample of rows and a note on where it came from, or null and why there is none.
    private record SampleOutcome(Sample sample, String note) {

    }

    // A charting library as installed: its Script doc and version.
    private record Installed(DocRef script, String version) {

    }
}
