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

    private static final String CACHE_NAME_BY_SUBJECT_ID = "User Cache (by Unique Identifier)";
    private static final String CACHE_NAME_BY_DISPLAY_NAME = "User Cache (by Display Name)";
    private static final String CACHE_NAME_BY_UUID = "User Cache (by User UUID)";

    private final AuthenticationService authenticationService;
    private final LoadingStroomCache<String, Optional<User>> cacheBySubjectId;
    private final LoadingStroomCache<String, Optional<User>> cacheByDisplayName;
    private final LoadingStroomCache<String, Optional<User>> cacheByUuid;

    @Inject
    UserCache(final CacheManager cacheManager,
              final Provider<AuthorisationConfig> authorisationConfigProvider,
              final AuthenticationService authenticationService,
              final UserService userService) {
        this.authenticationService = authenticationService;

        // TODO: 20/07/2023 We could consider trying to re-use User objects between
        //  all three caches but it presents concurrency issues and the risk of deadlocks,
        //  maybe.
        cacheByUuid = cacheManager.createLoadingCache(
                CACHE_NAME_BY_UUID,
                () -> authorisationConfigProvider.get().getUserByUuidCache(),
                userUuid -> {
                    LOGGER.debug("Loading user uuid '{}' into cache '{}'",
                            userUuid, CACHE_NAME_BY_DISPLAY_NAME);
                    return userService.loadByUuid(userUuid);
                });

        cacheBySubjectId = cacheManager.createLoadingCache(
                CACHE_NAME_BY_SUBJECT_ID,
                () -> authorisationConfigProvider.get().getUserCache(),
                subjectId -> {
                    LOGGER.debug("Loading user with subjectId '{}' into cache '{}'",
                            subjectId, CACHE_NAME_BY_SUBJECT_ID);
                    //
                    return authenticationService.getUser(subjectId);
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

    private Optional<User> getOrCreateUser(final String subjectId) {
        return Optional.ofNullable(authenticationService.getOrCreateUser(subjectId));
    }

    /**
     * Gets a user from the cache and if it doesn't exist creates it in the database.
     *
     * @param name This is the unique identifier for the user that links the stroom user
     *             to an IDP user, e.g. may be the 'sub' on the IDP depending on stroom config.
     */
    Optional<User> getOrCreate(final String name) {
        if (NullSafe.isBlankString(name)) {
            return Optional.empty();
        } else {
            Optional<User> optUser = cacheBySubjectId.get(name);
            if (optUser.isEmpty()) {
                optUser = getOrCreateUser(name);
                if (optUser.isPresent()) {
                    cacheBySubjectId.put(name, optUser);
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
            return cacheBySubjectId.get(name);
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
                    .or(() -> cacheBySubjectId.get(displayName));
        }
    }

    public Optional<User> getByUuid(final String userUuid) {
        return NullSafe.isBlankString(userUuid)
                ? Optional.empty()
                : cacheByUuid.get(userUuid);
    }

    @Override
    public void clear() {
        cacheBySubjectId.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (EntityAction.CLEAR_CACHE.equals(event.getAction())) {
            clear();
        } else if (UserDocRefUtil.USER.equals(event.getDocRef().getType())) {
            // Special DocRef type as user is not a Doc
            NullSafe.consume(event.getDocRef(), this::invalidateEntry);
            NullSafe.consume(event.getOldDocRef(), this::invalidateEntry);
        }
    }

    private void invalidateEntry(final DocRef docRef) {
        // User is not a Doc so DocRef is being abused to make use of EntityEvent
        // DocRef.name is User.subjectId
        // DocRef.uuid is User.userUuid
        String subjectId = docRef.getName();
        String userUuid = docRef.getUuid();
        String displayName = null;

        if (userUuid != null) {
            // Have to hit the other cache to find out its subjectId
            final Optional<User> optUser = cacheByUuid.get(userUuid);
            if (optUser.isPresent()) {
                if (subjectId == null) {
                    subjectId = optUser.get().getSubjectId();
                }
                displayName = optUser.get().getDisplayName();
            }
        }

        if (subjectId != null) {
            cacheBySubjectId.invalidate(subjectId);
        } else {
            cacheBySubjectId.invalidateEntries((userName, optUser) -> {
                final User user = optUser.orElse(null);
                if (user != null) {
                    return Objects.equals(userUuid, user.getUuid());
                } else {
                    return false;
                }
            });
        }

        if (userUuid != null) {
            cacheByUuid.invalidate(userUuid);
        } else {
            if (subjectId != null) {
                final String finalSubjectId = subjectId;
                cacheByUuid.invalidateEntries((userName, optUser) ->
                        optUser.isPresent() && Objects.equals(finalSubjectId, optUser.get().getSubjectId()));
            }
        }

        if (displayName != null) {
            cacheByDisplayName.invalidate(displayName);
        } else {
            cacheByDisplayName.invalidateEntries((userName, optUser) ->
                    optUser.isPresent() && Objects.equals(userUuid, optUser.get().getUuid()));
        }
    }
}
