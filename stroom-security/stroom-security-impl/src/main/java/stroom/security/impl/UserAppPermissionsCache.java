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

package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.shared.AppPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserDocRefUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Set;

@Singleton
public class UserAppPermissionsCache implements Clearable, PermissionChangeEvent.Handler {

    private static final String CACHE_NAME = "User App Permissions Cache";

    private final Provider<EntityEventBus> eventBusProvider;
    private final LoadingStroomCache<UserRef, Set<AppPermission>> cache;

    @Inject
    UserAppPermissionsCache(final CacheManager cacheManager,
                            final Provider<AuthorisationConfig> authorisationConfigProvider,
                            final Provider<AppPermissionDao> appPermissionDaoProvider,
                            final Provider<EntityEventBus> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserAppPermissionsCache(),
                userRef -> appPermissionDaoProvider.get().getPermissionsForUser(userRef.getUuid()));
    }

    Set<AppPermission> get(final UserRef userRef) {
        return cache.get(userRef);
    }

    void remove(final UserRef userRef) {
        cache.invalidate(userRef);

        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, UserDocRefUtil.createDocRef(userRef), EntityAction.CLEAR_CACHE);
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (event.getUserRef() != null) {
            cache.invalidate(event.getUserRef());
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
