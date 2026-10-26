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
import stroom.ai.impl.DashboardBuilder;
import stroom.ai.impl.DashboardChanges;
import stroom.ai.impl.DashboardChanges.Change;
import stroom.ai.impl.DashboardChanges.Checked;
import stroom.ai.impl.DashboardComponents;
import stroom.ai.impl.DashboardComponents.Component;
import stroom.ai.impl.DashboardSettings;
import stroom.ai.impl.DashboardSettings.Setting;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.vis.ChartLibrary;
import stroom.ai.impl.vis.EChartsTemplate;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableExpression;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// `propose_dashboard_change` - offers the user a change to a table on the dashboard in their attached tab (group or
/// ungroup, add, edit, hide or show a column, sort, or filter a column), shown as a plain-language list with an
/// "Apply to dashboard" button. It changes nothing itself: the button applies the change to the open dashboard
/// through the same code its column menus use, as an unsaved edit (which needs Edit on the dashboard, checked when
/// the user clicks it). Nothing is ever saved by it.
///
/// The change is checked against the dashboard as the user has it, from the latest snapshot of the tab: the table
/// and each column it names must exist, and each expression must be valid (with the parser the table tool's eval
/// uses, and the data source's fields where they can be read). A Visualisation a change adds or switches to must be
/// one the user can view, and its settings must be controls it declares, with each `field` control's value a column
/// of the table it is linked to. An invalid change is refused with the reason, so
/// the agent can correct it. The hash of the dashboard's settings is kept with the button, which only applies
/// while the dashboard still has them.
class ProposeDashboardChangeTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProposeDashboardChangeTool.class);

    static final String NAME = "propose_dashboard_change";
    static final String COMPONENT_ARG = "component";
    static final String CHANGES_ARG = "changes";
    static final String SUMMARY_ARG = "summary";
    static final int MAX_SUMMARY_CHARS = 500;
    /// The most fields read to check a column expression's fields.
    static final int MAX_FIELDS = 10_000;

    static final String NO_CHANGE = "no change was proposed";

    // The column ids a click on a generated chart selects.
    static final Set<String> SELECTED_ROLES = Set.of("category", "series", "x", "y", "name", "source",
            "target", "date", "level2", "level3", "level4", "level5", "level6");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /// The selection key of a time range dragged on a time-series chart: its terms keep what is within the
    /// selection's from and to.
    static final String RANGE_SELECTION = "range";
    // The selection keys of a clicked link's two ends: its source and target, or two neighbouring levels of a flow
    // with more (a link selects only its own two ends, so the rest drop out).
    private static final Set<String> LINK_ENDS = Set.of("source", "target", "level3", "level4", "level5",
            "level6");
    // A selection filter's id, as the dashboard file gives it.
    private static final Pattern SELECTION_FILTER_ID = Pattern.compile("\\[id ([^\\]\\s]+)]");
    // A component's heading in the dashboard file, e.g. "## Table 'Events' (id table-1)".
    private static final Pattern TABLE_HEADING = Pattern.compile("^## Table '(.*)' \\(id [^)]+\\)$");
    // A column of a table in the dashboard file formatted as a date, e.g. "- Day: floorDay(${EventTime}) (grouped,
    // level 1; format Date Time)".
    private static final Pattern DATE_COLUMN = Pattern.compile("^- (.+?): .*[(;] ?format Date Time[;)].*$");

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;
    private final DashboardBuilder dashboardBuilder;

    @Inject
    ProposeDashboardChangeTool(final ChatWorkspace workspace,
                               final TabSnapshots tabSnapshots,
                               final DocumentAccess documentAccess,
                               final DataSourceAccess dataSourceAccess,
                               final Provider<QueryService> queryServiceProvider,
                               final AiAuditLog auditLog,
                               final DashboardBuilder dashboardBuilder) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
        this.dashboardBuilder = dashboardBuilder;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public boolean isChatOnly() {
        // It proposes a change to the tab the user attached to the chat.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema change = ObjectSchema.builder()
                .addEnumProperty("op", DashboardChanges.OPERATIONS, "What to do.")
                .addStringProperty("column", "The column, by its name as the user sees it (not for add_column, "
                                             + "add_table or set_expression).")
                .addStringProperty("table", "The table the change is to, if not the component: only a table an "
                                            + "earlier add_table in these changes adds. For add_text: the table "
                                            + "it follows (default the component). For add_vis: the table it is "
                                            + "linked to (default the component).")
                .addIntegerProperty("level", "For group: the level to group at, from 1.")
                .addStringProperty("name", "For add_column: the new column's name. For edit_column: its new name. "
                                           + "For add_table, add_text or add_vis: the new component's name. For "
                                           + "rename: the new name.")
                .addBooleanProperty("copy", "For add_table: true to start with the component's columns, false "
                                            + "(the default) for none.")
                .addProperty("conditions", ArraySchema.builder()
                        .items(ObjectSchema.builder()
                                .addStringProperty("column", "The column, by its name as the user sees it.")
                                .addEnumProperty("condition", List.copyOf(DashboardChanges.RULE_CONDITIONS.keySet()),
                                        "The condition.")
                                .addStringProperty("value", "The value to compare with (for between, two values "
                                                            + "separated by a comma; for in, values separated by "
                                                            + "spaces). Not for is null or is not null.")
                                .required("column", "condition")
                                .build())
                        .description("For add_format_rule: the conditions a row must meet.")
                        .build())
                .addEnumProperty("match", List.of("all", "any"), "For add_format_rule: whether a row must meet all "
                                                                + "the conditions (the default) or any of them. For "
                                                                + "add_selection_filter: whether all its terms must "
                                                                + "hold (the default) or any. With any, a clicked "
                                                                + "link's source and target terms still hold "
                                                                + "together, so one filter can match a clicked node "
                                                                + "at either end or a clicked link. A link of an "
                                                                + "undirected graph or a chord diagram matches either "
                                                                + "way round, as the chart sends it both ways. "
                                                                + "Several filters on one target match when any of "
                                                                + "them does.")
                .addEnumProperty("style", DashboardChanges.FORMAT_STYLES, "For add_format_rule: the colour to give "
                                                                          + "matching rows.")
                .addEnumProperty("format_type", DashboardChanges.FORMAT_TYPES, "For add_format_rule: what the "
                                                                              + "colour is given to (default "
                                                                              + "background).")
                .addBooleanProperty("bold", "For add_format_rule: make matching rows bold.")
                .addBooleanProperty("italic", "For add_format_rule: make matching rows italic.")
                .addBooleanProperty("hide", "For add_format_rule: hide matching rows.")
                .addStringProperty("target", "For rename or set_setting: the component (table, query or text), "
                                             + "by its name as the user sees it; default the component. For "
                                             + "set_vis_settings: the Visualisation component. For "
                                             + "add_selection_filter: the query, embedded query or table to filter; "
                                             + "for remove_selection_filter, the one it filters.")
                .addStringProperty("chart", "For add_selection_filter: the Visualisation component whose "
                                            + "selection (what the user clicks on it) filters the target.")
                .addEnumProperty("mode", DashboardChanges.FILTER_MODES, "For add_selection_filter: query filters "
                                                                        + "the target's search, re-run as the "
                                                                        + "selection changes, so every table it "
                                                                        + "feeds narrows, the chart's own too "
                                                                        + "(its fields are the query's); table "
                                                                        + "filters the target table's rows and the "
                                                                        + "chart stays whole (its fields are the "
                                                                        + "table's columns). Ask the user which if "
                                                                        + "they haven't said.")
                .addProperty("terms", ArraySchema.builder()
                        .items(ObjectSchema.builder()
                                .addStringProperty("field", "A query field (mode query) or a column of the table "
                                                            + "(mode table).")
                                .addStringProperty("selection", "What the chart's selection holds to compare it "
                                                                + "with: a column id the chart maps (e.g. category, "
                                                                + "name, x, source), the table column mapped to it, "
                                                                + "or node for a network graph's node.")
                                .required("field", "selection")
                                .build())
                        .description("For add_selection_filter: each field and the selection it must equal. A "
                                     + "term drops out while nothing is selected. A date field or column "
                                     + "given a date the chart selects (a time axis's x, a calendar's date, or "
                                     + "any column the table formats as Date Time), or selection range, keeps "
                                     + "it within the selected time: the range dragged along a time axis, or "
                                     + "the time a clicked point stands for (e.g. its day, for floorDay() "
                                     + "values).")
                        .build())
                .addStringProperty("visualisation", "For add_vis: the UUID of the Visualisation document to show. "
                                                    + "For set_vis_settings: the UUID of one to switch to, if any.")
                .addProperty("settings", ObjectSchema.builder()
                        .description("For add_vis or set_vis_settings: the Visualisation's settings, as an object "
                                     + "of its control ids and their values as text, e.g. {\"category\": "
                                     + "\"UserId\", \"value\": \"Count\"}. A field control's value is a column "
                                     + "of the linked table. For set_vis_settings, only the controls to change; an "
                                     + "empty value puts a control back to its default.")
                        .build())
                .addEnumProperty("placement", DashboardChanges.PLACEMENTS, "For add_vis: below (the default) for a "
                                                                          + "new pane below the table, or tab for "
                                                                          + "a tab beside it.")
                .addStringProperty("setting", "For set_setting: the setting. " + settingsHelp())
                .addStringProperty("value", "For set_setting: its value (true or false, a number, a pipeline's "
                                            + "UUID, a data source's name or UUID, a component's name, or none "
                                            + "where allowed).")
                .addStringProperty("expression", "For add_column or edit_column: the column's expression in "
                                                 + "Stroom's expression language, e.g. count() or "
                                                 + "${EventTime}.")
                .addEnumProperty("format", DashboardChanges.FORMATS, "For add_column or edit_column: the format.")
                .addProperty("position", new IntegerSchema(ToolDescription.builder()
                        .general("For add_column: where to add it, from 1 (default the end). For move_column: where "
                                 + "to move it, from 1. For add_format_rule: where to add the rule among the table's "
                                 + "rules, from 1 (default the end; the first rule a row matches applies). For "
                                 + "remove_format_rule: the rule's number")
                        .chatOnly(", as the dashboard file lists them")
                        .general(".")
                        .build()))
                .addEnumProperty("direction", List.of(DashboardChanges.ASCENDING, DashboardChanges.DESCENDING,
                        DashboardChanges.NONE), "For sort: the direction, or none to stop sorting by it.")
                .addIntegerProperty("priority", "For sort: its priority, from 1 (sorted by first). By default "
                                                + "after the table's other sorts.")
                .addStringProperty("filter", "For filter: the column's filter text, as its filter box takes "
                                             + "it; empty to clear it. For remove_selection_filter: the filter's "
                                             + "id, as the dashboard file gives it (e.g. ab12c from [id ab12c]).")
                .addStringProperty("where", "For set_expression: the whole new set of conditions for the "
                                            + "classic query that feeds the table, written as a StroomQL where "
                                            + "clause has them (without 'where'), e.g. Text = \"*Afghan*\" or "
                                            + "Text = \"*Kabul*\". Stroom turns them into the query's "
                                            + "expression.")
                .required("op")
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .chatOnly("While the tab the user attached is a dashboard, ")
                        .general("proposes a change to one of its tables: group or ungroup, add_column, edit_column, "
                                 + "move_column, hide_column, show_column, sort, or filter, applied in order; "
                                 + "set_expression, which replaces the expression of the classic query that feeds "
                                 + "the table (give its conditions in where, and show the user the expression, never "
                                 + "StroomQL); add_table, which adds a table as a tab beside it, fed by the same "
                                 + "query, that the changes after it set up by naming it as their table (e.g. a tab "
                                 + "per event category: add_table, its columns, and a filter on the category "
                                 + "column); add_text, which adds a Text widget below the table (or a table added "
                                 + "before it, named as its table) showing the event of the selected row, that later "
                                 + "set_setting changes can set up by naming it as their target; add_vis, which adds "
                                 + "a Visualisation component linked to the table (or a table added before it), "
                                 + "below it or as a tab beside it, showing an existing Visualisation document by its "
                                 + "UUID (e.g. one of the content pack's D3 visualisations) with settings for its "
                                 + "controls; set_vis_settings, "
                                 + "which changes the controls of a Visualisation component, or the Visualisation it "
                                 + "shows; add_selection_filter, which filters a query's search or a table's rows "
                                 + "by what the user clicks on a chart (a slice, bar, point or node); "
                                 + "remove_selection_filter, which removes one (by its id in the dashboard file; to "
                                 + "change one, remove it and add the new one in the same proposal); "
                                 + "add_format_rule or "
                                 + "remove_format_rule, for the table's conditional formatting (rows matching "
                                 + "conditions on its columns coloured, bold, italic or hidden); rename, which "
                                 + "renames any component and its tab; or set_setting, which changes a setting of "
                                 + "any table, query or text component, as its Settings dialog has it. The user is "
                                 + "shown the changes with an 'Apply to dashboard' button; nothing changes until "
                                 + "they click it, and it is never saved. Columns and the table are named as the "
                                 + "user sees them. Expressions are checked first; an invalid change is refused with "
                                 + "the reason.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(COMPONENT_ARG, "The table the changes are for, by its name as the user "
                                                          + "sees it. Changes to other components name them as "
                                                          + "their target. Or an embedded query's name, for "
                                                          + "set_setting and rename changes only.")
                        .addProperty(CHANGES_ARG, ArraySchema.builder()
                                .items(change)
                                .description("The changes, applied in order: at most "
                                             + DashboardChanges.MAX_CHANGES + ", all in this one proposal.")
                                .build())
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what the change does and why, shown with "
                                                        + "it.")
                        .required(COMPONENT_ARG, CHANGES_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    // The table a Visualisation pane of the name is linked to, or null if there is no such pane: changes to a chart
    // are proposed for its table, so a pane's name is taken for its table's.
    private AiTabSearch linkedTable(final int chatId, final StoredTabSnapshot tab, final String paneName) {
        final UnsavedPart part = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.DASHBOARD).orElse(null);
        if (part == null) {
            return null;
        }
        return DashboardComponents.find(DashboardComponents.parse(part.text()).stream()
                        .filter(pane -> DashboardComponents.VISUALISATION.equals(pane.type()))
                        .toList(), paneName)
                .map(Component::table)
                .map(linked -> CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), linked))
                .orElse(null);
    }

    // An embedded query's search, in a dashboard tab: its results are a StroomQL query's.
    static boolean isEmbeddedQuery(final AiTabSearch search) {
        return search.getLive() instanceof QueryTableContext || search.getResults() instanceof QueryTableContext;
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String component = stringArg(request, COMPONENT_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (component == null) {
            throw new WorkspaceException("Give the table, by its name as the user sees it.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        final List<Change> changes;
        try {
            changes = DashboardChanges.parse(argsMap(request).get(CHANGES_ARG));
        } catch (final IllegalArgumentException e) {
            throw new WorkspaceException(e.getMessage());
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }

        // The tab the user attached to the chat.
        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef dashboard = NullSafe.get(tab, StoredTabSnapshot::docRef);
        if (dashboard == null || !DashboardDoc.TYPE.equals(dashboard.getType())) {
            return ToolResult.text(TabSnapshots.notAttached(tab,
                    tabSnapshots.latestOfType(chatId, Set.of(DashboardDoc.TYPE)).orElse(null), "dashboard",
                    NO_CHANGE));
        }
        if (!documentAccess.canView(dashboard)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final AiTabSearch named = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), component);
        final AiTabSearch table = named != null
                ? named
                : linkedTable(chatId, tab, component);
        if (table == null) {
            return ToolResult.text(
                    "The dashboard has no table or embedded query '" + component + "'. Its tables and embedded "
                    + "queries are: "
                    + String.join(", ", NullSafe.list(tab.searches()).stream()
                     .map(search -> "'" + NullSafe.nonBlankStringElse(search.getName(), search.getComponentId())
                                    + "'")
                     .toList()) + ".");
        }
        final UnsavedPart settings = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.DASHBOARD).orElse(null);
        if (settings == null || settings.cut() || NullSafe.isBlankString(table.getComponentId())) {
            return ToolResult.text(
                    "The dashboard's settings aren't in the tab snapshot in full (it may be too large, or still "
                    + "loading), so a change can't be proposed. Give the steps for the user to make it from the "
                    + "column menus instead.");
        }

        final String tableName = NullSafe.nonBlankStringElse(table.getName(), table.getComponentId());
        if (isEmbeddedQuery(table)) {
            final List<String> notSettings = changes.stream()
                    .map(Change::op)
                    .filter(op -> !DashboardChanges.SET_SETTING.equals(op) && !DashboardChanges.RENAME.equals(op)
                                  && !DashboardChanges.ADD_SELECTION_FILTER.equals(op)
                                  && !DashboardChanges.REMOVE_SELECTION_FILTER.equals(op))
                    .distinct()
                    .toList();
            if (!notSettings.isEmpty()) {
                return ToolResult.text("No change was proposed: '" + tableName + "' is an embedded query, whose "
                                       + "StroomQL makes its results, so it takes only " + DashboardChanges.SET_SETTING
                                       + " (e.g. queryOnOpen), " + DashboardChanges.RENAME + ", "
                                       + DashboardChanges.ADD_SELECTION_FILTER + " and "
                                       + DashboardChanges.REMOVE_SELECTION_FILTER + " changes, not "
                                       + String.join(", ", notSettings) + ". Its StroomQL is in the dashboard's "
                                       + "description: tell the user the StroomQL to use instead.");
            }
        }
        final Built result = build(changes, table, tableName, settings.text(), NullSafe.list(tab.searches()));
        if (!result.problems().isEmpty()) {
            return ToolResult.text("No change was proposed, as it isn't valid:\n- "
                                   + String.join("\n- ", result.problems()) + "\nCorrect it and propose it again.");
        }
        final List<Change> built = result.changes();

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", Map.of("type", dashboard.getType(), "uuid", dashboard.getUuid()));
        arguments.put("component", tableName);
        arguments.put("component_id", table.getComponentId());
        arguments.put("changes", built.stream().map(Change::toMap).toList());
        // Not of the Visualisations' render reports, which change as they render.
        arguments.put("snapshot_hash", AiTabSnapshotPart.hashText(
                DashboardComponents.withoutRenderReports(settings.text())));
        NullSafe.consume(summary, text -> arguments.put("summary", text));
        final ToolUserAction action = new ToolUserAction("apply-dashboard-change-" + SEQUENCE.incrementAndGet(),
                UserActions.APPLY_DASHBOARD_CHANGE, arguments);
        auditLog.documentViewed(context.session(), dashboard, "Proposed a change to table '" + tableName + "' with the "
                                                   + NAME + " tool: "
                                                   + String.join("; ", DashboardChanges.describe(built)));

        return ToolResult.text(
                "The user has been shown the change to table '" + tableName + "' on dashboard '"
                + dashboard.getName() + "' with an 'Apply to dashboard' button:\n- "
                + String.join("\n- ", DashboardChanges.describe(built))
                + "\nNothing changes until they click it, and it is never saved: it is applied to the open "
                  + "dashboard as an unsaved change, on top of any unsaved changes it has, which they can save, or "
                  + "undo with Undo apply. Tell them about the button rather than saying the dashboard was changed."
                  + eventNote(built, table, tableName)).withUserAction(action);
    }

    /// Checks changes to a table on a dashboard, and builds them as the browser applies them: what this tool
    /// proposes, and what another proposes with its own change (e.g. propose_visualisation, a table for its chart).
    ///
    /// @param changes       The changes, as the model gave them.
    /// @param table         The table (or embedded query) they change, or add a table beside.
    /// @param tableName     Its name, as the user sees it.
    /// @param dashboardText The dashboard's description, as the browser gives it.
    /// @param searches      The dashboard's tables and embedded queries.
    /// @return The changes as built, the checks' findings, and why any can't be made (none if all can).
    Built build(final List<Change> changes,
                final AiTabSearch table,
                final String tableName,
                final String dashboardText,
                final List<AiTabSearch> searches) {
        final Checked checked = DashboardChanges.check(changes, NullSafe.list(table.getColumns()), tableName,
                searches.stream()
                        .map(search -> NullSafe.nonBlankStringElse(search.getName(), search.getComponentId()))
                        .toList());
        final List<String> problems = new ArrayList<>(checked.problems());
        problems.addAll(expressionProblems(checked.changes(), table));
        final List<Change> withExpressions = withQueryExpressions(checked.changes(), table, dashboardText, problems);
        final List<Change> built = withSelectionFilters(withVisualisations(withComponents(withExpressions,
                dashboardText, tableName, problems), checked, dashboardText, searches, tableName, problems), checked,
                dashboardText, searches, problems);
        return new Built(built, checked, List.copyOf(problems));
    }

    // Resolves the component each rename and set_setting changes, by name (or a table these changes add), and checks
    // and resolves each setting's value: documents by their UUID (or a data source by its name), and components by
    // their name. Adds why to the problems for any that can't be.
    private List<Change> withComponents(final List<Change> changes,
                                        final String dashboardText,
                                        final String tableName,
                                        final List<String> problems) {
        final List<Component> components = DashboardComponents.parse(dashboardText);
        // The components these changes add, by name (lower case), with their type.
        final Map<String, String> added = new HashMap<>();
        final List<Change> resolved = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            final Change change = changes.get(i);
            final String prefix = "Change " + (i + 1) + " (" + change.op() + "): ";
            if (DashboardChanges.ADD_TABLE.equals(change.op()) || DashboardChanges.ADD_TEXT.equals(change.op())
                || DashboardChanges.ADD_VIS.equals(change.op())) {
                final String key = change.name().toLowerCase(Locale.ROOT);
                if (!DashboardChanges.ADD_TABLE.equals(change.op())
                    && (added.containsKey(key) || DashboardComponents.find(components, change.name()).isPresent())) {
                    problems.add(prefix + "the dashboard already has a component '" + change.name() + "'. Give "
                                 + "another name.");
                }
                added.put(key, switch (change.op()) {
                    case DashboardChanges.ADD_TABLE -> DashboardComponents.TABLE;
                    case DashboardChanges.ADD_VIS -> DashboardComponents.VISUALISATION;
                    default -> DashboardComponents.TEXT;
                });
            }
            if (!DashboardChanges.RENAME.equals(change.op()) && !DashboardChanges.SET_SETTING.equals(change.op())) {
                resolved.add(change);
                continue;
            }
            final String targetName = NullSafe.nonBlankStringElse(change.target(), tableName);
            final String targetId;
            final String type;
            if (added.containsKey(targetName.toLowerCase(Locale.ROOT))) {
                // Known by its name until it is added.
                targetId = null;
                type = added.get(targetName.toLowerCase(Locale.ROOT));
            } else {
                final Component target = DashboardComponents.find(components, targetName).orElse(null);
                if (target == null) {
                    problems.add(prefix + "the dashboard has no component '" + targetName + "'. Its components "
                                 + "are: " + DashboardComponents.describe(components) + ".");
                    resolved.add(change);
                    continue;
                }
                targetId = target.id();
                type = target.type();
            }
            if (DashboardChanges.RENAME.equals(change.op())) {
                if (components.stream().anyMatch(c -> c.name().equalsIgnoreCase(change.name())
                                                      && !c.id().equals(targetId))) {
                    problems.add(prefix + "the dashboard already has a component '" + change.name() + "'.");
                }
                resolved.add(change.withResolved(targetId, null, null, null));
                continue;
            }
            final Setting setting = DashboardSettings.find(type, change.setting()).orElse(null);
            if (setting == null) {
                final List<String> keys = DashboardSettings.keys(type);
                problems.add(prefix + type + " '" + targetName + "' has no setting '" + change.setting() + "'"
                             + (DashboardComponents.VISUALISATION.equals(type)
                        ? ". Change a Visualisation's settings with set_vis_settings."
                        : keys.isEmpty()
                                ? ", and its settings can't be changed here."
                                : ". Its settings are: " + String.join(", ", keys) + "."));
                resolved.add(change);
                continue;
            }
            try {
                resolved.add(resolveSetting(setting, change, targetId, components));
            } catch (final IllegalArgumentException e) {
                problems.add(prefix + e.getMessage());
                resolved.add(change);
            }
        }
        return resolved;
    }

    // Checks each add_vis and set_vis_settings change: the Visualisation it shows must be one the user can view,
    // and its settings controls the Visualisation declares, each field control's value a column of the table it is
    // linked to. Resolves the Visualisation (with its name) and, for set_vis_settings, the component it changes.
    // Adds why to the problems for any that can't be.
    private List<Change> withVisualisations(final List<Change> changes,
                                            final Checked checked,
                                            final String dashboardText,
                                            final List<AiTabSearch> searches,
                                            final String tableName,
                                            final List<String> problems) {
        final List<Component> components = DashboardComponents.parse(dashboardText);
        // The Visualisations read, by UUID; and the Visualisation components these changes add, by name (lower
        // case), as added.
        final Map<String, VisualisationDoc> visualisations = new HashMap<>();
        final Map<String, Change> addedVis = new HashMap<>();
        final List<Change> resolved = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            final Change change = changes.get(i);
            final String prefix = "Change " + (i + 1) + " (" + change.op() + "): ";
            if (DashboardChanges.ADD_VIS.equals(change.op())) {
                final VisualisationDoc visualisation = visualisation(change.doc(), visualisations);
                if (visualisation == null) {
                    problems.add(prefix + "no Visualisation you can view has the UUID '"
                                 + NullSafe.get(change.doc(), DocRef::getUuid) + "'. Give a Visualisation's UUID.");
                    resolved.add(change);
                    continue;
                }
                final String linked = NullSafe.nonBlankStringElse(change.table(), tableName);
                final Change exact = change.toBuilder().doc(docRef(visualisation)).build();
                visProblems(prefix, exact.settings(), visualisation, checked.columnsOf(change.table()), linked,
                        problems);
                addedVis.put(change.name().toLowerCase(Locale.ROOT), exact);
                resolved.add(exact);
                continue;
            }
            if (!DashboardChanges.SET_VIS_SETTINGS.equals(change.op())) {
                resolved.add(change);
                continue;
            }
            // The component: one these changes add (known by its name until it is added), or one the dashboard has.
            final Change added = addedVis.get(change.target().toLowerCase(Locale.ROOT));
            final Component component = added != null
                    ? null
                    : DashboardComponents.find(components, change.target()).orElse(null);
            if (added == null && (component == null
                                  || !DashboardComponents.VISUALISATION.equals(component.type()))) {
                final List<Component> visComponents = components.stream()
                        .filter(c -> DashboardComponents.VISUALISATION.equals(c.type()))
                        .toList();
                problems.add(prefix + "the dashboard has no Visualisation component '" + change.target() + "'. "
                             + (visComponents.isEmpty()
                        ? "It has none: add one with add_vis."
                        : "Its Visualisation components are: " + DashboardComponents.describe(visComponents) + "."));
                resolved.add(change);
                continue;
            }
            final DocRef current = added != null
                    ? added.doc()
                    : component.visualisation();
            final DocRef wanted = change.doc() != null
                    ? change.doc()
                    : current;
            final VisualisationDoc visualisation = visualisation(wanted, visualisations);
            if (visualisation == null) {
                problems.add(prefix + (wanted == null
                        ? "the component shows no Visualisation yet. Give the UUID of one to show (visualisation)."
                        : "no Visualisation you can view has the UUID '" + wanted.getUuid() + "'."));
                resolved.add(change);
                continue;
            }
            final String linked = added != null
                    ? NullSafe.nonBlankStringElse(added.table(), tableName)
                    : component.table();
            visProblems(prefix, change.settings(), visualisation, linkedColumns(linked, checked, searches, tableName),
                    NullSafe.nonBlankStringElse(linked, "?"), problems);
            resolved.add(change.toBuilder()
                    .targetId(NullSafe.get(component, Component::id))
                    .doc(change.doc() == null
                            ? null
                            : docRef(visualisation))
                    .build());
        }
        return resolved;
    }

    // Reads a Visualisation the user can view, once per UUID, or gives null if there is no such one.
    private VisualisationDoc visualisation(final DocRef docRef, final Map<String, VisualisationDoc> visualisations) {
        if (docRef == null || NullSafe.isBlankString(docRef.getUuid())) {
            return null;
        }
        return visualisations.computeIfAbsent(docRef.getUuid().strip(), uuid -> documentAccess
                .read(new DocRef(VisualisationDoc.TYPE, uuid), VisualisationDoc.class)
                .orElse(null));
    }

    private static DocRef docRef(final VisualisationDoc visualisation) {
        return new DocRef(VisualisationDoc.TYPE, visualisation.getUuid(), visualisation.getName());
    }

    // Adds what is wrong with a Visualisation's settings to the problems.
    private static void visProblems(final String prefix,
                                    final Map<String, String> settings,
                                    final VisualisationDoc visualisation,
                                    final List<String> columns,
                                    final String table,
                                    final List<String> problems) {
        DashboardChanges.visSettingsProblems(settings, DashboardChanges.visControls(visualisation.getSettings()),
                        columns, NullSafe.nonBlankStringElse(visualisation.getName(), visualisation.getUuid()),
                        "table '" + table + "'")
                .forEach(problem -> problems.add(prefix + problem));
        NullSafe.consume(kindChangeProblem(settings, visualisation), problem -> problems.add(prefix + problem));
    }

    // A chart Stroom AI generated can't be made another kind by its pane's settings: its Visualisation's Data tab has
    // the old kind's columns, so it would ask for columns it can't be given (seen in testing, a line chart made a
    // bar chart asked for x and y). It is converted with propose_visualisation's replace instead.
    static String kindChangeProblem(final Map<String, String> settings, final VisualisationDoc visualisation) {
        if (ChartLibrary.forFunctionName(visualisation.getFunctionName()).isEmpty()) {
            return null;
        }
        final Map<String, String> defaults = EChartsTemplate.defaults(visualisation.getSettings());
        final List<String> changed = Stream.of(EChartsTemplate.KIND_CONTROL, EChartsTemplate.FIELDS_CONTROL)
                .filter(id -> NullSafe.isNonBlankString(NullSafe.map(settings).get(id)))
                .filter(id -> !NullSafe.map(settings).get(id).strip().equalsIgnoreCase(
                        NullSafe.string(defaults.get(id)).strip()))
                .toList();
        if (changed.isEmpty()) {
            return null;
        }
        return "a chart Stroom AI generated can't be made another kind by its settings (" + String.join(" and ",
                changed) + "): its Visualisation has the " + NullSafe.nonBlankStringElse(defaults.get(
                EChartsTemplate.KIND_CONTROL), "old") + " chart's columns. Convert it with propose_visualisation, "
               + "replace (the pane's name), which rewrites the Visualisation for the new kind, clears the pane's "
               + "settings and keeps the filters its clicks drive.";
    }

    // The columns of the table a Visualisation is linked to: as these changes leave them, for the proposal's table
    // or one they add, else as the dashboard's searches have them; null if they aren't known.
    private static List<String> linkedColumns(final String table,
                                              final Checked checked,
                                              final List<AiTabSearch> searches,
                                              final String tableName) {
        if (table == null) {
            return null;
        }
        final List<String> known = table.equalsIgnoreCase(tableName)
                ? checked.columnsOf(null)
                : checked.columnsOf(table);
        if (known != null) {
            return known;
        }
        return searches.stream()
                .filter(search -> table.equals(NullSafe.nonBlankStringElse(search.getName(),
                        search.getComponentId())))
                .findFirst()
                .map(search -> NullSafe.list(search.getColumns()))
                .orElse(null);
    }

    // Checks a setting's value, resolving any document or component it names.
    private Change resolveSetting(final Setting setting,
                                  final Change change,
                                  final String targetId,
                                  final List<Component> components) {
        final String value = DashboardSettings.normalise(setting, change.value());
        final boolean none = DashboardSettings.NONE.equalsIgnoreCase(value);
        return switch (setting.kind()) {
            case PIPELINE, PIPELINE_OR_NONE -> {
                if (none && setting.kind() == DashboardSettings.Kind.PIPELINE_OR_NONE) {
                    yield change.withResolved(targetId, DashboardSettings.NONE, null, null);
                }
                final DocRef pipeline = documentAccess.findDocRef(value)
                        .filter(doc -> PipelineDoc.TYPE.equals(doc.getType()))
                        .filter(documentAccess::canView)
                        .orElseThrow(() -> new IllegalArgumentException("no pipeline you can view has the UUID '"
                                                                        + value + "'. Give a Pipeline's UUID."));
                yield change.withResolved(targetId, pipeline.getUuid(), null, pipeline);
            }
            case DATA_SOURCE -> {
                final List<DocRef> found = dataSourceAccess.find(value).stream()
                        .filter(dataSourceAccess::canUse)
                        .toList();
                if (found.size() != 1) {
                    throw new IllegalArgumentException(found.isEmpty()
                            ? "no data source you can use is called '" + value + "' or has that UUID."
                            : "more than one data source is called '" + value + "'. Give its UUID.");
                }
                yield change.withResolved(targetId, found.getFirst().getUuid(), null, found.getFirst());
            }
            case QUERY_COMPONENT, TABLE_COMPONENT -> {
                if (none && setting.kind() == DashboardSettings.Kind.TABLE_COMPONENT) {
                    yield change.withResolved(targetId, DashboardSettings.NONE, null, null);
                }
                final String wanted = setting.kind() == DashboardSettings.Kind.QUERY_COMPONENT
                        ? DashboardComponents.QUERY
                        : DashboardComponents.TABLE;
                final Component component = DashboardComponents.find(components, value)
                        .filter(c -> wanted.equals(c.type()))
                        .orElseThrow(() -> new IllegalArgumentException("the dashboard has no " + wanted
                                                                        + " '" + value + "'."));
                yield change.withResolved(targetId, component.name(), component.id(), null);
            }
            default -> change.withResolved(targetId, value, null, null);
        };
    }

    // Builds the query expression of each set_expression change from its conditions, against the table's data
    // source, adding why to the problems if it can't be.
    private List<Change> withQueryExpressions(final List<Change> changes,
                                              final AiTabSearch table,
                                              final String dashboardText,
                                              final List<String> problems) {
        final List<Change> built = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            final Change change = changes.get(i);
            if (!DashboardChanges.SET_EXPRESSION.equals(change.op())) {
                built.add(change);
                continue;
            }
            final AskStroomAiContext request = table.getLive() != null
                    ? table.getLive()
                    : table.getResults();
            final DocRef dataSource = ResultsRequests.dashboardDataSource(request);
            if (dataSource == null || !dataSourceAccess.canUse(dataSource)) {
                problems.add("Change " + (i + 1) + " (set_expression): the table's query has no data source you "
                             + "can use, so its expression can't be set.");
                built.add(change);
                continue;
            }
            // The expression it replaces, as its hash (ref), so it isn't applied over the expression if the user has
            // changed it since: everything else the user changes since is kept, as the change is applied on top.
            final String replaced = DashboardComponents.queryExpression(dashboardText, table.getComponentId())
                    .map(AiTabSnapshotPart::hashLines)
                    .orElse(null);
            try {
                built.add(change.withQuery(dashboardBuilder.whereExpression(dataSource, change.where()))
                        .toBuilder()
                        .ref(replaced)
                        .build());
            } catch (final IllegalArgumentException e) {
                problems.add("Change " + (i + 1) + " (set_expression): " + e.getMessage());
                built.add(change);
            }
        }
        return built;
    }

    // A note for the model when the changes add or change a Text widget (which shows, and steps through, the event
    // of the selected row) while the table's rows can't be traced to events: they are grouped, and the table doesn't
    // show the detail rows under them, nor do the changes turn them on.
    static String eventNote(final List<Change> changes, final AiTabSearch table, final String tableName) {
        final List<String> textSettings = DashboardSettings.keys(DashboardComponents.TEXT);
        final boolean text = changes.stream().anyMatch(change -> DashboardChanges.ADD_TEXT.equals(change.op())
                                                                 || (DashboardChanges.SET_SETTING.equals(change.op())
                                                                     && textSettings.contains(change.setting())));
        final boolean detailOn = changes.stream().anyMatch(change -> DashboardChanges.SET_SETTING.equals(change.op())
                                                                     && "showDetail".equals(change.setting())
                                                                     && "true".equals(change.value()));
        final AskStroomAiContext request = table.getLive() != null
                ? table.getLive()
                : table.getResults();
        if (!text || detailOn || !ResultsRequests.isGrouped(request) || ResultsRequests.hasEventIdColumns(request)) {
            return "";
        }
        return "\nNote: table '" + tableName + "' groups its rows and doesn't show the detail rows under them, so a "
               + "selected row has no single event: a Text widget following it shows nothing, and its stepping "
               + "button can't start. Tell the user, and offer to turn on the table's detail rows (set_setting "
               + "showDetail true), so they can expand a group and select one of its events.";
    }

    // The settings each kind of component has, for the model.
    private static String settingsHelp() {
        final List<String> parts = new ArrayList<>();
        for (final String type : List.of(DashboardComponents.TABLE, DashboardComponents.QUERY,
                DashboardComponents.EMBEDDED_QUERY, DashboardComponents.TEXT)) {
            parts.add(type + ": " + String.join("; ", DashboardSettings.SETTINGS.stream()
                    .filter(setting -> setting.componentType().equals(type))
                    .map(setting -> setting.key() + " (" + setting.label() + ")")
                    .toList()));
        }
        return String.join(". ", parts) + ".";
    }

    // Checks each expression a change gives, with the fields of the table's data source if they can be read.
    private List<String> expressionProblems(final List<Change> changes, final AiTabSearch table) {
        final List<String> problems = new ArrayList<>();
        List<String> fields = null;
        boolean fieldsRead = false;
        for (int i = 0; i < changes.size(); i++) {
            final Change change = changes.get(i);
            if (change.expression() == null) {
                continue;
            }
            if (!fieldsRead) {
                fields = fields(table);
                fieldsRead = true;
            }
            final int number = i + 1;
            TableExpression.dashboardProblem(change.expression(), fields)
                    .ifPresent(problem -> problems.add("Change " + number + " (" + change.op() + "): " + problem));
        }
        return problems;
    }

    // Resolves each add_selection_filter's components (the one it filters and the chart whose selection filters it),
    // checks its mode suits the component, its selection keys are ones the chart gives, and its fields are the
    // query's (or its columns the table's), and builds its filter. Adds why to the problems for any that can't be.
    private List<Change> withSelectionFilters(final List<Change> changes,
                                              final Checked checked,
                                              final String dashboardText,
                                              final List<AiTabSearch> searches,
                                              final List<String> problems) {
        final List<Component> components = DashboardComponents.parse(dashboardText);
        final List<Component> charts = components.stream()
                .filter(component -> DashboardComponents.VISUALISATION.equals(component.type()))
                .toList();
        final List<Change> resolved = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            final Change change = changes.get(i);
            final String prefix = "Change " + (i + 1) + " (" + change.op() + "): ";
            if (DashboardChanges.REMOVE_SELECTION_FILTER.equals(change.op())) {
                resolved.add(withFilterToRemove(prefix, change, components, dashboardText, problems));
                continue;
            }
            if (!DashboardChanges.ADD_SELECTION_FILTER.equals(change.op())) {
                resolved.add(change);
                continue;
            }
            final Component target = DashboardComponents.find(components, change.target()).orElse(null);
            final Component chart = DashboardComponents.find(charts, change.name()).orElse(null);
            if (target == null) {
                problems.add(prefix + "the dashboard has no component '" + change.target() + "'. Its components "
                             + "are: " + DashboardComponents.describe(components) + ".");
            }
            if (chart == null) {
                problems.add(prefix + "the dashboard has no Visualisation component '" + change.name() + "'. "
                             + (charts.isEmpty()
                        ? "It has none."
                        : "Its Visualisation components are: " + DashboardComponents.describe(charts) + "."));
            }
            if (target == null || chart == null) {
                resolved.add(change);
                continue;
            }
            final List<String> before = List.copyOf(problems);
            final Set<String> dateKeys = dateKeys(chart, dashboardText);
            checkSelectionFilter(prefix, change, target, chart, dateKeys, checked, searches, problems);
            if (!problems.equals(before)) {
                resolved.add(change);
                continue;
            }
            final Change ranged = withDatesAsRanges(change, dateKeys);
            // Stroom ANDs the terms of an operator that name the same component's selection, whatever the
            // operator, so for any of them to match, each is put in an operator of its own (seen in testing: a
            // filter on a sender or a recipient being the clicked node matched only messages to oneself).
            final boolean any = "any".equals(ranged.filter());
            final ExpressionOperator.Builder filter = ExpressionOperator.builder().op(any
                    ? Op.OR
                    : Op.AND);
            // A clicked link's source and target terms stay together: both ends must match (the chart sends a link
            // that is the same either way round both ways, so it matches in either direction).
            final List<ExpressionTerm> linkTerms = new ArrayList<>();
            final long termCount = ranged.settings().values().stream()
                    .mapToLong(selections -> DashboardChanges.selectionsOf(selections).size())
                    .sum();
            ranged.settings().forEach((field, selections) -> DashboardChanges.selectionsOf(selections)
                    .forEach(key -> {
                        final List<ExpressionTerm> terms = selectionTerms(field, key, chart.id());
                        if (any && LINK_ENDS.contains(key)) {
                            linkTerms.addAll(terms);
                        } else if (any && termCount > 1) {
                            filter.addOperator(ExpressionOperator.builder().addTerms(terms).build());
                        } else {
                            terms.forEach(filter::addTerm);
                        }
                    }));
            if (!linkTerms.isEmpty()) {
                filter.addOperator(ExpressionOperator.builder().addTerms(linkTerms).build());
            }
            resolved.add(ranged.toBuilder().targetId(target.id()).ref(chart.id()).query(filter.build()).build());
        }
        return resolved;
    }

    // The filter with each term comparing a field with a date the chart selects (e.g. a time axis's x) made a
    // range: equal to the date, it would match only the instant the clicked point starts at (seen in testing: a
    // line chart of days filtered by its x matched nothing). The chart gives a clicked date's time as from and to
    // (e.g. its day), as it does a range dragged along its time axis.
    static Change withDatesAsRanges(final Change change, final Set<String> dateKeys) {
        if (dateKeys.isEmpty()) {
            return change;
        }
        final Map<String, String> settings = new LinkedHashMap<>();
        change.settings().forEach((field, selections) -> settings.put(field, String.join(
                DashboardChanges.SELECTIONS_SEPARATOR, DashboardChanges.selectionsOf(selections).stream()
                        .map(key -> dateKeys.contains(key)
                                ? RANGE_SELECTION
                                : key)
                        .distinct()
                        .toList())));
        return change.toBuilder().settings(settings).build();
    }

    // The keys of a chart's selection that are dates: each column it maps that a click picks out (e.g. a time
    // axis's x) which its table formats as a date, by its id and by the column's name.
    static Set<String> dateKeys(final Component chart, final String dashboardText) {
        final Set<String> dateColumns = new HashSet<>();
        boolean inTable = false;
        for (final String line : NullSafe.string(dashboardText).split("\n")) {
            if (line.startsWith("## ")) {
                final Matcher heading = TABLE_HEADING.matcher(line);
                inTable = heading.matches() && heading.group(1).equals(chart.table());
            } else if (inTable) {
                final Matcher column = DATE_COLUMN.matcher(line);
                if (column.matches()) {
                    dateColumns.add(column.group(1));
                }
            }
        }
        final Set<String> keys = new LinkedHashSet<>();
        NullSafe.map(chart.settings()).forEach((key, column) -> {
            if (SELECTED_ROLES.contains(key) && dateColumns.contains(column)) {
                keys.add(key);
                keys.add(column);
            }
        });
        return keys;
    }

    // The terms comparing a field (or column) with what a chart's selection holds: equal to it, or, for a time range
    // (the selection's from and to), within it. Stroom ANDs the terms naming one selection, and leaves out each
    // whose value isn't held, so a range's terms keep what is within it, and drop out while nothing is selected.
    static List<ExpressionTerm> selectionTerms(final String field, final String key, final String chartId) {
        final String selection = "${component." + chartId + ".selection.";
        if (RANGE_SELECTION.equals(key)) {
            return List.of(
                    ExpressionTerm.builder()
                            .field(field)
                            .condition(Condition.GREATER_THAN_OR_EQUAL_TO)
                            .value(selection + "from}")
                            .build(),
                    ExpressionTerm.builder()
                            .field(field)
                            .condition(Condition.LESS_THAN_OR_EQUAL_TO)
                            .value(selection + "to}")
                            .build());
        }
        return List.of(ExpressionTerm.builder()
                .field(field)
                .condition(Condition.EQUALS)
                .value(selection + key + "}")
                .build());
    }

    // A remove_selection_filter with the id of the component it removes the filter from, if the component has a
    // filter with that id; otherwise as given, with what is wrong.
    private static Change withFilterToRemove(final String prefix,
                                             final Change change,
                                             final List<Component> components,
                                             final String dashboardText,
                                             final List<String> problems) {
        final Component target = DashboardComponents.find(components, change.target()).orElse(null);
        if (target == null) {
            problems.add(prefix + "the dashboard has no component '" + change.target() + "'. Its components are: "
                         + DashboardComponents.describe(components) + ".");
            return change;
        }
        final List<String> ids = selectionFilterIds(dashboardText, target.id());
        if (!ids.contains(change.filter())) {
            problems.add(prefix + "'" + target.name() + "' has no selection filter '" + change.filter() + "'. "
                         + (ids.isEmpty()
                    ? "It has none."
                    : "Its selection filters' ids are: " + String.join(", ", ids) + "."));
            return change;
        }
        return change.toBuilder().targetId(target.id()).build();
    }

    // The ids of a component's selection filters, as the dashboard file gives them ([id ab12c]) on its "Query
    // selection filters" and "Table selection filters" lines.
    static List<String> selectionFilterIds(final String dashboardText, final String componentId) {
        final List<String> ids = new ArrayList<>();
        boolean inComponent = false;
        for (final String line : NullSafe.string(dashboardText).split("\n")) {
            if (line.startsWith("## ")) {
                inComponent = line.endsWith("(id " + componentId + ")");
            } else if (inComponent && (line.startsWith("Query selection filters: ")
                                       || line.startsWith("Table selection filters: "))) {
                final Matcher matcher = SELECTION_FILTER_ID.matcher(line);
                while (matcher.find()) {
                    ids.add(matcher.group(1));
                }
            }
        }
        return ids;
    }

    // Whether a selection filter suits what it filters and the chart it is filtered by.
    private void checkSelectionFilter(final String prefix,
                                      final Change change,
                                      final Component target,
                                      final Component chart,
                                      final Set<String> dateKeys,
                                      final Checked checked,
                                      final List<AiTabSearch> searches,
                                      final List<String> problems) {
        final boolean query = DashboardChanges.QUERY_FILTER.equals(change.value());
        final String type = target.type();
        final boolean embedded = DashboardComponents.EMBEDDED_QUERY.equals(type);
        if (query && !embedded && !DashboardComponents.QUERY.equals(type)) {
            problems.add(prefix + "'" + target.name() + "' is a " + type + ": a query filter filters a Query's or "
                         + "Embedded Query's search. Use mode table to filter a table's rows.");
            return;
        }
        if (!query && !embedded && !DashboardComponents.TABLE.equals(type)) {
            problems.add(prefix + "'" + target.name() + "' is a " + type + ": a table filter filters a Table's (or "
                         + "an Embedded Query's) rows. Use mode query to filter a query's search.");
            return;
        }
        final Set<String> keys = selectionKeys(chart);
        if (!dateKeys.isEmpty()) {
            keys.add(RANGE_SELECTION);
        }
        change.settings().values().stream()
                .flatMap(selections -> DashboardChanges.selectionsOf(selections).stream())
                .filter(key -> !keys.isEmpty() && !keys.contains(key))
                .distinct()
                .forEach(key -> problems.add(prefix + "chart '" + chart.name() + "' doesn't select '" + key + "'. "
                                             + "A click on it selects: " + String.join(", ", keys) + "."));
        final List<String> known;
        final String what;
        if (query) {
            // An embedded query's fields are its StroomQL data source's, which aren't known here.
            known = embedded
                    ? null
                    : fieldsOf(target.dataSource());
            what = "field";
        } else {
            // The table's columns as the proposal's changes leave them (e.g. with a column it adds for the filter),
            // if they change the table; otherwise as the table has them.
            final List<String> changed = checked.columnsOf(target.name());
            known = changed != null
                    ? changed
                    : searches.stream()
                            .filter(search -> target.id().equals(search.getComponentId()))
                            .findFirst()
                            .map(AiTabSearch::getColumns)
                            .orElse(null);
            what = "column";
        }
        if (known != null) {
            change.settings().keySet().stream()
                    .filter(field -> !known.contains(field))
                    .forEach(field -> problems.add(prefix + "'" + target.name() + "' has no " + what + " '" + field
                                                   + "'. Its " + what + "s are: " + String.join(", ", known)
                                                   + "."));
        }
    }

    // The keys a click on a chart Stroom AI generated selects: each of its mapped columns a click picks out (not a
    // measure such as a bar's value) by its id (e.g. category) and by the name of the table column mapped to it, and
    // node for a network graph's nodes. Empty for another chart, whose keys aren't known.
    static Set<String> selectionKeys(final Component chart) {
        final Set<String> keys = new LinkedHashSet<>();
        NullSafe.map(chart.settings()).forEach((key, column) -> {
            if (SELECTED_ROLES.contains(key) && NullSafe.isNonBlankString(column)) {
                keys.add(key);
                keys.add(column);
            }
        });
        if (keys.contains("source")) {
            keys.add("node");
        }
        // A time-series chart's range tool selects a range of its x axis.
        if (keys.contains("x")) {
            keys.add(RANGE_SELECTION);
        }
        return keys;
    }

    // The names of the fields of the table's data source, or null if they can't be read.
    private List<String> fields(final AiTabSearch table) {
        final AskStroomAiContext request = table.getLive() != null
                ? table.getLive()
                : table.getResults();
        return fieldsOf(ResultsRequests.dashboardDataSource(request));
    }

    // The names of a data source's fields, or null if they can't be read.
    private List<String> fieldsOf(final DocRef dataSource) {
        if (dataSource == null || !dataSourceAccess.canUse(dataSource)) {
            return null;
        }
        try {
            final ResultPage<QueryField> page = queryServiceProvider.get().findFields(new FindFieldCriteria(
                    new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null, null));
            final List<String> names = NullSafe.list(NullSafe.get(page, ResultPage::getValues)).stream()
                    .map(QueryField::getFldName)
                    .filter(Objects::nonNull)
                    .toList();
            return names.isEmpty()
                    ? null
                    : names;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the fields of " + dataSource, e);
            return null;
        }
    }

    @Override
    public boolean readsUntrustedContent() {
        // Column names and expressions are written by users.
        return true;
    }


    // --------------------------------------------------------------------------------


    /// Changes to a dashboard table, checked and built (see [#build(List, AiTabSearch, String, String, List)]).
    ///
    /// @param changes  The changes as the browser applies them.
    /// @param checked  What checking them found, e.g. the columns of a table they add.
    /// @param problems Why any of them can't be made; none if all can.
    record Built(List<Change> changes, Checked checked, List<String> problems) {

    }
}
