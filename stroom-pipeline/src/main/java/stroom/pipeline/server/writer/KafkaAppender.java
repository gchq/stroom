package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@SuppressWarnings("unused")
@Component
@Scope(StroomScope.PROTOTYPE)
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
    public KafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
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
