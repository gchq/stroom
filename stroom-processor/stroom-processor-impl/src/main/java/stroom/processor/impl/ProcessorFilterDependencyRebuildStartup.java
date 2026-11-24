/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docstore.api.DocDependencyService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/// Rebuilds the stored dependency edges of every processor filter whose edges were not written by the
/// current dependency extraction logic, i.e. whose `processor_filter.dependency_version` is null or older
/// than [ProcessorFilterDependencies#CURRENT_DEPENDENCY_VERSION]. This is the processor filter equivalent
/// of the document dependency rebuild, as processor filters are not documents.
///
/// A live filter has its edges extracted exactly as a save would extract them. A logically deleted
/// filter, or one whose processor is deleted, has its edges removed. If any filters were rebuilt, edges
/// left behind by filters that no longer exist at all are also removed.
///
/// Every node runs this at startup, but only the node that gets the cluster lock does the work. The
/// others skip it. If the node doing the work stops part way, the filters it finished are already
/// stamped and the rest are rebuilt on the next start.
public class ProcessorFilterDependencyRebuildStartup implements Runnable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            ProcessorFilterDependencyRebuildStartup.class);

    static final String LOCK_NAME = "processor-filter-dependency-rebuild";
    // A filter that keeps changing while we rebuild it is left for the next start
    static final int MAX_ATTEMPTS = 3;
    static final int BATCH_SIZE = 1_000;

    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final ProcessorFilterDao processorFilterDao;
    private final ProcessorFilterDependencies processorFilterDependencies;
    private final Provider<DocDependencyService> docDependencyServiceProvider;

    @Inject
    ProcessorFilterDependencyRebuildStartup(final ClusterLockService clusterLockService,
                                            final SecurityContext securityContext,
                                            final ProcessorFilterDao processorFilterDao,
                                            final ProcessorFilterDependencies processorFilterDependencies,
                                            final Provider<DocDependencyService> docDependencyServiceProvider) {
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.processorFilterDao = processorFilterDao;
        this.processorFilterDependencies = processorFilterDependencies;
        this.docDependencyServiceProvider = docDependencyServiceProvider;
    }

    @Override
    public void run() {
        final AtomicBoolean gotLock = new AtomicBoolean(false);
        securityContext.asProcessingUser(() ->
                clusterLockService.tryLock(LOCK_NAME, () -> {
                    gotLock.set(true);
                    rebuild();
                }));
        if (!gotLock.get()) {
            LOGGER.info("Processor filter dependency rebuild skipped as another node holds lock '{}'", LOCK_NAME);
        }
    }

    private void rebuild() {
        final DurationTimer timer = DurationTimer.start();
        int processedCount = 0;
        int rebuiltCount = 0;
        int notRebuiltCount = 0;
        int errorCount = 0;

        // Work in batches so a large number of filters is never held in memory at once. Filters that are
        // rebuilt are no longer stale, but the batches are found by ID so that filters that could not be
        // rebuilt are not found again.
        int afterId = 0;
        List<ProcessorFilter> batch;
        do {
            batch = processorFilterDao.findWithStaleDependencies(
                    ProcessorFilterDependencies.CURRENT_DEPENDENCY_VERSION, afterId, BATCH_SIZE);
            if (processedCount == 0 && !batch.isEmpty()) {
                LOGGER.info("Rebuilding processor filter dependencies");
            }
            for (final ProcessorFilter staleFilter : batch) {
                try {
                    if (rebuildWithRetry(staleFilter)) {
                        rebuiltCount++;
                    } else {
                        notRebuiltCount++;
                    }
                } catch (final RuntimeException e) {
                    errorCount++;
                    LOGGER.error(() -> LogUtil.message("Error rebuilding dependencies for processor filter {}: {}",
                            staleFilter.getUuid(), e.getMessage()), e);
                }
                processedCount++;
                afterId = staleFilter.getId();
            }
            if (batch.size() == BATCH_SIZE) {
                LOGGER.info("Rebuilt dependencies for {} processor filters so far", processedCount);
            }
        } while (batch.size() == BATCH_SIZE);

        if (processedCount == 0) {
            LOGGER.debug("No processor filter dependencies need rebuilding");
            return;
        }

        // Edges from filters that no longer exist were left by the code that predates the rebuild, which
        // also left every filter stale, so we only need to look for them when there was something to rebuild
        final int orphanCount = removeOrphanedDependencies();

        docDependencyServiceProvider.get().invalidateBrokenDependencyCache();

        LOGGER.info("Rebuilt dependencies for {} processor filters in {} (rebuilt: {}, " +
                    "changed too often to rebuild: {}, errors: {}, removed for filters that no longer exist: {})",
                processedCount, timer, rebuiltCount, notRebuiltCount, errorCount, orphanCount);
    }

    /// Rebuild one filter, re-reading it if it changes while we work on it.
    ///
    /// The save that changed the filter wrote the right edges, but ours may have been written after them,
    /// so we go again with the current filter rather than leave possibly stale edges.
    ///
    /// @return `true` if the filter's edges are current, `false` if it kept changing.
    private boolean rebuildWithRetry(final ProcessorFilter staleFilter) {
        ProcessorFilter filter = staleFilter;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (rebuild(filter)) {
                return true;
            }
            final Optional<ProcessorFilter> optFilter = processorFilterDao.fetch(filter.getId());
            if (optFilter.isEmpty()) {
                // Physically deleted since we read it
                processorFilterDependencies.remove(filter.getUuid());
                return true;
            }
            filter = optFilter.get();
        }
        LOGGER.warn("Processor filter {} changed {} times while rebuilding its dependencies. " +
                    "It will be rebuilt on the next start.", staleFilter.getUuid(), MAX_ATTEMPTS);
        return false;
    }

    private boolean rebuild(final ProcessorFilter filter) {
        return isDeleted(filter)
                ? processorFilterDependencies.removeForDeleted(filter)
                : processorFilterDependencies.update(filter);
    }

    private static boolean isDeleted(final ProcessorFilter filter) {
        return filter.isDeleted()
               || (filter.getProcessor() != null && filter.getProcessor().isDeleted());
    }

    /// Remove the edges of any processor filter that no longer has a row, e.g. because it was logically
    /// deleted without its edges being removed and has since been physically deleted.
    ///
    /// @return The number of filters whose edges were removed.
    private int removeOrphanedDependencies() {
        final Set<String> sourceUuids = docDependencyServiceProvider.get()
                .getSourceUuids(ProcessorFilter.ENTITY_TYPE);
        final Set<String> orphanedUuids = new HashSet<>(sourceUuids);
        orphanedUuids.removeAll(processorFilterDao.fetchExistingUuids(sourceUuids));
        processorFilterDependencies.remove(orphanedUuids);
        return orphanedUuids.size();
    }
}
