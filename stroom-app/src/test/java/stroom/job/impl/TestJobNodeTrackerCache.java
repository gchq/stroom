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

import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeListResponse;
import stroom.node.api.NodeInfo;
import stroom.node.mock.MockNodeInfo;
import stroom.task.api.ExecutorProvider;
import stroom.task.shared.ThreadPool;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SimpleMetrics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestJobNodeTrackerCache extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestJobNodeTrackerCache.class);

    @Mock
    private JobNodeDao jobNodeDao;

    @Test
    void testQuick() throws InterruptedException {
        test(0, 1, TimeUnit.SECONDS);
    }

    @Test
    void testDelayed() throws InterruptedException {
        test(5000, 1, TimeUnit.SECONDS);
    }

    @Disabled
    @Test
    void testQuickPerformance() throws InterruptedException {
        test(0, 40, TimeUnit.SECONDS);
    }

    @Disabled
    @Test
    void testDelayedPerformance() throws InterruptedException {
        test(5000, 40, TimeUnit.SECONDS);
    }

    void test(final long delay, final long totalTime, final TimeUnit timeUnit) throws InterruptedException {
        final AtomicLong calls = new AtomicLong();
        try (final ScheduledExecutorService executors =
                Executors.newScheduledThreadPool(4)) {
            final String jobName = "MY_DISTRIBUTED_JOB";
            final String nodeName = "NODE_NAME";
            final String frequency = "10s";
            final Job job = new Job(1, true, jobName, false);
            final JobNode jobNode =
                    new JobNode(1, nodeName, job, 100, JobType.DISTRIBUTED, frequency, true);
            final JobNodeListResponse jobNodeListResponse = JobNodeListResponse
                    .createUnboundedJobNodeResponse(List.of(jobNode));

            SimpleMetrics.setEnabled(true);

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
            final NodeInfo nodeInfo = new MockNodeInfo();
            final JobNodeTrackerCacheImpl jobNodeTrackerCache =
                    new JobNodeTrackerCacheImpl(nodeInfo, jobNodeDao, executorProvider);

            // Bootstrap cache.
            when(jobNodeDao.find(Mockito.any(FindJobNodeCriteria.class)))
                    .then((Answer<JobNodeListResponse>) invocation -> jobNodeListResponse);
            jobNodeTrackerCache.getTrackers();

            when(jobNodeDao.find(Mockito.any(FindJobNodeCriteria.class)))
                    .then((Answer<JobNodeListResponse>) invocation -> {
                        SimpleMetrics.measure("find", () -> {
                            // Add delay.
                            ThreadUtil.sleep(delay);
                        });
                        return jobNodeListResponse;
                    });

            final AtomicBoolean running = new AtomicBoolean(true);
            executors.execute(() -> {
                while (running.get()) {
                    SimpleMetrics.measure("getTrackers", () -> {
                        jobNodeTrackerCache.getTrackers();
                        calls.incrementAndGet();
                    });
                }
            });

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            executors.schedule(() -> {
                running.set(false);
                countDownLatch.countDown();
            }, totalTime, timeUnit);

            countDownLatch.await();
        }

        SimpleMetrics.report();

        LOGGER.info(String.valueOf(calls.get()));
    }
}
