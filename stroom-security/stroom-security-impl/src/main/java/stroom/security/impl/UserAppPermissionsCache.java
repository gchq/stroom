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

package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.docref.DocRef;
import stroom.util.entity.EntityAction;
import stroom.util.entity.EntityEvent;
import stroom.util.entity.EntityEventBus;
import stroom.util.entity.EntityEventHandler;
import stroom.security.shared.User;
import stroom.security.shared.UserAppPermissions;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = UserDocRefUtil.USER, action = {EntityAction.CLEAR_CACHE})
public class UserAppPermissionsCache implements Clearable, EntityEvent.Handler {
    private static final String CACHE_NAME = "User App Permissions Cache";

    private final Provider<EntityEventBus> eventBusProvider;
    private final ICache<User, UserAppPermissions> cache;

    @Inject
    UserAppPermissionsCache(final CacheManager cacheManager,
                            final AuthorisationConfig authorisationConfig,
                            final UserAppPermissionService userAppPermissionService,
                            final Provider<EntityEventBus> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
        cache = cacheManager.create(CACHE_NAME, authorisationConfig::getUserAppPermissionsCache, userAppPermissionService::getPermissionsForUser);
    }

    UserAppPermissions get(final User user) {
        return cache.get(user);
    }

    void remove(final User user) {
        cache.invalidate(user);

        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, UserDocRefUtil.createDocRef(user), EntityAction.CLEAR_CACHE);
    }

    @Override
    public void onChange(final EntityEvent event) {
        final DocRef docRef = event.getDocRef();
        final User user = UserDocRefUtil.createUser(docRef);
        if (user != null) {
            cache.invalidate(user);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
