package stroom.pipeline.xslt;

import io.swagger.annotations.Api;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;
import stroom.util.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "xslt - /v1")
@Path("/xslt/v1")
@Produces(MediaType.APPLICATION_JSON)
public class XsltResource implements RestResource {
    private final XsltStore xsltStore;
    private final SecurityContext securityContext;

    @Inject
    public XsltResource(final XsltStore xsltStore, final SecurityContext securityContext) {
        this.xsltStore = xsltStore;
        this.securityContext = securityContext;
    }

    private DocRef getDocRef(final String xsltId) {
        return new DocRef.Builder()
                .uuid(xsltId)
                .type(XsltDoc.DOCUMENT_TYPE)
                .build();
    }

    private static class XsltDTO extends DocRef {
        private String description;
        private String data;

        XsltDTO() {

        }

        XsltDTO(final XsltDoc doc) {
            super(XsltDoc.DOCUMENT_TYPE, doc.getUuid(), doc.getName());
            setData(doc.getData());
            setDescription(doc.getDescription());
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    @GET
    @Path("/{xsltId}")
    public Response fetch(@PathParam("xsltId") final String xsltId) {
        return securityContext.secureResult(() -> {
            final XsltDoc xsltDoc = xsltStore.readDocument(getDocRef(xsltId));
            if (null != xsltDoc) {
                return Response.ok(new XsltDTO(xsltDoc)).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        });
    }

    @POST
    @Path("/{xsltId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("xsltId") final String xsltId,
                         final XsltDTO xsltDto) {
        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        securityContext.useAsRead(() -> {
            final XsltDoc xsltDoc = xsltStore.readDocument(getDocRef(xsltId));

            if (xsltDoc != null) {
                xsltDoc.setDescription(xsltDto.getDescription());
                xsltDoc.setData(xsltDto.getData());
                xsltStore.writeDocument(xsltDoc);
            }
        });

        return Response.noContent().build();
    }
}
