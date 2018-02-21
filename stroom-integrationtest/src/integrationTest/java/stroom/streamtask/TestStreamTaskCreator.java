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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.shared.Feed;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamTask;
import stroom.task.TaskMonitorImpl;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.config.StroomProperties;

import javax.annotation.Resource;
import java.util.List;

public class TestStreamTaskCreator extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStreamTaskCreator.class);

    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private NodeCache nodeCache;

    @Test
    public void testBasic() {
        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        final Feed feed1 = commonTestScenarioCreator.createSimpleFeed();
        final Feed feed2 = commonTestScenarioCreator.createSimpleFeed();

        commonTestScenarioCreator.createSample2LineRawFile(feed1, StreamType.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feed2, StreamType.RAW_EVENTS);

        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        Assert.assertNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        streamTaskCreator.createTasks(new TaskMonitorImpl());
        Assert.assertNotNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        Assert.assertFalse(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());

        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feed1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feed2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feed1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feed2);

        streamTaskCreator.createTasks(new TaskMonitorImpl());
        Assert.assertTrue(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());
        Assert.assertEquals(4, commonTestControl.countEntity(StreamTask.class));

        commonTestScenarioCreator.createSample2LineRawFile(feed1, StreamType.RAW_EVENTS);
        streamTaskCreator.createTasks(new TaskMonitorImpl());

        Assert.assertEquals(6, commonTestControl.countEntity(StreamTask.class));

        streamTaskCreator.createTasks(new TaskMonitorImpl());
        Assert.assertEquals(6, commonTestControl.countEntity(StreamTask.class));
    }

    @Test
    public void testMultiFeedInitialCreate() {
        final Node node = nodeCache.getDefaultNode();

        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));
        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        final Feed feed1 = commonTestScenarioCreator.createSimpleFeed();
        final Feed feed2 = commonTestScenarioCreator.createSimpleFeed();

        Assert.assertNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        streamTaskCreator.createTasks(new TaskMonitorImpl());
        Assert.assertNotNull(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails());
        Assert.assertFalse(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                    .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                        .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feed1.getName())
                        .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feed2.getName())
                        .build())
                    .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamType.RAW_EVENTS.getName())
                    .build())
                .build();

        commonTestScenarioCreator.createStreamProcessor(findStreamQueryData);

        for (int i = 0; i < 3000; i++) {
            commonTestScenarioCreator.createSample2LineRawFile(feed1, StreamType.RAW_EVENTS);
            commonTestScenarioCreator.createSample2LineRawFile(feed2, StreamType.RAW_EVENTS);
        }

        final int initialQueueSize = StroomProperties.getIntProperty(StreamTaskCreatorImpl.STREAM_TASKS_QUEUE_SIZE_PROPERTY, 1000);
        StroomProperties.setIntProperty(StreamTaskCreatorImpl.STREAM_TASKS_QUEUE_SIZE_PROPERTY, 5000, StroomProperties.Source.TEST);
        StroomProperties.setBooleanProperty(StreamTaskCreatorImpl.STREAM_TASKS_FILL_TASK_QUEUE_PROPERTY, false, StroomProperties.Source.TEST);

        streamTaskCreator.createTasks(new TaskMonitorImpl());

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // Assert.assertTrue(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());

        Assert.assertEquals(5000, commonTestControl.countEntity(StreamTask.class));
        List<StreamTask> tasks = streamTaskCreator.assignStreamTasks(node, 5000);
        Assert.assertEquals(5000, tasks.size());

        streamTaskCreator.createTasks(new TaskMonitorImpl());
        Assert.assertTrue(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail());
        Assert.assertEquals(6000, commonTestControl.countEntity(StreamTask.class));
        tasks = streamTaskCreator.assignStreamTasks(node, 5000);
        Assert.assertEquals(1000, tasks.size());

        StroomProperties.setBooleanProperty(StreamTaskCreatorImpl.STREAM_TASKS_FILL_TASK_QUEUE_PROPERTY, true, StroomProperties.Source.TEST);
        StroomProperties.setIntProperty(StreamTaskCreatorImpl.STREAM_TASKS_QUEUE_SIZE_PROPERTY, initialQueueSize, StroomProperties.Source.TEST);
    }

    @Test
    public void testLifecycle() {
        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        final Feed feed = commonTestScenarioCreator.createSimpleFeed();
        commonTestScenarioCreator.createSample2LineRawFile(feed, StreamType.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feed);

        Assert.assertEquals(0, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.createTasks(new TaskMonitorImpl());

        Assert.assertEquals(1, commonTestControl.countEntity(StreamTask.class));
        Assert.assertEquals(1, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.shutdown();

        Assert.assertEquals(1, commonTestControl.countEntity(StreamTask.class));
        Assert.assertEquals(0, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.startup();
        Assert.assertEquals(0, streamTaskCreator.getStreamTaskQueueSize());

        streamTaskCreator.createTasks(new TaskMonitorImpl());
        Assert.assertEquals(1, streamTaskCreator.getStreamTaskQueueSize());
    }
}
