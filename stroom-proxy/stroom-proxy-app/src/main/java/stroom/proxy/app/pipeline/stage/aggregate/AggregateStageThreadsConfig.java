/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.aggregate;

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * The aggregate stage's threads: claimers, which hold the open aggregates, and merge workers,
 * which close them ({@code designs/stages/aggregate.md §4.2}).
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class AggregateStageThreadsConfig extends ConsumerStageThreadsConfig {

    public static final int DEFAULT_MERGE_THREADS = 1;

    private final int mergeThreads;

    public AggregateStageThreadsConfig() {
        this(null, null);
    }

    @JsonCreator
    public AggregateStageThreadsConfig(
            @JsonProperty("consumerThreads") final Integer consumerThreads,
            @JsonProperty("mergeThreads") final Integer mergeThreads) {
        super(consumerThreads);
        this.mergeThreads = Objects.requireNonNullElse(mergeThreads, DEFAULT_MERGE_THREADS);
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("Threads claiming inputs from the input queue; each holds its own open aggregates. "
                             + "More than one is only allowed when the input queue is Kafka, where each thread is "
                             + "a consumer with its own partitions and a feed still reaches one of them.")
    public int getConsumerThreads() {
        return super.getConsumerThreads();
    }

    @JsonProperty
    @JsonPropertyDescription("Threads merging closed aggregates into the aggregate store and publishing them. "
                             + "One behaves like merging in line; more overlap the network round trips of an "
                             + "S3 store.")
    public int getMergeThreads() {
        return mergeThreads;
    }
}
