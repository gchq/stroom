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

package stroom.pipeline.task;


import org.junit.jupiter.api.Test;
import stroom.data.store.impl.mock.MockStore;
import stroom.core.dataprocess.PipelineDataProcessorTaskExecutor;
import stroom.meta.impl.mock.MockMetaService;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.processor.api.DataProcessorTaskExecutor;
import stroom.processor.impl.DataProcessorTask;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.processor.shared.ProcessorTask;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.api.TaskManager;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StoreCreationTool;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;

class TestTranslationTaskWithoutTranslation extends AbstractProcessIntegrationTest {
    private static final String DIR = "TestTranslationTaskWithoutTranslation/";
    private static final String FEED_NAME = "TEST_FEED";
    private static final Path RESOURCE_NAME = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "TestTask.out");

    @Inject
    private MockStore streamStore;
    @Inject
    private MockMetaService metaService;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private ProcessorTaskManager processorTaskManager;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private TaskManager taskManager;

    /**
     * Tests Task with a valid resource and feed.
     *
     * @throws IOException Could be thrown.
     */
    @Test
    void test() throws IOException {
        setup(FEED_NAME, RESOURCE_NAME);
        assertThat(metaService.getLockCount()).isEqualTo(0);

        final List<DataProcessorTaskExecutor> results = processAll();
        assertThat(results.size()).isEqualTo(1);

        for (final DataProcessorTaskExecutor result : results) {
            final PipelineDataProcessorTaskExecutor processor = (PipelineDataProcessorTaskExecutor) result;
            final String message = "Count = " + processor.getRead() + "," + processor.getWritten() + ","
                    + processor.getMarkerCount(Severity.SEVERITIES);

            assertThat(processor.getWritten() > 0).as(message).isTrue();
            assertThat(processor.getRead() <= processor.getWritten()).as(message).isTrue();
            assertThat(processor.getMarkerCount(Severity.SEVERITIES)).as(message).isEqualTo(0);
        }

        final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(DIR);
        final Path outputDir = StroomPipelineTestFileUtil.getTestOutputDir().resolve(DIR);

        for (final Entry<Long, Meta> entry : metaService.getMetaMap().entrySet()) {
            final long streamId = entry.getKey();
            final Meta meta = entry.getValue();
            if (StreamTypeNames.EVENTS.equals(meta.getTypeName())) {
                final byte[] data = streamStore.getFileData().get(streamId).get(meta.getTypeName());

                // Write the actual XML out.
                final OutputStream os = StroomPipelineTestFileUtil.getOutputStream(outputDir, "TestTask.out");
                os.write(data);
                os.flush();
                os.close();

                ComparisonHelper.compareDirs(inputDir, outputDir);
            }
        }

        // Make sure 10 records were written.
        assertThat(((PipelineDataProcessorTaskExecutor) results.get(0)).getWritten()).isEqualTo(10);
    }

    /**
     * Gets the next task to be processed.
     *
     * @return The next task or null if there are currently no more tasks.
     */
    private List<DataProcessorTaskExecutor> processAll() {
        final List<DataProcessorTaskExecutor> results = new ArrayList<>();
        List<ProcessorTask> streamTasks = processorTaskManager.assignTasks(nodeInfo.getThisNodeName(), 100);
        while (streamTasks.size() > 0) {
            for (final ProcessorTask streamTask : streamTasks) {
                final DataProcessorTask task = new DataProcessorTask(streamTask);
                taskManager.exec(task);
                results.add(task.getDataProcessorTaskExecutor());
            }
            streamTasks = processorTaskManager.assignTasks(nodeInfo.getThisNodeName(), 100);
        }
        return results;
    }

    private void setup(final String feedName, final Path dataLocation) throws IOException {
        storeCreationTool.addEventData(feedName, null, null, null, dataLocation, null);
    }
}
