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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

import java.util.Optional;

/**
 * Registers Codahale/Prometheus metrics for the pipeline runtime.
 * <p>
 * All gauges read monotonically increasing {@link java.util.concurrent.atomic.LongAdder}
 * totals from {@link FileGroupQueueWorkerCounters}. Prometheus derives rates
 * via {@code rate()}.
 * </p>
 * <p>
 * The existing {@code PrometheusModule} bridges Codahale metrics to the
 * admin {@code /metrics} endpoint in Prometheus format.
 * </p>
 */
public class PipelineMetricsRegistrar {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineMetricsRegistrar.class);

    private static final String PREFIX = "stroom.proxy.pipeline.";

    private PipelineMetricsRegistrar() {
        // Static utility class.
    }

    /**
     * Register all pipeline metrics for the given runtime.
     *
     * @param runtime  The assembled pipeline runtime.
     * @param registry The Codahale metric registry.
     */
    public static void register(final ProxyPipelineRuntime runtime,
                                final MetricRegistry registry) {
        // Per-stage metrics from worker counters.
        runtime.streamStages().forEach(stage -> {
            final Optional<FileGroupQueueWorker> workerOpt = stage.getWorker();
            if (workerOpt.isEmpty()) {
                return;
            }
            final FileGroupQueueWorkerCounters counters = workerOpt.get().getCounters();
            final String stageName = stage.getConfigName();
            final String stagePrefix = PREFIX + stageName + ".";

            registerGauge(registry, stagePrefix + "items.received", counters::getItemReceivedCount);
            registerGauge(registry, stagePrefix + "items.processed", counters::getItemProcessedCount);
            registerGauge(registry, stagePrefix + "items.acknowledged", counters::getItemAcknowledgedCount);
            registerGauge(registry, stagePrefix + "items.failed", counters::getItemFailedCount);
            registerGauge(registry, stagePrefix + "errors.processor", counters::getProcessorErrorCount);
            registerGauge(registry, stagePrefix + "errors.acknowledge", counters::getAcknowledgeErrorCount);
            registerGauge(registry, stagePrefix + "errors.fail", counters::getFailErrorCount);
            registerGauge(registry, stagePrefix + "errors.close", counters::getCloseErrorCount);
            registerGauge(registry, stagePrefix + "polls.total", counters::getPollCount);
            registerGauge(registry, stagePrefix + "polls.empty", counters::getEmptyPollCount);
        });

        // Per-queue depth metrics (for LocalFileGroupQueue only).
        runtime.getQueues().forEach((queueName, queue) -> {
            if (queue instanceof LocalFileGroupQueue localQueue) {
                final String queuePrefix = PREFIX + "queue." + queueName + ".";
                registerGauge(registry, queuePrefix + "pending",
                        () -> safeCount(localQueue::getApproximatePendingCount));
                registerGauge(registry, queuePrefix + "inflight",
                        () -> safeCount(localQueue::getApproximateInFlightCount));
                registerGauge(registry, queuePrefix + "failed",
                        () -> safeCount(localQueue::getApproximateFailedCount));
            }
        });

        // SQS heartbeat metrics.
        runtime.getQueues().forEach((queueName, queue) -> {
            if (queue instanceof SqsFileGroupQueue sqsQueue) {
                final SqsHeartbeatCounters heartbeatCounters = sqsQueue.getHeartbeatCounters();
                if (heartbeatCounters != null) {
                    final String hbPrefix = PREFIX + "queue." + queueName + ".heartbeat.";
                    registerGauge(registry, hbPrefix + "attempts", heartbeatCounters::getAttemptCount);
                    registerGauge(registry, hbPrefix + "successes", heartbeatCounters::getSuccessCount);
                    registerGauge(registry, hbPrefix + "failures", heartbeatCounters::getFailureCount);
                }
            }
        });

        LOGGER.info(() -> "Registered pipeline metrics");
    }

    private static void registerGauge(final MetricRegistry registry,
                                      final String name,
                                      final Gauge<Long> gauge) {
        try {
            registry.register(name, gauge);
        } catch (final IllegalArgumentException e) {
            // Metric already registered (e.g. after pipeline restart).
            LOGGER.debug(() -> "Metric already registered: " + name);
        }
    }

    private static long safeCount(final IoLongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (final Exception e) {
            return -1;
        }
    }

    @FunctionalInterface
    private interface IoLongSupplier {
        long getAsLong() throws Exception;
    }
}
