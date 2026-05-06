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


import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.health.HealthCheck;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated Dropwizard health check for the proxy pipeline.
 * <p>
 * Checks all configured queues and file stores for backend connectivity.
 * </p>
 */
@Singleton
public class PipelineHealthChecks implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineHealthChecks.class);

    private final Provider<ProxyPipelineAssembler> assemblerProvider;

    @Inject
    public PipelineHealthChecks(final Provider<ProxyPipelineAssembler> assemblerProvider) {
        this.assemblerProvider = assemblerProvider;
    }

    @Override
    public HealthCheck.Result getHealth() {
        try {
            final ProxyPipelineRuntime runtime = assemblerProvider.get().getRuntime();
            return checkRuntime(runtime);
        } catch (final Exception e) {
            LOGGER.warn(() -> "Failed to check pipeline health", e);
            return HealthCheck.Result.unhealthy(e);
        }
    }

    private HealthCheck.Result checkRuntime(final ProxyPipelineRuntime runtime) {
        final Map<String, Object> details = new LinkedHashMap<>();
        boolean allHealthy = true;

        // Check all queues.
        for (final Map.Entry<String, FileGroupQueue> entry : runtime.getQueues().entrySet()) {
            final String queueName = entry.getKey();
            try {
                final HealthCheck.Result queueResult = entry.getValue().healthCheck();
                details.put("queue." + queueName + ".healthy", queueResult.isHealthy());
                if (!queueResult.isHealthy()) {
                    allHealthy = false;
                    details.put("queue." + queueName + ".message", queueResult.getMessage());
                }
            } catch (final Exception e) {
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
                if (!storeResult.isHealthy()) {
                    allHealthy = false;
                    details.put("fileStore." + storeName + ".message", storeResult.getMessage());
                }
            } catch (final Exception e) {
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
}
