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

import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskHeartbeat;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699. The heartbeat may only ever stamp rows that are PROCESSING <em>and</em> owned by this
 * node <em>and</em> registered — a task that has completed, lost its lease, or was never moved to
 * PROCESSING by us must be left alone, because a wrongly-fresh status time would hide a dead task
 * from the reaper, and a stamp on a row another node now owns would mask a lost lease. And once
 * renewal has failed for longer than the lease timeout, the node must terminate its own in-flight
 * work (self-fencing) — the reaper may already have handed those tasks to other nodes.
 */
class TestProcessorTaskHeartbeat extends AbstractProcessorTest {

    private static final Instant SEED_TIME = Instant.ofEpochMilli(1_000);
    private static final long HEARTBEAT_TIME_MS = 555_000;
    private static final long LATER_HEARTBEAT_TIME_MS = 666_000;

    @Test
    void renewStampsOnlyOwnedProcessingRegisteredTasks() {
        final ProcessorTaskHeartbeat heartbeat = getInjector().getInstance(ProcessorTaskHeartbeat.class);
        Mockito.when(getInjector().getInstance(NodeInfo.class).getThisNodeName()).thenReturn(NODE1);

        // Nothing registered — no work, no DB interaction needed.
        assertThat(heartbeat.renew(HEARTBEAT_TIME_MS)).isZero();

        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);

        final long processing = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final long unregistered = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final long lostLease = createProcessorTask(filter, TaskStatus.PROCESSING, NODE2, FEED, SEED_TIME);
        final long queued = createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED, SEED_TIME);
        final long complete = createProcessorTask(filter, TaskStatus.COMPLETE, NODE1, FEED, SEED_TIME);

        // Register everything except 'unregistered' — including rows the stamp must then
        // refuse: owned by another node (lost lease), no longer PROCESSING.
        heartbeat.register(processing, filter.getId(), null);
        heartbeat.register(lostLease, filter.getId(), null);
        heartbeat.register(queued, filter.getId(), null);
        heartbeat.register(complete, filter.getId(), null);
        assertThat(heartbeat.size()).isEqualTo(4);

        assertThat(heartbeat.renew(HEARTBEAT_TIME_MS)).isEqualTo(1);

        assertThat(getTaskStatusTimeMs(processing)).isEqualTo(HEARTBEAT_TIME_MS);
        assertThat(getTaskStatusTimeMs(unregistered)).isEqualTo(SEED_TIME.toEpochMilli());
        assertThat(getTaskStatusTimeMs(lostLease)).isEqualTo(SEED_TIME.toEpochMilli());
        assertThat(getTaskStatusTimeMs(queued)).isEqualTo(SEED_TIME.toEpochMilli());
        assertThat(getTaskStatusTimeMs(complete)).isEqualTo(SEED_TIME.toEpochMilli());

        // The version must not move — heartbeats must not disturb optimistic locking on
        // status changes.
        assertThat(getTaskVersion(processing)).isEqualTo(getTaskVersion(unregistered));

        // Once deregistered (task finished), later heartbeats leave the row alone.
        heartbeat.deregister(processing);
        assertThat(heartbeat.renew(LATER_HEARTBEAT_TIME_MS)).isZero();
        assertThat(getTaskStatusTimeMs(processing)).isEqualTo(HEARTBEAT_TIME_MS);

        // Deregistering something never registered is a no-op.
        heartbeat.deregister(unregistered);
        assertThat(heartbeat.size()).isEqualTo(3);
    }

    @Test
    void selfFenceTerminatesInFlightTasksWhenRenewalOverdue() {
        final TaskManager taskManager = Mockito.mock(TaskManager.class);
        final NodeInfo nodeInfo = getInjector().getInstance(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE1);
        final ProcessorTaskHeartbeat heartbeat = new ProcessorTaskHeartbeat(
                processorTaskDao, nodeInfo, taskManager, ProcessorConfig::new);
        final long leaseTimeoutMs = new ProcessorConfig().getTaskLeaseTimeout().toMillis();

        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final long taskId = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final TaskId stroomTaskId = new TaskId("fence-me", null);
        heartbeat.register(taskId, filter.getId(), stroomTaskId);

        final long t0 = 1_000_000;
        assertThat(heartbeat.renew(t0)).isEqualTo(1);

        // Renewal current — nothing terminated, even though the check runs.
        heartbeat.fenceIfRenewalOverdue(t0 + 1_000);
        Mockito.verify(taskManager, Mockito.never()).terminate(Mockito.any());

        // No successful renewal for longer than the lease — in-flight work is terminated.
        heartbeat.fenceIfRenewalOverdue(t0 + leaseTimeoutMs + 1);
        Mockito.verify(taskManager).terminate(stroomTaskId);

        // A successful renewal resets the clock, so the fence stands down.
        assertThat(heartbeat.renew(t0 + leaseTimeoutMs + 2)).isEqualTo(1);
        heartbeat.fenceIfRenewalOverdue(t0 + leaseTimeoutMs + 3_000);
        Mockito.verify(taskManager, Mockito.times(1)).terminate(Mockito.any());

        // With nothing registered there is nothing to fence, however stale the clock.
        heartbeat.deregister(taskId);
        heartbeat.fenceIfRenewalOverdue(t0 + (10 * leaseTimeoutMs));
        Mockito.verify(taskManager, Mockito.times(1)).terminate(Mockito.any());
    }
}
