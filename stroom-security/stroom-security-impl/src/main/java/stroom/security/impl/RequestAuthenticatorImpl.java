package stroom.security.impl;

import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;

import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class RequestAuthenticatorImpl implements RequestAuthenticator {

    private final OpenIdManager openIdManager;

    @Inject
    public RequestAuthenticatorImpl(final OpenIdManager openIdManager) {
        this.openIdManager = openIdManager;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request) {
        return openIdManager.loginWithRequestToken(request);
    }
}
