package stroom.security.impl;

import stroom.authentication.api.OIDC;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.security.shared.ValidateSessionResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

class StroomSessionResourceImpl implements StroomSessionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomSessionResourceImpl.class);

    private final AuthenticationConfig authenticationConfig;
    private final OpenIdManager openIdManager;
    private final SecurityContext securityContext;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final AuthenticationEventLog eventLog;

    @Inject
    StroomSessionResourceImpl(final AuthenticationConfig authenticationConfig,
                              final OpenIdManager openIdManager,
                              final SecurityContext securityContext,
                              final Provider<HttpServletRequest> httpServletRequestProvider,
                              final AuthenticationEventLog eventLog) {
        this.authenticationConfig = authenticationConfig;
        this.openIdManager = openIdManager;
        this.securityContext = securityContext;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.eventLog = eventLog;
    }

    @Override
    public ValidateSessionResponse validateSession(final HttpServletRequest request,
                                                   final String postAuthRedirectUri) {
        final UserIdentity userIdentity = UserIdentitySessionUtil.get(request.getSession(false));
        if (userIdentity != null) {
            return new ValidateSessionResponse(true, userIdentity.getId(), null);
        }

        if (!authenticationConfig.isAuthenticationRequired()) {
            return new ValidateSessionResponse(true, "admin", null);
        } else {
            // If the session doesn't have a user ref then attempt login.
            try {
                LOGGER.debug("Using postAuthRedirectUri: {}", postAuthRedirectUri);

                String redirectUri = null;

                // If we have completed the front channel flow then we will have a state id.
                final String code = getParam(postAuthRedirectUri, OIDC.CODE);
                final String stateId = getParam(postAuthRedirectUri, OIDC.STATE);
                if (code != null && stateId != null) {
                    final String cleanUri = OIDC.removeOIDCParams(postAuthRedirectUri);
                    redirectUri = openIdManager.backChannelOIDC(request, code, stateId, cleanUri);
                }

                if (redirectUri == null) {
                    redirectUri = openIdManager.frontChannelOIDC(request, postAuthRedirectUri);
                }

                return new ValidateSessionResponse(false, null, redirectUri);

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    private String getParam(final String url, final String param) {
        int start = url.indexOf(param + "=");
        if (start != -1) {
            start += param.length() + 1;
            final int end = url.indexOf("&", start);
            if (end != -1) {
                return URLDecoder.decode(url.substring(start, end), StandardCharsets.UTF_8);
            }
            return URLDecoder.decode(url.substring(start), StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public Boolean invalidate() {
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
