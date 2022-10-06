package stroom.data.store.api;

import stroom.meta.shared.FindMetaCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Tag(name = "Data Download")
@Path("/dataDownload" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_OCTET_STREAM)
@Consumes(MediaType.APPLICATION_JSON)
public interface DataDownload extends RestResource {

    /*
    This method is defined separately to `DataResource` due to GWT incompatibility with the jaxax `Response` class.
     */
    @POST
    @Path("downloadZip")
    @Operation(
            summary = "Retrieve content matching the provided criteria as a zip file",
            operationId = "downloadZip",
            responses = {
                    @ApiResponse(description = "Returns Stroom content data as a zip file")
            })
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadZip(@Parameter(description = "criteria", required = true) FindMetaCriteria criteria);
}
