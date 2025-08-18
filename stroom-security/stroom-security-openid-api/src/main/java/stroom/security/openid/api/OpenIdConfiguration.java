package stroom.security.openid.api;

import java.util.List;
import java.util.Set;

/**
 * Defines the configuration required to interact with an Open ID Connect IDP
 * <p>
 * Depending on the implementation, some of these values may come from the IDP's configuration
 * endpoint and some may come from stroom's config file OR they may all come from stroom's
 * config file.
 * <p>
 * If you need to be sure you are getting only values from Stroom's config file then inject
 * the Stroom or Proxy named implementations or {@link AbstractOpenIdConfig} directly. E.g.
 * any classes that are the implementation of our internal IDP should be using an instance of this
 * class that is only backed by the config file.
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
     * @see AbstractOpenIdConfig#isAudienceClaimRequired()
     */
    boolean isAudienceClaimRequired();

    /**
     * @see AbstractOpenIdConfig#getAllowedAudiences()
     */
    Set<String> getAllowedAudiences();

    /**
     * @see AbstractOpenIdConfig#getValidIssuers()
     */
    Set<String> getValidIssuers();

    /**
     * @see AbstractOpenIdConfig#getUniqueIdentityClaim()
     */
    String getUniqueIdentityClaim();

    /**
     * @see AbstractOpenIdConfig#getUserDisplayNameClaim()
     */
    String getUserDisplayNameClaim();

    /**
     * @see AbstractOpenIdConfig#getLogoutRedirectParamName()
     */
    String getLogoutRedirectParamName();

    /**
     * @see AbstractOpenIdConfig#getExpectedSignerPrefixes()
     */
    Set<String> getExpectedSignerPrefixes();

    /**
     * @see AbstractOpenIdConfig#getPublicKeyUriPattern()
     */
    String getPublicKeyUriPattern();
}
