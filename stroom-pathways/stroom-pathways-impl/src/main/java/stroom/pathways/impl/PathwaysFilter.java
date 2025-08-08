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
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

@ConfigurableElement(
        type = "PathwaysFilter",
        category = Category.FILTER,
        description = """
                A filter consuming XML events in the `records:2` namespace to index/store the fields
                and their values in a Lucene Index.
                """,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_INDEX)
class PathwaysFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathwaysFilter.class);

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final MetaHolder metaHolder;
    private final PathwaysStore pathwaysStore;
    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final CharBuffer debugBuffer = new CharBuffer(10);
    private DocRef docRef;
    private PathwaysDoc doc;

    private Locator locator;

    @Inject
    PathwaysFilter(final MetaHolder metaHolder,
                   final LocationFactoryProxy locationFactory,
                   final ErrorReceiverProxy errorReceiverProxy,
                   final PathwaysStore pathwaysStore) {
        this.metaHolder = metaHolder;
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pathwaysStore = pathwaysStore;
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
//        if (DATA.equals(localName) && document != null) {
//            String name = atts.getValue(NAME);
//            String value = atts.getValue(VALUE);
//            if (name != null && value != null) {
//                name = name.trim();
//                value = value.trim();
//
//                if (!name.isEmpty() && !value.isEmpty()) {
//                    // See if we can get this field.
//                    final IndexField indexField = indexFieldCache.get(docRef, name);
//                    if (indexField != null) {
//                        // Index the current content if we are to store or index
//                        // this field.
//                        if (indexField.isIndexed() || indexField.isStored()) {
//                            processIndexContent(indexField, value);
//                        }
//                    } else {
//                        log(Severity.WARNING, "Attempt to index unknown field: " + name, null);
//                    }
//                }
//            }
//        } else if (RECORD.equals(localName)) {
//            // Create a document to store fields in.
//            document = new IndexDocument();
//        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
//        if (RECORD.equals(localName)) {
//            processDocument();
//            document = null;
//            currentEventTime = null;
//
//            if (errorReceiverProxy.getErrorReceiver() != null
//                && errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
//                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
//            }
//        }

        super.endElement(uri, localName, qName);
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
