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
import stroom.ai.impl.tools.AgentInstructions.Ignored;
import stroom.ai.impl.tools.AgentInstructions.Standing;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// `list_data_sources` - the data sources (views, indexes, Elastic and Solr indexes and so on) the user can run
/// queries on, as the query editor's help lists them, each checked for Use. Logged as a View of each one listed.
class ListDataSourcesTool extends AbstractWorkspaceTool {

    static final String NAME = "list_data_sources";
    /// What the model is reminded of when more than one data source is listed.
    static final String CHOOSE_NOTE = "If more than one of these could hold what the user asked for, read each "
                                      + "one's documentation with describe_data_source (it says what their fields "
                                      + "hold, which their names may not), then ask the user which to use with "
                                      + "ask_user (one option for each, with what each holds) rather than "
                                      + "choosing one yourself, unless they have already said. If it isn't clear "
                                      + "whether they want the search run or a Query document made, ask that in "
                                      + "the same ask_user call.";
    static final String FILTER_ARG = "filter";
    static final String INCLUDE_IGNORED_ARG = "includeIgnored";
    /// The most data sources listed in one call.
    static final int MAX_LISTED = 200;
    /// Documentation is given for each data source only when there are no more than this many.
    static final int MAX_WITH_DOCUMENTATION = 25;
    /// The most of each data source's documentation given.
    static final int DOCUMENTATION_CHARS = 200;

    private final DataSourceAccess dataSourceAccess;
    private final AgentInstructions agentInstructions;
    private final AiAuditLog auditLog;

    @Inject
    ListDataSourcesTool(final ChatWorkspace workspace,
                        final DataSourceAccess dataSourceAccess,
                        final AgentInstructions agentInstructions,
                        final AiAuditLog auditLog) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.agentInstructions = agentInstructions;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE)
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
                .description(ToolDescription.builder()
                        .general("Lists the data sources the user can run StroomQL queries on (views, indexes, "
                                 + "Elastic and Solr indexes and so on): name, type and uuid, with a line of their "
                                 + "documentation for a short list.")
                        .chatOnly(" Use describe_data_source for a data source's fields before writing a query.")
                        .general(" If more than one could hold what the user asked for, ask them which to use")
                        .chatOnly(" with ask_user")
                        .general(" rather than choosing one yourself. Data sources in folders an AGENTS doc says to "
                                 + "ignore (e.g. an Archive) are left out, and the AGENTS docs that apply to those "
                                 + "listed are given, e.g. which fields to query for what.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FILTER_ARG, "Optional. Only data sources whose name or type contains "
                                                       + "this, ignoring case.")
                        .addBooleanProperty(INCLUDE_IGNORED_ARG, "Optional. True to list data sources in ignored "
                                                                 + "folders too, only when the user asks for them.")
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String filter = NullSafe.get(stringArg(request, FILTER_ARG), text -> text.toLowerCase(Locale.ROOT));
        final boolean includeIgnored = booleanArg(request, INCLUDE_IGNORED_ARG, false);
        final List<DocRef> found = dataSourceAccess.listUsable().stream()
                .filter(docRef -> filter == null
                                  || NullSafe.string(docRef.getName()).toLowerCase(Locale.ROOT).contains(filter)
                                  || NullSafe.string(docRef.getType()).toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        final Standing standing = agentInstructions.load(context.session(), "Standing instructions read by the " + NAME
                                                                 + " tool");
        final List<DocRef> matching = new ArrayList<>();
        final Set<String> ignoredBy = new LinkedHashSet<>();
        int ignored = 0;
        for (final DocRef docRef : found) {
            final Optional<Ignored> ignoring = standing.ignoresAny()
                    ? standing.ignored(agentInstructions.folderOf(docRef))
                    : Optional.empty();
            if (ignoring.isPresent() && !includeIgnored) {
                ignored++;
                ignoredBy.add(ignoring.get().describe());
            } else {
                matching.add(docRef);
            }
        }
        final String ignoredNote = ignored == 0
                ? ""
                : "\n" + ignored + " data source(s) in folders an " + AgentInstructions.DOC_NAME + " doc says to "
                  + "ignore were left out: " + String.join(", ", ignoredBy) + ". Pass " + INCLUDE_IGNORED_ARG
                  + " only if the user asks for those.";
        if (matching.isEmpty()) {
            return ToolResult.text((filter == null
                    ? "There are no data sources you can use."
                    : "There are no data sources you can use matching '" + filter + "'.") + ignoredNote);
        }
        final List<DocRef> listed = matching.subList(0, Math.min(matching.size(), MAX_LISTED));
        auditLog.documentsViewed(context.session(), listed, "Listed by the " + NAME + " tool");
        final boolean withDocumentation = listed.size() <= MAX_WITH_DOCUMENTATION;
        final StringBuilder sb = new StringBuilder("Data sources you can use (")
                .append(matching.size()).append("):\n");
        for (final DocRef docRef : listed) {
            sb.append("- ").append(DataSourceAccess.describe(docRef));
            if (withDocumentation) {
                dataSourceAccess.documentation(docRef)
                        .map(ListDataSourcesTool::firstLine)
                        .ifPresent(line -> sb.append(": ").append(line));
            }
            sb.append('\n');
        }
        if (matching.size() > listed.size()) {
            sb.append("(").append(matching.size() - listed.size())
                    .append(" more not shown. Give a filter to narrow the list.)\n");
        }
        if (ignored > 0) {
            sb.append(ignoredNote.strip()).append('\n');
        }
        if (matching.size() > 1) {
            sb.append(CHOOSE_NOTE).append('\n');
        }
        sb.append(standing.note(context.links(),
                listed.stream().map(agentInstructions::folderOf).collect(Collectors.toSet())));
        return ToolResult.text(sb.toString().stripTrailing());
    }

    /// @return The first line of a data source's documentation that has text, cut short if long.
    static String firstLine(final String documentation) {
        final String line = documentation.lines()
                .map(String::strip)
                .map(text -> text.replaceFirst("^#+\\s*", ""))
                .filter(text -> !text.isEmpty())
                .findFirst()
                .orElse("");
        return line.length() > DOCUMENTATION_CHARS
                ? line.substring(0, DOCUMENTATION_CHARS) + "…"
                : line;
    }

    @Override
    public boolean readsUntrustedContent() {
        // Names and documentation are written by users.
        return true;
    }
}
