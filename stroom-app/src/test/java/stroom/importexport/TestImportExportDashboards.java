/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.importexport;


import org.junit.jupiter.api.Test;
import stroom.dashboard.impl.DashboardStore;
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
import stroom.docstore.shared.Doc;
import stroom.util.shared.DocRefs;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportState;
import stroom.index.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.resource.api.ResourceStore;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.script.shared.ScriptDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.shared.ResourceKey;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.visualisation.shared.VisualisationDoc;

import javax.inject.Inject;
import java.util.ArrayList;
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

        final DocRef folder1 = explorerService.create(ExplorerConstants.FOLDER, "Group1", null, null);
        final DocRef folder2 = explorerService.create(ExplorerConstants.FOLDER, "Group2", null, null);

        final List<ExplorerNode> nodes = explorerNodeService.getDescendants(null);
        assertThat(nodes.size()).isEqualTo(3);

        DocRef visRef = null;
        if (!skipVisCreation) {
            final DocRef scriptRef = explorerService.create(ScriptDoc.DOCUMENT_TYPE, "Test Script", folder2, null);
            ScriptDoc script = scriptStore.readDocument(scriptRef);
            script.setData("Test Data");
            scriptStore.writeDocument(script);
            assertThat(scriptStore.list().size()).isEqualTo(1);

            visRef = explorerService.create(VisualisationDoc.DOCUMENT_TYPE, "Test Vis", folder2, null);
            final VisualisationDoc vis = visualisationStore.readDocument(visRef);
            vis.setScriptRef(scriptRef);
            visualisationStore.writeDocument(vis);
            assertThat(visualisationStore.list().size()).isEqualTo(1);
        }

        final DocRef pipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "Test Pipeline", folder1, null);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        pipelineStore.writeDocument(pipelineDoc);
        assertThat(pipelineStore.list().size()).isEqualTo(1);

        final DocRef indexRef = explorerService.create(IndexDoc.DOCUMENT_TYPE, "Test Index", folder1, null);
        final IndexDoc index = indexStore.readDocument(indexRef);
        indexStore.writeDocument(index);
        assertThat(indexStore.list().size()).isEqualTo(1);

        final DocRef dictionaryRef = explorerService.create(DictionaryDoc.ENTITY_TYPE, "Test Dictionary", folder1, null);
        final DictionaryDoc dictionary = dictionaryStore.readDocument(dictionaryRef);
        dictionaryStore.writeDocument(dictionary);
        assertThat(dictionaryStore.list().size()).isEqualTo(1);

        // Create query.
        final QueryComponentSettings queryComponentSettings = new QueryComponentSettings();
        queryComponentSettings.setDataSource(indexRef);
        queryComponentSettings.setExpression(createExpression(dictionary).build());

        final ComponentConfig query = new ComponentConfig();
        query.setId("query-1234");
        query.setName("Query");
        query.setType("query");
        query.setSettings(queryComponentSettings);

        // Create table.
        final TableComponentSettings tableSettings = new TableComponentSettings();
        tableSettings.setExtractValues(true);
        tableSettings.setExtractionPipeline(pipelineRef);

        final ComponentConfig table = new ComponentConfig();
        table.setId("table-1234");
        table.setName("Table");
        table.setType("table");
        table.setSettings(tableSettings);

        // Create visualisation.
        final VisComponentSettings visSettings = new VisComponentSettings();
        visSettings.setTableId("table-1234");
        visSettings.setTableSettings(tableSettings);
        visSettings.setVisualisation(visRef);

        final ComponentConfig visualisation = new ComponentConfig();
        visualisation.setId("visualisation-1234");
        visualisation.setName("Visualisation");
        visualisation.setType("vis");
        visualisation.setSettings(visSettings);

        // Create component list.
        final List<ComponentConfig> components = new ArrayList<>();
        components.add(query);
        components.add(table);
        components.add(visualisation);

        // Create dashboard.
        final DashboardConfig dashboardData = new DashboardConfig();
        dashboardData.setComponents(components);

        final DocRef dashboardRef = explorerService.create(DashboardDoc.DOCUMENT_TYPE, "Test Dashboard", folder1, null);
        DashboardDoc dashboard = dashboardStore.readDocument(dashboardRef);
        dashboard.setDashboardConfig(dashboardData);
        dashboard = dashboardStore.writeDocument(dashboard);
        assertThat(dashboardStore.list().size()).isEqualTo(1);

        int startVisualisationSize = visualisationStore.list().size();
        final int startPipelineSize = pipelineStore.list().size();
        final int startIndexSize = indexStore.list().size();
        final int startDictionarySize = dictionaryStore.list().size();
        final int startDashboardSize = dashboardStore.list().size();

        final ResourceKey file = resourceStore.createTempFile("Export.zip");
        final DocRefs docRefs = new DocRefs();
        docRefs.add(folder1);
        if (!skipVisExport) {
            docRefs.add(folder2);
        } else {
//            startFolderSize = 1;
            startVisualisationSize = 0;
        }

        // Export all
        importExportService.exportConfig(docRefs, resourceStore.getTempFile(file), new ArrayList<>());

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, resourceStore.getTempFile(exportConfig), new ArrayList<>());

        if (!update) {
            // Delete everything.
            deleteAllAndCheck();
        }

        // Import All
        final List<ImportState> confirmations = importExportService
                .createImportConfirmationList(resourceStore.getTempFile(file));

        for (final ImportState confirmation : confirmations) {
            confirmation.setAction(true);
        }

        importExportService.performImportWithConfirmation(resourceStore.getTempFile(file), confirmations);

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
        assertThat(loadedQueryData.getDataSource()).isEqualTo(loadedIndex);
        assertThat(((ExpressionTerm) loadedQueryData.getExpression().getChildren().get(1)).getDictionary()).isEqualTo(stroom.docstore.shared.DocRefUtil.create(loadedDictionary));
        assertThat(loadedTableSettings.getExtractionPipeline()).isEqualTo(loadedPipeline);

        if (!skipVisExport || skipVisCreation) {
            assertThat(loadedVisSettings.getVisualisation()).isEqualTo(stroom.docstore.shared.DocRefUtil.create(loadedVisualisation));
        } else {
            assertThat(loadedVisSettings.getVisualisation()).isNotNull();
            assertThat(loadedVisSettings.getVisualisation().getType()).isNotNull();
            assertThat(loadedVisSettings.getVisualisation().getUuid()).isNotNull();
        }
    }

    private <T extends Doc> T first(final DocumentStore<T> store) {
        final Set<DocRef> set = store.listDocuments();
        if (set != null && set.size() > 0) {
            return store.readDocument(set.iterator().next());
        }
        return null;
    }

    private void deleteAllAndCheck() {
        clean(true);

        assertThat(visualisationStore.list().size()).isEqualTo(0);
        assertThat(pipelineStore.list().size()).isEqualTo(0);
        assertThat(indexStore.list().size()).isEqualTo(0);
        assertThat(dictionaryStore.list().size()).isEqualTo(0);
        assertThat(dashboardStore.list().size()).isEqualTo(0);
    }

    private ExpressionOperator.Builder createExpression(final DictionaryDoc dictionary) {
        final ExpressionOperator.Builder root = new ExpressionOperator.Builder(Op.AND);
        root.addTerm("EventTime", Condition.LESS_THAN, "2020-01-01T00:00:00.000Z");
        root.addDictionaryTerm("User", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dictionary));
        return root;
    }
}
