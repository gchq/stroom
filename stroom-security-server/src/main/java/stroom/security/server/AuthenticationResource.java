package stroom.security.server;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.codec.Base64;
import org.glassfish.jersey.server.ContainerRequest;
import stroom.entity.shared.EntityServiceException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 * This is used for unsecured authentication requests.
 * <p>
 * It is explicitly excluded from the Shiro filter in SecurityConfiguration.shiroFilter().
 */
@Path("/authentication/v1")
@Produces(MediaType.APPLICATION_JSON)
public class AuthenticationResource {
    private final AuthenticationService authenticationService;
    private final JWTService jwtService;

    public AuthenticationResource(final AuthenticationService authenticationService, final JWTService jwtService) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    private static Optional<UsernamePasswordToken> extractCredentialsFromHeader(final ContainerRequest request) {
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

    @GET
    @Path("/getToken")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Timed
    // We're going to use BasicHttpAuthentication by passing the token in with the header.
    public Response getToken(final ContainerRequest request) {
        Optional<UsernamePasswordToken> credentials = extractCredentialsFromHeader(request);

        if (credentials.isPresent()) {
            // FIXME: Bad credentials are not an exceptional case and I shouldn't have to use try-catch to detect one.
            try {
                // If we're not logged in the getting a token will fail.
                authenticationService.login(credentials.get().getUsername(), new String(credentials.get().getPassword()));

                final String token = jwtService.getTokenFor(credentials.get().getUsername());
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
}
