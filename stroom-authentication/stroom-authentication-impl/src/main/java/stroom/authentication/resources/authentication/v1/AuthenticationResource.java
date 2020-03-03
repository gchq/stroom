/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.resources.authentication.v1;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.jersey.sessions.Session;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.CertificateManager;
import stroom.authentication.TokenVerifier;
import stroom.authentication.exceptions.NoSuchUserException;
import stroom.authentication.resources.token.v1.Token;
import stroom.authentication.resources.token.v1.TokenService;
import stroom.security.impl.ExchangeAccessCodeRequest;
import stroom.util.shared.RestResource;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import static javax.ws.rs.core.Response.seeOther;
import static javax.ws.rs.core.Response.status;

@Singleton
@Path("/authentication/v1")
@Produces(MediaType.APPLICATION_JSON)
@Api(description = "Stroom Authentication API", tags = {"Authentication"})
public final class AuthenticationResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResource.class);
    private AuthenticationService service;
    private TokenService tokenService;
    private TokenVerifier tokenVerifier;
    private CertificateManager certificateManager;

    @Inject
    public AuthenticationResource(
            final AuthenticationService service,
            final TokenService tokenService,
            final TokenVerifier tokenVerifier,
            final CertificateManager certificateManager) {
        this.service = service;
        this.tokenService = tokenService;
        this.tokenVerifier = tokenVerifier;
        this.certificateManager = certificateManager;
    }

    @GET
    @Path("/authenticate")
    @Timed
    @ApiOperation(value = "Submit an OpenId AuthenticationRequest.", response = String.class, tags = {"Authentication"})
    public final Response handleAuthenticationRequest(
            @Session HttpSession httpSession,
            @Context @NotNull HttpServletRequest httpServletRequest,
            @QueryParam("scope") @NotNull String scope,
            @QueryParam("response_type") @NotNull String responseType,
            @QueryParam("client_id") @NotNull String clientId,
            @QueryParam("redirect_url") @NotNull String redirectUrl,
            @QueryParam("nonce") @Nullable String nonce,
            @QueryParam("state") @Nullable String state,
            @QueryParam("prompt") @Nullable String prompt) throws URISyntaxException {

        Optional<String> optionalCn = certificateManager.getCertificate(httpServletRequest);
        return service.handleAuthenticationRequest(
                httpSession.getId(), nonce, state, redirectUrl, clientId, prompt, optionalCn
        ).build();
    }

    /**
     * We expect the user to have a session if they're trying to log in.
     * If they don't then they need to be directed to an application that will submit
     * an AuthenticationRequest to /authenticate.
     */
    @POST
    @Path("/authenticate")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @Timed
    @NotNull
    @ApiOperation(value = "Handle a login request made using username and password credentials.",
            response = String.class, tags = {"Authentication"})
    public final Response handleLogin(
            @Session HttpSession httpSession,
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("Credentials") @NotNull Credentials credentials) throws URISyntaxException, UnsupportedEncodingException {
        String sessionId = httpSession.getId();
        LOGGER.info("Received a login request for session " + sessionId);
        var loginResponse = service.handleLogin(credentials, sessionId);
        return status(loginResponse.getResponseCode())
                .entity(loginResponse)
                .build();
    }

    @GET
    @Path("/logout")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @Timed
    @NotNull
    @ApiOperation(value = "Log a user out of their session")
    public final Response logout(
            @Session HttpSession httpSession,
            @Context @NotNull HttpServletRequest httpServletRequest,
            @QueryParam("redirect_url") @Nullable String redirectUrl) throws URISyntaxException {
        String sessionId = httpSession.getId();
        final String postLogoutUrl = service.logout(sessionId, redirectUrl);
        return seeOther(new URI(postLogoutUrl)).build();
    }

    @GET
    @Path("reset/{email}")
    @Timed
    @NotNull
    @ApiOperation(value = "Reset a user account using an email address.",
            response = String.class, tags = {"Authentication"})
    public final Response resetEmail(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("email") String emailAddress) throws NoSuchUserException {
        final boolean resetEmailSent = service.resetEmail(emailAddress);
        return resetEmailSent ?
                status(Status.NO_CONTENT).build() :
                status(Status.NOT_FOUND).entity("User does not exist").build();
    }

    @GET
    @Path("/verify/{token}")
    @Timed
    @NotNull
    @ApiOperation(value = "Verify the authenticity and current-ness of a JWS token.",
            response = String.class, tags = {"Authentication"})
    public final Response verifyToken(@PathParam("token") String token) {
        var usersEmail = tokenService.verifyToken(token);
        return usersEmail
                .map(s -> status(Status.OK).entity(s).build())
                .orElseGet(() -> status(Status.UNAUTHORIZED).build());
    }


    @POST
    @Path("changePassword")
    @Timed
    @NotNull
    @ApiOperation(value = "Change a user's password.",
            response = String.class, tags = {"Authentication"})
    public final Response changePassword(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("changePasswordRequest") @NotNull ChangePasswordRequest changePasswordRequest,
            //TODO: Delete this parameter
            @PathParam("id") int userId) {
        var changePasswordResponse = service.changePassword(changePasswordRequest);
        return Response.status(Status.OK).entity(changePasswordResponse).build();
    }

    @POST
    @Path("resetPassword")
    @Timed
    @NotNull
    @ApiOperation(value = "Reset an authenticated user's password.",
            response = String.class, tags = {"Authentication"})
    public final Response resetPassword(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("changePasswordRequest") @NotNull ResetPasswordRequest req) {
        var changePasswordResponse = service.resetPassword(req);
        if (changePasswordResponse != null) {
            return Response.status(Status.OK).entity(changePasswordResponse).build();
        } else return Response.status(Status.UNAUTHORIZED).build();
    }

    @GET
    @Path("needsPasswordChange")
    @Timed
    @NotNull
    @ApiOperation(value = "Check if a user's password needs changing.",
            response = Boolean.class, tags = {"Authentication"})
    public final Response needsPasswordChange(@QueryParam("email") String email) {
        var userNeedsToChangePassword = service.needsPasswordChange(email);
        return Response.status(Status.OK).entity(userNeedsToChangePassword).build();
    }

    @POST
    @Path("isPasswordValid")
    @Timed
    @NotNull
    @ApiOperation(value = "Returns the length and complexity rules.",
            response = Boolean.class, tags = {"Authentication"})
    public final Response isPasswordValid(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("passwordValidationRequest") @NotNull PasswordValidationRequest passwordValidationRequest) {
        var response = service.isPasswordValid(passwordValidationRequest);
        return Response.status(Status.OK).entity(response).build();
    }

    /**
     * Checks to see if the user needs to change their password, and re-directs them accordingly if they do.
     * If they don't it will create the redirection URL with access code as normal.
     */
    @GET
    @Path("postAuthenticationRedirect")
    @Produces({"application/json"})
    @Timed
    @NotNull
    public final Response postAuthenticationRedirect(
            @Session HttpSession httpSession,
            @QueryParam("clientId") @NotNull String clientId) throws UnsupportedEncodingException {
        String httpSessionId = httpSession.getId();
        var redirectUrl = service.postAuthenticationRedirect(httpSessionId, clientId);
        return seeOther(redirectUrl).build();
    }

    //TODO: auth-into-stroom: obviously this needs to go

    /**
     * Performs the back-channel exchange of accessCode for idToken.
     * <p>
     * This must be kept as a back-channel request, and the clientSecret kept away from the browser.
     */
    @POST
    @Path("noauth/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Exchanges an accessCode for an idToken",
            response = Response.class)
    public Response exchangeAccessCode(
            @ApiParam("ExchangeAccessCodeRequest") final ExchangeAccessCodeRequest exchangeAccessCodeRequest) {
        var optionalToken = service.exchangeAccessCode(exchangeAccessCodeRequest);
        return optionalToken.map(
                idToken -> Response.status(Status.OK).entity(idToken).build())
                .orElse(Response.status(Status.UNAUTHORIZED).entity("Invalid client or access code").build());
    }

}
