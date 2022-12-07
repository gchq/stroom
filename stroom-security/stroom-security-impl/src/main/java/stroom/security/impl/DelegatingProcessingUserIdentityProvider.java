package stroom.security.impl;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfiguration.IdpType;

import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Delegates to an implementation of ProcessingUserIdentityProvider depending on the type of
 * identity provider that is configured, e.g. internal vs external.
 */
public class DelegatingProcessingUserIdentityProvider implements ProcessingUserIdentityProvider {

    private final Map<IdpType, ProcessingUserIdentityProvider> delegates;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;

    @Inject // MapBinder injection
    public DelegatingProcessingUserIdentityProvider(final Map<IdpType, ProcessingUserIdentityProvider> delegates,
                                                    final Provider<OpenIdConfiguration> openIdConfigurationProvider) {
        this.delegates = delegates;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
    }

    @Override
    public UserIdentity get() {
        return getDelegate().get();
    }

    @Override
    public boolean isProcessingUser(final UserIdentity userIdentity) {
        return getDelegate().isProcessingUser(userIdentity);
    }

    @Override
    public boolean isProcessingUser(final String subject, final String issuer) {
        return getDelegate().isProcessingUser(subject, issuer);
    }

    private ProcessingUserIdentityProvider getDelegate() {
        final IdpType idpType = openIdConfigurationProvider.get().getIdentityProviderType();
        final ProcessingUserIdentityProvider delegate = delegates.get(idpType);

        Objects.requireNonNull(delegate, () -> "No ProcessingUserIdentityProvider binding for type " + idpType);
        return delegate;
    }
}
