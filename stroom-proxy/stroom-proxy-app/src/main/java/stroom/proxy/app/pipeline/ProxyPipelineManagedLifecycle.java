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

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Dropwizard {@link Managed} adapter for the reference-message pipeline lifecycle.
 * <p>
 * This is a thin wrapper that conditionally starts/stops the pipeline based on
 * the {@code pipeline.enabled} configuration flag. When disabled, start and
 * stop are no-ops.
 * </p>
 * <p>
 * The assembler is fetched lazily via a {@link Provider} so that production
 * handlers (PreAggregator, Aggregator, Forwarder, etc.) are only fully wired
 * when the pipeline is actually enabled.
 * </p>
 */
@Singleton
public class ProxyPipelineManagedLifecycle implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyPipelineManagedLifecycle.class);

    private final ProxyConfig proxyConfig;
    private final Provider<ProxyPipelineAssembler> assemblerProvider;
    private volatile ProxyPipelineLifecycle lifecycle;

    @Inject
    public ProxyPipelineManagedLifecycle(final ProxyConfig proxyConfig,
                                         final Provider<ProxyPipelineAssembler> assemblerProvider) {
        this.proxyConfig = proxyConfig;
        this.assemblerProvider = assemblerProvider;
    }

    @Override
    public void start() {
        if (proxyConfig.getPipelineConfig().isEnabled()) {
            LOGGER.info(() -> "Reference-message pipeline is enabled, starting lifecycle...");
            lifecycle = assemblerProvider.get().getLifecycle();
            lifecycle.start();
            LOGGER.info(() -> "Reference-message pipeline lifecycle started");
        } else {
            LOGGER.info(() -> "Reference-message pipeline is disabled, skipping lifecycle start");
        }
    }

    @Override
    public void stop() {
        if (lifecycle != null) {
            LOGGER.info(() -> "Stopping reference-message pipeline lifecycle...");
            lifecycle.stop();
            LOGGER.info(() -> "Reference-message pipeline lifecycle stopped");
        }
    }
}
