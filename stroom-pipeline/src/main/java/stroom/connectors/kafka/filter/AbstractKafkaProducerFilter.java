package stroom.connectors.kafka.filter;

import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.filter.AbstractSamplingFilter;
import stroom.util.shared.Severity;

public abstract class AbstractKafkaProducerFilter extends AbstractSamplingFilter {

    private boolean flushOnSend;
    private final StroomKafkaProducer stroomKafkaProducer;
    private final ErrorReceiverProxy errorReceiverProxy;

    public AbstractKafkaProducerFilter(
            final ErrorReceiverProxy errorReceiverProxy,
            final LocationFactoryProxy locationFactory,
            final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService) {

        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.stroomKafkaProducer = stroomKafkaProducerFactoryService.getProducer(exception ->
                errorReceiverProxy.log(Severity.ERROR, null, null, "Called function on Fake Kafka proxy!", exception)
        );
        this.flushOnSend = true;
    }

    @PipelineProperty(description="Flush the producer each time a message is sent")
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    void sendOutputToKafka() {
        final StroomKafkaProducerRecord<String, String> newRecord =
                new StroomKafkaProducerRecord.Builder<String, String>()
                        .topic(getTopic())
                        .key(getRecordKey())
                        .value(getOutput())
                        .build();
        try {
            stroomKafkaProducer.send(newRecord, flushOnSend, exception ->
                    errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", exception)
            );
        } catch (RuntimeException e) {
            errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", e);
        }
    }

    public abstract String getTopic();

    public abstract String getRecordKey();
}
