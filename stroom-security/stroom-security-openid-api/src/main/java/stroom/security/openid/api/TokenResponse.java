package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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

    @JsonProperty("refresh_token_expires_in")
    private final Long refreshTokenExpiresIn;

    @JsonProperty("token_type")
    private final String tokenType;

    @JsonCreator
    TokenResponse(@JsonProperty("id_token") final String idToken,
                  @JsonProperty("access_token") final String accessToken,
                  @JsonProperty("refresh_token") final String refreshToken,
                  @JsonProperty("expires_in") final Long expiresIn,
                  @JsonProperty("refresh_token_expires_in") final Long refreshTokenExpiresIn,
                  @JsonProperty("token_type") final String tokenType) {
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
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

    public Long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
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
                ", accessToken='" + accessToken + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                ", expiresIn=" + expiresIn +
                ", refreshTokenExpiresIn=" + refreshTokenExpiresIn +
                ", tokenType='" + tokenType + '\'' +
                '}';
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String idToken;
        private String accessToken;
        private String refreshToken;
        private Long expiresIn;
        private Long refreshTokenExpiresIn;
        private String tokenType;

        private Builder() {
        }

        private Builder(final TokenResponse tokenResponse) {
            this.idToken = tokenResponse.idToken;
            this.accessToken = tokenResponse.accessToken;
            this.refreshToken = tokenResponse.refreshToken;
            this.expiresIn = tokenResponse.expiresIn;
            this.refreshTokenExpiresIn = tokenResponse.refreshTokenExpiresIn;
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
                    tokenType);
        }
    }
}
