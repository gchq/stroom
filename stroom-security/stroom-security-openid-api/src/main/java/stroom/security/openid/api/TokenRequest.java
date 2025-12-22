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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class TokenRequest {

    @JsonProperty(OpenId.GRANT_TYPE)
    private final String grantType;

    @JsonProperty(OpenId.CLIENT_ID)
    private final String clientId;

    @JsonProperty(OpenId.CLIENT_SECRET)
    private final String clientSecret;

    @JsonProperty(OpenId.REDIRECT_URI)
    private final String redirectUri;

    @JsonProperty(OpenId.REFRESH_TOKEN)
    private final String refreshToken;

    @JsonProperty(OpenId.CODE)
    private final String code;

    @JsonProperty(OpenId.SCOPE)
    private final String scope;

    @JsonCreator
    public TokenRequest(
            @JsonProperty(OpenId.GRANT_TYPE) final String grantType,
            @JsonProperty(OpenId.CLIENT_ID) final String clientId,
            @JsonProperty(OpenId.CLIENT_SECRET) final String clientSecret,
            @JsonProperty(OpenId.REDIRECT_URI) final String redirectUri,
            @JsonProperty(OpenId.REFRESH_TOKEN) final String refreshToken,
            @JsonProperty(OpenId.CODE) final String code,
            @JsonProperty(OpenId.SCOPE) final String scope) {

        this.grantType = grantType;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.refreshToken = refreshToken;
        this.code = code;
        this.scope = scope;
    }

    public String getGrantType() {
        return grantType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getCode() {
        return code;
    }

    public String getScope() {
        return scope;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "TokenRequest{" +
                "grantType='" + grantType + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", redirectUri='" + redirectUri + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                ", code='" + code + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String grantType;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String refreshToken;
        private String code;
        private String scope;

        private Builder() {
        }

        private Builder(final TokenRequest tokenRequest) {
            grantType = tokenRequest.grantType;
            clientId = tokenRequest.clientId;
            clientSecret = tokenRequest.clientSecret;
            redirectUri = tokenRequest.redirectUri;
            refreshToken = tokenRequest.refreshToken;
            code = tokenRequest.code;
        }

        public Builder grantType(final String grantType) {
            this.grantType = grantType;
            return this;
        }

        public Builder clientId(final String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(final String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder redirectUri(final String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        public Builder refreshToken(final String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder code(final String code) {
            this.code = code;
            return this;
        }

        public Builder scope(final String scope) {
            this.scope = scope;
            return this;
        }

        public TokenRequest build() {
            return new TokenRequest(
                    grantType,
                    clientId,
                    clientSecret,
                    redirectUri,
                    refreshToken,
                    code,
                    scope);
        }
    }
}
