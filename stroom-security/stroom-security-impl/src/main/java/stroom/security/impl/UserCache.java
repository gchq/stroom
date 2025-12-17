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
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Optional;

@Singleton
public class UserCache implements Clearable, PermissionChangeEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserCache.class);

    private static final String CACHE_NAME_BY_UUID = "User Cache (by User UUID)";

    private final LoadingStroomCache<String, Optional<User>> cacheByUuid;

    @Inject
    public UserCache(final CacheManager cacheManager,
                     final Provider<AuthorisationConfig> authorisationConfigProvider,
                     final Provider<UserDao> userDaoProvider) {
        cacheByUuid = cacheManager.createLoadingCache(
                CACHE_NAME_BY_UUID,
                () -> authorisationConfigProvider.get().getUserByUuidCache(),
                userUuid -> {
                    LOGGER.debug("Loading user uuid '{}' into cache '{}'",
                            userUuid, CACHE_NAME_BY_UUID);
                    return userDaoProvider.get().getByUuid(userUuid);
                });
    }

    /**
     * Gets a user/group by their stroom user UUID if they exist.
     */
    public Optional<User> getByUuid(final String userUuid) {
        return NullSafe.isBlankString(userUuid)
                ? Optional.empty()
                : cacheByUuid.get(userUuid);
    }

    public Optional<User> getByRef(final UserRef userRef) {
        Objects.requireNonNull(userRef, "User ref is null");
        return cacheByUuid.get(userRef.getUuid());
    }

    @Override
    public void clear() {
        cacheByUuid.clear();
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (event.getUserRef() != null) {
            cacheByUuid.invalidate(event.getUserRef().getUuid());
        }
    }
}
