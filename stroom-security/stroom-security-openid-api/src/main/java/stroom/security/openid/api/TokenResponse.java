package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class TokenResponse {
    @JsonProperty("id_token")
    private final String idToken;
    @JsonProperty("access_token")
    private final String accessToken;
    @JsonProperty("refresh_token")
    private final String refreshToken;
    @JsonProperty("expires_in")
    private final int expiresIn;
    @JsonProperty("token_type")
    private final String tokenType;

    @JsonCreator
    TokenResponse(@JsonProperty("id_token") final String idToken,
                  @JsonProperty("access_token") final String accessToken,
                  @JsonProperty("refresh_token") final String refreshToken,
                  @JsonProperty("expires_in") final int expiresIn,
                  @JsonProperty("token_type") final String tokenType) {
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
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

    public int getExpiresIn() {
        return expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public static class Builder {
        private String idToken;
        private String accessToken;
        private String refreshToken;
        private int expiresIn;
        private String tokenType;

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

        public Builder expiresIn(final int expiresIn) {
            this.expiresIn = expiresIn;
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
                    tokenType);
        }
    }
}
