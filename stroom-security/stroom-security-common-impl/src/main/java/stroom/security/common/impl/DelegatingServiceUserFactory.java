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

        // TODO: 17/10/2023 Need config for this
        final boolean useCertAuthForServiceUsers = true;
        if (useCertAuthForServiceUsers) {
            delegate = new CertificateServiceUserFactory();
        } else {
            delegate = delegates.get(openIdConfiguration.getIdentityProviderType());
            if (delegate == null) {
                throw new RuntimeException(LogUtil.message("{} has no {} implementation.",
                        openIdConfiguration.getIdentityProviderType(),
                        ServiceUserFactory.class.getSimpleName()));
            }
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

    private static class CertificateServiceUserFactory implements ServiceUserFactory {

        @Override
        public UserIdentity createServiceUserIdentity() {
            return CertificateServiceUserIdentity.INSTANCE;
        }

        @Override
        public boolean isServiceUser(final UserIdentity userIdentity, final UserIdentity serviceUserIdentity) {
            return userIdentity instanceof CertificateServiceUserIdentity
                    && userIdentity == serviceUserIdentity;
        }
    }


    // --------------------------------------------------------------------------------


    private static class CertificateServiceUserIdentity implements UserIdentity {

        private static final String SUBJECT_ID = "stroom-internal-processing-user";
        private static final UserIdentity INSTANCE = new CertificateServiceUserIdentity();

        @Override
        public String getSubjectId() {
            return SUBJECT_ID;
        }

        @Override
        public boolean equals(final Object obj) {
            return super.equals(obj);
        }
    }
}
