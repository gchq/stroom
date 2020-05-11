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

import javax.validation.constraints.NotNull;

public class TokenConfig extends AbstractConfig {

    private StroomDuration timeUntilExpirationForUserToken = StroomDuration.ofDays(30);
    private StroomDuration timeUntilExpirationForEmailResetToken = StroomDuration.ofMinutes(5);
    private String jwsIssuer = "stroom";
    private boolean requireExpirationTime = false;
    private String algorithm = "RS256";

    @NotNull
    @JsonProperty
    public StroomDuration getTimeUntilExpirationForUserToken() {
        return timeUntilExpirationForUserToken;
    }

    @SuppressWarnings("unused")
    public void setTimeUntilExpirationForUserToken(final StroomDuration timeUntilExpirationForUserToken) {
        this.timeUntilExpirationForUserToken = timeUntilExpirationForUserToken;
    }

    @NotNull
    @JsonProperty
    public StroomDuration getTimeUntilExpirationForEmailResetToken() {
        return timeUntilExpirationForEmailResetToken;
    }

    @SuppressWarnings("unused")
    public void setTimeUntilExpirationForEmailResetToken(final StroomDuration timeUntilExpirationForEmailResetToken) {
        this.timeUntilExpirationForEmailResetToken = timeUntilExpirationForEmailResetToken;
    }

    @NotNull
    @JsonProperty
    public String getJwsIssuer() {
        return jwsIssuer;
    }

    @SuppressWarnings("unused")
    public void setJwsIssuer(String jwsIssuer) {
        this.jwsIssuer = jwsIssuer;
    }

    @NotNull
    @JsonProperty
    @SuppressWarnings("unused")
    public boolean isRequireExpirationTime() {
        return requireExpirationTime;
    }

    @SuppressWarnings("unused")
    public void setRequireExpirationTime(boolean requireExpirationTime) {
        this.requireExpirationTime = requireExpirationTime;
    }

    @NotNull
    @JsonProperty
    public String getAlgorithm() {
        return algorithm;
    }

    @SuppressWarnings("unused")
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
