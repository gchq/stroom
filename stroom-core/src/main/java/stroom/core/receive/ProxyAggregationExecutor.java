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
import stroom.proxy.repo.ProxyRepoSourceEntries;
import stroom.proxy.repo.ProxyRepoSources;
import stroom.proxy.repo.SourceForwarder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Singleton
public class ProxyAggregationExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final AggregatorConfig aggregatorConfig;
    private final ProxyRepoFileScanner proxyRepoFileScanner;
    private final Aggregator aggregator;

    @Inject
    public ProxyAggregationExecutor(final ProxyRepoFileScanner proxyRepoFileScanner,
                                    final ProxyRepoSources proxyRepoSources,
                                    final ProxyRepoSourceEntries proxyRepoSourceEntries,
                                    final AggregatorConfig aggregatorConfig,
                                    final Aggregator aggregator,
                                    final AggregateForwarder aggregateForwarder,
                                    final SourceForwarder sourceForwarder,
                                    final Cleanup cleanup) {
        this.aggregatorConfig = aggregatorConfig;
        this.proxyRepoFileScanner = proxyRepoFileScanner;
        this.aggregator = aggregator;

        if (aggregatorConfig.isEnabled()) {
            // If we are aggregating then we need to tell the source entry service to examine new sources when they are
            // added.
            proxyRepoSources.addChangeListener(proxyRepoSourceEntries::examineSource);
            // When new source entries have been added tell teh aggregator that they are ready to be added to
            // aggregates.
            proxyRepoSourceEntries.addChangeListener(aggregator::aggregate);
            // When new aggregates are complete tell the forwarder that it can forward them.
            aggregator.addChangeListener(count -> aggregateForwarder.forward());
            // When we have finished forwarding some data tell the cleanup process it can delete DB entries and files
            // that are no longer needed.
            aggregateForwarder.addChangeListener(cleanup::cleanup);

        } else {
            // If we are not aggregating then just tell the forwarder directly when there is new source to forward.
            proxyRepoSources.addChangeListener((sourceId, sourcePath, feedName, typeName) -> sourceForwarder.forward());
            // When we have finished forwarding some data tell the cleanup process it can delete DB entries and files
            // that are no longer needed.
            sourceForwarder.addChangeListener(cleanup::cleanup);
        }
    }

    public void exec() {
        exec(false, false);
    }

    public void exec(final boolean forceAggregation,
                     final boolean scanSorted) {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                if (aggregatorConfig.isEnabled()) {
                    // Try aggregating again.
                    aggregator.aggregate();

                    // Scan the proxy repo to find new files to aggregate.
                    proxyRepoFileScanner.scan(scanSorted);

                    if (forceAggregation) {
                        // Force close of old aggregates.
                        aggregator.closeOldAggregates(System.currentTimeMillis());
                    }

                } else {
                    // Scan the proxy repo to find new files to aggregate.
                    proxyRepoFileScanner.scan(scanSorted);
                }

            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
