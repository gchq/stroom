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
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Set;

/// `describe_data_source` - a data source's fields, as the query editor's help gives them: each field's type, the
/// conditions it supports and whether it can be queried, and the data source's time field. QueryService.findFields
/// makes no check of its own, so the tool checks Use on the data source first, and treats one the user can't use
/// as one that doesn't exist. Logged as a View of the data source.
class DescribeDataSourceTool extends AbstractWorkspaceTool {

    static final String NAME = "describe_data_source";
    static final String DATA_SOURCE_ARG = "data_source";
    /// The most fields given.
    static final int MAX_FIELDS = 1_000;

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AgentInstructions agentInstructions;
    private final DataSourceDocumentation dataSourceDocumentation;
    private final AiAuditLog auditLog;

    @Inject
    DescribeDataSourceTool(final ChatWorkspace workspace,
                           final DataSourceAccess dataSourceAccess,
                           final Provider<QueryService> queryServiceProvider,
                           final AgentInstructions agentInstructions,
                           final DataSourceDocumentation dataSourceDocumentation,
                           final AiAuditLog auditLog) {
        super(workspace);
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.agentInstructions = agentInstructions;
        this.dataSourceDocumentation = dataSourceDocumentation;
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
                        .general("Gives a data source's fields: each field's type, the conditions it supports and "
                                 + "whether it can be queried, and the data source's time field. Use it to check "
                                 + "the field names in a StroomQL query (from \"<data source name>\" ...) or a "
                                 + "search of an index. Also gives its documentation, which says what its fields "
                                 + "hold when their names don't (e.g. the field mapping of the pipeline that feeds "
                                 + "it): use it to choose a data source and its fields. The AGENTS docs that apply "
                                 + "where the data source is are given too: follow what they say about its fields, "
                                 + "e.g. which field holds the acting user. For a Lucene or Elastic index, it says "
                                 + "which searches through Stroom would mislead (e.g. CONTAINS on Elasticsearch "
                                 + "finds nothing), and what to search instead.")
                        .chatOnly(" To find an event the user describes in an index, use search_events with "
                                  + "index.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(DATA_SOURCE_ARG, ToolDescription.builder()
                                .general("The data source's name or uuid")
                                .chatOnly(", as list_data_sources gives it")
                                .general(".")
                                .build())
                        .required(DATA_SOURCE_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String given = stringArg(request, DATA_SOURCE_ARG);
        if (given == null) {
            throw new WorkspaceException("Give the data source's name or uuid.");
        }
        final List<DocRef> found = dataSourceAccess.find(given);
        if (found.isEmpty()) {
            return ToolResult.text(DataSourceAccess.NOT_FOUND);
        }
        if (found.size() > 1) {
            final StringBuilder sb = new StringBuilder("Several data sources you can use are called '")
                    .append(given).append("'. Give the uuid of the one you mean:\n");
            found.forEach(docRef -> sb.append("- ").append(DataSourceAccess.describe(docRef)).append('\n'));
            return ToolResult.text(sb.toString().stripTrailing());
        }
        final DocRef dataSource = found.getFirst();
        auditLog.documentViewed(context.session(), dataSource, "Fields described by the " + NAME + " tool");
        final ResultPage<QueryField> fields = queryServiceProvider.get().findFields(new FindFieldCriteria(
                new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null, null));
        final List<QueryField> values = NullSafe.list(NullSafe.get(fields, ResultPage::getValues));
        final StringBuilder sb = new StringBuilder(DataSourceAccess.describe(dataSource)).append('\n');
        dataSourceAccess.timeField(dataSource)
                .ifPresent(field -> sb.append("Time field: ").append(field.getFldName()).append('\n'));
        if (values.isEmpty()) {
            sb.append("It has no fields you can see.");
            return ToolResult.text(sb.append(contextNote(context, dataSource)).toString());
        }
        sb.append("Fields (").append(values.size()).append("):\n");
        for (final QueryField field : values) {
            sb.append("- ").append(field.getFldName()).append(" (")
                    .append(NullSafe.get(field.getFldType(), Object::toString)).append(')');
            if (!field.queryable()) {
                sb.append(", not queryable");
            }
            if (field.getConditionSet() != null) {
                sb.append(", conditions: ").append(field.getConditionSet());
            }
            if (NullSafe.isNonBlankString(field.getDocRefType())) {
                sb.append(", refers to ").append(field.getDocRefType()).append(" documents");
            }
            sb.append('\n');
        }
        if (values.size() >= MAX_FIELDS) {
            sb.append("(Only the first ").append(MAX_FIELDS).append(" fields are given.)\n");
        }
        MisleadingSearches.backendOf(dataSource).ifPresent(backend -> sb.append(searchingNote(backend, values)));
        sb.append(contextNote(context, dataSource));
        return ToolResult.text(sb.toString().stripTrailing());
    }

    /// What a search of a Lucene or Elastic index through Stroom gets wrong without an error (found against Stroom
    /// 7.13), so a query or search on it avoids it.
    ///
    /// @param backend [MisleadingSearches#ELASTICSEARCH] or [MisleadingSearches#LUCENE].
    /// @param fields  The index's fields.
    /// @return The note, on lines of its own.
    static String searchingNote(final String backend, final List<QueryField> fields) {
        final StringBuilder sb = new StringBuilder("\nSearching it through Stroom (");
        if (MisleadingSearches.ELASTICSEARCH.equals(backend)) {
            sb.append("Elasticsearch): STARTS_WITH and CONTAINS find nothing (use EQUALS with a * wildcard, e.g. "
                      + "'adm*' or '*err*', or MATCHES_REGEX); IS_NULL and IS_NOT_NULL match every document (use "
                      + "EQUALS '*' for any value, NOT_EQUALS '*' for none)");
            final Set<String> ipFields = MisleadingSearches.ipFields(fields);
            if (!ipFields.isEmpty()) {
                sb.append("; on an ip field (")
                        .append(String.join(", ", ipFields))
                        .append(") a wildcard finds nothing: use a CIDR range, e.g. EQUALS '192.0.2.0/24'");
            }
        } else {
            sb.append("Lucene): STARTS_WITH and ENDS_WITH find nothing (use EQUALS with a * wildcard, e.g. 'ca*' "
                      + "or '*ol'); CONTAINS finds nothing on a keyword field (use EQUALS '*aro*'), nor a range on "
                      + "a text field (use EQUALS with a wildcard, an address range as '10.1.*', or IN with the "
                      + "values); IN finds nothing when a value holds a space (search each such value with "
                      + "EQUALS)");
        }
        return sb.append("; IN takes values separated by commas. A field that is not queryable is found by no "
                         + "search.\n").toString();
    }

    // What the people and builds that look after the data source say about it: its documentation (e.g. that
    // document.description is a tweet's text, from the pipeline that feeds it) and its AGENTS docs (e.g. which field
    // holds the acting user). Given even when its fields can't be listed.
    private String contextNote(final ToolCallContext context, final DocRef dataSource) {
        return DataSourceDocumentation.note(context.links(), dataSourceDocumentation.find(context.session(),
                       dataSource, "Documentation read by the " + NAME + " tool"))
               + agentInstructions.load(context.session(), "Standing instructions read by the " + NAME + " tool")
                       .note(context.links(), List.of(agentInstructions.folderOf(dataSource)));
    }

    @Override
    public boolean readsUntrustedContent() {
        // Field names come from users' documents.
        return true;
    }
}
