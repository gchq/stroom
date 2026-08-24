/*
 * Copyright 2018 Crown Copyright
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

import stroom.security.openid.api.IdpType;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class AuthenticationConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_OPENID = "openId";
    public static final String PROP_NAME_PREVENT_LOGIN = "preventLogin";

    private static final boolean DEFAULT_PREVENT_LOGIN = false;
    public static final String PROP_NAME_API_KEY_CACHE = "apiKeyCache";
    public static final String PROP_NAME_AUTHENTICATION_STATE_CACHE = "authenticationStateCache";
    public static final String PROP_NAME_CSRF = "csrf";
    public static final String PROP_NAME_EDGE_AUTHENTICATION = "edgeAuthentication";
    public static final String PROP_NAME_MAX_API_KEY_EXPIRY_AGE = "maxApiKeyExpiryAge";

    private final CacheConfig apiKeyCache;
    private final CacheConfig authenticationStateCache;
    private final CsrfConfig csrfConfig;
    private final EdgeAuthenticationConfig edgeAuthenticationConfig;
    private final StroomDuration maxApiKeyExpiryAge;
    private final StroomOpenIdConfig openIdConfig;
    private final boolean preventLogin;

    public AuthenticationConfig() {
        apiKeyCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterWrite(StroomDuration.ofSeconds(60))
                .build();
        authenticationStateCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        csrfConfig = new CsrfConfig();
        edgeAuthenticationConfig = new EdgeAuthenticationConfig();
        maxApiKeyExpiryAge = StroomDuration.ofDays(365);
        openIdConfig = new StroomOpenIdConfig();
        preventLogin = DEFAULT_PREVENT_LOGIN;
    }

    @JsonCreator
    public AuthenticationConfig(
            @JsonProperty(PROP_NAME_API_KEY_CACHE) final CacheConfig apiKeyCache,
            @JsonProperty(PROP_NAME_AUTHENTICATION_STATE_CACHE) final CacheConfig authenticationStateCache,
            @JsonProperty(PROP_NAME_CSRF) final CsrfConfig csrfConfig,
            @JsonProperty(PROP_NAME_EDGE_AUTHENTICATION) final EdgeAuthenticationConfig edgeAuthenticationConfig,
            @JsonProperty(PROP_NAME_MAX_API_KEY_EXPIRY_AGE) final StroomDuration maxApiKeyExpiryAge,
            @JsonProperty(PROP_NAME_OPENID) final StroomOpenIdConfig openIdConfig,
            @JsonProperty(PROP_NAME_PREVENT_LOGIN) final Boolean preventLogin) {
        this.apiKeyCache = apiKeyCache;
        this.authenticationStateCache = authenticationStateCache;
        this.csrfConfig = Objects.requireNonNullElseGet(csrfConfig, CsrfConfig::new);
        this.edgeAuthenticationConfig = Objects.requireNonNullElseGet(
                edgeAuthenticationConfig, EdgeAuthenticationConfig::new);
        this.maxApiKeyExpiryAge = maxApiKeyExpiryAge;
        this.openIdConfig = openIdConfig;
        this.preventLogin = Objects.requireNonNullElse(preventLogin, DEFAULT_PREVENT_LOGIN);
    }

    @JsonProperty(PROP_NAME_API_KEY_CACHE)
    public CacheConfig getApiKeyCache() {
        return apiKeyCache;
    }

    @JsonProperty(PROP_NAME_AUTHENTICATION_STATE_CACHE)
    public CacheConfig getAuthenticationStateCache() {
        return authenticationStateCache;
    }

    @JsonProperty(PROP_NAME_CSRF)
    public CsrfConfig getCsrfConfig() {
        return csrfConfig;
    }

    @JsonProperty(PROP_NAME_EDGE_AUTHENTICATION)
    public EdgeAuthenticationConfig getEdgeAuthenticationConfig() {
        return edgeAuthenticationConfig;
    }

    @JsonProperty(PROP_NAME_MAX_API_KEY_EXPIRY_AGE)
    @JsonPropertyDescription("The maximum expiry age for new API keys. Defaults to 365 days.")
    @NotNull
    public StroomDuration getMaxApiKeyExpiryAge() {
        return maxApiKeyExpiryAge;
    }

    @JsonProperty(PROP_NAME_OPENID)
    public StroomOpenIdConfig getOpenIdConfig() {
        return openIdConfig;
    }

    @JsonPropertyDescription("Prevent new logins to the system. This is useful if the system is scheduled to " +
                             "have an outage.")
    @JsonProperty(PROP_NAME_PREVENT_LOGIN)
    public boolean isPreventLogin() {
        return preventLogin;
    }

    /**
     * With an edge proxy as the Relying Party there is a real external IDP that issued/verified the
     * user's identity; stroom's internal IDP cannot sit behind an edge that authenticates against a
     * different one, and NO_IDP has no tokens to verify at all.
     */
    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "If " + PROP_NAME_EDGE_AUTHENTICATION + "." +
                                EdgeAuthenticationConfig.PROP_NAME_ENABLED + " is true, property " +
                                PROP_NAME_OPENID + ".identityProviderType must be EXTERNAL_IDP.")
    public boolean isEdgeAuthenticationValid() {
        return !edgeAuthenticationConfig.isEnabled()
               || (openIdConfig != null
                   && IdpType.EXTERNAL_IDP.equals(openIdConfig.getIdentityProviderType()));
    }

    @Override
    public String toString() {
        return "AuthenticationConfig{" +
               "apiKeyCache=" + apiKeyCache +
               ", authenticationStateCache=" + authenticationStateCache +
               ", csrfConfig=" + csrfConfig +
               ", edgeAuthenticationConfig=" + edgeAuthenticationConfig +
               ", maxApiKeyExpiryAge=" + maxApiKeyExpiryAge +
               ", openIdConfig=" + openIdConfig +
               ", preventLogin=" + preventLogin +
               '}';
    }
}
