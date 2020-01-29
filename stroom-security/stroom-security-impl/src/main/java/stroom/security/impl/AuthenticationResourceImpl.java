package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.security.shared.AuthenticationResource;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

class AuthenticationResourceImpl implements AuthenticationResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResourceImpl.class);

    private final SecurityContext securityContext;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final AuthenticationEventLog eventLog;

    @Inject
    AuthenticationResourceImpl(final SecurityContext securityContext,
                               final Provider<HttpServletRequest> httpServletRequestProvider,
                               final AuthenticationEventLog eventLog) {
        this.securityContext = securityContext;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.eventLog = eventLog;
    }

    @Override
    public Boolean logout() {
        return securityContext.insecureResult(() -> {
            final HttpSession session = httpServletRequestProvider.get().getSession(false);
            final UserIdentity userIdentity = UserIdentitySessionUtil.get(session);
            if (session != null) {
                // Invalidate the current user session
                session.invalidate();
            }
            if (userIdentity != null) {
                // Create an event for logout
                eventLog.logoff(userIdentity.getId());
            }

            return true;
        });
    }
}
