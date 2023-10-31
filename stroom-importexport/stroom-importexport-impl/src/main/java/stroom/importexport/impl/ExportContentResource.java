package stroom.importexport.impl;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Tag(name = "Export")
@Path("/export" + ResourcePaths.V1)
public interface ExportContentResource extends RestResource {

    @GET
    @Operation(
            summary = "Exports all configuration to a file.",
            operationId = "exportAllContent",
            responses = {
                    @ApiResponse(description = "Returns Stroom content data in a zip file")
            })
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response export();
}
