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
public class KafkaTopicsConfig extends AbstractConfig implements IsStroomConfig {

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
