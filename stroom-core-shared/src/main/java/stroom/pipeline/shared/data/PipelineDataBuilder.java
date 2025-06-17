package stroom.pipeline.shared.data;

import stroom.pipeline.shared.data.PipelineElements.Builder;
import stroom.util.shared.NullSafe;

import java.util.Collection;

public class PipelineDataBuilder {

    private final PipelineElements.Builder elements;
    private final PipelineProperties.Builder properties;
    private final PipelineReferences.Builder references;
    private final PipelineLinks.Builder links;

    public PipelineDataBuilder() {
        elements = new PipelineElements.Builder();
        properties = new PipelineProperties.Builder();
        references = new PipelineReferences.Builder();
        links = new PipelineLinks.Builder();
    }

    public PipelineDataBuilder(final PipelineData pipelineData) {
        this.elements = new PipelineElements.Builder(pipelineData.getElements());
        this.properties = new PipelineProperties.Builder(pipelineData.getProperties());
        this.references = new PipelineReferences.Builder(pipelineData.getPipelineReferences());
        this.links = new PipelineLinks.Builder(pipelineData.getLinks());
    }

    public Builder getElements() {
        return elements;
    }

    public PipelineProperties.Builder getProperties() {
        return properties;
    }

    public PipelineReferences.Builder getReferences() {
        return references;
    }

    public PipelineLinks.Builder getLinks() {
        return links;
    }

    public PipelineDataBuilder addElement(final PipelineElementType elementType,
                                          final String id) {
        addElement(new PipelineElement(id, elementType.getType()));
        return this;
    }

    public PipelineDataBuilder addElement(final PipelineElement element) {
        elements.getAddList().add(element);
        return this;
    }

    public PipelineDataBuilder addElements(final Collection<PipelineElement> elements) {
        this.elements.getAddList().addAll(elements);
        return this;
    }

    public PipelineDataBuilder removeElement(final PipelineElementType elementType,
                                             final String id) {
        removeElement(new PipelineElement(id, elementType.getType()));
        return this;
    }

    public PipelineDataBuilder removeElement(final PipelineElement element) {
        elements.getRemoveList().add(element);
        return this;
    }

    public PipelineDataBuilder removeElements(final Collection<PipelineElement> elements) {
        this.elements.getRemoveList().addAll(elements);
        return this;
    }

    public PipelineDataBuilder addProperty(final String element,
                                           final PipelinePropertyType propertyType,
                                           final boolean value) {
        addProperty(new PipelineProperty(element, propertyType.getName(), new PipelinePropertyValue(value)));
        return this;
    }

    public PipelineDataBuilder addProperty(final PipelineProperty property) {
        properties.getAddList().add(property);
        return this;
    }

    public PipelineDataBuilder addProperties(final Collection<PipelineProperty> properties) {
        this.properties.getAddList().addAll(properties);
        return this;
    }

    public PipelineDataBuilder removeProperty(final String element,
                                              final PipelinePropertyType propertyType) {
        removeProperty(new PipelineProperty(element, propertyType.getName(), null));
        return this;
    }

    public PipelineDataBuilder removeProperty(final PipelineProperty property) {
        properties.getRemoveList().add(property);
        return this;
    }

    public PipelineDataBuilder removeProperties(final Collection<PipelineProperty> properties) {
        this.properties.getRemoveList().addAll(properties);
        return this;
    }

    public PipelineDataBuilder addPipelineReference(final PipelineReference pipelineReference) {
        references.getAddList().add(pipelineReference);
        return this;
    }

    public PipelineDataBuilder addPipelineReferences(final Collection<PipelineReference> references) {
        this.references.getAddList().addAll(references);
        return this;
    }

    public PipelineDataBuilder removePipelineReference(final PipelineReference pipelineReference) {
        references.getRemoveList().add(pipelineReference);
        return this;
    }

    public PipelineDataBuilder removePipelineReferences(final Collection<PipelineReference> references) {
        this.references.getRemoveList().addAll(references);
        return this;
    }

    public PipelineDataBuilder addLink(final String from,
                                       final String to) {
        addLink(new PipelineLink(from, to));
        return this;
    }

    public PipelineDataBuilder addLink(final PipelineElement from,
                                       final PipelineElement to) {
        addLink(new PipelineLink(from.getId(), to.getId()));
        return this;
    }

    public PipelineDataBuilder addLink(final PipelineLink link) {
        links.getAddList().add(link);
        return this;
    }

    public PipelineDataBuilder addLinks(final Collection<PipelineLink> links) {
        this.links.getAddList().addAll(links);
        return this;
    }

    public PipelineDataBuilder removeLink(final String from,
                                          final String to) {
        removeLink(new PipelineLink(from, to));
        return this;
    }

    public PipelineDataBuilder removeLink(final PipelineLink link) {
        links.getRemoveList().add(link);
        return this;
    }

    public PipelineDataBuilder removeLinks(final Collection<PipelineLink> links) {
        this.links.getRemoveList().addAll(links);
        return this;
    }

    public PipelineData build() {
        PipelineElements elements = this.elements.build();
        if (elements != null &&
            NullSafe.isEmptyCollection(elements.getAdd()) &&
            NullSafe.isEmptyCollection(elements.getRemove())) {
            elements = null;
        }

        PipelineProperties properties = this.properties.build();
        if (properties != null &&
            NullSafe.isEmptyCollection(properties.getAdd()) &&
            NullSafe.isEmptyCollection(properties.getRemove())) {
            properties = null;
        }

        PipelineReferences pipelineReferences = this.references.build();
        if (pipelineReferences != null &&
            NullSafe.isEmptyCollection(pipelineReferences.getAdd()) &&
            NullSafe.isEmptyCollection(pipelineReferences.getRemove())) {
            pipelineReferences = null;
        }

        PipelineLinks links = this.links.build();
        if (links != null &&
            NullSafe.isEmptyCollection(links.getAdd()) &&
            NullSafe.isEmptyCollection(links.getRemove())) {
            links = null;
        }

        return new PipelineData(elements, properties, pipelineReferences, links);
    }
}