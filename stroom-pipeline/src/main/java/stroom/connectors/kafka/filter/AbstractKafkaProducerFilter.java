package stroom.connectors.kafka.filter;

import com.google.common.base.Strings;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.filter.AbstractSamplingFilter;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

public abstract class AbstractKafkaProducerFilter extends AbstractSamplingFilter {

    private boolean flushOnSend;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService;

    private StroomKafkaProducer stroomKafkaProducer;

    private Locator locator;

    public AbstractKafkaProducerFilter(final ErrorReceiverProxy errorReceiverProxy,
                                       final LocationFactoryProxy locationFactory,
                                       final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService) {

        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.stroomKafkaProducerFactoryService = stroomKafkaProducerFactoryService;
        this.flushOnSend = true;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        sendOutputToKafka();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    @Override
    public void startProcessing() {
        if (Strings.isNullOrEmpty(getTopic())) {
            String msg = "A Kafka topic has not been set";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }
        if (Strings.isNullOrEmpty(getRecordKey())) {
            String msg = "A Kafka record key has not been set";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }
        try {
            this.stroomKafkaProducer = stroomKafkaProducerFactoryService.getProducer().orElse(null);
        } catch (Exception e) {
            String msg = "Error initialising kafka producer - " + e.getMessage();
            log(Severity.FATAL_ERROR, msg, e);
            throw new LoggedException(msg);
        }

        if (stroomKafkaProducer == null) {
            String msg = "No Kafka producer connector is available, check Stroom's configuration";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }
    }

    @PipelineProperty(description = "Flush the producer each time a message is sent")
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    void sendOutputToKafka() {
        String topic = getTopic();
        String recordKey = getRecordKey();

        final StroomKafkaProducerRecord<String, byte[]> newRecord =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(getTopic())
                        .key(getRecordKey())
                        .value(getOutput().getBytes(StreamUtil.DEFAULT_CHARSET))
                        .build();
        try {
            stroomKafkaProducer.send(newRecord, flushOnSend, this::error);
        } catch (RuntimeException e) {
            error(e);
        }
    }

    public abstract String getTopic();

    public abstract String getRecordKey();

    protected void error(final Exception e) {
        if (locator != null) {
            errorReceiverProxy.log(Severity.ERROR,
                    locationFactory.create(locator.getLineNumber(), locator.getColumnNumber()), getElementId(),
                    e.getMessage(), e);
        } else {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
        }
    }

    protected void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
