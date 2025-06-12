package stroom.pipeline.structure.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.pipeline.shared.FetchPropertyTypesResult;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Singleton
public class PipelineElementTypesFactory {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);

    private final RestFactory restFactory;

    private PipelineElementTypes pipelineElementTypes;

    @Inject
    public PipelineElementTypesFactory(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void get(final TaskMonitorFactory taskMonitorFactory,
                    final Consumer<PipelineElementTypes> consumer) {
        if (pipelineElementTypes != null) {
            consumer.accept(pipelineElementTypes);
        } else {
            // Get a map of all available elements and properties.
            restFactory
                    .create(PIPELINE_RESOURCE)
                    .method(PipelineResource::getPropertyTypes)
                    .onSuccess(result -> {
                        final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes =
                                result.stream().collect(Collectors.toMap(FetchPropertyTypesResult::getPipelineElementType,
                                        FetchPropertyTypesResult::getPropertyTypes));

                        final Map<Category, List<PipelineElementType>> elementTypesByCategory = new HashMap<>();
                        final Map<String, PipelineElementType> elementTypesByName = new HashMap<>();

                        for (final PipelineElementType elementType : propertyTypes.keySet()) {
                            final List<PipelineElementType> list = elementTypesByCategory
                                    .computeIfAbsent(elementType.getCategory(), k -> new ArrayList<>());
                            list.add(elementType);
                            elementTypesByName.put(elementType.getType(), elementType);
                        }

                        for (final List<PipelineElementType> types : elementTypesByCategory.values()) {
                            Collections.sort(types);
                        }

                        pipelineElementTypes = new PipelineElementTypes(
                                elementTypesByCategory,
                                elementTypesByName,
                                propertyTypes);
                        consumer.accept(pipelineElementTypes);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }
}
