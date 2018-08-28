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

package stroom.security;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.service.ApiClient;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.ApiKeyApi;
import stroom.auth.service.api.AuthenticationApi;
import stroom.auth.service.api.model.CreateTokenRequest;
import stroom.auth.service.api.model.SearchRequest;
import stroom.auth.service.api.model.SearchResponse;
import stroom.auth.service.api.model.Token;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Optional;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationServiceClients.class);
    private final ApiClient authServiceClient;
    private final boolean enableAuth;

    @Inject
    AuthenticationServiceClients(final AuthenticationConfig securityConfig) {
        enableAuth = securityConfig.isAuthenticationRequired();
        if (enableAuth) {
            if (Strings.isNullOrEmpty(securityConfig.getApiToken())) {
                throw new RuntimeException("Missing API key! Please configure using 'stroom.security.apiToken'");
            }

            if (Strings.isNullOrEmpty(securityConfig.getAuthServicesBaseUrl())) {
                throw new RuntimeException("Missing auth service URL! Please configure using 'stroom.auth.services.url'");
            }
        }

        authServiceClient = new ApiClient();
        authServiceClient.setBasePath(securityConfig.getAuthServicesBaseUrl());
        authServiceClient.addDefaultHeader("Authorization", "Bearer " + securityConfig.getApiToken());
    }

    AuthenticationApi newAuthenticationApi() {
        return new AuthenticationApi(authServiceClient);
    }

    ApiKeyApi newApiKeyApi() {
        return new ApiKeyApi(authServiceClient);
    }

//    UserApi newUserApi(){
//        return new UserApi(authServiceClient);
//    }

    String getUsersApiToken(String userId) {
        if (!enableAuth) {
            return null;
        }

        //TODO check cache
        Optional<String> optionalToken = getTokenFromTokenService(userId);

        if (!optionalToken.isPresent()) {
            try {
                LOGGER.info("This user ({}) does not have an API token - I'll ask the TokenService to create one.",
                        userId);
                return createTokenForUser(userId);
            } catch (ApiException e) {
                String message = "There was a problem requesting the creation of an API token from the TokenService. " +
                        "It failed for this user: " + userId;
                LOGGER.error(message);
                throw new RuntimeException(message, e);
            }
        } else {
            return optionalToken.get();
        }
    }

    private String createTokenForUser(String userId) throws ApiException {
        CreateTokenRequest createTokenRequest = new CreateTokenRequest();
        createTokenRequest.setEnabled(true);
        createTokenRequest.setTokenType("api");
        createTokenRequest.setUserEmail(userId);
        createTokenRequest.setComments(
                "Created by Stroom's AuthenticationServiceClients because the user did not have an existing API token.");
        Token token = newApiKeyApi().create(createTokenRequest);
        return token.getToken();
    }

    private Optional<String> getTokenFromTokenService(String userId) {
        LOGGER.debug("Requesting token for user: " + userId);
        SearchRequest authSearchRequest = new SearchRequest();
        authSearchRequest.setLimit(10);
        authSearchRequest.setPage(0);
        authSearchRequest.setFilters(new HashMap<String, String>() {{
            put("user_email", userId);
            put("token_type", "api");
            put("enabled", "true");
        }});

        Optional<String> usersApiToken = Optional.empty();
        try {
            SearchResponse authSearchResponse = newApiKeyApi().search(authSearchRequest);
            for(Token token : authSearchResponse.getTokens()){
                // We're using the auth token search API to get this token. It'll be a fuzzy match so
                // we need to make sure the userId matches exactly.
                if(token.getUserEmail().equalsIgnoreCase(userId)){
                    usersApiToken = Optional.of(token.getToken());
                    break;
                }
            }

            if(!usersApiToken.isPresent()){
                // User doesn't have an API token and cannot make this request.
                LOGGER.warn("Tried to get a user's API key but they don't have one! User was: " +
                        userId);
            }

        } catch (ApiException e) {
            String message =
                    "Unable to get the user's token from the Token service! User was: " + userId;
            LOGGER.error(message);
            throw new RuntimeException(message, e);
        }

        return usersApiToken;
    }
}
