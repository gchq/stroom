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

import stroom.job.api.JobStates.JobNodeState;
import stroom.job.api.JobStates.JobState;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class TestJobStatesImpl {

    private static final Job DATA_PROCESSOR = Job.builder().name("Data Processor").enabled(true).build();

    private final JobNodeService jobNodeService = Mockito.mock(JobNodeService.class);
    private final JobNodeResourceImpl jobNodeResource = Mockito.mock(JobNodeResourceImpl.class);
    private final TaskContextFactory taskContextFactory = Mockito.mock(TaskContextFactory.class);
    private final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);

    @SuppressWarnings("unchecked")
    TestJobStatesImpl() {
        // Child tasks run their function as given, as the real factory does inside a task context.
        Mockito.when(taskContextFactory.childContextResult(any(), anyString(), any())).thenAnswer(invocation -> {
            final Function<TaskContext, Object> function = invocation.getArgument(2);
            return (java.util.function.Supplier<Object>) () -> function.apply(Mockito.mock(TaskContext.class));
        });
        final Executor executor = Executors.newCachedThreadPool();
        Mockito.when(executorProvider.get()).thenReturn(executor);
    }

    @Test
    void eachNodesSettingsComeWithWhatTheNodeSaysNow() {
        nodes(node("node1", true, 20), node("node2", false, 5));
        Mockito.when(jobNodeResource.info("Data Processor", "node1")).thenReturn(new JobNodeInfo(3, null, 1_000L,
                2_000L));
        Mockito.when(jobNodeResource.info("Data Processor", "node2")).thenReturn(new JobNodeInfo(0, null, null,
                null));

        final JobState job = states().getJob("Data Processor", Duration.ofSeconds(5)).orElseThrow();

        assertThat(job.enabled()).isTrue();
        assertThat(job.nodes()).containsExactly(
                new JobNodeState("node1", true, "Distributed", null, 20, 3, 1_000L, 2_000L, null),
                new JobNodeState("node2", false, "Distributed", null, 5, 0, null, null, null));
    }

    @Test
    void aNodeThatCantBeAskedIsReportedWithWhy() {
        nodes(node("node1", true, 20));
        Mockito.when(jobNodeResource.info(eq("Data Processor"), eq("node1")))
                .thenThrow(new RuntimeException("Unable to contact node \"node1\""));

        final JobNodeState node = states().getJob("Data Processor", Duration.ofSeconds(5)).orElseThrow()
                .nodes().getFirst();

        assertThat(node.error()).isEqualTo("Unable to contact node \"node1\"");
        assertThat(node.currentTaskCount()).isNull();
        assertThat(node.taskLimit()).as("its settings still given").isEqualTo(20);
    }

    @Test
    void aNodeThatDoesntAnswerInTimeCostsOnlyTheTimeout() {
        nodes(node("node1", true, 20));
        Mockito.when(jobNodeResource.info(eq("Data Processor"), eq("node1"))).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return new JobNodeInfo(1, null, null, null);
        });

        final long start = System.nanoTime();
        final JobNodeState node = states().getJob("Data Processor", Duration.ofMillis(200)).orElseThrow()
                .nodes().getFirst();

        assertThat(node.error()).isEqualTo("no answer within 200 ms");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void aJobThatDoesntExistIsEmptyAndOnlyThatJobCounts() {
        nodes(JobNode.builder().job(Job.builder().name("Data Processor Old").enabled(true).build())
                .jobType(JobType.DISTRIBUTED).nodeName("node1").taskLimit(1).enabled(true).build());

        assertThat(states().getJob("Data Processor", Duration.ofSeconds(1))).isEqualTo(Optional.empty());
    }

    @Test
    void itNeedsManageJobsAsTheJobsScreenDoes() {
        Mockito.when(jobNodeService.find(any(FindJobNodeCriteria.class)))
                .thenThrow(new PermissionException(null, "Manage Jobs"));

        assertThatThrownBy(() -> states().getJob("Data Processor", Duration.ofSeconds(1)))
                .isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(jobNodeResource);
    }

    private JobStatesImpl states() {
        return new JobStatesImpl(() -> jobNodeService, () -> jobNodeResource, taskContextFactory, executorProvider);
    }

    private void nodes(final JobNode... nodes) {
        Mockito.when(jobNodeService.find(any(FindJobNodeCriteria.class)))
                .thenReturn(JobNodeListResponse.createUnboundedJobNodeResponse(List.of(nodes)));
    }

    private static JobNode node(final String name, final boolean enabled, final int taskLimit) {
        return JobNode.builder()
                .job(DATA_PROCESSOR)
                .jobType(JobType.DISTRIBUTED)
                .nodeName(name)
                .taskLimit(taskLimit)
                .enabled(enabled)
                .build();
    }
}
