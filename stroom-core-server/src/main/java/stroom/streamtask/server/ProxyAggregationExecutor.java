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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.shared.FeedService;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.proxy.repo.StroomZipRepository;
import stroom.proxy.repo.StroomZipRepositoryProcessor;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.config.PropertyUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Component
@Scope(value = StroomScope.TASK)
public class ProxyAggregationExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final StreamStore streamStore;
    private final MetaDataStatistic metaDataStatistic;
    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final FeedService feedService;

    private String proxyDir;
    private boolean aggregate = true;
    private final int threadCount;
    private final int maxAggregation;
    private final String maxStreamSizeStr;
    private final int maxFileScan;

    @Inject
    public ProxyAggregationExecutor(final StreamStore streamStore,
                                    @Named("cachedFeedService") final FeedService feedService,
                                    final MetaDataStatistic metaDataStatistic,
                                    final TaskContext taskContext,
                                    final ExecutorProvider executorProvider,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan) {
        this.feedService = feedService;
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.metaDataStatistic = metaDataStatistic;
        this.streamStore = streamStore;
        this.proxyDir = proxyDir;
        this.threadCount = PropertyUtil.toInt(threadCount, 10);

        this.maxAggregation = (PropertyUtil.toInt(maxAggregation, StroomZipRepositoryProcessor.DEFAULT_MAX_AGGREGATION));
        this.maxStreamSizeStr = maxStreamSize;
        this.maxFileScan = PropertyUtil.toInt(maxFileScan, StroomZipRepositoryProcessor.DEFAULT_MAX_FILE_SCAN);

        LOGGER.debug("ProxyAggregationExecutor() - new instance");

    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "Proxy Aggregation", advanced = false, description = "Job to pick up the data written by the proxy and store it in Stroom")
    public void exec(final Task<?> task) {
        aggregate(task, proxyDir);
    }

    public void aggregate(final Task<?> task, final String proxyDir) {
        aggregate(task, proxyDir, aggregate, null, null);
    }

    public void aggregate(final Task<?> task, final String proxyDir, final Boolean aggregate,
                          final Integer maxAggregation, final Long maxStreamSize) {

        Executor executor = getExecutor(task);

        ProxyAggregationStroomZipRepositoryProcessor stroomZipRepositoryProcessor = new ProxyAggregationStroomZipRepositoryProcessor(
                streamStore,
                metaDataStatistic,
                executor,
                feedService,
                taskContext,
                aggregate);

        //TODO should be ctor args
        stroomZipRepositoryProcessor.setMaxAggregation(this.maxAggregation);
        stroomZipRepositoryProcessor.setMaxStreamSizeString(this.maxStreamSizeStr);
        stroomZipRepositoryProcessor.setMaxFileScan(this.maxFileScan);

        try {
            if (proxyDir != null) {
                this.proxyDir = proxyDir;
            }
            this.aggregate = aggregate;
            if (maxAggregation != null) {
                stroomZipRepositoryProcessor.setMaxAggregation(maxAggregation);
            }
            if (maxStreamSize != null) {
                stroomZipRepositoryProcessor.setMaxStreamSize(maxStreamSize);
            }

            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.info("exec() - started");

            boolean complete = false;
            while (!complete && !taskContext.isTerminated()) {
                taskContext.info("Aggregate started %s, maxAggregation %s, maxAggregationScan %s, maxStreamSize %s",
                        DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                        ModelStringUtil.formatCsv(stroomZipRepositoryProcessor.getMaxAggregation()),
                        ModelStringUtil.formatCsv(stroomZipRepositoryProcessor.getMaxFileScan()),
                        ModelStringUtil.formatIECByteSizeString(stroomZipRepositoryProcessor.getMaxStreamSize()));

                final StroomZipRepository stroomZipRepository = new StroomZipRepository(this.proxyDir);
                complete = stroomZipRepositoryProcessor.process(stroomZipRepository);
            }

            LOGGER.info("exec() - completed in %s", logExecutionTime);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private Executor getExecutor(Task<?> task) {
        int priority = Optional.ofNullable(task)
                .flatMap(t -> Optional.ofNullable(t.getThreadPool()))
                .map(ThreadPool::getPriority)
                .orElse(3);

        final ThreadPool threadPool = new ThreadPoolImpl("Proxy Aggregation",
                priority,
                threadCount,
                threadCount);
        return executorProvider.getExecutor(threadPool);
    }

    public void setAggregate(final boolean aggregate) {
        this.aggregate = aggregate;
    }
}
