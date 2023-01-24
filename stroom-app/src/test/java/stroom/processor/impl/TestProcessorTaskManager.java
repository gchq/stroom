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

//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails()).isNull();
        processorTaskManager.createTasks();
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails()).isNotNull();
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isFalse();

        assertThat(getTaskCount()).isZero();

        // Double up on some processors
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName1);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName2);

        processorTaskManager.createTasks();
//        assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();
        assertThat(getTaskCount()).isEqualTo(4);

        commonTestScenarioCreator.createSample2LineRawFile(feedName1, StreamTypeNames.RAW_EVENTS);
        processorTaskManager.createTasks();

        assertThat(getTaskCount()).isEqualTo(6);

        processorTaskManager.createTasks();
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

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

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

        int size = 1000;
        LOGGER.logDurationIfInfoEnabled(() -> {
            addBulkMeta(feedName1, StreamTypeNames.RAW_EVENTS, Status.UNLOCKED, size);
            addBulkMeta(feedName2, StreamTypeNames.RAW_EVENTS, Status.UNLOCKED, size);
        }, "Creation of test files");

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(1000);

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();

        assertThat(getTaskCount()).isEqualTo(1000);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final ProcessorTaskList tasks = processorTaskManager.assignTasks(nodeName, 1000);
            assertThat(tasks.getList().size()).isEqualTo(1000);
        }, "assignTasks");

        processorTaskManager.createTasks();
        assertThat(getTaskCount()).isEqualTo(2000);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final ProcessorTaskList tasks = processorTaskManager.assignTasks(nodeName, 1000);
            assertThat(tasks.getList().size()).isEqualTo(1000);
        }, "assignTasks");

        processorConfig.setQueueSize(initialQueueSize);
    }

    @Test
    void testPerformance() {
        final String nodeName = nodeInfo.getThisNodeName();

        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isZero();
        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

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

        int size = TEST_SIZE;
        LOGGER.logDurationIfInfoEnabled(() -> {
            addBulkMeta(feedName1, StreamTypeNames.RAW_EVENTS, Status.UNLOCKED, size);
            addBulkMeta(feedName2, StreamTypeNames.RAW_EVENTS, Status.UNLOCKED, size);
        }, "Creation of test meta");

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(size);

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();

        assertThat(getTaskCount()).isEqualTo(size);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final ProcessorTaskList tasks = processorTaskManager.assignTasks(nodeName, size);
            assertThat(tasks.getList().size()).isEqualTo(size);
        }, "assignTasks1");

        processorTaskManager.createTasks();
        assertThat(getTaskCount()).isEqualTo(size * 2);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final ProcessorTaskList tasks = processorTaskManager.assignTasks(nodeName, size);
            assertThat(tasks.getList().size()).isEqualTo(size);
        }, "assignTasks2");

        processorConfig.setQueueSize(initialQueueSize);
    }

    @Test
    void testLockedUnlockedMetaPerformance() {
        final String nodeName = nodeInfo.getThisNodeName();

        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isZero();
        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

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

        int size = TEST_SIZE;
        LOGGER.logDurationIfInfoEnabled(() -> {
            addBulkMeta(feedName1, StreamTypeNames.RAW_EVENTS, Status.LOCKED, size);
            addBulkMeta(feedName2, StreamTypeNames.RAW_EVENTS, Status.LOCKED, size);
        }, "Creation of test meta");

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(size);

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();

        createAndAssign(1, nodeName, size);
        createAndAssign(2, nodeName, size);
        createAndAssign(3, nodeName, size);

        metaService.updateStatus(new FindMetaCriteria(), Status.LOCKED, Status.UNLOCKED);

        createAndAssign(4, nodeName, size);
        createAndAssign(5, nodeName, size);
        createAndAssign(6, nodeName, size);

        processorConfig.setQueueSize(initialQueueSize);
    }

    @Test
    void testUnlockedMetaPerformance() {
        final String nodeName = nodeInfo.getThisNodeName();

        processorTaskManager.shutdown();
        processorTaskManager.startup();

        assertThat(getTaskCount()).isZero();
        assertThat(getTaskCount()).isZero();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

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

        int size = TEST_SIZE;
        LOGGER.logDurationIfInfoEnabled(() -> {
            addBulkMeta(feedName1, StreamTypeNames.RAW_EVENTS, Status.UNLOCKED, size);
            addBulkMeta(feedName2, StreamTypeNames.RAW_EVENTS, Status.UNLOCKED, size);
        }, "Creation of test meta");

        final int initialQueueSize = processorConfig.getQueueSize();
        processorConfig.setQueueSize(size);

        LOGGER.logDurationIfInfoEnabled(() ->
                        processorTaskManager.createTasks(),
                "createTasks");

        // Because MySQL continues to create new incremental id's for streams this check will fail as Stroom thinks more
        // streams have been created so recreates recent stream info before this point which means that it doesn't have
        // recent stream info. This isn't a problem but this can't be checked in this test with MySql.
        // assertThat(processorTaskManager.getProcessorTaskManagerRecentStreamDetails().hasRecentDetail()).isTrue();

        createAndAssign(1, nodeName, size);
        createAndAssign(2, nodeName, size);
        createAndAssign(3, nodeName, size);
        createAndAssign(4, nodeName, size);
        createAndAssign(5, nodeName, size);
        createAndAssign(6, nodeName, size);

        processorConfig.setQueueSize(initialQueueSize);
    }

    private void createAndAssign(
            final int num,
            final String nodeName,
            final int size) {
        LOGGER.logDurationIfInfoEnabled(() -> {
            processorTaskManager.createTasks();
        }, "create - " + num);
        LOGGER.logDurationIfInfoEnabled(() -> {
            processorTaskManager.assignTasks(nodeName, size);
        }, "assign - " + num);
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
