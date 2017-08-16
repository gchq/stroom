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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.ImportState.State;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.ComparisonHelper;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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

    private DocRefs buildFindFolderCriteria() {
        final DocRefs criteria = new DocRefs();
        criteria.add(new DocRef(Folder.ENTITY_TYPE, "0", "System"));
        return criteria;
    }

    @Test
    public void testExport() throws IOException {
        final Feed eventFeed = commonTestScenarioCreator.createSimpleFeed();
        final DocRef docRef = DocRefUtil.create(eventFeed);
//        final Folder folder = folderService.load(eventFeed.getFolder());

//        final String eventFeedPath = folder.getName() + "/" + eventFeed.getName();

        commonTestControl.createRequiredXMLSchemas();

        BaseResultList<XMLSchema> allSchemas = xmlSchemaService.find(new FindXMLSchemaCriteria());

        final Path testDataDir = getCurrentTestPath().resolve("ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, null);

        List<ImportState> list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);

        // Should all be relative
        Map<DocRef, ImportState> map = new HashMap<>();
        for (final ImportState confirmation : list) {
//            Assert.assertTrue("Expected to start with / " + confirmation.getSourcePath(),
//                    confirmation.getSourcePath().startsWith("/"));
            map.put(confirmation.getDocRef(), confirmation);
        }

        Assert.assertTrue(list.size() > 0);
        Assert.assertEquals(State.EQUAL, map.get(docRef).getState());

        eventFeed.setDescription("New Description");
        feedService.save(eventFeed);
        for (final XMLSchema xmlSchema : allSchemas) {
            xmlSchema.setData("XML");
            xmlSchemaService.save(xmlSchema);
        }

        list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);

        map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        Assert.assertTrue(list.size() > 0);
        Assert.assertEquals(State.UPDATE, map.get(docRef).getState());
        Assert.assertTrue(map.get(docRef).getUpdatedFieldList().contains("description"));

        // Remove all entities from the database.
        clean(true);

        list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);

        Assert.assertTrue(list.size() > 0);
        Assert.assertEquals(State.NEW, list.get(0).getState());
        Assert.assertEquals(State.NEW, list.get(1).getState());

        importExportSerializer.read(testDataDir, list, ImportMode.IGNORE_CONFIRMATION);
        allSchemas = xmlSchemaService.find(new FindXMLSchemaCriteria());

        for (final XMLSchema xmlSchema : allSchemas) {
            Assert.assertNotSame("XML", xmlSchemaService.load(xmlSchema).getData());
        }

        Assert.assertNotNull(testDataDir);
    }

    @Test
    public void testPipeline() throws IOException {
        final DocRef folder = DocRefUtil.create(folderService.create(null, FileSystemTestUtil.getUniqueTestString()));
        final PipelineEntity parentPipeline = pipelineEntityService.create(folder, "Parent");

        final PipelineEntity childPipeline = pipelineEntityService.create(folder, "Child");
        childPipeline.setParentPipeline(DocRefUtil.create(parentPipeline));
        pipelineEntityService.save(childPipeline);

        Assert.assertEquals(2, pipelineEntityService.find(new FindPipelineEntityCriteria()).size());

        final Path testDataDir = getCurrentTestPath().resolve("ExportTest");

        FileSystemUtil.deleteDirectory(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, null);

        final String childXml = new String(Files.readAllBytes(testDataDir.resolve(folder.getName()).resolve("Child.Pipeline.xml")));

        Assert.assertTrue("Parent reference not serialised\n" + childXml,
                childXml.contains("&lt;name&gt;Parent&lt;/name&gt;"));

        // Remove all entities from the database.
        clean(true);

        Assert.assertEquals(0, pipelineEntityService.find(new FindPipelineEntityCriteria()).size());

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        Assert.assertEquals(2, pipelineEntityService.find(new FindPipelineEntityCriteria()).size());
    }

    @Test
    public void test() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().toPath().resolve("samples/config");
        final Path outDir = StroomCoreServerTestFileUtil.getTestOutputDir().toPath().resolve("samples/config");

        FileSystemUtil.deleteDirectory(outDir);
        Files.createDirectories(outDir);

        // Read input.
        importExportSerializer.read(inDir, null, ImportMode.IGNORE_CONFIRMATION);

        // Write to output.
        importExportSerializer.write(outDir, buildFindFolderCriteria(), true, null);

        // Compare input and output directory.
        ComparisonHelper.compareDirs(inDir.toFile(), outDir.toFile());

        // If the comparison was ok then delete the output.
        FileSystemUtil.deleteDirectory(outDir);
    }
}
