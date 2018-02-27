package stroom.kafka;

import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.destination.RollingKafkaDestination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.writer.AbstractRollingAppender;
import stroom.pipeline.writer.PathCreator;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;

@ConfigurableElement(
        type = "RollingKafkaAppender",
        category = PipelineElementType.Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.KAFKA)
class RollingKafkaAppender extends AbstractRollingAppender {
    private final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService;
    private final PathCreator pathCreator;
    private final ErrorReceiverProxy errorReceiverProxy;

    private String topic;
    private String recordKey;
    private boolean flushOnSend = true;

    private String key;

    @Inject
    RollingKafkaAppender(final RollingDestinations destinations,
                         final TaskMonitor taskMonitor,
                         final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                         final PathCreator pathCreator,
                         final ErrorReceiverProxy errorReceiverProxy) {
        super(destinations, taskMonitor);
        this.stroomKafkaProducerFactoryService = stroomKafkaProducerFactoryService;
        this.pathCreator = pathCreator;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    protected void validateSpecificSettings() {
        if (recordKey == null || recordKey.length() == 0) {
            throw new ProcessException("No recordKey has been specified");
        }

        if (topic == null || topic.length() == 0) {
            throw new ProcessException("No topic has been specified");
        }
    }

    @Override
    protected Object getKey() {
        if (key == null) {
            //this allows us to have two destinations for the same key and topic but with different
            //flush semantics
            key = String.format("%s:%s:%s", this.topic, this.recordKey, Boolean.toString(flushOnSend));
        }
        return key;
    }

    @Override
    public RollingDestination createDestination() {
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactoryService.getConnector()
                .orElseThrow(() -> new ProcessException("No kafka producer available to use"));

        return new RollingKafkaDestination(
                key,
                getFrequency(),
                getMaxSize(),
                System.currentTimeMillis(),
                stroomKafkaProducer,
                recordKey,
                topic,
                flushOnSend);
    }

    @PipelineProperty(description = "The record key to apply to records, used to select partition. Replacement variables can be used in path strings such as ${feed}.")
    public void setRecordKey(final String recordKey) {
        this.recordKey = pathCreator.replaceAll(recordKey);
    }

    @PipelineProperty(description = "The topic to send the record to. Replacement variables can be used in path strings such as ${feed}.")
    public void setTopic(final String topic) {
        this.topic = pathCreator.replaceAll(topic);
    }

    @PipelineProperty(
            description = "Wait for acknowledgement from the Kafka broker when the appender is rolled" +
                    "This is slower but catches errors in the pipeline process",
            defaultValue = "false")
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }
}
