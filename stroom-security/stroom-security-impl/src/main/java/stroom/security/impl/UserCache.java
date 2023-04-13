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
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = UserDocRefUtil.USER, action = {
        EntityAction.UPDATE,
        EntityAction.DELETE,
        EntityAction.CLEAR_CACHE})
class UserCache implements Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserCache.class);

    private static final String CACHE_NAME_BY_NAME = "User Cache";
    private static final String CACHE_NAME_BY_DISPLAY_NAME = "User Display Name Cache";

    private final AuthenticationService authenticationService;
    private final LoadingStroomCache<String, Optional<User>> cacheByName;
    private final LoadingStroomCache<String, Optional<User>> cacheByDisplayName;

    @Inject
    UserCache(final CacheManager cacheManager,
              final Provider<AuthorisationConfig> authorisationConfigProvider,
              final AuthenticationService authenticationService,
              final UserService userService) {
        this.authenticationService = authenticationService;

        cacheByName = cacheManager.createLoadingCache(
                CACHE_NAME_BY_NAME,
                () -> authorisationConfigProvider.get().getUserCache(),
                name -> {
                    LOGGER.debug("Loading user '{}' into cache '{}'", name, CACHE_NAME_BY_NAME);
                    return authenticationService.getUser(name);
                });

        cacheByDisplayName = cacheManager.createLoadingCache(
                CACHE_NAME_BY_DISPLAY_NAME,
                () -> authorisationConfigProvider.get().getUserByDisplayNameCache(),
                displayName -> {
                    LOGGER.debug("Loading user display name '{}' into cache '{}'",
                            displayName, CACHE_NAME_BY_DISPLAY_NAME);
                    return userService.getUserByDisplayName(displayName);
                });
    }

    private Optional<User> getOrCreateUser(final String name) {
        return Optional.ofNullable(authenticationService.getOrCreateUser(name));
    }

    /**
     * Gets a user from the cache and if it doesn't exist creates it in the database.
     * @param name This is the unique identifier for the user that links the stroom user
     *             to an IDP user, e.g. may be the 'sub' on the IDP depending on stroom config.
     */
    Optional<User> getOrCreate(final String name) {
        if (NullSafe.isBlankString(name)) {
            return Optional.empty();
        } else {
            Optional<User> optUser = cacheByName.get(name);
            if (optUser.isEmpty()) {
                optUser = getOrCreateUser(name);
                if (optUser.isPresent()) {
                    cacheByName.put(name, optUser);
                }
            }
            return optUser;
        }
    }

    /**
     * Gets a user by their unique identifier if it exists, else returns an empty optional
     */
    Optional<User> get(final String name) {
        if (NullSafe.isBlankString(name)) {
            return Optional.empty();
        } else {
            return cacheByName.get(name);
        }
    }

    /**
     * Gets a user by their display name. If no user is found, gets a user by their
     * unique identifier.
     */
    public Optional<User> getByDisplayName(final String displayName) {
        if (NullSafe.isBlankString(displayName)) {
            return Optional.empty();
        } else {
            return cacheByDisplayName.get(displayName)
                    .or(() -> cacheByName.get(displayName));
        }
    }

    @Override
    public void clear() {
        cacheByName.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        final Consumer<DocRef> docRefConsumer = docRef -> {
            if (docRef.getName() != null) {
                cacheByName.invalidate(docRef.getName());
            } else {
                cacheByName.invalidateEntries((userName, user) ->
                        user.isPresent() && Objects.equals(
                                docRef.getUuid(),
                                user.get().getUuid()));
            }
        };

        if (EntityAction.CLEAR_CACHE.equals(event.getAction())) {
            clear();
        } else if (UserDocRefUtil.USER.equals(event.getDocRef().getType())) {
            NullSafe.consume(event.getDocRef(), docRefConsumer);
            NullSafe.consume(event.getOldDocRef(), docRefConsumer);
        }
    }
}
