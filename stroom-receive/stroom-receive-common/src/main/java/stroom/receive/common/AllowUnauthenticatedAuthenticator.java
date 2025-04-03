package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;

import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

@Singleton
public class AllowUnauthenticatedAuthenticator implements AuthenticatorFilter {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static final Optional<UserIdentity> UNAUTHENTICATED_USER = Optional.ofNullable(
            UnauthenticatedUserIdentity.getInstance());

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        return UNAUTHENTICATED_USER;
    }
}
