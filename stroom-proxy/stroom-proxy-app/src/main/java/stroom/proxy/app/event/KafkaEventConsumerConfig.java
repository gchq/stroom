/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
