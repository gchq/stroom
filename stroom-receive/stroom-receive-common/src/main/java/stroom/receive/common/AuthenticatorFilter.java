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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface AuthenticatorFilter {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticatorFilter.class);

    /**
     * Filter that returns no identity, i.e. the request fails authentication.
     */
    AuthenticatorFilter NOT_AUTHENTICATED_FILTER = (request, attributeMap) ->
            Optional.empty();

    /**
     * Implementations may throw a {@link StroomStreamException} only if they are certain
     * that the client is trying to use the authentication method of the impl, e.g.
     * there is an Authorization Bearer token that matches the data feed key pattern.
     * However, if say the token is 'foo' then this may be valid for another {@link AuthenticatorFilter}
     * so an empty {@link Optional} should be returned.
     */
    Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                        final AttributeMap attributeMap);

    static AuthenticatorFilter wrap(final AuthenticatorFilter... attributeMapFilters) {
        return wrap(NullSafe.asList(attributeMapFilters));
    }

    static AuthenticatorFilter wrap(final List<AuthenticatorFilter> attributeMapFilters) {
        if (NullSafe.isEmptyCollection(attributeMapFilters)) {
            LOGGER.debug("Returning permissive instance");
            return NOT_AUTHENTICATED_FILTER;
        } else if (attributeMapFilters.size() == 1) {
            final AuthenticatorFilter first = NullSafe.first(attributeMapFilters);
            if (first != null) {
                LOGGER.debug(() -> "Returning " + first.getClass().getSimpleName());
                return first;
            } else {
                LOGGER.debug("Returning permissive instance");
                return NOT_AUTHENTICATED_FILTER;
            }
        } else {
            final MultiAuthenticatorFilter filter = new MultiAuthenticatorFilter(attributeMapFilters);
            LOGGER.debug("Returning {}", filter);
            return filter;
        }
    }


    // --------------------------------------------------------------------------------


    class MultiAuthenticatorFilter implements AuthenticatorFilter {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MultiAuthenticatorFilter.class);

        private final List<AuthenticatorFilter> authenticatorFilters;

        private MultiAuthenticatorFilter(final List<AuthenticatorFilter> authenticatorFilters) {
            if (NullSafe.isEmptyCollection(authenticatorFilters)) {
                throw new IllegalArgumentException("Null or empty authenticatorFilters");
            }
            this.authenticatorFilters = authenticatorFilters;
        }

        @Override
        public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                                   final AttributeMap attributeMap) {
            for (final AuthenticatorFilter authenticatorFilter : authenticatorFilters) {
                if (authenticatorFilter != null) {
                    LOGGER.debug(() -> "Calling authenticate on " + authenticatorFilter.getClass().getName());
                    final Optional<UserIdentity> optUserIdentity = authenticatorFilter.authenticate(
                            request, attributeMap);
                    if (optUserIdentity.isPresent()) {
                        return optUserIdentity;
                    }
                }
            }
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "Filters: " + authenticatorFilters.stream()
                    .map(AuthenticatorFilter::getClass)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", "));
        }
    }
}
