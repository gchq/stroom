/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.config;

import stroom.util.shared.AbstractConfig;
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
public class TokenConfig extends AbstractConfig {

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before a user token will expire.")
    private StroomDuration tokenExpiryTime = StroomDuration.ofMinutes(10);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an email reset token will expire.")
    private StroomDuration emailResetTokenExpiryTime = StroomDuration.ofMinutes(5);

    @JsonProperty
    @JsonPropertyDescription("The default API key expiry time")
    private StroomDuration defaultApiKeyExpiryTime = StroomDuration.ofDays(365);

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
            @JsonProperty("tokenExpiryTime") final StroomDuration tokenExpiryTime,
            @JsonProperty("emailResetTokenExpiryTime") final StroomDuration emailResetTokenExpiryTime,
            @JsonProperty("defaultApiKeyExpiryTime") final StroomDuration defaultApiKeyExpiryTime,
            @JsonProperty("jwsIssuer") final String jwsIssuer,
            @JsonProperty("algorithm") final String algorithm) {

        this.tokenExpiryTime = tokenExpiryTime;
        this.emailResetTokenExpiryTime = emailResetTokenExpiryTime;
        this.defaultApiKeyExpiryTime = defaultApiKeyExpiryTime;
        this.jwsIssuer = jwsIssuer;
        this.algorithm = algorithm;
    }

    public StroomDuration getTokenExpiryTime() {
        return tokenExpiryTime;
    }

    public void setTokenExpiryTime(final StroomDuration tokenExpiryTime) {
        this.tokenExpiryTime = tokenExpiryTime;
    }

    public StroomDuration getEmailResetTokenExpiryTime() {
        return emailResetTokenExpiryTime;
    }

    public void setEmailResetTokenExpiryTime(final StroomDuration emailResetTokenExpiryTime) {
        this.emailResetTokenExpiryTime = emailResetTokenExpiryTime;
    }

    public StroomDuration getDefaultApiKeyExpiryTime() {
        return defaultApiKeyExpiryTime;
    }

    public void setDefaultApiKeyExpiryTime(final StroomDuration defaultApiKeyExpiryTime) {
        this.defaultApiKeyExpiryTime = defaultApiKeyExpiryTime;
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
