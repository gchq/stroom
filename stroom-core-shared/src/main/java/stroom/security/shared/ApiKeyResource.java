package stroom.security.shared;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "API Key")
@Path("/apikey" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ApiKeyResource extends RestResource, DirectRestService, FetchWithIntegerId<ApiKey> {

    @POST
    @Path("/")
    @Operation(
            summary = "Creates a new API key",
            operationId = "createApiKey")
    CreateApiKeyResponse create(
            @Parameter(description = "request", required = true) final CreateApiKeyRequest request);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch a dictionary doc by its UUID",
            operationId = "fetchApiKey")
    ApiKey fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Update a dictionary doc",
            operationId = "updateApiKey")
    ApiKey update(@PathParam("id") final int id,
                  @Parameter(description = "apiKey", required = true) final ApiKey apiKey);

    @Operation(
            summary = "Delete a API key by ID.",
            operationId = "deleteApiKey")
    @DELETE
    @Path("/{id}")
    void delete(@PathParam("{id}") final int id);

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the API keys matching the supplied criteria",
            operationId = "findApiKeysByCriteria")
    ResultPage<ApiKey> find(@Parameter(description = "criteria", required = true) FindApiKeyCriteria criteria);
}
