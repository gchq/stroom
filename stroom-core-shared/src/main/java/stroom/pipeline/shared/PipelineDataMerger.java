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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class PipelineDataMerger {
    private static final String SOURCE = "Source";
    private static final PipelineElement SOURCE_ELEMENT = new PipelineElement(SOURCE, SOURCE);
    private static final Set<String> POSSIBLE_ROOT_ELEMENTS = new HashSet<>(Arrays.asList("Reader", "InvalidXMLCharFilterReader", "InvalidCharFilterReader", "BadTextXMLFilterReader", "BOMRemovalFilterInput", "XMLParser", "XMLFragmentParser", "JSONParser", "DSParser", "CombinedParser", "RollingFileAppender", "RollingStreamAppender", "FileAppender", "StreamAppender", "HDFSFileAppender", "TestAppender"));

    private final Map<String, PipelineElement> allElementMap = new HashMap<>();
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
        // Merge elements.
        for (final PipelineData pipelineData : configStack) {
            if (pipelineData != null) {
                // Merge elements.
                for (final PipelineElement element : pipelineData.getElements().getAdd()) {
                    final PipelineElement existing = allElementMap.get(element.getId());
                    if (existing == null) {
                        allElementMap.put(element.getId(), element);
                        elementMap.put(element.getId(), element);
                    } else if (!existing.getType().equals(element.getType())) {
                        throw new PipelineModelException("Attempt to add element with id=" + existing.getId()
                                + " but element already exists with the same id but different type");
                    }
                }

                for (final PipelineElement element : pipelineData.getElements().getRemove()) {
                    elementMap.remove(element.getId());
                }
            }
        }

        // Ensure the config stack gives us source elements.
        Map<String, PipelineElement> unlinkedElements = null;
        if (configStack.size() > 0 && !allElementMap.containsKey(SOURCE)) {
            unlinkedElements = new HashMap<>(elementMap);
            // Ensure that there is a source element.
            elementMap.put(SOURCE, SOURCE_ELEMENT);
            allElementMap.put(SOURCE, SOURCE_ELEMENT);
        }

        // Now we have a set of elements merge everything else.
        for (final PipelineData pipelineData : configStack) {
            if (pipelineData != null) {
                // Merge properties.
                for (final PipelineProperty property : pipelineData.getProperties().getAdd()) {
                    final PipelineElement element = elementMap.get(property.getElement());
                    if (element != null) {
                        final String elementType = element.getType();
                        if (elementType != null) {
                            propertyMap.computeIfAbsent(property.getElement(), k -> new HashMap<>()).put(property.getName(), property);
                        }
                    }
                }
                for (final PipelineProperty property : pipelineData.getProperties().getRemove()) {
                    final Map<String, PipelineProperty> map = propertyMap.get(property.getElement());
                    if (map != null) {
                        map.remove(property.getName());
                        if (map.size() == 0) {
                            propertyMap.remove(property.getElement());
                        }
                    }
                }

                // Merge pipeline references.
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
                for (final PipelineReference reference : pipelineData.getPipelineReferences().getRemove()) {
                    final Map<String, List<PipelineReference>> map = pipelineReferenceMap.get(reference.getElement());
                    if (map != null) {
                        final List<PipelineReference> list = map.get(reference.getName());
                        if (list != null) {
                            list.remove(reference);
                        }
                    }
                }

                // Merge links.
                for (final PipelineLink link : pipelineData.getLinks().getAdd()) {
                    if (unlinkedElements != null) {
                        // If an element is linked to (even by an element that has been removed) then the element is not to be considered a source.
                        unlinkedElements.remove(link.getTo());
                    }

                    final PipelineElement fromElement = elementMap.get(link.getFrom());
                    final PipelineElement toElement = elementMap.get(link.getTo());

                    // Only add links between elements that have been defined.
                    if (fromElement != null && toElement != null) {
                        final String fromType = elementMap.get(link.getFrom()).getType();
                        final String toType = elementMap.get(link.getTo()).getType();

                        if (fromType != null && toType != null) {
                            linkMap.computeIfAbsent(link.getFrom(), k -> new ArrayList<>()).add(link);
                        }
                    }
                }
                for (final PipelineLink link : pipelineData.getLinks().getRemove()) {
                    final List<PipelineLink> list = linkMap.get(link.getFrom());
                    if (list != null) {
                        list.remove(link);
                        if (list.size() == 0) {
                            linkMap.remove(link.getFrom());
                        }
                    }
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

        // Make sure that the source element provides links to unlinked elements if it is not already determined to be the source element.
        if (!linkMap.containsKey(SOURCE) && unlinkedElements != null) {
            unlinkedElements.values().stream()
                    .filter(element -> POSSIBLE_ROOT_ELEMENTS.contains(element.getType()))
                    .forEach(element -> {
                        final PipelineLink pipelineLink = new PipelineLink(SOURCE, element.getId());
                        linkMap.computeIfAbsent(SOURCE, k -> new ArrayList<>()).add(pipelineLink);
                    });
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
