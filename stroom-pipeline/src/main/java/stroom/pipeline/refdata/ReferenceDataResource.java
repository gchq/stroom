package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;

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
import java.util.List;

@Api(value = "reference data - /v1")
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
    List<RefStoreEntry> entries(@QueryParam("limit") final Integer limit);

    @POST
    @Path(LOOKUP_SUB_PATH)
    String lookup(@Valid @NotNull final RefDataLookupRequest refDataLookupRequest);

    @DELETE
    @Path(PURGE_SUB_PATH + "/{purgeAge}")
    void purge(@NotNull @PathParam("purgeAge") final String purgeAge);
}
