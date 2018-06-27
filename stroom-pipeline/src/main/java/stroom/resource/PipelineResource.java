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
import stroom.pipeline.shared.data.*;
import stroom.security.Security;
import stroom.util.HasHealthCheck;
import stroom.util.shared.SharedList;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import java.util.*;
import java.util.function.Function;
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

    private static final class PipelineElementDTO {
        private final String id;
        private final String type;

        PipelineElementDTO(final PipelineElement element) {
            this.id = element.getId();
            this.type = element.getType();
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }
    }

    private static final class PipelinePropertyDTO {
        private final String element;
        private final String name;
        private final PipelinePropertyValue value;

        PipelinePropertyDTO(final PipelineProperty property) {
            this.element = property.getElement();
            this.name = property.getName();
            this.value = property.getValue();
        }

        public String getElement() {
            return element;
        }

        public String getName() {
            return name;
        }

        public PipelinePropertyValue getValue() {
            return value;
        }
    }

    private static final class PipelineLinkDTO {
        private final String from;
        private final String to;

        PipelineLinkDTO(final PipelineLink link) {
            this.from = link.getFrom();
            this.to = link.getTo();
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }
    }

    private static final class PipelineAddRemoveDTO<Domain, DTO> {
        private final List<DTO> add;
        private final List<DTO> remove;

        PipelineAddRemoveDTO(final PipelineData data,
                         final Function<Domain, DTO> convert,
                         final Function<PipelineData, List<Domain>> getAdd,
                         final Function<PipelineData, List<Domain>> getRemove) {
            this.add = getAdd.apply(data).stream().map(convert).collect(Collectors.toList());
            this.remove = getRemove.apply(data).stream().map(convert).collect(Collectors.toList());
        }

        public List<DTO> getAdd() {
            return add;
        }

        public List<DTO> getRemove() {
            return remove;
        }
    }

    private static class PipelineDataDTO {
        final PipelineAddRemoveDTO<PipelineElement, PipelineElementDTO> elements;
        final PipelineAddRemoveDTO<PipelineProperty, PipelinePropertyDTO> properties;
        final PipelineReferences pipelineReferences;
        final PipelineAddRemoveDTO<PipelineLink, PipelineLinkDTO> links;

        PipelineDataDTO(final PipelineData data) {
            this.elements = new PipelineAddRemoveDTO<>(data,
                    PipelineElementDTO::new,
                    (d) -> d.getElements().getAdd(),
                    (d) -> d.getElements().getRemove()
            );
            this.properties = new PipelineAddRemoveDTO<>(data,
                    PipelinePropertyDTO::new,
                    (d) -> d.getProperties().getAdd(),
                    (d) -> d.getProperties().getRemove()
            );
            this.pipelineReferences = data.getPipelineReferences();
            this.links = new PipelineAddRemoveDTO<>(data,
                    PipelineLinkDTO::new,
                    (d) -> d.getLinks().getAdd(),
                    (d) -> d.getLinks().getRemove()
            );
        }

        public PipelineAddRemoveDTO<PipelineElement, PipelineElementDTO> getElements() {
            return elements;
        }

        public PipelineAddRemoveDTO<PipelineProperty, PipelinePropertyDTO> getProperties() {
            return properties;
        }

        public PipelineReferences getPipelineReferences() {
            return pipelineReferences;
        }

        public PipelineAddRemoveDTO<PipelineLink, PipelineLinkDTO> getLinks() {
            return links;
        }
    }

    private static class PipelineDTO {
        private final List<PipelineDataDTO> configStack;
        private final PipelineDataDTO merged;

        PipelineDTO(final List<PipelineData> configStack) {
            this.configStack = configStack.stream()
                    .map(PipelineDataDTO::new)
                    .collect(Collectors.toList());
            this.merged = new PipelineDataDTO(new PipelineDataMerger().merge(configStack).createMergedData());
        }

        public List<PipelineDataDTO> getConfigStack() {
            return configStack;
        }

        public PipelineDataDTO getMerged() {
            return merged;
        }
    }

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

    private DocRef getDocRef(final String pipelineId) {
        return new DocRef.Builder()
                .uuid(pipelineId)
                .type(PipelineDoc.DOCUMENT_TYPE)
                .build();
    }

    @GET
    @Path("/{pipelineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(@PathParam("pipelineId") final String pipelineId) {
        return security.secureResult(() -> {
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(getDocRef(pipelineId));
            final List<PipelineData> configStack = new ArrayList<>();

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
                        configStack.add(pipelineData);

                    }

                });
            });

            final PipelineDTO dto  = new PipelineDTO(configStack);
            return Response.ok(dto).build();
        });
    }

    @POST
    @Path("/{pipelineId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("pipelineId") final String pipelineId,
                         final PipelineData pipelineData) {
        pipelineScopeRunnable.scopeRunnable(() -> {
            // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
            security.useAsRead(() -> {
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(getDocRef(pipelineId));

                if (pipelineDoc != null) {
                    pipelineDoc.setPipelineData(pipelineData);
                    pipelineStore.writeDocument(pipelineDoc);
                }
            });
        });

        return Response.ok().build();
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}
