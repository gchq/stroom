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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.shared.PermissionChangeResource;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

/**
 * Resource to provide a central place for non-Explorer stuff to change permissions.
 */
@AutoLogged(OperationType.MANUALLY_LOGGED)
public class PermissionChangeResourceImpl implements PermissionChangeResource {

    /** Service to do the permission change */
    private final Provider<PermissionChangeService> permissionChangeServiceProvider;

    /** Event bus to notify of the change */
    private final Provider<EntityEventBus> entityEventBusProvider;

    /**
     * Injected constructor.
     * entityEventBus must be injected to keep java/stroom/dropwizard/common/TestRestResources.java
     * happy.
     */
    @SuppressWarnings("unused")
    @Inject
    PermissionChangeResourceImpl(final Provider<PermissionChangeService> permissionChangeServiceProvider,
                                 final Provider<EntityEventBus> entityEventBusProvider) {
        this.permissionChangeServiceProvider = permissionChangeServiceProvider;
        this.entityEventBusProvider = entityEventBusProvider;
    }

    @Override
    public Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        permissionChangeServiceProvider.get().changeDocumentPermissions(request);
        fireGenericEntityChangeEvent(request.getDocRef().getType());
        return Boolean.TRUE;
    }

    private void fireGenericEntityChangeEvent(final String type) {
        Objects.requireNonNull(type);
        EntityEvent.fire(
                entityEventBusProvider.get(),
                DocRef.builder().type(type).build(),
                EntityAction.UPDATE);
    }
}
