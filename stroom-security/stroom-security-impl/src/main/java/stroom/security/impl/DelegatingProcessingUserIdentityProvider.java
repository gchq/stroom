package stroom.security.impl;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;

import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Delegates to an implementation of ProcessingUserIdentityProvider depending on the type of
 * identity provider that is configured, e.g. internal vs external.
 */
@Singleton
public class DelegatingProcessingUserIdentityProvider implements ProcessingUserIdentityProvider {

    private final ProcessingUserIdentityProvider delegate;

    @Inject // MapBinder injection
    public DelegatingProcessingUserIdentityProvider(final Map<IdpType, ProcessingUserIdentityProvider> delegates,
                                                    final Provider<AbstractOpenIdConfig> openIdConfigProvider) {
        // Bake in the delegate as changing the idp type requires a restart so no point
        // checking the config on every call
        final IdpType idpType = openIdConfigProvider.get().getIdentityProviderType();
        final ProcessingUserIdentityProvider delegate = delegates.get(idpType);

        Objects.requireNonNull(delegate, () -> "No ProcessingUserIdentityProvider binding for type " + idpType);
        this.delegate = delegate;
    }

    @Override
    public UserIdentity get() {
        return delegate.get();
    }

    @Override
    public boolean isProcessingUser(final UserIdentity userIdentity) {
        return delegate.isProcessingUser(userIdentity);
    }

    @Override
    public boolean isProcessingUser(final String subject, final String issuer) {
        return delegate.isProcessingUser(subject, issuer);
    }
}
