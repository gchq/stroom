package stroom.docstore.shared;

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

@Tag(name = "Document Favourites")
@Path("/docFav" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DocFavResource extends RestResource, DirectRestService {

    @POST
    @Path("/create")
    @Operation(
            summary = "Set a document as a favourite for the current user",
            operationId = "createDocFav")
    void create(@Parameter(description = "docRef", required = true) DocRef docRef);

    @DELETE
    @Path("/delete")
    @Operation(
            summary = "Unset a document as favourite for the current user",
            operationId = "deleteDocFav")
    void delete(@Parameter(description = "docRef", required = true) DocRef docRef);

    @GET
    @Path("/fetchDocFavs")
    @Operation(
            summary = "Retrieve all DocRefs the current user has marked as favourite",
            operationId = "fetchDocFavs")
    List<DocRef> fetchDocFavs();
}
