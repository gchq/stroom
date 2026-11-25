/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl.dao;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskReaper;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699. End-to-end wiring of the reaper job: cluster lock taken, lease timeout read from
 * config (default 10m), stale PROCESSING work recovered, fresh work untouched. The detailed
 * boundary behaviour lives in {@code TestProcessorTaskDaoImpl.testReapDeadTasks}.
 */
class TestProcessorTaskReaper extends AbstractProcessorTest {

    @Test
    void reapRecoversTasksPastTheLeaseTimeout() {
        final ProcessorTaskReaper reaper = getInjector().getInstance(ProcessorTaskReaper.class);

        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);

        final Instant now = Instant.now();
        // Default lease timeout is 10m: 11m-stale is dead, 9m-stale is still leased.
        final long dead = createProcessorTask(
                filter, TaskStatus.PROCESSING, NODE1, FEED, now.minus(Duration.ofMinutes(11)));
        final long leased = createProcessorTask(
                filter, TaskStatus.PROCESSING, NODE1, FEED, now.minus(Duration.ofMinutes(9)));

        reaper.reap(now);

        assertThat(getTaskStatus(dead)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(dead)).isNull();
        assertThat(getTaskStatus(leased)).isEqualTo(TaskStatus.PROCESSING);
        assertThat(getTaskNodeId(leased)).isNotNull();
    }

    @Test
    void queueResidueIsOnlySweptWhenWorkerClaimingIsOn() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final Instant now = Instant.now();
        final long queued = createProcessorTask(
                filter, TaskStatus.QUEUED, NODE1, FEED, now.minus(Duration.ofMinutes(11)));
        final long assigned = createProcessorTask(
                filter, TaskStatus.ASSIGNED, NODE1, FEED, now.minus(Duration.ofMinutes(11)));
        final long recentlyQueued = createProcessorTask(
                filter, TaskStatus.QUEUED, NODE1, FEED, now.minus(Duration.ofMinutes(9)));

        // With the master queue in use these rows ARE the queue, so sweeping them would destroy
        // live work.
        reaper(false).reap(now);
        assertThat(getTaskStatus(queued)).isEqualTo(TaskStatus.QUEUED);
        assertThat(getTaskStatus(assigned)).isEqualTo(TaskStatus.ASSIGNED);

        // Once the cluster has been cut over to claiming, nothing writes either status, so a stale
        // one is residue that would otherwise never be processed by anything.
        reaper(true).reap(now);
        assertThat(getTaskStatus(queued)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(queued)).isNull();
        assertThat(getTaskStatus(assigned)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskStatus(recentlyQueued))
                .describedAs("a row the queue touched moments ago is not residue")
                .isEqualTo(TaskStatus.QUEUED);
    }

    /**
     * gh-5699 §3.4: a disabled reaper is silent by nature, so something else has to be able to see
     * that dead tasks are piling up.
     */
    @Test
    void deadTasksAreCountableWithoutRunningTheReaper() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final Instant now = Instant.now();
        createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, now.minus(Duration.ofMinutes(11)));
        createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, now.minus(Duration.ofMinutes(11)));
        createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, now.minus(Duration.ofMinutes(1)));
        createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED, now.minus(Duration.ofMinutes(11)));

        final Instant leaseExpiry = now.minus(Duration.ofMinutes(10));
        assertThat(processorTaskDao.countDeadTasks(leaseExpiry))
                .describedAs("only PROCESSING rows past the lease count as unrecovered")
                .isEqualTo(2);

        // Counting must not change anything - it is a diagnostic, not a second reaper.
        assertThat(processorTaskDao.countDeadTasks(leaseExpiry)).isEqualTo(2);

        reaper(true).reap(now);
        assertThat(processorTaskDao.countDeadTasks(leaseExpiry))
                .describedAs("a working reaper keeps this at zero, which is what makes it a signal")
                .isZero();
    }

    @Test
    void tasksThatHitAFaultAreTriedAgainAfterADelay() {
        final ProcessorTaskReaper reaper = getInjector().getInstance(ProcessorTaskReaper.class);
        final ProcessorFilter filter = createProcessorFilter(createProcessor());
        final Instant now = Instant.now();
        // The default delay is 10m.
        final long due = createProcessorTask(
                filter, TaskStatus.RETRY, NODE1, FEED, now.minus(Duration.ofMinutes(11)));
        final long waiting = createProcessorTask(
                filter, TaskStatus.RETRY, NODE1, FEED, now.minus(Duration.ofMinutes(9)));

        reaper.reap(now);

        assertThat(getTaskStatus(due)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskStatus(waiting)).isEqualTo(TaskStatus.RETRY);
    }

    @Test
    void warnsOfWaitingTasksOnceAnHourForEachFilter() {
        final ProcessorTaskDao dao = Mockito.mock(ProcessorTaskDao.class);
        final ProcessorTaskReaper reaper = reaper(false, dao);
        final Instant now = Instant.now();

        Mockito.when(dao.countRetryTasksByFilter()).thenReturn(Map.of(1, 10));
        Mockito.when(dao.findLatestRetryReason(Mockito.anyInt())).thenReturn(Optional.of("Expected"));
        reaper.reap(now);
        Mockito.verify(dao, Mockito.times(1)).findLatestRetryReason(1);

        // None waiting for a moment, as they have been put back, doesn't start the hour again.
        Mockito.when(dao.countRetryTasksByFilter()).thenReturn(Map.of());
        reaper.reap(now.plus(Duration.ofMinutes(1)));

        // Another filter has its own hour.
        Mockito.when(dao.countRetryTasksByFilter()).thenReturn(Map.of(1, 10, 2, 5));
        reaper.reap(now.plus(Duration.ofMinutes(59)));
        Mockito.verify(dao, Mockito.times(1)).findLatestRetryReason(1);
        Mockito.verify(dao, Mockito.times(1)).findLatestRetryReason(2);

        reaper.reap(now.plus(Duration.ofMinutes(60)));
        Mockito.verify(dao, Mockito.times(2)).findLatestRetryReason(1);
        Mockito.verify(dao, Mockito.times(1)).findLatestRetryReason(2);
    }

    @Test
    void failureToWarnDoesNotStopTasksFromBeingPutBack() {
        final ProcessorTaskDao dao = Mockito.mock(ProcessorTaskDao.class);
        Mockito.when(dao.countRetryTasksByFilter()).thenReturn(Map.of(1, 10));
        Mockito.when(dao.findLatestRetryReason(Mockito.anyInt())).thenThrow(new RuntimeException("Expected"));
        final Instant now = Instant.now();

        reaper(false, dao).reap(now);

        Mockito.verify(dao).putBackRetryTasks(
                Mockito.eq(1), Mockito.eq(now.minus(Duration.ofMinutes(10))), Mockito.anyInt());
    }

    @Test
    void failureToPutBackTasksOfAFilterDoesNotStopThoseOfOthers() {
        final ProcessorTaskDao dao = Mockito.mock(ProcessorTaskDao.class);
        Mockito.when(dao.countRetryTasksByFilter()).thenReturn(Map.of(1, 10, 2, 10));
        Mockito.when(dao.putBackRetryTasks(Mockito.anyInt(), Mockito.any(), Mockito.anyInt()))
                .thenThrow(new RuntimeException("Expected"));

        reaper(false, dao).reap(Instant.now());

        Mockito.verify(dao).putBackRetryTasks(Mockito.eq(1), Mockito.any(), Mockito.anyInt());
        Mockito.verify(dao).putBackRetryTasks(Mockito.eq(2), Mockito.any(), Mockito.anyInt());
    }

    private ProcessorTaskReaper reaper(final boolean claimTasksOnWorker) {
        return reaper(claimTasksOnWorker, processorTaskDao);
    }

    private ProcessorTaskReaper reaper(final boolean claimTasksOnWorker, final ProcessorTaskDao dao) {
        final ProcessorConfig processorConfig = Mockito.mock(ProcessorConfig.class);
        Mockito.when(processorConfig.getTaskLeaseTimeout()).thenReturn(StroomDuration.ofMinutes(10));
        Mockito.when(processorConfig.getTaskRetryDelay()).thenReturn(StroomDuration.ofMinutes(10));
        Mockito.when(processorConfig.isClaimTasksOnWorker()).thenReturn(claimTasksOnWorker);
        return new ProcessorTaskReaper(
                getInjector().getInstance(ClusterLockService.class),
                dao,
                () -> processorConfig);
    }
}
