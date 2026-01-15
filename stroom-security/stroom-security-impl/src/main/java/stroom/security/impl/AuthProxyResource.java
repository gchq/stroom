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

import stroom.security.common.impl.ClientCredentials;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;


/**
 * Acts as a proxy for the Identity Provider. This is to allow callers with no details of the
 * identity provider, (other than the {@link ClientCredentials}) to make a token request on the
 * identity provider.
 * No authentication required as we are just proxying for unauthenticated endpoints on the IDP.
 */
@Path("/authproxy/v1/noauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "AuthProxy")
public interface AuthProxyResource extends RestResource {

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/fetchClientCredsToken")
    @Operation(
            summary = "Fetch an access token from the configured IDP using the supplied client credentials",
            operationId = "fetchClientCredsToken")
    String fetchToken(@Parameter(description = "clientCredentials", required = true
    ) final ClientCredentials clientCredentials);
}
