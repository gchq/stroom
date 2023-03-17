package stroom.security.openid.api;

import java.util.List;

/**
 * Defines the configuration required to interact with an Open ID Connect IDP
 */
public interface OpenIdConfiguration {

    /**
     * @see AbstractOpenIdConfig#getIdentityProviderType()
     */
    IdpType getIdentityProviderType();

    /**
     * @see AbstractOpenIdConfig#getOpenIdConfigurationEndpoint()
     */
    String getOpenIdConfigurationEndpoint();

    /**
     * @see AbstractOpenIdConfig#getIssuer()
     */
    String getIssuer();

    /**
     * @see AbstractOpenIdConfig#getAuthEndpoint()
     */
    String getAuthEndpoint();

    /**
     * @see AbstractOpenIdConfig#getTokenEndpoint()
     */
    String getTokenEndpoint();

    /**
     * @see AbstractOpenIdConfig#getJwksUri()
     */
    String getJwksUri();

    /**
     * @see AbstractOpenIdConfig#getLogoutEndpoint()
     */
    String getLogoutEndpoint();

    /**
     * @see AbstractOpenIdConfig#getClientId()
     */
    String getClientId();

    /**
     * @see AbstractOpenIdConfig#getClientSecret()
     */
    String getClientSecret();

    /**
     * @see AbstractOpenIdConfig#isFormTokenRequest()
     */
    boolean isFormTokenRequest();

    /**
     * @see AbstractOpenIdConfig#getRequestScopes()
     */
    List<String> getRequestScopes();

    /**
     * @see AbstractOpenIdConfig#getClientCredentialsScopes()
     */
    List<String> getClientCredentialsScopes();

    /**
     * @see AbstractOpenIdConfig#isValidateAudience()
     */
    boolean isValidateAudience();

    /**
     * @see AbstractOpenIdConfig#getUniqueIdentityClaim()
     */
    String getUniqueIdentityClaim();

    /**
     * @see AbstractOpenIdConfig#getLogoutRedirectParamName()
     */
    String getLogoutRedirectParamName();
}
