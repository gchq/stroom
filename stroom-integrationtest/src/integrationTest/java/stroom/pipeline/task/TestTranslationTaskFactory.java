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

package stroom.pipeline.task;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.docref.DocRef;
import stroom.node.NodeCache;
import stroom.pipeline.XsltStore;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.data.store.api.StreamStore;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.store.tools.StoreCreationTool;
import stroom.streamtask.StreamProcessorTask;
import stroom.streamtask.StreamProcessorTaskExecutor;
import stroom.streamtask.StreamTaskCreator;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.task.SimpleTaskContext;
import stroom.task.TaskManager;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Create test data or recreate with large test
@Ignore("Create test data or recreate with large test")
public class TestTranslationTaskFactory extends AbstractProcessIntegrationTest {
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
    private TaskManager taskManager;
    @Inject
    private StreamStore streamStore;
    @Inject
    private DataMetaService streamMetaService;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private StreamTaskCreator streamTaskCreator;
    @Inject
    private NodeCache nodeCache;

    /**
     * Tests that valid streams are all processed and put into the processed and
     * raw stores and that no streams end up in the error store.
     */
    @Test
    public void testTranslationTaskFactory() {
        // Create a store.
        createStore(VALID_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        Assert.assertEquals(0, streamMetaService.getLockCount());

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        Assert.assertEquals("Check that we did the number of jobs expected", NO_OF_REFERENCE_FILES + NO_OF_EVENT_FILES,
                results.size());
        Assert.assertEquals(0, streamMetaService.getLockCount());

        // Check we have some raw events.
        final List<Data> raw = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check all passed.
        final List<Data> cooked = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, cooked.size());

        // Check none failed.
        for (final StreamProcessorTaskExecutor result : results) {
            Assert.assertEquals(0, ((PipelineStreamProcessor) result).getMarkerCount(Severity.SEVERITIES));
        }
    }

    /**
     * Gets the next task to be processed.
     *
     * @return The next task or null if there are currently no more tasks.
     */
    private List<StreamProcessorTaskExecutor> processAll() {
        final List<StreamProcessorTaskExecutor> results = new ArrayList<>();
        List<ProcessorFilterTask> streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        while (streamTasks.size() > 0) {
            for (final ProcessorFilterTask streamTask : streamTasks) {
                final StreamProcessorTask task = new StreamProcessorTask(streamTask);
                taskManager.exec(task);
                results.add(task.getStreamProcessorTaskExecutor());
            }
            streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        }
        return results;
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
    // Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());
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
    // Assert.assertEquals(0, streams.size());
    //
    // // Check none failed.
    // criteria = new FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.FAILED);
    // criteria.setType(StreamType.RAW_EVENTS);
    // streams = translationStreamTaskService.find(criteria);
    //
    // Assert.assertEquals(0, streams.size());
    // }

    /**
     * Tests that invalid streams are processed but errors are recorded against
     * them.
     */
    @Test
    public void testInvalidData() {
        // Create a store.
        createStore(INVALID_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        // Check we have some raw events.
        final List<Data> raw = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check no output streams were written.
        final List<Data> cooked = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.EVENTS));
        Assert.assertEquals(0, cooked.size());

        // Make sure we got 13 results.
        Assert.assertEquals(13, results.size());

        // Make sure there were errors.
        int errors = 0;
        for (final StreamProcessorTaskExecutor result : results) {
            final PipelineStreamProcessor pipelineStreamProcessor = (PipelineStreamProcessor) result;
            errors += pipelineStreamProcessor.getMarkerCount(Severity.ERROR, Severity.FATAL_ERROR);
        }
        Assert.assertEquals(10, errors);
    }

    /**
     * Tests that invalid streams are processed but errors are recorded against
     * them.
     */
    @Test
    public void testEmptyData() {
        // Create a store.
        createStore(EMPTY_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        // Check we have some raw events.
        final List<Data> raw = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check no output streams were written.
        final List<Data> cooked = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.EVENTS));
        Assert.assertEquals(0, cooked.size());

        // Make sure we got 13 results.
        Assert.assertEquals(13, results.size());

        // Make sure there were no errors.
        int errors = 0;
        for (final StreamProcessorTaskExecutor result : results) {
            errors += ((PipelineStreamProcessor) result).getMarkerCount(Severity.ERROR);
        }
        Assert.assertEquals(0, errors);
    }

    /**
     * Tests that invalid xslt causes all tasks to fail.
     */
    @Test
    public void testInvalidXSLT() {
        // Check none passed.
        List<Data> cooked = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.RAW_EVENTS));
        Assert.assertEquals(0, cooked.size());

        // Create a store.
        createStore(INVALID_DATA, REFERENCE_DATA, INVALID_XSL);

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        // Check we have some raw events.
        final List<Data> raw = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check all failed.
        cooked = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.EVENTS));
        Assert.assertEquals(0, cooked.size());

        Assert.assertEquals(13, results.size());

        int errors = 0;
        for (final StreamProcessorTaskExecutor result : results) {
            final PipelineStreamProcessor pipelineStreamProcessor = (PipelineStreamProcessor) result;
            errors += pipelineStreamProcessor.getMarkerCount(Severity.ERROR, Severity.FATAL_ERROR);
        }
        Assert.assertEquals(10, errors);
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
    // Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());
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
    // Assert.assertEquals(0, streams.size());
    //
    // // Check none failed.
    // criteria = new FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.FAILED);
    // criteria.setType(StreamType.RAW_EVENTS);
    // streams = translationStreamTaskService.find(criteria);
    //
    // Assert.assertEquals(0, streams.size());
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
                storeCreationTool.addEventData(EVENT_FEED_NAME, null, null, xslt, data, referenceFeeds);
            }

            // Force creation of stream tasks.
            streamTaskCreator.createTasks(new SimpleTaskContext());

        } catch (final IOException e) {
            e.printStackTrace();
            throw ProcessException.wrap(e);
        }
    }
}
