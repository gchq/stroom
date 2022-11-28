package stroom.security.api;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface RequestAuthenticator {

    Optional<UserIdentity> authenticate(final HttpServletRequest request);

    boolean hasAuthenticationToken(final HttpServletRequest request);

    void removeAuthorisationEntries(final Map<String, String> headers);
}
