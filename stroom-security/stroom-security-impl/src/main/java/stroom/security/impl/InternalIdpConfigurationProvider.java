package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.NullSafe;
import stroom.util.shared.ResourcePaths;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class InternalIdpConfigurationProvider implements IdpConfigurationProvider {

    public static final String INTERNAL_ISSUER = "stroom";
    // These paths must tally up with those in stroom.security.identity.openid.OpenIdResource
    private static final String OAUTH2_BASE_PATH = "/oauth2/v1/noauth";
    public static final String INTERNAL_AUTH_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/auth");
    public static final String INTERNAL_TOKEN_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/token");
    public static final String INTERNAL_JWKS_URI = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/certs");

    public static final String DEFAULT_REQUEST_SCOPE = "" +
            OpenId.SCOPE__OPENID +
            " " +
            OpenId.SCOPE__EMAIL;

    private final UriFactory uriFactory;
    private final Provider<OpenIdConfig> openIdConfigProvider;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    private volatile String lastConfigurationEndpoint;
    private volatile OpenIdConfigurationResponse openIdConfigurationResp;

    @Inject
    public InternalIdpConfigurationProvider(final UriFactory uriFactory,
                                            final Provider<OpenIdConfig> openIdConfigProvider,
                                            final OpenIdClientFactory openIdClientDetailsFactory) {
        this.uriFactory = uriFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();
        if (isNewResponseRequired(configurationEndpoint)) {
            synchronized (this) {
                if (isNewResponseRequired(configurationEndpoint)) {
                    openIdConfigurationResp = OpenIdConfigurationResponse.builder()
                            .issuer(INTERNAL_ISSUER)
                            .authorizationEndpoint(uriFactory.publicUri(INTERNAL_AUTH_ENDPOINT).toString())
                            .tokenEndpoint(uriFactory.nodeUri(INTERNAL_TOKEN_ENDPOINT).toString())
                            .jwksUri(uriFactory.nodeUri(INTERNAL_JWKS_URI).toString())
                            .logoutEndpoint(uriFactory.publicUri(INTERNAL_LOGOUT_ENDPOINT).toString())
                            .build();
                    lastConfigurationEndpoint = configurationEndpoint;
                }
            }
        }

        return openIdConfigurationResp;
    }

    private boolean isNewResponseRequired(final String configurationEndpoint) {
        return openIdConfigurationResp == null
                || !Objects.equals(lastConfigurationEndpoint, configurationEndpoint);
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigProvider.get().getOpenIdConfigurationEndpoint();
    }

    @Override
    public String getClientId() {
        return openIdClientDetailsFactory.getClient().getClientId();
    }

    @Override
    public String getClientSecret() {
        return openIdClientDetailsFactory.getClient().getClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        // Always true for internal idp
        return true;
    }

    @Override
    public String getRequestScope() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        return NullSafe.isBlankString(openIdConfig.getRequestScope())
                ? DEFAULT_REQUEST_SCOPE
                : openIdConfig.getRequestScope();
    }

    @Override
    public boolean isValidateAudience() {
        return openIdConfigProvider.get().isValidateAudience();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return openIdConfigProvider.get().getLogoutRedirectParamName();
    }
}
