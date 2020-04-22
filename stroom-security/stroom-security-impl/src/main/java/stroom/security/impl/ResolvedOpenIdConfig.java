package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;

public class ResolvedOpenIdConfig {
    public static final String INTERNAL_ISSUER = "stroom";
    // These paths must tally up with those in stroom.authentication.oauth2.OAuth2Resource
    private static final String OAUTH2_BASE_PATH = "/oauth2/v1/noauth";
    public static final String INTERNAL_AUTH_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/auth");
    public static final String INTERNAL_TOKEN_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/token");
    public static final String INTERNAL_JWKS_URI = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/certs");

    private final UriFactory uriFactory;
    private final OpenIdConfig openIdConfig;
    private final OpenIdClientDetailsFactory openIdClientDetailsFactory;

    @Inject
    public ResolvedOpenIdConfig(final UriFactory uriFactory,
                                final OpenIdConfig openIdConfig,
                                final OpenIdClientDetailsFactory openIdClientDetailsFactory) {
        this.uriFactory = uriFactory;
        this.openIdConfig = openIdConfig;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    public String getIssuer() {
        if (openIdConfig.isUseInternal()) {
            return INTERNAL_ISSUER;
        }
        return openIdConfig.getIssuer();
    }

    public String getAuthEndpoint() {
        if (openIdConfig.isUseInternal()) {
            // This needs to be the public Uri as it will be used in the browser
            return uriFactory.publicUri(INTERNAL_AUTH_ENDPOINT).toString();
        }
        return openIdConfig.getAuthEndpoint();
    }

    public String getTokenEndpoint() {
        if (openIdConfig.isUseInternal()) {
            return uriFactory.localUri(INTERNAL_TOKEN_ENDPOINT).toString();
        }
        return openIdConfig.getTokenEndpoint();
    }

    public String getJwksUri() {
        if (openIdConfig.isUseInternal()) {
            return uriFactory.localUri(INTERNAL_JWKS_URI).toString();
        }
        return openIdConfig.getJwksUri();
    }

    public String getClientId() {
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetailsFactory.getClientId();
        }
        return openIdConfig.getClientId();
    }

    public String getClientSecret() {
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetailsFactory.getClientSecret();
        }
        return openIdConfig.getClientSecret();
    }
}
