package stroom.security.common.impl;

import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.shared.ResourcePaths;

public interface IdpConfigurationProvider extends OpenIdConfiguration {

    // These paths must tally up with those in
    // stroom.security.identity.authenticate.AuthenticationResource
    String AUTHENTICATION_BASE_PATH = "/authentication/v1/noauth";
    String INTERNAL_LOGOUT_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            AUTHENTICATION_BASE_PATH, "/logout");

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
