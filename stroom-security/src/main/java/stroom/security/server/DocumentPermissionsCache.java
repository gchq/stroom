/*
 * Copyright 2016 Crown Copyright
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

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.springframework.stereotype.Component;
import stroom.cache.Loader;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventBus;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityAction;
import stroom.security.shared.DocumentPermissions;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.TimeUnit;

@Component
@EntityEventHandler(action = EntityAction.CLEAR_CACHE)
public class DocumentPermissionsCache implements EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 10000;

    private final Provider<EntityEventBus> eventBusProvider;

    private final Cache<DocRef, DocumentPermissions> cache;

    @Inject
    public DocumentPermissionsCache(final CentralCacheManager cacheManager,
                                    final DocumentPermissionService documentPermissionService,
                                    final Provider<EntityEventBus> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;

        final Loader<DocRef, DocumentPermissions> loader = new Loader<DocRef, DocumentPermissions>() {
            @Override
            public DocumentPermissions load(final DocRef key) throws Exception {
                return documentPermissionService.getPermissionsForDocument(key);
            }
        };

        final CacheConfiguration<DocRef, DocumentPermissions> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(DocRef.class, DocumentPermissions.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(30, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache("Document Permissions Cache", cacheConfiguration);
    }

    DocumentPermissions get(final DocRef key) {
        return cache.get(key);
    }

    void remove(final DocRef docRef) {
        cache.remove(docRef);
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, docRef, EntityAction.CLEAR_CACHE);
    }

    void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        remove(event.getDocRef());
    }
}
