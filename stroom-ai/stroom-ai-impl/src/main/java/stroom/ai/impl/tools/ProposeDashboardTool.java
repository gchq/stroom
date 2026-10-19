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

import stroom.ai.impl.DashboardBuilder;
import stroom.ai.impl.DashboardBuilder.Built;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.impl.QueryService;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_dashboard` - offers the user a "Create dashboard…" button for a new dashboard built from a StroomQL
/// query the agent has written: the query (kept as StroomQL, or turned into a classic expression query, as the user
/// chose), a table of the columns its `select` gives, and, if the user wants one, a data preview (Text) widget.
/// It creates nothing itself: the button opens Stroom's own New Dashboard dialog with the suggested name, and the
/// dashboard is only created, with the agent's settings, if the user confirms the dialog (which checks their
/// permission to create it in the folder they choose, and logs it as Stroom logs any new document). The query must
/// be valid and read a data source the user can use, and the dashboard must be one Stroom can build from it.
class ProposeDashboardTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_dashboard";
    static final String NAME_ARG = "name";
    static final String QUERY_ARG = "query";
    static final String QUERY_TYPE_ARG = "query_type";
    static final String PREVIEW_ARG = "preview";
    static final String DESCRIPTION_ARG = "description";
    static final int MAX_NAME_CHARS = 200;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final DashboardBuilder dashboardBuilder;

    @Inject
    ProposeDashboardTool(final ChatWorkspace workspace,
                         final DataSourceAccess dataSourceAccess,
                         final Provider<QueryService> queryServiceProvider,
                         final DashboardBuilder dashboardBuilder) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.dashboardBuilder = dashboardBuilder;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Offers the user a 'Create dashboard…' button for a new Dashboard built from your "
                             + "StroomQL: a query, a table of the columns your select gives (with any grouping, "
                             + "sorting and limit), and optionally a data preview (a Text widget showing the "
                             + "event of the selected row). It creates nothing itself: the button opens Stroom's "
                             + "New Dashboard dialog with the name you suggest. Before using it, ask the user with "
                             + "one ask_user call: whether the query should be StroomQL or Expression Language "
                             + "(classic); which fields the table should show, unless their request says (offer "
                             + "sets of fields from describe_data_source as options); whether they want a data "
                             + "preview; and which data source, if more than one could hold what they asked for. "
                             + "Write the query in StroomQL either way: for classic, Stroom turns its where "
                             + "clause into the query's expression and its select into the table's columns. "
                             + "Validate it first.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(NAME_ARG, "The name to suggest for the new dashboard.")
                        .addStringProperty(QUERY_ARG, "The StroomQL, with a select clause giving the table's "
                                                      + "columns.")
                        .addEnumProperty(QUERY_TYPE_ARG, DashboardBuilder.QUERY_TYPES, "As the user chose: "
                                + DashboardBuilder.STROOMQL + " keeps the query as StroomQL (an Embedded "
                                + "Query, which shows its own table); " + DashboardBuilder.CLASSIC + " makes an "
                                + "Expression Language query and a separate table. Classic can't hold window, "
                                + "filter, having or show clauses.")
                        .addBooleanProperty(PREVIEW_ARG, "Whether the user wants a data preview below the table, "
                                                         + "as they answered when asked. It needs ungrouped "
                                                         + "rows: if the query groups, say so when you ask.")
                        .addStringProperty(DESCRIPTION_ARG, "Optional. A sentence saying what the dashboard "
                                                            + "shows, shown with the button.")
                        .required(NAME_ARG, QUERY_ARG, QUERY_TYPE_ARG, PREVIEW_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final String name = stringArg(request, NAME_ARG);
        // Laid out as people write queries, as it is what an Embedded Query will hold.
        final String query = StroomQlFormatter.format(rawArg(request, QUERY_ARG));
        final String queryType = stringArg(request, QUERY_TYPE_ARG);
        if (name == null || name.length() > MAX_NAME_CHARS || name.chars().anyMatch(Character::isISOControl)) {
            throw new WorkspaceException("Give a name for the dashboard, of at most " + MAX_NAME_CHARS
                                         + " characters on one line.");
        }
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give the query's StroomQL.");
        }
        if (query.length() > limits().getMaxQueryLength()) {
            throw new WorkspaceException("The query may be at most " + limits().getMaxQueryLength()
                                         + " characters.");
        }
        if (!(argsMap(request).get(PREVIEW_ARG) instanceof final Boolean preview)) {
            throw new WorkspaceException("Say whether the user wants a data preview (true or false). If they "
                                         + "haven't said, ask them with ask_user.");
        }
        final ToolRunContext context = ToolRunContext.current().orElse(null);
        if (context == null) {
            return "Error: a button can only be offered while answering a question in a chat.";
        }
        final QueryService queryService = queryServiceProvider.get();
        final DocRef dataSource = queryService.getReferencedDataSource(query)
                .filter(dataSourceAccess::canUse)
                .orElse(null);
        if (dataSource == null) {
            return ValidateStroomQlTool.NO_DATA_SOURCE;
        }
        final ValidateExpressionResult validation = queryService.validateQuery(query);
        if (validation == null || !validation.isOk()) {
            return "The query is not valid, so no button was offered: "
                   + NullSafe.nonBlankStringElse(NullSafe.get(validation, ValidateExpressionResult::getString),
                    "the parser gave no reason") + "\nFix it and propose it again.";
        }
        final Built built;
        try {
            built = dashboardBuilder.build(name, query, queryType, preview);
        } catch (final IllegalArgumentException e) {
            return "No button was offered: " + e.getMessage();
        }

        final Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", "create-dashboard-" + SEQUENCE.incrementAndGet());
        action.put("action", UserActions.CREATE_DASHBOARD);
        action.put("name", name);
        action.put("query", query.strip());
        action.put("query_type", built.queryType());
        action.put("preview", preview);
        if (!built.outline().isEmpty()) {
            action.put("outline", built.outline());
        }
        NullSafe.consume(stringArg(request, DESCRIPTION_ARG), description -> action.put("description", description));
        context.offerUserAction(request.id(), action);
        return "The user has been offered a 'Create dashboard…' button for '" + name + "', reading "
               + DataSourceAccess.describe(dataSource) + ": " + built.describe() + ". Nothing is created until "
               + "they click it and confirm Stroom's New Dashboard dialog, which lets them choose the folder; then "
               + "the dashboard opens in a new tab, ready to run, and is attached to the chat, so they can ask you to "
               + "refine it (propose_dashboard_change). Tell them about the button rather than saying the dashboard "
               + "was created.";
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
