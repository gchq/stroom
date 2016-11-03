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

package stroom.pipeline.shared.data;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import stroom.util.shared.SharedObject;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Pipeline", propOrder = { "elements", "properties", "pipelineReferences", "links" })
@XmlRootElement(name = "pipeline")
public class PipelineData implements SharedObject {
    private static final long serialVersionUID = -4874097335141550178L;

    @XmlElement(name = "elements", required = false)
    private PipelineElements elements = new PipelineElements();
    @XmlElement(name = "properties", required = false)
    private PipelineProperties properties = new PipelineProperties();
    @XmlElement(name = "pipelineReferences", required = false)
    private PipelineReferences pipelineReferences = new PipelineReferences();
    @XmlElement(name = "links", required = false)
    private PipelineLinks links = new PipelineLinks();

    public PipelineElements getElements() {
        return elements;
    }

    public PipelineProperties getProperties() {
        return properties;
    }

    public PipelineReferences getPipelineReferences() {
        return pipelineReferences;
    }

    public PipelineLinks getLinks() {
        return links;
    }

    // Used for testing....
    public void addElement(final PipelineElementType elementType, final String id) {
        final PipelineElement element = new PipelineElement();
        element.setId(id);
        element.setType(elementType.getType());
        element.setElementType(elementType);
        addElement(element);
    }

    public void addElement(final PipelineElement element) {
        getAddedElements().add(element);
    }

    public void removeElement(final PipelineElementType elementType, final String id) {
        final PipelineElement element = new PipelineElement();
        element.setId(id);
        element.setType(elementType.getType());
        element.setElementType(elementType);
        removeElement(element);
    }

    public void removeElement(final PipelineElement element) {
        getRemovedElements().add(element);
    }

    public void addProperty(final String element, final PipelinePropertyType propertyType, final boolean value) {
        final PipelineProperty property = new PipelineProperty(element, propertyType.getName());
        property.setPropertyType(propertyType);
        final PipelinePropertyValue val = new PipelinePropertyValue(value);
        property.setValue(val);
        addProperty(property);
    }

    public void addProperty(final PipelineProperty property) {
        getAddedProperties().add(property);
    }

    public void removeProperty(final String element, final PipelinePropertyType propertyType) {
        final PipelineProperty property = new PipelineProperty(element, propertyType.getName());
        property.setPropertyType(propertyType);
        removeProperty(property);
    }

    public void removeProperty(final PipelineProperty property) {
        getRemovedProperties().add(property);
    }

    public void addPipelineReference(final PipelineReference pipelineReference) {
        getAddedPipelineReferences().add(pipelineReference);
    }

    public void removePipelineReference(final PipelineReference pipelineReference) {
        getRemovedPipelineReferences().add(pipelineReference);
    }

    public void addLink(final String from, final String to) {
        final PipelineLink link = new PipelineLink(from, to);
        addLink(link);
    }

    public void addLink(final PipelineLink link) {
        getAddedLinks().add(link);
    }

    public void removeLink(final String from, final String to) {
        final PipelineLink link = new PipelineLink(from, to);
        removeLink(link);
    }

    public void removeLink(final PipelineLink link) {
        getRemovedLinks().add(link);
    }

    /**
     * Convenience methods
     */
    public List<PipelineElement> getAddedElements() {
        return elements.getAdd();
    }

    public List<PipelineElement> getRemovedElements() {
        return elements.getRemove();
    }

    public List<PipelineProperty> getAddedProperties() {
        return properties.getAdd();
    }

    public List<PipelineProperty> getRemovedProperties() {
        return properties.getRemove();
    }

    public List<PipelineReference> getAddedPipelineReferences() {
        return pipelineReferences.getAdd();
    }

    public List<PipelineReference> getRemovedPipelineReferences() {
        return pipelineReferences.getRemove();
    }

    public List<PipelineLink> getAddedLinks() {
        return links.getAdd();
    }

    public List<PipelineLink> getRemovedLinks() {
        return links.getRemove();
    }
}
