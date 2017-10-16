/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.shared;

import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelineReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class PipelineDataMerger {
    private static final String SOURCE = "Source";
    private static final PipelineElementType SOURCE_ELEMENT_TYPE = new PipelineElementType(SOURCE, null,
            new String[]{PipelineElementType.ROLE_SOURCE, PipelineElementType.ROLE_HAS_TARGETS,
                    PipelineElementType.VISABILITY_SIMPLE},
            null);
    private static final PipelineElement SOURCE_ELEMENT = new PipelineElement(SOURCE, SOURCE);

    static {
        SOURCE_ELEMENT.setElementType(SOURCE_ELEMENT_TYPE);
    }

    private final Map<String, PipelineElement> elementMap = new HashMap<>();
    private final Map<String, Map<String, PipelineProperty>> propertyMap = new HashMap<>();
    private final Map<String, Map<String, List<PipelineReference>>> pipelineReferenceMap = new HashMap<>();
    private final Map<String, List<PipelineLink>> linkMap = new HashMap<>();

    public static Map<String, PipelineElementType> createElementMap() {
        final Map<String, PipelineElementType> map = new HashMap<>();
        // Ensure we always have a source element to link from.
        map.put(SOURCE, SOURCE_ELEMENT.getElementType());
        return map;
    }

    public PipelineDataMerger() {
        // Default constructor necessary for GWT serialisation.
    }

    public void merge(final PipelineData... configStack) throws PipelineModelException {
        merge(Arrays.asList(configStack));
    }

    public void merge(final List<PipelineData> configStack) throws PipelineModelException {
        final Map<String, PipelineElement> allElementMap = new HashMap<>();
        boolean sourceProvided = false;

        // Merge elements.
        for (final PipelineData pipelineData : configStack) {
            if (pipelineData != null) {

                // Add elements.
                for (final PipelineElement element : pipelineData.getElements().getAdd()) {
                    // If source is provided by a pipeline then remember this.
                    if (SOURCE.equals(element.getId())) {
                        sourceProvided = true;
                    }

                    final PipelineElement existing = allElementMap.get(element.getId());
                    if (existing == null) {
                        allElementMap.put(element.getId(), element);
                        elementMap.put(element.getId(), element);
                    } else if (!existing.getType().equals(element.getType())) {
                        throw new PipelineModelException("Attempt to add element with id=" + existing.getId()
                                + " but element already exists with the same id but different type");
                    }
                }

                // Remove elements.
                for (final PipelineElement element : pipelineData.getElements().getRemove()) {
                    elementMap.remove(element.getId());
                }
            }
        }

        // Merge properties.
        for (final PipelineData pipelineData : configStack) {
            if (pipelineData != null) {

                // Add properties.
                for (final PipelineProperty property : pipelineData.getProperties().getAdd()) {
                    final PipelineElement element = elementMap.get(property.getElement());
                    if (element != null) {
                        final String elementType = element.getType();
                        if (elementType != null) {
                            propertyMap.computeIfAbsent(property.getElement(), k -> new HashMap<>()).put(property.getName(), property);
                        }
                    }
                }

                // Remove properties.
                for (final PipelineProperty property : pipelineData.getProperties().getRemove()) {
                    propertyMap.compute(property.getElement(), (elementId, map) -> {
                        if (map != null) {
                            map.remove(property.getName());
                            if (map.size() == 0) {
                                return null;
                            }
                        }
                        return map;
                    });
                }
            }
        }

        // Merge pipeline references.
        for (final PipelineData pipelineData : configStack) {
            if (pipelineData != null) {

                // Add pipeline references.
                for (final PipelineReference reference : pipelineData.getPipelineReferences().getAdd()) {
                    final PipelineElement element = elementMap.get(reference.getElement());
                    if (element != null) {
                        final String elementType = element.getType();
                        if (elementType != null) {
                            final List<PipelineReference> list = pipelineReferenceMap
                                    .computeIfAbsent(reference.getElement(), k -> new HashMap<>())
                                    .computeIfAbsent(reference.getName(), k -> new ArrayList<>());
                            if (!list.contains(reference)) {
                                list.add(reference);
                            }
                        }
                    }
                }

                // Remove pipeline references.
                for (final PipelineReference reference : pipelineData.getPipelineReferences().getRemove()) {
                    pipelineReferenceMap.compute(reference.getElement(), (elementId, map) -> {
                        if (map != null) {
                            map.compute(reference.getName(), (name, list) -> {
                                if (list != null) {
                                    list.remove(reference);
                                    if (list.size() == 0) {
                                        return null;
                                    }
                                }
                                return list;
                            });
                            if (map.size() == 0) {
                                return null;
                            }
                        }
                        return map;
                    });
                }
            }
        }

        // Merge links.
        for (final PipelineData pipelineData : configStack) {
            if (pipelineData != null) {

                // Add links.
                for (final PipelineLink link : pipelineData.getLinks().getAdd()) {
                    final PipelineElement fromElement = elementMap.get(link.getFrom());
                    final PipelineElement toElement = elementMap.get(link.getTo());

                    // Only add links between elements that have been defined.
                    if (fromElement != null && toElement != null) {
                        final String fromType = fromElement.getType();
                        final String toType = toElement.getType();

                        if (fromType != null && toType != null) {
                            linkMap.computeIfAbsent(link.getFrom(), k -> new ArrayList<>()).add(link);
                        }
                    }
                }

                // Remove links.
                for (final PipelineLink link : pipelineData.getLinks().getRemove()) {
                    linkMap.compute(link.getFrom(), (elementId, list) -> {
                        if (list != null) {
                            list.remove(link);
                            if (list.size() == 0) {
                                return null;
                            }
                        }

                        return list;
                    });
                }
            }
        }

        // Ensure an element can only be linked to once.
        final Map<String, PipelineLink> uniqueLinkToMap = new HashMap<>();
        for (final Entry<String, List<PipelineLink>> entry : linkMap.entrySet()) {
            final List<PipelineLink> links = entry.getValue();
            final Iterator<PipelineLink> iter = links.iterator();
            while (iter.hasNext()) {
                final PipelineLink link = iter.next();
                final PipelineLink existing = uniqueLinkToMap.get(link.getTo());
                if (existing == null) {
                    // We haven't linked to this element before so just record
                    // the link.
                    uniqueLinkToMap.put(link.getTo(), link);
                } else {
                    // We already have a link to this element so remove this
                    // additional link.
                    iter.remove();
                }
            }
        }

        // If there is no source provided then we need to add a source and attach a parser.
        if (!sourceProvided) {
            // Ensure that there is always a source element.
            elementMap.put(SOURCE, SOURCE_ELEMENT);

            String parserId = null;
            for (final Entry<String, PipelineElement> entry : elementMap.entrySet()) {
                if (parserId == null) {
                    final String elementId = entry.getKey();
                    final PipelineElement element = entry.getValue();
                    if (element.getType().toLowerCase().contains("parser")) {
                        parserId = elementId;
                    }
                }
            }

            if (parserId != null) {
                final PipelineLink pipelineLink = new PipelineLink(SOURCE, parserId);
                linkMap.computeIfAbsent(SOURCE, k -> new ArrayList<>()).add(pipelineLink);
            }
        }
    }

    public Map<String, PipelineElement> getElements() {
        return elementMap;
    }

    public Map<String, Map<String, PipelineProperty>> getProperties() {
        return propertyMap;
    }

    public Map<String, List<PipelineLink>> getLinks() {
        return linkMap;
    }

    public Map<String, Map<String, List<PipelineReference>>> getPipelineReferences() {
        return pipelineReferenceMap;
    }

    public PipelineData createMergedData() {
        // Create merged data.
        final PipelineData pipelineData = new PipelineData();

        for (final PipelineElement element : elementMap.values()) {
            pipelineData.addElement(element);
        }
        for (final Map<String, PipelineProperty> map : propertyMap.values()) {
            for (final PipelineProperty property : map.values()) {
                pipelineData.addProperty(property);
            }
        }
        for (final Map<String, List<PipelineReference>> map : pipelineReferenceMap.values()) {
            for (final List<PipelineReference> list : map.values()) {
                for (final PipelineReference pipelineReference : list) {
                    pipelineData.addPipelineReference(pipelineReference);
                }
            }
        }
        for (final List<PipelineLink> list : linkMap.values()) {
            for (final PipelineLink link : list) {
                pipelineData.addLink(link);
            }
        }

        return pipelineData;
    }
}