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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Immutable topology model for the reference-message proxy pipeline.
 * <p>
 * The topology describes:
 * </p>
 * <ul>
 *     <li>the configured logical stages,</li>
 *     <li>which stages are enabled on this process,</li>
 *     <li>the named queues that connect stages, and</li>
 *     <li>the named file stores used by stages that write file groups.</li>
 * </ul>
 * <p>
 * This model is deliberately transport neutral. Queue edges refer to logical
 * queue names; queue construction remains the responsibility of
 * {@link FileGroupQueueFactory}.
 * </p>
 */
class ProxyPipelineTopology {

    private final Map<PipelineStageName, PipelineStage> stages;
    private final Map<String, QueueDefinition> queues;
    private final Map<String, FileStoreDefinition> fileStores;

    public ProxyPipelineTopology(final Map<PipelineStageName, PipelineStage> stages,
                                 final Map<String, QueueDefinition> queues,
                                 final Map<String, FileStoreDefinition> fileStores) {
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(queues, "queues");
        Objects.requireNonNull(fileStores, "fileStores");

        final EnumMap<PipelineStageName, PipelineStage> stageMap = new EnumMap<>(PipelineStageName.class);
        stageMap.putAll(stages);

        this.stages = Collections.unmodifiableMap(stageMap);
        this.queues = Map.copyOf(queues);
        this.fileStores = Map.copyOf(fileStores);
    }

    public static ProxyPipelineTopology fromConfig(final ProxyPipelineConfig pipelineConfig) {
        final ProxyPipelineConfig nonNullPipelineConfig = Objects.requireNonNull(
                pipelineConfig,
                "pipelineConfig");

        final PipelineStagesConfig stagesConfig = nonNullPipelineConfig.getStages();
        final EnumMap<PipelineStageName, PipelineStage> stages = new EnumMap<>(PipelineStageName.class);

        stages.put(PipelineStageName.RECEIVE, PipelineStage.receive(stagesConfig));
        stages.put(PipelineStageName.SPLIT_ZIP, PipelineStage.splitZip(stagesConfig));
        stages.put(PipelineStageName.PRE_AGGREGATE, PipelineStage.preAggregate(stagesConfig));
        stages.put(PipelineStageName.AGGREGATE, PipelineStage.aggregate(stagesConfig));
        stages.put(PipelineStageName.FORWARD, PipelineStage.forward(stagesConfig));

        return new ProxyPipelineTopology(
                stages,
                nonNullPipelineConfig.getQueues(),
                nonNullPipelineConfig.getFileStores());
    }

    public Map<PipelineStageName, PipelineStage> getStages() {
        return stages;
    }

    public Optional<PipelineStage> getStage(final PipelineStageName stageName) {
        return Optional.ofNullable(stages.get(stageName));
    }

    public Stream<PipelineStage> streamStages() {
        return stages.values().stream();
    }

    public Stream<PipelineStage> streamEnabledStages() {
        return streamStages()
                .filter(PipelineStage::enabled);
    }

    public boolean isStageEnabled(final PipelineStageName stageName) {
        return getStage(stageName)
                .map(PipelineStage::enabled)
                .orElse(false);
    }

    public Map<String, QueueDefinition> getQueues() {
        return queues;
    }

    public Optional<QueueDefinition> getQueue(final String queueName) {
        return Optional.ofNullable(queues.get(queueName));
    }

    public boolean hasQueue(final String queueName) {
        return queues.containsKey(queueName);
    }

    public Map<String, FileStoreDefinition> getFileStores() {
        return fileStores;
    }

    public Optional<FileStoreDefinition> getFileStore(final String fileStoreName) {
        return Optional.ofNullable(fileStores.get(fileStoreName));
    }

    public boolean hasFileStore(final String fileStoreName) {
        return fileStores.containsKey(fileStoreName);
    }

}
