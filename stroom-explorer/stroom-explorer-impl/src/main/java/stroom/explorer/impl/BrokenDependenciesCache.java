package stroom.explorer.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.docref.DocRef;
import stroom.importexport.api.ContentService;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@EntityEventHandler(
        action = {
                EntityAction.DELETE,
                EntityAction.UPDATE})
@Singleton
public class BrokenDependenciesCache implements EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BrokenDependenciesCache.class);

    private static final String CACHE_NAME = "Broken Dependencies Cache";

    private final Provider<ExplorerConfig> explorerConfigProvider;
    private final Provider<ContentService> contentServiceProvider;
    private final CacheManager cacheManager;
    private volatile StroomCache<DocRef, Set<DocRef>> cache = null;

    @Inject
    public BrokenDependenciesCache(final Provider<ContentService> contentServiceProvider,
                                   final Provider<ExplorerConfig> explorerConfigProvider,
                                   final CacheManager cacheManager) {
        this.contentServiceProvider = contentServiceProvider;
        this.explorerConfigProvider =explorerConfigProvider;
        this.cacheManager = cacheManager;
//                docRef ->
//                        securityContext.asProcessingUserResult(() ->
//                        contentServiceProvider.get().fetchBrokenDependencies(docRef)));
    }

    private StroomCache<DocRef, Set<DocRef>> getCache() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = cacheManager.create(
                            CACHE_NAME,
                            () -> explorerConfigProvider.get().getDocRefInfoCache());
                    LOGGER.logDurationIfDebugEnabled(() -> {
                        contentServiceProvider.get()
                                .fetchBrokenDependencies()
                                .forEach(cache::put);
                    }, "Filling cache");
                }
            }
        }
        return cache;
    }

    public Map<DocRef, Set<DocRef>> getMap() {
        return getCache().asMap();
    }

//    public void invalidate() {
//        cache.clear();
//        // More efficient to get all broken deps en mass.
//        contentServiceProvider.get()
//                .fetchBrokenDependencies()
//                .forEach(cache::put);
//        brokenDepsNextUpdateEpochMs = 0L;
//    }

    private void invalidate(final DocRef docRef) {
        LOGGER.trace("Invalidating borken dep mapping for docRef {}", docRef);
        getCache().invalidate(docRef);
    }

    private void updateEntry(final DocRef docRef) {
        LOGGER.trace("Updating broken dep mapping entry for docRef {}", docRef);
        getCache().put(docRef, contentServiceProvider.get().fetchBrokenDependencies(docRef));
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        switch (event.getAction()) {
            case UPDATE -> {
                // User has potentially fixed a dependency so invalidate its entry, so it can be checked again
                NullSafe.consume(event.getDocRef(), this::updateEntry);
            }
            case DELETE -> {
                final DocRef depDocRef = event.getDocRef();
                // User has potentially broken a dependency, so we need to see if any entities depended on it.
                // No choice but to get all broken deps to see if our doc is in there
                final Map<DocRef, Set<DocRef>> brokenDepsMap = LOGGER.logDurationIfDebugEnabled(() ->
                        contentServiceProvider.get().fetchBrokenDependencies(),
                        "Fetching all broken deps");

                LOGGER.logDurationIfDebugEnabled(() ->
                        brokenDepsMap.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().contains(depDocRef))
                        .map(Entry::getKey)
                        .forEach(this::updateEntry),
                        "Updating entries for DELETE event");

                // Now remove the deleted entity from our cache
                getCache().remove(depDocRef);
            }
            // We don't care about creates as the docRef for the created thing won't be in our cache.
        }
    }
}
