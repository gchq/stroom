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
import stroom.meta.shared.MetaFields;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FindProcessorTaskCriteria;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.data.shared.StreamTypeNames;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskManager extends AbstractCoreIntegrationTest {
    @Inject
    private ProcessorConfig processorConfig;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private ProcessorTaskManager processorTaskManager;
    @Inject
    private ProcessorTaskService processorTaskService;
    @Inject
    private NodeInfo nodeInfo;

    @Test
    void testBasic() {
        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);

        assertThat(getTaskCount()).isZero();

//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails()).isNull();
        processorTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails()).isNotNull();
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isFalse();

        assertThat(getTaskCount()).isZero();

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);

        processorTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(getTaskCount()).isEqualTo(4);

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        processorTaskManager.createTasks(new SimpleTaskContext());

        assertThat(getTaskCount()).isEqualTo(6);

        processorTaskManager.createTasks(new SimpleTaskContext());
        assertThat(getTaskCount()).isEqualTo(6);
    }

    @Test
    void testMultiFeedInitialCreate() {
        final String nodeName = nodeInfo.getThisNodeName();

        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isZero();
        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails()).isNull();
        processorTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails()).isNotNull();
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isFalse();

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName1)
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName2)
                                .build())
                        .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        commonTestScenarioCreator.createProcessor(findStreamQueryData);

        for (int i = 0; i < 1000; i++) {
            commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
            commonTestScenarioCreator.createSample2LineRawFile(feedName2, StreamTypeNames.RAW_EVENTS);
        }

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(1000);
        processorConfig.setFillTaskQueue(false);

        processorTaskManager.createTasks(new SimpleTaskContext());

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();

        assertThat(getTaskCount()).isEqualTo(1000);
        List<ProcessorTask> tasks = processorTaskManager.assignTasks(nodeName, 1000);
        assertThat(tasks.size()).isEqualTo(1000);

        processorTaskManager.createTasks(new SimpleTaskContext());
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(getTaskCount()).isEqualTo(2000);
        tasks = processorTaskManager.assignTasks(nodeName, 1000);
        assertThat(tasks.size()).isEqualTo(1000);

        processorConfig.setQueueSize(initialQueueSize);
        processorConfig.setFillTaskQueue(true);
    }

    private int getTaskCount() {
        return processorTaskService.find(new FindProcessorTaskCriteria()).size();
    }

    @Test
    void testLifecycle() {
        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isEqualTo(0);

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);

        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(0);

        processorTaskManager.createTasks(new SimpleTaskContext());

        assertThat(getTaskCount()).isEqualTo(1);
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(1);

        processorTaskManager.shutdown();

        assertThat(getTaskCount()).isEqualTo(1);
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(0);

        processorTaskManager.startup();
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(0);

        processorTaskManager.createTasks(new SimpleTaskContext());
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(1);
    }
}
