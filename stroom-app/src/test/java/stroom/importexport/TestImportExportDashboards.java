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
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.resource.api.ResourceStore;
import stroom.script.shared.ScriptDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.shared.ResourceKey;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportDashboards extends AbstractCoreIntegrationTest {

    @Inject
    private ImportExportService importExportService;
    @Inject
    private ResourceStore resourceStore;
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

        final ExplorerNode folder1 = explorerService.createFolder(
                "Group1",
                null,
                null);
        final ExplorerNode folder2 = explorerService.createFolder(
                "Group2",
                null,
                null);

        final List<ExplorerNode> nodes = explorerNodeService.getDescendants(null);
        assertThat(nodes.size()).isEqualTo(3);

        ExplorerNode visNode = null;
        if (!skipVisCreation) {
            ScriptDoc script = scriptStore.createDocument();
            script.setName("Test Script");
            script.setData("Test Data");
            script = scriptStore.writeDocument(script);
            explorerService.create(script.asDocRef(), folder2, null);
            assertThat(scriptStore.list().size()).isEqualTo(1);

            VisualisationDoc vis = visualisationStore.createDocument();
            vis.setName("Test Vis");
            vis.setScriptRef(script.asDocRef());
            vis = visualisationStore.writeDocument(vis);
            visNode = explorerService.create(vis.asDocRef(), folder2, null);
            assertThat(visualisationStore.list().size()).isEqualTo(1);
        }

        PipelineDoc pipelineDoc = pipelineStore.createDocument();
        pipelineDoc.setName("Test Pipeline");
        pipelineDoc = pipelineStore.writeDocument(pipelineDoc);
        final ExplorerNode pipelineNode = explorerService.create(pipelineDoc.asDocRef(), folder1, null);
        assertThat(pipelineStore.list().size()).isEqualTo(1);

        LuceneIndexDoc index = indexStore.createDocument();
        index.setName("Test Index");
        index = indexStore.writeDocument(index);
        final ExplorerNode indexNode = explorerService.create(index.asDocRef(), folder1, null);
        assertThat(indexStore.list().size()).isEqualTo(1);

        DictionaryDoc dictionary = dictionaryStore.createDocument();
        dictionary.setName("Test Dictionary");
        dictionary = dictionaryStore.writeDocument(dictionary);
        final ExplorerNode dictionaryNode = explorerService.create(dictionary.asDocRef(), folder1, null);
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

        DashboardDoc dashboard = dashboardStore.createDocument();
        dashboard.setName("Test Dashboard");
        dashboard.setDashboardConfig(dashboardData);
        dashboard = dashboardStore.writeDocument(dashboard);
        final ExplorerNode dashboardNode = explorerService.create(dashboard.asDocRef(), folder1, null);
        assertThat(dashboardStore.list().size()).isEqualTo(1);

        int startVisualisationSize = visualisationStore.list().size();
        final int startPipelineSize = pipelineStore.list().size();
        final int startIndexSize = indexStore.list().size();
        final int startDictionarySize = dictionaryStore.list().size();
        final int startDashboardSize = dashboardStore.list().size();

        final ResourceKey file = resourceStore.createTempFile("Export.zip");
        final Set<DocRef> docRefs = new HashSet<>();
        docRefs.add(folder1.getDocRef());
        if (!skipVisExport) {
            docRefs.add(folder2.getDocRef());
        } else {
//            startFolderSize = 1;
            startVisualisationSize = 0;
        }

        // Export all
        importExportService.exportConfig(docRefs, resourceStore.getTempFile(file));

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, resourceStore.getTempFile(exportConfig));

        if (!update) {
            // Delete everything.
            deleteAllAndCheck();
        }

        // Import All
        final List<ImportState> confirmations = importExportService.importConfig(
                resourceStore.getTempFile(file),
                ImportSettings.createConfirmation(),
                new ArrayList<>());
        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }
        importExportService.importConfig(
                resourceStore.getTempFile(file),
                ImportSettings.actionConfirmation(),
                confirmations);

        assertThat(visualisationStore.list().size()).isEqualTo(startVisualisationSize);
        assertThat(pipelineStore.list().size()).isEqualTo(startPipelineSize);
        assertThat(indexStore.list().size()).isEqualTo(startIndexSize);
        assertThat(dictionaryStore.list().size()).isEqualTo(startDictionarySize);
        assertThat(dashboardStore.list().size()).isEqualTo(startDashboardSize);

        // Load the dashboard.
        final VisualisationDoc loadedVisualisation = first(visualisationStore);
        final DocRef loadedPipeline = pipelineStore.list().get(0);
        final DocRef loadedIndex = indexStore.list().get(0);
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
        if (set != null && set.size() > 0) {
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
