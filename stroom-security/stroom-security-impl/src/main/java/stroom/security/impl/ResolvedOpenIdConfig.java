package stroom.security.impl;

import stroom.config.common.UriFactory;

import javax.inject.Inject;

public class ResolvedOpenIdConfig {
    public static final String INTERNAL_ISSUER = "stroom";
    public static final String INTERNAL_AUTH_ENDPOINT = "/api/oauth2/v1/noauth/auth";
    public static final String INTERNAL_TOKEN_ENDPOINT = "/api/oauth2/v1/noauth/token";
    public static final String INTERNAL_JWKS_URI = "/api/oauth2/v1/noauth/certs";

    private final UriFactory uriFactory;
    private final OpenIdConfig openIdConfig;
    private final OpenIdClientDetails openIdClientDetails;

    @Inject
    public ResolvedOpenIdConfig(final UriFactory uriFactory,
                                final OpenIdConfig openIdConfig,
                                final OpenIdClientDetails openIdClientDetails) {
        this.uriFactory = uriFactory;
        this.openIdConfig = openIdConfig;
        this.openIdClientDetails = openIdClientDetails;
    }

    public String getIssuer() {
        if (openIdConfig.isUseInternal()) {
            return INTERNAL_ISSUER;
        }
        return openIdConfig.getIssuer();
    }

    public String getAuthEndpoint() {
        if (openIdConfig.isUseInternal()) {
            return uriFactory.publicUriString(INTERNAL_AUTH_ENDPOINT);
        }
        return openIdConfig.getAuthEndpoint();
    }

    public String getTokenEndpoint() {
        if (openIdConfig.isUseInternal()) {
            return uriFactory.localUriString(INTERNAL_TOKEN_ENDPOINT);
        }
        return openIdConfig.getTokenEndpoint();
    }

    public String getJwksUri() {
        if (openIdConfig.isUseInternal()) {
            return uriFactory.publicUriString(INTERNAL_JWKS_URI);
        }
        return openIdConfig.getJwksUri();
    }

    public String getClientId() {
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetails.getClientId();
        }
        return openIdConfig.getClientId();
    }

    public String getClientSecret() {
        if (openIdConfig.isUseInternal()) {
            openIdClientDetails.getClientSecret();
        }
        return openIdConfig.getClientSecret();
    }
}
