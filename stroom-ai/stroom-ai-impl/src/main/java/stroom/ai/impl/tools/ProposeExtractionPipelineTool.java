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
import stroom.ai.impl.extraction.ExtractionChange;
import stroom.ai.impl.extraction.ExtractionField;
import stroom.ai.impl.extraction.ExtractionPipelines;
import stroom.ai.impl.extraction.ExtractionPlan;
import stroom.ai.impl.extraction.ExtractionPlan.Existing;
import stroom.ai.impl.extraction.ExtractionPreview;
import stroom.ai.impl.extraction.PreviewRecord;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AskStroomAiContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.api.TableSettings;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_extraction_pipeline` - offers the user a button to make the search extraction the agent tried with
/// preview_extraction for a table on the dashboard they attached: an XSLT, written by Stroom from the fields, and a
/// pipeline using it (a child of the Search Extraction template, or one of its own). It changes nothing itself.
/// When the user clicks the button and chooses its name and folder in Stroom's New dialog, it is written as the
/// button prepared it, as the user, and the table set to use it, with a column for each new field, as unsaved
/// changes.
///
/// For a table with an extraction pipeline already, it offers two buttons: one to change that pipeline's XSLT in
/// place (see [ExtractionChange]), if the user can edit it, shown as a diff with the other dashboards and queries
/// that use it; and one to save a new pipeline, a child of it with its own XSLT, for this table only.
///
/// It is checked as preview_extraction checks it, but without stepping: every field the table's columns use must
/// be given, as turning extraction on fills every value from it.
class ProposeExtractionPipelineTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_extraction_pipeline";
    static final String TABLE_ARG = "table";
    static final String FIELDS_ARG = "fields";
    static final String PIPELINE_NAME_ARG = "name";
    static final String SUMMARY_ARG = "summary";
    static final int MAX_NAME_CHARS = 100;
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
                                 + "fields, and a pipeline using it. When the user clicks it and chooses the name and "
                                 + "folder, it is written, and the table set to use it, with a column for each new "
                                 + "field, unsaved. Nothing changes unless they do. For a table with an extraction "
                                 + "pipeline already, it offers to change that pipeline in place, or to save a new one (a "
                                 + "child of it): give every field, if Stroom AI wrote its XSLT; only those to add, "
                                 + "if it was written by hand.")
                        .chatOnly(" Give the fields exactly as you last tried them with preview_extraction for this "
                                  + "table (it is refused otherwise), once the user "
                                  + "has seen its preview and agreed them. Tell the user about the button(s) rather "
                                  + "than saying it was made.")
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
                        .addStringProperty(PIPELINE_NAME_ARG, "The name to suggest for the new pipeline and its "
                                                              + "XSLT, e.g. 'Logons extraction'; the user may "
                                                              + "change it.")
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what it adds to the table, shown with "
                                                        + "the button.")
                        .required(TABLE_ARG, FIELDS_ARG, PIPELINE_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final List<ExtractionField> fields = ExtractionField.parse(argsMap(request).get(FIELDS_ARG));
        final String tableName = stringArg(request, TABLE_ARG);
        final String name = NullSafe.get(stringArg(request, PIPELINE_NAME_ARG), String::strip);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (tableName == null) {
            throw new WorkspaceException("Give the table, by its name as the user sees it.");
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
        // A table that extracts already: a change to its pipeline's XSLT.
        final DocRef pipeline = settings.extractValues()
                ? settings.getExtractionPipeline()
                : null;
        // Only fields tried on the table's events, as they were tried, are offered; the XSLT is written for the
        // namespace of an event they were tried on.
        final PreviewRecord tried = previewRecord(context, dashboard, table.getComponentId());
        if (tried == null) {
            return ToolResult.text("Nothing was offered: the extraction hasn't been tried, usably, on the events of "
                                   + "table '" + tableName + "' (its last preview, if any, said why). Try it with "
                                   + "preview_extraction first, show the user the preview, and propose the fields as "
                                   + "tried.");
        }
        if (!PreviewRecord.fieldsHash(fields).equals(tried.fieldsHash())
            || !Objects.equals(tried.tablePipeline(), NullSafe.get(pipeline, DocRef::getUuid))) {
            return ToolResult.text("Nothing was offered: these fields (or the table's extraction pipeline) aren't "
                                   + "as they were when the extraction was last tried on the events of table '"
                                   + tableName + "'. Try these fields with preview_extraction, show the user the "
                                   + "preview, and propose them as tried.");
        }
        final Existing existing;
        final ExtractionChange.Changed changed;
        if (pipeline != null) {
            final String uses = "Nothing was offered: table '" + tableName + "' extracts its values with pipeline '"
                                + name(pipeline) + "', ";
            final MergedPipeline merged = MergedPipeline.load(documentAccess, pipeline.getUuid()).orElse(null);
            if (merged == null) {
                return ToolResult.text(uses + "which no longer exists or you can't see, so it can't be changed.");
            }
            final String element = ExtractionPipelines.outputXslt(merged).orElse(null);
            final DocRef xslt = element == null
                    ? null
                    : ExtractionPipelines.xsltOf(merged, element).orElse(null);
            if (xslt == null) {
                return ToolResult.text(uses + "which has no XSLT element naming an XSLT before its search result "
                                       + "output, so it can't be changed this way.");
            }
            final String unchangeable = ExtractionPipelines.unchangeable(merged, element).orElse(null);
            if (unchangeable != null) {
                return ToolResult.text(uses + "which can't be changed this way. " + unchangeable);
            }
            final String code = documentAccess.read(xslt, XsltDoc.class).map(XsltDoc::getData).orElse(null);
            if (code == null) {
                return ToolResult.text(uses + "whose XSLT '" + name(xslt) + "' isn't one you can read, so it can't "
                                       + "be changed.");
            }
            try {
                // Checked only: the events' namespace is read when the button is used.
                changed = ExtractionChange.change(code, null, fields);
            } catch (final IllegalArgumentException e) {
                return ToolResult.text("Nothing was offered: " + e.getMessage());
            }
            existing = new Existing(merged.pipeline(), element, xslt, ExtractionChange.sha256(code));
        } else {
            existing = null;
            changed = null;
        }
        if (!Objects.equals(tried.xsltHash(), NullSafe.get(existing, Existing::codeHash))) {
            return ToolResult.text("Nothing was offered: the XSLT of the extraction pipeline of table '" + tableName
                                   + "' has changed since the extraction was tried. Try it again with "
                                   + "preview_extraction, show the user the preview, and propose it as tried.");
        }

        final Map<String, String> columns = ResultsRequests.columnExpressions(search);
        // An XSLT naming fields as it runs gives fields its code doesn't name: the preview, stepped, checked which.
        final boolean namesAsItRuns = changed != null && changed.namesAsItRuns();
        final List<String> problems = new ArrayList<>(namesAsItRuns
                ? List.of()
                : ExtractionPreview.blankColumns(fields, NullSafe.getOrElse(changed,
                        ExtractionChange.Changed::alsoGiven, Set.of()), columns));
        final List<String> newColumns = new ArrayList<>();
        final List<ExtractionField> added = changed == null
                ? fields
                : changed.added();
        for (final ExtractionField field : added) {
            final boolean shown = columns.values().stream()
                    .anyMatch(expression -> ExtractionPreview.fieldsOf(expression).stream()
                            .anyMatch(field.name()::equals));
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
                table.getComponentId()), table.getComponentId(), tried.sample(), fields, newColumns, existing);
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", Map.of("type", dashboard.getType(), "uuid", dashboard.getUuid()));
        arguments.put("name", name);
        arguments.put("plan", plan.toJson());
        NullSafe.consume(summary, text -> arguments.put("summary", text.strip()));
        final int sequence = SEQUENCE.incrementAndGet();
        final ToolUserAction create = new ToolUserAction("create-extraction-pipeline-" + sequence,
                UserActions.CREATE_EXTRACTION_PIPELINE, arguments);
        final String names = String.join(", ", fields.stream().map(ExtractionField::name).toList());
        final String columnsAdded = newColumns.isEmpty()
                ? ""
                : ", adding column(s) " + String.join(", ", newColumns);
        if (existing == null) {
            auditLog.documentViewed(context.session(), dashboard, "Proposed an extraction pipeline for table '"
                                                                  + tableName + "' with the " + NAME + " tool");
            return ToolResult.text("The user has been shown a 'Create extraction pipeline' button for table '"
                                   + tableName + "' on dashboard '" + dashboard.getName() + "', extracting " + names
                                   + columnsAdded + ". When they click it, they choose the name (suggested '" + name
                                   + "') and folder of the new pipeline and its XSLT; then it is written, and the "
                                   + "table set to use it, unsaved. Nothing changes unless they do. Tell them about "
                                   + "the button rather than saying it was made.")
                    .withUserAction(create);
        }

        auditLog.documentViewed(context.session(), dashboard, "Proposed a change to the extraction pipeline of "
                                                              + "table '" + tableName + "' with the " + NAME
                                                              + " tool");
        final String what = (changed.generated()
                ? "its XSLT written again from the fields " + names + (changed.removed().isEmpty()
                ? ""
                : ", no longer giving " + String.join(", ", changed.removed()) + " (which anything else using it "
                  + "loses: say so)")
                : "the fields " + names + " added to its XSLT") + columnsAdded
                            + (namesAsItRuns
                ? " (its XSLT names some fields as it runs, so which columns it fills, and whether a field it adds "
                  + "is one it gives already, are as the preview found)"
                : "");
        final ToolUserAction change = new ToolUserAction("change-extraction-pipeline-" + sequence,
                UserActions.CHANGE_EXTRACTION_PIPELINE, arguments);
        final boolean canEdit = documentAccess.canEdit(existing.xslt());
        final ToolResult result = ToolResult.text("The user has been shown "
                                                  + (canEdit
                ? "two buttons for table '" + tableName + "' on dashboard '" + dashboard.getName() + "', with "
                  + what + ": 'Change extraction pipeline' changes pipeline '" + name(pipeline) + "' in place "
                  + "(they are shown the change as a diff, and the other dashboards and queries that use it, which "
                  + "change too), and 'Create extraction pipeline' saves a new pipeline, a child of it with its "
                  + "own XSLT, in a name (suggested '" + name + "') and folder they choose, for this table only. "
                : "a 'Create extraction pipeline' button for table '" + tableName + "' on dashboard '"
                  + dashboard.getName() + "', with " + what + ": it saves a new pipeline, a child of pipeline '"
                  + name(pipeline) + "' with its own XSLT, in a name (suggested '" + name + "') and folder they "
                  + "choose. They can't change pipeline '" + name(pipeline) + "' in place, as they can't edit its "
                  + "XSLT. ")
                                                  + "Only once they write the change, or choose the new pipeline's "
                                                  + "name and folder, is anything written, and the table set up to "
                                                  + "use it, unsaved. Tell them about the button(s) rather than "
                                                  + "saying it was made.");
        return canEdit
                ? result.withUserAction(change).withUserAction(create)
                : result.withUserAction(create);
    }

    // The record of the table's last usable preview in this chat, or null if there is none.
    private PreviewRecord previewRecord(final ToolCallContext context,
                                        final DocRef dashboard,
                                        final String componentId) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = PreviewRecord.path(dashboard.getUuid(), componentId);
        if (workspace.find(path).isEmpty()) {
            return null;
        }
        try {
            final PreviewRecord record = PreviewRecord.fromJson(Files.readString(workspace.localFile(path),
                    StandardCharsets.UTF_8));
            return record != null && dashboard.getUuid().equals(record.dashboard())
                   && componentId.equals(record.componentId())
                    ? record
                    : null;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String name(final DocRef docRef) {
        return NullSafe.nonBlankStringElse(docRef.getName(), docRef.getUuid());
    }
}
