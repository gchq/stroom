package stroom.statistics.stroomstats.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class HBaseStatisticsConfig {
    private String docRefType = "StroomStatsStore";
    private KafkaTopicsConfig kafkaTopicsConfig = new KafkaTopicsConfig();
    private String kafkaConfigUuid;
    private int eventsPerMessage = 100;

    @JsonPropertyDescription("The entity type for the stroom-stats service")
    public String getDocRefType() {
        return docRefType;
    }

    public void setDocRefType(final String docRefType) {
        this.docRefType = docRefType;
    }

    @JsonProperty("kafkaTopics")
    public KafkaTopicsConfig getKafkaTopicsConfig() {
        return kafkaTopicsConfig;
    }

    public void setKafkaTopicsConfig(final KafkaTopicsConfig kafkaTopicsConfig) {
        this.kafkaTopicsConfig = kafkaTopicsConfig;
    }

    @JsonPropertyDescription("The UUID of the Kafka config to use")
    public String getKafkaConfigUuid() {
        return kafkaConfigUuid;
    }

    public void setKafkaConfigUuid(final String kafkaConfigUuid) {
        this.kafkaConfigUuid = kafkaConfigUuid;
    }

    @JsonPropertyDescription("The number of internal statistic events to batch together in a single Kafka message. High numbers reduce network overhead but limit the parallelism.")
    public int getEventsPerMessage() {
        return eventsPerMessage;
    }

    public void setEventsPerMessage(final int eventsPerMessage) {
        this.eventsPerMessage = eventsPerMessage;
    }
}
