package stroom.statistics.stroomstats.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.properties.api.PropertyService;
import stroom.statistics.shared.StatisticType;

import javax.inject.Inject;

public class TopicNameFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopicNameFactory.class);
    private static final String PROP_KEY_PREFIX_KAFKA_TOPICS = "stroom.services.stroomStats.kafkaTopics.";

    final PropertyService stroomPropertyService;

    @Inject
    TopicNameFactory(final PropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    public String getTopic(final StatisticType statisticType) {
        String propKey = PROP_KEY_PREFIX_KAFKA_TOPICS + statisticType.toString().toLowerCase();
        String topic = stroomPropertyService.getProperty(propKey);

        if (topic == null || topic.isEmpty()) {
            throw new RuntimeException(
                    String.format("Missing value for property %s, unable to send internal statistics", topic));
        }
        return topic;
    }
}
