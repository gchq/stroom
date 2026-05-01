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

package stroom.proxy.app.pipeline;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.Objects;

/**
 * Top-level configuration holder for the reference-message proxy pipeline.
 */
@JsonPropertyOrder(alphabetic = true)
public class ProxyPipelineConfig extends AbstractConfig implements IsProxyConfig {

    public static final String SPLIT_ZIP_INPUT_QUEUE = "splitZipInput";
    public static final String PRE_AGGREGATE_INPUT_QUEUE = "preAggregateInput";
    public static final String AGGREGATE_INPUT_QUEUE = "aggregateInput";
    public static final String FORWARDING_INPUT_QUEUE = "forwardingInput";

    public static final String RECEIVE_STORE = "receiveStore";
    public static final String SPLIT_STORE = "splitStore";
    public static final String PRE_AGGREGATE_STORE = "preAggregateStore";
    public static final String AGGREGATE_STORE = "aggregateStore";

    private final boolean enabled;
    private final Map<String, QueueDefinition> queues;
    private final PipelineStagesConfig stages;
    private final Map<String, FileStoreDefinition> fileStores;

    public ProxyPipelineConfig() {
        this(false, defaultQueues(), new PipelineStagesConfig(), defaultFileStores());
    }

    /**
     * Convenience constructor for tests that don't need to set the enabled flag.
     */
    public ProxyPipelineConfig(final Map<String, QueueDefinition> queues,
                               final PipelineStagesConfig stages,
                               final Map<String, FileStoreDefinition> fileStores) {
        this(false, queues, stages, fileStores);
    }

    @JsonCreator
    public ProxyPipelineConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("queues") final Map<String, QueueDefinition> queues,
            @JsonProperty("stages") final PipelineStagesConfig stages,
            @JsonProperty("fileStores") final Map<String, FileStoreDefinition> fileStores) {

        this.enabled = enabled != null && enabled;
        this.queues = queues == null || queues.isEmpty()
                ? defaultQueues()
                : Map.copyOf(queues);
        // When pipeline is enabled and no explicit stages block is provided,
        // automatically wire all 5 stages with standard queue/store references.
        if (this.enabled && stages == null) {
            this.stages = defaultFullPipelineStages();
        } else {
            this.stages = Objects.requireNonNullElseGet(stages, PipelineStagesConfig::new);
        }
        this.fileStores = fileStores == null || fileStores.isEmpty()
                ? defaultFileStores()
                : Map.copyOf(fileStores);
    }

    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @Valid
    @JsonProperty
    public Map<String, QueueDefinition> getQueues() {
        return queues;
    }

    @Valid
    @JsonProperty
    public PipelineStagesConfig getStages() {
        return stages;
    }

    @Valid
    @JsonProperty
    public Map<String, FileStoreDefinition> getFileStores() {
        return fileStores;
    }

    /**
     * @return A fully-wired stages config with all 5 stages enabled and connected
     * to the standard queue and file-store names. This is used as the default when
     * {@code pipeline.enabled=true} and no explicit stages block is provided.
     */
    public static PipelineStagesConfig defaultFullPipelineStages() {
        return new PipelineStagesConfig(
                new PipelineStageConfig(
                        true,
                        null,
                        PRE_AGGREGATE_INPUT_QUEUE,
                        SPLIT_ZIP_INPUT_QUEUE,
                        RECEIVE_STORE,
                        new PipelineStageThreadsConfig()),
                new PipelineStageConfig(
                        true,
                        SPLIT_ZIP_INPUT_QUEUE,
                        PRE_AGGREGATE_INPUT_QUEUE,
                        null,
                        SPLIT_STORE,
                        new PipelineStageThreadsConfig()),
                new PipelineStageConfig(
                        true,
                        PRE_AGGREGATE_INPUT_QUEUE,
                        AGGREGATE_INPUT_QUEUE,
                        null,
                        PRE_AGGREGATE_STORE,
                        new PipelineStageThreadsConfig()),
                new PipelineStageConfig(
                        true,
                        AGGREGATE_INPUT_QUEUE,
                        FORWARDING_INPUT_QUEUE,
                        null,
                        AGGREGATE_STORE,
                        new PipelineStageThreadsConfig()),
                new PipelineStageConfig(
                        true,
                        FORWARDING_INPUT_QUEUE,
                        null,
                        null,
                        null,
                        new PipelineStageThreadsConfig()));
    }

    private static Map<String, QueueDefinition> defaultQueues() {
        return Map.of(
                SPLIT_ZIP_INPUT_QUEUE, new QueueDefinition(),
                PRE_AGGREGATE_INPUT_QUEUE, new QueueDefinition(),
                AGGREGATE_INPUT_QUEUE, new QueueDefinition(),
                FORWARDING_INPUT_QUEUE, new QueueDefinition());
    }

    private static Map<String, FileStoreDefinition> defaultFileStores() {
        return Map.of(
                RECEIVE_STORE, new FileStoreDefinition(),
                SPLIT_STORE, new FileStoreDefinition(),
                PRE_AGGREGATE_STORE, new FileStoreDefinition(),
                AGGREGATE_STORE, new FileStoreDefinition());
    }
}
