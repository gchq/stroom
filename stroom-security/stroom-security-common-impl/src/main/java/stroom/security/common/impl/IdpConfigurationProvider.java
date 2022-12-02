package stroom.security.common.impl;

import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfigurationResponse;

/**
 * An abstraction on the Open ID Connect configuration.
 * Implementations may get the values from an external IDP or the internal
 * IDP depending on whether this is stroom or proxy and the local configuration.
 */
public interface IdpConfigurationProvider extends OpenIdConfiguration {

    /**
     * Get the configuration response from the Open ID Connect identity provider
     */
    OpenIdConfigurationResponse getConfigurationResponse();

    @Override
    default String getIssuer() {
        return getConfigurationResponse().getIssuer();
    }

    @Override
    default String getAuthEndpoint() {
        return getConfigurationResponse().getAuthorizationEndpoint();
    }

    @Override
    default String getTokenEndpoint() {
        return getConfigurationResponse().getTokenEndpoint();
    }

    @Override
    default String getJwksUri() {
        return getConfigurationResponse().getJwksUri();
    }

    @Override
    default String getLogoutEndpoint() {
        return getConfigurationResponse().getLogoutEndpoint();
    }
}
