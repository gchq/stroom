package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
