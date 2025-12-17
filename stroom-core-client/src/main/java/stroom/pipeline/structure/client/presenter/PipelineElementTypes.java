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

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.util.shared.NullSafe;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PipelineElementTypes {

    private final Map<Category, List<PipelineElementType>> elementTypesByCategory;
    private final Map<String, PipelineElementType> elementTypesByTypeName;
    private final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes;

    public PipelineElementTypes(final Map<String, PipelineElementType> elementTypesByTypeName) {
        this.elementTypesByCategory = Collections.emptyMap();
        this.elementTypesByTypeName = elementTypesByTypeName;
        this.propertyTypes = Collections.emptyMap();
    }

    public PipelineElementTypes(final Map<Category, List<PipelineElementType>> elementTypesByCategory,
                                final Map<String, PipelineElementType> elementTypesByTypeName,
                                final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes) {
        this.elementTypesByCategory = elementTypesByCategory;
        this.elementTypesByTypeName = elementTypesByTypeName;
        this.propertyTypes = propertyTypes;
    }

    public Map<Category, List<PipelineElementType>> getElementTypesByCategory() {
        return elementTypesByCategory;
    }

    public PipelineElementType getElementType(final PipelineElement element) {
        return elementTypesByTypeName.get(element.getType());
    }

    public boolean hasRole(final PipelineElement element, final String role) {
        final PipelineElementType pipelineElementType = getElementType(element);
        return NullSafe.getOrElse(pipelineElementType, pet -> pet.hasRole(role), false);
    }

    public Map<String, PipelinePropertyType> getPropertyTypes(final PipelineElement element) {
        final PipelineElementType pipelineElementType = getElementType(element);
        return NullSafe.getOrElse(pipelineElementType, et ->
                propertyTypes.get(pipelineElementType), Collections.emptyMap());
    }

    public PipelinePropertyType getPropertyType(final PipelineElement element, final PipelineProperty property) {
        final PipelineElementType pipelineElementType = getElementType(element);
        return NullSafe.get(pipelineElementType, et ->
                propertyTypes.get(pipelineElementType), ptm -> ptm.get(property.getName()));
    }
}
