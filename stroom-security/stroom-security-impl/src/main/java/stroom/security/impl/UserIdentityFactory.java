package stroom.security.impl;

import stroom.security.api.UserIdentity;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface UserIdentityFactory {

    Optional<UserIdentity> getApiUserIdentity(HttpServletRequest request);

    Optional<UserIdentity> getAuthFlowUserIdentity(HttpServletRequest request,
                                                   String code,
                                                   AuthenticationState state);

    void refresh(UserIdentity userIdentity);
}
