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
public class ProxyPipelineTopology {

    private final Map<PipelineStageName, PipelineStage> stages;
    private final List<PipelineEdge> edges;
    private final Map<String, QueueDefinition> queues;
    private final Map<String, FileStoreDefinition> fileStores;

    public ProxyPipelineTopology(final Map<PipelineStageName, PipelineStage> stages,
                                 final Collection<PipelineEdge> edges,
                                 final Map<String, QueueDefinition> queues,
                                 final Map<String, FileStoreDefinition> fileStores) {
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(queues, "queues");
        Objects.requireNonNull(fileStores, "fileStores");

        final EnumMap<PipelineStageName, PipelineStage> stageMap = new EnumMap<>(PipelineStageName.class);
        stageMap.putAll(stages);

        this.stages = Collections.unmodifiableMap(stageMap);
        this.edges = List.copyOf(edges);
        this.queues = Map.copyOf(queues);
        this.fileStores = Map.copyOf(fileStores);
    }

    public static ProxyPipelineTopology fromConfig(final ProxyPipelineConfig pipelineConfig) {
        final ProxyPipelineConfig nonNullPipelineConfig = Objects.requireNonNull(
                pipelineConfig,
                "pipelineConfig");

        final PipelineStagesConfig stagesConfig = nonNullPipelineConfig.getStages();
        final EnumMap<PipelineStageName, PipelineStage> stages = new EnumMap<>(PipelineStageName.class);

        stages.put(
                PipelineStageName.RECEIVE,
                new PipelineStage(PipelineStageName.RECEIVE, stagesConfig.getReceive()));
        stages.put(
                PipelineStageName.SPLIT_ZIP,
                new PipelineStage(PipelineStageName.SPLIT_ZIP, stagesConfig.getSplitZip()));
        stages.put(
                PipelineStageName.PRE_AGGREGATE,
                new PipelineStage(PipelineStageName.PRE_AGGREGATE, stagesConfig.getPreAggregate()));
        stages.put(
                PipelineStageName.AGGREGATE,
                new PipelineStage(PipelineStageName.AGGREGATE, stagesConfig.getAggregate()));
        stages.put(
                PipelineStageName.FORWARD,
                new PipelineStage(PipelineStageName.FORWARD, stagesConfig.getForward()));

        return new ProxyPipelineTopology(
                stages,
                buildEdges(stages.values()),
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
                .filter(PipelineStage::isEnabled);
    }

    public boolean isStageEnabled(final PipelineStageName stageName) {
        return getStage(stageName)
                .map(PipelineStage::isEnabled)
                .orElse(false);
    }

    public List<PipelineEdge> getEdges() {
        return edges;
    }

    public Stream<PipelineEdge> streamEdges() {
        return edges.stream();
    }

    public Stream<PipelineEdge> streamEdgesFrom(final PipelineStageName stageName) {
        return streamEdges()
                .filter(edge -> edge.sourceStage() == stageName);
    }

    public Stream<PipelineEdge> streamEdgesTo(final PipelineStageName stageName) {
        return streamEdges()
                .filter(edge -> edge.targetStage() == stageName);
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

    private static List<PipelineEdge> buildEdges(final Collection<PipelineStage> stages) {
        final List<PipelineEdge> edges = new ArrayList<>();

        for (final PipelineStage fromStage : stages) {
            addEdgesForQueue(stages, edges, fromStage, fromStage.getOutputQueue());
            addEdgesForQueue(stages, edges, fromStage, fromStage.getSplitZipQueue());
        }

        return edges;
    }

    private static void addEdgesForQueue(final Collection<PipelineStage> stages,
                                         final List<PipelineEdge> edges,
                                         final PipelineStage fromStage,
                                         final Optional<String> optionalQueueName) {
        optionalQueueName.ifPresent(queueName ->
                stages.stream()
                        .filter(toStage -> toStage.name() != fromStage.name())
                        .filter(toStage -> toStage.getInputQueue()
                                .filter(queueName::equals)
                                .isPresent())
                        .map(toStage -> new PipelineEdge(fromStage.name(), toStage.name(), queueName))
                        .forEach(edges::add));
    }


}
