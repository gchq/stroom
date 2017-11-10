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

package stroom.apiclients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import stroom.auth.service.ApiClient;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.ApiKeyApi;
import stroom.auth.service.api.AuthenticationApi;
import stroom.auth.service.api.SessionApi;
import stroom.auth.service.api.UserApi;
import stroom.auth.service.api.model.CreateTokenRequest;
import stroom.auth.service.api.model.SearchRequest;
import stroom.auth.service.api.model.SearchResponse;
import stroom.auth.service.api.model.Token;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Optional;

/**
 * This class manages tokens for the RemoteDataSourceProvider. The RemoteDataSourceProvider
 * needs a user's API token so it can make HTTP requests on their behalf. These tokens live in
 * the TokenService. If one doesn't exist then this manager will create one.
 *
 * TODO: add token caching
 *
 * If a logged-in user's API token is ever needed elsewhere then this class should be refactored accordingly.
 */
@Component
public class AuthenticationServiceClients {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationServiceClients.class);
    private final String ourApiToken;
    private final String tokenServiceUrl;
    private final ApiClient authServiceClient;

    @Inject
    public AuthenticationServiceClients(
        @Value("#{propertyConfigurer.getProperty('stroom.security.apiToken')}")
        final String ourApiToken,
        @Value("#{propertyConfigurer.getProperty('stroom.auth.url')}")
        final String tokenServiceUrl) {
        this.ourApiToken = ourApiToken;
        this.tokenServiceUrl = tokenServiceUrl;
        authServiceClient = new ApiClient();
        authServiceClient.setBasePath(tokenServiceUrl);
        authServiceClient.addDefaultHeader("Authorization", "Bearer " + ourApiToken);
    }

    public AuthenticationApi newAuthenticationApi(){
        return new AuthenticationApi(authServiceClient);
    }

    public ApiKeyApi newApiKeyApi(){
        return new ApiKeyApi(authServiceClient);
    }

    public UserApi newUserApi(){
        return new UserApi(authServiceClient);
    }

    public SessionApi newSessionApi(){
        return new SessionApi(authServiceClient);
    }

    public String getUsersApiToken(String userId) {
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

        Optional<String> usersApiToken;
        try {
            SearchResponse authSearchResponse = newApiKeyApi().search(authSearchRequest);
            if (authSearchResponse.getTokens().isEmpty()) {
                // User doesn't have an API token and cannot make this request.
                LOGGER.warn("Tried to get a user's API key but they don't have one! User was: " +
                        userId);
                usersApiToken = Optional.empty();
            } else {
                usersApiToken = Optional.of(authSearchResponse.getTokens().get(0).getToken());
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
