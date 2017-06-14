package stroom.statistics.server.stroomstats.kafka;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.shared.StatisticType;

import javax.inject.Inject;

@Component
public class TopicNameFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopicNameFactory.class);
    private static final String PROP_KEY_PREFIX_KAFKA_TOPICS = "stroom.services.stroomStats.kafkaTopics.";

    final StroomPropertyService stroomPropertyService;

    @Inject
    public TopicNameFactory(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    public String getTopic(final StatisticType statisticType) {
        String propKey = PROP_KEY_PREFIX_KAFKA_TOPICS + statisticType.toString().toLowerCase();
        String topic = stroomPropertyService.getProperty(propKey);

        if (Strings.isNullOrEmpty(topic)) {
            throw new RuntimeException(
                    String.format("Missing value for property %s, unable to send internal statistics", topic));
        }
        return topic;
    }
}
