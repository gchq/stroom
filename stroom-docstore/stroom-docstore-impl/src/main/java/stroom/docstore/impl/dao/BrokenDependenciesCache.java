/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class BrokenDependenciesCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BrokenDependenciesCache.class);

    private static final long BROKEN_DEPS_MAX_AGE_MS = 10_000L;

    private final Provider<DocDependencyDao> docDependencyDaoProvider;
    private final SecurityContext securityContext;

    @Inject
    public BrokenDependenciesCache(final Provider<DocDependencyDao> docDependencyDaoProvider,
                                   final SecurityContext securityContext) {
        this.docDependencyDaoProvider = docDependencyDaoProvider;
        this.securityContext = securityContext;
    }

    private volatile Map<DocRef, Set<DocRef>> brokenDependenciesMap = Collections.emptyMap();
    private volatile long brokenDepsNextUpdateEpochMs = 0L;

    public Map<DocRef, Set<DocRef>> getMap(final Set<String> pseudoRefUuids) {
        if (System.currentTimeMillis() > brokenDepsNextUpdateEpochMs) {
            synchronized (this) {
                if (System.currentTimeMillis() > brokenDepsNextUpdateEpochMs) {
                    securityContext.asProcessingUser(() -> {
                        LOGGER.debug("Updating broken dependencies map");
                        brokenDependenciesMap = LOGGER.logDurationIfDebugEnabled(() -> {
                            return docDependencyDaoProvider.get().getBrokenDependencies(pseudoRefUuids);
                        }, "Updating broken dependencies map");
                        brokenDepsNextUpdateEpochMs = System.currentTimeMillis() + BROKEN_DEPS_MAX_AGE_MS;
                    });
                }
            }
        }
        return brokenDependenciesMap;
    }

    public void invalidate() {
        brokenDepsNextUpdateEpochMs = 0L;
    }
}
