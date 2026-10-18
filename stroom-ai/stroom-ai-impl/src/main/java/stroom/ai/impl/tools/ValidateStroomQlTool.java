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

import java.util.Optional;

/// `validate_stroomql` - checks a StroomQL query without running it, as the query editor does before a rule is made
/// from a query: OK, or the parser's error. QueryService.validateQuery makes no check of its own, so the tool first
/// finds the data source the query reads and checks Use on it, and treats one the user can't use as one that
/// doesn't exist. Logged as a Process (validate) event naming the data source.
class ValidateStroomQlTool extends AbstractWorkspaceTool {

    static final String NAME = "validate_stroomql";
    static final String QUERY_ARG = "query";
    static final String NO_DATA_SOURCE = "The query doesn't name a data source you can use: check its 'from' "
                                         + "clause against list_data_sources.";

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;

    @Inject
    ValidateStroomQlTool(final ChatWorkspace workspace,
                         final DataSourceAccess dataSourceAccess,
                         final Provider<QueryService> queryServiceProvider,
                         final AiAuditLog auditLog) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Checks a StroomQL query without running it: OK (and whether it groups rows), or "
                             + "the parser's error. Validate a query before you show or propose it.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The StroomQL query.")
                        .required(QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final String query = rawArg(request, QUERY_ARG);
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give a StroomQL query.");
        }
        final int maxQueryLength = limits().getMaxQueryLength();
        if (query.length() > maxQueryLength) {
            throw new WorkspaceException("The query may be at most " + maxQueryLength + " characters.");
        }
        final QueryService queryService = queryServiceProvider.get();
        final DocRef dataSource = usableDataSource(queryService, query).orElse(null);
        if (dataSource == null) {
            auditLog.processed(chatId, null, NAME, query, "Not validated: no data source the user can use", false);
            return NO_DATA_SOURCE;
        }
        final ValidateExpressionResult result = queryService.validateQuery(query);
        final boolean ok = result != null && result.isOk();
        final String outcome = ok
                ? "OK"
                : "Invalid: " + NullSafe.get(result, ValidateExpressionResult::getString);
        auditLog.processed(chatId, dataSource, NAME, query, outcome, true);
        if (ok) {
            return "OK. The query is valid and reads " + DataSourceAccess.describe(dataSource)
                   + (result.isGroupBy()
                    ? ". It groups rows."
                    : ".");
        }
        return "The query is not valid: "
               + NullSafe.nonBlankStringElse(NullSafe.get(result, ValidateExpressionResult::getString),
                "the parser gave no reason") + "\nFix it and validate it again.";
    }

    // The data source the query reads, if it names one the user can use.
    private Optional<DocRef> usableDataSource(final QueryService queryService, final String query) {
        try {
            return queryService.getReferencedDataSource(query)
                    .filter(dataSourceAccess::canUse);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean readsUntrustedContent() {
        // The parser's errors quote the query, which the model wrote.
        return false;
    }
}
