package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Reference Data")
@Path(ReferenceDataResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReferenceDataResource extends RestResource {

    String BASE_PATH = "/refData" + ResourcePaths.V1;

    String ENTRIES_SUB_PATH = "/entries";
    String LOOKUP_SUB_PATH = "/lookup";
    String PURGE_SUB_PATH = "/purge";

    @GET
    @Path(ENTRIES_SUB_PATH)
    @Operation(summary = "List entries from the reference data store on the node called. This is primarily intended " +
            "for small scale debugging in non-production environments. If no limit is set a default limit is applied " +
            "else the results will be limited to limit entries.")
    List<RefStoreEntry> entries(@QueryParam("limit") final Integer limit);

    @POST
    @Path(LOOKUP_SUB_PATH)
    @Operation(summary = "Perform a reference data lookup using the supplied lookup request.")
    String lookup(@Valid @NotNull final RefDataLookupRequest refDataLookupRequest);

    @DELETE
    @Path(PURGE_SUB_PATH + "/{purgeAge}")
    @Operation(summary = "Explicitly delete all entries that are older than purgeAge.")
    void purge(@NotNull @PathParam("purgeAge") final String purgeAge);
}
