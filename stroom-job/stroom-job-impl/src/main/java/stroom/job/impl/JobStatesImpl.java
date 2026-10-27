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

import stroom.job.api.JobStates;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Reads job state for [JobStates], as the user, changing nothing.
@Singleton
class JobStatesImpl implements JobStates {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobStatesImpl.class);

    private final Provider<JobNodeService> jobNodeServiceProvider;
    // Its info asks a node itself, or calls the node that is asked about. Bound as a REST resource, not by its
    // interface.
    private final Provider<JobNodeResourceImpl> jobNodeResourceProvider;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    JobStatesImpl(final Provider<JobNodeService> jobNodeServiceProvider,
                  final Provider<JobNodeResourceImpl> jobNodeResourceProvider,
                  final TaskContextFactory taskContextFactory,
                  final ExecutorProvider executorProvider) {
        this.jobNodeServiceProvider = jobNodeServiceProvider;
        this.jobNodeResourceProvider = jobNodeResourceProvider;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get();
    }

    @Override
    public Optional<JobState> getJob(final String jobName, final Duration timeout) {
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.getJobName().setString(jobName);
        // Needs the Manage Jobs permission, as the Jobs screen does.
        final List<JobNode> jobNodes = NullSafe.list(jobNodeServiceProvider.get().find(criteria).getValues())
                .stream()
                // The criteria may match a pattern: only this job.
                .filter(jobNode -> jobName.equals(jobNode.getJobName()))
                .toList();
        if (jobNodes.isEmpty()) {
            return Optional.empty();
        }

        final TaskContext parent = taskContextFactory.current();
        // A list, not a map: job nodes are equal by id, which unsaved ones share.
        final List<Map.Entry<JobNode, CompletableFuture<JobNodeInfo>>> asked = new ArrayList<>();
        for (final JobNode jobNode : jobNodes) {
            // Each node asked in its own child task, so the call is made as this user and the nodes are asked at
            // once: one slow node costs the timeout, not the whole answer.
            asked.add(Map.entry(jobNode, CompletableFuture.supplyAsync(taskContextFactory.childContextResult(parent,
                    "Asking node " + jobNode.getNodeName() + " about job " + jobName,
                    taskContext -> jobNodeResourceProvider.get().info(jobName, jobNode.getNodeName())), executor)));
        }

        final long deadline = System.nanoTime() + timeout.toNanos();
        final List<JobNodeState> nodes = new ArrayList<>();
        for (final Map.Entry<JobNode, CompletableFuture<JobNodeInfo>> entry : asked) {
            nodes.add(state(entry.getKey(), entry.getValue(), deadline, timeout));
        }
        final boolean enabled = jobNodes.getFirst().getJob() != null && jobNodes.getFirst().getJob().isEnabled();
        return Optional.of(new JobState(jobName, enabled, nodes));
    }

    // A job node's settings with what its node says now, or why it didn't say by the deadline.
    private static JobNodeState state(final JobNode jobNode,
                                      final CompletableFuture<JobNodeInfo> future,
                                      final long deadline,
                                      final Duration timeout) {
        JobNodeInfo info = null;
        String error = null;
        try {
            final long remaining = Math.max(0, deadline - System.nanoTime());
            info = future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (final TimeoutException e) {
            future.cancel(true);
            error = "no answer within " + timeout.toMillis() + " ms";
        } catch (final ExecutionException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to get job info from node {}", jobNode.getNodeName()), e);
            error = NullSafe.nonBlankStringElse(NullSafe.get(e.getCause(), Throwable::getMessage),
                    String.valueOf(e.getCause()));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "interrupted";
        }
        final JobType type = jobNode.getJobType();
        return new JobNodeState(
                jobNode.getNodeName(),
                jobNode.isEnabled(),
                NullSafe.get(type, JobType::getDisplayValue),
                JobType.DISTRIBUTED.equals(type)
                        ? null
                        : jobNode.getSchedule(),
                jobNode.getTaskLimit(),
                NullSafe.get(info, JobNodeInfo::getCurrentTaskCount),
                NullSafe.get(info, JobNodeInfo::getLastExecutedTime),
                NullSafe.get(info, JobNodeInfo::getNextScheduledTime),
                error);
    }
}
