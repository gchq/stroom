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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import stroom.auth.service.ApiClient;
import stroom.auth.service.api.ApiKeyApi;
import stroom.auth.service.api.AuthenticationApi;

import javax.inject.Inject;

/**
 * This class manages tokens for the RemoteDataSourceProvider. The RemoteDataSourceProvider
 * needs a user's API token so it can make HTTP requests on their behalf. These tokens live in
 * the TokenService. If one doesn't exist then this manager will create one.
 * <p>
 * TODO: add token caching
 * <p>
 * If a logged-in user's API token is ever needed elsewhere then this class should be refactored accordingly.
 */
@Component
public class AuthenticationServiceClients {
    private final ApiClient authServiceClient;

    @Inject
    public AuthenticationServiceClients(
            @Value("#{propertyConfigurer.getProperty('stroom.auth.services.url')}") final String authServiceUrl,
            @Value("#{propertyConfigurer.getProperty('stroom.auth.services.verifyingSsl')}") final Boolean verifyingSsl) {
        authServiceClient = new ApiClient();
        authServiceClient.setBasePath(authServiceUrl);
        authServiceClient.setVerifyingSsl(verifyingSsl != null && verifyingSsl);
    }

    AuthenticationApi newAuthenticationApi() {
        return new AuthenticationApi(authServiceClient);
    }

    ApiKeyApi newApiKeyApi() {
        return new ApiKeyApi(authServiceClient);
    }
}
