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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.vis.ChartLibrary;
import stroom.ai.impl.vis.EChartsTemplate;
import stroom.ai.impl.vis.VisualisationActions;
import stroom.ai.impl.vis.VisualisationPlan;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Writes what a `create_timeline_dashboard` button offers ([ProposeTimelineDashboardTool]), when the user clicks
/// it, as the user, in the folder they chose: the timeline's table as a CSV Data Connection ([TimelineDataConnection])
/// and its timeline chart as a Visualisation drawn with ECharts, then gives the settings of the dashboard of them
/// ([TimelineDashboardConfig]) for Stroom's New Dashboard dialog. The table must be as it was when the button was
/// offered. Writing again (a second click, e.g. after the user cancelled the dialog) rewrites the same documents,
/// found by name in the folder; a re-run's button rewrites the earlier run's Data Connection, and renames it.
public class TimelineDashboards {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TimelineDashboards.class);

    private final ChatWorkspace workspace;
    private final DocumentAccess documentAccess;
    private final ChartLibraries chartLibraries;
    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<VisualisationStore> visualisationStoreProvider;

    /// @param workspace                   The chats' workspaces, holding the timeline's tables.
    /// @param documentAccess              Finds the folder's documents, and the Data Connection's handler.
    /// @param chartLibraries              Finds ECharts and puts Stroom's adapter beside it.
    /// @param explorerServiceProvider     Creates and renames documents, as the user.
    /// @param explorerNodeServiceProvider Finds the folder's node.
    /// @param visualisationStoreProvider  Writes the chart's Visualisation.
    @Inject
    public TimelineDashboards(final ChatWorkspace workspace,
                              final DocumentAccess documentAccess,
                              final ChartLibraries chartLibraries,
                              final Provider<ExplorerService> explorerServiceProvider,
                              final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                              final Provider<VisualisationStore> visualisationStoreProvider) {
        this.workspace = workspace;
        this.documentAccess = documentAccess;
        this.chartLibraries = chartLibraries;
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.visualisationStoreProvider = visualisationStoreProvider;
    }

    /// Writes the Data Connection and the chart's Visualisation in the folder, as the user, and builds the
    /// dashboard's settings.
    ///
    /// @param chatId The chat whose workspace holds the table.
    /// @param folder The folder the user chose.
    /// @param name   The Data Connection's name (the Visualisation's too).
    /// @param plan   What the button writes.
    /// @return What was written, and the dashboard's settings.
    /// @throws IllegalStateException saying why nothing (or only part) could be written, e.g. the table has changed
    /// since the button was offered, or the user may not create documents in the folder.
    public Written write(final int chatId,
                         final DocRef folder,
                         final String name,
                         final TimelineDashboardPlan plan) {
        final ExplorerNode folderNode = explorerNodeServiceProvider.get().getNode(folder)
                .orElseThrow(() -> new IllegalStateException("The folder no longer exists, or you can't see it."));
        final String csv = table(chatId, plan);
        final EChartsInstall echarts = chartLibraries.findECharts()
                .orElseThrow(() -> new IllegalStateException(ChartLibrary.ECHARTS.displayName() + " is no longer "
                                                             + "installed as a Script doc, so the timeline can't "
                                                             + "be drawn. Ask an administrator to install it."));
        if (!chartLibraries.currentUserCanUse(echarts.script())) {
            throw new IllegalStateException("You don't have Use permission on Script '" + echarts.script().getName()
                                            + "' (ECharts), so the timeline wouldn't load for you. Ask an "
                                            + "administrator for it.");
        }

        final List<ExplorerNode> children = documentAccess.children(folder);
        final DocRef replaced = plan.replace() == null
                ? null
                : documentAccess.node(plan.replace()).map(ExplorerNode::getDocRef).orElse(null);
        final DocRef existing = replaced != null
                ? replaced
                : named(children, DataConnectionDoc.TYPE, name).orElse(null);
        final DocRef dataConnection = writeDataConnection(folderNode, existing, name, plan, csv);

        final DocRef adapter = chartLibraries.ensureAdapter(echarts);
        final DocRef existingChart = named(children, VisualisationDoc.TYPE, name)
                .or(() -> replaced == null
                        ? Optional.empty()
                        : named(children, VisualisationDoc.TYPE, replaced.getName()))
                .filter(chartLibraries::isGenerated)
                .orElse(null);
        final DocRef visualisation = writeVisualisation(folderNode, existingChart, name, plan, adapter,
                echarts.version());

        final DashboardConfig config = TimelineDashboardConfig.build(dataConnection, visualisation, plan);
        return new Written(dataConnection, visualisation, existing, config,
                VisualisationActions.inExplorerTree(folderNode));
    }

    // The table's CSV, as it was when the button was offered.
    private String table(final int chatId, final TimelineDashboardPlan plan) {
        final SessionWorkspace session = new SessionWorkspace(workspace, chatId);
        final WorkspacePath path = WorkspacePath.parse(plan.path());
        if (session.find(path).isEmpty()) {
            throw new IllegalStateException("The timeline's table " + plan.path() + " is no longer in the chat's "
                                            + "workspace: ask for the timeline again.");
        }
        final String csv;
        try {
            csv = Files.readString(session.localFile(path), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!plan.hash().equals(ProposeCodeChangeTool.sha256(csv))) {
            throw new IllegalStateException("The timeline has been built again since this button was offered, so "
                                            + "its table has changed: ask for the dashboard again, to write the "
                                            + "new one.");
        }
        return csv;
    }

    // Writes the Data Connection: rewriting (and renaming) the one given, or a new one, deleted again if it can't
    // be filled.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private DocRef writeDataConnection(final ExplorerNode folderNode,
                                       final DocRef existing,
                                       final String name,
                                       final TimelineDashboardPlan plan,
                                       final String csv) {
        final DocumentActionHandler handler = documentAccess.handler(DataConnectionDoc.TYPE)
                .orElseThrow(() -> new IllegalStateException("This Stroom has no Data Connections."));
        final DocRef target = existing != null
                ? existing
                : create(DataConnectionDoc.TYPE, name, folderNode);
        try {
            final DataConnectionDoc doc = (DataConnectionDoc) handler.readDocument(target);
            handler.writeDocument(TimelineDataConnection.apply(doc.copy(), plan.table(), csv, plan.description())
                    .build());
        } catch (final RuntimeException e) {
            if (existing == null) {
                deleteQuietly(target);
            }
            throw new IllegalStateException("The Data Connection couldn't be written: " + message(e), e);
        }
        return rename(target, name);
    }

    // Writes the chart's Visualisation: rewriting (and renaming) the one Stroom AI generated earlier, or a new one.
    private DocRef writeVisualisation(final ExplorerNode folderNode,
                                      final DocRef existing,
                                      final String name,
                                      final TimelineDashboardPlan plan,
                                      final DocRef adapter,
                                      final String version) {
        final VisualisationStore store = visualisationStoreProvider.get();
        final DocRef target = existing != null
                ? existing
                : create(VisualisationDoc.TYPE, name, folderNode);
        final String settings = EChartsTemplate.settingsJson(ChartKind.TIMELINE,
                TimelineDashboardConfig.chartMappings(plan),
                VisualisationPlan.normaliseStyle(ChartKind.TIMELINE, TimelineDashboardConfig.chartStyle(plan)),
                VisualisationPlan.normaliseOptions(null, ChartLibrary.ECHARTS));
        try {
            final VisualisationDoc doc = store.readDocument(target);
            store.writeDocument(doc.copy()
                    .functionName(ChartLibrary.ECHARTS.functionName())
                    .scriptRef(adapter)
                    .settings(settings)
                    .description("Generated by Stroom AI: a timeline of Data Connection '" + name + "', drawn with "
                                 + ChartLibrary.ECHARTS.displayName() + " " + version + ". Change its columns and "
                                 + "style in its settings, or ask Stroom AI to tailor it.")
                    .build());
        } catch (final RuntimeException e) {
            if (existing == null) {
                deleteQuietly(target);
            }
            throw new IllegalStateException("The timeline chart's Visualisation couldn't be written: " + message(e),
                    e);
        }
        return rename(target, name);
    }

    // Creates a document in the folder, as the user: Stroom checks they may create it there, and logs it.
    private DocRef create(final String type, final String name, final ExplorerNode folderNode) {
        try {
            return explorerServiceProvider.get().create(type, name, folderNode, PermissionInheritance.DESTINATION)
                    .getDocRef();
        } catch (final RuntimeException e) {
            throw new IllegalStateException("The " + (DataConnectionDoc.TYPE.equals(type)
                    ? "Data Connection"
                    : "Visualisation") + " couldn't be created in the folder: " + message(e), e);
        }
    }

    private DocRef rename(final DocRef doc, final String name) {
        if (name.equals(doc.getName())) {
            return doc;
        }
        final ExplorerNode node = explorerNodeServiceProvider.get().getNode(doc)
                .orElseThrow(() -> new IllegalStateException(doc.getType() + " '" + doc.getName() + "' isn't in "
                                                             + "the explorer tree, so it can't be renamed."));
        return explorerServiceProvider.get().rename(node, name).getDocRef();
    }

    private void deleteQuietly(final DocRef doc) {
        try {
            explorerNodeServiceProvider.get().getNode(doc)
                    .ifPresent(node -> explorerServiceProvider.get().delete(List.of(node)));
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Unable to delete the empty " + doc + " after failing to fill it", e);
        }
    }

    /// @param children A folder's documents.
    /// @param type     A document type.
    /// @param name     A name.
    /// @return The first of the type with the name, if there is one.
    static Optional<DocRef> named(final List<ExplorerNode> children, final String type, final String name) {
        return children.stream()
                .map(ExplorerNode::getDocRef)
                .filter(Objects::nonNull)
                .filter(doc -> type.equals(doc.getType()) && Objects.equals(name, doc.getName()))
                .findFirst();
    }

    private static String message(final RuntimeException e) {
        return Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
    }


    // --------------------------------------------------------------------------------


    /// What was written.
    ///
    /// @param dataConnection The Data Connection.
    /// @param visualisation  The timeline chart's Visualisation.
    /// @param replaced       The Data Connection rewritten (before any rename), or null if it was new.
    /// @param config         The dashboard's settings.
    /// @param folder         The folder, as the explorer tree holds it, for the New Dashboard dialog.
    public record Written(DocRef dataConnection,
                          DocRef visualisation,
                          DocRef replaced,
                          DashboardConfig config,
                          ExplorerNode folder) {

    }
}
