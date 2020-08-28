package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty(OpenId.CODE)
    private final String code;

    @JsonCreator
    TokenRequest(@JsonProperty(OpenId.GRANT_TYPE) final String grantType,
                        @JsonProperty(OpenId.CLIENT_ID) final String clientId,
                        @JsonProperty(OpenId.CLIENT_SECRET) final String clientSecret,
                        @JsonProperty(OpenId.REDIRECT_URI) final String redirectUri,
                        @JsonProperty(OpenId.CODE) final String code) {
        this.grantType = grantType;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.code = code;
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

    public String getCode() {
        return code;
    }

    public static class Builder {
        private String grantType;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String code;

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

        public Builder code(final String code) {
            this.code = code;
            return this;
        }

        public TokenRequest build() {
            return new TokenRequest(
                    grantType,
                    clientId,
                    clientSecret,
                    redirectUri,
                    code);
        }
    }
}
