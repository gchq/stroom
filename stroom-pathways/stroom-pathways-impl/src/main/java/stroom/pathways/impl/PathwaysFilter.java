/*
 * Copyright 2017 Crown Copyright
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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

@ConfigurableElement(
        type = "PathwaysFilter",
        category = Category.FILTER,
        description = """
                A filter consuming OTEL spans.
                """,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_INDEX)
class PathwaysFilter extends AbstractXMLFilter {

    private final StringBuilder sb = new StringBuilder();
    private final PathwaysStore pathwaysStore;
    private final TracesStore tracesStore;
    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private DocRef docRef;
    private PathwaysDoc doc;
    private Locator locator;

    @Inject
    PathwaysFilter(final LocationFactoryProxy locationFactory,
                   final ErrorReceiverProxy errorReceiverProxy,
                   final PathwaysStore pathwaysStore,
                   final TracesStore tracesStore) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pathwaysStore = pathwaysStore;
        this.tracesStore = tracesStore;
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            if (docRef == null) {
                log(Severity.FATAL_ERROR, "Pathways have not been set", null);
                throw LoggedException.create("Pathways have not been set");
            }

            // Get the index and index fields from the cache.
            doc = pathwaysStore.readDocument(docRef);
            if (doc == null) {
                log(Severity.FATAL_ERROR, "Unable to load pathways", null);
                throw LoggedException.create("Unable to load pathways");
            }
        } finally {
            super.startProcessing();
        }
    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        sb.setLength(0);
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if ("span".equals(localName)) {
            try {
                final Span span = JsonUtil.readValue(sb.toString(), Span.class);
                tracesStore.addSpan(span);
            } catch (final RuntimeException e) {
                log(Severity.ERROR, e.getMessage(), e);
            }
        }

        sb.setLength(0);
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        sb.append(ch, start, length);
    }

    @PipelineProperty(description = "The pathways to reference.", displayPriority = 1)
    @PipelinePropertyDocRef(types = PathwaysDoc.TYPE)
    public void setPathways(final DocRef docRef) {
        this.docRef = docRef;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
