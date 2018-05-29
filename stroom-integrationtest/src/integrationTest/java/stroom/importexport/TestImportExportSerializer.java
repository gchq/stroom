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
import stroom.entity.shared.DocRefs;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.FeedStore;
import stroom.streamstore.FeedService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.importexport.shared.ImportState.State;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.docref.DocRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.ComparisonHelper;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.xmlschema.XmlSchemaStore;
import stroom.xmlschema.shared.XmlSchemaDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestImportExportSerializer extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private FeedStore feedStore;
    @Inject
    private XmlSchemaStore xmlSchemaStore;
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private ExplorerService explorerService;

    private DocRefs buildFindFolderCriteria() {
        final DocRefs criteria = new DocRefs();
        criteria.add(ExplorerConstants.ROOT_DOC_REF);
        return criteria;
    }

    @Test
    public void testExport() throws IOException {
        final DocRef docRef = explorerService.create(FeedDoc.DOCUMENT_TYPE, FileSystemTestUtil.getUniqueTestString(), null, null);
        FeedDoc eventFeed = feedStore.readDocument(docRef);
        eventFeed.setDescription("Original Description");
        feedStore.writeDocument(eventFeed);

        commonTestControl.createRequiredXMLSchemas();

        List<DocRef> allSchemas = xmlSchemaStore.list();

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

        eventFeed = feedStore.readDocument(docRef);
        eventFeed.setDescription("New Description");
        feedStore.writeDocument(eventFeed);
        for (final DocRef ref : allSchemas) {
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            xmlSchema.setData("XML");
            xmlSchemaStore.writeDocument(xmlSchema);
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
        allSchemas = xmlSchemaStore.list();

        for (final DocRef ref : allSchemas) {
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            Assert.assertNotSame("XML", xmlSchema.getData());
        }

        Assert.assertNotNull(testDataDir);
    }

    @Test
    public void testPipeline() throws IOException {
        final DocRef folder = explorerService.create(ExplorerConstants.FOLDER, FileSystemTestUtil.getUniqueTestString(), null, null);
        final DocRef parentPipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "Parent", folder, null);
        final DocRef childPipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "Child", folder, null);
        final PipelineDoc childPipeline = pipelineStore.readDocument(childPipelineRef);
        childPipeline.setParentPipeline(parentPipelineRef);
        pipelineStore.writeDocument(childPipeline);

        Assert.assertEquals(2, pipelineStore.list().size());

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, new ArrayList<>());

        final String fileNamePrefix = ImportExportFileNameUtil.createFilePrefix(childPipelineRef);
        final String fileName = fileNamePrefix + ".meta";
        final Path path = testDataDir.resolve(folder.getName()).resolve(fileName);
        final String childJson = new String(Files.readAllBytes(path), StreamUtil.DEFAULT_CHARSET);

        Assert.assertTrue("Parent reference not serialised\n" + childJson,
                childJson.contains("\"name\" : \"Parent\""));

        // Remove all entities from the database.
        clean(true);

        Assert.assertEquals(0, pipelineStore.list().size());

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        Assert.assertEquals(2, pipelineStore.list().size());
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
