package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingKafkaDestination;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.io.IOException;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "RollingKafkaAppender", category = PipelineElementType.Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class RollingKafkaAppender extends AbstractRollingAppender {

    @Resource
    private StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService;

    @Resource
    private PathCreator pathCreator;

    private String topic;
    private String recordKey;
    private boolean flushOnSend;

    private String key;

    @Override
    void validateSpecificSettings() {
        if (recordKey == null || recordKey.length() == 0) {
            throw new ProcessException("No recordKey has been specified");
        }

        if (topic == null || topic.length() == 0) {
            throw new ProcessException("No topic has been specified");
        }
    }

    @Override
    Object getKey() throws IOException {
        if (key == null) {
            key = String.format("%s:%s", this.topic, this.recordKey);
        }

        return key;
    }

    @Override
    public RollingDestination createDestination() throws IOException {
        return new RollingKafkaDestination(key,
                getFrequency(),
                getMaxSize(),
                System.currentTimeMillis(),
                stroomKafkaProducerFactoryService.getProducer(),
                recordKey,
                topic,
                flushOnSend);
    }

    @PipelineProperty(description = "The record key to apply to records, used to select patition. Replacement variables can be used in path strings such as ${feed}.")
    public void setRecordKey(final String recordKey) {
        this.recordKey = pathCreator.replaceAll(recordKey);
    }

    @PipelineProperty(description = "The topic to send the record to. Replacement variables can be used in path strings such as ${feed}.")
    public void setTopic(final String topic) {
        this.topic = pathCreator.replaceAll(topic);
    }

    @PipelineProperty(description="Flush the producer each time a message is sent")
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }
}
