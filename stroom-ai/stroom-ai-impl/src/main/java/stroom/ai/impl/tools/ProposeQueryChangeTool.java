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

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_query_change` - while the tab the user attached is a StroomQL Query, offers them an "Apply to query" button
/// for a revised query, which puts it in the tab's editor, unsaved, with "Undo apply" after. The query is laid out
/// a clause a line ([StroomQlFormatter]), must read a data source the user can use, and must be valid, as checked by
/// Stroom's parser. It is applied only while the editor still holds the query it was proposed against (the tab's
/// latest snapshot, unsaved edits included), and applying needs Edit on the Query, checked when the user clicks.
class ProposeQueryChangeTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_query_change";
    static final String QUERY_ARG = "query";
    static final String SUMMARY_ARG = "summary";
    static final int MAX_SUMMARY_CHARS = 500;
    static final String NOT_A_QUERY = "No StroomQL Query tab is attached to the chat, so no change was proposed. Give "
                                      + "the query as a code block to copy, or propose_query for a new Query; or ask "
                                      + "the user to attach the Query's tab (+ by the chat's input) and ask again.";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TabSnapshots tabSnapshots;
    private final DataSourceAccess dataSourceAccess;
    private final DocumentAccess documentAccess;
    private final Provider<QueryService> queryServiceProvider;

    @Inject
    ProposeQueryChangeTool(final ChatWorkspace workspace,
                           final TabSnapshots tabSnapshots,
                           final DataSourceAccess dataSourceAccess,
                           final DocumentAccess documentAccess,
                           final Provider<QueryService> queryServiceProvider) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.dataSourceAccess = dataSourceAccess;
        this.documentAccess = documentAccess;
        this.queryServiceProvider = queryServiceProvider;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE)
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
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .chatOnly("While the tab the user attached is a StroomQL Query, offers them an 'Apply to "
                                  + "query' button that puts a revised query in that tab's editor, unsaved; they can "
                                  + "undo it. ")
                        .general("Give the whole revised query. It is checked")
                        .chatOnly(" as propose_query's is")
                        .general(", and laid out a clause a line.")
                        .chatOnly(" Nothing changes until the user clicks the button. For a new Query, use "
                                  + "propose_query.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The whole revised StroomQL.")
                        .addStringProperty(SUMMARY_ARG, ToolDescription.builder()
                                .general("A sentence saying what the change does and why")
                                .chatOnly(", shown with the button")
                                .general(".")
                                .build())
                        .required(QUERY_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String given = rawArg(request, QUERY_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (given == null || given.isBlank()) {
            throw new WorkspaceException("Give the whole revised StroomQL.");
        }
        if (given.length() > limits(context).getMaxQueryLength()) {
            throw new WorkspaceException("The query may be at most " + limits(context).getMaxQueryLength()
                                         + " characters.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }

        // The tab the user attached to the chat.
        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef queryDoc = NullSafe.get(tab, StoredTabSnapshot::docRef);
        if (queryDoc == null || !QueryDoc.TYPE.equals(queryDoc.getType())) {
            return ToolResult.text(NOT_A_QUERY);
        }
        if (!documentAccess.canView(queryDoc)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final UnsavedPart base = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.QUERY).orElse(null);
        if (base == null || base.cut()) {
            return ToolResult.text(
                    "The tab's query isn't in its snapshot in full, so a change to it can't be proposed. Give the "
                    + "query as a code block to copy.");
        }

        // Laid out as people write queries, ending as the editor's does, as it is what the editor will hold.
        final String query = ProposeCodeChangeTool.withLineEndingOf(base.text(), StroomQlFormatter.format(given));
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
        final LineDiffResult diff = LineDiff.diff(base.text(), query);
        if (diff.isIdentical()) {
            return ToolResult.text("The revised query is the same as the tab's, so there is nothing to propose.");
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", ProposeCodeChangeTool.docRefMap(queryDoc));
        arguments.put("request", query);
        arguments.put("base", base.text());
        arguments.put("base_hash", ProposeCodeChangeTool.sha256(base.text()));
        NullSafe.consume(tab.hash(), hash -> arguments.put("snapshot_hash", hash));
        arguments.put("added", diff.getAddedLineCount());
        arguments.put("removed", diff.getRemovedLineCount());
        NullSafe.consume(summary, text -> arguments.put("summary", text));
        final ToolUserAction action = new ToolUserAction("apply-query-change-" + SEQUENCE.incrementAndGet(),
                UserActions.APPLY_QUERY_CHANGE, arguments);
        documentAccess.logView(context.session(), queryDoc,
                "Proposed a change to its query with the " + NAME + " tool");

        return ToolResult.text(
                "The user has been shown the revised query for Query '" + queryDoc.getName() + "', reading "
                + DataSourceAccess.describe(dataSource) + ", with an 'Apply to query' button. Nothing changes until "
                + "they click it, which puts it in the tab's editor, unsaved; they can undo it. Tell them about the "
                + "button rather than saying the query was changed. The query as it will be applied:\n" + query)
                .withUserAction(action);
    }
}
