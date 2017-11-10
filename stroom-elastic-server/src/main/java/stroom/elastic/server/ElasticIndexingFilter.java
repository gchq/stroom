package stroom.elastic.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.connectors.elastic.StroomElasticProducer;
import stroom.connectors.elastic.StroomElasticProducerFactoryService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.query.api.v2.DocRef;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * The index filter... takes the index XML and builds the LUCENE documents
 */
@Component
@Scope(StroomScope.PROTOTYPE)
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticIndexingFilter.class);

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;

    private DocRef indexRef;

    private final ElasticIndexCache elasticIndexCache;

    private final StroomElasticProducerFactoryService elasticProducerFactoryService;

    private StroomElasticProducer elasticProducer = null;
    private ElasticIndexConfig indexConfig = null;
    private Map<String, String> propertiesToIndex = null;
    private Locator locator = null;

    @Inject
    public ElasticIndexingFilter(final LocationFactoryProxy locationFactory,
                                 final ElasticIndexCache elasticIndexCache,
                                 final ErrorReceiverProxy errorReceiverProxy,
                                 final StroomElasticProducerFactoryService elasticProducerFactoryService) {
        this.locationFactory = locationFactory;
        this.elasticIndexCache = elasticIndexCache;
        this.errorReceiverProxy = errorReceiverProxy;
        this.elasticProducerFactoryService = elasticProducerFactoryService;
    }

    @PipelineProperty(description = "The elastic index to send records to.")
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

            // Get the index and index fields from the cache.
            indexConfig = elasticIndexCache.getOrCreate(indexRef);
            if (indexConfig == null) {
                log(Severity.FATAL_ERROR, "Unable to load index", null);
                throw new LoggedException("Unable to load index");
            }

            elasticProducer = elasticProducerFactoryService.getProducer(e ->
                    errorReceiverProxy.log(Severity.ERROR, null, null, "Called function on Fake Elastic proxy", e));

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
            elasticProducer.send(indexConfig.getIndexName(),
                    indexConfig.getIndexedType(),
                    propertiesToIndex,
                    this::error);
            propertiesToIndex = null;
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void endProcessing() {
        elasticProducer.shutdown();
        elasticProducer = null;

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
