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

package stroom.proxy.app.pipeline.monitor;


import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.execution.Loop;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.guice.ProxyCoreModule;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineRuntime;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated Dropwizard health check for the proxy pipeline: every queue's and file store's own
 * health check, with their details carried through under a per-component prefix, and every loop's
 * live thread count against its configured one.
 */
@Singleton
public class PipelineHealthChecks implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineHealthChecks.class);

    private final Provider<ProxyPipelineAssembler> assemblerProvider;
    private final WorkRegistry workRegistry;
    private final boolean instantForwarding;

    @Inject
    public PipelineHealthChecks(final ProxyConfig proxyConfig,
                                final Provider<ProxyPipelineAssembler> assemblerProvider,
                                final WorkRegistry workRegistry) {
        this.instantForwarding = ProxyCoreModule.isInstantForwarding(proxyConfig);
        this.assemblerProvider = assemblerProvider;
        this.workRegistry = workRegistry;
    }

    @Override
    public HealthCheck.Result getHealth() {
        if (instantForwarding) {
            // Do NOT call the assembler here. Instant forwarding bypasses the pipeline, and asking for
            // the assembler would build every queue and file store - which nothing then starts, uses
            // or closes.
            return HealthCheck.Result.healthy("Pipeline not in use - instant forwarding is configured");
        }
        try {
            final ProxyPipelineRuntime runtime = assemblerProvider.get().getRuntime();
            return checkRuntime(runtime);
        } catch (final Exception e) {
            LOGGER.warn(() -> "Failed to check pipeline health", e);
            return HealthCheck.Result.unhealthy(e);
        }
    }

    /**
     * Package-visible so the aggregation is reachable from a test: {@link #getHealth()} goes through the
     * assembler, which builds every queue and file store, and the property under test is only that a
     * component's own details survive into the aggregate result.
     */
    HealthCheck.Result checkRuntime(final ProxyPipelineRuntime runtime) {
        final Map<String, Object> details = new LinkedHashMap<>();
        boolean allHealthy = true;

        // A loop thread never dies on purpose, so fewer live threads than configured is a defect
        // that nothing else would show.
        if (workRegistry.isStarted()) {
            for (final Loop loop : workRegistry.loops()) {
                details.put("loop." + loop.getName() + ".threads.configured", loop.configuredThreads());
                details.put("loop." + loop.getName() + ".threads.live", loop.liveThreads());
                details.put("loop." + loop.getName() + ".paused", loop.isPaused());
                if (loop.liveThreads() < loop.configuredThreads()) {
                    allHealthy = false;
                    details.put("loop." + loop.getName() + ".message", "fewer live threads than configured");
                }
            }
        }

        // Check all queues.
        for (final Map.Entry<String, FileGroupQueue> entry : runtime.getQueues().entrySet()) {
            final String queueName = entry.getKey();
            try {
                final HealthCheck.Result queueResult = entry.getValue().healthCheck();
                details.put("queue." + queueName + ".healthy", queueResult.isHealthy());
                // And everything the queue said: an SQS queue reports its approximate depth, in-flight
                // count and active heartbeats from a live GetQueueAttributes, a Kafka queue its topic
                // and partitions, and this is the one place those are exposed.
                copyDetailsInto(details, "queue." + queueName, queueResult);
                if (!queueResult.isHealthy()) {
                    allHealthy = false;
                    details.put("queue." + queueName + ".message", queueResult.getMessage());
                }
            } catch (final Exception e) {
                LOGGER.warn(() -> LogUtil.message("Health probe of queue {} failed: {}", queueName, e.getMessage()), e);
                allHealthy = false;
                details.put("queue." + queueName + ".healthy", false);
                details.put("queue." + queueName + ".message", e.getMessage());
            }
        }

        // Check all file stores.
        for (final Map.Entry<String, FileStore> entry : runtime.getFileStores().entrySet()) {
            final String storeName = entry.getKey();
            try {
                final HealthCheck.Result storeResult = entry.getValue().healthCheck();
                details.put("fileStore." + storeName + ".healthy", storeResult.isHealthy());
                // And everything the store said.
                copyDetailsInto(details, "fileStore." + storeName, storeResult);
                if (!storeResult.isHealthy()) {
                    allHealthy = false;
                    details.put("fileStore." + storeName + ".message", storeResult.getMessage());
                }
            } catch (final Exception e) {
                LOGGER.warn(() -> LogUtil.message(
                        "Health probe of file store {} failed: {}", storeName, e.getMessage()), e);
                allHealthy = false;
                details.put("fileStore." + storeName + ".healthy", false);
                details.put("fileStore." + storeName + ".message", e.getMessage());
            }
        }

        final HealthCheck.ResultBuilder builder = HealthCheck.Result.builder();
        if (allHealthy) {
            builder.healthy();
        } else {
            builder.unhealthy()
                    .withMessage("One or more pipeline components are unhealthy");
        }
        builder.withDetail("components", details);
        return builder.build();
    }

    /**
     * Copy a component's own health-check details into the aggregate result, namespaced by the
     * component.
     *
     * @param prefix e.g. {@code queue.receiveOutput}, so a key reads {@code queue.receiveOutput.approximateMessages}.
     */
    private static void copyDetailsInto(final Map<String, Object> details,
                                        final String prefix,
                                        final HealthCheck.Result result) {
        final Map<String, Object> resultDetails = result.getDetails();
        if (resultDetails != null) {
            resultDetails.forEach((key, value) -> details.put(prefix + "." + key, value));
        }
    }

}
