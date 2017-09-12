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
 */

package stroom.importexport.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardService;
import stroom.dashboard.shared.FindDashboardCriteria;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.Res;
import stroom.feed.shared.FeedService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.query.api.v2.ExpressionBuilder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.resource.server.ResourceStore;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.shared.ResourceKey;
import stroom.visualisation.shared.FindVisualisationCriteria;
import stroom.visualisation.shared.Visualisation;
import stroom.visualisation.shared.VisualisationService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

public class TestImportExportDashboards extends AbstractCoreIntegrationTest {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ResourceStore resourceStore;
    @Resource
    private FolderService folderService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private FeedService feedService;
    @Resource
    private VisualisationService visualisationService;
    @Resource
    private ScriptService scriptService;
    @Resource
    private IndexService indexService;
    @Resource
    private DictionaryService dictionaryService;
    @Resource
    private DashboardService dashboardService;
    @Resource
    private CommonTestControl commonTestControl;

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

        final Folder folder1 = folderService.create(null, "Group1");
        final Folder folder2 = folderService.create(null, "Group2");

        Assert.assertEquals(2, commonTestControl.countEntity(Folder.class));

        Visualisation vis = null;
        if (!skipVisCreation) {
            final Res res = new Res();
            res.setData("Test Data");

            Script script = scriptService.create(DocRefUtil.create(folder2), "Test Script");
            script.setResource(res);
            script = scriptService.save(script);

            vis = visualisationService.create(DocRefUtil.create(folder2), "Test Vis");
            vis.setScriptRef(DocRefUtil.create(script));
            vis = visualisationService.save(vis);
            Assert.assertEquals(1, commonTestControl.countEntity(Visualisation.class));
        }

        PipelineEntity pipelineEntity = pipelineEntityService.create(DocRefUtil.create(folder1), "Test Pipeline");
        pipelineEntity = pipelineEntityService.save(pipelineEntity);
        Assert.assertEquals(1, commonTestControl.countEntity(PipelineEntity.class));

        Index index = indexService.create(DocRefUtil.create(folder1), "Test Index");
        index = indexService.save(index);
        Assert.assertEquals(1, commonTestControl.countEntity(Index.class));

        Dictionary dictionary = dictionaryService.create(DocRefUtil.create(folder1), "Test Dictionary");
        dictionary = dictionaryService.save(dictionary);
        Assert.assertEquals(1, commonTestControl.countEntity(Dictionary.class));

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

        Dashboard dashboard = dashboardService.create(DocRefUtil.create(folder1), "Test Dashboard");
        dashboard.setDashboardData(dashboardData);
        dashboard = dashboardService.save(dashboard);
        Assert.assertEquals(1, commonTestControl.countEntity(Dashboard.class));

        int startFolderSize = commonTestControl.countEntity(Folder.class);
        int startVisualisationSize = commonTestControl.countEntity(Visualisation.class);
        final int startPipelineSize = commonTestControl.countEntity(PipelineEntity.class);
        final int startIndexSize = commonTestControl.countEntity(Index.class);
        final int startDictionarySize = commonTestControl.countEntity(Dictionary.class);
        final int startDashboardSize = commonTestControl.countEntity(Dashboard.class);

        final ResourceKey file = resourceStore.createTempFile("Export.zip");
        final DocRefs docRefs = new DocRefs();
        docRefs.add(DocRefUtil.create(folder1));
        if (!skipVisExport) {
            docRefs.add(DocRefUtil.create(folder2));
        } else {
            startFolderSize = 1;
            startVisualisationSize = 0;
        }

        // Export all
        importExportService.exportConfig(docRefs, resourceStore.getTempFile(file), null);

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(docRefs, resourceStore.getTempFile(exportConfig), null);

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

        Assert.assertEquals(startFolderSize, commonTestControl.countEntity(Folder.class));
        Assert.assertEquals(startVisualisationSize, commonTestControl.countEntity(Visualisation.class));
        Assert.assertEquals(startPipelineSize, commonTestControl.countEntity(PipelineEntity.class));
        Assert.assertEquals(startIndexSize, commonTestControl.countEntity(Index.class));
        Assert.assertEquals(startDictionarySize, commonTestControl.countEntity(Dictionary.class));
        Assert.assertEquals(startDashboardSize, commonTestControl.countEntity(Dashboard.class));

        // Load the dashboard.
        final Folder loadedFolder = folderService.find(new FindFolderCriteria()).getFirst();
        final Visualisation loadedVisualisation = visualisationService.find(new FindVisualisationCriteria()).getFirst();
        final PipelineEntity loadedPipeline = pipelineEntityService.find(new FindPipelineEntityCriteria()).getFirst();
        final Index loadedIndex = indexService.find(new FindIndexCriteria()).getFirst();
        final Dictionary loadedDictionary = dictionaryService.find(new FindDictionaryCriteria()).getFirst();
        final Dashboard loadedDashboard = dashboardService.find(new FindDashboardCriteria()).getFirst();
        final List<ComponentConfig> loadedComponents = loadedDashboard.getDashboardData().getComponents();
        final ComponentConfig loadedQuery = loadedComponents.get(0);
        final QueryComponentSettings loadedQueryData = (QueryComponentSettings) loadedQuery.getSettings();
        final ComponentConfig loadedTable = loadedComponents.get(1);
        final TableComponentSettings loadedTableSettings = (TableComponentSettings) loadedTable.getSettings();
        final ComponentConfig loadedVis = loadedComponents.get(2);
        final VisComponentSettings loadedVisSettings = (VisComponentSettings) loadedVis.getSettings();

        // Verify all entity references have been restored.
        Assert.assertEquals(DocRefUtil.create(loadedIndex), loadedQueryData.getDataSource());
        Assert.assertEquals(DocRefUtil.create(loadedDictionary),
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

        Assert.assertEquals(0, commonTestControl.countEntity(Folder.class));
        Assert.assertEquals(0, commonTestControl.countEntity(Visualisation.class));
        Assert.assertEquals(0, commonTestControl.countEntity(PipelineEntity.class));
        Assert.assertEquals(0, commonTestControl.countEntity(Index.class));
        Assert.assertEquals(0, commonTestControl.countEntity(Dictionary.class));
        Assert.assertEquals(0, commonTestControl.countEntity(Dashboard.class));
    }

    private ExpressionBuilder createExpression(final Dictionary dictionary) {
        final ExpressionBuilder root = new ExpressionBuilder(Op.AND);
        root.addTerm("EventTime", Condition.LESS_THAN, "2020-01-01T00:00:00.000Z");
        root.addDictionaryTerm("User", Condition.IN_DICTIONARY, DocRefUtil.create(dictionary));
        return root;
    }
}
