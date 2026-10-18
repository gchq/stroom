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
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

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
    private final AiAuditLog auditLog;

    @Inject
    DescribeDataSourceTool(final ChatWorkspace workspace,
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
                .description("Gives a data source's fields: each field's type, the conditions it supports and "
                             + "whether it can be queried, and the data source's time field. Use it to check the "
                             + "field names in a StroomQL query (from \"<data source name>\" ...).")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(DATA_SOURCE_ARG, "The data source's name or uuid, as list_data_sources "
                                                            + "gives it.")
                        .required(DATA_SOURCE_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final String given = stringArg(request, DATA_SOURCE_ARG);
        if (given == null) {
            throw new WorkspaceException("Give the data source's name or uuid.");
        }
        final List<DocRef> found = dataSourceAccess.find(given);
        if (found.isEmpty()) {
            return DataSourceAccess.NOT_FOUND;
        }
        if (found.size() > 1) {
            final StringBuilder sb = new StringBuilder("Several data sources you can use are called '")
                    .append(given).append("'. Give the uuid of the one you mean:\n");
            found.forEach(docRef -> sb.append("- ").append(DataSourceAccess.describe(docRef)).append('\n'));
            return sb.toString().stripTrailing();
        }
        final DocRef dataSource = found.getFirst();
        auditLog.documentViewed(chatId, dataSource, "Fields described by the " + NAME + " tool");
        final ResultPage<QueryField> fields = queryServiceProvider.get().findFields(new FindFieldCriteria(
                new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null, null));
        final List<QueryField> values = NullSafe.list(NullSafe.get(fields, ResultPage::getValues));
        final StringBuilder sb = new StringBuilder(DataSourceAccess.describe(dataSource)).append('\n');
        dataSourceAccess.timeField(dataSource)
                .ifPresent(field -> sb.append("Time field: ").append(field.getFldName()).append('\n'));
        if (values.isEmpty()) {
            return sb.append("It has no fields you can see.").toString();
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
        return sb.toString().stripTrailing();
    }

    @Override
    public boolean readsUntrustedContent() {
        // Field names come from users' documents.
        return true;
    }
}
