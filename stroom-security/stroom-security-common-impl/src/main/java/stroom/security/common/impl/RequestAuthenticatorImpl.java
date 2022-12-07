package stroom.security.common.impl;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
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
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        if (hasAuthenticationToken(request)) {
            return authenticateWithToken(request, attributeMap);
        } else {
            throw new UnsupportedOperationException("TODO");
        }
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

    private Optional<UserIdentity> authenticateWithToken(final HttpServletRequest request,
                                                         final AttributeMap attributeMap) {

        final Optional<UserIdentity> optUserIdentity = userIdentityFactory.getApiUserIdentity(request);

        if (attributeMap != null) {
            // Add the user identified in the token (if present) to the attribute map.
            // Use both ID and username as the ID will likely be a nasty UUID while the username will be more
            // useful for a human to read.
            optUserIdentity
                    .map(UserIdentity::getId)
                    .ifPresent(id ->
                            attributeMap.put(StandardHeaderArguments.UPLOAD_USER_ID, id));
            optUserIdentity
                    .map(UserIdentity::getPreferredUsername)
                    .ifPresent(username ->
                            attributeMap.put(StandardHeaderArguments.UPLOAD_USERNAME, username));

            // Remove authorization header from attributes as it should not be stored or forwarded on.
            removeAuthorisationEntries(attributeMap);
        }
        return optUserIdentity;
    }
}
