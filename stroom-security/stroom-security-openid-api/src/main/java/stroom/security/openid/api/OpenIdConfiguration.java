package stroom.security.openid.api;

/**
 * Defines the configuration required to interact with an Open ID Connect IDP
 */
public interface OpenIdConfiguration {

    IdpType getIdentityProviderType();

    String getOpenIdConfigurationEndpoint();

    String getIssuer();

    String getAuthEndpoint();

    String getTokenEndpoint();

    String getJwksUri();

    String getLogoutEndpoint();

    String getClientId();

    String getClientSecret();

    boolean isFormTokenRequest();

    String getRequestScope();

    boolean isValidateAudience();

    String getLogoutRedirectParamName();

    /**
     * The type of identity provider that stroom(-proxy) will use for authentication
     */
    enum IdpType {

        /**
         * Stroom's internal IDP. Not valid for stroom-proxy
         */
        INTERNAL,

        /**
         * An external IDP such as KeyCloak/Cognito
         */
        EXTERNAL,

        /**
         * Use hard-coded credentials for testing/demo only
         */
        TEST
    }
}
