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

package stroom.proxy.app.pipeline;

import stroom.proxy.app.ProxyConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects pipeline runtime state into an immutable
 * {@link PipelineMonitorSnapshot} for the admin monitoring endpoint.
 * <p>
 * When the pipeline is disabled, returns a snapshot with
 * {@code pipelineEnabled=false} and empty stage/queue/store lists.
 * </p>
 */
@Singleton
public class PipelineMonitorProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineMonitorProvider.class);

    private final ProxyConfig proxyConfig;
    private final Provider<ProxyPipelineAssembler> assemblerProvider;

    @Inject
    public PipelineMonitorProvider(final ProxyConfig proxyConfig,
                                   final Provider<ProxyPipelineAssembler> assemblerProvider) {
        this.proxyConfig = proxyConfig;
        this.assemblerProvider = assemblerProvider;
    }

    /**
     * @return A snapshot of the current pipeline state. Returns a disabled
     * snapshot when the pipeline is not enabled.
     */
    public PipelineMonitorSnapshot snapshot() {
        if (!proxyConfig.getPipelineConfig().isEnabled()) {
            return new PipelineMonitorSnapshot(
                    false,
                    List.of(),
                    List.of(),
                    List.of());
        }

        try {
            final ProxyPipelineRuntime runtime = assemblerProvider.get().getRuntime();
            return buildSnapshot(runtime);
        } catch (final Exception e) {
            LOGGER.warn(() -> "Failed to build pipeline monitor snapshot", e);
            return new PipelineMonitorSnapshot(
                    true,
                    List.of(),
                    List.of(),
                    List.of());
        }
    }

    /**
     * Build a snapshot from an existing runtime (useful for testing).
     */
    public static PipelineMonitorSnapshot buildSnapshot(final ProxyPipelineRuntime runtime) {
        final List<PipelineMonitorSnapshot.StageSnapshot> stages = new ArrayList<>();
        runtime.streamStages().forEach(stage -> {
            final Optional<FileGroupQueueWorker> worker = stage.getWorker();
            final FileGroupQueueWorkerCounters.Snapshot counters = worker
                    .map(w -> w.getCounters().snapshot())
                    .orElse(null);
            final int threadCount = stage.getThreads().getConsumerThreads();

            stages.add(new PipelineMonitorSnapshot.StageSnapshot(
                    stage.getConfigName(),
                    worker.isPresent(),
                    threadCount,
                    counters));
        });

        final List<PipelineMonitorSnapshot.QueueSnapshot> queues = new ArrayList<>();
        runtime.getQueues().forEach((name, queue) ->
                queues.add(new PipelineMonitorSnapshot.QueueSnapshot(
                        name,
                        queue.getClass().getSimpleName())));

        final List<PipelineMonitorSnapshot.FileStoreSnapshot> fileStores = new ArrayList<>();
        runtime.getFileStores().forEach((name, store) ->
                fileStores.add(new PipelineMonitorSnapshot.FileStoreSnapshot(name)));

        return new PipelineMonitorSnapshot(true, stages, queues, fileStores);
    }
}
