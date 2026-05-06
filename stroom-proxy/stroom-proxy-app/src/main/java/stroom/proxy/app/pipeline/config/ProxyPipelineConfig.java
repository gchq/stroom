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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.TreeMap;


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

    private final Map<String, QueueDefinition> queues;
    private final PipelineStagesConfig stages;
    private final Map<String, FileStoreDefinition> fileStores;

    public ProxyPipelineConfig() {
        this(null, null, null);
    }


    @JsonCreator
    public ProxyPipelineConfig(
            @JsonProperty("queues") final Map<String, QueueDefinition> queues,
            @JsonProperty("stages") final PipelineStagesConfig stages,
            @JsonProperty("fileStores") final Map<String, FileStoreDefinition> fileStores) {

        this.queues = queues == null || queues.isEmpty()
                ? defaultQueues()
                : new TreeMap<>(queues);
        // When no explicit stages block is provided, automatically wire
        // all 5 stages with standard queue/store references.
        this.stages = stages == null
                ? defaultFullPipelineStages()
                : stages;
        this.fileStores = fileStores == null || fileStores.isEmpty()
                ? defaultFileStores()
                : new TreeMap<>(fileStores);
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
     * to the standard queue and file-store names. This is the default when no
     * explicit stages block is provided in YAML.
     */
    public static PipelineStagesConfig defaultFullPipelineStages() {
        return new PipelineStagesConfig(
                new ReceiveStageConfig(
                        true,
                        PRE_AGGREGATE_INPUT_QUEUE,
                        SPLIT_ZIP_INPUT_QUEUE,
                        RECEIVE_STORE,
                        new ReceiveStageThreadsConfig()),
                new SplitZipStageConfig(
                        true,
                        SPLIT_ZIP_INPUT_QUEUE,
                        PRE_AGGREGATE_INPUT_QUEUE,
                        SPLIT_STORE,
                        new ConsumerStageThreadsConfig()),
                new PreAggregateStageConfig(
                        true,
                        PRE_AGGREGATE_INPUT_QUEUE,
                        AGGREGATE_INPUT_QUEUE,
                        PRE_AGGREGATE_STORE,
                        new PreAggregateStageThreadsConfig()),
                new AggregateStageConfig(
                        true,
                        AGGREGATE_INPUT_QUEUE,
                        FORWARDING_INPUT_QUEUE,
                        AGGREGATE_STORE,
                        new ConsumerStageThreadsConfig()),
                new ForwardStageConfig(
                        true,
                        FORWARDING_INPUT_QUEUE,
                        new ConsumerStageThreadsConfig()));
    }

    private static Map<String, QueueDefinition> defaultQueues() {
        final TreeMap<String, QueueDefinition> map = new TreeMap<>();
        map.put(SPLIT_ZIP_INPUT_QUEUE, new QueueDefinition());
        map.put(PRE_AGGREGATE_INPUT_QUEUE, new QueueDefinition());
        map.put(AGGREGATE_INPUT_QUEUE, new QueueDefinition());
        map.put(FORWARDING_INPUT_QUEUE, new QueueDefinition());
        return map;
    }

    private static Map<String, FileStoreDefinition> defaultFileStores() {
        final TreeMap<String, FileStoreDefinition> map = new TreeMap<>();
        map.put(RECEIVE_STORE, new FileStoreDefinition());
        map.put(SPLIT_STORE, new FileStoreDefinition());
        map.put(PRE_AGGREGATE_STORE, new FileStoreDefinition());
        map.put(AGGREGATE_STORE, new FileStoreDefinition());
        return map;
    }
}
