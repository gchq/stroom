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

import stroom.data.store.mock.MockStore;
import stroom.node.api.NodeInfo;
import stroom.task.api.TaskManager;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StoreCreationTool;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

class TestTranslationTaskContextAndFlattening extends AbstractProcessIntegrationTest {

    private static final String DIR = "TestTranslationTaskContextAndFlattening/";
    private static final String FLATTENING_DIR = DIR + "TEST_FEED_FLATTENING/1";

    private static final String XSLT = DIR + "TestTaskContext_Transform.xsl";
    private static final String XSLT_CONTEXT = DIR + "TestTaskContext_ContextTransform.xsl";
    private static final String XSLT_FLATTENING = DIR + "TestTaskContext_FlatteningTransform.xsl";

    private static final String INPUT_RESOURCE_NAME = DIR + "TestTaskContext.in";
    private static final String CONTEXT_RESOURCE_NAME = DIR + "TestTaskContext.ctx";

    @Inject
    private MockStore streamStore;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private TaskManager taskManager;

    /**
     * Tests Task with a valid resource and feed.
     */
    @Test
    void test() {
        // FIXME : Fix this.

        // setup();
        // assertThat(streamStore.getLockCount()).isEqualTo(0);
        //
        // final List<TranslationTaskResult> results = doTest();
        // assertThat(results.size()).isEqualTo(1);
        // for (final TranslationTaskResult result : results) {
        // Assert
        // .assertThat(// result.getRecordsWritten() > 0).as(result.toString()).isTrue();
        // assertThat(// result.getRecordsRead() <= result.getRecordsWritten()).as(result.toString()).isTrue();
        // assertThat(result
        // .getRecordsWarning()).as(result.toString()).isEqualTo(0);
        // assertThat(result.getRecordsError()).as(result.toString()).isEqualTo(0);
        // }
        //
        // for (final Stream stream : streamStore.getFileData().keySet()) {
        // if (stream.getType() == StreamType.EVENTS) {
        // final byte[] data = streamStore.getFileData().get(stream).loadByName(
        // stream.getType());
        //
        // StreamUtil.stringToStream(new String(data),
        // StroomProcessTestFileUtil.getOutputStream(OUTPUT));
        // }
        // }
        //
        // // Make sure 1 record was written.
        // assertThat(results.loadByName(0).getRecordsWritten()).isEqualTo(1);
        //
        // // Compare all files.
        // ComparisonHelper.compareFiles(StroomProcessTestFileUtil
        // .getTestDataDir().resolve(DIR));
        //
        // // Make sure flattening dir exists.
        // final Path flatteningDir = StroomProcessTestFileUtil
        // .getTestDataDir().resolve(FLATTENING_DIR);
        // assertThat(Files.isDirectory(flatteningDir)).isTrue();
        // final Path flatFile = flatteningDir.resolve("2_STROOM.out");
        // assertThat(Files.isRegularFile(flatFile)).isTrue();
    }

    // private List<TranslationTaskResult> doTest() {
    // List<TranslationTaskResult> results = new
    // ArrayList<TranslationTaskResult>();
    // DataProcessorTask task = null;
    //
    // do {
    // task = nextTask();
    // if (task != null) {
    // final TranslationTaskResult result = taskManager.exec(task);
    // results.add(result);
    // }
    //
    // } while (task != null);
    //
    // return results;
    // }
    //
    // /**
    // * Gets the next task to be processed.
    // *
    // * @return The next task or null if there are currently no more tasks.
    // */
    // DataProcessorTask nextTask() {
    // DataProcessorTask task = null;
    //
    // // First try and look for raw reference streams that need processing.
    // final FindTranslationStreamTaskCriteria criteria = new
    // FindTranslationStreamTaskCriteria();
    // criteria.setNode(nodeCache.get());
    // criteria.getPageRequest().setLength(1);
    // criteria.setStreamTaskStatus(TaskStatus.UNPROCESSED);
    // criteria.setType(StreamType.RAW_REFERENCE);
    // List<TranslationStreamTask> processTasks = translationStreamTaskService
    // .assignUnprocessed(criteria);
    //
    // if (processTasks != null && processTasks.size() > 0) {
    // // If we have some raw reference streams then process them.
    // final TranslationStreamTask processTask = processTasks.loadByName(0);
    // task = new DataProcessorTask(processTask.getElementId());
    // } else {
    // // Otherwise look for some raw event streams that need processing.
    // criteria.setType(StreamType.RAW_EVENTS);
    // processTasks = translationStreamTaskService
    // .assignUnprocessed(criteria);
    //
    // if (processTasks != null && processTasks.size() > 0) {
    // final TranslationStreamTask processTask = processTasks.loadByName(0);
    // task = new DataProcessorTask(processTask.getElementId());
    // }
    // }
    //
    // return task;
    // }
}
