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

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.security.shared.User;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class UserCache implements Clearable {
    private static final String CACHE_NAME = "User Cache";

    private final AuthenticationService authenticationService;
    private final ICache<String, Optional<User>> cache;

    @Inject
    UserCache(final CacheManager cacheManager,
              final AuthorisationConfig authorisationConfig,
              final AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        cache = cacheManager.create(CACHE_NAME, authorisationConfig::getUserCache, this::getUser);
    }

    private Optional<User> getUser(final String name) {
        return Optional.ofNullable(authenticationService.getOrCreateUser(name));
    }

    Optional<User> get(final String name) {
        return cache.get(name);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}