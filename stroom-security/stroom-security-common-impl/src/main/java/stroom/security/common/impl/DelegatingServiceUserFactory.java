package stroom.security.common.impl;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.logging.LogUtil;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Delegates to the appropriate {@link ServiceUserFactory} based on what {@link ServiceUserFactory}
 * implementations are bound.
 */
public class DelegatingServiceUserFactory implements ServiceUserFactory {

    private final ServiceUserFactory delegate;

    // MapBinder
    @Inject
    public DelegatingServiceUserFactory(
            final Provider<OpenIdConfiguration> openIdConfigurationProvider,
            final Map<IdpType, ServiceUserFactory> delegates) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();

        delegate = delegates.get(openIdConfiguration.getIdentityProviderType());
        if (delegate == null) {
            throw new RuntimeException(LogUtil.message("{} has no {} implementation.",
                    openIdConfiguration.getIdentityProviderType(),
                    ServiceUserFactory.class.getSimpleName()));
        }
    }

    @Override
    public UserIdentity createServiceUserIdentity() {
        return delegate.createServiceUserIdentity();
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        return delegate.isServiceUser(userIdentity, serviceUserIdentity);
    }
}
