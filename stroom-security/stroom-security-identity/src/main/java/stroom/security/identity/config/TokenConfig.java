/*
 *
 *
 *
 *
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *   Copyright 2017 Crown Copyright
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   See the License for the specific language governing permissions and
 *   Unless required by applicable law or agreed to in writing, software
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   You may obtain a copy of the License at
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   import stroom.util.shared.IsStroomConfig;
 *   limitations under the License.
 *   you may not use this file except in compliance with the License.
 */

package stroom.security.identity.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TokenConfig extends AbstractConfig implements IsStroomConfig {

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before a refresh token will expire.")
    private StroomDuration refreshTokenExpiration = StroomDuration.ofDays(30);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an access token will expire.")
    private StroomDuration accessTokenExpiration = StroomDuration.ofMinutes(60);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an ID token will expire.")
    private StroomDuration idTokenExpiration = StroomDuration.ofMinutes(60);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an email reset token will expire.")
    private StroomDuration emailResetTokenExpiration = StroomDuration.ofMinutes(10);

    @JsonProperty
    @JsonPropertyDescription("The default API key expiry time")
    private StroomDuration defaultApiKeyExpiration = StroomDuration.ofDays(365);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The Issuer value used in Json Web Tokens.")
    private String jwsIssuer = "stroom";

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The cryptographic algorithm used in the Json Web Signatures. " +
            "Valid values can be found at https://openid.net/specs/draft-jones-json-web-signature-04.html#Signing")
    private String algorithm = "RS256";


    public TokenConfig() {
    }

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public TokenConfig(
            @JsonProperty("refreshTokenExpiration") final StroomDuration refreshTokenExpiration,
            @JsonProperty("accessTokenExpiration") final StroomDuration accessTokenExpiration,
            @JsonProperty("idTokenExpiration") final StroomDuration idTokenExpiration,
            @JsonProperty("emailResetTokenExpiration") final StroomDuration emailResetTokenExpiration,
            @JsonProperty("defaultApiKeyExpiration") final StroomDuration defaultApiKeyExpiration,
            @JsonProperty("jwsIssuer") final String jwsIssuer,
            @JsonProperty("algorithm") final String algorithm) {
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.accessTokenExpiration = accessTokenExpiration;
        this.idTokenExpiration = idTokenExpiration;
        this.emailResetTokenExpiration = emailResetTokenExpiration;
        this.defaultApiKeyExpiration = defaultApiKeyExpiration;
        this.jwsIssuer = jwsIssuer;
        this.algorithm = algorithm;
    }

    public StroomDuration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public void setRefreshTokenExpiration(final StroomDuration refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public StroomDuration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public void setAccessTokenExpiration(final StroomDuration accessTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public StroomDuration getIdTokenExpiration() {
        return idTokenExpiration;
    }

    public void setIdTokenExpiration(final StroomDuration idTokenExpiration) {
        this.idTokenExpiration = idTokenExpiration;
    }

    public StroomDuration getEmailResetTokenExpiration() {
        return emailResetTokenExpiration;
    }

    public void setEmailResetTokenExpiration(final StroomDuration emailResetTokenExpiration) {
        this.emailResetTokenExpiration = emailResetTokenExpiration;
    }

    public StroomDuration getDefaultApiKeyExpiration() {
        return defaultApiKeyExpiration;
    }

    public void setDefaultApiKeyExpiration(final StroomDuration defaultApiKeyExpiration) {
        this.defaultApiKeyExpiration = defaultApiKeyExpiration;
    }

    public String getJwsIssuer() {
        return jwsIssuer;
    }

    @SuppressWarnings("unused")
    public void setJwsIssuer(String jwsIssuer) {
        this.jwsIssuer = jwsIssuer;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    @SuppressWarnings("unused")
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
