package stroom.proxy.app.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class KafkaEventConsumerConfig {
    private final String topic;
    private final KafkaProducerConfig producerConfig;

    public KafkaEventConsumerConfig() {
        topic = "events";
        producerConfig = new KafkaProducerConfig();
    }

    @JsonCreator
    public KafkaEventConsumerConfig(@JsonProperty("topic") final String topic,
                                    @JsonProperty("producerConfig") final KafkaProducerConfig producerConfig) {
        this.topic = topic;
        this.producerConfig = producerConfig;
    }

    @JsonProperty
    public String getTopic() {
        return topic;
    }

    @JsonProperty
    public KafkaProducerConfig getProducerConfig() {
        return producerConfig;
    }
}
