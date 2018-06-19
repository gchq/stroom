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

package stroom.streamtask;

import org.junit.Assert;
import org.junit.Test;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.meta.api.StreamDataSource;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.task.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.config.StroomProperties;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.List;

public class TestStreamTaskCreator extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private StreamTaskCreator streamTaskCreator;
    @Inject
    private NodeCache nodeCache;

    @Test
    public void testBasic() {
        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        Assert.assertEquals(0, commonTestControl.countEntity(ProcessorFilterTask.class));

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);

        Assert.assertEquals(0, commonTestControl.countEntity(ProcessorFilterTask.class));

        Assert.assertNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        streamTaskCreator.createTasks(new SimpleTaskContext());
        Assert.assertNotNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        Assert.assertFalse(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());

        Assert.assertEquals(0, commonTestControl.countEntity(ProcessorFilterTask.class));

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);

        streamTaskCreator.createTasks(new SimpleTaskContext());
        Assert.assertTrue(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());
        Assert.assertEquals(4, commonTestControl.countEntity(ProcessorFilterTask.class));

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        streamTaskCreator.createTasks(new SimpleTaskContext());

        Assert.assertEquals(6, commonTestControl.countEntity(ProcessorFilterTask.class));

        streamTaskCreator.createTasks(new SimpleTaskContext());
        Assert.assertEquals(6, commonTestControl.countEntity(ProcessorFilterTask.class));
    }

    @Test
    public void testMultiFeedInitialCreate() {
        final Node node = nodeCache.getDefaultNode();

        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        Assert.assertEquals(0, commonTestControl.countEntity(ProcessorFilterTask.class));
        Assert.assertEquals(0, commonTestControl.countEntity(ProcessorFilterTask.class));

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        Assert.assertNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        streamTaskCreator.createTasks(new SimpleTaskContext());
        Assert.assertNotNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        Assert.assertFalse(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feedName1)
                                .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feedName2)
                                .build())
                        .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        commonTestScenarioCreator.createStreamProcessor(findStreamQueryData);

        for (int i = 0; i < 1000; i++) {
            commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
            commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);
        }

        final int initialQueueSize = StroomProperties.getIntProperty(StreamTaskCreatorImpl.STREAM_TASKS_QUEUE_SIZE_PROPERTY, 1000);
        StroomProperties.setIntProperty(StreamTaskCreatorImpl.STREAM_TASKS_QUEUE_SIZE_PROPERTY, 1000, StroomProperties.Source.TEST);
        StroomProperties.setBooleanProperty(StreamTaskCreatorImpl.STREAM_TASKS_FILL_TASK_QUEUE_PROPERTY, false, StroomProperties.Source.TEST);

        streamTaskCreator.createTasks(new SimpleTaskContext());

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // Assert.assertTrue(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());

        Assert.assertEquals(1000, commonTestControl.countEntity(ProcessorFilterTask.class));
        List<ProcessorFilterTask> tasks = streamTaskCreator.assignStreamTasks(node, 1000);
        Assert.assertEquals(1000, tasks.size());

        streamTaskCreator.createTasks(new SimpleTaskContext());
        Assert.assertTrue(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());
        Assert.assertEquals(2000, commonTestControl.countEntity(ProcessorFilterTask.class));
        tasks = streamTaskCreator.assignStreamTasks(node, 1000);
        Assert.assertEquals(1000, tasks.size());

        StroomProperties.setBooleanProperty(StreamTaskCreatorImpl.STREAM_TASKS_FILL_TASK_QUEUE_PROPERTY, true, StroomProperties.Source.TEST);
        StroomProperties.setIntProperty(StreamTaskCreatorImpl.STREAM_TASKS_QUEUE_SIZE_PROPERTY, initialQueueSize, StroomProperties.Source.TEST);
    }

    @Test
    public void testLifecycle() {
        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        Assert.assertEquals(0, commonTestControl.countEntity(ProcessorFilterTask.class));

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);

        Assert.assertEquals(0, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.createTasks(new SimpleTaskContext());

        Assert.assertEquals(1, commonTestControl.countEntity(ProcessorFilterTask.class));
        Assert.assertEquals(1, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.shutdown();

        Assert.assertEquals(1, commonTestControl.countEntity(ProcessorFilterTask.class));
        Assert.assertEquals(0, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.startup();
        Assert.assertEquals(0, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.createTasks(new SimpleTaskContext());
        Assert.assertEquals(1, streamTaskCreator.getStreamTaskQueueSize());
    }
}
