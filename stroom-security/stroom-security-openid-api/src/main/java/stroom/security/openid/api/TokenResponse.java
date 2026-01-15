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

package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class TokenResponse {

    @JsonProperty("id_token")
    private final String idToken;

    @JsonProperty("access_token")
    private final String accessToken;

    @JsonProperty("refresh_token")
    private final String refreshToken;

    /**
     * RECOMMENDED.  The lifetime in seconds of the access token.  For
     * example, the value "3600" denotes that the access token will
     * expire in one hour from the time the response was generated.
     * If omitted, the authorization server SHOULD provide the
     * expiration time via other means or document the default value.
     */
    @JsonProperty("expires_in")
    private final Long expiresIn;

    // This seems to be provided by AWS/AD
    @JsonProperty("refresh_token_expires_in")
    private final Long refreshTokenExpiresIn;

    // This seems to be provided by Keycloak
    @JsonProperty("refresh_expires_in")
    private final Long refreshExpiresIn;

    @JsonProperty("token_type")
    private final String tokenType;

    @JsonCreator
    TokenResponse(@JsonProperty("id_token") final String idToken,
                  @JsonProperty("access_token") final String accessToken,
                  @JsonProperty("refresh_token") final String refreshToken,
                  @JsonProperty("expires_in") final Long expiresIn,
                  @JsonProperty("refresh_token_expires_in") final Long refreshTokenExpiresIn,
                  @JsonProperty("refresh_expires_in") final Long refreshExpiresIn,
                  @JsonProperty("token_type") final String tokenType) {
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
        this.tokenType = tokenType;
    }

    public String getIdToken() {
        return idToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Use {@link TokenResponse#getEffectiveRefreshExpiresIn()}
     */
    @Deprecated // Here for serialisation
    public Long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }

    /**
     * Use {@link TokenResponse#getEffectiveRefreshExpiresIn()}
     */
    @Deprecated // Here for serialisation
    public Long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    /**
     * @return The time until the refresh token expires. Provides the value of 'refresh_token_expires_in' or
     * if that is null 'refresh_expires_in'. This is to support different IDPs that seem to use different
     * property names.
     */
    @JsonIgnore
    public Long getEffectiveRefreshExpiresIn() {
        return Objects.requireNonNullElse(refreshTokenExpiresIn, refreshExpiresIn);
    }

    public String getTokenType() {
        return tokenType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "TokenResponse{" +
                "idToken='" + idToken + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", refreshTokenExpiresIn=" + refreshTokenExpiresIn +
                ", accessToken='" + accessToken + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                '}';
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String idToken;
        private String accessToken;
        private String refreshToken;
        private Long expiresIn;
        private Long refreshTokenExpiresIn;
        private Long refreshExpiresIn;
        private String tokenType;

        private Builder() {
        }

        private Builder(final TokenResponse tokenResponse) {
            this.idToken = tokenResponse.idToken;
            this.accessToken = tokenResponse.accessToken;
            this.refreshToken = tokenResponse.refreshToken;
            this.expiresIn = tokenResponse.expiresIn;
            this.refreshTokenExpiresIn = tokenResponse.refreshTokenExpiresIn;
            this.refreshExpiresIn = tokenResponse.refreshExpiresIn;
            this.tokenType = tokenResponse.tokenType;
        }

        public Builder idToken(final String idToken) {
            this.idToken = idToken;
            return this;
        }

        public Builder accessToken(final String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(final String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expiresIn(final Long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder refreshTokenExpiresIn(final Long refreshTokenExpiresIn) {
            this.refreshTokenExpiresIn = refreshTokenExpiresIn;
            return this;
        }

        public Builder refreshExpiresIn(final Long refreshExpiresIn) {
            this.refreshExpiresIn = refreshExpiresIn;
            return this;
        }

        public Builder tokenType(final String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public TokenResponse build() {
            return new TokenResponse(
                    idToken,
                    accessToken,
                    refreshToken,
                    expiresIn,
                    refreshTokenExpiresIn,
                    refreshExpiresIn,
                    tokenType);
        }
    }
}
