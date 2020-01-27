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

package stroom.security.impl;

import com.google.common.base.Strings;
import stroom.auth.service.ApiClient;
import stroom.auth.service.api.ApiKeyApi;
import stroom.auth.service.api.AuthenticationApi;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class manages tokens for the RemoteDataSourceProvider. The RemoteDataSourceProvider
 * needs a user's API token so it can make HTTP requests on their behalf. These tokens live in
 * the TokenService. If one doesn't exist then this manager will create one.
 * <p>
 * TODO: add token caching
 * <p>
 * If a logged-in user's API token is ever needed elsewhere then this class should be refactored accordingly.
 */
@Singleton
class AuthenticationServiceClients {
    private final ApiClient authServiceClient;

    @Inject
    AuthenticationServiceClients(final AuthenticationConfig securityConfig) {
        if (securityConfig.isAuthenticationRequired()) {
            if (Strings.isNullOrEmpty(securityConfig.getAuthServicesBaseUrl())) {
                throw new RuntimeException("Missing auth service URL! Please configure using 'stroom.auth.services.url'");
            }
        }

        authServiceClient = new ApiClient();
        authServiceClient.setBasePath(securityConfig.getAuthServicesBaseUrl());
        authServiceClient.setVerifyingSsl(securityConfig.isVerifySsl());
    }

    AuthenticationApi newAuthenticationApi() {
        return new AuthenticationApi(authServiceClient);
    }

    ApiKeyApi newApiKeyApi() {
        return new ApiKeyApi(authServiceClient);
    }
}
