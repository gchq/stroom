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

package stroom.pipeline.factory;

import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelineReference;

import jakarta.inject.Inject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PipelineDataValidator {

    private final ElementRegistryFactory pipelineElementRegistryFactory;

    @Inject
    PipelineDataValidator(final ElementRegistryFactory pipelineElementRegistryFactory) {
        this.pipelineElementRegistryFactory = pipelineElementRegistryFactory;
    }

    public void validate(final PipelineData pipelineData,
                         final Map<String, PipelineElementType> elementMap) {
        final ElementRegistry registry = pipelineElementRegistryFactory.get();

        // Validate elements.
        validateElementList(registry, pipelineData.getAddedElements(), elementMap);
        validateElementList(registry, pipelineData.getRemovedElements(), elementMap);

        // Validate properties.
        validatePropertiesList(registry, pipelineData.getAddedProperties(), elementMap);
        validatePropertiesList(registry, pipelineData.getRemovedProperties(), elementMap);

        // Validate pipeline references.
        validatePipelineReferencesList(registry,
                pipelineData.getAddedPipelineReferences(),
                elementMap);
        validatePipelineReferencesList(registry,
                pipelineData.getRemovedPipelineReferences(),
                elementMap);

        // Validate links.
        validateLinksList(pipelineData.getAddedLinks(), elementMap);
        validateLinksList(pipelineData.getRemovedLinks(), elementMap);
    }

    private void validateElementList(final ElementRegistry registry,
                                     final List<PipelineElement> elementsList,
                                     final Map<String, PipelineElementType> elementMap) {
        for (final PipelineElement element : elementsList) {
            if (element.getId() == null) {
                throw new PipelineFactoryException("No id has been declared for element: " + element.getType());
            }

            if (element.getType() == null) {
                throw new PipelineFactoryException("No type has been declared for element: " + element.getId());
            }

            final PipelineElementType elementType = registry.getElementType(element.getType());
            if (elementType == null) {
                throw new PipelineFactoryException("Element type \"" + element.getType() + "\" is unknown");
            }

            final PipelineElementType existing = elementMap.put(element.getId(), elementType);
            if (existing != null && !existing.getType().equals(elementType.getType())) {
                throw new PipelineFactoryException("Attempt to add element with id=" + element.getId()
                                                   + " but element already exists with the same id but different type");
            }
        }
    }

    private void validatePropertiesList(final ElementRegistry registry,
                                        final List<PipelineProperty> propertiesList,
                                        final Map<String, PipelineElementType> elementMap) {
        final Iterator<PipelineProperty> iterator = propertiesList.iterator();
        while (iterator.hasNext()) {
            final PipelineProperty property = iterator.next();
            if (property.getElement() == null) {
                throw new PipelineFactoryException(
                        "No element id has been declared for property: " + property.getName());
            }
            if (property.getName() == null) {
                throw new PipelineFactoryException("No name has been declared for property: " + property.getElement());
            }

            final PipelineElementType elementType = elementMap.get(property.getElement());
            if (elementType == null) {
                iterator.remove();
//                throw new PipelineFactoryException(
//                        "Attempt to set property on unknown element \"" + property.getElement() + "\"");

            } else {
                final PipelinePropertyType propertyType = registry.getPropertyType(elementType, property.getName());
                if (propertyType == null) {
                    throw new PipelineFactoryException("Attempt to set property \"" + property.getName() +
                                                       "\" on element \"" + property.getElement() +
                                                       "\" but property is unknown.");
                }
            }
        }
    }

    private void validatePipelineReferencesList(final ElementRegistry registry,
                                                final List<PipelineReference> pipelineReferencesList,
                                                final Map<String, PipelineElementType> elementMap) {
        final Iterator<PipelineReference> iterator = pipelineReferencesList.iterator();
        while (iterator.hasNext()) {
            final PipelineReference pipelineReference = iterator.next();
            if (pipelineReference.getElement() == null) {
                throw new PipelineFactoryException(
                        "No element id has been declared for pipeline reference: " + pipelineReference.getName());
            }
            if (pipelineReference.getName() == null) {
                throw new PipelineFactoryException(
                        "No name has been declared for pipeline reference: " + pipelineReference.getElement());
            }

            final PipelineElementType elementType = elementMap.get(pipelineReference.getElement());
            if (elementType == null) {
                iterator.remove();
//                throw new PipelineFactoryException("Attempt to set pipeline reference on unknown element \""
//                        + pipelineReference.getElement() + "\"");

            } else {
                final PipelinePropertyType propertyType = registry.getPropertyType(elementType,
                        pipelineReference.getName());
                if (propertyType == null) {
                    throw new PipelineFactoryException("Attempt to set pipeline reference \"" +
                                                       pipelineReference.getName() + "\" on element \"" +
                                                       pipelineReference.getElement() +
                                                       "\" but property is unknown.");
                }
            }
        }
    }

    private void validateLinksList(final List<PipelineLink> linksList,
                                   final Map<String, PipelineElementType> elementMap) {
        final Iterator<PipelineLink> iterator = linksList.iterator();
        while (iterator.hasNext()) {
            final PipelineLink link = iterator.next();
            if (!elementMap.containsKey(link.getFrom())) {
                iterator.remove();
//                throw new PipelineFactoryException("Attempt to link from \"" + link.getFrom() + "\" to \""
//                        + link.getTo() + "\" but \"" + link.getFrom() + "\" is unknown");
            } else if (!elementMap.containsKey(link.getTo())) {
                iterator.remove();
//                throw new PipelineFactoryException("Attempt to link from \"" + link.getFrom() + "\" to \""
//                        + link.getTo() + "\" but \"" + link.getTo() + "\" is unknown");
            }
        }
    }
}
