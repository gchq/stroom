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

package stroom.security.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;
import stroom.security.api.SecurityContext;
import stroom.security.shared.User;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
class UserCache implements Clearable {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<String, Optional<User>> cache;

    @Inject
    @SuppressWarnings("unchecked")
    UserCache(final CacheManager cacheManager,
              final UserService userService,
              final SecurityContext securityContext) {
        // TODO if get is called on the cache before the admin user has been created
        //  then the cache will get an empty optional and then nobody will be able to
        //  get the admin user. May want to call getUser on AuthenticationService which
        //  will ensure the user.
        final CacheLoader<String, Optional<User>> cacheLoader = CacheLoader.from(
                name -> securityContext.asProcessingUserResult(
                        () -> Optional.ofNullable(userService.getUserByName(name))));

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(30, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("User Cache", cacheBuilder, cache);
    }

    @SuppressWarnings("unchecked")
    Optional<User> get(final String name) {
        return cache.getUnchecked(name);
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}