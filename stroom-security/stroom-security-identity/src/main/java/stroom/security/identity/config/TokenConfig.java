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

package stroom.security.identity.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TokenConfig extends AbstractConfig implements IsStroomConfig {

    /**
     * Added to the longest token lifetime to give the retention of a retired signing key, covering
     * the 30s allowed clock skew plus processing time with room to spare. Erring long is safe, a key
     * is merely kept a little longer; erring short would drop still-valid tokens.
     */
    private static final Duration RETENTION_MARGIN = Duration.ofHours(1);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before a refresh token will expire.")
    private final StroomDuration refreshTokenExpiration;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an access token will expire.")
    private final StroomDuration accessTokenExpiration;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an ID token will expire.")
    private final StroomDuration idTokenExpiration;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an email reset token will expire.")
    private final StroomDuration emailResetTokenExpiration;

    @JsonProperty
    @JsonPropertyDescription("The default API key expiry time")
    private final StroomDuration defaultApiKeyExpiration;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("How often the internal identity provider replaces its signing key. " +
            "A retired key stays published, so it can still verify tokens it signed, for as long as " +
            "the longest token lifetime; keeping this at or above that lifetime means only two or three " +
            "keys are ever published at once, whereas a much shorter interval accumulates keys.")
    private final StroomDuration jwkRotationInterval;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The Issuer value used in Json Web Tokens.")
    private final String jwsIssuer;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The cryptographic algorithm used in the Json Web Signatures. " +
            "Valid values can be found at https://openid.net/specs/draft-jones-json-web-signature-04.html#Signing")
    private final String algorithm;


    public TokenConfig() {
        refreshTokenExpiration = StroomDuration.ofDays(30);
        accessTokenExpiration = StroomDuration.ofMinutes(60);
        idTokenExpiration = StroomDuration.ofMinutes(60);
        emailResetTokenExpiration = StroomDuration.ofMinutes(10);
        defaultApiKeyExpiration = StroomDuration.ofDays(365);
        jwkRotationInterval = StroomDuration.ofDays(30);
        jwsIssuer = "stroom";
        algorithm = "RS256";
    }

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public TokenConfig(
            @JsonProperty("refreshTokenExpiration") final StroomDuration refreshTokenExpiration,
            @JsonProperty("accessTokenExpiration") final StroomDuration accessTokenExpiration,
            @JsonProperty("idTokenExpiration") final StroomDuration idTokenExpiration,
            @JsonProperty("emailResetTokenExpiration") final StroomDuration emailResetTokenExpiration,
            @JsonProperty("defaultApiKeyExpiration") final StroomDuration defaultApiKeyExpiration,
            @JsonProperty("jwkRotationInterval") final StroomDuration jwkRotationInterval,
            @JsonProperty("jwsIssuer") final String jwsIssuer,
            @JsonProperty("algorithm") final String algorithm) {
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.accessTokenExpiration = accessTokenExpiration;
        this.idTokenExpiration = idTokenExpiration;
        this.emailResetTokenExpiration = emailResetTokenExpiration;
        this.defaultApiKeyExpiration = defaultApiKeyExpiration;
        this.jwkRotationInterval = jwkRotationInterval;
        this.jwsIssuer = jwsIssuer;
        this.algorithm = algorithm;
    }

    public StroomDuration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public StroomDuration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public StroomDuration getIdTokenExpiration() {
        return idTokenExpiration;
    }

    public StroomDuration getEmailResetTokenExpiration() {
        return emailResetTokenExpiration;
    }

    public StroomDuration getDefaultApiKeyExpiration() {
        return defaultApiKeyExpiration;
    }

    public StroomDuration getJwkRotationInterval() {
        return jwkRotationInterval;
    }

    /**
     * How long a retired signing key must stay published: the longest lifetime of any token it
     * could have signed, plus a margin for clock skew and processing.
     * <p>
     * Deliberately derived rather than configurable. Setting it below the longest token lifetime
     * would make tokens stop verifying before they expire, so there is no safe value for an operator
     * to choose. Note {@code defaultApiKeyExpiration} is excluded: API keys are opaque database
     * values, not JWTs signed by this key, so its 365 day default does not apply here.
     * </p>
     */
    @JsonIgnore
    public Duration getJwkRetention() {
        final Duration longestTokenLifetime = Collections.max(List.of(
                        refreshTokenExpiration,
                        accessTokenExpiration,
                        idTokenExpiration,
                        emailResetTokenExpiration))
                .getDuration();
        return longestTokenLifetime.plus(RETENTION_MARGIN);
    }

    public String getJwsIssuer() {
        return jwsIssuer;
    }

    public String getAlgorithm() {
        return algorithm;
    }
}
