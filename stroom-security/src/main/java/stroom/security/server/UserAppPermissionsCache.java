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
import stroom.pool.CacheUtil;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.TimeUnit;

@Component
@EntityEventHandler(type = User.ENTITY_TYPE, action = {EntityAction.CLEAR_CACHE})
public class UserAppPermissionsCache implements EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final Provider<EntityEventBus> eventBusProvider;
    private final Cache<UserRef, UserAppPermissions> cache;

    @Inject
    UserAppPermissionsCache(final CentralCacheManager cacheManager,
                            final UserAppPermissionService userAppPermissionService, final Provider<EntityEventBus> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;

        final Loader<UserRef, UserAppPermissions> loader = new Loader<UserRef, UserAppPermissions>() {
            @Override
            public UserAppPermissions load(final UserRef key) throws Exception {
                return userAppPermissionService.getPermissionsForUser(key);
            }
        };

        final CacheConfiguration<UserRef, UserAppPermissions> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(UserRef.class, UserAppPermissions.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(30, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache("User App Permissions Cache", cacheConfiguration);
    }

    UserAppPermissions get(final UserRef key) {
        return cache.get(key);
    }

    void remove(final UserRef userRef) {
        cache.remove(userRef);
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, userRef, EntityAction.CLEAR_CACHE);
    }

    void clear() {
        CacheUtil.removeAll(cache);
    }

    @Override
    public void onChange(final EntityEvent event) {
        final DocRef docRef = event.getDocRef();
        final UserRef userRef = new UserRef(docRef.getType(), docRef.getUuid(), docRef.getName(), false, false);
        remove(userRef);
    }
}
