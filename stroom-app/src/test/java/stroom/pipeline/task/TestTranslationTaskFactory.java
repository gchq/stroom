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

package stroom.pipeline.task;


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
import stroom.processor.api.ProcessorResult;
import stroom.processor.impl.ProcessorTaskTestHelper;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// TODO: Create test data or recreate with large test
@Disabled("Create test data or recreate with large test")
class TestTranslationTaskFactory extends AbstractProcessIntegrationTest {

    private static final String DIR = "GenericTestTranslationTaskFactory/";

    private static final Path FORMAT_DEFINITION = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SimpleCSVSplitter.ds");
    private static final Path XSLT_HOST_NAME_TO_IP = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.xsl");

    private static final Path IMPORTED_XSLT = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "imported.xsl");
    private static final Path SAMPLE_XSLT = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "sample.xsl");

    private static final String REFERENCE_FEED_NAME = "HOSTNAME_TO_IP";
    private static final String EVENT_FEED_NAME = "TEST_FEED";

    private static final Path REFERENCE_DATA = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.in");
    private static final Path VALID_DATA = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "SampleEvt.in");
    private static final Path INVALID_XSL = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "Invalid.nxsl");
    private static final Path INVALID_DATA = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "Invalid.in");
    private static final Path EMPTY_DATA = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "Empty.in");

    private static final int NO_OF_REFERENCE_FILES = 3;
    private static final int NO_OF_EVENT_FILES = 10;

    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private MetaService metaService;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;

    /**
     * Tests that valid streams are all processed and put into the processed and
     * raw stores and that no streams end up in the error store.
     */
    @Test
    void testTranslationTaskFactory() {
        // Create a store.
        createStore(VALID_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        assertThat(metaService.getLockCount())
                .isEqualTo(0);

        // Process the store sequentially.
        final List<ProcessorResult> results = processAll();

        assertThat(results.size())
                .as("Check that we did the number of jobs expected")
                .isEqualTo(NO_OF_REFERENCE_FILES + NO_OF_EVENT_FILES);
        assertThat(metaService.getLockCount())
                .isEqualTo(0);

        // Check we have some raw events.
        final List<Meta> raw = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.RAW_EVENTS))
                .getValues();
        assertThat(raw.size())
                .isEqualTo(NO_OF_EVENT_FILES);

        // Check all passed.
        final List<Meta> cooked = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.EVENTS))
                .getValues();
        assertThat(cooked.size())
                .isEqualTo(NO_OF_EVENT_FILES);

        // Check none failed.
        for (final ProcessorResult result : results) {
            assertThat(result.getMarkerCount(Severity.SEVERITIES))
                    .isEqualTo(0);
        }
    }

    /**
     * Gets the next task to be processed.
     *
     * @return The next task or null if there are currently no more tasks.
     */
    private List<ProcessorResult> processAll() {
        return commonTranslationTestHelper.processAll();
    }

    // /**
    // * Tests that the store remains clean if processing is started and stopped
    // * instantly.
    // *
    // * Could be thrown.
    // */
    // @Test
    // public void testNoTranslation() {
    // // Create a store.
    // createStore(VALID_DATA, REFERENCE_DATA, DTS_XSLT);
    //
    // // Check we have some raw events.
    // List<Stream> raw = streamStore.findStreamSource(new Stream(
    // StreamType.RAW_EVENTS, null, null));
    // assertThat(raw.size()).isEqualTo(NO_OF_EVENT_FILES);
    //
    // // Check none passed.
    // FindTranslationStreamTaskCriteria criteria = new
    // FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.COMPLETE);
    // criteria.setType(StreamType.RAW_EVENTS);
    // criteria.setConversionStreamType(StreamType.EVENTS);
    // List<TranslationStreamTask> streams = translationStreamTaskService
    // .find(criteria);
    //
    // assertThat(streams.size()).isEqualTo(0);
    //
    // // Check none failed.
    // criteria = new FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.FAILED);
    // criteria.setType(StreamType.RAW_EVENTS);
    // streams = translationStreamTaskService.find(criteria);
    //
    // assertThat(streams.size()).isEqualTo(0);
    // }

    /**
     * Tests that invalid streams are processed but errors are recorded against
     * them.
     */
    @Test
    void testInvalidData() {
        // Create a store.
        createStore(INVALID_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        // Process the store sequentially.
        final List<ProcessorResult> results = processAll();

        // Check we have some raw events.
        final List<Meta> raw = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.RAW_EVENTS))
                .getValues();
        assertThat(raw.size())
                .isEqualTo(NO_OF_EVENT_FILES);

        // Check no output streams were written.
        final List<Meta> cooked = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.EVENTS))
                .getValues();
        assertThat(cooked.size())
                .isEqualTo(0);

        // Make sure we got 13 results.
        assertThat(results.size())
                .isEqualTo(13);

        // Make sure there were errors.
        int errors = 0;
        for (final ProcessorResult result : results) {
            errors += result.getMarkerCount(Severity.ERROR, Severity.FATAL_ERROR);
        }
        assertThat(errors)
                .isEqualTo(10);
    }

    /**
     * Tests that invalid streams are processed but errors are recorded against
     * them.
     */
    @Test
    void testEmptyData() {
        // Create a store.
        createStore(EMPTY_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        // Process the store sequentially.
        final List<ProcessorResult> results = processAll();

        // Check we have some raw events.
        final List<Meta> raw = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.RAW_EVENTS))
                .getValues();
        assertThat(raw.size())
                .isEqualTo(NO_OF_EVENT_FILES);

        // Check no output streams were written.
        final List<Meta> cooked = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.EVENTS))
                .getValues();
        assertThat(cooked.size())
                .isEqualTo(0);

        // Make sure we got 13 results.
        assertThat(results.size())
                .isEqualTo(13);

        // Make sure there were no errors.
        int errors = 0;
        for (final ProcessorResult result : results) {
            errors += result.getMarkerCount(Severity.ERROR);
        }
        assertThat(errors)
                .isEqualTo(0);
    }

    /**
     * Tests that invalid xslt causes all tasks to fail.
     */
    @Test
    void testInvalidXSLT() {
        // Check none passed.
        List<Meta> cooked = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.RAW_EVENTS)).getValues();
        assertThat(cooked.size())
                .isEqualTo(0);

        // Create a store.
        createStore(INVALID_DATA, REFERENCE_DATA, INVALID_XSL);

        // Process the store sequentially.
        final List<ProcessorResult> results = processAll();

        // Check we have some raw events.
        final List<Meta> raw = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.RAW_EVENTS))
                .getValues();
        assertThat(raw.size())
                .isEqualTo(NO_OF_EVENT_FILES);

        // Check all failed.
        cooked = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.EVENTS)).getValues();
        assertThat(cooked.size())
                .isEqualTo(0);

        assertThat(results.size())
                .isEqualTo(13);

        int errors = 0;
        for (final ProcessorResult result : results) {
            errors += result.getMarkerCount(Severity.ERROR, Severity.FATAL_ERROR);
        }
        assertThat(errors)
                .isEqualTo(10);
    }

    // /**
    // * Tests that the store remains clean if processing is started and stopped
    // * instantly.
    // *
    // * @throws Exception
    // * Could be thrown.
    // */
    // @Test
    // public void testInvalidNoTranslation() {
    // // Create a store.
    // createStore(INVALID_DATA, REFERENCE_DATA, DTS_XSLT);
    //
    // // Check we have some raw events.
    // List<Stream> raw = streamStore.findStreamSource(new Stream(
    // StreamType.RAW_EVENTS, null, null));
    // assertThat(raw.size()).isEqualTo(NO_OF_EVENT_FILES);
    //
    // // Check none passed.
    // FindTranslationStreamTaskCriteria criteria = new
    // FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.COMPLETE);
    // criteria.setType(StreamType.RAW_EVENTS);
    // criteria.setConversionStreamType(StreamType.EVENTS);
    // List<TranslationStreamTask> streams = translationStreamTaskService
    // .find(criteria);
    //
    // assertThat(streams.size()).isEqualTo(0);
    //
    // // Check none failed.
    // criteria = new FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.FAILED);
    // criteria.setType(StreamType.RAW_EVENTS);
    // streams = translationStreamTaskService.find(criteria);
    //
    // assertThat(streams.size()).isEqualTo(0);
    // }

    /**
     * Creates a data store duplicating the file found in data.
     *
     * @param data The path of the data to replicate.
     */
    private void createStore(final Path data, final Path reference, final Path xslt) {
        try {
            // Add imported XSLT.
            final DocRef xsltRef = xsltStore.createDocument("imported.xsl");
            final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
            xsltDoc.setDescription("Imported XSLT");
            xsltDoc.setData(StreamUtil.fileToString(IMPORTED_XSLT));
            xsltStore.writeDocument(xsltDoc);

            DocRef hostNameToIP = null;

            for (int i = 0; i < NO_OF_REFERENCE_FILES; i++) {
                // Setup the feed definitions.
                hostNameToIP = storeCreationTool.addReferenceData(REFERENCE_FEED_NAME, TextConverterType.DATA_SPLITTER,
                        FORMAT_DEFINITION, XSLT_HOST_NAME_TO_IP, reference);
            }

            final Set<DocRef> referenceFeeds = new HashSet<>();
            referenceFeeds.add(hostNameToIP);

            for (int i = 0; i < NO_OF_EVENT_FILES; i++) {
                storeCreationTool.addEventData(
                        EVENT_FEED_NAME,
                        null,
                        null,
                        xslt,
                        data,
                        referenceFeeds);
            }

            // Force creation of stream tasks.
            processorTaskTestHelper.createAndQueueTasks();

        } catch (final IOException e) {
            e.printStackTrace();
            throw ProcessException.wrap(e);
        }
    }
}
