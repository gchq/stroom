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

package stroom.security.impl;

import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.openid.api.IdpType;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;

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
    private final Provider<StroomOpenIdConfig> openIdConfigProvider;

    @Inject
    public DelegatingJwtContextFactory(final InternalJwtContextFactory internalJwtContextFactory,
                                       final StandardJwtContextFactory standardJwtContextFactory,
                                       final Provider<StroomOpenIdConfig> openIdConfigProvider) {
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
            case INTERNAL_IDP, TEST_CREDENTIALS -> internalJwtContextFactory;
            case EXTERNAL_IDP -> standardJwtContextFactory;
            case NO_IDP ->
                    throw new UnsupportedOperationException("No JwtContextFactory when IDP type is " + IdpType.NO_IDP);
        };
    }
}
