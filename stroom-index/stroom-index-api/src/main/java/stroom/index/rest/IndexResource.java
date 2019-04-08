package stroom.index.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.docref.DocRef;
import stroom.importexport.api.OldDocumentData;
import stroom.index.shared.IndexDoc;
import stroom.util.RestResource;
import stroom.util.shared.DocRefs;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

@Api(value = "index - /v1")
@Path("/index/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface IndexResource extends RestResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    DocRefs listDocuments();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import")
    @ApiOperation(
            value = "Submit an import request",
            response = DocRef.class)
    DocRef importDocument(@ApiParam("DocumentData") OldDocumentData documentData);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/export")
    @ApiOperation(
            value = "Submit an export request",
            response = OldDocumentData.class)
    OldDocumentData exportDocument(@ApiParam("DocRef") DocRef docRef);

    @GET
    @Path("/{indexUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    Response fetch(@PathParam("indexUuid") String indexUuid);

    @POST
    @Path("/{indexUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response save(@PathParam("indexUuid") String indexUuid, IndexDoc updates);
}
