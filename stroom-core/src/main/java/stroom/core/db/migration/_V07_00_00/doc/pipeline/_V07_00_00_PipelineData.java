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

package stroom.core.db.migration._V07_00_00.doc.pipeline;

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Pipeline", propOrder = {"elements", "properties", "pipelineReferences", "links"})
@XmlRootElement(name = "pipeline")
public class _V07_00_00_PipelineData implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = -4874097335141550178L;

    @XmlElement(name = "elements")
    private _V07_00_00_PipelineElements elements = new _V07_00_00_PipelineElements();
    @XmlElement(name = "properties")
    private _V07_00_00_PipelineProperties properties = new _V07_00_00_PipelineProperties();
    @XmlElement(name = "pipelineReferences")
    private _V07_00_00_PipelineReferences pipelineReferences = new _V07_00_00_PipelineReferences();
    @XmlElement(name = "links")
    private _V07_00_00_PipelineLinks links = new _V07_00_00_PipelineLinks();

    public _V07_00_00_PipelineElements getElements() {
        return elements;
    }

    public _V07_00_00_PipelineProperties getProperties() {
        return properties;
    }

    public _V07_00_00_PipelineReferences getPipelineReferences() {
        return pipelineReferences;
    }

    public _V07_00_00_PipelineLinks getLinks() {
        return links;
    }

    // Used for testing....
    public void addElement(final _V07_00_00_PipelineElementType elementType, final String id) {
        final _V07_00_00_PipelineElement element = new _V07_00_00_PipelineElement();
        element.setId(id);
        element.setType(elementType.getType());
        element.setElementType(elementType);
        addElement(element);
    }

    public void addElement(final _V07_00_00_PipelineElement element) {
        getAddedElements().add(element);
    }

    public void removeElement(final _V07_00_00_PipelineElementType elementType, final String id) {
        final _V07_00_00_PipelineElement element = new _V07_00_00_PipelineElement();
        element.setId(id);
        element.setType(elementType.getType());
        element.setElementType(elementType);
        removeElement(element);
    }

    public void removeElement(final _V07_00_00_PipelineElement element) {
        getRemovedElements().add(element);
    }

    public void addProperty(final String element, final _V07_00_00_PipelinePropertyType propertyType, final boolean value) {
        final _V07_00_00_PipelineProperty property = new _V07_00_00_PipelineProperty(element, propertyType.getName());
        property.setPropertyType(propertyType);
        final _V07_00_00_PipelinePropertyValue val = new _V07_00_00_PipelinePropertyValue(value);
        property.setValue(val);
        addProperty(property);
    }

    public void addProperty(final _V07_00_00_PipelineProperty property) {
        getAddedProperties().add(property);
    }

    public void removeProperty(final String element, final _V07_00_00_PipelinePropertyType propertyType) {
        final _V07_00_00_PipelineProperty property = new _V07_00_00_PipelineProperty(element, propertyType.getName());
        property.setPropertyType(propertyType);
        removeProperty(property);
    }

    public void removeProperty(final _V07_00_00_PipelineProperty property) {
        getRemovedProperties().add(property);
    }

    public void addPipelineReference(final _V07_00_00_PipelineReference pipelineReference) {
        getAddedPipelineReferences().add(pipelineReference);
    }

    public void removePipelineReference(final _V07_00_00_PipelineReference pipelineReference) {
        getRemovedPipelineReferences().add(pipelineReference);
    }

    public void addLink(final String from, final String to) {
        final _V07_00_00_PipelineLink link = new _V07_00_00_PipelineLink(from, to);
        addLink(link);
    }

    public void addLink(final _V07_00_00_PipelineLink link) {
        getAddedLinks().add(link);
    }

    public void removeLink(final String from, final String to) {
        final _V07_00_00_PipelineLink link = new _V07_00_00_PipelineLink(from, to);
        removeLink(link);
    }

    public void removeLink(final _V07_00_00_PipelineLink link) {
        getRemovedLinks().add(link);
    }

    /**
     * Convenience methods
     */
    public List<_V07_00_00_PipelineElement> getAddedElements() {
        return elements.getAdd();
    }

    public List<_V07_00_00_PipelineElement> getRemovedElements() {
        return elements.getRemove();
    }

    public List<_V07_00_00_PipelineProperty> getAddedProperties() {
        return properties.getAdd();
    }

    public List<_V07_00_00_PipelineProperty> getRemovedProperties() {
        return properties.getRemove();
    }

    public List<_V07_00_00_PipelineReference> getAddedPipelineReferences() {
        return pipelineReferences.getAdd();
    }

    public List<_V07_00_00_PipelineReference> getRemovedPipelineReferences() {
        return pipelineReferences.getRemove();
    }

    public List<_V07_00_00_PipelineLink> getAddedLinks() {
        return links.getAdd();
    }

    public List<_V07_00_00_PipelineLink> getRemovedLinks() {
        return links.getRemove();
    }
}
