package stroom.security.impl;

import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.openid.api.OpenIdConfig;
import stroom.util.NullSafe;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

///**
// * A front for {@link InternalJwtContextFactory} and {@link StandardJwtContextFactory}
// * that always tries the {@link InternalJwtContextFactory} first in case it is a processing
// * user request which always uses the internal idp. It also takes into account whether
// * an external IDP is in use.
// */
/**
 * A front for {@link InternalJwtContextFactory} and {@link StandardJwtContextFactory}
 * that picks the delegate based on the identity provider that has been configured, e.g.
 * internal vs external IDP.
 */
public class DelegatingJwtContextFactory implements JwtContextFactory {

    private final InternalJwtContextFactory internalJwtContextFactory;
    private final StandardJwtContextFactory standardJwtContextFactory;
    private final Provider<OpenIdConfig> openIdConfigProvider;

    @Inject
    public DelegatingJwtContextFactory(final InternalJwtContextFactory internalJwtContextFactory,
                                       final StandardJwtContextFactory standardJwtContextFactory,
                                       final Provider<OpenIdConfig> openIdConfigProvider) {
        this.internalJwtContextFactory = internalJwtContextFactory;
        this.standardJwtContextFactory = standardJwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
    }

    @Override
    public boolean hasToken(final HttpServletRequest request) {
        return getDelegate().hasToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        if (NullSafe.hasEntries(headers)) {
            getDelegate().removeAuthorisationEntries(headers);
        }
    }

    @Override
    public Map<String, String> createAuthorisationEntries(final String jwt) {
        return getDelegate().createAuthorisationEntries(jwt);
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        return getDelegate().getJwtContext(request);
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jwt) {
        return getDelegate().getJwtContext(jwt);
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jwt, final boolean doVerification) {
        return getDelegate().getJwtContext(jwt, doVerification);
    }

    private JwtContextFactory getDelegate() {
        return switch (openIdConfigProvider.get().getIdentityProviderType()) {
            case INTERNAL, TEST ->
                    internalJwtContextFactory;
            case EXTERNAL ->
                    standardJwtContextFactory;
        };
    }
}
