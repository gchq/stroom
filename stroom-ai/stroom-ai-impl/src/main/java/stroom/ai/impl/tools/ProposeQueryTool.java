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
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Offers the user a 'Create query…' button for a new StroomQL Query document. It "
                             + "creates nothing itself: the button opens Stroom's New Query dialog with the name "
                             + "you suggest, and once the user creates it there, the query is saved with your "
                             + "StroomQL and opened. Validate the query first. If more than one data source could "
                             + "hold what the user asked for, ask them which to use with ask_user first. To change "
                             + "a query the user already has, give the revised StroomQL as a code block instead.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(NAME_ARG, "The name to suggest for the new query.")
                        .addStringProperty(QUERY_ARG, "The StroomQL.")
                        .addStringProperty(DESCRIPTION_ARG, "Optional. A sentence saying what the query finds, "
                                                            + "shown with the button.")
                        .required(NAME_ARG, QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final String name = stringArg(request, NAME_ARG);
        final String query = rawArg(request, QUERY_ARG);
        if (name == null || name.length() > MAX_NAME_CHARS || name.chars().anyMatch(Character::isISOControl)) {
            throw new WorkspaceException("Give a name for the query, of at most " + MAX_NAME_CHARS
                                         + " characters on one line.");
        }
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give the query's StroomQL.");
        }
        if (query.length() > limits().getMaxQueryLength()) {
            throw new WorkspaceException("The query may be at most " + limits().getMaxQueryLength()
                                         + " characters.");
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
        final Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", "create-query-" + SEQUENCE.incrementAndGet());
        action.put("action", UserActions.CREATE_QUERY);
        action.put("name", name);
        action.put("query", query.strip());
        NullSafe.consume(stringArg(request, DESCRIPTION_ARG), description -> action.put("description", description));
        context.offerUserAction(request.id(), action);
        return "The user has been offered a 'Create query…' button for '" + name + "', reading "
               + DataSourceAccess.describe(dataSource) + ". Nothing is created until they click it and confirm "
               + "Stroom's New Query dialog, which lets them choose the folder; then the query opens in a new tab. "
               + "Tell them about the button rather than saying the query was created.";
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
