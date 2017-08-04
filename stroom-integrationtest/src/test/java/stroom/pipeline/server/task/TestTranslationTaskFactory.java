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

package stroom.pipeline.server.task;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.feed.shared.Feed;
import stroom.node.server.NodeCache;
import stroom.pipeline.server.XSLTService;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.tools.StoreCreationTool;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.server.StreamProcessorTask;
import stroom.streamtask.server.StreamProcessorTaskExecutor;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.streamtask.shared.StreamTask;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskMonitorImpl;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Create test data or recreate with large test
@Ignore("Create test data or recreate with large test")
public class TestTranslationTaskFactory extends AbstractProcessIntegrationTest {
    private static final String DIR = "GenericTestTranslationTaskFactory/";

    private static final File FORMAT_DEFINITION = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SimpleCSVSplitter.ds");
    private static final File XSLT_HOST_NAME_TO_IP = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.xsl");

    private static final File IMPORTED_XSLT = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "imported.xsl");
    private static final File SAMPLE_XSLT = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "sample.xsl");

    private static final String REFERENCE_FEED_NAME = "HOSTNAME_TO_IP";
    private static final String EVENT_FEED_NAME = "TEST_FEED";

    private static final File REFERENCE_DATA = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.in");
    private static final File VALID_DATA = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "SampleEvt.in");
    private static final File INVALID_XSL = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "Invalid.nxsl");
    private static final File INVALID_DATA = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "Invalid.in");
    private static final File EMPTY_DATA = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "Empty.in");

    private static final int NO_OF_REFERENCE_FILES = 3;
    private static final int NO_OF_EVENT_FILES = 10;

    @Resource
    private TaskManager taskManager;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StoreCreationTool storeCreationTool;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private XSLTService xsltService;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private NodeCache nodeCache;

    /**
     * Tests that valid streams are all processed and put into the processed and
     * raw stores and that no streams end up in the error store.
     *
     * @throws Exception Could be thrown.
     */
    @Test
    public void testTranslationTaskFactory() throws Exception {
        // Create a store.
        createStore(VALID_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        Assert.assertEquals(0, streamStore.getLockCount());

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        Assert.assertEquals("Check that we did the number of jobs expected", NO_OF_REFERENCE_FILES + NO_OF_EVENT_FILES,
                results.size());
        Assert.assertEquals(0, streamStore.getLockCount());

        // Check we have some raw events.
        final List<Stream> raw = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check all passed.
        final List<Stream> cooked = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.EVENTS));
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
        List<StreamTask> streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        while (streamTasks.size() > 0) {
            for (final StreamTask streamTask : streamTasks) {
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
    // * @throws Exception
    // * Could be thrown.
    // */
    // @Test
    // public void testNoTranslation() throws Exception {
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
    // criteria.setStreamType(StreamType.RAW_EVENTS);
    // criteria.setConversionStreamType(StreamType.EVENTS);
    // List<TranslationStreamTask> streams = translationStreamTaskService
    // .find(criteria);
    //
    // Assert.assertEquals(0, streams.size());
    //
    // // Check none failed.
    // criteria = new FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.FAILED);
    // criteria.setStreamType(StreamType.RAW_EVENTS);
    // streams = translationStreamTaskService.find(criteria);
    //
    // Assert.assertEquals(0, streams.size());
    // }

    /**
     * Tests that invalid streams are processed but errors are recorded against
     * them.
     *
     * @throws Exception Could be thrown.
     */
    @Test
    public void testInvalidData() throws Exception {
        // Create a store.
        createStore(INVALID_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        // Check we have some raw events.
        final List<Stream> raw = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check no output streams were written.
        final List<Stream> cooked = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.EVENTS));
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
     *
     * @throws Exception Could be thrown.
     */
    @Test
    public void testEmptyData() throws Exception {
        // Create a store.
        createStore(EMPTY_DATA, REFERENCE_DATA, SAMPLE_XSLT);

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        // Check we have some raw events.
        final List<Stream> raw = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check no output streams were written.
        final List<Stream> cooked = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.EVENTS));
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
     *
     * @throws Exception Could be thrown.
     */
    @Test
    public void testInvalidXSLT() throws Exception {
        // Check none passed.
        List<Stream> cooked = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.RAW_EVENTS));
        Assert.assertEquals(0, cooked.size());

        // Create a store.
        createStore(INVALID_DATA, REFERENCE_DATA, INVALID_XSL);

        // Process the store sequentially.
        final List<StreamProcessorTaskExecutor> results = processAll();

        // Check we have some raw events.
        final List<Stream> raw = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.RAW_EVENTS));
        Assert.assertEquals(NO_OF_EVENT_FILES, raw.size());

        // Check all failed.
        cooked = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.EVENTS));
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
    // public void testInvalidNoTranslation() throws Exception {
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
    // criteria.setStreamType(StreamType.RAW_EVENTS);
    // criteria.setConversionStreamType(StreamType.EVENTS);
    // List<TranslationStreamTask> streams = translationStreamTaskService
    // .find(criteria);
    //
    // Assert.assertEquals(0, streams.size());
    //
    // // Check none failed.
    // criteria = new FindTranslationStreamTaskCriteria();
    // criteria.setStreamTaskStatus(TaskStatus.FAILED);
    // criteria.setStreamType(StreamType.RAW_EVENTS);
    // streams = translationStreamTaskService.find(criteria);
    //
    // Assert.assertEquals(0, streams.size());
    // }

    /**
     * Creates a data store duplicating the file found in data.
     *
     * @param data The path of the data to replicate.
     */
    private void createStore(final File data, final File reference, final File xslt) {
        try {
            // Add imported XSLT.
            final XSLT xsltImport = xsltService.create(commonTestScenarioCreator.getTestFolder(), "imported.xsl");
            xsltImport.setDescription("Imported XSLT");
            xsltImport.setData(StreamUtil.fileToString(IMPORTED_XSLT));
            xsltService.save(xsltImport);

            Feed hostNameToIP = null;

            for (int i = 0; i < NO_OF_REFERENCE_FILES; i++) {
                // Setup the feed definitions.
                hostNameToIP = storeCreationTool.addReferenceData(REFERENCE_FEED_NAME, TextConverterType.DATA_SPLITTER,
                        FORMAT_DEFINITION, XSLT_HOST_NAME_TO_IP, reference);
            }

            final Set<Feed> referenceFeeds = new HashSet<>();
            referenceFeeds.add(hostNameToIP);

            for (int i = 0; i < NO_OF_EVENT_FILES; i++) {
                storeCreationTool.addEventData(EVENT_FEED_NAME, null, null, xslt, data, referenceFeeds);
            }

            // Force creation of stream tasks.
            if (streamTaskCreator instanceof StreamTaskCreator) {
                streamTaskCreator.createTasks(new TaskMonitorImpl());
            }

        } catch (final Exception ex) {
            ex.printStackTrace();
            throw ProcessException.wrap(ex);
        }
    }
}
