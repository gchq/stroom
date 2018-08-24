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

import org.junit.Ignore;
import org.junit.Test;
import stroom.data.store.impl.fs.MockStreamStore;
import stroom.test.StoreCreationTool;
import stroom.node.NodeCache;
import stroom.task.TaskManager;
import stroom.test.AbstractProcessIntegrationTest;

import javax.inject.Inject;

@Ignore("TODO 2015-10-21: Restore tests or delete the class.")
public class TestTranslationTaskContextAndFlattening extends AbstractProcessIntegrationTest {
    private static final String DIR = "TestTranslationTaskContextAndFlattening/";
    private static final String FLATTENING_DIR = DIR + "TEST_FEED_FLATTENING/1";

    private static final String XSLT = DIR + "TestTaskContext_Transform.xsl";
    private static final String XSLT_CONTEXT = DIR + "TestTaskContext_ContextTransform.xsl";
    private static final String XSLT_FLATTENING = DIR + "TestTaskContext_FlatteningTransform.xsl";

    private static final String INPUT_RESOURCE_NAME = DIR + "TestTaskContext.in";
    private static final String CONTEXT_RESOURCE_NAME = DIR + "TestTaskContext.ctx";

    @Inject
    private MockStreamStore streamStore;
    @Inject
    private NodeCache nodeCache;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private TaskManager taskManager;

    /**
     * Tests Task with a valid resource and feed.
     */
    @Test
    public void test() {
        // FIXME : Fix this.

        // setup();
        // Assert.assertEquals(0, streamStore.getLockCount());
        //
        // final List<TranslationTaskResult> results = doTest();
        // Assert.assertEquals(1, results.size());
        // for (final TranslationTaskResult result : results) {
        // Assert
        // .assertTrue(result.toString(),
        // result.getRecordsWritten() > 0);
        // Assert.assertTrue(result.toString(),
        // result.getRecordsRead() <= result.getRecordsWritten());
        // Assert.assertEquals(result.toString(), 0, result
        // .getRecordsWarning());
        // Assert.assertEquals(result.toString(), 0, result.getRecordsError());
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
        // Assert.assertEquals(1, results.loadByName(0).getRecordsWritten());
        //
        // // Compare all files.
        // ComparisonHelper.compareFiles(StroomProcessTestFileUtil
        // .getTestDataDir().resolve(DIR));
        //
        // // Make sure flattening dir exists.
        // final Path flatteningDir = StroomProcessTestFileUtil
        // .getTestDataDir().resolve(FLATTENING_DIR);
        // Assert.assertTrue(Files.isDirectory(flatteningDir));
        // final Path flatFile = flatteningDir.resolve("2_STROOM.out");
        // Assert.assertTrue(Files.isRegularFile(flatFile));
    }

    // private List<TranslationTaskResult> doTest() {
    // List<TranslationTaskResult> results = new
    // ArrayList<TranslationTaskResult>();
    // StreamProcessorTask task = null;
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
    // StreamProcessorTask nextTask() {
    // StreamProcessorTask task = null;
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
    // task = new StreamProcessorTask(processTask.getElementId());
    // } else {
    // // Otherwise look for some raw event streams that need processing.
    // criteria.setType(StreamType.RAW_EVENTS);
    // processTasks = translationStreamTaskService
    // .assignUnprocessed(criteria);
    //
    // if (processTasks != null && processTasks.size() > 0) {
    // final TranslationStreamTask processTask = processTasks.loadByName(0);
    // task = new StreamProcessorTask(processTask.getElementId());
    // }
    // }
    //
    // return task;
    // }
}
