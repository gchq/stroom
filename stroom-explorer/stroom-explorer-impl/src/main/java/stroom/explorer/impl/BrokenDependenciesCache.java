/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.importexport.api.ContentService;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

//@EntityEventHandler(
//        action = {
//                EntityAction.CREATE,
//                EntityAction.DELETE,
//                EntityAction.UPDATE})
@Singleton
public class BrokenDependenciesCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BrokenDependenciesCache.class);

    private static final long BROKEN_DEPS_MAX_AGE_MS = 10_000L;

    private final Provider<ContentService> contentServiceProvider;
    private final SecurityContext securityContext;

    @Inject
    public BrokenDependenciesCache(final Provider<ContentService> contentServiceProvider,
                                   final SecurityContext securityContext) {
        this.contentServiceProvider = contentServiceProvider;
        this.securityContext = securityContext;
    }

    private volatile Map<DocRef, Set<DocRef>> brokenDependenciesMap = Collections.emptyMap();
    private volatile long brokenDepsNextUpdateEpochMs = 0L;

    public Map<DocRef, Set<DocRef>> getMap() {
        if (System.currentTimeMillis() > brokenDepsNextUpdateEpochMs) {
            synchronized (this) {
                if (System.currentTimeMillis() > brokenDepsNextUpdateEpochMs) {
                    securityContext.asProcessingUser(() -> {
                        LOGGER.debug("Updating broken dependencies map");
                        brokenDependenciesMap = LOGGER.logDurationIfDebugEnabled(() -> {
                            return contentServiceProvider.get().fetchBrokenDependencies();
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

//    @Override
//    public void onChange(final EntityEvent event) {
//        switch (event.getAction()) {
//            case UPDATE -> {
//                // User has potentially fixed a dependency so
//
//                brokenDependenciesMap.
//
//            }
//            case DELETE -> {
//                // User has potentially broken a dependency
//
//            }
//        }
//    }
}
