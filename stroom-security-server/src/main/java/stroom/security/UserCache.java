/*
 * Copyright 2018 Crown Copyright
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

package stroom.security;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.entity.shared.Clearable;
import stroom.security.shared.UserRef;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
class UserCache implements Clearable {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<String, Optional<UserRef>> cache;

    @Inject
    @SuppressWarnings("unchecked")
    UserCache(final CacheManager cacheManager,
              final UserService userService) {
        final CacheLoader<String, Optional<UserRef>> cacheLoader = CacheLoader.from(name -> Optional.ofNullable(userService.getUserByName(name)));
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(30, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("User Cache", cacheBuilder, cache);
    }

    @SuppressWarnings("unchecked")
    Optional<UserRef> get(final String name) {
        return cache.getUnchecked(name);
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}