package stroom.index.impl;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.docref.DocRef;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.index.shared.IndexDoc;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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
@Path("/index" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface NewUIIndexResource extends RestResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    Set<DocRef> listDocuments();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import")
    @ApiOperation(
            value = "Submit an import request",
            response = DocRef.class)
    DocRef importDocument(@ApiParam("DocumentData") Base64EncodedDocumentData documentData);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/export")
    @ApiOperation(
            value = "Submit an export request",
            response = Base64EncodedDocumentData.class)
    Base64EncodedDocumentData exportDocument(@ApiParam("DocRef") DocRef docRef);

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
