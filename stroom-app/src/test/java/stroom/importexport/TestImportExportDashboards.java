/*
 * Copyright 2017-2024 Crown Copyright
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

package stroom.importexport;


import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStore;
import stroom.docstore.shared.AbstractDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.script.shared.ScriptDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportDashboards extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportService importExportService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private VisualisationStore visualisationStore;
    @Inject
    private ScriptStore scriptStore;
    @Inject
    private IndexStore indexStore;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private DashboardStore dashboardStore;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;

    @TempDir
    private Path tempDir;

    private Path createTempFile(final String filename) {
        return tempDir.resolve(filename);
    }

    @Test
    void testComplete() {
        test(false, false, false);
    }

    @Test
    void testUpdate() {
        test(false, false, true);
    }

    @Test
    void testSkipVisCreation() {
        test(true, false, false);
    }

    @Test
    void testSkipVisExport() {
        test(false, true, false);
    }

    @Test
    void testSkipVisCreationAndExport() {
        test(true, true, false);
    }

    private void test(final boolean skipVisCreation, final boolean skipVisExport, final boolean update) {
        deleteAllAndCheck();

        final ExplorerNode folder1 = explorerService.create(
                ExplorerConstants.FOLDER_TYPE,
                "Group1",
                null,
                null);
        final ExplorerNode folder2 = explorerService.create(
                ExplorerConstants.FOLDER_TYPE,
                "Group2",
                null,
                null);

        final List<ExplorerNode> nodes = explorerNodeService.getDescendants(null);
        assertThat(nodes.size()).isEqualTo(3);

        ExplorerNode visNode = null;
        if (!skipVisCreation) {
            final ExplorerNode scriptNode = explorerService.create(ScriptDoc.TYPE,
                    "Test Script",
                    folder2,
                    null);
            final ScriptDoc script = scriptStore.readDocument(scriptNode.getDocRef());
            script.setData("Test Data");
            scriptStore.writeDocument(script);
            assertThat(scriptStore.list().size()).isEqualTo(1);

            visNode = explorerService.create(
                    VisualisationDoc.TYPE,
                    "Test Vis",
                    folder2,
                    null);
            final VisualisationDoc vis = visualisationStore.readDocument(visNode.getDocRef());
            vis.setScriptRef(scriptNode.getDocRef());
            visualisationStore.writeDocument(vis);
            assertThat(visualisationStore.list().size()).isEqualTo(1);
        }

        final ExplorerNode pipelineNode = explorerService.create(PipelineDoc.TYPE,
                "Test Pipeline",
                folder1,
                null);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineNode.getDocRef());
        pipelineStore.writeDocument(pipelineDoc);
        assertThat(pipelineStore.list().size()).isEqualTo(1);

        final ExplorerNode indexNode = explorerService.create(
                LuceneIndexDoc.TYPE,
                "Test Index",
                folder1,
                null);
        final LuceneIndexDoc index = indexStore.readDocument(indexNode.getDocRef());
        indexStore.writeDocument(index);
        assertThat(indexStore.list().size()).isEqualTo(1);

        final ExplorerNode dictionaryNode = explorerService.create(DictionaryDoc.TYPE,
                "Test Dictionary",
                folder1,
                null);
        final DictionaryDoc dictionary = dictionaryStore.readDocument(dictionaryNode.getDocRef());
        dictionaryStore.writeDocument(dictionary);
        assertThat(dictionaryStore.list().size()).isEqualTo(1);

        // Create query.
        final QueryComponentSettings queryComponentSettings = QueryComponentSettings.builder()
                .dataSource(indexNode.getDocRef())
                .expression(createExpression(dictionary).build())
                .build();

        final ComponentConfig query = ComponentConfig.builder()
                .id("query-1234")
                .name("Query")
                .type("query")
                .settings(queryComponentSettings)
                .build();

        // Create table.
        final TableComponentSettings tableSettings = TableComponentSettings.builder()
                .extractValues(true)
                .extractionPipeline(pipelineNode.getDocRef())
                .build();

        final ComponentConfig table = ComponentConfig.builder()
                .id("table-1234")
                .name("Table")
                .type("table")
                .settings(tableSettings)
                .build();

        // Create visualisation.
        final VisComponentSettings visSettings = VisComponentSettings.builder()
                .tableId("table-1234")
                .visualisation(visNode != null
                        ? visNode.getDocRef()
                        : null)
                .build();

        final ComponentConfig visualisation = ComponentConfig.builder()
                .id("visualisation-1234")
                .name("Visualisation")
                .type("vis")
                .settings(visSettings)
                .build();

        // Create component list.
        final List<ComponentConfig> components = new ArrayList<>();
        components.add(query);
        components.add(table);
        components.add(visualisation);

        // Create dashboard.
        final DashboardConfig dashboardData = new DashboardConfig();
        dashboardData.setComponents(components);

        final ExplorerNode dashboardNode = explorerService.create(
                DashboardDoc.TYPE,
                "Test Dashboard",
                folder1,
                null);
        DashboardDoc dashboard = dashboardStore.readDocument(dashboardNode.getDocRef());
        dashboard.setDashboardConfig(dashboardData);
        dashboard = dashboardStore.writeDocument(dashboard);
        assertThat(dashboardStore.list().size()).isEqualTo(1);

        int startVisualisationSize = visualisationStore.list().size();
        final int startPipelineSize = pipelineStore.list().size();
        final int startIndexSize = indexStore.list().size();
        final int startDictionarySize = dictionaryStore.list().size();
        final int startDashboardSize = dashboardStore.list().size();

        final Path file = createTempFile("Export.zip");
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(folder1.getDocRef());
        if (!skipVisExport) {
            docRefs.add(folder2.getDocRef());
        } else {
//            startFolderSize = 1;
            startVisualisationSize = 0;
        }

        // Export all
        importExportService.exportConfig(docRefs, file);

        final Path exportConfigFile = createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, exportConfigFile);

        if (!update) {
            // Delete everything.
            deleteAllAndCheck();
        }

        // Import All
        final List<ImportState> confirmations = importExportService.importConfig(
                file,
                ImportSettings.createConfirmation(),
                new ArrayList<>());
        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }
        importExportService.importConfig(
                file,
                ImportSettings.actionConfirmation(),
                confirmations);

        assertThat(visualisationStore.list().size()).isEqualTo(startVisualisationSize);
        assertThat(pipelineStore.list().size()).isEqualTo(startPipelineSize);
        assertThat(indexStore.list().size()).isEqualTo(startIndexSize);
        assertThat(dictionaryStore.list().size()).isEqualTo(startDictionarySize);
        assertThat(dashboardStore.list().size()).isEqualTo(startDashboardSize);

        // Load the dashboard.
        final VisualisationDoc loadedVisualisation = first(visualisationStore);
        final DocRef loadedPipeline = pipelineStore.list().getFirst();
        final DocRef loadedIndex = indexStore.list().getFirst();
        final DictionaryDoc loadedDictionary = first(dictionaryStore);
        final DashboardDoc loadedDashboard = first(dashboardStore);
        final List<ComponentConfig> loadedComponents = loadedDashboard.getDashboardConfig().getComponents();
        final ComponentConfig loadedQuery = loadedComponents.get(0);
        final QueryComponentSettings loadedQueryData = (QueryComponentSettings) loadedQuery.getSettings();
        final ComponentConfig loadedTable = loadedComponents.get(1);
        final TableComponentSettings loadedTableSettings = (TableComponentSettings) loadedTable.getSettings();
        final ComponentConfig loadedVis = loadedComponents.get(2);
        final VisComponentSettings loadedVisSettings = (VisComponentSettings) loadedVis.getSettings();

        // Verify all entity references have been restored.
        assertThat(loadedQueryData.getDataSource())
                .isEqualTo(loadedIndex);
        assertThat(((ExpressionTerm) loadedQueryData.getExpression().getChildren().get(1)).getDocRef())
                .isEqualTo(stroom.docstore.shared.DocRefUtil.create(loadedDictionary));
        assertThat(loadedTableSettings.getExtractionPipeline())
                .isEqualTo(loadedPipeline);

        if (!skipVisExport || skipVisCreation) {
            assertThat(loadedVisSettings.getVisualisation())
                    .isEqualTo(stroom.docstore.shared.DocRefUtil.create(loadedVisualisation));
        } else {
            assertThat(loadedVisSettings.getVisualisation()).isNotNull();
            assertThat(loadedVisSettings.getVisualisation().getType()).isNotNull();
            assertThat(loadedVisSettings.getVisualisation().getUuid()).isNotNull();
        }
    }

    private <T extends AbstractDoc> T first(final DocumentStore<T> store) {
        final Set<DocRef> set = store.listDocuments();
        if (set != null && !set.isEmpty()) {
            return store.readDocument(set.iterator().next());
        }
        return null;
    }

    private void deleteAllAndCheck() {
        commonTestControl.clear();

        assertThat(visualisationStore.list().size()).isEqualTo(0);
        assertThat(pipelineStore.list().size()).isEqualTo(0);
        assertThat(indexStore.list().size()).isEqualTo(0);
        assertThat(dictionaryStore.list().size()).isEqualTo(0);
        assertThat(dashboardStore.list().size()).isEqualTo(0);
    }

    private ExpressionOperator.Builder createExpression(final DictionaryDoc dictionary) {
        final ExpressionOperator.Builder root = ExpressionOperator.builder();
        root.addTerm("EventTime", Condition.LESS_THAN, "2020-01-01T00:00:00.000Z");
        root.addDocRefTerm("User", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dictionary));
        return root;
    }
}
