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
import stroom.docstore.shared.Doc;
import stroom.security.api.DocumentPermissionCache;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.PermissionException;

import java.util.function.Supplier;

public abstract class AbstractDocPool<K extends Doc, V> extends AbstractPoolCache<K, V> implements Pool<K, V> {
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
        if (!documentPermissionCache.hasDocumentPermission(key.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use " + key);
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
}
