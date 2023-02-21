package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Explorer Favourites")
@Path("/explorerFavourite" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ExplorerFavouriteResource extends RestResource, DirectRestService {

    @POST
    @Path("/createUserFavourite")
    @Operation(
            summary = "Set a document as a favourite for the current user",
            operationId = "createUserFavourite")
    void createUserFavourite(@Parameter(description = "docRef", required = true) DocRef docRef);

    @DELETE
    @Path("/deleteUserFavourite")
    @Operation(
            summary = "Unset a document as favourite for the current user",
            operationId = "deleteUserFavourite")
    void deleteUserFavourite(@Parameter(description = "docRef", required = true) DocRef docRef);

    @GET
    @Path("/getUserFavourites")
    @Operation(
            summary = "Retrieve all DocRefs the current user has marked as favourite",
            operationId = "getUserFavourites")
    List<DocRef> getUserFavourites();
}
