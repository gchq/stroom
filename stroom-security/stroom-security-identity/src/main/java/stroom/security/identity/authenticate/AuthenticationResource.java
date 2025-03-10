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

package stroom.security.identity.authenticate;

import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.openid.api.OpenId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path(AuthenticationResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication")
public interface AuthenticationResource extends RestResource {

    String BASE_PATH = "/authentication" + ResourcePaths.V1;

    @GET
    @Path("/noauth/getAuthenticationState")
    @NotNull
    @Operation(
            summary = "Get the current authentication state",
            operationId = "getAuthenticationState")
    AuthenticationState getAuthenticationState(@Context @NotNull HttpServletRequest request);

    /**
     * We expect the user to have a session if they're trying to log in.
     * If they don't then they need to be directed to an application that will submit
     * an AuthenticationRequest to /authenticate.
     */
    @POST
    @Path("/noauth/login")
    @NotNull
    @Operation(
            summary = "Handle a login request made using username and password credentials.",
            operationId = "login")
    LoginResponse login(
            @Context @NotNull HttpServletRequest request,
            @Parameter(description = "Credentials", required = true)
            @NotNull LoginRequest loginRequest);

    @GET
    @Path("/noauth/logout")
    @NotNull
    @Operation(
            summary = "Log a user out of their session",
            operationId = "logout")
    Boolean logout(
            @Context @NotNull HttpServletRequest request,
            @QueryParam(OpenId.POST_LOGOUT_REDIRECT_URI) @NotNull String postLogoutRedirectUri,
            @QueryParam(OpenId.STATE) @NotNull String state);

    @POST
    @Path("/noauth/confirmPassword")
    @NotNull
    @Operation(
            summary = "Confirm an authenticated user's current password.",
            operationId = "confirmPassword")
    ConfirmPasswordResponse confirmPassword(
            @Context @NotNull HttpServletRequest request,
            @Parameter(description = "confirmPasswordRequest", required = true)
            @NotNull ConfirmPasswordRequest confirmPasswordRequest);


    @POST
    @Path("/noauth/changePassword")
    @NotNull
    @Operation(
            summary = "Change a user's password.",
            operationId = "changePassword")
    ChangePasswordResponse changePassword(
            @Context @NotNull HttpServletRequest request,
            @Parameter(description = "changePasswordRequest", required = true)
            @NotNull ChangePasswordRequest changePasswordRequest);

    @GET
    @Path("/noauth/reset/{email}")
    @NotNull
    @Operation(
            summary = "Reset a user account using an email address.",
            operationId = "resetEmail")
    Boolean resetEmail(
            @Context @NotNull HttpServletRequest request,
            @PathParam("email") String emailAddress);

    @POST
    @Path("resetPassword")
    @NotNull
    @Operation(
            summary = "Reset an authenticated user's password.",
            operationId = "resetPassword")
    ChangePasswordResponse resetPassword(
            @Context @NotNull HttpServletRequest request,
            @Parameter(description = "changePasswordRequest", required = true)
            @NotNull ResetPasswordRequest req);

    @GET
    @Path("needsPasswordChange")
    @NotNull
    @Operation(
            summary = "Check if a user's password needs changing.",
            operationId = "needsPasswordChange")
    Boolean needsPasswordChange(@QueryParam("email") String email);

    @GET
    @Path("/noauth/fetchPasswordPolicy")
    @NotNull
    @Operation(
            summary = "Get the password policy",
            operationId = "fetchPasswordPolicy")
    PasswordPolicyConfig fetchPasswordPolicy();
}
