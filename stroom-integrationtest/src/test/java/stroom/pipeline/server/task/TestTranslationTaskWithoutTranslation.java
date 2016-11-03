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

package stroom.pipeline.server.task;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import stroom.test.StroomProcessTestFileUtil;
import org.junit.Assert;
import org.junit.Test;

import stroom.AbstractProcessIntegrationTest;
import stroom.node.server.NodeCache;
import stroom.streamstore.server.MockStreamStore;
import stroom.streamstore.server.tools.StoreCreationTool;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.server.StreamProcessorTask;
import stroom.streamtask.server.StreamProcessorTaskExecutor;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.streamtask.shared.StreamTask;
import stroom.task.server.TaskManager;
import stroom.test.ComparisonHelper;
import stroom.util.shared.Severity;

public class TestTranslationTaskWithoutTranslation extends AbstractProcessIntegrationTest {
    private static final String DIR = "TestTranslationTaskWithoutTranslation/";
    private static final String FEED_NAME = "TEST_FEED";
    private static final File RESOURCE_NAME = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "TestTask.out");

    @Resource
    private MockStreamStore streamStore;
    @Resource
    private NodeCache nodeCache;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private StoreCreationTool storeCreationTool;
    @Resource
    private TaskManager taskManager;

    /**
     * Tests Task with a valid resource and feed.
     *
     * @throws Exception
     *             Could be thrown.
     */
    @Test
    public void test() throws Exception {
        setup(FEED_NAME, RESOURCE_NAME);
        Assert.assertEquals(0, streamStore.getLockCount());

        final List<StreamProcessorTaskExecutor> results = processAll();
        Assert.assertEquals(1, results.size());

        for (final StreamProcessorTaskExecutor result : results) {
            final PipelineStreamProcessor processor = (PipelineStreamProcessor) result;
            final String message = "Count = " + processor.getRead() + "," + processor.getWritten() + ","
                    + processor.getMarkerCount(Severity.SEVERITIES);

            Assert.assertTrue(message, processor.getWritten() > 0);
            Assert.assertTrue(message, processor.getRead() <= processor.getWritten());
            Assert.assertEquals(message, 0, processor.getMarkerCount(Severity.SEVERITIES));
        }

        final File inputDir = new File(StroomProcessTestFileUtil.getTestResourcesDir(), DIR);
        final File outputDir = new File(StroomProcessTestFileUtil.getTestOutputDir(), DIR);

        for (final Stream stream : streamStore.getFileData().keySet()) {
            if (stream.getStreamType() == StreamType.EVENTS) {
                final byte[] data = streamStore.getFileData().get(stream).get(stream.getStreamType().getId());

                // Write the actual XML out.
                final OutputStream os = StroomProcessTestFileUtil.getOutputStream(outputDir, "TestTask.out");
                os.write(data);
                os.flush();
                os.close();

                ComparisonHelper.compareDirs(inputDir, outputDir);
            }
        }

        // Make sure 10 records were written.
        Assert.assertEquals(10, ((PipelineStreamProcessor) results.get(0)).getWritten());
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

    private void setup(final String feedName, final File dataLocation) throws IOException {
        storeCreationTool.addEventData(feedName, null, null, null, dataLocation, null);
    }
}
