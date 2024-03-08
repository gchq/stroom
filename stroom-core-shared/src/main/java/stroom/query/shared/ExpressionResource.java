package stroom.query.shared;

import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Expressions")
@Path(ExpressionResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ExpressionResource extends RestResource, DirectRestService {

    String BASE_PATH = "/expression" + ResourcePaths.V1;

    @POST
    @Path("/validate")
    @Operation(
            summary = "Validate an expression",
            operationId = "validate")
    ValidateExpressionResult validate(
            @Parameter(description = "request", required = true) ValidateExpressionRequest request);


}
