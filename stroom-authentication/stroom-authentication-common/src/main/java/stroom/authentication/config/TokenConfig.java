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

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class TokenConfig extends AbstractConfig {

    private int minutesUntilExpirationForUserToken = 43200;
    private int minutesUntilExpirationForEmailResetToken = 5;
    private String jwsIssuer = "stroom";
    private boolean requireExpirationTime = false;
    private String algorithm = "RS256";

    @Min(0)
    @JsonProperty
    public int getMinutesUntilExpirationForUserToken() {
        return minutesUntilExpirationForUserToken;
    }

    @SuppressWarnings("unused")
    public void setMinutesUntilExpirationForUserToken(int minutesUntilExpirationForUserToken) {
        this.minutesUntilExpirationForUserToken = minutesUntilExpirationForUserToken;
    }

    @Min(0)
    @JsonProperty
    public int getMinutesUntilExpirationForEmailResetToken() {
        return minutesUntilExpirationForEmailResetToken;
    }

    @SuppressWarnings("unused")
    public void setMinutesUntilExpirationForEmailResetToken(int minutesUntilExpirationForEmailResetToken) {
        this.minutesUntilExpirationForEmailResetToken = minutesUntilExpirationForEmailResetToken;
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
