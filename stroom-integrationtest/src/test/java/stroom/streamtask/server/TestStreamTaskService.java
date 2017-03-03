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

package stroom.streamtask.server;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;

import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.StreamTaskService;
import stroom.streamtask.shared.TaskStatus;
import stroom.task.server.TaskMonitorImpl;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class TestStreamTaskService extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamTaskService streamTaskService;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamTaskCreator streamTaskCreator;

    @Test
    public void testSaveAndGetAll() {
        final Feed efd = commonTestScenarioCreator.createSimpleFeed();
        final Stream file1 = commonTestScenarioCreator.createSample2LineRawFile(efd, StreamType.RAW_EVENTS);
        final Stream file2 = commonTestScenarioCreator.createSampleBlankProcessedFile(efd, file1);
        final Stream file3 = commonTestScenarioCreator.createSample2LineRawFile(efd, StreamType.RAW_EVENTS);

        commonTestScenarioCreator.createBasicTranslateStreamProcessor(efd);

        Assert.assertEquals("checking we can delete stand alone files", 1, streamStore.deleteStream(file3).intValue());

        // Create all required tasks.
        createTasks();

        final StreamTask ps1 = streamTaskService.find(FindStreamTaskCriteria.createWithStream(file1)).getFirst();
        Assert.assertNotNull(ps1);
        ps1.setStatus(TaskStatus.COMPLETE);

        streamTaskService.save(ps1);

        FindStreamTaskCriteria criteria = new FindStreamTaskCriteria();
        criteria.getFetchSet().add(Stream.ENTITY_TYPE);
        criteria.obtainStreamTaskStatusSet().add(TaskStatus.COMPLETE);

        Assert.assertEquals(1, streamTaskService.find(criteria).size());

        // Check the date filter works
        final FindStreamCriteria findStreamCriteria = criteria.obtainFindStreamCriteria();
        findStreamCriteria.setCreatePeriod(new Period(file1.getCreateMs() - 10000, file1.getCreateMs() + 10000));
        Assert.assertEquals(1, streamTaskService.find(criteria).size());

        findStreamCriteria.setCreatePeriod(
                new Period(Instant.ofEpochMilli(findStreamCriteria.getCreatePeriod().getFrom()).atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli(),
                        Instant.ofEpochMilli(findStreamCriteria.getCreatePeriod().getTo()).atZone(ZoneOffset.UTC).plusYears(100).toInstant().toEpochMilli()));
        Assert.assertEquals(0, streamTaskService.find(criteria).size());

        Assert.assertNotNull(streamStore.loadStreamById(file1.getId()));
        Assert.assertNotNull(streamStore.loadStreamById(file2.getId()));

        criteria = new FindStreamTaskCriteria();
        Assert.assertNotNull(streamTaskService.findSummary(criteria));
    }

    @Test
    public void testApplyAllCriteria() {
        commonTestScenarioCreator.createSimpleFeed();

        final Node testNode = new Node();
        testNode.setId(1L);

        final FindStreamTaskCriteria criteria = new FindStreamTaskCriteria();
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.obtainNodeIdSet().add(1L);
        criteria.setOrderBy(FindStreamTaskCriteria.ORDER_BY_CREATE_TIME);
        criteria.obtainStreamTaskIdSet().add(1L);
        criteria.obtainFindStreamCriteria().obtainFeeds().obtainInclude().add(1L);
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(1L);
        criteria.obtainFindStreamCriteria().obtainStreamTypeIdSet().add(1L);
        criteria.obtainStreamTaskStatusSet().add(TaskStatus.COMPLETE);

        criteria.getFindStreamCriteria()
                .setCreatePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
        criteria.getFindStreamCriteria()
                .setEffectivePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
        criteria.getFindStreamCriteria().obtainStreamTypeIdSet().add(StreamType.CONTEXT.getId());

        criteria.getFetchSet().add(Stream.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.getFetchSet().add(Feed.ENTITY_TYPE);
        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);

        Assert.assertEquals(0, streamTaskService.find(criteria).size());
    }

    @Test
    public void testApplyAllCriteriaSummary() {
        commonTestScenarioCreator.createSimpleFeed();

        final Node testNode = new Node();
        testNode.setId(1L);

        final FindStreamTaskCriteria criteria = new FindStreamTaskCriteria();
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.obtainNodeIdSet().add(1L);
        criteria.setOrderBy(FindStreamTaskCriteria.ORDER_BY_CREATE_TIME);
        criteria.obtainStreamTaskIdSet().add(1L);
        criteria.obtainFindStreamCriteria().obtainFeeds().obtainInclude().add(1L);
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(1L);
        criteria.obtainFindStreamCriteria().obtainStreamTypeIdSet().add(1L);
        criteria.obtainStreamTaskStatusSet().add(TaskStatus.COMPLETE);

        criteria.getFindStreamCriteria()
                .setCreatePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
        criteria.getFindStreamCriteria()
                .setEffectivePeriod(new Period(System.currentTimeMillis(), System.currentTimeMillis()));
        criteria.getFindStreamCriteria().getStreamTypeIdSet().add(StreamType.CONTEXT.getId());

    }

    private void createTasks() {
        // Make sure there are no tasks yet.
        streamTaskCreator.createTasks(new TaskMonitorImpl());
    }
}
