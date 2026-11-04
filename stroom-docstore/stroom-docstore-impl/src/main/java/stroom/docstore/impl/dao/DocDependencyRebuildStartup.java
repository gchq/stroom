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

package stroom.docstore.impl.dao;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyProvider;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.impl.dao.DocDependencyDao.DocVersion;
import stroom.security.api.SecurityContext;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Rebuilds the stored dependency edges of every document whose edges were not written by the current
/// dependency extraction logic, i.e. whose `doc.dependency_version` is null or older than
/// [DocDependencyDao#CURRENT_DEPENDENCY_VERSION].
///
/// Each document's dependencies are extracted by its own store, exactly as a save would extract them.
/// The edges are written with [DocDependencyDao#setDependenciesIfUnchanged], so a document saved while
/// the rebuild is running keeps the edges its save wrote.
///
/// Every node runs this at startup, but only the node that gets the cluster lock does the work. The
/// others skip it. If the node doing the work stops part way, the documents it finished are already
/// stamped and the rest are rebuilt on the next start.
public class DocDependencyRebuildStartup implements Runnable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocDependencyRebuildStartup.class);

    static final String LOCK_NAME = "doc-dependency-rebuild";
    private static final int PROGRESS_INTERVAL = 1_000;

    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final DocDependencyDao docDependencyDao;
    private final DocDependencyService docDependencyService;
    private final Provider<Set<DocDependencyProvider>> docDependencyProvidersProvider;

    @Inject
    DocDependencyRebuildStartup(final ClusterLockService clusterLockService,
                                final SecurityContext securityContext,
                                final DocDependencyDao docDependencyDao,
                                final DocDependencyService docDependencyService,
                                final Provider<Set<DocDependencyProvider>> docDependencyProvidersProvider) {
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.docDependencyDao = docDependencyDao;
        this.docDependencyService = docDependencyService;
        this.docDependencyProvidersProvider = docDependencyProvidersProvider;
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
            LOGGER.info("Document dependency rebuild skipped as another node holds lock '{}'", LOCK_NAME);
        }
    }

    private void rebuild() {
        final List<DocVersion> staleDocs = docDependencyDao.findDocsWithStaleDependencies();
        if (staleDocs.isEmpty()) {
            LOGGER.debug("No document dependencies need rebuilding");
            return;
        }

        final int total = staleDocs.size();
        LOGGER.info("Rebuilding dependencies for {} documents", total);
        final DurationTimer timer = DurationTimer.start();

        final Map<String, DocDependencyProvider> providersByType = docDependencyProvidersProvider.get()
                .stream()
                .collect(Collectors.toMap(DocDependencyProvider::getType, Function.identity(), (a, b) -> a));

        int processedCount = 0;
        int rebuiltCount = 0;
        int changedCount = 0;
        int errorCount = 0;
        final Map<String, Integer> noProviderCounts = new TreeMap<>();

        for (final DocVersion docVersion : staleDocs) {
            final DocRef docRef = docVersion.docRef();
            final DocDependencyProvider provider = providersByType.get(docRef.getType());
            if (provider == null) {
                noProviderCounts.merge(docRef.getType(), 1, Integer::sum);
            } else {
                try {
                    final Set<DocRef> dependencies = provider.getDependencies(docRef);
                    if (docDependencyDao.setDependenciesIfUnchanged(docRef, docVersion.version(), dependencies)) {
                        rebuiltCount++;
                    } else {
                        // Saved or deleted since we found it, so its edges are already current
                        changedCount++;
                    }
                } catch (final RuntimeException e) {
                    errorCount++;
                    LOGGER.error(() -> LogUtil.message("Error rebuilding dependencies for {}: {}",
                            docRef, e.getMessage()), e);
                }
            }

            processedCount++;
            if (processedCount % PROGRESS_INTERVAL == 0) {
                LOGGER.info("Rebuilt dependencies for {} of {} documents", processedCount, total);
            }
        }

        docDependencyService.invalidateBrokenDependencyCache();

        LOGGER.info("Rebuilt dependencies for {} documents in {} (rebuilt: {}, changed during rebuild: {}, " +
                    "errors: {}, no provider: {})",
                total, timer, rebuiltCount, changedCount, errorCount, noProviderCounts);
        if (!noProviderCounts.isEmpty()) {
            LOGGER.warn("Dependencies were not rebuilt for these document types as they have no " +
                        "dependency provider: {}", noProviderCounts);
        }
    }
}
