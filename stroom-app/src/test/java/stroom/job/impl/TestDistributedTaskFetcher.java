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

package stroom.job.impl;

import stroom.cluster.impl.MockClusterNodeManager;
import stroom.cluster.task.impl.TargetNodeSetFactoryImpl;
import stroom.job.api.DistributedTask;
import stroom.job.api.DistributedTaskFactory;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.node.api.NodeInfo;
import stroom.node.mock.MockNodeInfo;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SimpleMetrics;
import stroom.util.scheduler.FrequencyTrigger;
import stroom.util.scheduler.SimpleScheduleExec;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

class TestDistributedTaskFetcher extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDistributedTaskFetcher.class);

    @Disabled
    @Test
    void test() {
        try (final ExecutorService executors = Executors.newCachedThreadPool()) {
            final ExecutorProvider executorProvider = new ExecutorProvider() {
                @Override
                public Executor get(final ThreadPool threadPool) {
                    return executors;
                }

                @Override
                public Executor get() {
                    return executors;
                }
            };
            final TaskContextFactory taskContextFactory = new SimpleTaskContextFactory();

            final ThreadPool threadPool = new ThreadPoolImpl("MY_THREAD_POOL");
            final String jobName = "MY_DISTRIBUTED_JOB";
            final String nodeName = "NODE_NAME";
            final String frequency = "10s";
            final Job job = new Job(1, true, jobName, false);
            final JobNode jobNode =
                    new JobNode(1, nodeName, job, 100, JobType.DISTRIBUTED, frequency, true);
            final JobNodeTracker jobNodeTracker = new JobNodeTracker(jobNode);
            final SimpleScheduleExec scheduler = new SimpleScheduleExec(new FrequencyTrigger(frequency));
            final JobNodeTrackerCache jobNodeTrackerCache = () -> new JobNodeTrackers() {
                @Override
                public JobNodeTracker getTrackerForJobName(final String jobName1) {
                    return jobNodeTracker;
                }

                @Override
                public List<JobNodeTracker> getDistributedJobNodeTrackers() {
                    return List.of(jobNodeTracker);
                }

                @Override
                public SimpleScheduleExec getScheduleExec(final JobNode jobNode1) {
                    return scheduler;
                }

                @Override
                public void triggerImmediateExecution(final JobNode jobNode) {
                    throw new UnsupportedOperationException("Not expected to be called in this test");
                }
            };

            final NodeInfo nodeInfo = new MockNodeInfo();
            final AtomicLong executionCount = new AtomicLong();

            SimpleMetrics.setEnabled(true);

            final DistributedTaskFactory distributedTaskFactory = new DistributedTaskFactory() {
                @Override
                public List<DistributedTask> fetch(final String nodeName, final int count) {
                    if (count == 0) {
                        LOGGER.info("ZERO");
                    }

                    final List<DistributedTask> list = new ArrayList<>(count);
                    SimpleMetrics.measure("fetch", () -> {
                        for (int i = 0; i < count; i++) {
                            final Runnable runnable = () ->
                                    SimpleMetrics.measure("exec task", executionCount::incrementAndGet);
                            final DistributedTask distributedTask =
                                    new DistributedTask(jobName, runnable, threadPool, "test");
                            list.add(distributedTask);
                        }
                    });
                    return list;
                }

                @Override
                public Boolean abandon(final String nodeName, final List<DistributedTask> tasks) {
                    return Boolean.TRUE;
                }
            };
            final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry = () ->
                    Map.of(jobName, distributedTaskFactory);

            final DistributedTaskFetcher distributedTaskFetcher =
                    new DistributedTaskFetcher(
                            executorProvider,
                            taskContextFactory,
                            jobNodeTrackerCache,
                            nodeInfo,
                            new MockSecurityContext(),
                            distributedTaskFactoryRegistry,
                            new TargetNodeSetFactoryImpl(nodeInfo, new MockClusterNodeManager(nodeInfo)));
            distributedTaskFetcher.execute();

            while (true) {
                SimpleMetrics.report();
                ThreadUtil.sleep(1000);
            }
        }
    }
}
