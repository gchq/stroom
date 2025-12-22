/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.common.impl;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

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
