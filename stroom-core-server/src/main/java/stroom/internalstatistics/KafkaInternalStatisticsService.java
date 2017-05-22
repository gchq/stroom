package stroom.internalstatistics;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.kafka.StroomKafkaProducer;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.shared.StatisticType;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class KafkaInternalStatisticsService implements InternalStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaInternalStatisticsService.class);

    public static final String NAME = "kafka";

    private final StroomKafkaProducer stroomKafkaProducer;

    public KafkaInternalStatisticsService(final StroomKafkaProducer stroomKafkaProducer) {
        this.stroomKafkaProducer = stroomKafkaProducer;
    }

    @Override
    public void putEvents(final List<StatisticEvent> internalStatisticEvents) {
        internalStatisticEvents.stream()
                .collect(Collectors.groupingBy(StatisticEvent::getName, Collectors.toList()))
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

    private String buildMessage(List<StatisticEvent> events) {

        //TODO - convert the list of internal stats into a single stroom-stats statistics object
        //then marhsall it to xml
        throw new RuntimeException(String.format("not yet implemented"));
    }

    private String getTopic(final StatisticType statisticType) {

        //TODO get this from a stroom prop, one prop for each type or a single prop that holds a prefix
        throw new RuntimeException(String.format("not yet implemented"));
    }

    @Override
    public String getName() {
        return NAME;
    }
}
