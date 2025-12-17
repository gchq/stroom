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

package stroom.statistics.impl.hbase.internal;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class HBaseStatisticsConfig extends AbstractConfig implements IsStroomConfig {

    private final String docRefType;
    private final KafkaTopicsConfig kafkaTopicsConfig;
    private final String kafkaConfigUuid;
    private final int eventsPerMessage;

    public HBaseStatisticsConfig() {
        docRefType = "StroomStatsStore";
        kafkaTopicsConfig = new KafkaTopicsConfig();
        kafkaConfigUuid = null;
        eventsPerMessage = 100;
    }

    @JsonCreator
    public HBaseStatisticsConfig(@JsonProperty("docRefType") final String docRefType,
                                 @JsonProperty("kafkaTopics") final KafkaTopicsConfig kafkaTopicsConfig,
                                 @JsonProperty("kafkaConfigUuid") final String kafkaConfigUuid,
                                 @JsonProperty("eventsPerMessage") final int eventsPerMessage) {
        this.docRefType = docRefType;
        this.kafkaTopicsConfig = kafkaTopicsConfig;
        this.kafkaConfigUuid = kafkaConfigUuid;
        this.eventsPerMessage = eventsPerMessage;
    }

    @JsonPropertyDescription("The entity type for the stroom-stats service")
    public String getDocRefType() {
        return docRefType;
    }

    @JsonProperty("kafkaTopics")
    public KafkaTopicsConfig getKafkaTopicsConfig() {
        return kafkaTopicsConfig;
    }

    @JsonPropertyDescription("The UUID of the Kafka config document to use")
    public String getKafkaConfigUuid() {
        return kafkaConfigUuid;
    }

    @JsonPropertyDescription("The number of internal statistic events to batch together in a single Kafka message. " +
            "High numbers reduce network overhead but limit the parallelism.")
    public int getEventsPerMessage() {
        return eventsPerMessage;
    }

    public HBaseStatisticsConfig withDocRefType(final String docRefType) {
        return new HBaseStatisticsConfig(docRefType, kafkaTopicsConfig, kafkaConfigUuid, eventsPerMessage);
    }

    public HBaseStatisticsConfig withKafkaTopicConfig(final KafkaTopicsConfig kafkaTopicsConfig) {
        return new HBaseStatisticsConfig(docRefType, kafkaTopicsConfig, kafkaConfigUuid, eventsPerMessage);
    }

    public HBaseStatisticsConfig withEventsPerMessage(final int eventsPerMessage) {
        return new HBaseStatisticsConfig(docRefType, kafkaTopicsConfig, kafkaConfigUuid, eventsPerMessage);
    }

    @Override
    public String toString() {
        return "HBaseStatisticsConfig{" +
                "docRefType='" + docRefType + '\'' +
                ", kafkaTopicsConfig=" + kafkaTopicsConfig +
                ", kafkaConfigUuid='" + kafkaConfigUuid + '\'' +
                ", eventsPerMessage=" + eventsPerMessage +
                '}';
    }
}
