package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.openid.api.TokenResponse;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public interface UserIdentityFactory {

    Optional<UserIdentity> getApiUserIdentity(HttpServletRequest request);

    Optional<UserIdentity> getAuthFlowUserIdentity(HttpServletRequest request,
                                                   String code,
                                                   String postAuthRedirectUri,
                                                   AuthenticationState state);

    Optional<UserIdentity> refresh(UserIdentity userIdentity);
}
