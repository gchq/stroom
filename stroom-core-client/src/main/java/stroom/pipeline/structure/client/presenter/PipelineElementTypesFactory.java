/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
                                result
                                        .stream()
                                        .collect(Collectors.toMap(FetchPropertyTypesResult::getPipelineElementType,
                                                FetchPropertyTypesResult::getPropertyTypes));

                        final Map<Category, List<PipelineElementType>> elementTypesByCategory = new HashMap<>();
                        final Map<String, PipelineElementType> elementTypesByName = new HashMap<>();

                        for (final PipelineElementType elementType : propertyTypes.keySet()) {
                            if (elementType.getCategory() != null) {
                                elementTypesByCategory
                                        .computeIfAbsent(elementType.getCategory(), k -> new ArrayList<>())
                                        .add(elementType);
                            }
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
