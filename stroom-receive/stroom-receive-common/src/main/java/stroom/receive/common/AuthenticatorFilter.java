package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;

public interface AuthenticatorFilter {

    /**
     * Filter that returns no identity, i.e. the request fails authentication.
     */
    AuthenticatorFilter NOT_AUTHENTICATED_FILTER = (request, attributeMap) ->
            Optional.empty();

    Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                        final AttributeMap attributeMap);

    static AuthenticatorFilter wrap(final AuthenticatorFilter... attributeMapFilters) {
        return wrap(NullSafe.asList(attributeMapFilters));
    }

    static AuthenticatorFilter wrap(final List<AuthenticatorFilter> attributeMapFilters) {
        if (NullSafe.isEmptyCollection(attributeMapFilters)) {
            return NOT_AUTHENTICATED_FILTER;
        } else if (attributeMapFilters.size() == 1 && attributeMapFilters.get(0) != null) {
            return attributeMapFilters.get(0);
        } else {
            return new MultiAuthenticatorFilter(attributeMapFilters);
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
    }
}
