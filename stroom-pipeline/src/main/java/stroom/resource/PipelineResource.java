package stroom.resource;

import com.codahale.metrics.health.HealthCheck;
import io.swagger.annotations.Api;
import org.eclipse.jetty.http.HttpStatus;
import stroom.docref.DocRef;
import stroom.docstore.db.DocumentNotFoundException;
import stroom.guice.PipelineScopeRunnable;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataValidator;
import stroom.pipeline.factory.PipelineStackLoader;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.security.Security;
import stroom.util.HasHealthCheck;
import stroom.util.shared.SharedList;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

@Api(
        value = "pipeline - /v1",
        description = "Stroom Pipeline API")
@Path("/pipelines/v1")
@Produces(MediaType.APPLICATION_JSON)
public class PipelineResource implements HasHealthCheck  {

    private final PipelineStore pipelineStore;
    private final PipelineStackLoader pipelineStackLoader;
    private final PipelineDataValidator pipelineDataValidator;
    private final Security security;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    @Inject
    public PipelineResource(final PipelineStore pipelineStore,
                            final PipelineStackLoader pipelineStackLoader,
                            final PipelineDataValidator pipelineDataValidator,
                            final Security security,
                            final PipelineScopeRunnable pipelineScopeRunnable) {
        this.pipelineStore = pipelineStore;
        this.pipelineStackLoader = pipelineStackLoader;
        this.pipelineDataValidator = pipelineDataValidator;
        this.security = security;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    @GET
    @Path("/{pipelineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(@PathParam("pipelineId") final String pipelineId) {
        return security.secureResult(() -> {
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(new DocRef.Builder()
                    .uuid(pipelineId)
                    .type(PipelineDoc.DOCUMENT_TYPE)
                    .build());
            final SharedList<PipelineData> result = new SharedList<>();

            pipelineScopeRunnable.scopeRunnable(() -> {
                // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
                security.useAsRead(() -> {
                    final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);

                    final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
                    for (final PipelineDoc pipe : pipelines) {
                        final PipelineData pipelineData = pipe.getPipelineData();

                        // Validate the pipeline data and add element and property type
                        // information.
                        final SourcePipeline source = new SourcePipeline(pipe);
                        pipelineDataValidator.validate(source, pipelineData, elementMap);
                        result.add(pipelineData);
                    }

                });
            });

            return Response.ok(result).build();
        });
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}
