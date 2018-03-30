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

package stroom.pool;

import stroom.entity.DocumentPermissionCache;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.PermissionException;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.cache.CacheManager;

public abstract class AbstractEntityPool<K extends DocumentEntity, V> extends AbstractPoolCache<VersionedEntityDecorator<K>, V> implements Pool<K, V> {
    private final DocumentPermissionCache documentPermissionCache;
    private final Security security;
    private final SecurityContext securityContext;

    public AbstractEntityPool(final CacheManager cacheManager,
                              final String name,
                              final DocumentPermissionCache documentPermissionCache,
                              final Security security,
                              final SecurityContext securityContext) {
        super(cacheManager, name);
        this.documentPermissionCache = documentPermissionCache;
        this.security = security;
        this.securityContext = securityContext;
    }

    @Override
    public PoolItem<V> borrowObject(final K key, final boolean usePool) {
        if (!documentPermissionCache.hasDocumentPermission(key.getType(), key.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use " + DocRefUtil.create(key));
        }

        // Get the item from the pool.
        return super.internalBorrowObject(new VersionedEntityDecorator<>(key), usePool);
    }

    @Override
    public void returnObject(final PoolItem<V> poolItem, final boolean usePool) {
        super.internalReturnObject(poolItem, usePool);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected V internalCreateValue(final Object key) {
        return security.asProcessingUserResult(() -> {
            final VersionedEntityDecorator<K> versionedEntityDecorator = (VersionedEntityDecorator<K>) key;
            return createValue(versionedEntityDecorator.getEntity());
        });
    }

    protected abstract V createValue(final K key);
}
