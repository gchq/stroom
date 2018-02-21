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

package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.proxy.repo.RepositoryProcessor;
import stroom.proxy.repo.StroomZipRepository;
import stroom.task.ExecutorProvider;
import stroom.task.ThreadPoolImpl;
import stroom.util.config.PropertyUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.concurrent.Executor;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
class ProxyAggregationExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final static int DEFAULT_MAX_AGGREGATION = 10000;

    private final TaskMonitor taskMonitor;
    private final String proxyDir;
    private final int maxAggregation;
    private final ProxyFileProcessorImpl proxyFileProcessor;
    private final Executor executor;
    private final RepositoryProcessor repositoryProcessor;
    private final long maxStreamSize;

    @Inject
    ProxyAggregationExecutor(final ProxyFileProcessorImpl proxyFileProcessor,
                             final TaskMonitor taskMonitor,
                             final ExecutorProvider executorProvider,
                             @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                             @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize) {
        this(
                proxyFileProcessor,
                taskMonitor,
                executorProvider,
                proxyDir,
                PropertyUtil.toInt(threadCount, 10),
                PropertyUtil.toInt(maxAggregation, DEFAULT_MAX_AGGREGATION),
                PropertyUtil.toInt(maxFileScan, RepositoryProcessor.DEFAULT_MAX_FILE_SCAN),
                ProxyFileProcessorImpl.getByteSize(maxStreamSize, ProxyFileProcessorImpl.DEFAULT_MAX_STREAM_SIZE)
        );
    }

    ProxyAggregationExecutor(final ProxyFileProcessorImpl proxyFileProcessor,
                             final TaskMonitor taskMonitor,
                             final ExecutorProvider executorProvider,
                             final String proxyDir,
                             final int threadCount,
                             final int maxAggregation,
                             final int maxFileScan,
                             final long maxStreamSize) {
        this.proxyFileProcessor = proxyFileProcessor;
        this.taskMonitor = taskMonitor;
        this.proxyDir = proxyDir;
        this.maxAggregation = maxAggregation;
        this.maxStreamSize = maxStreamSize;

        final ThreadPool threadPool = new ThreadPoolImpl("Proxy Aggregation", 5, 0, threadCount);

        this.executor = executorProvider.getExecutor(threadPool);
        repositoryProcessor = new RepositoryProcessor(proxyFileProcessor, executor, taskMonitor);
        repositoryProcessor.setMaxFileScan(maxFileScan);
    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "Proxy Aggregation", advanced = false, description = "Job to pick up the data written by the proxy and store it in Stroom")
    public void exec(final Task<?> task) {
        try {
            taskMonitor.addTerminateHandler(this::stop);

            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.info("exec() - started");

            boolean complete = false;
            while (!complete && !taskMonitor.isTerminated()) {
                taskMonitor.info("Aggregate started {}, maxAggregation {}, maxAggregationScan {}, maxStreamSize {}",
                        DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                        ModelStringUtil.formatCsv(maxAggregation),
                        ModelStringUtil.formatCsv(repositoryProcessor.getMaxFileScan()),
                        ModelStringUtil.formatIECByteSizeString(maxStreamSize));

                final StroomZipRepository stroomZipRepository = new StroomZipRepository(this.proxyDir);
                complete = repositoryProcessor.process(stroomZipRepository);
            }

            LOGGER.info("exec() - completed in {}", logExecutionTime);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }


//    private void startExecutor() {
//        taskPool = new AsyncTaskHelper<>(null, taskMonitor, taskManager, threadCount);
//    }
//
//    private void stopExecutor(final boolean now) {
//        if (taskPool != null) {
//            if (now) {
//                taskMonitor.terminate();
//                taskPool.clear();
//            }
//            taskPool.join();
//        }
//    }
//
//    private void waitForComplete() {
//        taskPool.join();
//    }
//
//    private void execute(final String message, final Runnable runnable) {
//        if (!stop) {
//            final GenericServerTask genericServerTask = GenericServerTask.create(task, task.getTaskName(), message);
//            genericServerTask.setRunnable(runnable);
//            taskPool.fork(genericServerTask);
//        }
//    }


    /**
     * Stops the task as soon as possible.
     */
    private void stop() {
        LOGGER.info("stop() - Proxy Aggregation - Stopping");
        proxyFileProcessor.stop();
    }
}
