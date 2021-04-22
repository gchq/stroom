package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.security.openid.api.OpenId;
import stroom.security.shared.StroomSessionResource;
import stroom.security.shared.UrlResponse;
import stroom.security.shared.ValidateSessionResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

class StroomSessionResourceImpl implements StroomSessionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomSessionResourceImpl.class);

    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final Provider<AuthenticationEventLog> authenticationEventLogProvider;
    private final OpenIdManager openIdManager;

    @Inject
    StroomSessionResourceImpl(final Provider<AuthenticationConfig> authenticationConfigProvider,
                              final Provider<OpenIdManager> openIdManagerProvider,
                              final Provider<HttpServletRequest> httpServletRequestProvider,
                              final Provider<AuthenticationEventLog> authenticationEventLogProvider,
                              final OpenIdManager openIdManager) {
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.openIdManagerProvider = openIdManagerProvider;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.authenticationEventLogProvider = authenticationEventLogProvider;
        this.openIdManager = openIdManager;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateSessionResponse validateSession(final String postAuthRedirectUri) {
        final HttpServletRequest request = httpServletRequestProvider.get();
        final Optional<UserIdentity> userIdentity = openIdManagerProvider.get().loginWithRequestToken(request);
        if (userIdentity.isPresent()) {
            return new ValidateSessionResponse(true, userIdentity.get().getId(), null);
        }

        if (!authenticationConfigProvider.get().isAuthenticationRequired()) {
            return new ValidateSessionResponse(true, "admin", null);

//        } else if (openIdManagerProvider.get().isTokenExpectedInRequest()) {
//            LOGGER.error("We are expecting requests that contain authenticated tokens");
//            return new ValidateSessionResponse(false, null, null);

        } else {
            // If the session doesn't have a user ref then attempt login.
            try {
                LOGGER.debug("Using postAuthRedirectUri: {}", postAuthRedirectUri);

                // If we have completed the front channel flow then we will have a state id.
                final String code = getParam(postAuthRedirectUri, OpenId.CODE);
                final String stateId = getParam(postAuthRedirectUri, OpenId.STATE);
                final String redirectUri = openIdManagerProvider.get()
                        .redirect(request, code, stateId, postAuthRedirectUri);
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
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public UrlResponse logout(final String redirectUri) {
        final String postAuthRedirectUri = OpenId.removeReservedParams(redirectUri);
        final HttpServletRequest request = httpServletRequestProvider.get();

        // Invalidate the Stroom session.
        final HttpSession session = request.getSession(false);
        final Optional<UserIdentity> userIdentity = UserIdentitySessionUtil.get(session);
        if (session != null) {
            // Invalidate the current user session
            session.invalidate();
        }
        // Record the logoff event.
        userIdentity.ifPresent(ui -> {
            // Create an event for logout
            authenticationEventLogProvider.get().logoff(ui.getId());
        });

        final String url = openIdManager.logout(request, postAuthRedirectUri);
        return new UrlResponse(url);
    }
}
