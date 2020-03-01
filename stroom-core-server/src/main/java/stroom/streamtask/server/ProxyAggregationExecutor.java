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

package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.proxy.repo.FileSetProcessor;
import stroom.proxy.repo.RepositoryProcessor;
import stroom.util.concurrent.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.util.config.PropertyUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskWrapper;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Component
@Scope(value = StroomScope.PROTOTYPE)
public class ProxyAggregationExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private static final int DEFAULT_MAX_FILE_SCAN = 100000;
    private static final int DEFAULT_MAX_CONCURRENT_MAPPED_FILES = 100000;
    private static final int DEFAULT_MAX_FILES_PER_AGGREGATE = 10000;
    private static final long DEFAULT_MAX_UNCOMPRESSED_FILE_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private final TaskContext taskContext;
    private final RepositoryProcessor repositoryProcessor;

    @Inject
    ProxyAggregationExecutor(final TaskContext taskContext,
                             final ExecutorProvider executorProvider,
                             final Provider<TaskWrapper> taskWrapperProvider,
                             final Provider<FileSetProcessor> fileSetProcessorProvider,
                             @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                             @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxFileScan')}") final String maxFileScan,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxConcurrentMappedFiles')}") final String maxConcurrentMappedFiles,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxFilesPerAggregate,
                             @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxUncompressedFileSize) {
        this(
                taskContext,
                executorProvider,
                taskWrapperProvider,
                fileSetProcessorProvider,
                proxyDir,
                PropertyUtil.toInt(threadCount, 10),
                PropertyUtil.toInt(maxFileScan, DEFAULT_MAX_FILE_SCAN),
                PropertyUtil.toInt(maxConcurrentMappedFiles, DEFAULT_MAX_CONCURRENT_MAPPED_FILES),
                PropertyUtil.toInt(maxFilesPerAggregate, DEFAULT_MAX_FILES_PER_AGGREGATE),
                getByteSize(maxUncompressedFileSize, DEFAULT_MAX_UNCOMPRESSED_FILE_SIZE)
        );
    }

    ProxyAggregationExecutor(final TaskContext taskContext,
                             final ExecutorProvider executorProvider,
                             final Provider<TaskWrapper> taskWrapperProvider,
                             final Provider<FileSetProcessor> fileSetProcessorProvider,
                             final String proxyDir,
                             final int threadCount,
                             final int maxFileScan,
                             final int maxConcurrentMappedFiles,
                             final int maxFilesPerAggregate,
                             final long maxUncompressedFileSize) {
        this.taskContext = taskContext;
        this.repositoryProcessor = new RepositoryProcessor(taskContext,
                executorProvider,
                taskWrapperProvider,
                fileSetProcessorProvider,
                proxyDir,
                threadCount,
                maxFileScan,
                maxConcurrentMappedFiles,
                maxFilesPerAggregate,
                maxUncompressedFileSize);
    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "Proxy Aggregation", advanced = false, description = "Job to pick up the data written by the proxy and store it in Stroom")
    public void exec(final Task<?> task) {
        if (!taskContext.isTerminated()) {
            try {
                repositoryProcessor.process();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private static long getByteSize(final String propertyValue, final long defaultValue) {
        Long value = null;
        try {
            value = ModelStringUtil.parseIECByteSizeString(propertyValue);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (value == null) {
            value = defaultValue;
        }

        return value;
    }
}
