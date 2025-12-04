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

package stroom.processor.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.node.shared.Node;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskService extends AbstractCoreIntegrationTest {

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ProcessorTaskService processorTaskService;
    @Inject
    private ProcessorTaskDao processorTaskDao;
    @Inject
    private MetaService metaService;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;

    @Test
    void testSaveAndGetAll() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final Meta file1 = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        final Meta file2 = commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, file1);
        final Meta file3 = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);

        assertThat(metaService.delete(file3.getId())).as("checking we can delete stand alone files").isEqualTo(1);

        // Create all required tasks.
        createTasks();

        final ProcessorTask ps1 = processorTaskService.find(
                        new ExpressionCriteria(ProcessorTaskExpressionUtil.createWithStream(file1)))
                .getFirst();
        assertThat(ps1).isNotNull();
        processorTaskDao.changeTaskStatus(ps1,
                ps1.getNodeName(),
                TaskStatus.COMPLETE,
                ps1.getStartTimeMs(),
                ps1.getEndTimeMs());

        final ExpressionOperator expressionOperator1 = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .build();
        ExpressionCriteria criteria = new ExpressionCriteria(expressionOperator1);
        assertThat(processorTaskService.find(criteria).size()).isEqualTo(1);

        // Check the date filter works
        final ExpressionOperator expressionOperator2 = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        file1.getCreateMs() - 10000)
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS, Condition.LESS_THAN, file1.getCreateMs() + 10000)
                .build();
        criteria.setExpression(expressionOperator2);
        //setCreatePeriod(new Period(file1.getCreateMs() - 10000, file1.getCreateMs() + 10000));
        assertThat(processorTaskService.find(criteria).size()).isEqualTo(1);

        final ExpressionOperator expressionOperator3 = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        Instant.ofEpochMilli(file1.getCreateMs() - 10000)
                                .atZone(ZoneOffset.UTC).plusYears(100)
                                .toInstant()
                                .toEpochMilli())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.LESS_THAN,
                        Instant.ofEpochMilli(file1.getCreateMs() + 10000)
                                .atZone(ZoneOffset.UTC)
                                .plusYears(100)
                                .toInstant()
                                .toEpochMilli())
                .build();
        criteria.setExpression(expressionOperator3);
//        criteria.setCreatePeriod(
//                new Period(Instant.ofEpochMilli(criteria.getCreatePeriod().getFrom())
//                .atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli(),
//                        Instant.ofEpochMilli(criteria.getCreatePeriod().getTo())
//                        .atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli()));
        assertThat(processorTaskService.find(criteria).size()).isEqualTo(0);

        assertThat(metaService.getMeta(file1.getId())).isNotNull();
        assertThat(metaService.getMeta(file2.getId())).isNotNull();

        criteria = new ExpressionCriteria();
        assertThat(processorTaskService.findSummary(criteria)).isNotNull();
    }

    @Test
    void testBulkSaveAndGetAll() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final Meta file1 = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        final Meta file2 = commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, file1);
        final Meta file3 = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);

        assertThat(metaService.delete(file3.getId())).as("checking we can delete stand alone files").isEqualTo(1);

        // Create all required tasks.
        createTasks();

        final ProcessorTask ps1 = processorTaskService.find(
                        new ExpressionCriteria(ProcessorTaskExpressionUtil.createWithStream(file1)))
                .getFirst();
        assertThat(ps1).isNotNull();

        final ExpressionOperator updateExpressionOperator = ExpressionOperator.builder()
                .addIdTerm(ProcessorTaskFields.TASK_ID, Condition.EQUALS, ps1.getId())
                .build();
        final ExpressionCriteria updateCriteria = new ExpressionCriteria(updateExpressionOperator);
        final ResultPage<ProcessorTask> updatedTasks = processorTaskDao.changeTaskStatus(updateCriteria,
                ps1.getNodeName(),
                TaskStatus.COMPLETE,
                ps1.getStartTimeMs(),
                ps1.getEndTimeMs());

        assertThat(updatedTasks.size()).isEqualTo(1);
        assertThat(updatedTasks.getFirst().getId()).isEqualTo(ps1.getId());
        assertThat(updatedTasks.getFirst().getStatus()).isEqualTo(TaskStatus.COMPLETE);

        // Check the date filter works
        final ExpressionOperator expressionOperator2 = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        file1.getCreateMs() - 10000)
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS, Condition.LESS_THAN, file1.getCreateMs() + 10000)
                .build();
        ExpressionCriteria criteria = new ExpressionCriteria(expressionOperator2);
        //setCreatePeriod(new Period(file1.getCreateMs() - 10000, file1.getCreateMs() + 10000));
        assertThat(processorTaskService.find(criteria).size()).isEqualTo(1);

        final ExpressionOperator expressionOperator3 = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        Instant.ofEpochMilli(file1.getCreateMs() - 10000)
                                .atZone(ZoneOffset.UTC).plusYears(100)
                                .toInstant()
                                .toEpochMilli())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.LESS_THAN,
                        Instant.ofEpochMilli(file1.getCreateMs() + 10000)
                                .atZone(ZoneOffset.UTC)
                                .plusYears(100)
                                .toInstant()
                                .toEpochMilli())
                .build();
        criteria.setExpression(expressionOperator3);
//        criteria.setCreatePeriod(
//                new Period(Instant.ofEpochMilli(criteria.getCreatePeriod().getFrom())
//                .atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli(),
//                        Instant.ofEpochMilli(criteria.getCreatePeriod().getTo())
//                        .atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli()));
        assertThat(processorTaskService.find(criteria).size()).isEqualTo(0);

        assertThat(metaService.getMeta(file1.getId())).isNotNull();
        assertThat(metaService.getMeta(file2.getId())).isNotNull();

        criteria = new ExpressionCriteria();
        assertThat(processorTaskService.findSummary(criteria)).isNotNull();
    }

    @Test
    void testApplyAllCriteria() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Node testNode = new Node();
        testNode.setId(1);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.NODE_NAME, Condition.EQUALS, "Node name")
                .addIdTerm(ProcessorTaskFields.TASK_ID, Condition.EQUALS, 1)
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        System.currentTimeMillis())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS, Condition.LESS_THAN, System.currentTimeMillis())
                .build();
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
//        criteria.obtainNodeNameCriteria().setString("Node name");
        criteria.setSort(ProcessorTaskFields.FIELD_CREATE_TIME);
//        criteria.obtainProcessorTaskIdSet().add(1L);
//        criteria.obtainFeedNameSet().add(feedName);
//        criteria.obtainMetaIdSet().add(1L);
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.RAW_EVENTS);
//        criteria.obtainTaskStatusSet().add(TaskStatus.COMPLETE);

//        criteria.setCreatePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.setEffectivePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.CONTEXT);

        assertThat(processorTaskService.find(criteria).size()).isZero();
    }

    @Test
    void testApplyAllCriteriaSummary() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Node testNode = new Node();
        testNode.setId(1);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.NODE_NAME, Condition.EQUALS, "Node name")
                .addIdTerm(ProcessorTaskFields.TASK_ID, Condition.EQUALS, 1)
                .addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.COMPLETE.getDisplayValue())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        System.currentTimeMillis())
                .addIdTerm(ProcessorTaskFields.CREATE_TIME_MS, Condition.LESS_THAN, System.currentTimeMillis())
                .build();
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
//        criteria.obtainNodeNameCriteria().setString("Node name");
//        criteria.setSort(FindProcessorTaskCriteria.FIELD_CREATE_TIME);
//        criteria.obtainProcessorTaskIdSet().add(1L);
//        criteria.obtainFeedNameSet().add(feedName);
//        criteria.obtainMetaIdSet().add(1L);
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.RAW_EVENTS);
//        criteria.obtainTaskStatusSet().add(TaskStatus.COMPLETE);

//        criteria.setCreatePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.setEffectivePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.CONTEXT);

        assertThat(processorTaskService.findSummary(criteria).size()).isZero();
    }

    private void createTasks() {
        // Make sure there are no tasks yet.
        processorTaskTestHelper.createAndQueueTasks();
    }
}
