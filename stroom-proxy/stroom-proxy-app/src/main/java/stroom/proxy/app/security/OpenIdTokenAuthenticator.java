package stroom.proxy.app.security;

import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class OpenIdTokenAuthenticator implements RequestAuthenticator {

    private final ApiUserIdentityFactory apiUserIdentityFactory;

    @Inject
    public OpenIdTokenAuthenticator(final ApiUserIdentityFactory apiUserIdentityFactory) {
        this.apiUserIdentityFactory = apiUserIdentityFactory;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request) {
        return apiUserIdentityFactory.getApiUserIdentity(request);
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return apiUserIdentityFactory.hasAuthenticationToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        NullSafe.consume(headers, apiUserIdentityFactory::removeAuthorisationEntries);
    }
}
