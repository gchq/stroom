package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.UrlUtils;
import stroom.util.servlet.UserAgentSessionUtil;

import com.google.common.base.Strings;

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.UriBuilder;

class OpenIdManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdManager.class);

    private final ResolvedOpenIdConfig openIdConfig;
    private final UserIdentityFactory userIdentityFactory;
    private final UriFactory uriFactory;

    @Inject
    public OpenIdManager(final ResolvedOpenIdConfig openIdConfig,
                         final UserIdentityFactory userIdentityFactory,
                         final UriFactory uriFactory) {
        this.openIdConfig = openIdConfig;
        this.userIdentityFactory = userIdentityFactory;
        this.uriFactory = uriFactory;
    }

    public String redirect(final HttpServletRequest request,
                           final String code,
                           final String stateId) {
        String redirectUri = null;

        // If we have completed the front channel flow then we will have a state id.
        if (code != null && stateId != null) {
            redirectUri = backChannelOIDC(request, code, stateId);
        }

        if (redirectUri == null) {
            redirectUri = frontChannelOIDC(request);
        }

        return redirectUri;
    }

    private String frontChannelOIDC(final HttpServletRequest request) {
        final String endpoint = openIdConfig.getAuthEndpoint();
        final String clientId = openIdConfig.getClientId();
        Objects.requireNonNull(endpoint,
                "To make an authentication request the OpenId config 'authEndpoint' must not be null");
        Objects.requireNonNull(clientId,
                "To make an authentication request the OpenId config 'clientId' must not be null");
        // Create a state for this authentication request.
        final AuthenticationState state = AuthenticationStateSessionUtil.create(request, buildRedirectUrl(request));
        LOGGER.debug(() -> "frontChannelOIDC state=" + state);
        return createAuthUri(request, endpoint, clientId, state, false);
    }

    private String backChannelOIDC(final HttpServletRequest request,
                                   final String code,
                                   final String stateId) {
        Objects.requireNonNull(code, "Null code");
        Objects.requireNonNull(stateId, "Null state Id");

        // If we have a state id then this should be a return from the auth service.
        LOGGER.debug(() -> "We have the following state: " + stateId);

        // Check the state is one we requested.
        final AuthenticationState state = AuthenticationStateSessionUtil.pop(request, stateId);
        if (state == null) {
            LOGGER.warn(() -> "Unexpected state: " + stateId);
        } else {
            LOGGER.debug(() -> "backChannelOIDC state=" + state);
            final HttpSession session = request.getSession(false);
            UserAgentSessionUtil.set(request);

            final Optional<UserIdentity> optionalUserIdentity =
                    userIdentityFactory.getAuthFlowUserIdentity(request, code, state);

            if (optionalUserIdentity.isPresent()) {
                // Set the token in the session.
                UserIdentitySessionUtil.set(session, optionalUserIdentity.get());

                // Login successful. Use the redirect URI if configured, else use the original request URI.
                final String redirectUri = state.getUri();
                LOGGER.info(() -> "Redirecting to initiating URI: " + redirectUri);
                return redirectUri;
            }
        }

        return null;
    }

    /**
     * Build a complete redirect URL using the configured public URL.
     * This is the URL used to redirect after the authorisation flow has completed.
     */
    private String buildRedirectUrl(final HttpServletRequest request) {
        return uriFactory.publicUri(UrlUtils.getFullUri(request)).toString();
    }

    /**
     * Prefer the configured redirect URI. Otherwise, use the original request URI when redirecting after successful
     * authorisation.
     */
    public String getRedirectUri(final HttpServletRequest request) {
        return getRedirectUri(buildRedirectUrl(request));
    }

    public String getRedirectUri(final String originalUrl) {
        final String redirectUri = openIdConfig.getRedirectUri();
        if (redirectUri == null || redirectUri.isEmpty()) {
            return originalUrl;
        } else {
            return redirectUri;
        }
    }

    /**
     * This method attempts to get a token from the request headers and, if present, use that to login.
     */
    public Optional<UserIdentity> loginWithRequestToken(final HttpServletRequest request) {
        return userIdentityFactory.getApiUserIdentity(request);
    }

    public Optional<UserIdentity> getOrSetSessionUser(final HttpServletRequest request,
                                                      final Optional<UserIdentity> userIdentity) {
        Optional<UserIdentity> result = userIdentity;

        if (userIdentity.isEmpty()) {
            // Provide identity from the session if we are allowing this to happen.
            result = UserIdentitySessionUtil.get(request.getSession(false));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User identity from session: [{}]", userIdentity.orElse(null));
            }

        } else if (UserIdentitySessionUtil.requestHasSessionCookie(request)) {
            // Set the user ref in the session.
            UserIdentitySessionUtil.set(request.getSession(true), userIdentity.get());
        }

        return result;
    }

    public String logout(final HttpServletRequest request) {
        final String endpoint = openIdConfig.getLogoutEndpoint();
        final String clientId = openIdConfig.getClientId();
        Objects.requireNonNull(endpoint,
                "To make a logout request the OpenId config 'logoutEndpoint' must not be null");
        Objects.requireNonNull(clientId,
                "To make an authentication request the OpenId config 'clientId' must not be null");
        final AuthenticationState state = AuthenticationStateSessionUtil.create(request, buildRedirectUrl(request));
        LOGGER.debug(() -> "logout state=" + state);
        return createAuthUri(request, endpoint, clientId, state, true);
    }

    private String createAuthUri(final HttpServletRequest request,
                                 final String endpoint,
                                 final String clientId,
                                 final AuthenticationState state,
                                 final boolean prompt) {
        // In some cases we might need to use an external URL as the current incoming one might have been proxied.
        // Use OIDC API.
        UriBuilder uriBuilder = UriBuilder.fromUri(endpoint);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.RESPONSE_TYPE, OpenId.CODE);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.CLIENT_ID, clientId);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.REDIRECT_URI, getRedirectUri(state.getUri()));
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.SCOPE, OpenId.SCOPE__OPENID +
                " " +
                OpenId.SCOPE__EMAIL);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.STATE, state.getId());
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.NONCE, state.getNonce());

        // If there's 'prompt' in the request then we'll want to pass that on to the AuthenticationService.
        // In OpenId 'prompt=login' asks the IP to present a login page to the user, and that's the effect
        // this will have. We need this so that we can bypass certificate logins, e.g. for when we need to
        // log in as the 'admin' user but the browser is always presenting a certificate.
        final String promptParam = UrlUtils.getLastParam(request, OpenId.PROMPT);
        if (!Strings.isNullOrEmpty(promptParam)) {
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.PROMPT, promptParam);
        } else if (prompt) {
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.PROMPT, "login");
        }

        final String authenticationRequestUrl = uriBuilder.build().toString();
        LOGGER.info(() -> "Redirecting with an AuthenticationRequest to: " + authenticationRequestUrl);
        // We want to make sure that the client has the cookie.
        return authenticationRequestUrl;
    }
}
