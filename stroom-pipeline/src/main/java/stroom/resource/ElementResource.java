package stroom.resource;

import io.swagger.annotations.Api;
import stroom.pipeline.scope.PipelineScopeRunnable;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Api(
        value = "elements - /v1",
        description = "Stroom Element API")
@Path("/elements/v1")
@Produces(MediaType.APPLICATION_JSON)
public class ElementResource {

    private final ElementRegistryFactory pipelineElementRegistryFactory;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    @Inject
    public ElementResource(final ElementRegistryFactory pipelineElementRegistryFactory,
                           final PipelineScopeRunnable pipelineScopeRunnable) {
        this.pipelineElementRegistryFactory = pipelineElementRegistryFactory;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    @GET
    @Path("/elements")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getElements() {
        Set<PipelineElementType> result = new HashSet<>();

        pipelineScopeRunnable.scopeRunnable(() -> {
            Map<PipelineElementType, Map<String, PipelinePropertyType>> pts = pipelineElementRegistryFactory.get().getPropertyTypes();
            result.addAll(pts.keySet());
        });

        return Response.ok(result).build();
    }

    @GET
    @Path("/elementProperties")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getElementProperties() {
        Map<PipelineElementType, Map<String, PipelinePropertyType>> result = new HashMap<>();

        pipelineScopeRunnable.scopeRunnable(() -> {
            Map<PipelineElementType, Map<String, PipelinePropertyType>> pts = pipelineElementRegistryFactory.get().getPropertyTypes();
            result.putAll(pts);
        });

        return Response.ok(result).build();
    }
}
