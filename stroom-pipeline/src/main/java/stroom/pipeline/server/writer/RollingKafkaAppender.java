package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.kafka.StroomKafkaProducer;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingKafkaDestination;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "RollingKafkaAppender", category = PipelineElementType.Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class RollingKafkaAppender extends AbstractRollingAppender {

    private final StroomKafkaProducer stroomKafkaProducer;
    private final PathCreator pathCreator;

    private String topic;
    private String partition;

    private String key;

    @Inject
    public RollingKafkaAppender(final StroomKafkaProducer stroomKafkaProducer,
                                final PathCreator pathCreator) {
        this.stroomKafkaProducer = stroomKafkaProducer;
        this.pathCreator = pathCreator;
    }


    @Override
    void validateSpecificSettings() {
        if (partition == null || partition.length() == 0) {
            throw new ProcessException("No recordKey has been specified");
        }

        if (topic == null || topic.length() == 0) {
            throw new ProcessException("No recordKey has been specified");
        }
    }

    @Override
    Object getKey() throws IOException {
        if (key == null) {
            key = String.format("%s:%s", this.topic, this.partition);
        }

        return key;
    }

    @Override
    public RollingDestination createDestination() throws IOException {
        return new RollingKafkaDestination(key,
                getFrequency(),
                getMaxSize(),
                System.currentTimeMillis(),
                stroomKafkaProducer,
                partition,
                topic);
    }

    @PipelineProperty(description = "The partition to send the record on. Replacement variables can be used in path strings such as ${feed}.")
    public void setPartition(final String partition) {
        this.partition = pathCreator.replaceAll(partition);
    }

    @PipelineProperty(description = "The topic to send the record to. Replacement variables can be used in path strings such as ${feed}.")
    public void setTopic(final String topic) {
        this.topic = pathCreator.replaceAll(topic);
    }
}
