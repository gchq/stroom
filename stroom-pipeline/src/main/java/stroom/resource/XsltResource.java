package stroom.resource;

import io.swagger.annotations.Api;
import stroom.docref.DocRef;
import stroom.pipeline.XsltStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.security.Security;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "xslt - /v1",
        description = "Stroom XSLT API")
@Path("/xslt/v1")
@Produces(MediaType.APPLICATION_JSON)
public class XsltResource {
    private final XsltStore xsltStore;
    private final Security security;

    @Inject
    public XsltResource(final XsltStore xsltStore, final Security security) {
        this.xsltStore = xsltStore;
        this.security = security;
    }

    private DocRef getDocRef(final String xsltId) {
        return new DocRef.Builder()
                .uuid(xsltId)
                .type(XsltDoc.DOCUMENT_TYPE)
                .build();
    }

    @GET
    @Path("/{xsltId}")
    @Produces(MediaType.APPLICATION_XML)
    public Response fetch(@PathParam("xsltId") final String xsltId) {
        return security.secureResult(() -> {
            final XsltDoc xsltDoc = xsltStore.readDocument(getDocRef(xsltId));

            return Response.ok(xsltDoc.getData()).build();
        });
    }

    @POST
    @Path("/{xsltId}")
    @Produces(MediaType.APPLICATION_XML)
    @Consumes(MediaType.APPLICATION_XML)
    public Response save(@PathParam("xsltId") final String xsltId,
                         final String xsltData) {
        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        security.useAsRead(() -> {
            final XsltDoc xsltDoc = xsltStore.readDocument(getDocRef(xsltId));

            if (xsltDoc != null) {
                xsltDoc.setData(xsltData);
                xsltStore.writeDocument(xsltDoc);
            }
        });

        return Response.ok().build();
    }
}
