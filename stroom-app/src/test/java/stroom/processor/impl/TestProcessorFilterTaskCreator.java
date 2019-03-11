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

package stroom.processor.impl;


import org.junit.jupiter.api.Test;
import stroom.meta.shared.MetaFieldNames;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorFilterTaskService;
import stroom.processor.shared.FindProcessorFilterTaskCriteria;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorFilterTaskManager extends AbstractCoreIntegrationTest {
    @Inject
    private ProcessorConfig processorConfig;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private ProcessorFilterTaskManager processorFilterTaskManager;
    @Inject
    private ProcessorFilterTaskService processorFilterTaskService;
    @Inject
    private NodeInfo nodeInfo;

    @Test
    void testBasic() {
        processorFilterTaskManager.shutdown();
        processorFilterTaskManager.startup();

        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);

        assertThat(getTaskCount()).isZero();

//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails()).isNull();
        processorFilterTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails()).isNotNull();
//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails().hasRecentDetail()).isFalse();

        assertThat(getTaskCount()).isZero();

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);

        processorFilterTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(getTaskCount()).isEqualTo(4);

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        processorFilterTaskManager.createTasks(new SimpleTaskContext());

        assertThat(getTaskCount()).isEqualTo(6);

        processorFilterTaskManager.createTasks(new SimpleTaskContext());
        assertThat(getTaskCount()).isEqualTo(6);
    }

    @Test
    void testMultiFeedInitialCreate() {
        final String nodeName = nodeInfo.getThisNodeName();

        processorFilterTaskManager.shutdown();
        processorFilterTaskManager.startup();

        assertThat(getTaskCount()).isZero();
        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails()).isNull();
        processorFilterTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails()).isNotNull();
//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails().hasRecentDetail()).isFalse();

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

        processorFilterTaskManager.createTasks(new SimpleTaskContext());

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();

        assertThat(getTaskCount()).isEqualTo(1000);
        List<ProcessorFilterTask> tasks = processorFilterTaskManager.assignStreamTasks(nodeName, 1000);
        assertThat(tasks.size()).isEqualTo(1000);

        processorFilterTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorFilterTaskManager.getProcessorFilterTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(getTaskCount()).isEqualTo(2000);
        tasks = processorFilterTaskManager.assignStreamTasks(nodeName, 1000);
        assertThat(tasks.size()).isEqualTo(1000);

        processorConfig.setQueueSize(initialQueueSize);
        processorConfig.setFillTaskQueue(true);
    }

    private int getTaskCount() {
        return processorFilterTaskService.find(new FindProcessorFilterTaskCriteria()).size();
    }

    @Test
    void testLifecycle() {
        processorFilterTaskManager.shutdown();
        processorFilterTaskManager.startup();

        assertThat(getTaskCount()).isEqualTo(0);

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);

        assertThat(processorFilterTaskManager.getStreamTaskQueueSize()).isEqualTo(0);

        processorFilterTaskManager.createTasks(new SimpleTaskContext());

        assertThat(getTaskCount()).isEqualTo(1);
        assertThat(processorFilterTaskManager.getStreamTaskQueueSize()).isEqualTo(1);

        processorFilterTaskManager.shutdown();

        assertThat(getTaskCount()).isEqualTo(1);
        assertThat(processorFilterTaskManager.getStreamTaskQueueSize()).isEqualTo(0);

        processorFilterTaskManager.startup();
        assertThat(processorFilterTaskManager.getStreamTaskQueueSize()).isEqualTo(0);

        processorFilterTaskManager.createTasks(new SimpleTaskContext());
        assertThat(processorFilterTaskManager.getStreamTaskQueueSize()).isEqualTo(1);
    }
}
