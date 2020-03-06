package stroom.security.impl;

import stroom.util.shared.IsConfig;

public class OpenIdConfig implements IsConfig {
    private static final String ISSUER = "accounts.google.com";
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://accounts.google.com/o/oauth2/token";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private String issuer = ISSUER;
    private String authEndpoint = AUTH_ENDPOINT;
    private String tokenEndpoint = TOKEN_ENDPOINT;
    private String jwksUri = JWKS_URI;

    private String clientId = "255179628077-rfa18bkalfl26rirpktghcgmt602mtuh.apps.googleusercontent.com";
    private String clientSecret = "9B6fidUVXdoSMm-bEVrvKud3";
    private String redirectUri = "http://localhost:8080/stroom/ui";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(final String issuer) {
        this.issuer = issuer;
    }

    public String getAuthEndpoint() {
        return authEndpoint;
    }

    public void setAuthEndpoint(final String authEndpoint) {
        this.authEndpoint = authEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(final String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(final String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(final String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
                "issuer='" + issuer + '\'' +
                ", authEndpoint='" + authEndpoint + '\'' +
                ", tokenEndpoint='" + tokenEndpoint + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", redirectUri='" + redirectUri + '\'' +
                '}';
    }
}