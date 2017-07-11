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

package stroom.security.server;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventBus;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.query.api.v1.DocRef;
import stroom.security.shared.DocumentPermissions;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.TimeUnit;

@Component
@EntityEventHandler(action = EntityAction.CLEAR_CACHE)
public class DocumentPermissionsCache extends AbstractCacheBean<DocRef, DocumentPermissions> implements EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final DocumentPermissionService documentPermissionService;
    private final Provider<EntityEventBus> eventBusProvider;

    private static final CacheConfiguration CACHE_CONFIGURATION = new CacheConfiguration("Document Permissions Cache", MAX_CACHE_ENTRIES);

    static {
        CACHE_CONFIGURATION.setMemoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LFU.toString());
    }

    @Inject
    public DocumentPermissionsCache(final CacheManager cacheManager,
                                    final DocumentPermissionService documentPermissionService, final Provider<EntityEventBus> eventBusProvider) {
        super(cacheManager, CACHE_CONFIGURATION);
        this.documentPermissionService = documentPermissionService;
        this.eventBusProvider = eventBusProvider;
        setMaxIdleTime(30, TimeUnit.MINUTES);
        setMaxLiveTime(30, TimeUnit.MINUTES);
    }

    @Override
    protected DocumentPermissions create(final DocRef document) {
        return documentPermissionService.getPermissionsForDocument(document);
    }

    @Override
    public void remove(final DocRef docRef) {
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, docRef, EntityAction.CLEAR_CACHE);
    }

    @Override
    public void onChange(final EntityEvent event) {
        super.remove(event.getDocRef());
    }
}
