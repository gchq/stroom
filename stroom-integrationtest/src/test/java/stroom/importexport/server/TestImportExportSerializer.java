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
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.importexport.server.ImportExportSerializer.ImportMode;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.test.ComparisonHelper;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestImportExportSerializer extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private FeedService feedService;
    @Resource
    private XMLSchemaService xmlSchemaService;
    @Resource
    private ImportExportSerializer importExportSerializer;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private FolderService folderService;

    private FindFolderCriteria buildFindFolderCriteria() {
        final FindFolderCriteria criteria = new FindFolderCriteria();
        criteria.getFolderIdSet().setDeep(true);
        criteria.getFolderIdSet().setMatchNull(Boolean.TRUE);
        return criteria;
    }

    @Test
    public void testExport() {
        final Feed eventFeed = commonTestScenarioCreator.createSimpleFeed();
        final Folder folder = folderService.load(eventFeed.getFolder());

        final String eventFeedPath = "/" + folder.getName() + "/" + eventFeed.getName();

        commonTestControl.createRequiredXMLSchemas();

        BaseResultList<XMLSchema> allSchemas = xmlSchemaService.find(new FindXMLSchemaCriteria());

        final File testDataDir = new File(getCurrentTestDir(), "ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        FileSystemUtil.mkdirs(null, testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, false, null);

        List<EntityActionConfirmation> list = new ArrayList<>();
        Map<String, EntityActionConfirmation> map = null;

        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);

        // Shoudl all be relative
        for (final EntityActionConfirmation confirmation : list) {
            Assert.assertTrue("Expected to start with / " + confirmation.getPath(),
                    confirmation.getPath().startsWith("/"));
        }
        map = EntityActionConfirmation.asMap(list);

        Assert.assertTrue(list.size() > 0);
        Assert.assertEquals(EntityAction.EQUAL, map.get(eventFeedPath).getEntityAction());

        eventFeed.setDescription("New Description");
        feedService.save(eventFeed);
        for (final XMLSchema xmlSchema : allSchemas) {
            xmlSchema.setData("XML");
            xmlSchemaService.save(xmlSchema);
        }

        list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);
        map = EntityActionConfirmation.asMap(list);

        Assert.assertTrue(list.size() > 0);
        Assert.assertEquals(EntityAction.UPDATE, map.get(eventFeedPath).getEntityAction());
        Assert.assertTrue(map.get(eventFeedPath).getUpdatedFieldList().contains("description"));

        // Remove all entities from the database.
        clean(true);

        list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);

        Assert.assertTrue(list.size() > 0);
        Assert.assertEquals(EntityAction.ADD, list.get(0).getEntityAction());
        Assert.assertEquals(EntityAction.ADD, list.get(1).getEntityAction());

        importExportSerializer.read(testDataDir, list, ImportMode.IGNORE_CONFIRMATION);
        allSchemas = xmlSchemaService.find(new FindXMLSchemaCriteria());

        for (final XMLSchema xmlSchema : allSchemas) {
            Assert.assertNotSame("XML", xmlSchemaService.load(xmlSchema).getData());
        }

        Assert.assertNotNull(testDataDir);
    }

    @Test
    public void testPipeline() {
        final DocRef folder = DocRefUtil.create(folderService.create(null, FileSystemTestUtil.getUniqueTestString()));
        final PipelineEntity parentPipeline = pipelineEntityService.create(folder, "Parent");

        final PipelineEntity childPipeline = pipelineEntityService.create(folder, "Child");
        childPipeline.setParentPipeline(DocRefUtil.create(parentPipeline));
        pipelineEntityService.save(childPipeline);

        Assert.assertEquals(2, pipelineEntityService.find(new FindPipelineEntityCriteria()).size());

        final File testDataDir = new File(getCurrentTestDir(), "ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        FileSystemUtil.mkdirs(null, testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, false, null);

        final String childXml = StreamUtil
                .fileToString(new File(new File(testDataDir, folder.getName()), "Child.Pipeline.xml"));

        Assert.assertTrue("Parent reference not serialised\n" + childXml,
                childXml.contains("&lt;name&gt;Parent&lt;/name&gt;"));

        // Remove all entities from the database.
        clean(true);

        Assert.assertEquals(0, pipelineEntityService.find(new FindPipelineEntityCriteria()).size());

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        Assert.assertEquals(2, pipelineEntityService.find(new FindPipelineEntityCriteria()).size());
    }

    @Test
    public void test() {
        final File inDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "samples/config");
        final File outDir = new File(StroomCoreServerTestFileUtil.getTestOutputDir(), "samples/config");

        FileSystemUtil.deleteDirectory(outDir);
        FileSystemUtil.mkdirs(null, outDir);

        // Read input.
        importExportSerializer.read(inDir, null, ImportMode.IGNORE_CONFIRMATION);

        // Write to output.
        importExportSerializer.write(outDir, buildFindFolderCriteria(), true, false, null);

        // Compare input and output directory.
        ComparisonHelper.compareDirs(inDir, outDir);

        // If the comparison was ok then delete the output.
        FileSystemUtil.deleteDirectory(outDir);
    }
}
