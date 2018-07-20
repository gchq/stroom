package stroom.resource;

import com.codahale.metrics.health.HealthCheck;
import io.swagger.annotations.Api;
import org.eclipse.jetty.http.HttpStatus;
import stroom.docref.DocRef;
import stroom.docstore.db.DocumentNotFoundException;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

@Api(
        value = "pipeline - /v1",
        description = "Stroom Pipeline API")
@Path("/pipelines/v1")
@Produces(MediaType.APPLICATION_JSON)
public class PipelineResource implements HasHealthCheck  {

    private final PipelineStore pipelineStore;

    @Inject
    public PipelineResource(final PipelineStore pipelineStore) {
        this.pipelineStore = pipelineStore;
    }

    @GET
    @Path("/{pipelineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(@PathParam("pipelineId") final String pipelineId) {
        try {
            final PipelineDoc doc = pipelineStore.readDocument(new DocRef.Builder()
                    .uuid(pipelineId)
                    .type(PipelineDoc.DOCUMENT_TYPE)
                    .build());

            final PipelineData pipelineData = Optional.of(doc)
                    .map(PipelineDoc::getPipelineData)
                    .orElseThrow(() -> new NotFoundException("Pipeline Data was Missing"));

            return Response.ok(pipelineData).build();
        } catch (NotFoundException | DocumentNotFoundException e) {
            return Response.status(HttpStatus.NOT_FOUND_404).build();
        }
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}
