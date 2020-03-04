package stroom.statistics.impl.hbase.pipeline;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.docref.DocRef;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractSamplingFilter;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import java.util.Collections;

public abstract class AbstractKafkaProducerFilter extends AbstractSamplingFilter {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractKafkaProducerFilter.class);

    private boolean flushOnSend;
    private DocRef kafkaConfigRef;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final KafkaProducerFactory stroomKafkaProducerFactory;

    private org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;

    private Locator locator;

    protected AbstractKafkaProducerFilter(final ErrorReceiverProxy errorReceiverProxy,
                                          final LocationFactoryProxy locationFactory,
                                          final KafkaProducerFactory stroomKafkaProducerFactory) {

        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.stroomKafkaProducerFactory = stroomKafkaProducerFactory;
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

    /*
    Warning! This software has not been tested recently and is likely to need some rework as a number of things
    have moved on, including the way that KafkaProducer works.
    todo test and fix as appropriate!
     */
    @Override
    public void startProcessing() {
        super.startProcessing();

        if (kafkaConfigRef == null) {
            throw new ProcessException("No Kafka config has been specified");
        }

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
            this.kafkaProducer = stroomKafkaProducerFactory.createProducer(kafkaConfigRef).orElse(null);
        } catch (final RuntimeException e) {
            String msg = "Error initialising kafka producer - " + e.getMessage();
            log(Severity.FATAL_ERROR, msg, e);
            throw new LoggedException(msg);
        }

        if (kafkaProducer == null) {
            String msg = "No Kafka producer connector is available, check Stroom's configuration";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }
    }

    @PipelineProperty(
            description = "Not available in this version.",
            defaultValue = "false",
            displayPriority = 3)
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    @PipelineProperty(description = "The Kafka config to use.", displayPriority = 1)
    @PipelinePropertyDocRef(types = KafkaConfigDoc.DOCUMENT_TYPE)
    public void setKafkaConfig(final DocRef kafkaConfigRef) {
        this.kafkaConfigRef = kafkaConfigRef;
    }

    void sendOutputToKafka() {

        final ProducerRecord<String, String> record = new ProducerRecord(getTopic(),
                getRecordKey(), getOutput().getBytes(StreamUtil.DEFAULT_CHARSET));

        try {
            kafkaProducer.send(record);
            if (flushOnSend) {
                throw new UnsupportedOperationException("Flush on send is not available in this version of Stroom");
            }
        } catch (RuntimeException e) {
            error(e);
        }
    }

    public abstract String getTopic();

    public abstract String getRecordKey();

    protected void error(final RuntimeException e) {
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
