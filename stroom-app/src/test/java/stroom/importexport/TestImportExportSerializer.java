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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.impl.ImportExportFileNameUtil;
import stroom.importexport.impl.ImportExportSerializer;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.importexport.shared.ImportState.State;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;
import stroom.xmlschema.shared.XmlSchemaDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportSerializer extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestImportExportSerializer.class);

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
    @Inject
    private ProcessorService processorService;
    @Inject
    private ProcessorFilterService processorFilterService;

    private Set<DocRef> buildFindFolderCriteria() {
        final Set<DocRef> criteria = new HashSet<>();
        criteria.add(ExplorerConstants.ROOT_DOC_REF);
        return criteria;
    }

    @Test
    void testExport() throws IOException {
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
        assertThat(list.size() > 0).isTrue();

        // Should all be relative
        Map<DocRef, ImportState> map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        assertThat(map.get(docRef).getState()).isEqualTo(State.EQUAL);

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

        assertThat(list.size() > 0).isTrue();
        assertThat(map.get(docRef).getState()).isEqualTo(State.UPDATE);
        assertThat(map.get(docRef).getUpdatedFieldList().contains("description")).isTrue();

        // Remove all entities from the database.
        clean(true);

        list = new ArrayList<>();
        importExportSerializer.read(testDataDir, list, ImportMode.CREATE_CONFIRMATION);

        assertThat(list.size() > 0).isTrue();
        assertThat(list.get(0).getState()).isEqualTo(State.NEW);
        assertThat(list.get(1).getState()).isEqualTo(State.NEW);

        importExportSerializer.read(testDataDir, list, ImportMode.IGNORE_CONFIRMATION);
        allSchemas = xmlSchemaStore.list();

        for (final DocRef ref : allSchemas) {
            LOGGER.info("Reading doc {}", ref);
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            assertThat(xmlSchema.getData()).isNotSameAs("XML");
        }

        assertThat(testDataDir).isNotNull();
    }

    @Test
    void testPipelineWithProcessorFilter() {
        final DocRef folder = explorerService.create(ExplorerConstants.FOLDER, FileSystemTestUtil.getUniqueTestString(), null, null);
        final DocRef pipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "TestPipeline", folder, null);

        final PipelineDoc pipeline = pipelineStore.readDocument(pipelineRef);



        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(MetaFields.FEED_NAME,ExpressionTerm.Condition.EQUALS, "TEST-FEED-EVENTS")
                .addTerm(MetaFields.FIELD_TYPE,ExpressionTerm.Condition.EQUALS, "Raw Events")
                .build();
        QueryData filterConstraints = new QueryData();
        filterConstraints.setExpression(expression);

        Processor processor = processorService.create(pipelineRef, true);

        ProcessorFilter filter = processorFilterService.create(processor,filterConstraints, 10, false, true);

        HashSet<DocRef> forExport = new HashSet<DocRef>();

//        forExport.add (new DocRef(Processor.ENTITY_TYPE,processor.getUuid()));
        forExport.add (new DocRef(ProcessorFilter.ENTITY_TYPE,filter.getUuid()));

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");


        System.err.println ("Exporting to " + testDataDir);
        importExportSerializer.write(testDataDir, forExport, true, new ArrayList<>());


        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        System.out.println ("Exported to " + testDataDir);
    }

    @Test
    void testPipeline() throws IOException {
        final DocRef folder = explorerService.create(ExplorerConstants.FOLDER, FileSystemTestUtil.getUniqueTestString(), null, null);
        final DocRef parentPipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "Parent", folder, null);
        final DocRef childPipelineRef = explorerService.create(PipelineDoc.DOCUMENT_TYPE, "Child", folder, null);
        final PipelineDoc childPipeline = pipelineStore.readDocument(childPipelineRef);
        childPipeline.setParentPipeline(parentPipelineRef);
        pipelineStore.writeDocument(childPipeline);

        assertThat(pipelineStore.list().size()).isEqualTo(2);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(testDataDir, buildFindFolderCriteria(), true, new ArrayList<>());

        final String fileNamePrefix = ImportExportFileNameUtil.createFilePrefix(childPipelineRef);
        final String fileName = fileNamePrefix + ".meta";
        final Path path = testDataDir.resolve(folder.getName()).resolve(fileName);
        final String childJson = new String(Files.readAllBytes(path), StreamUtil.DEFAULT_CHARSET);

        assertThat(childJson.contains("\"name\" : \"Parent\"")).as("Parent reference not serialised\n" + childJson).isTrue();

        // Remove all entities from the database.
        clean(true);

        assertThat(pipelineStore.list().size()).isEqualTo(0);

        importExportSerializer.read(testDataDir, null, ImportMode.IGNORE_CONFIRMATION);

        assertThat(pipelineStore.list().size()).isEqualTo(2);
    }



    @Test
    void test() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config");
        final Path outDir = StroomCoreServerTestFileUtil.getTestOutputDir().resolve("samples/config");

        FileUtil.deleteDir(outDir);
        Files.createDirectories(outDir);

        // Read input.
        Set<DocRef> exported = importExportSerializer.read(inDir, null, ImportMode.IGNORE_CONFIRMATION);

        // Write to output.
        List<Message> messageList = new ArrayList<>();
        importExportSerializer.write(outDir, exported, true, messageList);

        messageList.forEach(message -> {
            if (message.getSeverity().equals(Severity.ERROR)) {
                LOGGER.error("Export error: {}", message.getMessage());
            } else {
                LOGGER.warn("Export warning: {}", message.getMessage());
            }
        });

        // Compare input and output directory.
        ComparisonHelper.compareDirs(inDir, outDir);

        // If the comparison was ok then delete the output.
        FileUtil.deleteDir(outDir);
    }
}
