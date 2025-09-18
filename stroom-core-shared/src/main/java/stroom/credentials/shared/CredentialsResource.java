package stroom.credentials.shared;

import stroom.util.shared.PageRequest;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Credentials")
@Path("/credentials")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CredentialsResource extends RestResource, DirectRestService {

    @POST
    @Path("/list")
    @Operation(
            summary = "Lists credentials",
            operationId = "listCredentials")
    ResultPage<Credentials> list(PageRequest pageRequest);

}
