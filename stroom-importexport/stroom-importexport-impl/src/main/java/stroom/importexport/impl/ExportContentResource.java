package stroom.importexport.impl;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
