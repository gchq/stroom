package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
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

@AutoLogged(OperationType.MANUALLY_LOGGED)
public class SessionResourceImpl implements SessionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResourceImpl.class);

    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final Provider<AuthenticationEventLog> authenticationEventLogProvider;
    private final Provider<SessionListService> sessionListService;

    @Inject
    public SessionResourceImpl(final Provider<AuthenticationConfig> authenticationConfigProvider,
                               final Provider<OpenIdManager> openIdManagerProvider,
                               final Provider<HttpServletRequest> httpServletRequestProvider,
                               final Provider<AuthenticationEventLog> authenticationEventLogProvider,
                               final Provider<SessionListService> sessionListService) {
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.openIdManagerProvider = openIdManagerProvider;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.authenticationEventLogProvider = authenticationEventLogProvider;
        this.sessionListService = sessionListService;
    }

    // For testing.
    SessionResourceImpl(final Provider<SessionListService> sessionListService) {
        this.authenticationConfigProvider = null;
        this.openIdManagerProvider = null;
        this.httpServletRequestProvider = null;
        this.authenticationEventLogProvider = null;
        this.sessionListService = sessionListService;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateSessionResponse validateSession(final String postAuthRedirectUri) {
        final AuthenticationConfig authenticationConfig = authenticationConfigProvider.get();
        final OpenIdManager openIdManager = openIdManagerProvider.get();
        final HttpServletRequest request = httpServletRequestProvider.get();
        Optional<UserIdentity> userIdentity = openIdManager.loginWithRequestToken(request);
        userIdentity = openIdManager.getOrSetSessionUser(request, userIdentity);
        if (userIdentity.isPresent()) {
            return new ValidateSessionResponse(true, userIdentity.get().getId(), null);
        }

        if (!authenticationConfig.isAuthenticationRequired()) {
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
                final String redirectUri = openIdManager.redirect(request, code, stateId);

                // We might have completed the back channel authentication now so see if we have a user session.
                userIdentity = UserIdentitySessionUtil.get(request.getSession(false));
                return userIdentity
                        .map(identity ->
                                new ValidateSessionResponse(true, identity.getId(), null))
                        .orElseGet(() ->
                                new ValidateSessionResponse(false, null, redirectUri));

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
        final HttpServletRequest request = httpServletRequestProvider.get();

        // Get the session.
        final HttpSession session = request.getSession(false);
        if (session != null) {
            final Optional<UserIdentity> userIdentity = UserIdentitySessionUtil.get(session);
            // Record the logoff event.
            userIdentity.ifPresent(ui -> {
                // Create an event for logout
                authenticationEventLogProvider.get().logoff(ui.getId());
            });

            // Remove the user identity from the current session.
            UserIdentitySessionUtil.set(session, null);
        }

        final String url = openIdManagerProvider.get().logout(request);
        return new UrlResponse(url);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public SessionListResponse list(final String nodeName) {
        LOGGER.debug("list({}) called", nodeName);
        if (nodeName != null) {
            return sessionListService.get().listSessions(nodeName);
        } else {
            return sessionListService.get().listSessions();
        }
    }
}
