package stroom.statistics.impl.hbase.internal;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class KafkaTopicsConfig extends AbstractConfig {

    private final String count;
    private final String value;

    public KafkaTopicsConfig() {
        count = "statisticEvents-Count";
        value = "statisticEvents-Value";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public KafkaTopicsConfig(@JsonProperty("count") final String count,
                             @JsonProperty("value") final String value) {
        this.count = count;
        this.value = value;
    }

    @JsonPropertyDescription("The kafka topic to send Count type stroom-stats statistic events to")
    public String getCount() {
        return count;
    }

    @JsonPropertyDescription("The kafka topic to send Value type stroom-stats statistic events to")
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "KafkaTopicsConfig{" +
                "count='" + count + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
