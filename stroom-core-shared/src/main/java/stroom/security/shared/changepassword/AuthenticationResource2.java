package stroom.security.shared.changepassword;

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
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Path(AuthenticationResource2.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication")
public interface AuthenticationResource2 extends RestResource, DirectRestService {

    String BASE_PATH = "/authentication" + ResourcePaths.V2;

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

    @GET
    @Path("/noauth/fetchPasswordPolicy")
    @NotNull
    @Operation(
            summary = "Get the password policy",
            operationId = "fetchPasswordPolicy")
    InternalIdpPasswordPolicyConfig fetchPasswordPolicy();
}
