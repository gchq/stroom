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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolUserAction;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.vis.ChartCheck;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisualisationDoc;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_timeline_dashboard`: offers the user one button that puts a timeline the chat built
/// (`activity_timeline`) on a dashboard, as a dashboard can't read the chat's workspace. The button writes the
/// timeline's detail spans (`spans.csv`) or its activities (`activities.csv`), as they are, as a CSV Data Connection
/// (stroom#6010) in the folder the user chooses, named for the entity, the period and the table, and described as the
/// dated snapshot it is ([TimelineDataConnection]), with its timeline chart as a Visualisation beside it; then
/// opens Stroom's New Dashboard dialog for a dashboard of them: a query of the Data Connection, the chart, and a
/// table a click on a bar filters to its interval ([TimelineDashboardConfig]). Nothing is written until the user
/// clicks it ([TimelineDashboards]).
///
/// A new action of `activity_timeline` would run the whole timeline again (its searches and side calls) for what
/// its tables already hold, and `propose_dashboard` reads data sources, never the workspace; so this is a tool of its
/// own in the timelines group, offered only once the group is turned on.
///
/// A re-run of the timeline for the same entity and table offers to replace the earlier run's Data Connection in
/// the folder (rewritten and renamed for the new period). Needs Create (Data Connection and Visualisation) in the
/// folder, and Edit on a Data Connection it replaces, checked before the button is offered.
public class ProposeTimelineDashboardTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_timeline_dashboard";
    static final String ENTITY_ARG = ActivityTimelineTool.ENTITY_ARG;
    static final String TABLE_ARG = "table";
    static final String FOLDER_ARG = "folder";
    /// The folder form's key.
    static final String FOLDER_FORM = "timeline-dashboard-folder";
    /// The most rows of the table the chart check reads.
    static final int SAMPLE_ROWS = 2_000;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final DocumentAccess documentAccess;
    private final ChartLibraries chartLibraries;
    private final Provider<TabSnapshots> tabSnapshotsProvider;
    private final DataSourceAccess dataSourceAccess;

    /// @param workspace            The chats' workspaces, holding the timeline's tables.
    /// @param documentAccess       Finds the folder, its documents and the user's permissions on them.
    /// @param chartLibraries       Finds ECharts, which draws the chart.
    /// @param tabSnapshotsProvider Gives the dashboard attached to the chat's question, whose folder is suggested.
    /// @param dataSourceAccess     Says whether the user can use an Elastic Index, which a timeline reads.
    @Inject
    ProposeTimelineDashboardTool(final ChatWorkspace workspace,
                                 final DocumentAccess documentAccess,
                                 final ChartLibraries chartLibraries,
                                 final Provider<TabSnapshots> tabSnapshotsProvider,
                                 final DataSourceAccess dataSourceAccess) {
        super(workspace);
        this.documentAccess = documentAccess;
        this.chartLibraries = chartLibraries;
        this.tabSnapshotsProvider = tabSnapshotsProvider;
        this.dataSourceAccess = dataSourceAccess;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .note("Offered only if the user may create a Data Connection and a Visualisation in the folder "
                      + "(and edit the Data Connection it replaces); the button writes them as the user, and the "
                      + "New Dashboard dialog checks the dashboard's folder.")
                .build();
    }

    /// Offered to a user who can use an Elastic Index, as `activity_timeline` is: there is no timeline to put on a
    /// dashboard without one.
    ///
    /// @return True if the user can use at least one Elastic Index.
    @Override
    public boolean isUsable() {
        return dataSourceAccess.canUseAny(ElasticIndexDoc.TYPE);
    }

    @Override
    public boolean isChatOnly() {
        // The user writes it with a button in the chat.
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
                        .general("Puts an activity timeline built in this session on a dashboard, when the user "
                                 + "wants it there rather than in the chat: offers one button that writes its spans "
                                 + "(detail) or activities table, as it is, as a CSV Data Connection (a dated "
                                 + "snapshot, named for the entity and period) with its timeline chart in the "
                                 + "folder the user chooses, then opens the New Dashboard dialog for a dashboard "
                                 + "of a query, the chart and a table a bar's click filters. A Data Connection of "
                                 + "an earlier run for the same entity and table there is replaced. It holds the "
                                 + "user's activity: anyone who can view the folder can read it. Omit folder to "
                                 + "ask the user")
                        .chatOnly(" (the attached dashboard's folder suggested)")
                        .general(".")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(ENTITY_ARG, "The user (or entity), as the timeline was built for.")
                        .addEnumProperty(TABLE_ARG, TimelineDataConnection.TABLES, "Which table: spans (the "
                                                                                    + "detail) or activities.")
                        .addStringProperty(FOLDER_ARG, "Optional: the folder, by UUID or Explorer path, as the "
                                                       + "user chose it.")
                        .required(ENTITY_ARG, TABLE_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String entity = stringArg(request, ENTITY_ARG);
        final String table = stringArg(request, TABLE_ARG);
        if (entity == null) {
            throw new WorkspaceException("Give " + ENTITY_ARG + ": the user (or entity) whose timeline to put on a "
                                         + "dashboard.");
        }
        if (table == null || !TimelineDataConnection.TABLES.contains(table)) {
            throw new WorkspaceException("Give " + TABLE_ARG + ": " + String.join(" or ",
                    TimelineDataConnection.TABLES) + ".");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String timelineFolder = ActivityTimelineTool.FOLDER + ActivityTimelineTool.safe(entity) + "/";
        final TimelineSnapshot snapshot = TimelineSnapshot.read(workspace, timelineFolder)
                .filter(found -> entity.strip().equals(found.entity()))
                .orElse(null);
        if (snapshot == null) {
            return ToolResult.text("Error: this chat has no timeline of " + entity + " yet (or it was built before "
                                   + "Stroom wrote what a dashboard needs): build it with activity_timeline first.");
        }
        final WorkspacePath path = WorkspacePath.parse(timelineFolder + TimelineDataConnection.file(table));
        final Table rows = read(workspace, path).orElse(null);
        if (rows == null) {
            return ToolResult.text("Error: the timeline of " + entity + " has no " + path + ": build it again with "
                                   + "activity_timeline.");
        }
        final Optional<String> headerProblem = TimelineDataConnection.headerProblem(table, String.join(",",
                rows.header()));
        if (headerProblem.isPresent()) {
            return ToolResult.text("Error: " + path + " can't be written as a Data Connection: "
                                   + headerProblem.get() + ". Build the timeline again with activity_timeline.");
        }
        if (rows.count() == 0) {
            return ToolResult.text("Error: " + path + " has no rows, so there is nothing to put on a dashboard"
                                   + (TimelineDataConnection.ACTIVITIES.equals(table)
                    ? ": the activities are found only with view activities (or both, or characterise)."
                    : "."));
        }
        if (rows.count() > DataConnectionDoc.DEFAULT_MAX_ROWS) {
            return ToolResult.text("Error: " + path + " has " + rows.count() + " rows, more than a Data Connection "
                                   + "reads (" + DataConnectionDoc.DEFAULT_MAX_ROWS + "): build the timeline for a "
                                   + "shorter period.");
        }

        final DocRef folder = folder(request, context).orElse(null);
        if (folder == null) {
            return ToolResult.text("No button was offered: the user didn't choose a folder for the Data "
                                   + "Connection. Ask which, or give " + FOLDER_ARG + ".");
        }
        final String where = "folder '" + folderPath(folder) + "'";
        final List<String> cannot = new ArrayList<>();
        if (!documentAccess.canCreate(folder, DataConnectionDoc.TYPE)) {
            cannot.add("a Data Connection");
        }
        if (!documentAccess.canCreate(folder, VisualisationDoc.TYPE)) {
            cannot.add("a Visualisation");
        }
        if (!cannot.isEmpty()) {
            return ToolResult.text("No button was offered: you don't have permission to create "
                                   + String.join(" or ", cannot) + " in " + where + ". Ask the user for a folder "
                                   + "they may create documents in, or ask someone who can to grant Create there.");
        }
        final EChartsInstall echarts = chartLibraries.findECharts().orElse(null);
        if (echarts == null || !chartLibraries.currentUserCanUse(echarts.script())) {
            return ToolResult.text("No button was offered: the timeline chart is drawn with ECharts, which "
                                   + (echarts == null
                    ? "isn't installed as a Script doc"
                    : "you don't have Use permission on (Script '" + echarts.script().getName() + "')")
                                   + ". Ask an administrator for it, or draw the timeline in the chat with "
                                   + "gantt_chart.");
        }

        final Map<String, String> mappings = TimelineDashboardConfig.mappings(table, snapshot.lanes(),
                snapshot.characterised());
        final ChartCheck.Result check = ChartCheck.check(ChartKind.TIMELINE, mappings, Map.of(),
                TimelineDashboardConfig.chartColumns(table), new ChartCheck.Sample(rows.header(), rows.sample()));
        if (!check.ok()) {
            return ToolResult.text("No button was offered: the timeline chart can't be drawn from " + path + ": "
                                   + String.join(" ", check.problems()));
        }

        final List<ExplorerNode> children = documentAccess.children(folder);
        final DocRef replace = children.stream()
                .map(ExplorerNode::getDocRef)
                .filter(doc -> doc != null && DataConnectionDoc.TYPE.equals(doc.getType())
                               && TimelineDataConnection.isFor(doc.getName(), snapshot.entity(), table))
                .findFirst()
                .orElse(null);
        if (replace != null && !documentAccess.canEdit(replace)) {
            return ToolResult.text("No button was offered: " + where + " has Data Connection '" + replace.getName()
                                   + "' of an earlier timeline of " + snapshot.entity() + ", which a new one "
                                   + "replaces, and you don't have permission to edit it. Ask the user for "
                                   + "another folder.");
        }
        final String name = TimelineDataConnection.name(snapshot.entity(), snapshot.fromMs(), snapshot.toMs(),
                TimelineDataConnection.zone(snapshot.zone()), table);
        final TimelineDashboardPlan plan = new TimelineDashboardPlan(snapshot.entity(), table, path.toString(),
                ProposeCodeChangeTool.sha256(rows.csv()), rows.count(), snapshot.fromMs(), snapshot.toMs(),
                TimelineDataConnection.description(snapshot, table), mappings, replace);

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", ProposeCodeChangeTool.docRefMap(folder));
        arguments.put("name", name);
        arguments.put("plan", plan.toJson());
        arguments.put("summary", rows.count() + " " + (TimelineDataConnection.SPANS.equals(table)
                ? "spans"
                : "activity rows") + " of " + snapshot.entity() + ", as built at " + snapshot.built() + ".");
        if (replace != null) {
            arguments.put("replace", ProposeCodeChangeTool.docRefMap(replace));
        }
        final ToolUserAction action = new ToolUserAction("create-timeline-dashboard-" + SEQUENCE.incrementAndGet(),
                UserActions.CREATE_TIMELINE_DASHBOARD, arguments);
        documentAccess.logView(context.session(), folder, "Folder for a timeline's Data Connection, offered by "
                                                          + NAME);
        return ToolResult.text(describe(name, where, snapshot, table, rows.count(), mappings, replace, check,
                documentAccess.canCreate(folder, DashboardDoc.TYPE))).withUserAction(action);
    }

    // What the user has been offered, for the model to tell them.
    private static String describe(final String name,
                                   final String where,
                                   final TimelineSnapshot snapshot,
                                   final String table,
                                   final int rows,
                                   final Map<String, String> mappings,
                                   final DocRef replace,
                                   final ChartCheck.Result check,
                                   final boolean canCreateDashboard) {
        final StringBuilder sb = new StringBuilder("The user has been offered a 'Create Data Connection and "
                                                   + "dashboard…' button. Nothing is written until they click it; "
                                                   + "then Stroom, as the user, ");
        sb.append(replace == null
                        ? "writes Data Connection '" + name + "'"
                        : "replaces Data Connection '" + replace.getName() + "' (an earlier timeline of "
                          + snapshot.entity() + "), renamed '" + name + "',")
                .append(" in ").append(where).append(": the ").append(rows).append(" rows of ")
                .append(TimelineDataConnection.file(table)).append(" as a CSV, a snapshot as built at ")
                .append(snapshot.built()).append(" (its description says so, and from which sources), typed (start "
                                                 + "and end Date), its time field start; and beside it Visualisation '")
                .append(name).append("', a timeline of start to end, label ").append(mappings.get("label"))
                .append(", lane ").append(mappings.get("lane")).append(", colour ").append(mappings.get("colour"))
                .append(". Then Stroom's New Dashboard dialog opens in that folder for a dashboard of a query of the "
                        + "Data Connection over the timeline's period, the chart, and a table of its columns in time "
                        + "order, which a click on a bar filters to the rows starting within the bar's interval.");
        if (!canCreateDashboard) {
            sb.append(" The user can't create a dashboard in that folder: they choose another in the dialog.");
        }
        sb.append("\nPrivacy: the Data Connection holds ").append(snapshot.entity())
                .append("'s activity, with the folder's permissions: anyone who can view ").append(where)
                .append(" can read it. Tell the user so, and that it isn't updated (building the timeline again "
                        + "offers to replace it). Tell them about the button rather than saying it was written.");
        if (!check.warnings().isEmpty()) {
            sb.append("\nThe chart: ").append(String.join(" ", check.warnings()));
        }
        return sb.toString();
    }

    // The folder: the call's; else asked in a form, the attached dashboard's folder suggested.
    private Optional<DocRef> folder(final ToolCall request, final ToolCallContext context) {
        final String given = stringArg(request, FOLDER_ARG);
        if (given != null) {
            final DocRef folder = documentAccess.findFolder(given).orElse(null);
            if (folder == null || !ExplorerConstants.FOLDER_TYPE.equals(folder.getType())) {
                throw new WorkspaceException("No folder '" + given + "' that you can see. Give a folder's UUID or "
                                             + "Explorer path.");
            }
            return Optional.of(folder);
        }
        if (!context.userQuestions().canAsk()) {
            throw new WorkspaceException("Give " + FOLDER_ARG + ": the folder the user chose for the Data "
                                         + "Connection.");
        }
        final String suggested = dashboardFolder(context).orElse(null);
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", "Folder");
        field.put("description", "An Explorer path, e.g. /Investigations/Case 12. The timeline goes there with "
                                 + "its permissions: anyone who can view the folder can read it.");
        if (suggested != null) {
            field.put("default", suggested);
        }
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of(FOLDER_ARG, field));
        form.put("required", List.of(FOLDER_ARG));
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                FOLDER_FORM), "Which folder should the timeline's Data Connection and chart go in?", form);
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            return Optional.empty();
        }
        final Object value = NullSafe.map(answer.content()).get(FOLDER_ARG);
        final String chosen = value == null
                ? ""
                : String.valueOf(value).strip();
        final DocRef folder = documentAccess.findFolder(chosen).orElse(null);
        if (folder == null || !ExplorerConstants.FOLDER_TYPE.equals(folder.getType())) {
            throw new WorkspaceException("No folder '" + chosen + "' that you can see: ask the user again for a "
                                         + "folder's Explorer path.");
        }
        return Optional.of(folder);
    }

    // A folder's own Explorer path, e.g. /Investigations/Case 12 (DocumentAccess#path gives the folder holding it).
    private String folderPath(final DocRef folder) {
        if (ExplorerConstants.SYSTEM_DOC_REF.getUuid().equals(folder.getUuid())) {
            return "/";
        }
        final String parent = documentAccess.path(folder);
        final String name = NullSafe.nonBlankStringElse(folder.getName(), folder.getUuid());
        return parent.endsWith("/")
                ? parent + name
                : parent + "/" + name;
    }

    // The Explorer path of the folder holding the dashboard attached to the chat's latest question, if one is.
    private Optional<String> dashboardFolder(final ToolCallContext context) {
        try {
            if (context.session().chatId().isEmpty()) {
                return Optional.empty();
            }
            final StoredTabSnapshot tab = tabSnapshotsProvider.get()
                    .forLatestQuestion(context.session().chatId().getAsInt()).orElse(null);
            if (tab == null || tab.docRef() == null || !DashboardDoc.TYPE.equals(tab.docRef().getType())) {
                return Optional.empty();
            }
            return Optional.of(documentAccess.path(tab.docRef()));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    // The table: its header, its rows' count and a sample of them, and its CSV.
    private static Optional<Table> read(final SessionWorkspace workspace, final WorkspacePath path) {
        if (workspace.find(path).isEmpty()) {
            return Optional.empty();
        }
        final String csv;
        try {
            csv = Files.readString(workspace.localFile(path), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final List<String> header = new ArrayList<>();
        final List<List<String>> sample = new ArrayList<>();
        int count = 0;
        try (final CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)) {
            for (final CSVRecord record : parser) {
                if (record.getRecordNumber() == 1) {
                    header.addAll(record.toList());
                } else {
                    count++;
                    if (sample.size() < SAMPLE_ROWS) {
                        sample.add(record.toList());
                    }
                }
            }
        } catch (final IOException | UncheckedIOException | IllegalStateException e) {
            throw new WorkspaceException(path + " can't be read as CSV: " + e.getMessage());
        }
        return Optional.of(new Table(csv, header, count, sample));
    }


    // --------------------------------------------------------------------------------


    // A table read from the workspace.
    private record Table(String csv, List<String> header, int count, List<List<String>> sample) {

    }
}
