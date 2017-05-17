package stroom.internalstatistics;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.kafka.StroomKafkaProducer;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KafkaInternalStatisticsService implements InternalStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaInternalStatisticsService.class);

    private final StroomKafkaProducer stroomKafkaProducer;

    public KafkaInternalStatisticsService(final StroomKafkaProducer stroomKafkaProducer) {
        this.stroomKafkaProducer = stroomKafkaProducer;
    }

    @Override
    public void putEvents(final List<InternalStatisticEvent> internalStatisticEvents) {

    }

    private ProducerRecord<String, String> buildProducerRecord(List<InternalStatisticEvent> events) {

        events.stream()
                .collect(Collectors.groupingBy(InternalStatisticEvent::getStatisticName, Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .forEach(entry -> {
                    String statName = entry.getKey();
                    //all have same name so have same type
                    String topic = getTopic(entry.getValue().get(0).getType());
                    String message = buildMessage(entry.getValue());
                    ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, statName, message);
                    stroomKafkaProducer.send(producerRecord, exception -> {
                        LOGGER.error("Error sending {} internal statistics with name {} to kafka on topic {}",
                                entry.getValue().size(), statName, topic);
                    });
                });

    }

    private String buildMessage(List<InternalStatisticEvent> events) {

    }

    private String getTopic(final InternalStatisticEvent.Type statisticType) {

    }
}
