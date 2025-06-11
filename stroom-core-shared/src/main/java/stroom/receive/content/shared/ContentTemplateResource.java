package stroom.receive.content.shared;

import stroom.query.api.datasource.QueryField;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Set;

@Path(ContentTemplateResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Templates")
public interface ContentTemplateResource extends RestResource, DirectRestService {

    String BASE_PATH = "/contentTemplates" + ResourcePaths.V1;

    @GET
    @Path("/")
    @Operation(
            summary = "Get content templates",
            operationId = "fetchContentTemplates")
    ContentTemplates fetch();

    @PUT
    @Path("/")
    @Operation(
            summary = "Update content templates",
            operationId = "updateContentTemplates")
    ContentTemplates update(
            @Parameter(description = "contentTemplates", required = true) ContentTemplates contentTemplates);

    @GET
    @Path("/fields")
    @Operation(
            summary = "Get the list of fields for use in the match expression.",
            operationId = "fetchFields")
    Set<QueryField> fetchFields();
}
