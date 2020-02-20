package stroom.pipeline;

import com.google.common.base.Strings;
import io.swagger.annotations.Api;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.factory.PipelineDataValidator;
import stroom.pipeline.factory.PipelineStackLoader;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Api(value = "pipeline - /v1")
@Path("/pipelines" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public class NewUiPipelineResource implements RestResource {
    private final PipelineStore pipelineStore;
    private final PipelineStackLoader pipelineStackLoader;
    private final PipelineDataValidator pipelineDataValidator;
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    private static class PipelineDTO extends DocRef {
        private DocRef parentPipeline;
        private String description;
        private List<PipelineData> configStack;
        private PipelineData merged;

        PipelineDTO() {

        }

        PipelineDTO(final DocRef parentPipeline,
                    final DocRef docRef,
                    final String description,
                    final List<PipelineData> configStack) {
            super(docRef.getType(), docRef.getUuid(),docRef.getName());
            this.parentPipeline = parentPipeline;
            this.description = description;
            this.configStack = configStack;
            this.merged = new PipelineDataMerger().merge(configStack).createMergedData();
        }

        public String getDescription() {
            return description;
        }

        public DocRef getParentPipeline() {
            return parentPipeline;
        }

        public List<PipelineData> getConfigStack() {
            return configStack;
        }

        public PipelineData getMerged() {
            return merged;
        }
    }

    @Inject
    public NewUiPipelineResource(final PipelineStore pipelineStore,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final PipelineDataValidator pipelineDataValidator,
                                 final SecurityContext securityContext,
                                 final PipelineScopeRunnable pipelineScopeRunnable) {
        this.pipelineStore = pipelineStore;
        this.pipelineStackLoader = pipelineStackLoader;
        this.pipelineDataValidator = pipelineDataValidator;
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    private DocRef getDocRef(final String pipelineId) {
        return new DocRef.Builder()
                .uuid(pipelineId)
                .type(PipelineDoc.DOCUMENT_TYPE)
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@QueryParam("offset") Integer offset,
                           @QueryParam("pageSize") Integer pageSize,
                           @QueryParam("filter") String filter) {
        return securityContext.secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && offset == null) || (pageSize == null && offset != null)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("A pagination request requires both a pageSize and an offset").build();
            }

            // TODO: The below isn't very efficient because it grabs and processes on all the pipelines. Better to
            // do paging on the database. But this sort of paging is done like this elsewhere so it's a general issue.
            List<DocRef> pipelines = pipelineStore.list();
            int totalPipelines = pipelines.size();

            // Filter
            if (!Strings.isNullOrEmpty(filter)) {
                pipelines = pipelines.stream().filter(pipeline -> pipeline.getName().contains(filter)).collect(Collectors.toList());
            }

            // Sorting
            pipelines = pipelines.stream().sorted(Comparator.comparing(DocRef::getName)).collect(Collectors.toList());

            // Paging
            if (pageSize != null && offset != null) {
                final int fromIndex = offset * pageSize;
                int toIndex = fromIndex + pageSize;
                if (toIndex >= pipelines.size()) {
                    toIndex = pipelines.size() == 0 ? 0 : pipelines.size();
                }
                pipelines = pipelines.subList(fromIndex, toIndex);
            }

            // Produce response
            final List<DocRef> results = pipelines;
            Object response = new Object() {
                public int total = totalPipelines;
                public List<DocRef> pipelines = results;
            };
            return Response.ok(response).build();
        });
    }

    @GET
    @Path("/{pipelineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(@PathParam("pipelineId") final String pipelineId) {
        return securityContext.secureResult(() -> pipelineScopeRunnable.scopeResult(() -> {
            // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
            return securityContext.useAsReadResult(() -> fetchInScope(pipelineId));
        }));
    }

    private Response fetchInScope(final String pipelineId) {
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(getDocRef(pipelineId));
        final List<PipelineData> configStack = new ArrayList<>();

        final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);

        final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
        for (final PipelineDoc pipe : pipelines) {
            final PipelineData pipelineData = pipe.getPipelineData();

            // Validate the pipeline data and add element and property type
            // information.
            final SourcePipeline source = new SourcePipeline(pipe);
            pipelineDataValidator.validate(source, pipelineData, elementMap);
            configStack.add(pipelineData);

        }

        final PipelineDTO dto = new PipelineDTO(
                pipelineDoc.getParentPipeline(),
                DocRefUtil.create(pipelineDoc),
                pipelineDoc.getDescription(),
                configStack);
        return Response.ok(dto).build();
    }

    @POST
    @Path("/{parentPipelineId}/inherit")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createInherited(@PathParam("parentPipelineId") final String pipelineId,
                                    final DocRef parentPipeline) {

        return pipelineScopeRunnable.scopeResult(() -> {
            final PipelineDoc parentDoc = pipelineStore.readDocument(getDocRef(pipelineId));

            final DocRef docRef = pipelineStore.createDocument(String.format("Child of %s", parentDoc.getName()));

            final PipelineDoc pipelineDoc = pipelineStore.readDocument(docRef);
            if (pipelineDoc != null) {
                pipelineDoc.setParentPipeline(parentPipeline);
                pipelineStore.writeDocument(pipelineDoc);
            }

            return fetchInScope(docRef.getUuid());
        });
    }

    @POST
    @Path("/{pipelineId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("pipelineId") final String pipelineId,
                         final PipelineDTO pipelineDocUpdates) {
        pipelineScopeRunnable.scopeRunnable(() -> {
            // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
            securityContext.useAsRead(() -> {
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(getDocRef(pipelineId));

                if (pipelineDoc != null) {
                    pipelineDoc.setDescription(pipelineDocUpdates.getDescription());
                    pipelineDocUpdates.getConfigStack().forEach(pipelineDoc::setPipelineData); // will have the effect of setting last one
                    pipelineStore.writeDocument(pipelineDoc);
                }
            });
        });

        return Response.noContent().build();
    }
}
