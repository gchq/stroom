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

package stroom.security.impl;

import stroom.security.shared.AuthFlowResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;

/**
 * Resource for the SPA (Single Page Application) authentication flow.
 * <p>
 * Provides a status endpoint for the SPA to check if the user is authenticated,
 * and a callback endpoint that the IDP redirects to after authentication.
 * </p>
 * <p>
 * This interface is in the impl module (not stroom-core-shared) because the callback
 * method requires servlet types ({@link HttpServletRequest}, {@link HttpServletResponse})
 * which are not available in the GWT-compiled shared module. The GWT UI does not call
 * these endpoints directly — the bootstrap script uses fetch() and the callback is hit
 * by browser navigation from the IdP redirect.
 * </p>
 */
@Tag(name = "Auth Flow")
@Path(AuthFlowResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuthFlowResource extends RestResource {

    String BASE_PATH = "/auth/flow" + ResourcePaths.V1;
    String NOAUTH_PATH = BASE_PATH + ResourcePaths.NO_AUTH;

    @GET
    @Path(ResourcePaths.NO_AUTH + "/status")
    @Operation(
            summary = "Check authentication status for the SPA",
            operationId = "authFlowStatus")
    AuthFlowResponse status(@QueryParam("redirect_uri") String postAuthRedirectUri,
                            @Context @NotNull HttpServletRequest request);

    @GET
    @Path(ResourcePaths.NO_AUTH + "/callback")
    @Produces(MediaType.TEXT_HTML)
    @Operation(
            summary = "OIDC callback endpoint for the SPA authentication flow",
            operationId = "authFlowCallback")
    void callback(@QueryParam("code") String code,
                  @QueryParam("state") String state,
                  @Context @NotNull HttpServletRequest request,
                  @Context @NotNull HttpServletResponse response) throws IOException;
}
