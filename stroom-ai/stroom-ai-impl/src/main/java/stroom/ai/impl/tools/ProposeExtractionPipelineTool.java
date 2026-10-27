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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.extraction.ExtractionField;
import stroom.ai.impl.extraction.ExtractionPlan;
import stroom.ai.impl.extraction.ExtractionPreview;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AskStroomAiContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.query.api.TableSettings;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_extraction_pipeline` - offers the user a button to make the search extraction the agent tried with
/// preview_extraction for a table on the dashboard they attached: an XSLT, written by Stroom from the fields, and a
/// pipeline using it (a child of the Search Extraction template, or one of its own). It changes nothing itself.
/// The button shows exactly what would be written, with the name and folder the user chooses in Stroom's New
/// dialog, for them to confirm; only then is it written, as the user, and the table set to use it, with a column
/// for each new field, as unsaved changes.
///
/// For a table with no extraction pipeline of its own. It is checked as preview_extraction checks it, but without
/// stepping: every field the table's columns use must be given, as turning extraction on fills every value from it.
class ProposeExtractionPipelineTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_extraction_pipeline";
    static final String TABLE_ARG = "table";
    static final String FIELDS_ARG = "fields";
    static final String NAMESPACE_ARG = "namespace";
    static final String PIPELINE_NAME_ARG = "name";
    static final String SUMMARY_ARG = "summary";
    static final int MAX_NAME_CHARS = 100;
    static final int MAX_NAMESPACE_CHARS = 200;
    static final int MAX_SUMMARY_CHARS = 500;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;
    private final AiAuditLog auditLog;

    @Inject
    ProposeExtractionPipelineTool(final ChatWorkspace workspace,
                                  final TabSnapshots tabSnapshots,
                                  final DocumentAccess documentAccess,
                                  final AiAuditLog auditLog) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
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
                        .general("Offers the user a button to make a search extraction for a dashboard table, "
                                 + "to add values from its events to it: an XSLT, which Stroom writes from the "
                                 + "fields, and a pipeline using it. The button shows exactly what would be written, "
                                 + "with the name and folder the user chooses, for them to confirm; only then is it "
                                 + "written, and the table set to use it, with a column for each new field, unsaved. "
                                 + "Nothing changes until they confirm it.")
                        .chatOnly(" Give the fields and namespace you tried with preview_extraction, once the user "
                                  + "has seen its preview and agreed them, for a table with no extraction pipeline "
                                  + "of its own. Tell the user about the button rather than saying it was made.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(TABLE_ARG, "The dashboard table, by its name as the user sees it.")
                        .addProperty(FIELDS_ARG, ArraySchema.builder()
                                .description("The fields to extract, besides StreamId and EventId, as tried.")
                                .items(ObjectSchema.builder()
                                        .addStringProperty("name", "The field's name.")
                                        .addStringProperty("xpath", "The XPath of its value, from the event "
                                                                    + "element.")
                                        .addEnumProperty("type", ExtractionField.TYPES, "Its type (default text).")
                                        .required("name", "xpath")
                                        .build())
                                .build())
                        .addStringProperty(NAMESPACE_ARG, "The events' namespace, as the preview gave it (blank "
                                                          + "for none).")
                        .addStringProperty(PIPELINE_NAME_ARG, "The name to suggest for the new pipeline and its "
                                                              + "XSLT, e.g. 'Logons extraction'; the user may "
                                                              + "change it.")
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what it adds to the table, shown with "
                                                        + "the button.")
                        .required(TABLE_ARG, FIELDS_ARG, NAMESPACE_ARG, PIPELINE_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final List<ExtractionField> fields = ExtractionField.parse(argsMap(request).get(FIELDS_ARG));
        final String tableName = stringArg(request, TABLE_ARG);
        final String namespace = NullSafe.string(stringArg(request, NAMESPACE_ARG)).strip();
        final String name = NullSafe.get(stringArg(request, PIPELINE_NAME_ARG), String::strip);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (tableName == null) {
            throw new WorkspaceException("Give the table, by its name as the user sees it.");
        }
        if (namespace.length() > MAX_NAMESPACE_CHARS || namespace.chars().anyMatch(Character::isWhitespace)
            || namespace.chars().anyMatch(Character::isISOControl)) {
            throw new WorkspaceException("Give the events' namespace as the preview gave it, e.g. event-logging:3.");
        }
        if (NullSafe.isBlankString(name) || name.length() > MAX_NAME_CHARS
            || name.chars().anyMatch(Character::isISOControl)) {
            throw new WorkspaceException("Give the new pipeline a name of up to " + MAX_NAME_CHARS + " characters.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }

        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final DocRef dashboard = NullSafe.get(tab, StoredTabSnapshot::docRef);
        if (dashboard == null || !DashboardDoc.TYPE.equals(dashboard.getType())) {
            return ToolResult.text(TabSnapshots.notAttached(tab,
                    tabSnapshots.latestOfType(chatId, Set.of(DashboardDoc.TYPE)).orElse(null), "dashboard",
                    "Nothing was offered."));
        }
        if (!documentAccess.canView(dashboard)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final AiTabSearch table = CurrentResultsTool.chooseTable(NullSafe.list(tab.searches()), tableName);
        final AskStroomAiContext search = table == null
                ? null
                : table.getLive() != null
                        ? table.getLive()
                        : table.getResults();
        final TableSettings settings = search == null
                ? null
                : ResultsRequests.tableSettings(search);
        if (settings == null || NullSafe.isBlankString(table.getComponentId())
            || ProposeDashboardChangeTool.isEmbeddedQuery(table)) {
            return ToolResult.text("Nothing was offered: the dashboard has no table '" + tableName + "' whose search "
                                   + "is known (an embedded query's StroomQL makes its results, so it has no "
                                   + "extraction).");
        }
        if (settings.extractValues() && settings.getExtractionPipeline() != null) {
            return ToolResult.text("Nothing was offered: table '" + tableName + "' extracts its values with "
                                   + "pipeline '" + NullSafe.nonBlankStringElse(settings.getExtractionPipeline()
                    .getName(), settings.getExtractionPipeline().getUuid()) + "' already. Changing an extraction "
                                   + "pipeline isn't supported yet.");
        }

        final Map<String, String> columns = ResultsRequests.columnExpressions(search);
        final List<String> problems = new ArrayList<>(ExtractionPreview.blankColumns(fields, columns));
        final List<String> newColumns = new ArrayList<>();
        for (final ExtractionField field : fields) {
            final boolean shown = columns.values().stream()
                    .anyMatch(expression -> ExtractionPreview.fieldsOf(expression).stream()
                            .anyMatch(field.name()::equalsIgnoreCase));
            if (shown) {
                continue;
            }
            if (columns.keySet().stream().anyMatch(column -> column.equalsIgnoreCase(field.name()))) {
                problems.add("The table has a column named '" + field.name() + "' already, showing something "
                             + "else: name the field otherwise.");
            } else {
                newColumns.add(field.name());
            }
        }
        if (!problems.isEmpty()) {
            return ToolResult.text("Nothing was offered, as the extraction can't be used as it is:\n- "
                                   + String.join("\n- ", problems) + "\nCorrect it, try it again with "
                                   + "preview_extraction, and propose it again.");
        }

        final ExtractionPlan plan = new ExtractionPlan(NullSafe.nonBlankStringElse(table.getName(),
                table.getComponentId()), table.getComponentId(), namespace, fields, newColumns);
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", Map.of("type", dashboard.getType(), "uuid", dashboard.getUuid()));
        arguments.put("name", name);
        arguments.put("plan", plan.toJson());
        NullSafe.consume(summary, text -> arguments.put("summary", text.strip()));
        final ToolUserAction action = new ToolUserAction("create-extraction-pipeline-" + SEQUENCE.incrementAndGet(),
                UserActions.CREATE_EXTRACTION_PIPELINE, arguments);
        auditLog.documentViewed(context.session(), dashboard, "Proposed an extraction pipeline for table '"
                                                              + tableName + "' with the " + NAME + " tool");
        final String names = String.join(", ", fields.stream().map(ExtractionField::name).toList());
        return ToolResult.text("The user has been shown a 'Create extraction pipeline' button for table '"
                               + tableName + "' on dashboard '" + dashboard.getName() + "', extracting " + names
                               + (newColumns.isEmpty()
                ? ""
                : ", adding column(s) " + String.join(", ", newColumns)) + ". When they click it, they choose "
                               + "the name (suggested '" + name + "') and folder of the new pipeline and its XSLT, "
                               + "and confirm exactly what is written; only then is it written, and the table set "
                               + "to use it, unsaved. Nothing changes until they confirm it. Tell them about the "
                               + "button rather than saying it was made.")
                .withUserAction(action);
    }
}
