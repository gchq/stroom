package stroom.security;

public class SecurityConfig {
    private String authenticationServiceUrl;
    private String advertisedStroomUrl;
    private boolean authenticationRequired = true;
    private String apiToken;
    private String authServicesBaseUrl;
    private JwtConfig jwtConfig;

    public String getAuthenticationServiceUrl() {
        return authenticationServiceUrl;
    }

    public void setAuthenticationServiceUrl(final String authenticationServiceUrl) {
        this.authenticationServiceUrl = authenticationServiceUrl;
    }

    public String getAdvertisedStroomUrl() {
        return advertisedStroomUrl;
    }

    public void setAdvertisedStroomUrl(final String advertisedStroomUrl) {
        this.advertisedStroomUrl = advertisedStroomUrl;
    }

    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    public void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(final String apiToken) {
        this.apiToken = apiToken;
    }

    public String getAuthServicesBaseUrl() {
        return authServicesBaseUrl;
    }

    public void setAuthServicesBaseUrl(final String authServicesBaseUrl) {
        this.authServicesBaseUrl = authServicesBaseUrl;
    }

    public JwtConfig getJwtConfig() {
        return jwtConfig;
    }

    public  void setJwtConfig(final JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public static class JwtConfig {
        private String jwtIssuer;
        private boolean enableTokenRevocationCheck;

        public String getJwtIssuer() {
            return jwtIssuer;
        }

        public void setJwtIssuer(final String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        public boolean isEnableTokenRevocationCheck() {
            return enableTokenRevocationCheck;
        }

        public void setEnableTokenRevocationCheck(final boolean enableTokenRevocationCheck) {
            this.enableTokenRevocationCheck = enableTokenRevocationCheck;
        }
    }
}
