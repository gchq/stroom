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

package stroom.index.server;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexShardKey;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ErrorStatistics;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.factory.PipelinePropertyDocRef;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.StreamHolder;
import stroom.query.api.v2.DocRef;
import stroom.util.CharBuffer;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

/**
 * The index filter... takes the index XML and builds the LUCENE documents
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "IndexingFilter", category = Category.FILTER, roles = {PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE}, icon = ElementIcons.INDEX)
class IndexingFilter extends AbstractXMLFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexingFilter.class);

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final StreamHolder streamHolder;
    private final LocationFactoryProxy locationFactory;
    private final Indexer indexer;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final IndexConfigCache indexConfigCache;
    private final CharBuffer debugBuffer = new CharBuffer(10);
    private IndexFieldsMap indexFieldsMap;
    private DocRef indexRef;
    private IndexShardKey indexShardKey;
    private Document document;

    private int fieldsIndexed = 0;

    private Locator locator;

    @Inject
    IndexingFilter(final StreamHolder streamHolder,
                   final LocationFactoryProxy locationFactory,
                   final Indexer indexer,
                   final ErrorReceiverProxy errorReceiverProxy,
                   final IndexConfigCache indexConfigCache) {
        this.streamHolder = streamHolder;
        this.locationFactory = locationFactory;
        this.indexer = indexer;
        this.errorReceiverProxy = errorReceiverProxy;
        this.indexConfigCache = indexConfigCache;
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            if (indexRef == null) {
                log(Severity.FATAL_ERROR, "Index has not been set", null);
                throw new LoggedException("Index has not been set");
            }

            // Get the index and index fields from the cache.
            final IndexConfig indexConfig = indexConfigCache.getOrCreate(indexRef);
            if (indexConfig == null) {
                log(Severity.FATAL_ERROR, "Unable to load index", null);
                throw new LoggedException("Unable to load index");
            }

            final Index index = indexConfig.getIndex();
            indexFieldsMap = indexConfig.getIndexFieldsMap();

            // Create a key to create shards with.
            if (streamHolder == null || streamHolder.getStream() == null) {
                // Many tests don't use streams so where this is the case just
                // create a basic key.
                indexShardKey = IndexShardKeyUtil.createTestKey(index);
            } else {
                final long timeMs = streamHolder.getStream().getCreateMs();
                indexShardKey = IndexShardKeyUtil.createTimeBasedPartition(index, timeMs);
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
        if (DATA.equals(localName) && document != null) {
            String name = atts.getValue(NAME);
            String value = atts.getValue(VALUE);
            if (name != null && value != null) {
                name = name.trim();
                value = value.trim();

                if (name.length() > 0 && value.length() > 0) {
                    // See if we can get this field.
                    final IndexField indexField = indexFieldsMap.get(name);
                    if (indexField != null) {
                        // Index the current content if we are to store or index
                        // this field.
                        if (indexField.isIndexed() || indexField.isStored()) {
                            processIndexContent(indexField, value);
                        }
                    } else {
                        log(Severity.WARNING, "Attempt to index unknown field: " + name, null);
                    }
                }
            }
        } else if (RECORD.equals(localName)) {
            // Create a document to store fields in.
            document = new Document();
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (RECORD.equals(localName)) {
            processDocument();
            document = null;

            // Reset the count of how many fields we have indexed for the
            // current event.
            fieldsIndexed = 0;

            if (errorReceiverProxy.getErrorReceiver() != null
                    && errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
            }
        }

        super.endElement(uri, localName, qName);
    }

    private void processDocument() throws SAXException {
        // Write the document if we have dropped out of the record element and
        // have indexed some fields.
        if (fieldsIndexed > 0) {
            try {
                indexer.addDocument(indexShardKey, document);
            } catch (final RuntimeException e) {
                log(Severity.FATAL_ERROR, e.getMessage(), e);
                // Terminate processing as this is a fatal error.
                throw new LoggedException(e.getMessage(), e);
            }
        }
    }

    private void processIndexContent(final IndexField indexField, final String value) {
        try {
            Field field = null;

            if (indexField.getFieldType().isNumeric()) {
                final long val = Long.parseLong(value);
                field = FieldFactory.create(indexField, val);

            } else if (IndexFieldType.DATE_FIELD.equals(indexField.getFieldType())) {
                try {
                    final long val = DateUtil.parseUnknownString(value);
                    field = FieldFactory.create(indexField, val);
                } catch (final Exception e) {
                    LOGGER.trace(e.getMessage(), e);
                }
            } else {
                field = FieldFactory.create(indexField, value);
            }

            // Add the current field to the document if it is not null.
            if (field != null) {
                // Output some debug.
                if (LOGGER.isDebugEnabled()) {
                    debugBuffer.append("processIndexContent() - Adding to index indexName=");
                    debugBuffer.append(indexRef.getName());
                    debugBuffer.append(" name=");
                    debugBuffer.append(indexField.getFieldName());
                    debugBuffer.append(" value=");
                    debugBuffer.append(value);

                    final String debug = debugBuffer.toString();
                    debugBuffer.clear();

                    LOGGER.debug(debug);
                }

                fieldsIndexed++;
                document.add(field);
            }
        } catch (final RuntimeException e) {
            log(Severity.ERROR, e.getMessage(), e);
        }
    }

    @PipelineProperty(description = "The index to send records to.")
    @PipelinePropertyDocRef(types=Index.ENTITY_TYPE)
    public void setIndex(final DocRef indexRef) {
        this.indexRef = indexRef;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
