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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;
import stroom.docref.DocRef;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventBus;
import stroom.security.shared.User;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
class UserGroupsCache implements EntityEvent.Handler, Clearable {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final Provider<EntityEventBus> eventBusProvider;
    private final LoadingCache<String, List> cache;

    @Inject
    @SuppressWarnings("unchecked")
    UserGroupsCache(final CacheManager cacheManager,
                    final UserService userService,
                    final Provider<EntityEventBus> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
        final CacheLoader<String, List> cacheLoader = CacheLoader.from(userService::findGroupsForUser);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(30, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("User Groups Cache", cacheBuilder, cache);
    }

    @SuppressWarnings("unchecked")
    List<User> get(final String userUuid) {
        return cache.getUnchecked(userUuid);
    }

    void remove(final User user) {
        cache.invalidate(user.getUuid());
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, DocRefUtil.create(user), EntityAction.CLEAR_CACHE);
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }

    @Override
    public void onChange(final EntityEvent event) {
        final DocRef docRef = event.getDocRef();
        if (docRef != null && docRef.getType().equals(DocRefUtil.USER)) {
            cache.invalidate(docRef.getUuid());
        }
    }
}