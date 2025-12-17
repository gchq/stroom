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

package stroom.security.common.impl;

import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfigurationResponse;

/**
 * An abstraction on the Open ID Connect configuration.
 * Implementations may get the values from an external IDP or the internal
 * IDP depending on whether this is stroom or proxy and the local configuration.
 */
public interface IdpConfigurationProvider extends OpenIdConfiguration {

    /**
     * Get the configuration response from the Open ID Connect identity provider
     */
    OpenIdConfigurationResponse getConfigurationResponse();

    @Override
    default String getIssuer() {
        return getConfigurationResponse().getIssuer();
    }

    @Override
    default String getAuthEndpoint() {
        return getConfigurationResponse().getAuthorizationEndpoint();
    }

    @Override
    default String getTokenEndpoint() {
        return getConfigurationResponse().getTokenEndpoint();
    }

    @Override
    default String getJwksUri() {
        return getConfigurationResponse().getJwksUri();
    }

    @Override
    default String getLogoutEndpoint() {
        return getConfigurationResponse().getLogoutEndpoint();
    }
}
