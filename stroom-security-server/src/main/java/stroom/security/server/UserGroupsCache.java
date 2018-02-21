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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventBus;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.UserRef;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.concurrent.TimeUnit;

@EntityEventHandler(type = User.ENTITY_TYPE, action = EntityAction.CLEAR_CACHE)
class UserGroupsCache implements EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final Provider<EntityEventBus> eventBusProvider;
    private final LoadingCache<UserRef, List> cache;

    @Inject
    @SuppressWarnings("unchecked")
    UserGroupsCache(final CacheManager cacheManager,
                    final UserService userService,
                    final Provider<EntityEventBus> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
        final CacheLoader<UserRef, List> cacheLoader = CacheLoader.from(userService::findGroupsForUser);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(30, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("User Groups Cache", cacheBuilder, cache);
    }

    @SuppressWarnings("unchecked")
    List<UserRef> get(final UserRef key) {
        return cache.getUnchecked(key);
    }

    void remove(final UserRef userRef) {
        cache.invalidate(userRef);
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, userRef, EntityAction.CLEAR_CACHE);
    }

    void clear() {
        CacheUtil.clear(cache);
    }

    @Override
    public void onChange(final EntityEvent event) {
        final DocRef docRef = event.getDocRef();
        if (docRef != null) {
            if (docRef instanceof UserRef) {
                UserRef userRef = (UserRef) docRef;
                cache.invalidate(userRef);
            } else {
                final UserRef userRef = new UserRef(docRef.getType(), docRef.getUuid(), docRef.getName(), false, false);
                cache.invalidate(userRef);
            }
        }
    }
}