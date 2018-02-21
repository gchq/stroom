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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.importexport.shared.ImportState.State;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.ComparisonHelper;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.xmlschema.XMLSchemaService;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;

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
    private PipelineService pipelineService;
    @Resource
    private ExplorerService explorerService;

    private DocRefs buildFindFolderCriteria() {
        final DocRefs criteria = new DocRefs();
        criteria.add(ExplorerConstants.ROOT_DOC_REF);
        return criteria;
    }

    @Test
    public void testExport() throws IOException {
        final DocRef docRef = explorerService.create(Feed.ENTITY_TYPE, FileSystemTestUtil.getUniqueTestString(), null, null);
        Feed eventFeed = feedService.readDocument(docRef);
        eventFeed.setDescription("Original Description");
        feedService.save(eventFeed);

        commonTestControl.createRequiredXMLSchemas();

        BaseResultList<XMLSchema> allSchemas = xmlSchemaService.find(new FindXMLSchemaCriteria());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, new ArrayList<>());

        List<ImportState> list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);
        Assert.assertTrue(list.size() > 0);

        // Should all be relative
        Map<DocRef, ImportState> map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        Assert.assertEquals(State.EQUAL, map.get(docRef).getState());

        eventFeed = feedService.readDocument(docRef);
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
        final DocRef folder = explorerService.create(ExplorerConstants.FOLDER, FileSystemTestUtil.getUniqueTestString(), null, null);
        final DocRef parentPipelineRef = explorerService.create(PipelineEntity.ENTITY_TYPE, "Parent", folder, null);
        final PipelineEntity parentPipeline = pipelineService.readDocument(parentPipelineRef);

        final DocRef childPipelineRef = explorerService.create(PipelineEntity.ENTITY_TYPE, "Child", folder, null);
        final PipelineEntity childPipeline = pipelineService.readDocument(childPipelineRef);
        childPipeline.setParentPipeline(DocRefUtil.create(parentPipeline));
        pipelineService.save(childPipeline);

        Assert.assertEquals(2, pipelineService.find(new FindPipelineEntityCriteria()).size());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, new ArrayList<>());

        final String fileNamePrefix = ImportExportFileNameUtil.createFilePrefix(childPipelineRef);
        final String fileName = fileNamePrefix + ".xml";
        final Path path = testDataDir.resolve(folder.getName()).resolve(fileName);
        final String childXml = new String(Files.readAllBytes(path), StreamUtil.DEFAULT_CHARSET);

        Assert.assertTrue("Parent reference not serialised\n" + childXml,
                childXml.contains("&lt;name&gt;Parent&lt;/name&gt;"));

        // Remove all entities from the database.
        clean(true);

        Assert.assertEquals(0, pipelineService.find(new FindPipelineEntityCriteria()).size());

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        Assert.assertEquals(2, pipelineService.find(new FindPipelineEntityCriteria()).size());
    }

    @Test
    public void test() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config");
        final Path outDir = StroomCoreServerTestFileUtil.getTestOutputDir().resolve("samples/config");

        FileUtil.deleteDir(outDir);
        Files.createDirectories(outDir);

        // Read input.
        importExportSerializer.read(inDir, null, ImportMode.IGNORE_CONFIRMATION);

        // Write to output.
        importExportSerializer.write(outDir, buildFindFolderCriteria(), true, new ArrayList<>());

        // Compare input and output directory.
        ComparisonHelper.compareDirs(inDir, outDir);

        // If the comparison was ok then delete the output.
        FileUtil.deleteDir(outDir);
    }
}
