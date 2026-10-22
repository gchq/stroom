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
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Checks a StroomQL query without running it: OK (and whether it groups rows), or "
                             + "the parser's error. Validate a query before you show or propose it.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The StroomQL query.")
                        .required(QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String query = rawArg(request, QUERY_ARG);
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give a StroomQL query.");
        }
        final int maxQueryLength = limits(context).getMaxQueryLength();
        if (query.length() > maxQueryLength) {
            throw new WorkspaceException("The query may be at most " + maxQueryLength + " characters.");
        }
        final QueryService queryService = queryServiceProvider.get();
        final DocRef dataSource = usableDataSource(queryService, query).orElse(null);
        if (dataSource == null) {
            auditLog.processed(context.session(), null, NAME, query, "Not validated: no data source the user can use",
                    false);
            return ToolResult.text(NO_DATA_SOURCE);
        }
        final ValidateExpressionResult result = queryService.validateQuery(query);
        final boolean ok = result != null && result.isOk();
        final String outcome = ok
                ? "OK"
                : "Invalid: " + NullSafe.get(result, ValidateExpressionResult::getString);
        auditLog.processed(context.session(), dataSource, NAME, query, outcome, true);
        if (ok) {
            return ToolResult.text("OK. The query is valid and reads " + DataSourceAccess.describe(dataSource)
                                   + (result.isGroupBy()
                                    ? ". It groups rows."
                                    : "."));
        }
        return ToolResult.text("The query is not valid: "
                               + NullSafe.nonBlankStringElse(NullSafe.get(result, ValidateExpressionResult::getString),
                                "the parser gave no reason") + "\nFix it and validate it again.");
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
        // It names the data source the query reads as Stroom holds it (a document name, written by whoever made
        // the document), and the parser's errors are not known to quote only what the model wrote.
        return true;
    }
}
