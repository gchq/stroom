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

import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.impl.QueryService;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_query` - offers the user a "Create query…" button for a StroomQL query the agent has written. It
/// creates nothing itself: the button opens Stroom's own New Query dialog with the suggested name, and the query is
/// only created, with the agent's text, if the user confirms the dialog (which checks their permission to create it
/// in the folder they choose, and logs it as Stroom logs any new document). The query must be valid and read a data
/// source the user can use.
class ProposeQueryTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_query";
    static final String NAME_ARG = "name";
    static final String QUERY_ARG = "query";
    static final String DESCRIPTION_ARG = "description";
    static final int MAX_NAME_CHARS = 200;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;

    @Inject
    ProposeQueryTool(final ChatWorkspace workspace,
                     final DataSourceAccess dataSourceAccess,
                     final Provider<QueryService> queryServiceProvider) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .build();
    }

    @Override
    public boolean isChatOnly() {
        // It proposes a query to the user in the chat, for them to open.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Offers the user a 'Create query…' button for a new StroomQL Query document. It "
                                 + "creates nothing itself")
                        .chatOnly(": the button opens Stroom's New Query dialog with the name you suggest, and "
                                  + "once the user creates it there, the query is saved with your StroomQL and "
                                  + "opened")
                        .general(". Validate the query first. If more than one data source could hold what the "
                                 + "user asked for, ask them which to use")
                        .chatOnly(" with ask_user")
                        .general(" first.")
                        .chatOnly(" To change the query open in the tab the user attached, use propose_query_change "
                                  + "instead.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(NAME_ARG, "The name to suggest for the new query.")
                        .addStringProperty(QUERY_ARG, "The StroomQL.")
                        .addStringProperty(DESCRIPTION_ARG, ToolDescription.builder()
                                .general("Optional. A sentence saying what the query finds")
                                .chatOnly(", shown with the button")
                                .general(".")
                                .build())
                        .required(NAME_ARG, QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String name = stringArg(request, NAME_ARG);
        // Laid out as people write queries, as it is what the new Query document will hold.
        final String query = StroomQlFormatter.format(rawArg(request, QUERY_ARG));
        if (name == null || name.length() > MAX_NAME_CHARS || name.chars().anyMatch(Character::isISOControl)) {
            throw new WorkspaceException("Give a name for the query, of at most " + MAX_NAME_CHARS
                                         + " characters on one line.");
        }
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give the query's StroomQL.");
        }
        if (query.length() > limits(context).getMaxQueryLength()) {
            throw new WorkspaceException("The query may be at most " + limits(context).getMaxQueryLength()
                                         + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }
        final QueryService queryService = queryServiceProvider.get();
        final DocRef dataSource = queryService.getReferencedDataSource(query)
                .filter(dataSourceAccess::canUse)
                .orElse(null);
        if (dataSource == null) {
            return ToolResult.text(ValidateStroomQlTool.NO_DATA_SOURCE);
        }
        final ValidateExpressionResult validation = queryService.validateQuery(query);
        if (validation == null || !validation.isOk()) {
            return ToolResult.text(
                    "The query is not valid, so no button was offered: "
                    + NullSafe.nonBlankStringElse(NullSafe.get(validation, ValidateExpressionResult::getString),
                     "the parser gave no reason") + "\nFix it and propose it again.");
        }
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("name", name);
        arguments.put("query", query.strip());
        NullSafe.consume(stringArg(request, DESCRIPTION_ARG), description -> arguments.put("description", description));
        final ToolUserAction action = new ToolUserAction("create-query-" + SEQUENCE.incrementAndGet(),
                UserActions.CREATE_QUERY, arguments);
        return ToolResult.text(
                "The user has been offered a 'Create query…' button for '" + name + "', reading "
                + DataSourceAccess.describe(dataSource) + ". Nothing is created until they click it and confirm "
                + "Stroom's New Query dialog, which lets them choose the folder; then the query opens in a new tab and "
                + "is attached to the chat, so they can ask you to refine it (propose_query_change). Tell them about "
                + "the button rather than saying the query was created.").withUserAction(action);
    }

    @Override
    public boolean readsUntrustedContent() {
        // It names the data source the query reads as Stroom holds it (a document name, written by whoever made
        // the document), and the parser's errors are not known to quote only what the model wrote.
        return true;
    }
}
