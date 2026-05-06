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

package stroom.proxy.app.pipeline.stage.preaggregate;

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * Thread configuration for the pre-aggregate stage.
 * <p>
 * Extends {@link ConsumerStageThreadsConfig} with an additional thread
 * setting for the background task that closes old aggregates.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class PreAggregateStageThreadsConfig extends ConsumerStageThreadsConfig {

    public static final int DEFAULT_CLOSE_OLD_AGGREGATES_THREADS = 1;

    private final int closeOldAggregatesThreads;

    public PreAggregateStageThreadsConfig() {
        this(null, DEFAULT_CLOSE_OLD_AGGREGATES_THREADS);
    }

    @JsonCreator
    public PreAggregateStageThreadsConfig(
            @JsonProperty("consumerThreads") final Integer consumerThreads,
            @JsonProperty("closeOldAggregatesThreads") final Integer closeOldAggregatesThreads) {

        super(consumerThreads);
        this.closeOldAggregatesThreads = Objects.requireNonNullElse(
                closeOldAggregatesThreads, DEFAULT_CLOSE_OLD_AGGREGATES_THREADS);
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("Number of threads used by pre-aggregation to close old aggregates.")
    public int getCloseOldAggregatesThreads() {
        return closeOldAggregatesThreads;
    }
}
