/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.server;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.servlet.ShiroHttpServletRequest;
import org.apache.shiro.web.util.WebUtils;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.apiclients.AuthenticationServiceClients;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.model.IdTokenRequest;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

// TODO: Could this be refactored into a resource with distinct
// sendAuthenticationRequest and handleAuthenticationResponse parts?
// The logic here works in the context of a Shiro filter. It's possible that
// this is no longer an appropriate abstraction, which means this whole area
// could be refactored.
public class JWTAuthenticationFilter extends AuthenticatingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

    // This cookie contains the SSO session ID. It is created here so we can track the nonce.
    private static final String STROOM_SESSION_COOKIE = "stroomSessionId";

    private final String authenticationServiceUrl;
    private final String advertisedStroomUrl;
    private final String jwtVerificationKey;
    private final String jwtIssuer;
    private final JWTService jwtService;
    private final NonceManager nonceManager;
    private final AuthenticationServiceClients authenticationServiceClients;

    public JWTAuthenticationFilter(
            final String authenticationServiceUrl,
            final String advertisedStroomUrl,
            final String jwtVerificationKey,
            final String jwtIssuer,
            final JWTService jwtService,
            final NonceManager nonceManager,
            final AuthenticationServiceClients authenticationServiceClients) {
        this.authenticationServiceUrl = authenticationServiceUrl;
        this.advertisedStroomUrl = advertisedStroomUrl;
        this.jwtVerificationKey = jwtVerificationKey;
        this.jwtIssuer = jwtIssuer;
        this.jwtService = jwtService;
        this.nonceManager = nonceManager;
        this.authenticationServiceClients = authenticationServiceClients;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {

        // We need to allow CORS preflight requests
        String httpMethod = (((ShiroHttpServletRequest) request).getMethod());
        if (httpMethod.toUpperCase().equals(HttpMethod.OPTIONS)) {
            return true;
        }

        boolean loggedIn = false;

        // Authenticate requests from an API client
        boolean isAuthenticatedApiRequest = jwtService.containsValidJws(request);
        if (isAuthenticatedApiRequest) {
            loggedIn = executeLogin(request, response);
            return loggedIn;
        }

        // Authenticate requests from a User Agent
        // We need to see if we have a stroom session cookie, and create one if we don't.
        // We need one so we we get an access code we know which nonce to check against.
        String stroomSessionId;
        LOGGER.debug("There is no stroomSessionId in the request -- attempting to get one from a cookie.");
        Optional<String> optionalStroomSessionId = Optional.empty();
        if(((ShiroHttpServletRequest) request).getCookies() != null) {
            optionalStroomSessionId = Arrays.stream(((ShiroHttpServletRequest) request).getCookies())
                    .filter(cookie -> cookie.getName().equals(STROOM_SESSION_COOKIE))
                    .findFirst()
                    .map(cookie -> cookie.getValue());
        }
        if(optionalStroomSessionId.isPresent()){
            stroomSessionId = optionalStroomSessionId.get();
        }
        else {
            // If we don't have a stroomSessionId then we need to create one.
            stroomSessionId = UUID.randomUUID().toString();
        }


        // If we have an access code we can try and log in.
        String accessCode = request.getParameter("accessCode");
        if(accessCode != null) {
            LOGGER.debug("We have the following access code: {{}}", accessCode);
            loggedIn = executeLogin(request, response);
        }

        // If we're not logged in we need to start an AuthenticationRequest flow.
        if (!loggedIn) {
            // We need to distinguish between requests from an API client and from the GWT front-end.
            // If a request is from the GWT front-end and fails authentication then we need to redirect to the login page.
            // If a request is from an API client and fails authentication then we need to return HTTP 403 UNAUTHORIZED.
            String servletPath = ((ShiroHttpServletRequest) request).getServletPath();
            boolean isApiRequest = servletPath.contains("/api");

            if(isApiRequest) {
                LOGGER.debug("API request is unauthorised.");
                HttpServletResponse httpResponse = WebUtils.toHttp(response);
                httpResponse.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
            }
            else {
                // We have a a new request so we're going to redirect with an AuthenticationRequest.
                String authenticationRequestBaseUrl = authenticationServiceUrl + "/authentication/v1/authenticate";
                String nonceHash = nonceManager.createNonce(stroomSessionId);

                StringBuilder authenticationRequestParams = new StringBuilder();
                authenticationRequestParams.append("?scope=openid");
                authenticationRequestParams.append("&response_type=code");
                authenticationRequestParams.append("&client_id=stroom");
                authenticationRequestParams.append("&redirect_url=");
                authenticationRequestParams.append(advertisedStroomUrl);
                authenticationRequestParams.append("&state="); //TODO Not yet sure what's needed here
                authenticationRequestParams.append("&nonce=");
                authenticationRequestParams.append(nonceHash);

                String authenticationRequestUrl = authenticationRequestBaseUrl + authenticationRequestParams.toString();
                LOGGER.info("Redirecting with an AuthenticationRequest to: {}", authenticationRequestUrl);
                HttpServletResponse httpResponse = WebUtils.toHttp(response);
                // We want to make sure that the client has the cookie.
                Cookie sessionIdCookie = new Cookie(STROOM_SESSION_COOKIE, stroomSessionId);
                sessionIdCookie.setPath("/");
                httpResponse.addCookie(sessionIdCookie);
                httpResponse.sendRedirect(authenticationRequestUrl);
                return false;
            }
        }

        return loggedIn;
    }

    /**
     * This method must create the token used by Shiro.
     * It does this by enacting the OpenId exchange of accessCode for idToken.
     */
    @Override
    protected JWTAuthenticationToken createToken(ServletRequest request, ServletResponse response) throws IOException, InvalidJwtException, MalformedClaimException {
        // First we'll check if this is an API request. If it is we'll check the JWS and create a token.
        String path = ((HttpServletRequest)request).getRequestURI();
        if(path.contains("/api/")){
            Optional<String> optionalJws = jwtService.getJws(request);
            if(optionalJws.isPresent()){
                JwtConsumer consumer = jwtService.newJwsConsumer();
                final JwtClaims claims = consumer.processToClaims(optionalJws.get());
                return new JWTAuthenticationToken(claims.getSubject(), optionalJws.get());
            }
            else{
                LOGGER.error("Cannot get a JWS for an API request!");
                return null;
            }
        }

        // If we're not dealing with an API request we'll assume we're completing the AuthenticationRequest
        // flow, i.e. we have an access code and we need to get an idToken.

        // We expect the accessCode in the query parameters...
        String accessCode = request.getParameter("accessCode");
        // ... and we expect a stroomSessionId cookie.
        // We need the sessionId cookie so we can identify this session to the remote AuthenticationService.
        Optional<String> optionalSessionId = Arrays.stream(((ShiroHttpServletRequest) request).getCookies())
                .filter(cookie -> cookie.getName().equals(STROOM_SESSION_COOKIE))
                .findFirst()
                .map(cookie -> cookie.getValue());
        String sessionId = optionalSessionId.get();


        //TODO: check the optionals and handle empties.
        if (accessCode != null) {
            IdTokenRequest idTokenRequest = new IdTokenRequest();
            idTokenRequest.setAccessCode(accessCode);
            idTokenRequest.setSessionId(sessionId);
            idTokenRequest.setRequestingClientId("stroom");
            String idToken = null;
            try {
                idToken = authenticationServiceClients.newAuthenticationApi().getIdTokenWithPost(idTokenRequest);
            } catch (ApiException e) {
                if (e.getCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                    LOGGER.error("The accessCode used to obtain an idToken was rejected. Has it already been used?", e);
                    // If we can't exchange the accessCode for an idToken then this probably means the
                    // accessCode doesn't exist any more, or has already been used. so we can't proceed.
                    // But we still need to return a token. We'll return an empty one and it'll end up being judged
                    // invalid and the authentication flow will begin again, leading to a fresh, correct token.
                    return new JWTAuthenticationToken("","");
                }
            }

            JwtConsumer consumer = jwtService.newJwsConsumer();
            final JwtClaims claims = consumer.processToClaims(idToken);
            String nonceHash = (String) claims.getClaimsMap().get("nonce");
            boolean doNoncesMatch = nonceManager.match(sessionId, nonceHash);
            if (!doNoncesMatch) {
                LOGGER.info("Received a bad nonce!");
                // If the nonces don't match we need to redirect to log in again.
                // Maybe the request uses an out-of-date stroomSessionId?
                // We still need to return a token. We'll return an empty one and it'll end up being judged
                // invalid and the authentication flow will begin again, leading to a fresh, correct token.
                return new JWTAuthenticationToken("","");
            }

            LOGGER.info("User is authenticated for sessionId " + sessionId);
            // The user is authenticated now.
            nonceManager.forget(sessionId);
            return new JWTAuthenticationToken(claims.getSubject(), idToken);
        } else {
            LOGGER.error("Attempted access without an access code!");
            // We still need to return a token. We'll return an empty one and it'll end up being judged
            // invalid and the authentication flow will begin again, leading to a fresh, correct token.
            return new JWTAuthenticationToken("","");
        }
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}