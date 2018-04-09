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
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.FindDashboardCriteria;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dictionary.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.Res;
import stroom.explorer.ExplorerNodeService;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.FeedService;
import stroom.importexport.shared.ImportState;
import stroom.index.IndexService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.resource.ResourceStore;
import stroom.script.ScriptService;
import stroom.script.shared.Script;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.shared.ResourceKey;
import stroom.visualisation.VisualisationService;
import stroom.visualisation.shared.FindVisualisationCriteria;
import stroom.visualisation.shared.Visualisation;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class TestImportExportDashboards extends AbstractCoreIntegrationTest {
    @Inject
    private ImportExportService importExportService;
    @Inject
    private ResourceStore resourceStore;
    @Inject
    private PipelineService pipelineService;
    @Inject
    private FeedService feedService;
    @Inject
    private VisualisationService visualisationService;
    @Inject
    private ScriptService scriptService;
    @Inject
    private IndexService indexService;
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

        Visualisation vis = null;
        if (!skipVisCreation) {
            final Res res = new Res();
            res.setData("Test Data");

            final DocRef scriptRef = explorerService.create(Script.ENTITY_TYPE, "Test Script", folder2, null);
            Script script = scriptService.readDocument(scriptRef);
            script.setResource(res);
            script = scriptService.save(script);

            final DocRef visRef = explorerService.create(Visualisation.ENTITY_TYPE, "Test Vis", folder2, null);
            vis = visualisationService.readDocument(visRef);
            vis.setScriptRef(DocRefUtil.create(script));
            vis = visualisationService.save(vis);
            Assert.assertEquals(1, commonTestControl.countEntity(Visualisation.class));
        }

        final DocRef pipelineRef = explorerService.create(PipelineEntity.ENTITY_TYPE, "Test Pipeline", folder1, null);
        PipelineEntity pipelineEntity = pipelineService.readDocument(pipelineRef);
        pipelineEntity = pipelineService.save(pipelineEntity);
        Assert.assertEquals(1, commonTestControl.countEntity(PipelineEntity.class));

        final DocRef indexRef = explorerService.create(Index.ENTITY_TYPE, "Test Index", folder1, null);
        Index index = indexService.readDocument(indexRef);
        index = indexService.save(index);
        Assert.assertEquals(1, commonTestControl.countEntity(Index.class));

        final DocRef dictionaryRef = explorerService.create(DictionaryDoc.ENTITY_TYPE, "Test Dictionary", folder1, null);
        DictionaryDoc dictionary = dictionaryStore.readDocument(dictionaryRef);
        dictionaryStore.update(dictionary);
//        Assert.assertEquals(1, commonTestControl.countEntity(DictionaryDoc.class));

        // Create query.
        final QueryComponentSettings queryComponentSettings = new QueryComponentSettings();
        queryComponentSettings.setDataSource(DocRefUtil.create(index));
        queryComponentSettings.setExpression(createExpression(dictionary).build());

        final ComponentConfig query = new ComponentConfig();
        query.setId("query-1234");
        query.setName("Query");
        query.setType("query");
        query.setSettings(queryComponentSettings);

        // Create table.
        final TableComponentSettings tableSettings = new TableComponentSettings();
        tableSettings.setExtractValues(true);
        tableSettings.setExtractionPipeline(DocRefUtil.create(pipelineEntity));

        final ComponentConfig table = new ComponentConfig();
        table.setId("table-1234");
        table.setName("Table");
        table.setType("table");
        table.setSettings(tableSettings);

        // Create visualisation.
        final VisComponentSettings visSettings = new VisComponentSettings();
        visSettings.setTableId("table-1234");
        visSettings.setTableSettings(tableSettings);
        visSettings.setVisualisation(DocRefUtil.create(vis));

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

//        int startFolderSize = commonTestControl.countEntity(Folder.class);
        int startVisualisationSize = commonTestControl.countEntity(Visualisation.class);
        final int startPipelineSize = commonTestControl.countEntity(PipelineEntity.class);
        final int startIndexSize = commonTestControl.countEntity(Index.class);
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

//        Assert.assertEquals(startFolderSize, commonTestControl.countEntity(Folder.class));
        Assert.assertEquals(startVisualisationSize, commonTestControl.countEntity(Visualisation.class));
        Assert.assertEquals(startPipelineSize, commonTestControl.countEntity(PipelineEntity.class));
        Assert.assertEquals(startIndexSize, commonTestControl.countEntity(Index.class));
        Assert.assertEquals(startDictionarySize, dictionaryStore.list().size());
        Assert.assertEquals(startDashboardSize, dashboardStore.list().size());

        // Load the dashboard.
        final Visualisation loadedVisualisation = visualisationService.find(new FindVisualisationCriteria()).getFirst();
        final PipelineEntity loadedPipeline = pipelineService.find(new FindPipelineEntityCriteria()).getFirst();
        final Index loadedIndex = indexService.find(new FindIndexCriteria()).getFirst();
        final DictionaryDoc loadedDictionary = dictionaryStore.readDocument(dictionaryStore.list().iterator().next());
        final DashboardDoc loadedDashboard = dashboardStore.readDocument(dashboardStore.list().iterator().next());
        final List<ComponentConfig> loadedComponents = loadedDashboard.getDashboardConfig().getComponents();
        final ComponentConfig loadedQuery = loadedComponents.get(0);
        final QueryComponentSettings loadedQueryData = (QueryComponentSettings) loadedQuery.getSettings();
        final ComponentConfig loadedTable = loadedComponents.get(1);
        final TableComponentSettings loadedTableSettings = (TableComponentSettings) loadedTable.getSettings();
        final ComponentConfig loadedVis = loadedComponents.get(2);
        final VisComponentSettings loadedVisSettings = (VisComponentSettings) loadedVis.getSettings();

        // Verify all entity references have been restored.
        Assert.assertEquals(DocRefUtil.create(loadedIndex), loadedQueryData.getDataSource());
        Assert.assertEquals(stroom.docstore.shared.DocRefUtil.create(loadedDictionary),
                ((ExpressionTerm) loadedQueryData.getExpression().getChildren().get(1)).getDictionary());
        Assert.assertEquals(DocRefUtil.create(loadedPipeline), loadedTableSettings.getExtractionPipeline());

        if (!skipVisExport || skipVisCreation) {
            Assert.assertEquals(DocRefUtil.create(loadedVisualisation), loadedVisSettings.getVisualisation());
        } else {
            Assert.assertNotNull(loadedVisSettings.getVisualisation());
            Assert.assertNotNull(loadedVisSettings.getVisualisation().getType());
            Assert.assertNotNull(loadedVisSettings.getVisualisation().getUuid());
        }
    }

    private void deleteAllAndCheck() {
        clean(true);

//        Assert.assertEquals(0, commonTestControl.countEntity(Folder.class));
        Assert.assertEquals(0, commonTestControl.countEntity(Visualisation.class));
        Assert.assertEquals(0, commonTestControl.countEntity(PipelineEntity.class));
        Assert.assertEquals(0, commonTestControl.countEntity(Index.class));
        Assert.assertEquals(0, dictionaryStore.list().size());
        Assert.assertEquals(0, commonTestControl.countEntity(DashboardDoc.class));
    }

    private ExpressionOperator.Builder createExpression(final DictionaryDoc dictionary) {
        final ExpressionOperator.Builder root = new ExpressionOperator.Builder(Op.AND);
        root.addTerm("EventTime", Condition.LESS_THAN, "2020-01-01T00:00:00.000Z");
        root.addDictionaryTerm("User", Condition.IN_DICTIONARY, stroom.docstore.shared.DocRefUtil.create(dictionary));
        return root;
    }
}
