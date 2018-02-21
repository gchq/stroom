package stroom.pipeline.writer;

import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;

import javax.inject.Inject;

/**
 * A generic Kafka appender for sending messages to kafka with any key and topic
 */
@SuppressWarnings("unused")
@ConfigurableElement(
        type = "KafkaAppender",
        category = PipelineElementType.Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.KAFKA)
public class KafkaAppender extends AbstractKafkaAppender {

    private final PathCreator pathCreator;

    private String topic;
    private String recordKey;

    @SuppressWarnings("unused")
    @Inject
    KafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
                  final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                  final PathCreator pathCreator) {
        super(errorReceiverProxy, stroomKafkaProducerFactoryService);
        this.pathCreator = pathCreator;
    }

    @PipelineProperty(description = "This key to apply to the records, used to select partition.")
    public void setRecordKey(final String recordKey) {
        this.recordKey = pathCreator.replaceAll(recordKey);
    }

    @PipelineProperty(description = "The topic to send the record to.")
    public void setTopic(final String topic) {
        this.topic = pathCreator.replaceAll(topic);
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public String getRecordKey() {
        return recordKey;
    }
}
