package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    public TokenResponse(@JsonProperty("id_token") final String idToken,
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
}
