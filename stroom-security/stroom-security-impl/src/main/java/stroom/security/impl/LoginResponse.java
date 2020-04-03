package stroom.security.impl;

public class LoginResponse {
    private final boolean authenticated;
    private final String redirectUri;

    public LoginResponse(final boolean authenticated,
                         final String redirectUri) {
        this.authenticated = authenticated;
        this.redirectUri = redirectUri;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}
