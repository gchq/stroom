package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;

import java.util.Optional;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

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
