package stroom.pipeline.server.filter;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.StroomKafkaProducer;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "KafkaProducerFilter", category = PipelineElementType.Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS,
        PipelineElementType.VISABILITY_SIMPLE}, icon = ElementIcons.STREAM)
public class KafkaProducerFilter extends AbstractSamplingFilter {
    private final ErrorReceiverProxy errorReceiverProxy;
    private final StroomKafkaProducer stroomKafkaProducer;

    private String recordKey;
    private String topic;

    @Inject
    public KafkaProducerFilter(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory, final StroomKafkaProducer stroomKafkaProducer) {
        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.stroomKafkaProducer = stroomKafkaProducer;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        ProducerRecord<String, String> newRecord = new ProducerRecord<>(topic, recordKey, getOutput());
        try{
            stroomKafkaProducer.send(newRecord, errorReceiverProxy);
        }
        catch(RuntimeException e){
            errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", e);
        }
    }

    @PipelineProperty(description = "The key for the record. This determines the partition.")
    public void setRecordKey(final String recordKey) {
        this.recordKey = recordKey;
    }

    @PipelineProperty(description = "The topic to send the record to.")
    public void setTopic(final String topic) {
        this.topic = topic;
    }
}