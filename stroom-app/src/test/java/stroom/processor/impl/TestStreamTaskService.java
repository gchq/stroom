/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.processor.impl;


import org.junit.jupiter.api.Test;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.node.shared.Node;
import stroom.processor.api.ProcessorFilterTaskService;
import stroom.processor.shared.FindProcessorFilterTaskCriteria;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.TaskStatus;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.Period;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamTaskService extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ProcessorFilterTaskService processorFilterTaskService;
    @Inject
    private ProcessorFilterTaskDao processorFilterTaskDao;
    @Inject
    private MetaService metaService;
    @Inject
    private ProcessorFilterTaskManager processorFilterTaskManager;

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

        final ProcessorFilterTask ps1 = processorFilterTaskService.find(FindProcessorFilterTaskCriteria.createWithStream(file1)).getFirst();
        assertThat(ps1).isNotNull();
        processorFilterTaskDao.changeTaskStatus(ps1, ps1.getNodeName(), TaskStatus.COMPLETE, ps1.getStartTimeMs(), ps1.getEndTimeMs());

        FindProcessorFilterTaskCriteria criteria = new FindProcessorFilterTaskCriteria();
        criteria.obtainTaskStatusSet().add(TaskStatus.COMPLETE);

        assertThat(processorFilterTaskService.find(criteria).size()).isEqualTo(1);

        // Check the date filter works
        criteria.setCreatePeriod(new Period(file1.getCreateMs() - 10000, file1.getCreateMs() + 10000));
        assertThat(processorFilterTaskService.find(criteria).size()).isEqualTo(1);

        criteria.setCreatePeriod(
                new Period(Instant.ofEpochMilli(criteria.getCreatePeriod().getFrom()).atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli(),
                        Instant.ofEpochMilli(criteria.getCreatePeriod().getTo()).atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli()));
        assertThat(processorFilterTaskService.find(criteria).size()).isEqualTo(0);

        assertThat(metaService.getMeta(file1.getId())).isNotNull();
        assertThat(metaService.getMeta(file2.getId())).isNotNull();

        criteria = new FindProcessorFilterTaskCriteria();
        assertThat(processorFilterTaskService.findSummary(criteria)).isNotNull();
    }

    @Test
    void testApplyAllCriteria() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Node testNode = new Node();
        testNode.setId(1L);

        final FindProcessorFilterTaskCriteria criteria = new FindProcessorFilterTaskCriteria();
        criteria.obtainNodeNameCriteria().setString("Node name");
        criteria.setSort(FindProcessorFilterTaskCriteria.FIELD_CREATE_TIME);
        criteria.obtainProcessorFilterTaskIdSet().add(1L);
//        criteria.obtainFeedNameSet().add(feedName);
        criteria.obtainMetaIdSet().add(1L);
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.RAW_EVENTS);
        criteria.obtainTaskStatusSet().add(TaskStatus.COMPLETE);

        criteria.setCreatePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.setEffectivePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.CONTEXT);

        assertThat(processorFilterTaskService.find(criteria).size()).isEqualTo(0);
    }

    @Test
    void testApplyAllCriteriaSummary() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Node testNode = new Node();
        testNode.setId(1L);

        final FindProcessorFilterTaskCriteria criteria = new FindProcessorFilterTaskCriteria();
        criteria.obtainNodeNameCriteria().setString("Node name");
        criteria.setSort(FindProcessorFilterTaskCriteria.FIELD_CREATE_TIME);
        criteria.obtainProcessorFilterTaskIdSet().add(1L);
//        criteria.obtainFeedNameSet().add(feedName);
        criteria.obtainMetaIdSet().add(1L);
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.RAW_EVENTS);
        criteria.obtainTaskStatusSet().add(TaskStatus.COMPLETE);

        criteria.setCreatePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.setEffectivePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
//        criteria.obtainStreamTypeNameSet().add(StreamTypeNames.CONTEXT);

    }

    private void createTasks() {
        // Make sure there are no tasks yet.
        processorFilterTaskManager.createTasks(new SimpleTaskContext());
    }
}
