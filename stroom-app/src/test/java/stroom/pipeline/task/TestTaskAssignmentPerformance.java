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

package stroom.pipeline.task;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.api.FeedStore;
import stroom.job.impl.DistributedTaskFetcher;
import stroom.job.impl.FindJobCriteria;
import stroom.job.impl.JobBootstrap;
import stroom.job.impl.JobDao;
import stroom.job.impl.JobNodeDao;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeListResponse;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.impl.db.MetaDaoImpl;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.pipeline.PipelineStore;
import stroom.processor.api.JobNames;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.impl.DataProcessorTaskFactory;
import stroom.processor.impl.PrioritisedFilters;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskCreatorImpl;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProgressMonitor;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.task.api.SimpleTaskContext;
import stroom.test.CoreTestModule;
import stroom.test.StroomIntegrationTest;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringCriteria;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(DbTestModule.class)
@IncludeModule(CoreTestModule.class)
public class TestTaskAssignmentPerformance extends StroomIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestTaskAssignmentPerformance.class);

    @Inject
    private FeedStore feedStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private MetaService metaService;
    @Inject
    private MetaDaoImpl metaDao;
    @Inject
    private ProcessorFilterService processorFilterService;

    @Inject
    private PrioritisedFilters prioritisedFilters;
    @Inject
    private ProcessorTaskCreatorImpl processorTaskCreator;
    @Inject
    private ProcessorTaskDao processorTaskDao;
    @Inject
    private Provider<ProcessorConfig> processorConfigProvider;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private JobBootstrap jobBootstrap;
    @Inject
    private JobDao jobDao;
    @Inject
    private JobNodeDao jobNodeDao;

    @Inject
    private DistributedTaskFetcher distributedTaskFetcher;
    @Inject
    private DataProcessorTaskFactory dataProcessorTaskFactory;

    @Test
    void testUpFrontTaskCreation() {
        final int metaCount = 100;
        final CountDownLatch countDownLatch = new CountDownLatch(metaCount);
        final ProcessorFilter filter = setup(metaCount, countDownLatch);

        // Create tasks.
        LOGGER.info("Creating tasks");
        assertThat(processorTaskDao.find(new ExpressionCriteria()).size()).isZero();
        processorConfigProvider.get().setSkipNonProducingFiltersDuration(StroomDuration.ZERO);
        prioritisedFilters.clear();

        // Manually create tasks.
        processorTaskCreator.createTasksForFilter(
                new SimpleTaskContext(),
                filter,
                new ProgressMonitor(1),
                metaCount,
                new LongAdder());

        // Fetch tasks and execute them.
        executeTasks(countDownLatch);
    }

    @Test
    void testPeriodicTaskCreation() {
        final int metaCount = 100;
        final CountDownLatch countDownLatch = new CountDownLatch(metaCount);
        final ProcessorFilter filter = setup(metaCount, countDownLatch);

        // Create tasks.
        LOGGER.info("Creating tasks");
        assertThat(processorTaskDao.find(new ExpressionCriteria()).size()).isZero();
        processorConfigProvider.get().setSkipNonProducingFiltersDuration(StroomDuration.ZERO);
        prioritisedFilters.clear();

        try (final ScheduledExecutorService scheduledExecutorService =
                Executors.newScheduledThreadPool(1)) {
            scheduledExecutorService.scheduleAtFixedRate(() ->
                    processorTaskCreator.exec(), 0, 10, TimeUnit.SECONDS);

            // Fetch tasks and execute them.
            executeTasks(countDownLatch);
        }
    }

    private void executeTasks(final CountDownLatch countDownLatch) {
        // Fetch tasks and execute them.
        LOGGER.info("Executing tasks");
        LOGGER.logDurationIfInfoEnabled(() -> {
            try {
                // New request tasks to execute.
                distributedTaskFetcher.execute();

                countDownLatch.await();
            } catch (final InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
        }, "Completed task fetching and execution");
    }

    ProcessorFilter setup(final int metaCount,
                          final CountDownLatch countDownLatch) {
        jobBootstrap.startup();

        final AtomicLong executionCount = new AtomicLong();
        dataProcessorTaskFactory.setRunnableFactory(processorTask -> () -> {
            final long count = executionCount.incrementAndGet();
            if (count % (metaCount / 10) == 0) {
                LOGGER.info("Execute " + count);
            }
            countDownLatch.countDown();
        });

        final ResultPage<Job> jobs = jobDao.find(new FindJobCriteria(
                PageRequest.oneRow(),
                Collections.emptyList(),
                new StringCriteria(JobNames.DATA_PROCESSOR)));
        final Job job = jobs.getFirst();
        job.setEnabled(true);
        jobDao.update(job);

        final JobNodeListResponse response = jobNodeDao.find(new FindJobNodeCriteria(
                PageRequest.oneRow(),
                Collections.emptyList(),
                new StringCriteria(JobNames.DATA_PROCESSOR),
                null, null));
        final JobNode jobNode = response.getFirst();
        jobNode.setEnabled(true);
        jobNodeDao.update(jobNode);

        final String feedName = "TEST-FEED";
        final DocRef feedRef = feedStore.createDocument(feedName);
        final DocRef pipelineRef = pipelineStore.createDocument(feedName);

        // Add the associated data to the stream store.
        LOGGER.info("Adding data");
        assertThat(metaService.find(new FindMetaCriteria()).size()).isZero();

        final List<MetaProperties> list = IntStream
                .range(0, metaCount)
                .mapToObj(i -> MetaProperties.builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(System.currentTimeMillis())
                        .build()).toList();
        metaDao.bulkCreate(list, Status.UNLOCKED);
        LOGGER.info("Added data");
        LOGGER.info("Finding unlocked");
        assertThat(metaService.find(
                new FindMetaCriteria(MetaExpressionUtil.createStatusExpression(Status.UNLOCKED))
        ).size()).isEqualTo(metaCount);
        LOGGER.info("Found unlocked");

        // Setup the stream processor filter.
        final QueryData findStreamQueryData = QueryData.builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(ExpressionOperator.builder()
                        .addTextTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        // Now create the processor filter using the find stream criteria.
        final CreateProcessFilterRequest request = CreateProcessFilterRequest
                .builder()
                .processorType(ProcessorType.PIPELINE)
                .pipeline(pipelineRef)
                .queryData(findStreamQueryData)
                .autoPriority(true)
                .enabled(true)
                .minMetaCreateTimeMs(0L)
                .maxMetaCreateTimeMs(Long.MAX_VALUE)
                .build();
        return processorFilterService.create(request);
    }
}
