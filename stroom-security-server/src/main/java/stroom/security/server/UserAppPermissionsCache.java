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

import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventBus;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.query.api.v1.DocRef;
import stroom.security.shared.User;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.TimeUnit;

@Component
@EntityEventHandler(type = User.ENTITY_TYPE, action = {EntityAction.CLEAR_CACHE})
public class UserAppPermissionsCache extends AbstractCacheBean<UserRef, UserAppPermissions> implements EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final UserAppPermissionService userAppPermissionService;
    private final Provider<EntityEventBus> eventBusProvider;

    @Inject
    UserAppPermissionsCache(final CacheManager cacheManager,
                            final UserAppPermissionService userAppPermissionService, final Provider<EntityEventBus> eventBusProvider) {
        super(cacheManager, "User App Permissions Cache", MAX_CACHE_ENTRIES);
        this.userAppPermissionService = userAppPermissionService;
        this.eventBusProvider = eventBusProvider;
        setMaxIdleTime(30, TimeUnit.MINUTES);
        setMaxLiveTime(30, TimeUnit.MINUTES);
    }

    @Override
    protected UserAppPermissions create(final UserRef user) {
        return userAppPermissionService.getPermissionsForUser(user);
    }

    @Override
    public void remove(final UserRef docRef) {
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, docRef, EntityAction.CLEAR_CACHE);
    }

    @Override
    public void onChange(final EntityEvent event) {
        final DocRef docRef = event.getDocRef();
        final UserRef userRef = new UserRef(docRef.getType(), docRef.getUuid(), docRef.getName(), false, false);
        super.remove(userRef);
    }
}
