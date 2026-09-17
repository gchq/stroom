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
 */

package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.security.api.SecurityContext;
import stroom.util.cache.CacheConfig;
import stroom.util.entityevent.EntityEvent;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.sysinfo.HasSystemInfo;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractDocPool<K extends AbstractDoc, V>
        extends AbstractPoolCache<K, V>
        implements Pool<K, V>, HasSystemInfo, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDocPool.class);

    private final DocumentPermissionCache documentPermissionCache;
    private final SecurityContext securityContext;

    public AbstractDocPool(final CacheManager cacheManager,
                           final String name,
                           final Supplier<CacheConfig> cacheConfigSupplier,
                           final DocumentPermissionCache documentPermissionCache,
                           final SecurityContext securityContext) {
        super(cacheManager, name, cacheConfigSupplier);
        this.documentPermissionCache = documentPermissionCache;
        this.securityContext = securityContext;
    }

    @Override
    public PoolItem<V> borrowObject(final K key, final boolean usePool) {
        if (!documentPermissionCache.canUseDocument(key.asDocRef())) {
            throw new PermissionException(
                    securityContext.getUserRef(), "You do not have permission to use " + key);
        }

        // Get the item from the pool.
        return super.internalBorrowObject(key, usePool);
    }

    @Override
    public void returnObject(final PoolItem<V> poolItem, final boolean usePool) {
        super.internalReturnObject(poolItem, usePool);
    }

    @Override
    protected V internalCreateValue(final K key) {
        return securityContext.asProcessingUserResult(() -> createValue(key));
    }

    protected abstract V createValue(final K key);

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange() called for {}", event);
        // Get the doc object for the changed xslt then invalidate it in the cache.
        // If the oldDocRef is present then use that as that is what would be in the pool
        final DocRef changedDocRef = event.getOldDocRef() != null
                ? event.getOldDocRef()
                : event.getDocRef();

        LOGGER.debug("Invalidating XsltDoc {}", changedDocRef);

        final Consumer<K> loggingPeekFunc = LOGGER.createIfDebugConsumer(doc ->
                LOGGER.debug("Invalidating XsltDoc with name: {}, uuid: {}", doc.getName(), doc.getUuid()));

        // Need to scan over the keys because if we read the doc from the store it may look
        // different to the one in the pool cache, e.g. due to name change.
        getKeys().stream()
                .filter(key ->
                        Objects.equals(key.getUuid(), changedDocRef.getUuid()))
                .peek(loggingPeekFunc)
                .forEach(this::invalidate);
    }

    @Override
    Object mapKeyForSystemInfo(final K key) {
        if (key == null) {
            return null;
        } else {
            return Map.of(
                    "name", key.getName(),
                    "uuid", key.getUuid(),
                    "version", key.getVersion());
        }
    }
}
