/*
 * Copyright 2025 Crown Copyright
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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.shared.PermissionChangeResource;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * Resource to provide a central place for non-Explorer stuff to change permissions.
 */
@AutoLogged(OperationType.MANUALLY_LOGGED) // Logging done by PermissionChangeService
public class PermissionChangeResourceImpl implements PermissionChangeResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PermissionChangeResourceImpl.class);

    private final Provider<PermissionChangeService> permissionChangeServiceProvider;

    @SuppressWarnings("unused")
    @Inject
    PermissionChangeResourceImpl(final Provider<PermissionChangeService> permissionChangeServiceProvider) {
        this.permissionChangeServiceProvider = permissionChangeServiceProvider;
    }

    @Override
    public Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        LOGGER.debug("changeDocumentPermissions() - request: {}", request);
        permissionChangeServiceProvider.get().changeDocumentPermissions(request);
        return Boolean.TRUE;
    }
}
