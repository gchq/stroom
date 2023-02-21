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

import stroom.data.shared.StreamTypeNames;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.impl.db.MetaDaoImpl;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskManager extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProcessorTaskManager.class);

    private static final int TEST_SIZE = 1000;

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
    @Inject
    private MetaService metaService;
    @Inject
    private MetaDaoImpl metaDao;

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
        processorTaskManager.createTasks();

        assertThat(getTaskCount()).isZero();

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);

        processorTaskManager.createTasks();
        assertThat(getTaskCount()).isEqualTo(4);

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        processorTaskManager.createTasks();

        assertThat(getTaskCount()).isEqualTo(6);

        processorTaskManager.createTasks();
        assertThat(getTaskCount()).isEqualTo(6);
    }

    @Test
    void testLockedUnlockedMetaPerformance() {
        final String nodeName = nodeInfo.getThisNodeName();
        final int size = TEST_SIZE;
        test(size, Status.LOCKED, () -> {
            // All meta is locked so tasks can be created but not queued or assigned.

            createTasks(1, size);
            assignTasks(1, nodeName, size, 0);

            createTasks(2, size * 2);
            assignTasks(2, nodeName, size, 0);

            createTasks(3, size * 2);
            assignTasks(3, nodeName, size, 0);

            // Unlock all meta so tasks can now be queued and assigned.
            metaService.updateStatus(new FindMetaCriteria(), Status.LOCKED, Status.UNLOCKED);

            createTasks(4, size * 2);
            assignTasks(4, nodeName, size, size);

            createTasks(5, size * 2);
            assignTasks(5, nodeName, size, size);

            createTasks(6, size * 2);
            assignTasks(6, nodeName, size, 0);
        });
    }

    @Test
    void testUnlockedMetaPerformance() {
        final String nodeName = nodeInfo.getThisNodeName();
        final int size = TEST_SIZE;
        test(size, Status.UNLOCKED, () -> {
            // All meta is unlocked so tasks can be created, queued and assigned.

            createTasks(1, size);
            assignTasks(1, nodeName, size, size);

            createTasks(2, size * 2);
            assignTasks(2, nodeName, size, size);

            createTasks(3, size * 2);
            assignTasks(3, nodeName, size, 0);

            createTasks(4, size * 2);
            assignTasks(4, nodeName, size, 0);

            createTasks(5, size * 2);
            assignTasks(5, nodeName, size, 0);

            createTasks(6, size * 2);
            assignTasks(6, nodeName, size, 0);
        });
    }

    private void test(final int size, final Status metaStatus, final Runnable runnable) {
        final int initialQueueSize = processorConfig.getQueueSize();
        try {
            processorConfig.setQueueSize(size);

            processorTaskManager.shutdown();
            processorTaskManager.startup();

            assertThat(getTaskCount()).isZero();
            assertThat(getTaskCount()).isZero();

            final String feedName1 = FileSystemTestUtil.getUniqueTestString();
            final String feedName2 = FileSystemTestUtil.getUniqueTestString();

            // Just try to create tasks to check none exist or are created at this time.
            createTasks(0, 0);

            final QueryData findStreamQueryData = QueryData
                    .builder()
                    .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                    .expression(ExpressionOperator.builder()
                            .addOperator(ExpressionOperator.builder().op(Op.OR)
                                    .addTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feedName1)
                                    .addTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feedName2)
                                    .build())
                            .addTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                            .build())
                    .build();

            commonTestScenarioCreator.createProcessor(findStreamQueryData);

            LOGGER.logDurationIfInfoEnabled(() -> {
                addBulkMeta(feedName1, StreamTypeNames.RAW_EVENTS, metaStatus, size);
                addBulkMeta(feedName2, StreamTypeNames.RAW_EVENTS, metaStatus, size);
            }, "Creation of test meta");

            runnable.run();

        } finally {
            processorConfig.setQueueSize(initialQueueSize);
        }
    }

    private void createTasks(final int callCount, final int expected) {
        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks " + callCount);
        assertThat(getTaskCount()).isEqualTo(expected);
    }

    private void assignTasks(final int callCount,
                             final String nodeName,
                             final int count,
                             final int expected) {
        final ProcessorTaskList tasks = LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.assignTasks(nodeName, count),
                "assignTasks - " + callCount);
        assertThat(tasks.getList().size()).isEqualTo(expected);
    }

    private void addBulkMeta(final String feed, final String streamType, final Status status, final int count) {
        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feed)
                .typeName(streamType)
                .build();
        final List<MetaProperties> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(metaProperties);
        }
        metaDao.bulkCreate(list, status);
    }

    @Disabled
    @Test
    void testPerformance() {
        Metrics.setEnabled(true);

        final String nodeName = nodeInfo.getThisNodeName();

        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isZero();
        assertThat(getTaskCount()).isZero();

        for (int j = 0; j < 1000; j++) {
            final String feedName = FileSystemTestUtil.getUniqueTestString();
            final QueryData findStreamQueryData = QueryData
                    .builder()
                    .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                    .expression(ExpressionOperator.builder()
                            .addOperator(ExpressionOperator.builder().op(Op.OR)
                                    .addTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feedName)
                                    .build())
                            .addTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                            .build())
                    .build();

            commonTestScenarioCreator.createProcessor(findStreamQueryData);

            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(feedName)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .build();
            Meta meta = metaService.create(metaProperties);
            meta = metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);
        }

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(1000000);
        processorConfig.setFillTaskQueue(false);

        processorTaskManager.createTasks();

        Metrics.report();

//        assertThat(getTaskCount()).isEqualTo(1000000);
//        ProcessorTaskList tasks = processorTaskManager.assignTasks(nodeName, 1000000);
//        assertThat(tasks.getList().size()).isEqualTo(1000000);
//
//        processorConfig.setQueueSize(initialQueueSize);
//        processorConfig.setFillTaskQueue(true);
//
//        processorConfig.getClass().equals(ProcessorConfig.class);
    }

    private int getTaskCount() {
        return processorTaskService.find(new ExpressionCriteria()).size();
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

        processorTaskManager.createTasks();

        assertThat(getTaskCount()).isEqualTo(1);
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(1);

        processorTaskManager.shutdown();

        assertThat(getTaskCount()).isEqualTo(1);
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(0);

        processorTaskManager.startup();
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(0);

        processorTaskManager.createTasks();
        assertThat(processorTaskManager.getTaskQueueSize()).isEqualTo(1);
    }
}
