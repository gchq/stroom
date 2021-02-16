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

package stroom.search.elastic.indexing;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.factory.PipelinePropertyDocRef;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.query.api.v2.DocRef;
import stroom.search.solr.shared.SolrIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

/**
 * The Solr index filter... takes the index XML and sends documents to Solr for indexing.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "ElasticsearchIndexingFilter", category = Category.FILTER, roles = {PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE}, icon = ElementIcons.ELASTIC_SEARCH)
class ElasticIndexingFilter extends AbstractXMLFilter {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexingFilter.class);

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;

    private int batchSize = 1000;
    private int commitWithinMs = -1;
    private boolean softCommit = true;
    private int fieldsIndexed;
    private long docsIndexed;

    private Locator locator;

    @Inject
    ElasticIndexingFilter(final LocationFactoryProxy locationFactory,
                          final ErrorReceiverProxy errorReceiverProxy) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {

        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        try {

        } finally {
            super.endProcessing();
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
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        super.endElement(uri, localName, qName);
    }

    @PipelineProperty(description = "The index to send records to.")
    @PipelinePropertyDocRef(types = SolrIndex.ENTITY_TYPE)
    public void setIndex(final DocRef indexRef) {

    }

    @PipelineProperty(description = "How many documents to send to the index in a single post.", defaultValue = "1000")
    public void setBatchSize(final int batchSize) {
        this.batchSize = batchSize;
    }

    @PipelineProperty(description = "Commit indexed documents within the specified number of milliseconds.", defaultValue = "-1")
    public void setCommitWithinMs(final int commitWithinMs) {
        if (commitWithinMs < 0) {
            this.commitWithinMs = -1;
        } else {
            this.commitWithinMs = commitWithinMs;
        }
    }

    @PipelineProperty(description = "Perform a soft commit after every batch so that docs are available for searching immediately (if using NRT replicas).", defaultValue = "true")
    public void setSoftCommit(final boolean softCommit) {
        this.softCommit = softCommit;
    }
}
