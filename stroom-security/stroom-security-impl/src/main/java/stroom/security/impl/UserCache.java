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
import stroom.docref.DocRef;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
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

    private static final String CACHE_NAME = "User Cache";

    private final AuthenticationService authenticationService;
    private final ICache<String, Optional<User>> cache;

    @Inject
    UserCache(final CacheManager cacheManager,
              final Provider<AuthorisationConfig> authorisationConfigProvider,
              final AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        cache = cacheManager.create(
                CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserCache(),
                this::getUser);
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

    @Override
    public void onChange(final EntityEvent event) {
        final Consumer<DocRef> docRefConsumer = docRef -> {
            if (docRef.getName() != null) {
                cache.invalidate(docRef.getName());
            } else {
                cache.invalidateEntries((userName, user) ->
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
