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

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStore;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The assembled pipeline as a model: a {@link RuntimeStage} per enabled stage with
 * its resolved queues, store and worker, and every queue and file store this process opened, by
 * name. Built by {@link ProxyPipelineAssembler} from an already-validated configuration - the
 * runtime validates nothing itself (R15) - and read by the assembler to register loops, by the
 * health checks, the metrics and the monitor, and closed with everything else at stop.
 */
public class ProxyPipelineRuntime implements AutoCloseable {

    private final Map<PipelineStageName, RuntimeStage> stages;
    private final Map<String, FileGroupQueue> queues;
    private final Map<String, FileStore> fileStores;

    public ProxyPipelineRuntime(final Map<PipelineStageName, RuntimeStage> stages,
                                final Map<String, FileGroupQueue> queues,
                                final Map<String, FileStore> fileStores) {
        final EnumMap<PipelineStageName, RuntimeStage> stageMap = new EnumMap<>(PipelineStageName.class);
        stageMap.putAll(Objects.requireNonNull(stages, "stages"));
        this.stages = Collections.unmodifiableMap(stageMap);

        this.queues = Map.copyOf(Objects.requireNonNull(queues, "queues"));
        this.fileStores = Map.copyOf(Objects.requireNonNull(fileStores, "fileStores"));
    }

    /**
     * Build a runtime model from pipeline configuration and factories. The configuration must
     * already have been validated; nothing here checks it.
     *
     * @param pipelineConfig The pipeline configuration.
     * @param queueFactory The queue factory.
     * @param fileStoreFactory The file-store factory.
     * @return The assembled runtime model.
     */
    public static ProxyPipelineRuntime fromConfig(final ProxyPipelineConfig pipelineConfig,
                                                  final FileGroupQueueFactory queueFactory,
                                                  final FileStoreFactory fileStoreFactory) {
        return fromConfig(
                pipelineConfig,
                queueFactory,
                fileStoreFactory,
                Map.of());
    }

    /**
     * Build a runtime model from pipeline configuration, factories, and stage processors.
     * <p>
     * Queue-consuming stages are given a {@link FileGroupQueueWorker} when a processor
     * is supplied for the stage. Receive is not queue-consuming, so it does not get a
     * worker.
     * </p>
     *
     * @param pipelineConfig The pipeline configuration.
     * @param queueFactory The queue factory.
     * @param fileStoreFactory The file-store factory.
     * @param stageProcessors Stage-specific queue item processors keyed by stage name.
     * @return The assembled runtime model.
     */
    public static ProxyPipelineRuntime fromConfig(final ProxyPipelineConfig pipelineConfig,
                                                  final FileGroupQueueFactory queueFactory,
                                                  final FileStoreFactory fileStoreFactory,
                                                  final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                          stageProcessors) {
        return fromConfig(pipelineConfig, queueFactory, fileStoreFactory, stageProcessors, List.of(), List.of());
    }

    /**
     * @param additionalQueueNames     Queues no stage names in its configuration but this process
     *                                 uses, such as the per-destination forward queues, opened so
     *                                 that they are monitored and closed with the rest.
     * @param additionalFileStoreNames Likewise for file stores.
     */
    public static ProxyPipelineRuntime fromConfig(final ProxyPipelineConfig pipelineConfig,
                                                  final FileGroupQueueFactory queueFactory,
                                                  final FileStoreFactory fileStoreFactory,
                                                  final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                          stageProcessors,
                                                  final Collection<String> additionalQueueNames,
                                                  final Collection<String> additionalFileStoreNames) {
        return fromConfig(pipelineConfig, queueFactory, fileStoreFactory, stageProcessors,
                additionalQueueNames, additionalFileStoreNames, Map.of());
    }

    /**
     * @param threadOverrides A thread count that replaces a stage's configured one, so that the
     *                        runtime reports what runs: the forward stage's loop is its one
     *                        destination's loop when there is one destination, and runs with that
     *                        destination's thread count.
     */
    public static ProxyPipelineRuntime fromConfig(final ProxyPipelineConfig pipelineConfig,
                                                  final FileGroupQueueFactory queueFactory,
                                                  final FileStoreFactory fileStoreFactory,
                                                  final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                          stageProcessors,
                                                  final Collection<String> additionalQueueNames,
                                                  final Collection<String> additionalFileStoreNames,
                                                  final Map<PipelineStageName, Integer> threadOverrides) {
        Objects.requireNonNull(pipelineConfig, "pipelineConfig");
        Objects.requireNonNull(queueFactory, "queueFactory");
        Objects.requireNonNull(fileStoreFactory, "fileStoreFactory");
        Objects.requireNonNull(stageProcessors, "stageProcessors");

        final ProxyPipelineTopology topology = ProxyPipelineTopology.fromConfig(pipelineConfig);
        return fromTopology(topology, queueFactory, fileStoreFactory, stageProcessors,
                additionalQueueNames, additionalFileStoreNames, threadOverrides);
    }

    /**
     * Build a runtime model from an already-created topology.
     *
     * @param topology The validated topology.
     * @param queueFactory The queue factory.
     * @param fileStoreFactory The file-store factory.
     * @return The assembled runtime model.
     */
    public static ProxyPipelineRuntime fromTopology(final ProxyPipelineTopology topology,
                                                    final FileGroupQueueFactory queueFactory,
                                                    final FileStoreFactory fileStoreFactory) {
        return fromTopology(
                topology,
                queueFactory,
                fileStoreFactory,
                Map.of());
    }

    /**
     * Build a runtime model from an already-created topology and stage processors.
     *
     * @param topology The validated topology.
     * @param queueFactory The queue factory.
     * @param fileStoreFactory The file-store factory.
     * @param stageProcessors Stage-specific queue item processors keyed by stage name.
     * @return The assembled runtime model.
     */
    public static ProxyPipelineRuntime fromTopology(final ProxyPipelineTopology topology,
                                                    final FileGroupQueueFactory queueFactory,
                                                    final FileStoreFactory fileStoreFactory,
                                                    final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                            stageProcessors) {
        return fromTopology(topology, queueFactory, fileStoreFactory, stageProcessors, List.of(), List.of());
    }

    public static ProxyPipelineRuntime fromTopology(final ProxyPipelineTopology topology,
                                                    final FileGroupQueueFactory queueFactory,
                                                    final FileStoreFactory fileStoreFactory,
                                                    final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                            stageProcessors,
                                                    final Collection<String> additionalQueueNames,
                                                    final Collection<String> additionalFileStoreNames) {
        return fromTopology(topology, queueFactory, fileStoreFactory, stageProcessors,
                additionalQueueNames, additionalFileStoreNames, Map.of());
    }

    public static ProxyPipelineRuntime fromTopology(final ProxyPipelineTopology topology,
                                                    final FileGroupQueueFactory queueFactory,
                                                    final FileStoreFactory fileStoreFactory,
                                                    final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                            stageProcessors,
                                                    final Collection<String> additionalQueueNames,
                                                    final Collection<String> additionalFileStoreNames,
                                                    final Map<PipelineStageName, Integer> threadOverrides) {
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(threadOverrides, "threadOverrides");
        Objects.requireNonNull(additionalQueueNames, "additionalQueueNames");
        Objects.requireNonNull(additionalFileStoreNames, "additionalFileStoreNames");
        Objects.requireNonNull(queueFactory, "queueFactory");
        Objects.requireNonNull(fileStoreFactory, "fileStoreFactory");
        Objects.requireNonNull(stageProcessors, "stageProcessors");

        final Map<String, FileGroupQueue> queues = new LinkedHashMap<>();
        final Map<String, FileStore> fileStores = new LinkedHashMap<>();
        final EnumMap<PipelineStageName, RuntimeStage> runtimeStages = new EnumMap<>(PipelineStageName.class);

        topology.streamEnabledStages()
                .forEach(stage -> {
                    final RuntimeStage runtimeStage = createRuntimeStage(
                            stage,
                            queueFactory,
                            fileStoreFactory,
                            stageProcessors,
                            queues,
                            fileStores,
                            threadOverrides.get(stage.name()));

                    runtimeStages.put(stage.name(), runtimeStage);
                });
        additionalQueueNames.forEach(name -> getOrCreateQueue(name, queueFactory, queues));
        additionalFileStoreNames.forEach(name -> getOrCreateFileStore(name, fileStoreFactory, fileStores));

        return new ProxyPipelineRuntime(
                runtimeStages,
                queues,
                fileStores);
    }

    public Map<PipelineStageName, RuntimeStage> getStages() {
        return stages;
    }

    public Optional<RuntimeStage> getStage(final PipelineStageName stageName) {
        return Optional.ofNullable(stages.get(stageName));
    }

    public Stream<RuntimeStage> streamStages() {
        return stages.values().stream();
    }

    public Optional<FileGroupQueueWorker> getWorker(final PipelineStageName stageName) {
        return getStage(stageName)
                .flatMap(RuntimeStage::getWorker);
    }

    public Stream<FileGroupQueueWorker> streamWorkers() {
        return streamStages()
                .map(RuntimeStage::getWorker)
                .flatMap(Optional::stream);
    }

    public Map<PipelineStageName, FileGroupQueueWorker> getWorkers() {
        final EnumMap<PipelineStageName, FileGroupQueueWorker> workers = new EnumMap<>(PipelineStageName.class);
        stages.forEach((stageName, runtimeStage) ->
                runtimeStage.getWorker()
                        .ifPresent(worker -> workers.put(stageName, worker)));
        return Collections.unmodifiableMap(workers);
    }

    public boolean isStageEnabled(final PipelineStageName stageName) {
        return stages.containsKey(stageName);
    }

    public Map<String, FileGroupQueue> getQueues() {
        return queues;
    }

    public Optional<FileGroupQueue> getQueue(final String queueName) {
        return Optional.ofNullable(queues.get(queueName));
    }

    public Map<String, FileStore> getFileStores() {
        return fileStores;
    }

    public Optional<FileStore> getFileStore(final String fileStoreName) {
        return Optional.ofNullable(fileStores.get(fileStoreName));
    }

    /**
     * Collect a close failure without letting it stop the rest of the closing.
     * <p>
     * R8: nothing depends on any of this having run, so the only thing worth preserving is a report
     * of what went wrong - and the first failure must not prevent the remaining components being
     * released.
     * </p>
     */
    private static IOException record(final IOException first,
                                      final Throwable error,
                                      final String what) {
        final IOException wrapped = error instanceof IOException io
                ? io
                : new IOException("Failed to close " + what, error);
        if (first == null) {
            return wrapped;
        }
        first.addSuppressed(wrapped);
        return first;
    }

    /**
     * Close every queue and file store this runtime opened.
     * <p>
     * <strong>Queues and file stores, in that order.</strong> A queue's close may still resolve or
     * delete through a store, so the stores go last.
     * </p>
     * <p>
     * <strong>Nothing depends on any of this running</strong> (R8). Correctness rests on
     * {@code kill -9} being survivable; what this buys is promptness - consumer group memberships
     * released rather than timing out, admin-client threads ended, connection pools returned.
     * </p>
     * <p>
     * Every component is closed whatever the others do; if more than one fails, the first exception
     * is thrown with the rest attached as suppressed.
     * </p>
     *
     * @throws IOException If one or more components fail to close.
     */
    @Override
    public void close() throws IOException {
        IOException firstException = null;

        // The AWS and Kafka clients signal with unchecked exceptions, so RuntimeException is caught
        // too: one failing client must not leave every component after it open.
        for (final FileGroupQueue queue : queues.values()) {
            try {
                queue.close();
            } catch (final IOException | RuntimeException e) {
                firstException = record(firstException, e, "queue " + queue.getName());
            }
        }

        // File stores hold closeable resources - S3FileStore has an S3Client. Closed after the
        // queues, because a queue's close may still resolve or delete through a store.
        for (final FileStore fileStore : fileStores.values()) {
            try {
                fileStore.close();
            } catch (final Exception e) {
                firstException = record(firstException, e, "file store " + fileStore.getName());
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }

    private static RuntimeStage createRuntimeStage(final PipelineStage stage,
                                                   final FileGroupQueueFactory queueFactory,
                                                   final FileStoreFactory fileStoreFactory,
                                                   final Map<PipelineStageName, FileGroupQueueItemProcessor>
                                                           stageProcessors,
                                                   final Map<String, FileGroupQueue> queues,
                                                   final Map<String, FileStore> fileStores,
                                                   final Integer threadOverride) {
        final Optional<FileGroupQueue> inputQueue = stage.getInputQueueOpt()
                .map(queueName -> getOrCreateQueue(queueName, queueFactory, queues));

        final Optional<FileGroupQueue> outputQueue = stage.getOutputQueueOpt()
                .map(queueName -> getOrCreateQueue(queueName, queueFactory, queues));

        final Optional<FileGroupQueue> splitZipQueue = stage.getSplitZipQueueOpt()
                .map(queueName -> getOrCreateQueue(queueName, queueFactory, queues));

        final Optional<FileStore> fileStore = stage.getFileStoreOpt()
                .map(fileStoreName -> getOrCreateFileStore(fileStoreName, fileStoreFactory, fileStores));

        final FileGroupQueueWorker worker = inputQueue
                .flatMap(queue -> Optional.ofNullable(stageProcessors.get(stage.name()))
                        .map(processor -> new FileGroupQueueWorker(queue, processor)))
                .orElse(null);

        return new RuntimeStage(
                stage.name(),
                threadOverride != null
                        ? new ConsumerStageThreadsConfig(threadOverride)
                        : stage.consumerThreads(),
                inputQueue.orElse(null),
                outputQueue.orElse(null),
                splitZipQueue.orElse(null),
                fileStore.orElse(null),
                worker);
    }

    private static FileGroupQueue getOrCreateQueue(final String queueName,
                                                   final FileGroupQueueFactory queueFactory,
                                                   final Map<String, FileGroupQueue> queues) {
        return queues.computeIfAbsent(queueName, queueFactory::getQueue);
    }

    private static FileStore getOrCreateFileStore(final String fileStoreName,
                                                  final FileStoreFactory fileStoreFactory,
                                                  final Map<String, FileStore> fileStores) {
        return fileStores.computeIfAbsent(fileStoreName, fileStoreFactory::getFileStore);
    }

    /**
     * Runtime model for a single enabled pipeline stage.
     *
     * @param stageName The logical stage name.
     * @param consumerThreads The consumer thread config, if this is a queue-consuming stage.
     * @param inputQueue The resolved input queue, if configured.
     * @param outputQueue The resolved output queue, if configured.
     * @param splitZipQueue The resolved split zip queue, if configured.
     * @param fileStore The resolved file store, if configured.
     * @param worker The queue worker for queue-consuming stages, if a processor was supplied.
     */
    public record RuntimeStage(
            PipelineStageName stageName,
            ConsumerStageThreadsConfig consumerThreads,
            FileGroupQueue inputQueue,
            FileGroupQueue outputQueue,
            FileGroupQueue splitZipQueue,
            FileStore fileStore,
            FileGroupQueueWorker worker) {

        public RuntimeStage {
            stageName = Objects.requireNonNull(stageName, "stageName");
        }

        public String getConfigName() {
            return stageName.getConfigName();
        }

        public ConsumerStageThreadsConfig getThreads() {
            return consumerThreads;
        }

        public Optional<FileGroupQueue> getInputQueue() {
            return Optional.ofNullable(inputQueue);
        }

        public Optional<FileGroupQueue> getOutputQueue() {
            return Optional.ofNullable(outputQueue);
        }

        public Optional<FileGroupQueue> getSplitZipQueue() {
            return Optional.ofNullable(splitZipQueue);
        }

        public Optional<FileStore> getFileStore() {
            return Optional.ofNullable(fileStore);
        }

        public Optional<FileGroupQueueWorker> getWorker() {
            return Optional.ofNullable(worker);
        }

        public boolean hasInputQueue() {
            return inputQueue != null;
        }

        public boolean hasOutputQueue() {
            return outputQueue != null;
        }

        public boolean hasSplitZipQueue() {
            return splitZipQueue != null;
        }

        public boolean hasFileStore() {
            return fileStore != null;
        }

        public boolean hasWorker() {
            return worker != null;
        }
    }

    /**
     * @return The queues used by this runtime.
     */
    public Collection<FileGroupQueue> streamQueueValues() {
        return queues.values();
    }

    /**
     * @return The file stores used by this runtime.
     */
    public Collection<FileStore> streamFileStoreValues() {
        return fileStores.values();
    }
}
