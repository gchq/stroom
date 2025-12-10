/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.importexport;


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.api.ImportExportVersion;
import stroom.importexport.impl.ImportExportFileNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImportExportSerializer extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestImportExportSerializer.class);

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
    private ExplorerNodeService explorerNodeService;
    @Inject
    private ProcessorService processorService;
    @Inject
    private ProcessorFilterService processorFilterService;

    private Set<DocRef> buildFindFolderCriteria() {
        final Set<DocRef> criteria = new HashSet<>();
        criteria.add(ExplorerConstants.SYSTEM_DOC_REF);
        return criteria;
    }

    @Test
    void testExport() throws IOException {
        final ExplorerNode testNode = explorerService.create(FeedDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                null,
                null);
        FeedDoc eventFeed = feedStore.readDocument(testNode.getDocRef());
        eventFeed.setDescription("Original Description");
        feedStore.writeDocument(eventFeed);

        commonTestControl.createRequiredXMLSchemas();


        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                buildFindFolderCriteria(),
                Collections.emptySet(),
                true,
                ImportExportVersion.V1);

        List<ImportState> list = new ArrayList<>();
        importExportSerializer.read(
                testDataDir,
                list,
                ImportSettings.createConfirmation());
        assertThat(list.size() > 0).isTrue();

        // Should all be relative
        Map<DocRef, ImportState> map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        assertThat(map.get(testNode.getDocRef()).getState()).isEqualTo(State.EQUAL);

        eventFeed = feedStore.readDocument(testNode.getDocRef());
        eventFeed.setDescription("New Description");
        feedStore.writeDocument(eventFeed);

        List<DocRef> allSchemas = xmlSchemaStore.list();
        for (final DocRef ref : allSchemas) {
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            xmlSchema.setData("XML");
            xmlSchemaStore.writeDocument(xmlSchema);
        }

        list = new ArrayList<>();
        importExportSerializer.read(
                testDataDir,
                list,
                ImportSettings.createConfirmation());

        map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        assertThat(list.size() > 0).isTrue();
        assertThat(map.get(testNode.getDocRef()).getState()).isEqualTo(State.UPDATE);
        assertThat(map.get(testNode.getDocRef()).getUpdatedFieldList().contains("description")).isTrue();

        // Remove all entities from the database.
        commonTestControl.clear();

        list = new ArrayList<>();
        importExportSerializer.read(
                testDataDir,
                list,
                ImportSettings.createConfirmation());

        assertThat(!list.isEmpty()).isTrue();
        assertThat(list.get(0).getState()).isEqualTo(State.NEW);
        assertThat(list.get(1).getState()).isEqualTo(State.NEW);

        importExportSerializer.read(testDataDir, list, ImportSettings.auto());
        allSchemas = xmlSchemaStore.list();

        for (final DocRef ref : allSchemas) {
            LOGGER.info("Reading doc {}", ref);
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            assertThat(xmlSchema.getData()).isNotSameAs("XML");
        }

        assertThat(testDataDir).isNotNull();
    }

    @Test
    void testExportV2() throws IOException {
        final ExplorerNode testNode = explorerService.create(FeedDoc.TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                null,
                null);
        FeedDoc eventFeed = feedStore.readDocument(testNode.getDocRef());
        eventFeed.setDescription("Original Description");
        feedStore.writeDocument(eventFeed);

        commonTestControl.createRequiredXMLSchemas();

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                buildFindFolderCriteria(),
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);

        List<ImportState> list = new ArrayList<>();
        importExportSerializer.read(
                testDataDir,
                list,
                ImportSettings.createConfirmation());
        assertThat(!list.isEmpty()).isTrue();

        // Should all be relative
        Map<DocRef, ImportState> map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        assertThat(map.get(testNode.getDocRef()).getState()).isEqualTo(State.EQUAL);

        eventFeed = feedStore.readDocument(testNode.getDocRef());
        eventFeed.setDescription("New Description");
        feedStore.writeDocument(eventFeed);

        List<DocRef> allSchemas = xmlSchemaStore.list();
        for (final DocRef ref : allSchemas) {
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            xmlSchema.setData("XML");
            xmlSchemaStore.writeDocument(xmlSchema);
        }

        list = new ArrayList<>();
        importExportSerializer.read(
                testDataDir,
                list,
                ImportSettings.createConfirmation());

        map = new HashMap<>();
        for (final ImportState confirmation : list) {
            map.put(confirmation.getDocRef(), confirmation);
        }

        assertThat(!list.isEmpty()).isTrue();
        assertThat(map.get(testNode.getDocRef()).getState()).isEqualTo(State.UPDATE);
        assertThat(map.get(testNode.getDocRef()).getUpdatedFieldList().contains("description")).isTrue();

        // Remove all entities from the database.
        commonTestControl.clear();

        list = new ArrayList<>();
        importExportSerializer.read(
                testDataDir,
                list,
                ImportSettings.createConfirmation());

        assertThat(!list.isEmpty()).isTrue();
        assertThat(list.get(0).getState()).isEqualTo(State.NEW);
        assertThat(list.get(1).getState()).isEqualTo(State.NEW);

        importExportSerializer.read(testDataDir, list, ImportSettings.auto());
        allSchemas = xmlSchemaStore.list();

        for (final DocRef ref : allSchemas) {
            LOGGER.info("Reading doc {}", ref);
            final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(ref);
            assertThat(xmlSchema.getData()).isNotSameAs("XML");
        }

        assertThat(testDataDir).isNotNull();
    }

    /**
     * Debugging
     */
    private void dumpNodeStructure(final ExplorerNode node, final int indent) {
        if (node != null) {
            System.err.println("  ".repeat(indent) + ": " + node.getDocRef());

            final List<ExplorerNode> children = explorerNodeService.getChildren(node.getDocRef());
            if (children != null) {
                for (final ExplorerNode child : children) {
                    dumpNodeStructure(child, indent + 1);
                }
            }
        }
    }

    @Test
    void testPipelineWithProcessorFilter() {
        final ExplorerNode folder = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                null,
                null);
        final ExplorerNode pipelineNode = explorerService.create(PipelineDoc.TYPE,
                "TestPipeline",
                folder,
                null);

        final PipelineDoc pipeline = pipelineStore.readDocument(pipelineNode.getDocRef());


        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, "TEST-FEED-EVENTS")
                .addTerm(MetaFields.FIELD_TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .build();
        final QueryData filterConstraints = new QueryData();
        filterConstraints.setExpression(expression);

        final Processor processor = processorService.create(
                ProcessorType.PIPELINE,
                pipelineNode.getDocRef(),
                true);

        final ProcessorFilter filter = processorFilterService.create(processor,
                CreateProcessFilterRequest
                        .builder()
                        .pipeline(pipelineNode.getDocRef())
                        .queryData(filterConstraints)
                        .build());

        final Set<DocRef> forExport = new HashSet<>();

//        forExport.add (new DocRef(Processor.ENTITY_TYPE,processor.getUuid()));
        forExport.add(new DocRef(ProcessorFilter.ENTITY_TYPE, filter.getUuid()));

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");


        System.err.println("Exporting to " + testDataDir);
        importExportSerializer.write(
                null,
                testDataDir,
                forExport,
                Collections.emptySet(),
                true,
                ImportExportVersion.V1);


        importExportSerializer.read(
                testDataDir,
                null,
                ImportSettings.auto());

        System.out.println("Exported to " + testDataDir);
    }

    @Test
    void testPipelineWithProcessorFilterV2() {
        LOGGER.info("=======================================================");
        final ExplorerNode folder = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                null,
                null);
        final ExplorerNode pipelineNode = explorerService.create(PipelineDoc.TYPE,
                "TestPipeline",
                folder,
                null);

        final PipelineDoc pipeline = pipelineStore.readDocument(pipelineNode.getDocRef());

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, "TEST-FEED-EVENTS")
                .addTerm(MetaFields.FIELD_TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .build();
        final QueryData filterConstraints = new QueryData();
        filterConstraints.setExpression(expression);

        final Processor processor = processorService.create(
                ProcessorType.PIPELINE,
                pipelineNode.getDocRef(),
                true);

        final ProcessorFilter filter = processorFilterService.create(processor,
                CreateProcessFilterRequest
                        .builder()
                        .pipeline(pipelineNode.getDocRef())
                        .queryData(filterConstraints)
                        .build());

        // Debug
        dumpNodeStructure(explorerNodeService.getRoot(), 0);

        final Set<DocRef> forExport = new HashSet<>();

//        forExport.add (new DocRef(Processor.ENTITY_TYPE,processor.getUuid()));
        forExport.add(new DocRef(ProcessorFilter.ENTITY_TYPE, filter.getUuid()));

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");


        System.err.println("Exporting to " + testDataDir);
        importExportSerializer.write(
                null,
                testDataDir,
                forExport,
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);


        importExportSerializer.read(
                testDataDir,
                null,
                ImportSettings.auto());

        System.out.println("Exported to " + testDataDir);
    }

    @Test
    void testPipeline() throws IOException {
        final ExplorerNode folder = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                null,
                null);
        final ExplorerNode parentPipelineNode = explorerService.create(PipelineDoc.TYPE,
                "Parent",
                folder,
                null);
        final ExplorerNode childPipelineNode = explorerService.create(
                PipelineDoc.TYPE,
                "Child",
                folder,
                null);
        final PipelineDoc childPipeline = pipelineStore.readDocument(childPipelineNode.getDocRef());
        childPipeline.setParentPipeline(parentPipelineNode.getDocRef());
        pipelineStore.writeDocument(childPipeline);

        assertThat(pipelineStore.list().size()).isEqualTo(2);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                buildFindFolderCriteria(),
                Collections.emptySet(),
                true,
                ImportExportVersion.V1);

        final String fileNamePrefix = ImportExportFileNameUtil.createFilePrefix(childPipelineNode.getDocRef());
        final String fileName = fileNamePrefix + ".meta";
        final Path path = testDataDir.resolve(folder.getName()).resolve(fileName);
        final String childJson = Files.readString(path, StreamUtil.DEFAULT_CHARSET);

        assertThat(childJson.contains("\"name\" : \"Parent\""))
                .as("Parent reference not serialised\n" + childJson)
                .isTrue();

        // Remove all entities from the database.
        commonTestControl.clear();

        assertThat(pipelineStore.list().size())
                .isEqualTo(0);

        importExportSerializer.read(testDataDir, null, ImportSettings.auto());

        assertThat(pipelineStore.list().size())
                .isEqualTo(2);
    }

    @Test
    void testPipelineV2() throws IOException {
        final ExplorerNode folder = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                FileSystemTestUtil.getUniqueTestString(),
                null,
                null);
        final ExplorerNode parentPipelineNode = explorerService.create(PipelineDoc.TYPE,
                "Parent",
                folder,
                null);
        final ExplorerNode childPipelineNode = explorerService.create(
                PipelineDoc.TYPE,
                "Child",
                folder,
                null);
        final PipelineDoc childPipeline = pipelineStore.readDocument(childPipelineNode.getDocRef());
        childPipeline.setParentPipeline(parentPipelineNode.getDocRef());
        pipelineStore.writeDocument(childPipeline);

        dumpNodeStructure(explorerNodeService.getRoot(), 0);

        assertThat(pipelineStore.list().size()).isEqualTo(2);

        final Path testDataDir = getCurrentTestDir().resolve("ExportTest");

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        importExportSerializer.write(
                null,
                testDataDir,
                buildFindFolderCriteria(),
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);

        final String fileNamePrefix = ImportExportFileNameUtil.createFilePrefix(childPipelineNode.getDocRef());
        final String folderPrefix = ImportExportFileNameUtil.createFilePrefix(folder.getDocRef());
        LOGGER.info("filenamePrefix = {}", fileNamePrefix);
        LOGGER.info("folder.getName()");
        final String fileName = fileNamePrefix + ".meta";
        final Path path = testDataDir.resolve(folderPrefix).resolve(fileName);
        final String childJson = Files.readString(path, StreamUtil.DEFAULT_CHARSET);

        assertThat(childJson.contains("\"name\" : \"Parent\""))
                .as("Parent reference not serialised\n" + childJson)
                .isTrue();

        // Remove all entities from the database.
        commonTestControl.clear();

        assertThat(pipelineStore.list().size())
                .isEqualTo(0);

        importExportSerializer.read(testDataDir, null, ImportSettings.auto());

        assertThat(pipelineStore.list().size())
                .isEqualTo(2);
    }


    @Test
    void testConfig() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config");
        final Path outDir = StroomCoreServerTestFileUtil.getTestOutputDir().resolve("samples/config");

        FileUtil.deleteDir(outDir);
        Files.createDirectories(outDir);

        // Read input.
        final Set<DocRef> exported =
                importExportSerializer.read(
                        inDir,
                        null,
                        ImportSettings.auto());

        // Write to output.
        final ExportSummary exportSummary = importExportSerializer.write(
                null,
                outDir,
                exported,
                Collections.emptySet(),
                true,
                ImportExportVersion.V1);

        final List<Message> messageList = exportSummary.getMessages();
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

    @Test
    void testConfigV2() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config-v2");
        final Path outDir = StroomCoreServerTestFileUtil.getTestOutputDir().resolve("samples/config-v2");

        FileUtil.deleteDir(outDir);
        Files.createDirectories(outDir);

        // Read input.
        final Set<DocRef> exported =
                importExportSerializer.read(
                        inDir,
                        null,
                        ImportSettings.auto());

        // Write to output.
        final ExportSummary exportSummary = importExportSerializer.write(
                null,
                outDir,
                exported,
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);

        final List<Message> messageList = exportSummary.getMessages();
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

    @Test
    void testFeedsAndTranslationsV2() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve("samples/feeds-and-translations-internal-v2");
        final Path outDir = StroomCoreServerTestFileUtil.getTestOutputDir()
                .resolve("samples/feeds-and-translations-internal-v2");

        FileUtil.deleteDir(outDir);
        Files.createDirectories(outDir);

        // Read input.
        final Set<DocRef> exported =
                importExportSerializer.read(
                        inDir,
                        null,
                        ImportSettings.auto());

        // Write to output in V2 format
        final ExportSummary exportSummary = importExportSerializer.write(
                List.of(ExplorerConstants.SYSTEM_NODE),
                outDir,
                exported,
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);

        final List<Message> messageList = exportSummary.getMessages();
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

    /*@Test
    void migrate() throws IOException {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config");
        final Path outDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config-v2");

        FileUtil.deleteDir(outDir);
        Files.createDirectories(outDir);

        final Set<DocRef> exported = importExportSerializer.read(inDir, null, ImportSettings.auto());
        importExportSerializer.write(List.of(ExplorerConstants.SYSTEM_NODE),
                outDir,
                exported,
                Collections.emptySet(),
                true,
                ImportExportVersion.V2);
    }*/

}
