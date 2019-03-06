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


import org.junit.jupiter.api.Test;
import stroom.meta.shared.MetaFieldNames;
import stroom.node.api.NodeInfo;
import stroom.processor.ProcessorConfig;
import stroom.processor.impl.db.StreamTaskCreator;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.processor.shared.QueryData;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamTaskCreator extends AbstractCoreIntegrationTest {
    @Inject
    private ProcessorConfig processorConfig;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private StreamTaskCreator streamTaskCreator;
    @Inject
    private NodeInfo nodeInfo;

    @Test
    void testBasic() {
        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(0);

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(0);

//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails()).isNull();
        streamTaskCreator.createTasks(new SimpleTaskContext());
//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails()).isNotNull();
//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail()).isFalse();

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(0);

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);

        streamTaskCreator.createTasks(new SimpleTaskContext());
//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(4);

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        streamTaskCreator.createTasks(new SimpleTaskContext());

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(6);

        streamTaskCreator.createTasks(new SimpleTaskContext());
        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(6);
    }

    @Test
    void testMultiFeedInitialCreate() {
        final String nodeName = nodeInfo.getThisNodeName();

        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(0);
        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(0);

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails()).isNull();
        streamTaskCreator.createTasks(new SimpleTaskContext());
//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails()).isNotNull();
//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail()).isFalse();

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(MetaFieldNames.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFieldNames.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName1)
                                .addTerm(MetaFieldNames.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName2)
                                .build())
                        .addTerm(MetaFieldNames.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        commonTestScenarioCreator.createStreamProcessor(findStreamQueryData);

        for (int i = 0; i < 1000; i++) {
            commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
            commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);
        }

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(1000);
        processorConfig.setFillTaskQueue(false);

        streamTaskCreator.createTasks(new SimpleTaskContext());

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail()).isTrue();

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(1000);
        List<ProcessorFilterTask> tasks = streamTaskCreator.assignStreamTasks(nodeName, 1000);
        assertThat(tasks.size()).isEqualTo(1000);

        streamTaskCreator.createTasks(new SimpleTaskContext());
//        assertThat(streamTaskCreator.getStreamTaskCreatorRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(2000);
        tasks = streamTaskCreator.assignStreamTasks(nodeName, 1000);
        assertThat(tasks.size()).isEqualTo(1000);

        processorConfig.setQueueSize(initialQueueSize);
        processorConfig.setFillTaskQueue(true);
    }

    @Test
    void testLifecycle() {
        streamTaskCreator.shutdown();
        streamTaskCreator.startup();

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(0);

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);

        assertThat(streamTaskCreator.getStreamTaskQueueSize()).isEqualTo(0);

        streamTaskCreator.createTasks(new SimpleTaskContext());

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(1);
        assertThat(streamTaskCreator.getStreamTaskQueueSize()).isEqualTo(1);

        streamTaskCreator.shutdown();

        assertThat(commonTestControl.countEntity(ProcessorFilterTask.TABLE_NAME)).isEqualTo(1);
        assertThat(streamTaskCreator.getStreamTaskQueueSize()).isEqualTo(0);

        streamTaskCreator.startup();
        assertThat(streamTaskCreator.getStreamTaskQueueSize()).isEqualTo(0);

        streamTaskCreator.createTasks(new SimpleTaskContext());
        assertThat(streamTaskCreator.getStreamTaskQueueSize()).isEqualTo(1);
    }
}
