package stroom.security.common.impl;

import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class RequestAuthenticatorImpl implements RequestAuthenticator {

    private final UserIdentityFactory userIdentityFactory;

    @Inject
    public RequestAuthenticatorImpl(final UserIdentityFactory userIdentityFactory) {
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request) {
        return userIdentityFactory.getApiUserIdentity(request);
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return userIdentityFactory.hasAuthenticationToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        NullSafe.consume(headers, userIdentityFactory::removeAuthEntries);
    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
        return userIdentityFactory.getAuthHeaders(userIdentity);
    }

    @Override
    public Map<String, String> getServiceUserAuthHeaders() {
        return userIdentityFactory.getAuthHeaders(userIdentityFactory.getServiceUserIdentity());
    }
}
