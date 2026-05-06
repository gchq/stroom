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

package stroom.proxy.app.pipeline.monitor;


import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.queue.sqs.SqsFileGroupQueue;
import stroom.proxy.app.pipeline.queue.sqs.SqsHeartbeatCounters;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineRuntime;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorkerCounters;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.health.HealthCheck;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects pipeline runtime state into an immutable
 * {@link PipelineMonitorSnapshot} for the admin monitoring endpoint.
 */
@Singleton
public class PipelineMonitorProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineMonitorProvider.class);

    private final Provider<ProxyPipelineAssembler> assemblerProvider;

    @Inject
    public PipelineMonitorProvider(final Provider<ProxyPipelineAssembler> assemblerProvider) {
        this.assemblerProvider = assemblerProvider;
    }

    /**
     * @return A snapshot of the current pipeline state.
     */
    public PipelineMonitorSnapshot snapshot() {
        try {
            final ProxyPipelineRuntime runtime = assemblerProvider.get().getRuntime();
            return buildSnapshot(runtime);
        } catch (final Exception e) {
            LOGGER.warn(() -> "Failed to build pipeline monitor snapshot", e);
            return new PipelineMonitorSnapshot(
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
            final int threadCount = stage.getThreads() != null
                    ? stage.getThreads().getConsumerThreads()
                    : 0;

            stages.add(new PipelineMonitorSnapshot.StageSnapshot(
                    stage.getConfigName(),
                    worker.isPresent(),
                    threadCount,
                    counters));
        });

        final List<PipelineMonitorSnapshot.QueueSnapshot> queues = new ArrayList<>();
        runtime.getQueues().forEach((name, queue) -> {
            // Run health check.
            boolean healthy = true;
            String healthDetail = null;
            try {
                final HealthCheck.Result result = queue.healthCheck();
                healthy = result.isHealthy();
                if (!healthy) {
                    healthDetail = result.getMessage();
                }
            } catch (final Exception e) {
                healthy = false;
                healthDetail = e.getMessage();
            }

            // Get queue depths for local queues.
            Map<String, Long> depths = null;
            if (queue instanceof LocalFileGroupQueue localQueue) {
                try {
                    depths = Map.of(
                            "pending", localQueue.getApproximatePendingCount(),
                            "inflight", localQueue.getApproximateInFlightCount(),
                            "failed", localQueue.getApproximateFailedCount());
                } catch (final Exception e) {
                    LOGGER.debug(() -> "Failed to read queue depths for " + name, e);
                }
            }

            // Get heartbeat counters for SQS queues.
            SqsHeartbeatCounters.Snapshot heartbeatSnapshot = null;
            if (queue instanceof SqsFileGroupQueue sqsQueue) {
                heartbeatSnapshot = sqsQueue.getHeartbeatCounters().snapshot();
            }

            queues.add(new PipelineMonitorSnapshot.QueueSnapshot(
                    name,
                    queue.getClass().getSimpleName(),
                    healthy,
                    healthDetail,
                    depths,
                    heartbeatSnapshot));
        });

        final List<PipelineMonitorSnapshot.FileStoreSnapshot> fileStores = new ArrayList<>();
        runtime.getFileStores().forEach((name, store) -> {
            boolean healthy = true;
            String healthDetail = null;
            try {
                final HealthCheck.Result result = store.healthCheck();
                healthy = result.isHealthy();
                if (!healthy) {
                    healthDetail = result.getMessage();
                }
            } catch (final Exception e) {
                healthy = false;
                healthDetail = e.getMessage();
            }
            fileStores.add(new PipelineMonitorSnapshot.FileStoreSnapshot(name, healthy, healthDetail));
        });

        return new PipelineMonitorSnapshot(stages, queues, fileStores);
    }
}
