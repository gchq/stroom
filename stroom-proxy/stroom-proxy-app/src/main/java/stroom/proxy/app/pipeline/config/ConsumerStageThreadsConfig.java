/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.proxy.app.pipeline.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * Thread configuration for queue-consuming pipeline stages.
 * <p>
 * Shared by split-zip, aggregate, and forward stages.
 * Pre-aggregate extends this class with additional thread settings.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ConsumerStageThreadsConfig extends AbstractConfig implements IsProxyConfig {

    public static final int DEFAULT_CONSUMER_THREADS = 1;

    private final int consumerThreads;

    public ConsumerStageThreadsConfig() {
        this(DEFAULT_CONSUMER_THREADS);
    }

    @JsonCreator
    public ConsumerStageThreadsConfig(
            @JsonProperty("consumerThreads") final Integer consumerThreads) {

        this.consumerThreads = Objects.requireNonNullElse(consumerThreads, DEFAULT_CONSUMER_THREADS);
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("Number of worker threads consuming this stage's input queue.")
    public int getConsumerThreads() {
        return consumerThreads;
    }
}
