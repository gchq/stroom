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

import org.junit.Assert;
import org.junit.Test;
import stroom.dashboard.DashboardStore;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dictionary.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.shared.Doc;
import stroom.document.DocumentStore;
import stroom.entity.shared.DocRefs;
import stroom.explorer.ExplorerNodeService;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.FeedService;
import stroom.importexport.shared.ImportState;
import stroom.index.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.resource.ResourceStore;
import stroom.script.ScriptStore;
import stroom.script.shared.ScriptDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.shared.ResourceKey;
import stroom.visualisation.VisualisationStore;
import stroom.visualisation.shared.VisualisationDoc;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TestImportExportDashboards extends AbstractCoreIntegrationTest {
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
    public void testComplete() {
        test(false, false, false);
    }

    @Test
    public void testUpdate() {
        test(false, false, true);
    }

    @Test
    public void testSkipVisCreation() {
        test(true, false, false);
    }

    @Test
    public void testSkipVisExport() {
        test(false, true, false);
    }

    @Test
    public void testSkipVisCreationAndExport() {
        test(true, true, false);
    }

    private void test(final boolean skipVisCreation, final boolean skipVisExport, final boolean update) {
        deleteAllAndCheck();

        final DocRef folder1 = explorerService.create(ExplorerConstants.FOLDER, "Group1", null, null);
        final DocRef folder2 = explorerService.create(ExplorerConstants.FOLDER, "Group2", null, null);

        final List<ExplorerNode> nodes = explorerNodeService.getDescendants(null);
        Assert.assertEquals(3, nodes.size());

        DocRef visRef = null;
        if (!skipVisCreation) {
            final DocRef scriptRef = explorerService.create(ScriptDoc.DOCUMENT_TYPE, "Test Script", folder2, null);
            ScriptDoc script = scriptStore.readDocument(scriptRef);
            script.setData("Test Data");
            scriptStore.writeDocument(script);
            Assert.assertEquals(1, scriptStore.list().size());

            visRef = explorerService.create(VisualisationDoc.DOCUMENT_TYPE, "Test Vis", folder2, null);
            final VisualisationDoc vis = visualisationStore.readDocument(visRef);
            vis.setScriptRef(scriptRef);
            visualisationStore.writeDocument(vis);
            Assert.assertEquals(1, visualisationStore.list().size());
        }

        final DocRef pipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "Test Pipeline", folder1, null);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        pipelineStore.writeDocument(pipelineDoc);
        Assert.assertEquals(1, pipelineStore.list().size());

        final DocRef indexRef = explorerService.create(IndexDoc.DOCUMENT_TYPE, "Test Index", folder1, null);
        final IndexDoc index = indexStore.readDocument(indexRef);
        indexStore.writeDocument(index);
        Assert.assertEquals(1, indexStore.list().size());

        final DocRef dictionaryRef = explorerService.create(DictionaryDoc.ENTITY_TYPE, "Test Dictionary", folder1, null);
        final DictionaryDoc dictionary = dictionaryStore.readDocument(dictionaryRef);
        dictionaryStore.writeDocument(dictionary);
        Assert.assertEquals(1, dictionaryStore.list().size());

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
        Assert.assertEquals(1, dashboardStore.list().size());

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

        Assert.assertEquals(startVisualisationSize, visualisationStore.list().size());
        Assert.assertEquals(startPipelineSize, pipelineStore.list().size());
        Assert.assertEquals(startIndexSize, indexStore.list().size());
        Assert.assertEquals(startDictionarySize, dictionaryStore.list().size());
        Assert.assertEquals(startDashboardSize, dashboardStore.list().size());

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
        Assert.assertEquals(loadedIndex, loadedQueryData.getDataSource());
        Assert.assertEquals(stroom.docstore.shared.DocRefUtil.create(loadedDictionary),
                ((ExpressionTerm) loadedQueryData.getExpression().getChildren().get(1)).getDictionary());
        Assert.assertEquals(loadedPipeline, loadedTableSettings.getExtractionPipeline());

        if (!skipVisExport || skipVisCreation) {
            Assert.assertEquals(stroom.docstore.shared.DocRefUtil.create(loadedVisualisation), loadedVisSettings.getVisualisation());
        } else {
            Assert.assertNotNull(loadedVisSettings.getVisualisation());
            Assert.assertNotNull(loadedVisSettings.getVisualisation().getType());
            Assert.assertNotNull(loadedVisSettings.getVisualisation().getUuid());
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

        Assert.assertEquals(0, visualisationStore.list().size());
        Assert.assertEquals(0, pipelineStore.list().size());
        Assert.assertEquals(0, indexStore.list().size());
        Assert.assertEquals(0, dictionaryStore.list().size());
        Assert.assertEquals(0, dashboardStore.list().size());
    }

    private ExpressionOperator.Builder createExpression(final DictionaryDoc dictionary) {
        final ExpressionOperator.Builder root = new ExpressionOperator.Builder(Op.AND);
        root.addTerm("EventTime", Condition.LESS_THAN, "2020-01-01T00:00:00.000Z");
        root.addDictionaryTerm("User", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dictionary));
        return root;
    }
}
