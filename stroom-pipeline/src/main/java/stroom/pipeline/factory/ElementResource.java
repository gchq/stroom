package stroom.pipeline.factory;

import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Api(tags = "Elements")
@Path("/elements" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ElementResource implements RestResource {

    private final Provider<ElementRegistryFactory> elementRegistryFactoryProvider;
    private final Provider<PipelineScopeRunnable> pipelineScopeRunnableProvider;

    @Inject
    public ElementResource(final Provider<ElementRegistryFactory> elementRegistryFactoryProvider,
                           final Provider<PipelineScopeRunnable> pipelineScopeRunnableProvider) {
        this.elementRegistryFactoryProvider = elementRegistryFactoryProvider;
        this.pipelineScopeRunnableProvider = pipelineScopeRunnableProvider;
    }

    @GET
    @Path("/elements")
    @ApiOperation(
            value = "Get all pipeline element types",
            response = PipelineElementType.class,
            responseContainer = "Set")
    public Response getElements() {
        Set<PipelineElementType> result = new HashSet<>();

        pipelineScopeRunnableProvider.get().scopeRunnable(() -> {
            Map<PipelineElementType, Map<String, PipelinePropertyType>> pts = elementRegistryFactoryProvider.get().get()
                    .getPropertyTypes();
            result.addAll(pts.keySet());
        });

        return Response.ok(result).build();
    }

    @GET
    @Path("/elementProperties")
    @ApiOperation(
            value = "Get a nested map of pipeline element properties, keyed on element type then property name.",
            response = PipelineElementType.class,
            responseContainer = "Map")
    public Response getElementProperties() {
        Map<PipelineElementType, Map<String, PipelinePropertyType>> result = new HashMap<>();

        pipelineScopeRunnableProvider.get().scopeRunnable(() -> {
            Map<PipelineElementType, Map<String, PipelinePropertyType>> pts = elementRegistryFactoryProvider.get()
                    .get().getPropertyTypes();
            result.putAll(pts);
        });

        return Response.ok(result).build();
    }
}
