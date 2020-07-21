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

package stroom.authentication.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class TokenConfig extends AbstractConfig {

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before a user token will expire.")
    private StroomDuration timeUntilExpirationForUserToken = StroomDuration.ofDays(30);

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The time before an email reset token will expire.")
    private StroomDuration timeUntilExpirationForEmailResetToken = StroomDuration.ofMinutes(5);

    @JsonProperty
    @JsonPropertyDescription("The default API key expiry time")
    private Long defaultApiKeyExpiryInMinutes = 525600L;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The Issuer value used in Json Web Tokens.")
    private String jwsIssuer = "stroom";

    // TODO is this needed?
//    @NotNull
//    @JsonProperty
//    @JsonPropertyDescription("The Issuer value used in Json Web Tokens.")
//    private boolean requireExpirationTime = false;

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The cryptographic algorithm used in the Json Web Signatures. " +
            "Valid values can be found at https://openid.net/specs/draft-jones-json-web-signature-04.html#Signing")
    private String algorithm = "RS256";

    public StroomDuration getTimeUntilExpirationForUserToken() {
        return timeUntilExpirationForUserToken;
    }

    @SuppressWarnings("unused")
    public void setTimeUntilExpirationForUserToken(final StroomDuration timeUntilExpirationForUserToken) {
        this.timeUntilExpirationForUserToken = timeUntilExpirationForUserToken;
    }

    public StroomDuration getTimeUntilExpirationForEmailResetToken() {
        return timeUntilExpirationForEmailResetToken;
    }

    @SuppressWarnings("unused")
    public void setTimeUntilExpirationForEmailResetToken(final StroomDuration timeUntilExpirationForEmailResetToken) {
        this.timeUntilExpirationForEmailResetToken = timeUntilExpirationForEmailResetToken;
    }

    public Long getDefaultApiKeyExpiryInMinutes() {
        return defaultApiKeyExpiryInMinutes;
    }

    public void setDefaultApiKeyExpiryInMinutes(final Long defaultApiKeyExpiryInMinutes) {
        this.defaultApiKeyExpiryInMinutes = defaultApiKeyExpiryInMinutes;
    }

    public String getJwsIssuer() {
        return jwsIssuer;
    }

    @SuppressWarnings("unused")
    public void setJwsIssuer(String jwsIssuer) {
        this.jwsIssuer = jwsIssuer;
    }

//    public boolean isRequireExpirationTime() {
//        return requireExpirationTime;
//    }

//    @SuppressWarnings("unused")
//    public void setRequireExpirationTime(boolean requireExpirationTime) {
//        this.requireExpirationTime = requireExpirationTime;
//    }

    public String getAlgorithm() {
        return algorithm;
    }

    @SuppressWarnings("unused")
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
