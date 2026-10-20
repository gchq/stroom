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
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableExpression;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AskStroomAiContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_dashboard_change` - offers the user a change to a table on the dashboard in their attached tab (group or
/// ungroup, add, edit, hide or show a column, sort, or filter a column), shown as a plain-language list with an
/// "Apply to dashboard" button. It changes nothing itself: the button applies the change to the open dashboard
/// through the same code its column menus use, as an unsaved edit (which needs Edit on the dashboard, checked when
/// the user clicks it). Nothing is ever saved by it.
///
/// The change is checked against the dashboard as the user has it, from the latest snapshot of the tab: the table
/// and each column it names must exist, and each expression must be valid (with the parser the table tool's eval
/// uses, and the data source's fields where they can be read). An invalid change is refused with the reason, so
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

    static final String NOT_DASHBOARD = "No dashboard tab is attached to the chat, so no change was proposed. Ask the "
                                        + "user to open the dashboard and attach its tab (+ by the chat's input), then "
                                        + "ask again.";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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
    public ToolDefinition definition() {
        final ObjectSchema change = ObjectSchema.builder()
                .addEnumProperty("op", DashboardChanges.OPERATIONS, "What to do.")
                .addStringProperty("column", "The column, by its name as the user sees it (not for add_column, "
                                             + "add_table or set_expression).")
                .addStringProperty("table", "The table the change is to, if not the component: only a table an "
                                            + "earlier add_table in these changes adds. For add_text: the table "
                                            + "it follows (default the component).")
                .addIntegerProperty("level", "For group: the level to group at, from 1.")
                .addStringProperty("name", "For add_column: the new column's name. For edit_column: its new name. "
                                           + "For add_table or add_text: the new component's name. For rename: "
                                           + "the new name.")
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
                                                                + "the conditions (the default) or any of them.")
                .addEnumProperty("style", DashboardChanges.FORMAT_STYLES, "For add_format_rule: the colour to give "
                                                                          + "matching rows.")
                .addEnumProperty("format_type", DashboardChanges.FORMAT_TYPES, "For add_format_rule: what the "
                                                                              + "colour is given to (default "
                                                                              + "background).")
                .addBooleanProperty("bold", "For add_format_rule: make matching rows bold.")
                .addBooleanProperty("italic", "For add_format_rule: make matching rows italic.")
                .addBooleanProperty("hide", "For add_format_rule: hide matching rows.")
                .addStringProperty("target", "For rename or set_setting: the component (table, query or text), "
                                             + "by its name as the user sees it; default the component.")
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
                                             + "it; empty to clear it.")
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
                                 + "set_setting changes can set up by naming it as their target; add_format_rule or "
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
                                                          + "their target.")
                        .addProperty(CHANGES_ARG, ArraySchema.builder()
                                .items(change)
                                .description("The changes, applied in order.")
                                .build())
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what the change does and why, shown with "
                                                        + "it.")
                        .required(COMPONENT_ARG, CHANGES_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
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
            return ToolResult.text(NOT_DASHBOARD);
        }
        if (!documentAccess.canView(dashboard)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final AiTabSearch table = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), component);
        if (table == null) {
            return ToolResult.text(
                    "The dashboard has no table '" + component + "'. Its tables are: "
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
        final Checked checked = DashboardChanges.check(changes, NullSafe.list(table.getColumns()), tableName,
                NullSafe.list(tab.searches()).stream()
                        .map(search -> NullSafe.nonBlankStringElse(search.getName(), search.getComponentId()))
                        .toList());
        final List<String> problems = new ArrayList<>(checked.problems());
        problems.addAll(expressionProblems(checked.changes(), table));
        final List<Change> built = withComponents(withQueryExpressions(checked.changes(), table, problems),
                settings.text(), tableName, problems);
        if (!problems.isEmpty()) {
            return ToolResult.text("No change was proposed, as it isn't valid:\n- " + String.join("\n- ", problems)
                                   + "\nCorrect it and propose it again.");
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", Map.of("type", dashboard.getType(), "uuid", dashboard.getUuid()));
        arguments.put("component", tableName);
        arguments.put("component_id", table.getComponentId());
        arguments.put("changes", built.stream().map(Change::toMap).toList());
        arguments.put("snapshot_hash", AiTabSnapshotPart.hashText(settings.text()));
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
            if (DashboardChanges.ADD_TABLE.equals(change.op()) || DashboardChanges.ADD_TEXT.equals(change.op())) {
                final String key = change.name().toLowerCase(Locale.ROOT);
                if (DashboardChanges.ADD_TEXT.equals(change.op())
                    && (added.containsKey(key) || DashboardComponents.find(components, change.name()).isPresent())) {
                    problems.add(prefix + "the dashboard already has a component '" + change.name() + "'. Give "
                                 + "another name.");
                }
                added.put(key, DashboardChanges.ADD_TABLE.equals(change.op())
                        ? DashboardComponents.TABLE
                        : DashboardComponents.TEXT);
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
                             + (keys.isEmpty()
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
            try {
                built.add(change.withQuery(dashboardBuilder.whereExpression(dataSource, change.where())));
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
                DashboardComponents.TEXT)) {
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

    // The names of the fields of the table's data source, or null if they can't be read.
    private List<String> fields(final AiTabSearch table) {
        final AskStroomAiContext request = table.getLive() != null
                ? table.getLive()
                : table.getResults();
        final DocRef dataSource = ResultsRequests.dashboardDataSource(request);
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
}
