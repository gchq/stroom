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

import stroom.proxy.repo.FileSetProcessor;
import stroom.proxy.repo.RepositoryProcessor;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.PathCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final PathCreator pathCreator;

    @Inject
    ProxyAggregationExecutor(final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory,
                             final Provider<FileSetProcessor> fileSetProcessorProvider,
                             final ProxyAggregationConfig proxyAggregationConfig,
                             final PathCreator pathCreator) {
        this(
                executorProvider,
                taskContextFactory,
                fileSetProcessorProvider,
                pathCreator,
                proxyAggregationConfig.getProxyDir(),
                proxyAggregationConfig.getProxyThreads(),
                proxyAggregationConfig.getMaxFileScan(),
                proxyAggregationConfig.getMaxConcurrentMappedFiles(),
                proxyAggregationConfig.getMaxFilesPerAggregate(),
                proxyAggregationConfig.getMaxUncompressedFileSizeBytes()
        );
    }


    public ProxyAggregationExecutor(final ExecutorProvider executorProvider,
                                    final TaskContextFactory taskContextFactory,
                                    final Provider<FileSetProcessor> fileSetProcessorProvider,
                                    final PathCreator pathCreator,
                                    final String proxyDir,
                                    final int threadCount,
                                    final int maxFileScan,
                                    final int maxConcurrentMappedFiles,
                                    final int maxFilesPerAggregate,
                                    final long maxUncompressedFileSize) {

        this.pathCreator = pathCreator;
        this.repositoryProcessor = new RepositoryProcessor(
                executorProvider,
                taskContextFactory,
                fileSetProcessorProvider,
                getAbsoluteProxyDir(proxyDir),
                threadCount,
                maxFileScan,
                maxConcurrentMappedFiles,
                maxFilesPerAggregate,
                maxUncompressedFileSize);
    }

    private String getAbsoluteProxyDir(final String proxyDir) {
        if (proxyDir == null) {
            return null;
        } else {
            // handle any things like ${stroom.home} or turn relative paths into absolute
            // paths based on the stroom home location.
            // This dir is normally owned by stroom-proxy as it writes to it and we
            // consume from it
            // It could be theoretically be something like
            // ../../stroom-proxy/stroom-proxy-vX.Y.Z/repo
            String absoluteProxyDir = pathCreator.replaceSystemProperties(proxyDir);
            absoluteProxyDir = pathCreator.makeAbsolute(absoluteProxyDir);
            return absoluteProxyDir;
        }
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
