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

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenRequest.Builder;
import stroom.security.openid.api.TokenResponse;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Helper for getting OIDC tokens from an endpoint
 */
public class OpenIdTokenRequestHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdTokenRequestHelper.class);

    private final String endpointUri;
    private final OpenIdConfiguration openIdConfiguration;
    private final ObjectMapper objectMapper;
    private final JerseyClientFactory jerseyClientFactory;

    private String code = null;
    private String grantType = null;
    private String redirectUri = null;
    private String refreshToken = null;
    private List<String> scopes = null;
    private ClientCredentials clientCredentials = null;

    public OpenIdTokenRequestHelper(final String endpointUri,
                                    final OpenIdConfiguration openIdConfiguration,
                                    final ObjectMapper objectMapper,
                                    final JerseyClientFactory jerseyClientFactory) {
        this.endpointUri = endpointUri;
        this.openIdConfiguration = openIdConfiguration;
        this.objectMapper = objectMapper;
        this.jerseyClientFactory = jerseyClientFactory;
    }

    /**
     * Allows you do define client credentials that differ from those in {@link OpenIdConfiguration}
     */
    public OpenIdTokenRequestHelper withClientCredentials(final ClientCredentials clientCredentials) {
        this.clientCredentials = clientCredentials;
        return this;
    }

    public OpenIdTokenRequestHelper withCode(final String code) {
        this.code = code;
        return this;
    }

    public OpenIdTokenRequestHelper withGrantType(final String grantType) {
        this.grantType = grantType;
        return this;
    }

    public OpenIdTokenRequestHelper withRedirectUri(final String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }

    public OpenIdTokenRequestHelper withRefreshToken(final String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    public OpenIdTokenRequestHelper withScopes(final List<String> scopes) {
        this.scopes = scopes;
        return this;
    }

    public OpenIdTokenRequestHelper addScopes(final Collection<String> scopes) {
        if (scopes != null) {
            if (this.scopes == null) {
                this.scopes = new ArrayList<>(scopes);
            } else {
                this.scopes.addAll(scopes);
            }
        }
        return this;
    }

    public OpenIdTokenRequestHelper addScope(final String scope) {
        if (scopes == null) {
            scopes = new ArrayList<>();
        }
        this.scopes.add(scope);
        return this;
    }

    public TokenResponse sendRequest(final boolean expectingIdToken) {
        final Invocation.Builder invocationBuilder = createWebTarget().request(MediaType.APPLICATION_JSON_TYPE);
        addBasicAuth(invocationBuilder);

        TokenResponse tokenResponse = null;
        try (final Response response = doPost(invocationBuilder)) {
            if (HttpServletResponse.SC_OK == response.getStatus()) {
                final String json = response.readEntity(String.class);
                LOGGER.debug("response json:\n{}", json);
                tokenResponse = objectMapper.readValue(json, TokenResponse.class);
            } else {
                // Attempt to get content from the response, e.g. an error msg

                // NOTE: If you get a 500 here and are using KeyCloak, make sure the jersey client has
                // gzipEnabledForRequests: false
                // else you will see this error in the KeyCloak logs.
                // java.lang.IllegalArgumentException: URLDecoder: Illegal hex characters in escape (%) pattern
                // This may only be an issue for KeyCloak standalone.
                final String msg = getContent(response);
                throw new AuthenticationException("Received status '" +
                                                  response.getStatus() +
                                                  "' from " +
                                                  endpointUri + " :\n" + msg);
            }
        } catch (final Exception e) {
            throw new AuthenticationException(
                    "Error requesting token from " + endpointUri + ": " + e.getMessage(), e);
        }

        if (tokenResponse == null) {
            throw new AuthenticationException("Null tokenResponse using url: " + endpointUri);
        } else if (expectingIdToken && tokenResponse.getIdToken() == null) {
            throw new AuthenticationException("Expecting '" +
                                              OpenId.ID_TOKEN +
                                              "' to be in response but it is absent. Using url: " + endpointUri);
        }

        return tokenResponse;
    }

    private void addBasicAuth(final Invocation.Builder invocationBuilder) {
        // Some OIDC providers expect authentication using a basic auth header
        // others expect the client(Id|Secret) to be in the form params and some cope
        // with both. Therefore, put them in both places to cover all bases.
        final String clientId = getClientId();
        final String clientSecret = getClientSecret();
        if (!NullSafe.isBlankString(clientId)
            && !NullSafe.isBlankString(clientSecret)) {
            String authorization = String.join(":", clientId, clientSecret);
            authorization = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
            authorization = "Basic " + authorization;
            invocationBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private Response doFormPost(final Invocation.Builder invocationBuilder) {
        final MultivaluedMap<String, String> formParamsMap = new MultivaluedHashMap<>();

        final BiConsumer<String, String> mapAddFunc = (name, val) -> {
            if (!NullSafe.isBlankString(val)) {
                formParamsMap.add(name, val);
            }
        };

        mapAddFunc.accept(OpenId.CLIENT_ID, getClientId());
        mapAddFunc.accept(OpenId.CLIENT_SECRET, getClientSecret());
        mapAddFunc.accept(OpenId.CODE, code);
        mapAddFunc.accept(OpenId.GRANT_TYPE, grantType);
        mapAddFunc.accept(OpenId.REDIRECT_URI, redirectUri);
        mapAddFunc.accept(OpenId.REFRESH_TOKEN, refreshToken);
        mapAddFunc.accept(OpenId.SCOPE, getScopesAsString());

        // application/x-www-form-urlencoded in, application/json out
        invocationBuilder.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_TYPE);

        LOGGER.debug("Form name/value pairs: {}", formParamsMap);
        return invocationBuilder.post(Entity.form(formParamsMap));
    }

    private Response doJsonPost(final Invocation.Builder invocationBuilder) {
        try {
            final Builder builder = TokenRequest.builder();
            final BiConsumer<Consumer<String>, String> builderSetFunc = (func, val) -> {
                if (!NullSafe.isBlankString(val)) {
                    func.accept(val);
                }
            };
            builderSetFunc.accept(builder::clientId, getClientId());
            builderSetFunc.accept(builder::clientSecret, getClientSecret());
            builderSetFunc.accept(builder::code, code);
            builderSetFunc.accept(builder::grantType, grantType);
            builderSetFunc.accept(builder::redirectUri, redirectUri);
            builderSetFunc.accept(builder::refreshToken, refreshToken);
            builderSetFunc.accept(builder::scope, getScopesAsString());

            final TokenRequest tokenRequest = builder.build();

            final String json = objectMapper.writeValueAsString(tokenRequest);

            // json in, json out
            invocationBuilder.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_TYPE);

            LOGGER.debug("json: {}", json);
            // Do this rather than Entity.json, so we can use our own lenient object mapper
            return invocationBuilder.post(Entity.entity(json, MediaType.APPLICATION_JSON_TYPE));

        } catch (final JsonProcessingException e) {
            throw new AuthenticationException(e.getMessage(), e);
        }
    }

    private String getScopesAsString() {
        return String.join(" ", NullSafe.list(scopes));
    }

    private Response doPost(final Invocation.Builder invocationBuilder) {
        LOGGER.debug("State: {}", this);
        if (openIdConfiguration.isFormTokenRequest()) {
            return doFormPost(invocationBuilder);
        } else {
            return doJsonPost(invocationBuilder);
        }
    }

    private static String getContent(final Response response) {
        String msg = "";
        if (response != null) {
            try {
                msg = response.readEntity(String.class);
            } catch (final Exception e) {
                // Just swallow it in case there is no content
            }
        }
        return msg;
    }

    private WebTarget createWebTarget() {
        final Client client = jerseyClientFactory.getNamedClient(JerseyClientName.OPEN_ID);
        return client.target(endpointUri);
    }

    private String getClientId() {
        return NullSafe.getOrElseGet(
                clientCredentials,
                ClientCredentials::getClientId,
                openIdConfiguration::getClientId);
    }

    private String getClientSecret() {
        // clientSecret may not be set, e.g. if we are using mTLS auth
        String clientSecret = NullSafe.get(clientCredentials, ClientCredentials::getClientSecret);
        if (NullSafe.isEmptyString(clientSecret)) {
            clientSecret = openIdConfiguration.getClientSecret();
        }
        if (LOGGER.isDebugEnabled() && NullSafe.isBlankString(clientSecret)) {
            LOGGER.debug("Blank clientSecret");
        }
        return clientSecret;
    }

    @Override
    public String toString() {
        return "OpenIdTokenRequestHelper{" +
               "endpointUri='" + endpointUri + '\'' +
               ", clientId='" + getClientId() + '\'' +
               ", clientSecret='" + getClientSecret() + '\'' +
               ", code='" + code + '\'' +
               ", grantType='" + grantType + '\'' +
               ", redirectUri='" + redirectUri + '\'' +
               ", refreshToken='" + refreshToken + '\'' +
               ", scopes=" + scopes +
               '}';
    }
}
