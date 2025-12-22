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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.common.impl.ClientCredentials;
import stroom.util.shared.Unauthenticated;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.BadRequestException;

@AutoLogged(OperationType.UNLOGGED) // We are only proxying for the idp so no need to log it
public class AuthProxyResourceImpl implements AuthProxyResource {

    private final Provider<AuthProxyService> idpProxyServiceProvider;

    @Inject
    public AuthProxyResourceImpl(final Provider<AuthProxyService> idpProxyServiceProvider) {
        this.idpProxyServiceProvider = idpProxyServiceProvider;
    }

    @Unauthenticated
    @Override
    public String fetchToken(final ClientCredentials clientCredentials) {
        try {
            final String token = idpProxyServiceProvider.get().fetchToken(clientCredentials);

            return token;
        } catch (final IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        } catch (final Exception e) {
            // Let the ex mapper handle it
            throw e;
        }
    }
}
