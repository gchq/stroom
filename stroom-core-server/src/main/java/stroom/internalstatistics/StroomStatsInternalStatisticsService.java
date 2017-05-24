package stroom.internalstatistics;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.kafka.StroomKafkaProducer;
import stroom.node.server.StroomPropertyService;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StroomStatsInternalStatisticsService implements InternalStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsInternalStatisticsService.class);

    private static final String PROP_KEY_DOC_REF_TYPE = "stroom.services.stroomStats.docRefType";

    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final StroomKafkaProducer stroomKafkaProducer;
    private final StroomPropertyService stroomPropertyService;
    private final String docRefType;

    @Inject
    public StroomStatsInternalStatisticsService(final DataSourceProviderRegistry dataSourceProviderRegistry,
                                                final StroomKafkaProducer stroomKafkaProducer,
                                                final StroomPropertyService stroomPropertyService) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.stroomKafkaProducer = stroomKafkaProducer;
        this.stroomPropertyService = stroomPropertyService;
        this.docRefType = stroomPropertyService.getProperty(PROP_KEY_DOC_REF_TYPE);
    }

    @Override
    public void putEvents(final List<DecoratedInternalStatisticEvent> internalStatisticEvents) {

        dataSourceProviderRegistry.getDataSourceProvider(docRefType).ifPresent(dataSourceProvider -> {

            internalStatisticEvents.stream()
                    .collect(Collectors.groupingBy(
                            DecoratedInternalStatisticEvent::getDocRef, Collectors.toList()))
                    .entrySet().stream()
                    .filter(entry ->
                            !entry.getValue().isEmpty())
                    .filter(entry ->
                            dataSourceProvider.getDataSource(entry.getKey()) != null)
                    .forEach(entry -> {
                        String statName = entry.getKey().getName();
                        //all have same name so have same type
                        String topic = getTopic(entry.getValue().get(0).getType());
                        String message = buildMessage(entry.getValue());
                        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, statName, message);
                        stroomKafkaProducer.send(producerRecord, exception -> {
                            throw new RuntimeException(String.format(
                                    "Error sending %s internal statistics with name %s to kafka on topic %s",
                                    entry.getValue().size(), statName, topic), exception);
                        });
                    });
        });
    }

    private String buildMessage(List<DecoratedInternalStatisticEvent> events) {

        //TODO - convert the list of internal stats into a single stroom-stats statistics object
        //then marhsall it to xml
        throw new RuntimeException(String.format("not yet implemented"));
    }

    private String getTopic(final InternalStatisticEvent.Type type) {

        //TODO get this from a stroom prop, one prop for each type or a single prop that holds a prefix
        throw new RuntimeException(String.format("not yet implemented"));
    }

    @Override
    public String getDocRefType() {
        return docRefType;
    }
}
