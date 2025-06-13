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
