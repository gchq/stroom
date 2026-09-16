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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.TreeMap;


/**
 * Top-level configuration holder for the reference-message proxy pipeline.
 */
@JsonPropertyOrder(alphabetic = true)
public class ProxyPipelineConfig extends AbstractConfig implements IsProxyConfig {

    public static final String SPLIT_ZIP_INPUT_QUEUE = "splitZipInput";
    public static final String AGGREGATE_INPUT_QUEUE = "aggregateInput";
    public static final String FORWARDING_INPUT_QUEUE = "forwardingInput";

    public static final String RECEIVE_STORE = "receiveStore";
    public static final String SPLIT_STORE = "splitStore";
    public static final String AGGREGATE_STORE = "aggregateStore";

    public static final String PROP_NAME_STAGES = "stages";
    public static final String PROP_NAME_MODE = "mode";

    private final PipelineMode mode;
    private final Map<String, QueueDefinition> queues;
    private final PipelineStagesConfig stages;
    private final Map<String, FileStoreDefinition> fileStores;

    /**
     * The programmatic default is the standard full pipeline. That is a convenience for tests and
     * embedded callers, <strong>not</strong> a reading of an absent {@code stages} block - see the
     * {@link JsonCreator} constructor, which keeps a null block null so the operator's silence is
     * still visible to validation.
     */
    public ProxyPipelineConfig() {
        this(defaultQueues(), defaultFullPipelineStages(), defaultFileStores());
    }

    /**
     * Nothing stated: what a {@code proxyConfig} with no {@code pipeline} block gets, so validation
     * can say so rather than a default pipeline running in its place.
     */
    public static ProxyPipelineConfig unconfigured() {
        return new ProxyPipelineConfig(null, null, null, null);
    }

    /**
     * For code that builds a local pipeline directly: a null map means the standard queues or
     * stores. Configuration never comes through here.
     */
    public ProxyPipelineConfig(final Map<String, QueueDefinition> queues,
                               final PipelineStagesConfig stages,
                               final Map<String, FileStoreDefinition> fileStores) {
        this(PipelineMode.LOCAL,
                queues == null ? defaultQueues() : queues,
                stages,
                fileStores == null ? defaultFileStores() : fileStores);
    }

    /**
     * What the operator wrote, and nothing else. A block that is absent stays absent - an unstated
     * mode is null, unstated queues or stores are an empty map, an unstated stage set is
     * {@link PipelineStagesConfig#unconfigured()} - so the validator can report it. No compile-time
     * default is merged into a pipeline.
     */
    @JsonCreator
    public ProxyPipelineConfig(
            @JsonProperty(PROP_NAME_MODE) final PipelineMode mode,
            @JsonProperty("queues") final Map<String, QueueDefinition> queues,
            @JsonProperty("stages") final PipelineStagesConfig stages,
            @JsonProperty("fileStores") final Map<String, FileStoreDefinition> fileStores) {
        this.mode = mode;
        this.queues = queues == null
                ? Map.of()
                : new TreeMap<>(queues);
        // No stages block means nothing is stated, not "everything" (R14). The field is non-null
        // because the config tree walkers (AbstractConfigUtil.mutateBranch, and the injectable-config
        // provider) dereference every branch; the "was it stated" signal lives in
        // getConfiguredStages(), which stays empty here, so validation still sees the silence.
        this.stages = stages != null
                ? stages
                : PipelineStagesConfig.unconfigured();
        this.fileStores = fileStores == null
                ? Map.of()
                : new TreeMap<>(fileStores);
    }

    @JsonProperty(PROP_NAME_MODE)
    @JsonPropertyDescription("How this proxy is deployed, and required. LOCAL: one node, local queues and local file "
                             + "stores. SHARED: many nodes, distributed queues (SQS or Kafka) and shared file stores "
                             + "(S3 or SHARED_FILESYSTEM). Every queue and store must fit the mode.")
    public PipelineMode getMode() {
        return mode;
    }

    @JsonProperty
    public Map<String, QueueDefinition> getQueues() {
        return queues;
    }

    @JsonProperty
    public PipelineStagesConfig getStages() {
        return stages;
    }

    @JsonProperty
    public Map<String, FileStoreDefinition> getFileStores() {
        return fileStores;
    }

    /**
     * @return A fully-wired stages config with all four stages enabled and connected to the
     * standard queue and file-store names: what the no-arg constructor builds, for tests and for
     * the generated default YAML. A YAML that omits the stages block gets nothing filled in; the
     * validator reports every stage as unconfigured.
     */
    public static PipelineStagesConfig defaultFullPipelineStages() {
        return new PipelineStagesConfig(
                defaultReceiveStage(),
                defaultSplitZipStage(),
                defaultAggregateStage(),
                defaultForwardStage());
    }

    /**
     * The standard wiring for a single stage, used by {@link #defaultFullPipelineStages()}. A
     * stage omitted from a YAML {@code stages} block is not filled from here: it is recorded as
     * unconfigured and refused by the validator.
     */
    public static ReceiveStageConfig defaultReceiveStage() {
        return new ReceiveStageConfig(
                true,
                AGGREGATE_INPUT_QUEUE,
                SPLIT_ZIP_INPUT_QUEUE,
                RECEIVE_STORE);
    }

    public static SplitZipStageConfig defaultSplitZipStage() {
        return new SplitZipStageConfig(
                true,
                SPLIT_ZIP_INPUT_QUEUE,
                AGGREGATE_INPUT_QUEUE,
                SPLIT_STORE,
                new ConsumerStageThreadsConfig());
    }

    public static AggregateStageConfig defaultAggregateStage() {
        return new AggregateStageConfig(
                true,
                AGGREGATE_INPUT_QUEUE,
                FORWARDING_INPUT_QUEUE,
                AGGREGATE_STORE,
                new AggregateStageThreadsConfig());
    }

    public static ForwardStageConfig defaultForwardStage() {
        return new ForwardStageConfig(
                true,
                FORWARDING_INPUT_QUEUE,
                new ConsumerStageThreadsConfig());
    }

    private static Map<String, QueueDefinition> defaultQueues() {
        final Map<String, QueueDefinition> map = new TreeMap<>();
        map.put(SPLIT_ZIP_INPUT_QUEUE, new QueueDefinition());
        map.put(AGGREGATE_INPUT_QUEUE, new QueueDefinition());
        map.put(FORWARDING_INPUT_QUEUE, new QueueDefinition());
        return map;
    }

    private static Map<String, FileStoreDefinition> defaultFileStores() {
        final Map<String, FileStoreDefinition> map = new TreeMap<>();
        map.put(RECEIVE_STORE, new FileStoreDefinition());
        map.put(SPLIT_STORE, new FileStoreDefinition());
        map.put(AGGREGATE_STORE, new FileStoreDefinition());
        return map;
    }
}
