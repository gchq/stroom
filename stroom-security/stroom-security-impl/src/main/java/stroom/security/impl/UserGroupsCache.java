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
import stroom.security.api.UserGroupsService;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.stream.Collectors;

@Singleton
class UserGroupsCache implements PermissionChangeEvent.Handler, Clearable, UserGroupsService {

    private static final String USER_GROUPS_CACHE_NAME = "User Groups Cache";

    private final Provider<UserDao> userDaoProvider;
    private final LoadingStroomCache<UserRef, Set<UserRef>> cache;

    @Inject
    UserGroupsCache(final CacheManager cacheManager,
                    final Provider<UserDao> userDaoProvider,
                    final Provider<AuthorisationConfig> authorisationConfigProvider) {
        this.userDaoProvider = userDaoProvider;
        cache = cacheManager.createLoadingCache(
                USER_GROUPS_CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserGroupsCache(),
                this::create);
    }

    private Set<UserRef> create(final UserRef userRef) {
        final FindUserCriteria criteria = new FindUserCriteria();
        final ResultPage<User> users = userDaoProvider.get().findGroupsForUser(userRef.getUuid(), criteria);
        return users.getValues().stream().map(User::asRef).collect(Collectors.toSet());
    }

    @Override
    public Set<UserRef> getGroups(final UserRef userRef) {
        return cache.get(userRef);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        final UserRef userRef = event.getUserRef();
        if (userRef != null) {
            cache.invalidate(userRef);
        }
    }
}
