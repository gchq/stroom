/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.codec.Base64;
import org.glassfish.jersey.server.ContainerRequest;
import stroom.entity.shared.EntityServiceException;
import stroom.security.Insecure;
import stroom.security.server.AuthenticationService;
import stroom.security.server.AuthorisationService;
import stroom.security.server.JWTUtils;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 * This is used for unsecured auth requests.
 *
 * It is explicitly excluded from the Shiro filter in SecurityConfiguration.shiroFilter().
 */
@Path("auth")
@Produces(MediaType.APPLICATION_JSON)
@Insecure
public class AuthResource {

    private AuthenticationService authenticationService;
    private AuthorisationService authorisationService;

    @GET
    @Path("/getToken")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Timed
    @Insecure
    // We're going to use BasicHttpAuthentication by passing the token in with the header.
    public Response getToken(ContainerRequest request) {
        Optional<UsernamePasswordToken> credentials = extractCredentialsFromHeader(request);

        if (credentials.isPresent()) {
            // FIXME: Bad credentials are not an exceptional case and I shouldn't have to use try-catch to detect one.
            try {
                // If we're not logged in the getting a token will fail.
                authenticationService.login(credentials.get().getUsername(), new String(credentials.get().getPassword()));

                String token = JWTUtils.getTokenFor(credentials.get().getUsername());

                return Response
                        .ok(token, MediaType.TEXT_PLAIN)
                        .build();
            } catch (EntityServiceException e) {
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(e.getMessage())
                        .build();
            }
        } else {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("This method expects a username and password (like this: 'username:password') in the Authorization header, encoded as Base64.")
                    .build();
        }
    }

    @POST
    @Path("isAuthorised")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    /**
     * Authenticates using JWT
     */
    public Response isAuthorisedForStatistic(AuthorisationRequest authorisationRequest) {
        boolean result = authorisationService.hasDocumentPermission(
                authorisationRequest.getDocRef().getType(),
                authorisationRequest.getDocRef().getUuid(),
                authorisationRequest.getPermissions());
        return result ? Response.ok().build() : Response.status(Response.Status.UNAUTHORIZED).build();
    }

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setAuthorisationService(AuthorisationService authorisationService) {
        this.authorisationService = authorisationService;
    }

    private static Optional<UsernamePasswordToken> extractCredentialsFromHeader(ContainerRequest request) {
        try {
            String authorizationHeader = request.getHeaderString("Authorization");
            if (Strings.isNullOrEmpty(authorizationHeader) || !authorizationHeader.contains("Basic")) {
                return Optional.empty();
            } else {
                String credentials = Base64.decodeToString(authorizationHeader.substring(6));
                String[] splitCredentials = credentials.split(":");
                UsernamePasswordToken usernamePasswordToken = new UsernamePasswordToken(splitCredentials[0], splitCredentials[1]);
                return Optional.of(usernamePasswordToken);
            }
        } catch (Exception e) {
            // For example if the username/password pair is badly formed and splitting fails.
            return Optional.empty();
        }
    }
}