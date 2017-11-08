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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.shared.FeedService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.statistic.server.MetaDataStatistic;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.TaskManager;
import stroom.util.config.PropertyUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.StroomZipRepository;
import stroom.util.zip.StroomZipRepositoryProcessor;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Component
@Scope(value = StroomScope.TASK)
public class ProxyAggregationExecutor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ProxyAggregationExecutor.class);

    private final StreamStore streamStore;
    private final MetaDataStatistic metaDataStatistic;
    private final TaskManager taskManager;
    private final FeedService feedService;
    private final TaskMonitor taskMonitor;
    private final ThreadLocalBuffer proxyAggregationThreadLocalBuffer;


    private Task<?> task;
    private String proxyDir;
    private boolean aggregate = true;
    private final int threadCount;
    private final int maxAggreagtion;
    private final String maxStreamSizeStr;
    private final int maxFileScan;

    private ProxyAggregationStroomZipRepositoryProcessor stroomZipRepositoryProcessor;

    @Inject
    public ProxyAggregationExecutor(final StreamStore streamStore,
                                    @Named("cachedFeedService") final FeedService feedService,
                                    final MetaDataStatistic metaDataStatistic,
                                    final TaskMonitor taskMonitor,
                                    final TaskManager taskManager,
                                    @Named("prototypeThreadLocalBuffer") final ThreadLocalBuffer proxyAggregationThreadLocalBuffer,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan) {
        this.feedService = feedService;
        this.taskMonitor = taskMonitor;
        this.taskManager = taskManager;
        this.metaDataStatistic = metaDataStatistic;
        this.streamStore = streamStore;
        this.proxyDir = proxyDir;
        this.threadCount = PropertyUtil.toInt(threadCount, 10);
        this.proxyAggregationThreadLocalBuffer = proxyAggregationThreadLocalBuffer;

        this.maxAggreagtion = (PropertyUtil.toInt(maxAggregation, StroomZipRepositoryProcessor.DEFAULT_MAX_AGGREGATION));
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

        this.stroomZipRepositoryProcessor = new ProxyAggregationStroomZipRepositoryProcessor(
                streamStore,
                metaDataStatistic,
                taskManager,
                taskMonitor,
                feedService,
                proxyAggregationThreadLocalBuffer,
                this.threadCount,
                task,
                aggregate);

        //TODO should be ctor args
        stroomZipRepositoryProcessor.setMaxAggregation(this.maxAggreagtion);
        stroomZipRepositoryProcessor.setMaxStreamSizeString(this.maxStreamSizeStr);
        stroomZipRepositoryProcessor.setMaxFileScan(this.maxFileScan);

        try {
            this.task = task;
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

            taskMonitor.addTerminateHandler(() -> stop());

            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.info("exec() - started");

            boolean complete = false;
            while (!complete && !taskMonitor.isTerminated()) {
                taskMonitor.info("Aggregate started %s, maxAggregation %s, maxAggregationScan %s, maxStreamSize %s",
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
        } finally {
            //dereference to ensure we don't hold on to any copies
            stroomZipRepositoryProcessor = null;
        }
    }

    public void setAggregate(final boolean aggregate) {
        this.aggregate = aggregate;
    }

    /**
     * Stops the task as soon as possible.
     */
    private void stop() {
        LOGGER.info("stop() - Proxy Aggregation - Stopping");
        if (stroomZipRepositoryProcessor != null) {
            stroomZipRepositoryProcessor.stopExecutor(true);
            stroomZipRepositoryProcessor.stop();
        }
    }

}
