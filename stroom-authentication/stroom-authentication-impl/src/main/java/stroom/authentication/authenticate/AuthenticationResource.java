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

package stroom.authentication.authenticate;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.authentication.api.OIDC;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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
@Api(description = "Stroom Authentication API", tags = {"Authentication"})
public interface AuthenticationResource extends RestResource {
    String BASE_PATH = "/authentication" + ResourcePaths.V1;
//    String PATH_POST_AUTHENTICATION_REDIRECT = "/noauth/postAuthenticationRedirect";

    /**
     * We expect the user to have a session if they're trying to log in.
     * If they don't then they need to be directed to an application that will submit
     * an AuthenticationRequest to /authenticate.
     */
    @POST
    @Path("/noauth/login")
    @Timed
    @NotNull
    @ApiOperation(value = "Handle a login request made using username and password credentials.",
            response = String.class, tags = {"Authentication"})
    LoginResponse login(
            @Context @NotNull HttpServletRequest request,
            @QueryParam(OIDC.REDIRECT_URI) final String redirectUri,
            @ApiParam("Credentials") @NotNull Credentials credentials);

    @GET
    @Path("/logout")
    @Timed
    @NotNull
    @ApiOperation(value = "Log a user out of their session")
    Boolean logout(
            @Context @NotNull HttpServletRequest request,
            @QueryParam(OIDC.REDIRECT_URI) @NotNull String redirectUri);

    @GET
    @Path("/noauth/reset/{email}")
    @Timed
    @NotNull
    @ApiOperation(value = "Reset a user account using an email address.",
            response = String.class, tags = {"Authentication"})
    Boolean resetEmail(
            @Context @NotNull HttpServletRequest request,
            @PathParam("email") String emailAddress);

    @POST
    @Path("noauth/changePassword")
    @Timed
    @NotNull
    @ApiOperation(value = "Change a user's password.",
            response = String.class, tags = {"Authentication"})
    ChangePasswordResponse changePassword(
            @Context @NotNull HttpServletRequest request,
            @ApiParam("changePasswordRequest") @NotNull ChangePasswordRequest changePasswordRequest);

    @POST
    @Path("resetPassword")
    @Timed
    @NotNull
    @ApiOperation(value = "Reset an authenticated user's password.",
            response = String.class, tags = {"Authentication"})
    ChangePasswordResponse resetPassword(
            @ApiParam("changePasswordRequest") @NotNull ResetPasswordRequest req);

    @GET
    @Path("needsPasswordChange")
    @Timed
    @NotNull
    @ApiOperation(value = "Check if a user's password needs changing.",
            response = Boolean.class, tags = {"Authentication"})
    Boolean needsPasswordChange(@QueryParam("email") String email);

    @POST
    @Path("noauth/isPasswordValid")
    @Timed
    @NotNull
    @ApiOperation(value = "Returns the length and complexity rules.",
            response = Boolean.class, tags = {"Authentication"})
    PasswordValidationResponse isPasswordValid(
            @Context @NotNull HttpServletRequest request,
            @ApiParam("passwordValidationRequest") @NotNull PasswordValidationRequest passwordValidationRequest);
}
