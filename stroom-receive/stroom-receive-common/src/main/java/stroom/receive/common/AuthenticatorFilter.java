package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;

import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface AuthenticatorFilter {

    /**
     * Filter that returns no identity, i.e. the request fails authentication.
     */
    AuthenticatorFilter UNAUTHENTICATED_FILTER = (request, attributeMap) ->
            Optional.empty();

    Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                        final AttributeMap attributeMap);

    static AuthenticatorFilter wrap(final AuthenticatorFilter... attributeMapFilters) {
        return wrap(NullSafe.asList(attributeMapFilters));
    }

    static AuthenticatorFilter wrap(final List<AuthenticatorFilter> attributeMapFilters) {
        if (NullSafe.isEmptyCollection(attributeMapFilters)) {
            return UNAUTHENTICATED_FILTER;
        } else if (attributeMapFilters.size() == 1 && attributeMapFilters.get(0) != null) {
            return attributeMapFilters.get(0);
        } else {
            return new MultiAuthenticatorFilter(attributeMapFilters);
        }
    }


    // --------------------------------------------------------------------------------


    class MultiAuthenticatorFilter implements AuthenticatorFilter {

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
