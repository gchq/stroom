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
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventBus;
import stroom.entity.shared.EntityEventHandler;
import stroom.security.shared.User;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@EntityEventHandler(type = UserDocRefUtil.USER, action = {EntityAction.CLEAR_CACHE})
class UserGroupsCache implements EntityEvent.Handler, Clearable {
    private static final String CACHE_NAME = "User Groups Cache";

    private final Provider<EntityEventBus> eventBusProvider;
    private final ICache<String, List> cache;

    @Inject
    UserGroupsCache(final CacheManager cacheManager,
                    final UserService userService,
                    final Provider<EntityEventBus> eventBusProvider,
                    final AuthorisationConfig authorisationConfig) {
        this.eventBusProvider = eventBusProvider;
        cache = cacheManager.create(CACHE_NAME, authorisationConfig::getUserGroupsCache, userService::findGroupsForUser);
    }

    @SuppressWarnings("unchecked")
    List<User> get(final String userUuid) {
        return cache.get(userUuid);
    }

    void remove(final User user) {
        cache.invalidate(user.getUuid());
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, UserDocRefUtil.createDocRef(user), EntityAction.CLEAR_CACHE);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        final DocRef docRef = event.getDocRef();
        final User user = UserDocRefUtil.createUser(docRef);
        if (user != null) {
            cache.invalidate(user.getUuid());
        }
    }
}