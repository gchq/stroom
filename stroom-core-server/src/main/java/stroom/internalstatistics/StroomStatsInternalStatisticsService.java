package stroom.internalstatistics;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.DataSourceProvider;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.datasource.api.v1.DataSource;
import stroom.kafka.StroomKafkaProducer;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.shared.StatisticType;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StroomStatsInternalStatisticsService implements InternalStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsInternalStatisticsService.class);

    public static final String NAME = "stroom-stats";
    public static final String DOC_REF_TYPE_PROP_KEY = "stroom.services.stroomStats.docRefType";

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
        this.docRefType = stroomPropertyService.getProperty(DOC_REF_TYPE_PROP_KEY);
    }

    @Override
    public void putEvents(final List<StatisticEvent> internalStatisticEvents) {

        dataSourceProviderRegistry.getDataSourceProvider(docRefType).ifPresent(dataSourceProvider -> {

            internalStatisticEvents.stream()
                    .collect(Collectors.groupingBy(StatisticEvent::getName, Collectors.toList()))
                    .entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .filter(entry -> getDataSource(dataSourceProvider, entry.getKey()) != null)
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

    private DataSource getDataSource(final DataSourceProvider dataSourceProvider, final String name) {
        //TODO need to get the datasource by name and possibly type as we don't have a uuid
//       return dataSourceProvider.

        return null;

    }

    @Override
    public String getName() {
        return NAME;
    }
}
