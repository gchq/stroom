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

package stroom.core.receive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.repo.FileSetProcessor;
import stroom.proxy.repo.RepositoryProcessor;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
public class ProxyAggregationExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final RepositoryProcessor repositoryProcessor;

    @Inject
    ProxyAggregationExecutor(final TaskContext taskContext,
                             final ExecutorProvider executorProvider,
                             final Provider<FileSetProcessor> fileSetProcessorProvider,
                             final ProxyAggregationConfig proxyAggregationConfig) {
        this(
                taskContext,
                executorProvider,
                fileSetProcessorProvider,
                proxyAggregationConfig.getProxyDir(),
                proxyAggregationConfig.getProxyThreads(),
                proxyAggregationConfig.getMaxFilesPerAggregate(),
                proxyAggregationConfig.getMaxConcurrentMappedFiles(),
                proxyAggregationConfig.getMaxUncompressedFileSizeBytes()
        );
    }

    public ProxyAggregationExecutor(final TaskContext taskContext,
                             final ExecutorProvider executorProvider,
                             final Provider<FileSetProcessor> fileSetProcessorProvider,
                             final String proxyDir,
                             final int threadCount,
                             final int maxFilesPerAggregate,
                             final int maxConcurrentMappedFiles,
                             final long maxUncompressedFileSize) {
        this.repositoryProcessor = new RepositoryProcessor(taskContext,
                executorProvider,
                fileSetProcessorProvider,
                proxyDir,
                threadCount,
                maxFilesPerAggregate,
                maxConcurrentMappedFiles,
                maxUncompressedFileSize);
    }

    public void exec() {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                repositoryProcessor.process();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
