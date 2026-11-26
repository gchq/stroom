/*
 * Copyright 2026 Crown Copyright
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

package stroom.job.impl;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.job.api.DistributedTask;
import stroom.job.api.DistributedTaskFactory;
import stroom.job.shared.JobNode;
import stroom.node.api.NodeInfo;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.ThreadPoolImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests that a node gives back the tasks it has been given but won't run.
class TestDistributedTaskFetcherGiveBack {

    private static final String NODE = "node1";
    private static final String JOB = "job";

    private final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
    private final DistributedTaskFactory factory = Mockito.mock(DistributedTaskFactory.class);
    private final JobNodeTracker tracker = new JobNodeTracker(JobNode.builder().build());
    private final AtomicInteger runCount = new AtomicInteger();

    private DistributedTaskFetcher fetcher;

    @BeforeEach
    void beforeEach() {
        Mockito.when(executorProvider.get(Mockito.any())).thenReturn(Runnable::run);
        Mockito.when(factory.abandon(Mockito.any(), Mockito.any())).thenReturn(true);
        final JobNodeTrackers trackers = Mockito.mock(JobNodeTrackers.class);
        Mockito.when(trackers.getTrackerForJobName(JOB)).thenReturn(tracker);
        fetcher = new DistributedTaskFetcher(
                executorProvider,
                new SimpleTaskContextFactory(),
                () -> trackers,
                Mockito.mock(NodeInfo.class),
                new MockSecurityContext(),
                () -> java.util.Map.of(JOB, factory),
                Mockito.mock(TargetNodeSetFactory.class));
    }

    @Test
    void tasksAreRunAndNotGivenBack() {
        assertThat(fetcher.handleResult(NODE, JOB, factory, List.of(task(), task()))).isEqualTo(2);

        assertThat(runCount).hasValue(2);
        Mockito.verify(factory, Mockito.never()).abandon(Mockito.any(), Mockito.any());
        assertThat(tracker.getCurrentTaskCount()).isZero();
    }

    @Test
    void tasksGivenToANodeThatIsStoppingAreGivenBack() {
        fetcher.shutdown();
        final List<DistributedTask> tasks = List.of(task(), task());

        // Those given back aren't counted as running.
        assertThat(fetcher.handleResult(NODE, JOB, factory, tasks)).isZero();

        assertThat(runCount).hasValue(0);
        Mockito.verify(factory).abandon(NODE, tasks);
    }

    @Test
    void tasksThatCannotBeStartedAreGivenBack() {
        final DistributedTask started = task();
        final DistributedTask rejected = task();
        Mockito.when(executorProvider.get(Mockito.any()))
                .thenReturn(Runnable::run)
                .thenReturn(runnable -> {
                    throw new RejectedExecutionException("Expected");
                });

        assertThat(fetcher.handleResult(NODE, JOB, factory, List.of(started, rejected))).isOne();

        assertThat(runCount).hasValue(1);
        Mockito.verify(factory).abandon(NODE, List.of(rejected));
        assertThat(tracker.getCurrentTaskCount()).isZero();
    }

    @Test
    void tasksAreGivenBackIfTheJobHasGone() {
        final List<DistributedTask> tasks = List.of(task(), task());

        fetcher.handleResult(NODE, "other job", factory, tasks);

        assertThat(runCount).hasValue(0);
        Mockito.verify(factory).abandon(NODE, tasks);
    }

    @Test
    void failureToGiveBackIsNotPassedOn() {
        fetcher.shutdown();
        Mockito.when(factory.abandon(Mockito.any(), Mockito.any())).thenThrow(new RuntimeException("Expected"));

        fetcher.handleResult(NODE, JOB, factory, List.of(task()));

        assertThat(runCount).hasValue(0);
    }

    private DistributedTask task() {
        return new DistributedTask(JOB, runCount::incrementAndGet, new ThreadPoolImpl("pool"), "task");
    }
}
