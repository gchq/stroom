package stroom.security.impl;

import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfiguration.IdpType;
import stroom.util.NullSafe;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

/**
 * A front for {@link InternalJwtContextFactory} and {@link StandardJwtContextFactory}
 * that always tries the {@link InternalJwtContextFactory} first in case it is a processing
 * user request which always uses the internal idp. It also takes into account whether
 * an external IDP is in use.
 */
public class CombinedJwtContextFactory implements JwtContextFactory {

    private final InternalJwtContextFactory internalJwtContextFactory;
    private final StandardJwtContextFactory standardJwtContextFactory;
    private final Provider<OpenIdConfig> openIdConfigProvider;

    @Inject
    public CombinedJwtContextFactory(final InternalJwtContextFactory internalJwtContextFactory,
                                     final StandardJwtContextFactory standardJwtContextFactory,
                                     final Provider<OpenIdConfig> openIdConfigProvider) {
        this.internalJwtContextFactory = internalJwtContextFactory;
        this.standardJwtContextFactory = standardJwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
    }

    @Override
    public boolean hasToken(final HttpServletRequest request) {
        return internalJwtContextFactory.hasToken(request)
                || (useExternalIdentityProvider() && standardJwtContextFactory.hasToken(request));
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        if (NullSafe.hasEntries(headers)) {
            internalJwtContextFactory.removeAuthorisationEntries(headers);
            if (useExternalIdentityProvider()) {
                standardJwtContextFactory.removeAuthorisationEntries(headers);
            }
        }
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        // Always try the internal context factory first as the processing user only
        // uses the internal IDP
        // TODO: 02/12/2022 Use a service account on the ext idp for proc user
        return internalJwtContextFactory.getJwtContext(request)
                .or(() -> useExternalIdentityProvider()
                        ? standardJwtContextFactory.getJwtContext(request)
                        : Optional.empty());
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jwt) {
        // Always try the internal context factory first as the processing user only
        // uses the internal IDP
        return internalJwtContextFactory.getJwtContext(jwt)
                .or(() -> useExternalIdentityProvider()
                        ? standardJwtContextFactory.getJwtContext(jwt)
                        : Optional.empty());
    }

    private boolean useExternalIdentityProvider() {
        return IdpType.EXTERNAL.equals(openIdConfigProvider.get().getIdentityProviderType());
    }
}
