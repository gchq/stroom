package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface ApiUserIdentityFactory {

    Optional<UserIdentity> getApiUserIdentity(HttpServletRequest request);

    boolean hasAuthenticationToken(final HttpServletRequest request);

    void removeAuthorisationEntries(final Map<String, String> headers);

//    void refresh(UserIdentity userIdentity);

}
