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

import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.ProxyRepoFileScanner;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.SourceForwarder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Singleton
public class ProxyAggregationExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final Exec exec;

    @Inject
    public ProxyAggregationExecutor(final Provider<AggregatorConfig> aggregatorConfigProvider,
                                    final Provider<ProxyRepoFileScanner> proxyRepoFileScannerProvider,
                                    final Provider<RepoSourceItems> proxyRepoSourceEntriesProvider,
                                    final Provider<Aggregator> aggregatorProvider,
                                    final Provider<AggregateForwarder> aggregatorForwarderProvider,
                                    final Provider<SourceForwarder> sourceForwarderProvider,
                                    final Provider<Cleanup> cleanupProvider) {
        if (aggregatorConfigProvider.get().isEnabled()) {
            final ProxyRepoFileScanner proxyRepoFileScanner = proxyRepoFileScannerProvider.get();
            final RepoSourceItems repoSourceItems = proxyRepoSourceEntriesProvider.get();
            final Aggregator aggregator = aggregatorProvider.get();
            final AggregateForwarder aggregateForwarder = aggregatorForwarderProvider.get();
            final Cleanup cleanup = cleanupProvider.get();

            // We are going to do aggregate forwarding so reset source forwarder.
            cleanup.resetSourceForwarder();

            this.exec = (boolean forceAggregation, boolean scanSorted) -> {
                // Scan the proxy repo to find new files to aggregate.
                proxyRepoFileScanner.scan(scanSorted);

                // Examine all sources.
                repoSourceItems.examineAll();

                // Aggregate all of the examined source items.
                aggregator.aggregateAll();

                // Close old aggregates.
                if (forceAggregation) {
                    // Force close of old aggregates.
                    aggregator.closeOldAggregates(System.currentTimeMillis());
                } else {
                    aggregator.closeOldAggregates();
                }

                // Creating forward state tracking records.
                aggregateForwarder.createAllForwardRecords();

                // Forward.
                aggregateForwarder.forwardAll();

                // Cleanup
                cleanup.cleanupSources();
            };

        } else {
            final ProxyRepoFileScanner proxyRepoFileScanner = proxyRepoFileScannerProvider.get();
            final SourceForwarder sourceForwarder = sourceForwarderProvider.get();
            final Cleanup cleanup = cleanupProvider.get();

            // We are going to do source forwarding so reset aggregate forwarder.
            cleanup.resetAggregateForwarder();

            this.exec = (boolean forceAggregation, boolean scanSorted) -> {
                // Scan the proxy repo to find new files to aggregate.
                proxyRepoFileScanner.scan(scanSorted);

                // Creating forward state tracking records.
                sourceForwarder.createAllForwardRecords();

                // Forward.
                sourceForwarder.forwardAll();

                // Cleanup
                cleanup.cleanupSources();
            };
        }
    }

    public void exec() {
        exec(false, false);
    }

    public void exec(final boolean forceAggregation,
                     final boolean scanSorted) {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                exec.exec(forceAggregation, scanSorted);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private interface Exec {

        void exec(boolean forceAggregation, boolean scanSorted);
    }
}
