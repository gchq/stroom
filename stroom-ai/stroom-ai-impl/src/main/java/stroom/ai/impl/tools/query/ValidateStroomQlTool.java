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

package stroom.ai.impl.tools.query;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
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

import java.util.List;
import java.util.stream.Collectors;

/// `validate_stroomql` - checks a StroomQL query without running it, as the query editor does before a rule is made
/// from a query: OK, or the parser's error. QueryService.validateQuery makes no check of its own, so the tool first
/// finds the data sources the query reads (the from source, and each joined one: join lines and where subqueries) and
/// checks Use on each, and treats one the user can't use as one that doesn't exist. Logged as a Process (validate)
/// event naming the data sources.
public class ValidateStroomQlTool extends AbstractWorkspaceTool {

    static final String NAME = "validate_stroomql";
    static final String QUERY_ARG = "query";
    public static final String NO_DATA_SOURCE = StroomQlUseCheck.NO_DATA_SOURCE;
    static final String NO_JOINED_DATA_SOURCE = StroomQlUseCheck.NO_JOINED_DATA_SOURCE;

    private final StroomQlUseCheck useCheck;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;

    @Inject
    ValidateStroomQlTool(final ChatWorkspace workspace,
                         final StroomQlUseCheck useCheck,
                         final Provider<QueryService> queryServiceProvider,
                         final AiAuditLog auditLog) {
        super(workspace);
        this.useCheck = useCheck;
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
                .description("Checks a StroomQL query without running it: OK (with the data sources it reads, "
                             + "joined ones too, and whether it groups rows), or the parser's error. Validate a "
                             + "query before you show or propose it.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The StroomQL query (a field whose name has a space is "
                                + "${Field Name}; double quotes give text).")
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
        final StroomQlUseCheck.Checked checked = useCheck.check(query);
        if (!checked.usable()) {
            auditLog.processedDocuments(context.session(), checked.audited(), NAME, query,
                    "Not validated: " + (checked.first() == null
                            ? "no data source the user can use"
                            : "a joined data source the user can't use"), false);
            return ToolResult.text(checked.refusal());
        }
        final DocRef dataSource = checked.first();
        final List<DocRef> joined = checked.joined();
        final List<DocRef> read = checked.audited();
        final QueryService queryService = queryServiceProvider.get();
        final ValidateExpressionResult result = queryService.validateQuery(query);
        final boolean ok = result != null && result.isOk();
        final String outcome = ok
                ? "OK"
                : "Invalid: " + NullSafe.get(result, ValidateExpressionResult::getString);
        auditLog.processedDocuments(context.session(), read, NAME, query, outcome, true);
        if (ok) {
            return ToolResult.text("OK. The query is valid and reads " + DataSourceAccess.describe(dataSource)
                                   + (joined.isEmpty()
                                    ? ""
                                    : ", joined with " + joined.stream()
                                            .map(DataSourceAccess::describe)
                                            .collect(Collectors.joining(", ")))
                                   + (result.isGroupBy()
                                    ? ". It groups rows."
                                    : ".")
                                   + QuotedSelectFields.note(queryService, dataSource, query));
        }
        return ToolResult.text("The query is not valid: "
                               + StroomQlErrorHints.appendTo(NullSafe.nonBlankStringElse(NullSafe.get(result,
                                ValidateExpressionResult::getString), "the parser gave no reason"), query)
                               + "\nFix it and validate it again.");
    }

    @Override
    public boolean readsUntrustedContent() {
        // It names the data source the query reads as Stroom holds it (a document name, written by whoever made
        // the document), and the parser's errors are not known to quote only what the model wrote.
        return true;
    }
}
