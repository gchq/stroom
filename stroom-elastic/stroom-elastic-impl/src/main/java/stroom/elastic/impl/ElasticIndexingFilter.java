package stroom.elastic.impl;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.docref.DocRef;
import stroom.elastic.api.ElasticIndexWriter;
import stroom.elastic.api.ElasticIndexWriterFactory;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.security.Security;
import stroom.security.UserService;
import stroom.security.util.UserTokenUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * The index filter... takes the index XML and builds the LUCENE documents
 */
@ConfigurableElement(
        type = "ElasticIndexingFilter",
        category = PipelineElementType.Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE
        },
        icon = ElementIcons.ELASTIC_SEARCH)
public class ElasticIndexingFilter extends AbstractXMLFilter {
    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;

    private String idFieldName;
    private DocRef indexRef;

    private final ElasticIndexConfigCache elasticIndexCache;

    private final ElasticIndexWriterFactory elasticProducerFactory;
    private final Security security;

    private ElasticIndexWriter elasticProducer = null;
    private ElasticIndexConfigDoc indexConfig = null;
    private Map<String, String> propertiesToIndex = null;
    private Locator locator = null;

    @Inject
    public ElasticIndexingFilter(final LocationFactoryProxy locationFactory,
                                 final ElasticIndexConfigCache elasticIndexCache,
                                 final Security security,
                                 final ErrorReceiverProxy errorReceiverProxy,
                                 final ElasticIndexWriterFactory elasticProducerFactory) {
        this.locationFactory = locationFactory;
        this.elasticIndexCache = elasticIndexCache;
        this.errorReceiverProxy = errorReceiverProxy;
        this.elasticProducerFactory = elasticProducerFactory;
        this.security = security;
    }

    @PipelineProperty(description = "The field name to use as the unique ID for records.")
    public void setIdFieldName(final String value) {
        this.idFieldName = value;
    }

    @PipelineProperty(description = "The elastic index to write records to.")
    @PipelinePropertyDocRef(types = ElasticIndexConfigDoc.DOCUMENT_TYPE)
    public void setIndex(final DocRef indexRef) {
        this.indexRef = indexRef;
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

            security.asUser(UserTokenUtil.create(UserService.STROOM_SERVICE_USER_NAME, null), () -> {
                // Get the index and index fields from the cache.
                indexConfig = elasticIndexCache.get(indexRef);
                if (indexConfig == null) {
                    log(Severity.FATAL_ERROR, "Unable to load index", null);
                    throw new LoggedException("Unable to load index");
                }
            });

            elasticProducer = elasticProducerFactory.create(indexRef).orElseThrow(() -> {
                String msg = "No Elastic Search connector is available to use";
                log(Severity.FATAL_ERROR, msg, null);
                return new LoggedException(msg);
            });

        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (DATA.equals(localName) && propertiesToIndex != null) {
            String name = atts.getValue(NAME);
            String value = atts.getValue(VALUE);
            if (name != null && value != null) {
                name = name.trim();
                value = value.trim();

                if (name.length() > 0 && value.length() > 0) {
                    propertiesToIndex.put(name, value);
                }
            }
        } else if (RECORD.equals(localName)) {
            // Create a document to store fields in.
            propertiesToIndex = new HashMap<>();
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (RECORD.equals(localName)) {
            if (elasticProducer == null) {
                //shouldn't get here as we should have had a FATAL on start processing, but just in case
                String msg = "No Elastic Search connector is available to use";
                log(Severity.FATAL_ERROR, msg, null);
                throw new LoggedException(msg);
            }
            elasticProducer.write(idFieldName,
                    indexConfig.getIndexName(),
                    indexConfig.getIndexedType(),
                    propertiesToIndex,
                    this::error);
            propertiesToIndex = null;
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void endProcessing() {
//        if (elasticProducer != null) {
//            elasticProducer.shutdown();
//            elasticProducer = null;
//        }

        super.endProcessing();
    }


    protected void error(final Exception e) {
        if (locator != null) {
            errorReceiverProxy.log(Severity.ERROR,
                    locationFactory.create(locator.getLineNumber(), locator.getColumnNumber()), getElementId(),
                    e.getMessage(), e);
        } else {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
        }
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
