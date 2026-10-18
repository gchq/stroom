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
import stroom.ai.impl.DashboardChanges;
import stroom.ai.impl.DashboardChanges.Change;
import stroom.ai.impl.DashboardChanges.Checked;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableExpression;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AskStroomAiContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_dashboard_change` - offers the user a change to a table on the dashboard in their active tab (group or
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

    static final String NOT_DASHBOARD = "The user's active tab isn't a dashboard, so no change was proposed. Ask the "
                                        + "user to open the dashboard, then ask again.";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;

    @Inject
    ProposeDashboardChangeTool(final ChatWorkspace workspace,
                               final TabSnapshots tabSnapshots,
                               final DocumentAccess documentAccess,
                               final DataSourceAccess dataSourceAccess,
                               final Provider<QueryService> queryServiceProvider,
                               final AiAuditLog auditLog) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolSpecification specification() {
        final JsonObjectSchema change = JsonObjectSchema.builder()
                .addEnumProperty("op", DashboardChanges.OPERATIONS, "What to do.")
                .addStringProperty("column", "The column, by its name as the user sees it (not for add_column).")
                .addIntegerProperty("level", "For group: the level to group at, from 1.")
                .addStringProperty("name", "For add_column: the new column's name. For edit_column: its new name.")
                .addStringProperty("expression", "For add_column or edit_column: the column's expression in "
                                                 + "Stroom's expression language, e.g. count() or "
                                                 + "${EventTime}.")
                .addEnumProperty("format", DashboardChanges.FORMATS, "For add_column or edit_column: the format.")
                .addIntegerProperty("position", "For add_column: where to add it, from 1 (default the end).")
                .addEnumProperty("direction", List.of(DashboardChanges.ASCENDING, DashboardChanges.DESCENDING,
                        DashboardChanges.NONE), "For sort: the direction, or none to stop sorting by it.")
                .addIntegerProperty("priority", "For sort: its priority, from 1 (sorted by first). By default "
                                                + "after the table's other sorts.")
                .addStringProperty("filter", "For filter: the column's filter text, as its filter box takes "
                                             + "it; empty to clear it.")
                .required("op")
                .build();
        return ToolSpecification.builder()
                .name(NAME)
                .description("While the user's active tab is a dashboard, proposes a change to one of its tables: "
                             + "group or ungroup, add_column, edit_column, hide_column, show_column, sort, or "
                             + "filter, applied in order. The user is shown the changes with an 'Apply to "
                             + "dashboard' button; nothing changes until they click it, and it is never saved. "
                             + "Columns and the table are named as the user sees them. Expressions are checked "
                             + "first; an invalid change is refused with the reason.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(COMPONENT_ARG, "The table, by its name as the user sees it.")
                        .addProperty(CHANGES_ARG, JsonArraySchema.builder()
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
    String run(final ToolExecutionRequest request, final int chatId) {
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
        final ToolRunContext context = ToolRunContext.current().orElse(null);
        if (context == null) {
            return "Error: a button can only be offered while answering a question in a chat.";
        }

        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef dashboard = NullSafe.get(tab, StoredTabSnapshot::docRef);
        if (dashboard == null || !DashboardDoc.TYPE.equals(dashboard.getType())) {
            return NOT_DASHBOARD;
        }
        if (!documentAccess.canView(dashboard)) {
            return DocumentAccess.NO_PERMISSION;
        }
        final AiTabSearch table = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), component);
        if (table == null) {
            return "The dashboard has no table '" + component + "'. Its tables are: "
                   + String.join(", ", NullSafe.list(tab.searches()).stream()
                    .map(search -> "'" + NullSafe.nonBlankStringElse(search.getName(), search.getComponentId())
                                   + "'")
                    .toList()) + ".";
        }
        final UnsavedPart settings = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.DASHBOARD).orElse(null);
        if (settings == null || settings.cut() || NullSafe.isBlankString(table.getComponentId())) {
            return "The dashboard's settings aren't in the tab snapshot in full (it may be too large, or still "
                   + "loading), so a change can't be proposed. Give the steps for the user to make it from the "
                   + "column menus instead.";
        }

        final Checked checked = DashboardChanges.check(changes, NullSafe.list(table.getColumns()));
        final List<String> problems = new ArrayList<>(checked.problems());
        problems.addAll(expressionProblems(checked.changes(), table));
        if (!problems.isEmpty()) {
            return "No change was proposed, as it isn't valid:\n- " + String.join("\n- ", problems)
                   + "\nCorrect it and propose it again.";
        }

        final String tableName = NullSafe.nonBlankStringElse(table.getName(), table.getComponentId());
        final Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", "apply-dashboard-change-" + SEQUENCE.incrementAndGet());
        action.put("action", UserActions.APPLY_DASHBOARD_CHANGE);
        action.put("doc", Map.of("type", dashboard.getType(), "uuid", dashboard.getUuid()));
        action.put("component", tableName);
        action.put("component_id", table.getComponentId());
        action.put("changes", checked.changes().stream().map(Change::toMap).toList());
        action.put("snapshot_hash", AiTabSnapshotPart.hashText(settings.text()));
        NullSafe.consume(summary, text -> action.put("summary", text));
        context.offerUserAction(request.id(), action);
        auditLog.documentViewed(chatId, dashboard, "Proposed a change to table '" + tableName + "' with the "
                                                   + NAME + " tool: "
                                                   + String.join("; ", DashboardChanges.describe(checked.changes())));

        return "The user has been shown the change to table '" + tableName + "' on dashboard '"
               + dashboard.getName() + "' with an 'Apply to dashboard' button:\n- "
               + String.join("\n- ", DashboardChanges.describe(checked.changes()))
               + "\nNothing changes until they click it, and it is never saved: it is applied to the open "
                 + "dashboard as an unsaved change, which they can save, or undo by closing it without saving. "
                 + "Tell them about the button rather than saying the dashboard was changed.";
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
