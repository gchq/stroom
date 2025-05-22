package stroom.appstore.shared;

import stroom.util.shared.PageRequest;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

/**
 * Interface for the REST API for the App Store functionality.
 */
@Tag(name = "AppStoreContentPacks")
@Path("/appstore")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AppStoreResource extends RestResource, DirectRestService {

    /**
     * Method returns a paged list of Content Packs available from the
     * App Store.
     * @return a list of available content packs.
     */
    @GET
    @Path("/list")
    @Operation(
            summary = "Lists App Store Content Packs",
            operationId = "listAppStoreContentPacks")
    ResultPage<AppStoreContentPack> list(PageRequest pageRequest);

}
