/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Path(AuthenticationResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication")
public interface AuthenticationResource extends RestResource, DirectRestService {

    String BASE_PATH = "/authentication" + ResourcePaths.V1;

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
    LoginResponse login(@Parameter(description = "Credentials", required = true)
                        @NotNull LoginRequest loginRequest);

    @GET
    @Path("/noauth/logout")
    @NotNull
    @Operation(
            summary = "Log a user out of their session",
            operationId = "logout")
    Boolean logout(@QueryParam("post_logout_redirect_uri") @NotNull String postLogoutRedirectUri);

    @POST
    @Path("/noauth/confirmPassword")
    @NotNull
    @Operation(
            summary = "Confirm an authenticated user's current password.",
            operationId = "confirmPassword")
    ConfirmPasswordResponse confirmPassword(
            @Parameter(description = "confirmPasswordRequest", required = true)
            @NotNull ConfirmPasswordRequest confirmPasswordRequest);


    @POST
    @Path("/noauth/changePassword")
    @NotNull
    @Operation(
            summary = "Change a user's password.",
            operationId = "changePassword")
    ChangePasswordResponse changePassword(
            @Parameter(description = "changePasswordRequest", required = true)
            @NotNull ChangePasswordRequest changePasswordRequest);

    @POST
    @Path("/noauth/reset")
    @NotNull
    @Operation(
            summary = "Reset a user account using an email address.",
            operationId = "resetEmail")
    Boolean resetEmail(
            @Parameter(description = "email", required = true) @NotNull String emailAddress);

    @GET
    @Path("/noauth/fetchPasswordPolicy")
    @NotNull
    @Operation(
            summary = "Get the password policy",
            operationId = "fetchPasswordPolicy")
    InternalIdpPasswordPolicyConfig fetchPasswordPolicy();
}
