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

import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.Forwarder;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoFileScanner;
import stroom.proxy.repo.ProxyRepoSourceEntries;
import stroom.proxy.repo.ProxyRepoSources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
public class ProxyAggregationExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final ProxyRepo proxyRepo;
    private final ProxyRepoFileScanner proxyRepoFileScanner;
    private final Aggregator aggregator;
    private final Forwarder forwarder;

//    private volatile boolean firstRun = true;

    @Inject
    public ProxyAggregationExecutor(final ProxyRepo proxyRepo,
                                    final ProxyRepoFileScanner proxyRepoFileScanner,
                                    final ProxyRepoSources proxyRepoSources,
                                    final ProxyRepoSourceEntries proxyRepoSourceEntries,
                                    final Aggregator aggregator,
                                    final Forwarder forwarder,
                                    final Cleanup cleanup) {
        this.proxyRepo = proxyRepo;
        this.proxyRepoFileScanner = proxyRepoFileScanner;
        this.aggregator = aggregator;
        this.forwarder = forwarder;

        proxyRepoSources.addChangeListener(proxyRepoSourceEntries::examineSource);
        proxyRepoSourceEntries.addChangeListener(aggregator::aggregate);
        aggregator.addChangeListener(forwarder::forward);
        forwarder.addChangeListener(cleanup::cleanup);
    }

    public void exec() {
        if (!Thread.currentThread().isInterrupted()) {
            try {
//                // On first startup run aggregation and forwarding just to clean up and half finished forwarding jobs.
//                if (firstRun) {
//                    firstRun = false;
                aggregator.aggregate();
                forwarder.forward();
//                }

                // Scan the proxy repo to find new files to aggregate.
                proxyRepoFileScanner.scan();

                // Cleanup the repo to remove empty dirs and stale lock files.
                proxyRepo.clean(false);

            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
