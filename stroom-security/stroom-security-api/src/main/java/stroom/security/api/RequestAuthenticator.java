package stroom.security.api;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface RequestAuthenticator {

    Optional<UserIdentity> authenticate(HttpServletRequest request);
}
