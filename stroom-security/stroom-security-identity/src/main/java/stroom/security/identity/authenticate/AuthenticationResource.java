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

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

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
@Api(tags = "Authentication")
public interface AuthenticationResource extends RestResource {

    String BASE_PATH = "/authentication" + ResourcePaths.V1;

    @GET
    @Path("/noauth/getAuthenticationState")
    @Timed
    @NotNull
    @ApiOperation("Get the current authentication state")
    AuthenticationState getAuthenticationState(@Context @NotNull HttpServletRequest request);

    /**
     * We expect the user to have a session if they're trying to log in.
     * If they don't then they need to be directed to an application that will submit
     * an AuthenticationRequest to /authenticate.
     */
    @POST
    @Path("/noauth/login")
    @Timed
    @NotNull
    @ApiOperation("Handle a login request made using username and password credentials.")
    LoginResponse login(
            @Context @NotNull HttpServletRequest request,
            @ApiParam("Credentials") @NotNull LoginRequest loginRequest);

    @GET
    @Path("/logout")
    @Timed
    @NotNull
    @ApiOperation("Log a user out of their session")
    Boolean logout(
            @Context @NotNull HttpServletRequest request,
            @QueryParam(OpenId.REDIRECT_URI) @NotNull String redirectUri);

    @POST
    @Path("/noauth/confirmPassword")
    @Timed
    @NotNull
    @ApiOperation("Confirm an authenticated users current password.")
    ConfirmPasswordResponse confirmPassword(
            @Context @NotNull HttpServletRequest request,
            @ApiParam("confirmPasswordRequest") @NotNull ConfirmPasswordRequest confirmPasswordRequest);


    @POST
    @Path("/noauth/changePassword")
    @Timed
    @NotNull
    @ApiOperation("Change a user's password.")
    ChangePasswordResponse changePassword(
            @Context @NotNull HttpServletRequest request,
            @ApiParam("changePasswordRequest") @NotNull ChangePasswordRequest changePasswordRequest);

    @GET
    @Path("/noauth/reset/{email}")
    @Timed
    @NotNull
    @ApiOperation("Reset a user account using an email address.")
    Boolean resetEmail(
            @Context @NotNull HttpServletRequest request,
            @PathParam("email") String emailAddress);

    @POST
    @Path("resetPassword")
    @Timed
    @NotNull
    @ApiOperation("Reset an authenticated user's password.")
    ChangePasswordResponse resetPassword(
            @Context @NotNull HttpServletRequest request,
            @ApiParam("changePasswordRequest") @NotNull ResetPasswordRequest req);

    @GET
    @Path("needsPasswordChange")
    @Timed
    @NotNull
    @ApiOperation("Check if a user's password needs changing.")
    Boolean needsPasswordChange(@QueryParam("email") String email);

    @GET
    @Path("/noauth/fetchPasswordPolicy")
    @Timed
    @NotNull
    @ApiOperation("Get the password policy")
    PasswordPolicyConfig fetchPasswordPolicy();
}
