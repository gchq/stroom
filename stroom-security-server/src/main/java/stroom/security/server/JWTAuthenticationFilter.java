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
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.HmacKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.apiclients.AuthenticationServiceClient;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.model.IdTokenRequest;
import stroom.util.config.StroomProperties;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class JWTAuthenticationFilter extends AuthenticatingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

    private JWTService jwtService;
    private NonceManager nonceManager;
    private AuthenticationServiceClient authenticationServiceClient;
    private SessionManager sessionManager;
    //TODO Use an API gateway
    private final String LOGIN_URL_PROPERTY_NAME = "stroom.security.login.url";
    private final String AUTHENTICATION_URL_PROPERTY_NAME = "stroom.security.authentication.url";
    private final String ADVERTISED_STROOM_URL = "stroom.advertisedUrl";
    private final String JWT_SECRET = "stroom.security.jwtSecret";
    private final String JWT_ISSUER = "stroom.security.jwtIssuer";

    public JWTAuthenticationFilter(
            final JWTService jwtService,
            final NonceManager nonceManager,
            AuthenticationServiceClient authenticationServiceClient,
            SessionManager sessionManager) {
        this.jwtService = jwtService;
        this.nonceManager = nonceManager;
        this.authenticationServiceClient = authenticationServiceClient;
        this.sessionManager = sessionManager;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        // We need to allow CORS preflight requests
        String httpMethod = (((ShiroHttpServletRequest) request).getMethod());
        if(httpMethod.toUpperCase().equals(HttpMethod.OPTIONS)) {
            return true;
        }

        boolean loggedIn = false;
        String accessCode = request.getParameter("accessCode");
        String sessionId = request.getParameter("sessionId");

        // Are we dealing with a new session?
        if(sessionId == null){
            sessionId = UUID.randomUUID().toString();
        }

        // We need to know what jSessionIds are associated with this
        // wider session Id, so we'll record a mapping.
        if(((ShiroHttpServletRequest) request).getCookies() != null) {
            Optional<String> optionalJSessionId = Arrays.stream(((ShiroHttpServletRequest) request).getCookies())
                    .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst()
                    .map(cookie -> cookie.getValue());

            if (optionalJSessionId.isPresent()) {
                sessionManager.add(sessionId, optionalJSessionId.get());
            }
        }

        if(accessCode != null) {
            loggedIn = executeLogin(request, response);
        }

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
                String authenticationUrl = StroomProperties.getProperty(AUTHENTICATION_URL_PROPERTY_NAME) + "/authenticate";
                String advertisedStroomUrl = StroomProperties.getProperty(ADVERTISED_STROOM_URL);

                String nonceHash = nonceManager.createNonce(sessionId);

                StringBuilder redirectionParams = new StringBuilder();
                redirectionParams.append("?scope=openid");
                redirectionParams.append("&response_type=code");
                redirectionParams.append("&client_id=stroom");
                redirectionParams.append("&redirect_url=");
                redirectionParams.append(advertisedStroomUrl);
                redirectionParams.append("&state="); //TODO Not yet sure what's needed here
                redirectionParams.append("&nonce=");
                redirectionParams.append(nonceHash);
                redirectionParams.append("&sessionId=");
                redirectionParams.append(sessionId);

                String redirectionUrl = authenticationUrl + redirectionParams.toString();
                LOGGER.info("Redirecting with an AuthenticationRequest to: {}", redirectionUrl);
                HttpServletResponse httpResponse = WebUtils.toHttp(response);
                // We need to make sure that the client has the cookie.
                httpResponse.addCookie(new Cookie("sessionId", sessionId));
                httpResponse.sendRedirect(redirectionUrl);
            }
        }

        return loggedIn;
    }

    @Override
    protected JWTAuthenticationToken createToken(ServletRequest request, ServletResponse response) throws IOException, InvalidJwtException, MalformedClaimException {
        String accessCode = request.getParameter("accessCode");
        String sessionId = request.getParameter("sessionId");

        //TODO: check the optionals and handle empties.
        if(accessCode != null){
            IdTokenRequest idTokenRequest = new IdTokenRequest();
            idTokenRequest.setAccessCode(accessCode);
            idTokenRequest.setSessionId(sessionId);
            idTokenRequest.setRequestingClientId("stroom");
            String idToken = null;
            try {
                idToken = authenticationServiceClient.getAuthServiceApi().getIdToken(idTokenRequest);
            } catch (ApiException e) {
                if(e.getCode() == Response.Status.UNAUTHORIZED.getStatusCode()){
                    // Our access code isn't valid, so we need to redirect and start the flow again.
                    LOGGER.error("My request for an id_token was rejected as unauthorised!", e);
                    throw new RuntimeException("Request to log you in was not authenticated! Is Stroom configured correctly?");
                }
            }

            String jwtSecret = StroomProperties.getProperty(JWT_SECRET);
            String jwtIssuer = StroomProperties.getProperty(JWT_ISSUER);

            JwtConsumerBuilder builder = new JwtConsumerBuilder()
                    .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                    .setRequireSubject() // the JWT must have a subject claim
                    .setVerificationKey(new HmacKey(jwtSecret.getBytes())) // verify the signature with the public key
                    .setRelaxVerificationKeyValidation() // relaxes key length requirement
                    .setExpectedIssuer(jwtIssuer);

            JwtConsumer consumer = builder.build();

            final JwtClaims claims = consumer.processToClaims(idToken);
            String nonceHash = (String)claims.getClaimsMap().get("nonce");
            boolean doNoncesMatch = nonceManager.match(sessionId, nonceHash);
            if(!doNoncesMatch){
                // If the nonces don't match we need to redirect to log in again.
                LOGGER.info("Received a bad nonce!");
                return null;
            }

            LOGGER.info("User is authenticated for sessionId " + sessionId);
            // The user is authenticated now.
            nonceManager.forget(sessionId);
            return new JWTAuthenticationToken(claims.getSubject(), idToken);
        }
        else{
            LOGGER.info("Attempted access without an access code.");
            return null;
        }
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}