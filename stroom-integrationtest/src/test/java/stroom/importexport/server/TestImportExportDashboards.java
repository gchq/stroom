/*
 * Copyright 2016 Crown Copyright
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
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardService;
import stroom.dashboard.shared.FindDashboardCriteria;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.Res;
import stroom.feed.shared.FeedService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.query.shared.Condition;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.QueryData;
import stroom.query.shared.TableSettings;
import stroom.query.shared.VisDashboardSettings;
import stroom.resource.server.ResourceStore;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;
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

        final Folder group1 = folderService.create(null, "Group1");
        final Folder group2 = folderService.create(null, "Group2");

        Assert.assertEquals(2, commonTestControl.countEntity(Folder.class));

        Visualisation vis = null;
        if (!skipVisCreation) {
            final Res res = new Res();
            res.setData("Test Data");

            Script script = scriptService.create(DocRefUtil.create(group2), "Test Script");
            script.setResource(res);
            script = scriptService.save(script);

            vis = visualisationService.create(DocRefUtil.create(group2), "Test Vis");
            vis.setScriptRef(DocRefUtil.create(script));
            vis = visualisationService.save(vis);
            Assert.assertEquals(1, commonTestControl.countEntity(Visualisation.class));
        }

        PipelineEntity pipelineEntity = pipelineEntityService.create(DocRefUtil.create(group1), "Test Pipeline");
        pipelineEntity = pipelineEntityService.save(pipelineEntity);
        Assert.assertEquals(1, commonTestControl.countEntity(PipelineEntity.class));

        Index index = indexService.create(DocRefUtil.create(group1), "Test Index");
        index = indexService.save(index);
        Assert.assertEquals(1, commonTestControl.countEntity(Index.class));

        Dictionary dictionary = dictionaryService.create(DocRefUtil.create(group1), "Test Dictionary");
        dictionary = dictionaryService.save(dictionary);
        Assert.assertEquals(1, commonTestControl.countEntity(Dictionary.class));

        // Create query.
        final QueryData queryData = new QueryData();
        queryData.setDataSource(DocRefUtil.create(index));
        queryData.setExpression(createExpression(dictionary));

        final ComponentConfig query = new ComponentConfig();
        query.setId("query-1234");
        query.setName("Query");
        query.setType("query");
        query.setSettings(queryData);

        // Create table.
        final TableSettings tableSettings = new TableSettings();
        tableSettings.setExtractValues(true);
        tableSettings.setExtractionPipeline(DocRefUtil.create(pipelineEntity));

        final ComponentConfig table = new ComponentConfig();
        table.setId("table-1234");
        table.setName("Table");
        table.setType("table");
        table.setSettings(tableSettings);

        // Create visualisation.
        final VisDashboardSettings visSettings = new VisDashboardSettings();
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

        Dashboard dashboard = dashboardService.create(DocRefUtil.create(group1), "Test Dashboard");
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
        final FindFolderCriteria criteria = new FindFolderCriteria();
        criteria.getFolderIdSet().add(group1);
        if (!skipVisExport) {
            criteria.getFolderIdSet().add(group2);
        } else {
            startFolderSize = 1;
            startVisualisationSize = 0;
        }

        // Export all
        importExportService.exportConfig(criteria, resourceStore.getTempFile(file), false, null);

        final ResourceKey exportConfig = resourceStore.createTempFile("ExportPlain.zip");

        importExportService.exportConfig(criteria, resourceStore.getTempFile(exportConfig), false, null);

        if (!update) {
            // Delete everything.
            deleteAllAndCheck();
        }

        // Import All
        final List<EntityActionConfirmation> confirmations = importExportService
                .createImportConfirmationList(resourceStore.getTempFile(file));

        for (final EntityActionConfirmation confirmation : confirmations) {
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
        final QueryData loadedQueryData = (QueryData) loadedQuery.getSettings();
        final ComponentConfig loadedTable = loadedComponents.get(1);
        final TableSettings loadedTableSettings = (TableSettings) loadedTable.getSettings();
        final ComponentConfig loadedVis = loadedComponents.get(2);
        final VisDashboardSettings loadedVisSettings = (VisDashboardSettings) loadedVis.getSettings();

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

    private ExpressionOperator createExpression(final Dictionary dictionary) {
        final ExpressionOperator root = new ExpressionOperator(Op.AND);
        root.addChild(new ExpressionTerm("EventTime", Condition.LESS_THAN, "2020-01-01T00:00:00.000Z"));
        root.addChild(new ExpressionTerm("User", Condition.IN_DICTIONARY, DocRefUtil.create(dictionary)));
        return root;
    }
}
