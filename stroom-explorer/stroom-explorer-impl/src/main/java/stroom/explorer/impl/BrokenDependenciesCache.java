package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.importexport.api.ContentService;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

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
}
